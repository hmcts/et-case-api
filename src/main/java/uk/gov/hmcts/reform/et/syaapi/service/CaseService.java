package uk.gov.hmcts.reform.et.syaapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import uk.gov.dwp.regex.InvalidPostcodeException;
import uk.gov.hmcts.ecm.common.helpers.DocumentHelper;
import uk.gov.hmcts.ecm.common.service.PostcodeToOfficeService;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.JurCodesTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.JurisdictionCodesMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.pdf.PdfUploadService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.GenericServiceUtil;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.SUBMITTED_STATE;
import static uk.gov.hmcts.ecm.common.model.helper.TribunalOffice.getCaseTypeId;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.APP_TYPE_MAP;
import static uk.gov.hmcts.reform.et.syaapi.constants.DocumentCategoryConstants.CASE_MANAGEMENT_DOC_CATEGORY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.CLAIMANT_CORRESPONDENCE_DOCUMENT;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.DEFAULT_TRIBUNAL_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.JURISDICTION_ID;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SCOTLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.UNASSIGNED_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.INITIATE_CASE_DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.SUBMIT_CASE_DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.UPDATE_CASE_SUBMITTED;
/**
 * Provides read and write access to cases stored by ET.
 */

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
public class CaseService {

    public static final String DOCUMENT_COLLECTION = "documentCollection";
    private final AuthTokenGenerator authTokenGenerator;
    private final CoreCaseDataApi ccdApiClient;
    private final IdamClient idamClient;
    private final PostcodeToOfficeService postcodeToOfficeService;
    private final AcasService acasService;
    private final CaseDocumentService caseDocumentService;
    private final NotificationService notificationService;
    private final PdfUploadService pdfUploadService;
    private final JurisdictionCodesMapper jurisdictionCodesMapper;
    private final CaseOfficeService caseOfficeService;
    private static final String ALL_CASES_QUERY = "{\"size\":10000,\"query\":{\"match_all\": {}}}";
    private final FeatureToggleService featureToggleService;

    /**
     * Given a user derived from the authorisation token in the request,
     * this will get all cases {@link CaseDetails} for that user.
     *
     * @param authorization is used to get the {@link UserInfo} for the request
     * @return the associated {@link CaseDetails} list for the authorization code provided
     */
    // @Retryable({FeignException.class, RuntimeException.class}) --> No need to give exception classes as Retryable
    // covers all runtime exceptions.
    @Retryable
    protected List<CaseDetails> getAllUserCases(String authorization) {
        // Elasticsearch
        List<CaseDetails> scotlandCases = Optional.ofNullable(ccdApiClient.searchCases(
            authorization,
            authTokenGenerator.generate(),
            SCOTLAND_CASE_TYPE,
            ALL_CASES_QUERY).getCases()).orElse(Collections.emptyList());

        // Elasticsearch
        List<CaseDetails> englandCases = Optional.ofNullable(ccdApiClient.searchCases(
            authorization,
            authTokenGenerator.generate(),
            ENGLAND_CASE_TYPE,
            ALL_CASES_QUERY).getCases()).orElse(Collections.emptyList());
        return Stream.of(scotlandCases, englandCases)
            .flatMap(Collection::stream).toList();
    }

    /**
     * Given a caseID, this will retrieve the correct {@link CaseDetails}.
     *
     * @param authorization is used to find the {@link UserInfo} for request
     * @param caseRequest   case data for request
     * @return the associated {@link CaseDetails} if the case is created
     */
    @Retryable
    public CaseDetails createCase(String authorization,
                                  CaseRequest caseRequest) {
        String s2sToken = authTokenGenerator.generate();
        String userId = idamClient.getUserInfo(authorization).getUid();
        String eventTypeName = INITIATE_CASE_DRAFT.name();
        String caseType = getCaseType(caseRequest);
        Et1CaseData data = new EmployeeObjectMapper().getEmploymentCaseData(caseRequest.getCaseData());
        StartEventResponse ccdCase = ccdApiClient.startForCitizen(
            authorization,
            s2sToken,
            userId,
            JURISDICTION_ID,
            caseType,
            eventTypeName
        );

        CaseDataContent caseDataContent = CaseDataContent.builder()
            .event(Event.builder().id(eventTypeName).build())
            .eventToken(ccdCase.getToken())
            .data(data)
            .build();

        return ccdApiClient.submitForCitizen(
            authorization,
            s2sToken,
            userId,
            JURISDICTION_ID,
            caseType,
            true,
            caseDataContent
        );
    }

    private String getCaseType(CaseRequest caseRequest) {
        try {
            if (!isNullOrEmpty(caseRequest.getCaseTypeId())) {
                return caseRequest.getCaseTypeId();
            }
            return getCaseTypeId(
                postcodeToOfficeService.getTribunalOfficeFromPostcode(caseRequest.getPostCode())
                    .orElse(DEFAULT_TRIBUNAL_OFFICE).getOfficeName());
        } catch (InvalidPostcodeException e) {
            log.info("Failed to find tribunal office : {} ", e.getMessage());
            return getCaseTypeId(DEFAULT_TRIBUNAL_OFFICE.getOfficeName());
        }
    }

    /**
     * Will accept a {@link CaseRequest} trigger an event to update a give case in ET.
     *
     * @param authorization jwt of the user
     * @param caseRequest   case to be updated
     * @return the newly updated case wrapped in a {@link CaseDetails} object.
     */
    public CaseDetails updateCase(String authorization,
                                  CaseRequest caseRequest) {
        return triggerEvent(authorization, caseRequest.getCaseId(), CaseEvent.UPDATE_CASE_DRAFT,
                            caseRequest.getCaseTypeId(), caseRequest.getCaseData()
        );
    }

    /**
     * Given Case Request, triggers submit case events for the case. Before submitting case events
     * sets managing office (tribunal office), created PDF file for the case and saves PDF file.
     *
     * @param authorization is used to seek the {UserInfo} for request
     * @param caseRequest   is used to provide the caseId, caseTypeId and {@link CaseData} in JSON Format
     * @return the associated {@link CaseData} if the case is submitted
     */
    public CaseDetails submitCase(String authorization, CaseRequest caseRequest) {
        // Assigning local office to case data
        CaseData caseData = caseOfficeService.convertCaseRequestToCaseDataWithTribunalOffice(caseRequest);
        // Getting user info from IDAM
        UserInfo userInfo = idamClient.getUserInfo(authorization);
        // Submitting the case to CCD, receiving caseDetails and setting ethosCaseReference,
        // receiptDate, feeGroupReference with the received details.
        CaseDetails caseDetails = triggerEventForSubmitCase(authorization, caseRequest);

        if (!featureToggleService.citizenEt1Generation()) {
            log.info("Citizen ET1 generation feature is disabled");
            setCaseDataWithSubmittedCaseDetails(caseDetails, caseData);
            // Create case pdf file(s). If the user selected language is Welsh, we also create Welsh pdf file
            // and add it to our pdf files list
            List<PdfDecodedMultipartFile> casePdfFiles =
                pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseData, userInfo);
            // Submit e-mail to the user with attached ET1 pdf file according to selected contact language
            // (Welsh or English)
            notificationService.sendSubmitCaseConfirmationEmail(caseRequest, caseData, userInfo, casePdfFiles);
            // Creating acas certificates for each respondent
            List<PdfDecodedMultipartFile> acasCertificates =
                pdfUploadService.convertAcasCertificatesToPdfDecodedMultipartFiles(
                    caseData, acasService.getAcasCertificatesByCaseData(caseData)
            );
            // Uploading all documents to document store
            List<DocumentTypeItem> documentList = uploadAllDocuments(authorization,
                                                                     caseRequest,
                                                                     caseData,
                                                                     casePdfFiles,
                                                                     acasCertificates
            );

            caseDetails.getData().put(DOCUMENT_COLLECTION, documentList);

            triggerEvent(authorization,
                         caseRequest.getCaseId(),
                         UPDATE_CASE_SUBMITTED,
                         caseDetails.getCaseTypeId(),
                         caseDetails.getData()
            );
        }
        return caseDetails;
    }

    private List<DocumentTypeItem> uploadAllDocuments(String authorization,
                                                      CaseRequest caseRequest,
                                                      CaseData caseData,
                                                      List<PdfDecodedMultipartFile> casePdfFiles,
                                                      List<PdfDecodedMultipartFile> acasCertificates) {
        List<DocumentTypeItem> documentList = new ArrayList<>();
        try {
            documentList.addAll(caseDocumentService
                                    .uploadAllDocuments(authorization, caseRequest.getCaseTypeId(),
                                                        casePdfFiles, acasCertificates, caseData));
        } catch (CaseDocumentException cde) {
            // Send upload error alert email to shared inbox
            notificationService.sendDocUploadErrorEmail(caseRequest, casePdfFiles, acasCertificates,
                                                        caseData.getClaimantRequests().getClaimDescriptionDocument());
            GenericServiceUtil.logException("Case Documents Upload error - Failed to complete case documents upload",
                                            caseData.getEthosCaseReference(), cde.getMessage(),
                                            this.getClass().getName(), "submitCase");
        }
        return documentList;
    }

    private static void setCaseDataWithSubmittedCaseDetails(CaseDetails caseDetails, CaseData caseData) {
        caseData.setEthosCaseReference(caseDetails.getData().get("ethosCaseReference") == null ? "" :
                                           caseDetails.getData().get("ethosCaseReference").toString());
        caseData.setReceiptDate(caseDetails.getData().get("receiptDate") == null ? "" :
                                    caseDetails.getData().get("receiptDate").toString());
        caseData.setFeeGroupReference(caseDetails.getData().get("feeGroupReference") == null ? "" :
                                          caseDetails.getData().get("feeGroupReference").toString());
    }

    /**
     * Given a caseId, initialization of trigger event to start and submit update for case.
     *
     * @param authorization is used to seek the {@link UserInfo} for request
     * @param caseId        used to retrieve get case details
     * @param caseType      is used to determine if the case is for ET_EnglandWales or ET_Scotland
     * @param eventName     is used to determine INITIATE_CASE_DRAFT or UPDATE_CASE_DRAFT
     * @param caseData      is used to provide the {@link Et1CaseData} in json format
     * @return the associated {@link CaseData} if the case is updated
     */
    public CaseDetails triggerEvent(String authorization, String caseId, CaseEvent eventName,
                                    String caseType, Map<String, Object> caseData) {
        ObjectMapper objectMapper = new ObjectMapper();
        CaseDetailsConverter caseDetailsConverter = new CaseDetailsConverter(objectMapper);
        StartEventResponse startEventResponse = startUpdate(authorization, caseId, caseType, eventName);
        CaseData caseData1 = EmployeeObjectMapper.convertCaseDataMapToCaseDataObject(caseData);

        if (SUBMIT_CASE_DRAFT == eventName) {
            enrichCaseDataWithJurisdictionCodes(caseData1);
        }

        return submitUpdate(
            authorization,
            caseId,
            caseDetailsConverter.et1ToCaseDataContent(startEventResponse, caseData1),
            caseType
        );
    }

    /**
     * Given a caseId, submit the case into ECM.
     *
     * @param authorization is used to seek the {@link UserInfo} for request
     * @param caseRequest   is used to provide the caseId, caseTypeId and {@link CaseData} in JSON Format
     * @return the associated {@link CaseData} on submission
     */
    public CaseDetails triggerEventForSubmitCase(String authorization, CaseRequest caseRequest) {
        StartEventResponse startEventResponse = startUpdate(authorization, caseRequest.getCaseId(),
                                                            caseRequest.getCaseTypeId(), SUBMIT_CASE_DRAFT
        );

        //Remove case time to live to avoid case being deleted
        startEventResponse.getCaseDetails().getData().put("TTL", new HashMap<>());
        CaseData caseData1 = EmployeeObjectMapper.convertCaseDataMapToCaseDataObject(
            startEventResponse.getCaseDetails().getData());
        enrichCaseDataWithJurisdictionCodes(caseData1);
        caseData1.setManagingOffice(caseRequest.getCaseData().get("managingOffice") == null ? UNASSIGNED_OFFICE :
                                        caseRequest.getCaseData().get("managingOffice").toString());
        caseData1.setReceiptDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        caseData1.setFeeGroupReference(caseRequest.getCaseId());
        caseData1.setPositionType("ET1 Online submission");
        caseData1.setClaimantPcqId(caseRequest.getCaseData().get("claimantPcqId") == null ? "" :
                                      caseRequest.getCaseData().get("claimantPcqId").toString());
        caseData1.setEt1OnlineSubmission(YES);

        ObjectMapper objectMapper = new ObjectMapper();
        CaseDetailsConverter caseDetailsConverter = new CaseDetailsConverter(objectMapper);
        try {
            return submitUpdate(
                authorization,
                caseRequest.getCaseId(),
                caseDetailsConverter.et1ToCaseDataContent(startEventResponse, caseData1),
                caseRequest.getCaseTypeId()
            );
        } catch (Exception e) {
            // In case it has submitted but CCD has failed for some reason
            CaseDetails ccdCaseDetails = ccdApiClient.getCase(authorization, authTokenGenerator.generate(),
                                                             caseRequest.getCaseId());
            if (SUBMITTED_STATE.equals(ccdCaseDetails.getState())) {
                return ccdCaseDetails;
            } else {
                log.error("Failed to submit case with caseId: {}", caseRequest.getCaseId(), e);
                throw e;
            }
        }
    }

    /**
     * Given a caseId, start update for the case.
     *
     * @param authorization is used to seek the {@link UserInfo} for request
     * @param caseId        used to retrieve get case details
     * @param caseType      is used to determine if the case is for ET_EnglandWales or ET_Scotland
     * @param eventName     is used to determine INITIATE_CASE_DRAFT or UPDATE_CASE_DRAFT
     * @return startEventResponse associated case details updated
     */
    public StartEventResponse startUpdate(String authorization, String caseId,
                                          String caseType, CaseEvent eventName) {
        String s2sToken = authTokenGenerator.generate();
        UserInfo userInfo = idamClient.getUserInfo(authorization);

        return ccdApiClient.startEventForCitizen(
            authorization,
            s2sToken,
            userInfo.getUid(),
            JURISDICTION_ID,
            caseType,
            caseId,
            eventName.name()
        );
    }

    /**
     * Given a caseId, submit update for the case.
     *
     * @param authorization   is used to seek the {@link UserInfo} for request
     * @param caseId          used to retrieve get case details
     * @param caseDataContent provides overall content of the case
     * @param caseType        is used to determine if the case is for ET_EnglandWales or ET_Scotland
     */
    public CaseDetails submitUpdate(String authorization, String caseId,
                                    CaseDataContent caseDataContent, String caseType) {
        UserInfo userInfo = idamClient.getUserInfo(authorization);
        String s2sToken = authTokenGenerator.generate();
        return ccdApiClient.submitEventForCitizen(
            authorization,
            s2sToken,
            userInfo.getUid(),
            JURISDICTION_ID,
            caseType,
            caseId,
            true,
            caseDataContent
        );
    }

    private void enrichCaseDataWithJurisdictionCodes(CaseData caseData) {
        List<JurCodesTypeItem> jurCodesTypeItems = jurisdictionCodesMapper.mapToJurCodes(caseData);
        caseData.setJurCodesCollection(jurCodesTypeItems);
    }

    void uploadTseSupportingDocument(CaseDetails caseDetails, UploadedDocumentType contactApplicationFile,
                                     String contactApplicationType) {
        CaseData caseData = EmployeeObjectMapper.convertCaseDataMapToCaseDataObject(caseDetails.getData());
        List<DocumentTypeItem> docList = caseData.getDocumentCollection();

        if (docList == null) {
            docList = new ArrayList<>();
        }
        String extension = FilenameUtils.getExtension(contactApplicationFile.getDocumentFilename());
        String docName = "Application %d - %s - Attachment.%s".formatted(
            ApplicationService.getNextApplicationNumber(caseData),
            APP_TYPE_MAP.get(contactApplicationType),
            extension);
        String applicationDocMapping =
            DocumentHelper.claimantApplicationTypeToDocType(contactApplicationType);
        String topLevel = DocumentHelper.getTopLevelDocument(applicationDocMapping);
        contactApplicationFile.setDocumentFilename(docName);
        DocumentType documentType = DocumentType.builder()
            .topLevelDocuments(topLevel)
            .typeOfDocument(CLAIMANT_CORRESPONDENCE_DOCUMENT)
            .uploadedDocument(contactApplicationFile)
            .shortDescription(APP_TYPE_MAP.get(contactApplicationType))
            .dateOfCorrespondence(LocalDate.now().toString())
            .build();
        DocumentHelper.setSecondLevelDocumentFromType(documentType, applicationDocMapping);
        DocumentHelper.setDocumentTypeForDocument(documentType);

        docList.add(DocumentTypeItem.builder()
                        .id(UUID.randomUUID().toString())
                        .value(documentType)
                        .build());
        caseDetails.getData().put(DOCUMENT_COLLECTION, docList);
    }

    void uploadTseCyaAsPdf(
        String authorization,
        CaseDetails caseDetails,
        ClaimantTse claimantTse,
        String caseType
    ) throws DocumentGenerationException, CaseDocumentException {

        CaseData caseData = EmployeeObjectMapper.convertCaseDataMapToCaseDataObject(caseDetails.getData());
        List<DocumentTypeItem> docList = caseData.getDocumentCollection();

        if (docList == null) {
            docList = new ArrayList<>();
        }

        String docName = "Application %d - %s.pdf".formatted(
            ApplicationService.getNextApplicationNumber(caseData),
            ClaimantTse.APP_TYPE_MAP.get(claimantTse.getContactApplicationType())
                .replace("/", " or "));
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertClaimantTseIntoMultipartFile(claimantTse,
                                                                 caseData.getEthosCaseReference(),
                                                                 docName);
        String applicationDocMapping =
            DocumentHelper.claimantApplicationTypeToDocType(claimantTse.getContactApplicationType());
        String topLevel = DocumentHelper.getTopLevelDocument(applicationDocMapping);

        docList.add(caseDocumentService.createDocumentTypeItemLevels(
            authorization,
            caseType,
            topLevel,
            applicationDocMapping,
            CASE_MANAGEMENT_DOC_CATEGORY,
            pdfDecodedMultipartFile
        ));

        caseDetails.getData().put(DOCUMENT_COLLECTION, docList);
    }

    void createResponsePdf(String authorization,
                           CaseData caseData,
                           RespondToApplicationRequest request,
                           String appType)
        throws DocumentGenerationException, CaseDocumentException {
        String description = "Response to " + appType;
        GenericTseApplicationType application = TseApplicationHelper.getSelectedApplication(
                caseData.getGenericTseApplicationCollection(),
                request.getApplicationId())
            .getValue();

        PdfDecodedMultipartFile multipartResponsePdf =
            pdfUploadService.convertClaimantResponseIntoMultipartFile(request,
                                                                      description,
                                                                      caseData.getEthosCaseReference(),
                                                                      application);

        String applicationDoc = TseApplicationHelper.getApplicationDoc(application);
        String topLevel = DocumentHelper.getTopLevelDocument(applicationDoc);
        DocumentTypeItem responsePdf = caseDocumentService.createDocumentTypeItemLevels(
            authorization,
            request.getCaseTypeId(),
            topLevel,
            applicationDoc,
            CASE_MANAGEMENT_DOC_CATEGORY,
            multipartResponsePdf
        );

        if (CollectionUtils.isEmpty(caseData.getDocumentCollection())) {
            caseData.setDocumentCollection(new ArrayList<>());
        }
        caseData.getDocumentCollection().add(responsePdf);
    }

    /**
     * Will accept a {@link CaseRequest} trigger an event to update a submitted case in ET.
     *
     * @param authorization jwt of the user
     * @param caseRequest   case to be updated
     * @return the newly updated case wrapped in a {@link CaseDetails} object.
     */
    public CaseDetails updateCaseSubmitted(String authorization,
                                           CaseRequest caseRequest) {
        return triggerEvent(authorization, caseRequest.getCaseId(), UPDATE_CASE_SUBMITTED,
                            caseRequest.getCaseTypeId(), caseRequest.getCaseData()
        );
    }
}
