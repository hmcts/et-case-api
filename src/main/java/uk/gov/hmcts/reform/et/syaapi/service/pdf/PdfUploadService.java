package uk.gov.hmcts.reform.et.syaapi.service.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.ecm.common.exceptions.PdfServiceException;
import uk.gov.hmcts.ecm.common.helpers.UtilHelper;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.ecm.common.service.pdf.PdfService;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.models.AcasCertificate;
import uk.gov.hmcts.reform.et.syaapi.models.AppResponseCitizen;
import uk.gov.hmcts.reform.et.syaapi.models.GenericTseApplication;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationException;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ClaimantTseUtil;
import uk.gov.hmcts.reform.et.syaapi.service.utils.GenericServiceUtil;
import uk.gov.hmcts.reform.et.syaapi.service.utils.RespondentTseUtil;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static uk.gov.hmcts.ecm.common.constants.PdfMapperConstants.PDF_TYPE_ET1;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.APP_TYPE_MAP;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLISH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ET1;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.PDF_FILE_TIKA_CONTENT_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.STRING_DASH;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE;

/**
 * Uses {@link PdfUploadService} to convert a given case into a Pdf Document.
 */
@Slf4j
@Service
@RequiredArgsConstructor()
public class PdfUploadService {

    private final PdfService pdfService;
    private final DocumentGenerationService documentGenerationService;
    @Value("${pdf.english}")
    public String englishPdfTemplateSource;
    @Value("${pdf.welsh}")
    public String welshPdfTemplateSource;
    @Value("${pdf.contact_tribunal_template}")
    public String contactTheTribunalPdfTemplate;
    @Value("${pdf.claimant_response_template}")
    public String claimantResponsePdfTemplate;

    private static final String TSE_FILENAME = "Contact the tribunal - ";
    private static final String CLAIMANT_TITLE = "Claimant";
    private static final String NOT_FOUND = "not found";
    private static final String CLIENT_TYPE_CLAIMANT = "claimant";
    private static final String SUBMIT_ET1_CITIZEN = "submitET1Citizen";
    private static final List<String> DOCUMENT_CHARS_TO_REPLACE = List.of("@", "/", "\\", "'", ":");

    public static String createPdfDocumentNameFromCaseData(CaseData caseData,
                                                            String documentLanguage,
                                                            UserInfo userInfo,
                                                            String documentType) {
        String claimantFirstName = caseData.getClaimantIndType().getClaimantFirstNames();
        String claimantLastName = caseData.getClaimantIndType().getClaimantLastName();
        if (isNullOrEmpty(claimantFirstName)) {
            claimantFirstName = userInfo.getGivenName();
        }
        if (isNullOrEmpty(claimantLastName)) {
            claimantLastName = userInfo.getFamilyName();
        }
        return documentType + StringUtils.SPACE + STRING_DASH + StringUtils.SPACE
            + sanitizePartyName(claimantFirstName)
            + StringUtils.SPACE
            + sanitizePartyName(claimantLastName)
            + (ENGLISH_LANGUAGE.equals(documentLanguage)
            ? ""
            : StringUtils.SPACE + STRING_DASH + StringUtils.SPACE + documentLanguage)
            + ".pdf";
    }

    private static String createPdfDocumentNameFromCaseDataAndAcasCertificate(CaseData caseData,
                                                                              AcasCertificate acasCertificate) {
        Optional<RespondentSumTypeItem> respondent = caseData.getRespondentCollection().stream()
            .filter(r -> acasCertificate.getCertificateNumber().equals(
                defaultIfEmpty(r.getValue().getRespondentAcas(), "")))
            .findFirst();
        String acasName = "";
        if (respondent.isPresent()) {
            acasName = sanitizePartyName(respondent.get().getValue().getRespondentName()) + " - ";
        }

        return "ACAS Certificate - "
            + acasName
            + acasCertificate.getCertificateNumber().replace("/", "_");
    }

    public static String createPdfDocumentDescriptionFromCaseData(CaseData caseData) {
        return "ET1 - "
            + caseData.getClaimantIndType().getClaimantFirstNames()
            + " " + caseData.getClaimantIndType().getClaimantLastName();
    }

    /**
     * Converts case data to a pdf byte array wrapped in a {@link PdfDecodedMultipartFile} Object.
     *
     * @param caseData The case data to be converted into a pdf file wrapped in a {@link CaseData}
     * @param userInfo a {@link UserInfo} used username as a backup if no name in case
     * @return a list of {@link PdfDecodedMultipartFile} which contains the pdf values
     */
    public List<PdfDecodedMultipartFile> convertCaseDataToPdfDecodedMultipartFile(CaseData caseData, UserInfo
        userInfo) {
        List<PdfDecodedMultipartFile> files = new ArrayList<>();
        try {
            byte[] pdfData = pdfService.convertCaseToPdf(caseData, this.englishPdfTemplateSource, PDF_TYPE_ET1,
                                                         CLIENT_TYPE_CLAIMANT, SUBMIT_ET1_CITIZEN);
            if (ObjectUtils.isEmpty(pdfData)) {
                throw new PdfServiceException(
                    "Failed to convert to PDF. English Template Not Found",
                    new NullPointerException()
                );
            }
            files.add(new PdfDecodedMultipartFile(
                pdfData,
                createPdfDocumentNameFromCaseData(caseData, ENGLISH_LANGUAGE, userInfo, ET1),
                PDF_FILE_TIKA_CONTENT_TYPE,
                createPdfDocumentDescriptionFromCaseData(caseData)
            ));
        } catch (PdfServiceException e) {
            GenericServiceUtil.logException("Case English PDF file could not be created for case: ",
                                            caseData.getEthosCaseReference(), e.getMessage(),
                                            this.getClass().getName(), "convertCaseDataToPdfDecodedMultipartFile");
        }
        try {
            if (WELSH_LANGUAGE.equals(GenericServiceUtil.findClaimantLanguage(caseData))) {
                // New parameter CLIENT_TYPE_RESPONDENT not has any effect to the flow of ET1 pdf creation.
                // It is used to discriminate representatives and respondents while mapping ET3 PDF data.
                // SUBMIT_ET1_CITIZEN does not have any effect to the flow of ET1 pdf creation just checks if
                // the event is submission event not. If submit event, sets date received field of pdf file to the
                // local date current value. This is because, on caseworker screens we create PDF files without
                // submission of the event.
                byte[] pdfData = pdfService.convertCaseToPdf(caseData, this.welshPdfTemplateSource, PDF_TYPE_ET1,
                                                             CLIENT_TYPE_CLAIMANT, SUBMIT_ET1_CITIZEN);
                if (ObjectUtils.isEmpty(pdfData)) {
                    throw new PdfServiceException("Failed to convert to PDF. Welsh Template Not Found",
                                                  new NullPointerException());
                }
                files.add(new PdfDecodedMultipartFile(
                    pdfData,
                    createPdfDocumentNameFromCaseData(caseData, WELSH_LANGUAGE, userInfo, ET1),
                    PDF_FILE_TIKA_CONTENT_TYPE,
                    createPdfDocumentDescriptionFromCaseData(caseData)
                ));
            }
        } catch (PdfServiceException e) {
            GenericServiceUtil.logException("Case Welsh PDF file could not be created for case: ",
                                            caseData.getEthosCaseReference(), e.getMessage(),
                                            this.getClass().getName(), "convertCaseDataToPdfDecodedMultipartFile");
        }
        return files;
    }

    /**
     * Converts a list of {@link AcasCertificate} into a list of pdf files.
     *
     * @param caseData         case data wrapped in {@link CaseData} used when creating pdf name
     * @param acasCertificates certificates as a {@link AcasCertificate} to be converted into pdf files
     * @return a list of pdf files wrapped in {@link PdfDecodedMultipartFile}
     */
    public List<PdfDecodedMultipartFile> convertAcasCertificatesToPdfDecodedMultipartFiles(
        CaseData caseData, List<AcasCertificate> acasCertificates) {
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles = new ArrayList<>();
        for (AcasCertificate acasCertificate : acasCertificates) {
            if (!NOT_FOUND.equals(acasCertificate.getCertificateDocument())) {
                byte[] pdfData = Base64.getDecoder().decode(acasCertificate.getCertificateDocument());
                String docName = createPdfDocumentNameFromCaseDataAndAcasCertificate(
                    caseData,
                    acasCertificate
                );
                pdfDecodedMultipartFiles.add(new PdfDecodedMultipartFile(
                    pdfData,
                    docName + ".pdf",
                    PDF_FILE_TIKA_CONTENT_TYPE,
                    docName
                ));
            }
        }
        return pdfDecodedMultipartFiles;
    }

    /**
     * Converts a given object of type {@link ClaimantTse} to a {@link PdfDecodedMultipartFile}.
     * Firstly by converting to a pdf byte array and then wrapping within the return object.
     *
     * @param claimantTse the {@link ClaimantTse} object to be converted.
     * @param caseReference used to TseApplication from ClaimantTse
     * @param docName used for the name of the pdf document to be generated
     * @return {@link PdfDecodedMultipartFile} with the claimant tse CYA page in pdf format.
     * @throws DocumentGenerationException if there is an error generating the PDF.
     */
    public PdfDecodedMultipartFile convertClaimantTseIntoMultipartFile(
        ClaimantTse claimantTse,
        String caseReference,
        String docName)

        throws DocumentGenerationException {

        GenericTseApplication genericTseApplication =
            ClaimantTseUtil.getGenericTseApplicationFromClaimantTse(claimantTse, caseReference);

        byte[] tseApplicationPdf = documentGenerationService.genPdfDocument(
            contactTheTribunalPdfTemplate,
            docName,
            genericTseApplication
        );

        return new PdfDecodedMultipartFile(
            tseApplicationPdf,
            docName,
            PDF_FILE_TIKA_CONTENT_TYPE,
            TSE_FILENAME + APP_TYPE_MAP.get(claimantTse.getContactApplicationType())
        );
    }

    /**
     * Converts a given object of type {@link RespondToApplicationRequest} to a {@link PdfDecodedMultipartFile}.
     * Firstly by converting to a pdf byte array and then wrapping within the return object.
     *
     * @param request {@link RespondToApplicationRequest} object that contains the data to be converted
     * @return {@link PdfDecodedMultipartFile} with the claimant response CYA page in pdf format.
     * @throws DocumentGenerationException if there is an error generating the PDF.
     */
    public PdfDecodedMultipartFile convertApplicationResponseIntoMultipartFile(RespondToApplicationRequest request,
                                                                               String description,
                                                                               String ethosCaseReference,
                                                                               GenericTseApplicationType application,
                                                                               String respondingUserType)
        throws DocumentGenerationException {

        String documentName = "Application %s  - %s - Claimant Response.pdf".formatted(
            application.getNumber(),
            application.getType()
        );

        return new PdfDecodedMultipartFile(
            convertClaimantResponseToPdf(request, ethosCaseReference, description, documentName, respondingUserType),
            documentName,
            PDF_FILE_TIKA_CONTENT_TYPE,
            description
        );
    }

    private byte[] convertClaimantResponseToPdf(RespondToApplicationRequest request,
                                                String ethosCaseReference,
                                                String appTypeDescription,
                                                String documentName,
                                                String respondingUserType)
        throws DocumentGenerationException {
        TseRespondType applicationResponse = request.getResponse();
        String fileName = applicationResponse.getSupportingMaterial() != null
            ? request.getSupportingMaterialFile().getDocumentFilename() : null;

        AppResponseCitizen claimantResponseCya = AppResponseCitizen.builder()
            .caseNumber(ethosCaseReference)
            .applicant(respondingUserType)
            .applicationType(appTypeDescription)
            .applicationDate(UtilHelper.formatCurrentDate(LocalDate.now()))
            .response(applicationResponse.getResponse())
            .fileName(fileName)
            .copyToOtherPartyYesOrNo(applicationResponse.getCopyToOtherParty())
            .build();

        return documentGenerationService.genPdfDocument(
            claimantResponsePdfTemplate,
            documentName,
            claimantResponseCya
        );
    }

    protected static String sanitizePartyName(String partyName) {
        if (isNullOrEmpty(partyName)) {
            return "";
        }

        String sanitizedName = partyName;
        for (String charToReplace : DOCUMENT_CHARS_TO_REPLACE) {
            sanitizedName = sanitizedName.replace(charToReplace, " ");
        }
        return sanitizedName;
    }

    /**
     * Converts a given object of type {@link RespondentTse} to a {@link PdfDecodedMultipartFile}.
     * Firstly by converting to a pdf byte array and then wrapping within the return object.
     *
     * @param respondentTse the {@link RespondentTse} object to be converted.
     * @param caseReference the caseReference used to identify TseApplication from RespondentTse.
     * @param docName the name to be used for the pdf document to be generated.
     * @return {@link PdfDecodedMultipartFile} with the claimant tse CYA page in pdf format.
     * @throws DocumentGenerationException if there is an error generating the PDF.
     */
    public PdfDecodedMultipartFile convertRespondentTseIntoMultipartFile(
        RespondentTse respondentTse,
        String caseReference,
        String docName)

        throws DocumentGenerationException {

        GenericTseApplication genericTseApplication =
            RespondentTseUtil.getGenericTseApplicationFromRespondentTse(respondentTse, caseReference);

        byte[] tseApplicationPdf = documentGenerationService.genPdfDocument(
            contactTheTribunalPdfTemplate,
            docName,
            genericTseApplication
        );

        return new PdfDecodedMultipartFile(
            tseApplicationPdf,
            docName,
            PDF_FILE_TIKA_CONTENT_TYPE,
            TSE_FILENAME + APP_TYPE_MAP.get(respondentTse.getContactApplicationType())
        );
    }
}
