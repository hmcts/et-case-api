package uk.gov.hmcts.reform.et.syaapi.service.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.elasticsearch.common.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.models.AcasCertificate;
import uk.gov.hmcts.reform.et.syaapi.models.GenericTseApplication;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationException;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationService;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLISH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE;

/**
 * Uses {@link PdfMapperService} to convert a given case into a Pdf Document.
 */
@Slf4j
@Service
@RequiredArgsConstructor()
public class PdfService {

    private final PdfMapperService pdfMapperService;
    private final DocumentGenerationService documentGenerationService;
    @Value("${pdf.english}")
    public String englishPdfTemplateSource;
    @Value("${pdf.welsh}")
    public String welshPdfTemplateSource;
    @Value("${pdf.contact_tribunal_template}")
    public String contactTheTribunalPdfTemplate;

    private static final String TSE_CONTACT_THE_TRIBUNAL_FILENAME = "contact_about_something_else.pdf";
    private static final String PDF_FILE_TIKA_CONTENT_TYPE = "application/pdf";
    private static final String NOT_FOUND = "not found";

    /**
     * Converts a {@link CaseData} class object into a pdf document
     * using template (ver. ET1_1122)
     *
     * @param caseData  The data that is to be converted into pdf
     * @param pdfSource The source location of the PDF file to be used as the template
     * @return A byte array that contains the pdf document.
     */
    public byte[] convertCaseToPdf(CaseData caseData, String pdfSource) throws PdfServiceException {
        byte[] pdfDocumentBytes;
        try {
            pdfDocumentBytes = createPdf(caseData, pdfSource);
        } catch (IOException ex) {
            throw new PdfServiceException("Failed to convert to PDF", ex);
        }
        return pdfDocumentBytes;
    }

    private byte[] convertClaimantTseToPdf(CaseData caseData) throws DocumentGenerationException {
        GenericTseApplication genericTseApplication = new GenericTseApplication();
        genericTseApplication.setApplicationType(caseData.getClaimantTse().getContactApplicationType());
        genericTseApplication.setTellOrAskTribunal(caseData.getClaimantTse().getContactApplicationText());
        genericTseApplication.setSupportingEvidence(caseData.getClaimantTse().getContactApplicationFile());
        genericTseApplication.setCopyToOtherPartyYesOrNo(caseData.getResTseCopyToOtherPartyYesOrNo());
        genericTseApplication.setCopyToOtherPartyText(caseData.getResTseCopyToOtherPartyTextArea());

        return documentGenerationService.genPdfDocument(contactTheTribunalPdfTemplate,
            TSE_CONTACT_THE_TRIBUNAL_FILENAME, genericTseApplication);
    }

    /**
     * Populates a pdf document with data stored in the case data parameter.
     * @param caseData {@link CaseData} object with information in which to populate the pdf with
     * @param pdfSource file name of the pdf template used to create the pdf
     * @return a byte array of the generated pdf file.
     * @throws IOException if there is an issue reading the pdf template
     */
    protected byte[] createPdf(CaseData caseData, String pdfSource) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        try (PDDocument pdfDocument = Loader.loadPDF(
            Objects.requireNonNull(cl.getResourceAsStream(pdfSource)))) {
            PDDocumentCatalog pdDocumentCatalog = pdfDocument.getDocumentCatalog();
            PDAcroForm pdfForm = pdDocumentCatalog.getAcroForm();
            for (Map.Entry<String, Optional<String>> entry : this.pdfMapperService.mapHeadersToPdf(caseData)
                .entrySet()) {
                String entryKey = entry.getKey();
                Optional<String> entryValue = entry.getValue();
                if (entryValue.isPresent()) {
                    try {
                        PDField pdfField = pdfForm.getField(entryKey);
                        pdfField.setValue(entryValue.get());
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            pdfDocument.save(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static String createPdfDocumentNameFromCaseData(CaseData caseData,
                                                            String documentLanguage,
                                                            UserInfo userInfo) {
        String claimantFirstName = caseData.getClaimantIndType().getClaimantFirstNames();
        String claimantLastName = caseData.getClaimantIndType().getClaimantLastName();
        if (Strings.isNullOrEmpty(claimantFirstName)) {
            claimantFirstName = userInfo.getGivenName();
        }
        if (Strings.isNullOrEmpty(claimantLastName)) {
            claimantLastName = userInfo.getFamilyName();
        }
        return "ET1_CASE_DOCUMENT_"
            + claimantFirstName.replace(" ", "_")
            + "_"
            + claimantLastName.replace(" ", "_")
            + (ENGLISH_LANGUAGE.equals(documentLanguage) ? "" : "_" + documentLanguage)
            + ".pdf";
    }

    private static String createPdfDocumentNameFromCaseDataAndAcasCertificate(
        CaseData caseData, AcasCertificate acasCertificate) {
        return "ET1_ACAS_CERTIFICATE_"
            + caseData.getClaimantIndType().getClaimantFirstNames().replace(" ", "_")
            + "_"
            + caseData.getClaimantIndType().getClaimantLastName().replace(" ", "_")
            + "_"
            + acasCertificate.getCertificateNumber().replace("/", "_")
            + ".pdf";
    }

    private static String createPdfDocumentDescriptionFromCaseData(CaseData caseData) {
        return "Case Details - "
            + caseData.getClaimantIndType().getClaimantFirstNames()
            + " " + caseData.getClaimantIndType().getClaimantLastName();
    }

    private static String createPdfDocumentDescriptionFromCaseDataAndAcasCertificate(
        CaseData caseData,
        AcasCertificate acasCertificate) {
        return "ACAS Certificate - "
            + caseData.getClaimantIndType().getClaimantFirstNames()
            + " "
            + caseData.getClaimantIndType().getClaimantLastName()
            + " - "
            + acasCertificate.getCertificateNumber();
    }

    /**
     * Converts case data to a pdf byte array wrapped in a {@link PdfDecodedMultipartFile} Object.
     * @param caseData The case data to be converted into a pdf file wrapped in a {@link CaseData}
     * @param userInfo a {@link UserInfo} used user name as a backup if no name in case
     * @return a list of {@link PdfDecodedMultipartFile} which contains the pdf values
     * @throws PdfServiceException when convertCaseToPdf throws an exception
     */
    public List<PdfDecodedMultipartFile> convertCaseDataToPdfDecodedMultipartFile(CaseData caseData, UserInfo userInfo)
        throws PdfServiceException {
        List<PdfDecodedMultipartFile> files = new ArrayList<>();
        files.add(new PdfDecodedMultipartFile(
            convertCaseToPdf(caseData, this.englishPdfTemplateSource),
            createPdfDocumentNameFromCaseData(caseData, ENGLISH_LANGUAGE, userInfo),
            PDF_FILE_TIKA_CONTENT_TYPE,
            createPdfDocumentDescriptionFromCaseData(caseData)
        ));

        if (caseData.getClaimantHearingPreference().getContactLanguage() != null
            && WELSH_LANGUAGE.equals(caseData.getClaimantHearingPreference().getContactLanguage())) {
            files.add(new PdfDecodedMultipartFile(
                convertCaseToPdf(caseData, this.welshPdfTemplateSource),
                createPdfDocumentNameFromCaseData(caseData, WELSH_LANGUAGE, userInfo),
                PDF_FILE_TIKA_CONTENT_TYPE,
                createPdfDocumentDescriptionFromCaseData(caseData)
            ));
        }

        return files;
    }

    /**
     * Converts a list of {@link AcasCertificate} into a list of pdf files.
     * @param caseData case data wrapped in {@link CaseData} used when creating pdf name
     * @param acasCertificates certificates as a {@link AcasCertificate} to be converted into pdf files
     * @return a list of pdf files wrapped in {@link PdfDecodedMultipartFile}
     */
    public List<PdfDecodedMultipartFile> convertAcasCertificatesToPdfDecodedMultipartFiles(
        CaseData caseData, List<AcasCertificate> acasCertificates) {
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles = new ArrayList<>();
        for (AcasCertificate acasCertificate : acasCertificates) {
            if (!NOT_FOUND.equals(acasCertificate.getCertificateDocument())) {
                byte[] pdfData = Base64.getDecoder().decode(acasCertificate.getCertificateDocument());
                pdfDecodedMultipartFiles.add(new PdfDecodedMultipartFile(
                    pdfData,
                    createPdfDocumentNameFromCaseDataAndAcasCertificate(
                        caseData,
                        acasCertificate
                    ),
                    PDF_FILE_TIKA_CONTENT_TYPE,
                    createPdfDocumentDescriptionFromCaseDataAndAcasCertificate(
                        caseData,
                        acasCertificate
                    )
                ));
            }
        }
        return pdfDecodedMultipartFiles;
    }

    /**
     * Converts a given object of type {@link ClaimantTse} to a {@link PdfDecodedMultipartFile}.
     * Firstly by converting to a pdf byte array and then wrapping within the return object.
     * @param caseData {@link CaseData} object that contains the {@link ClaimantTse} object to be converted.
     * @return {@link PdfDecodedMultipartFile} with the claiment tse CYA page in pdf format.
     * @throws DocumentGenerationException if there is an error generating the PDF.
     */
    public PdfDecodedMultipartFile convertClaimantTseIntoMultipartFile(CaseData caseData)
        throws DocumentGenerationException {
        return new PdfDecodedMultipartFile(
            convertClaimantTseToPdf(caseData),
            TSE_CONTACT_THE_TRIBUNAL_FILENAME,
            PDF_FILE_TIKA_CONTENT_TYPE,
            "Test"
        );
    }
}
