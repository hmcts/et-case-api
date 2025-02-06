package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.TseRespondTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantIndType;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.NotificationService.CoreEmailDetails;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.util.List;
import java.util.UUID;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper.getRespondentNames;

@RequiredArgsConstructor
@Service
@Slf4j
public class ApplicationService {
    public static final String WEEKS_78 = "78 weeks";

    private static final String TSE_FILENAME = "Contact the tribunal";

    private final CaseService caseService;
    private final NotificationService notificationService;
    private final CaseDocumentService caseDocumentService;
    private final CaseDetailsConverter caseDetailsConverter;

    /**
     * Get the next application number for the case.
     * @param caseData - case data
     * @return the next application number
     */
    public static int getNextApplicationNumber(CaseData caseData) {
        if (isEmpty(caseData.getGenericTseApplicationCollection())) {
            return 1;
        }
        return caseData.getGenericTseApplicationCollection().size() + 1;
    }

    /**
     * Submit Claimant Application to Tell Something Else.
     *
     * @param authorization - authorization
     * @param request - application request from the claimant
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails submitApplication(String authorization, ClaimantApplicationRequest request)
        throws NotificationClientException {

        String caseTypeId = request.getCaseTypeId();

        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.SUBMIT_CLAIMANT_TSE
        );

        CaseDetails caseDetails = startEventResponse.getCaseDetails();
        ClaimantTse claimantTse = request.getClaimantTse();
        caseDetails.getData().put("claimantTse", claimantTse);

        try {
            log.info("Uploading pdf of TSE application");
            caseService.uploadTseCyaAsPdf(authorization, caseDetails, claimantTse, caseTypeId);
        } catch (CaseDocumentException | DocumentGenerationException e) {
            logTseApplicationDocumentUploadError(e);
        }

        UploadedDocumentType contactApplicationFile = claimantTse.getContactApplicationFile();
        if (contactApplicationFile != null) {
            log.info("Uploading supporting file to document collection");
            caseService.uploadTseSupportingDocument(caseDetails, contactApplicationFile,
                                                    claimantTse.getContactApplicationType()
            );
        }

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(caseDetails.getData());
        CaseDataContent content = caseDetailsConverter.caseDataContent(startEventResponse, caseData);

        CaseDetails finalCaseDetails = caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            content,
            request.getCaseTypeId()
        );

        sendAcknowledgementEmails(authorization, request, finalCaseDetails);

        return finalCaseDetails;
    }

    private static void logTseApplicationDocumentUploadError(Exception exception) {
        log.error("Couldn't upload pdf of TSE application {}", exception.getMessage());
    }

    private void sendAcknowledgementEmails(
        String authorization,
        ClaimantApplicationRequest request,
        CaseDetails finalCaseDetails
    ) throws NotificationClientException {
        CaseData caseData = EmployeeObjectMapper.convertCaseDataMapToCaseDataObject(finalCaseDetails.getData());
        ClaimantIndType claimantIndType = caseData.getClaimantIndType();
        String hearingDate = NotificationsHelper.getNearestHearingToReferral(caseData, "Not set");
        CoreEmailDetails details = new CoreEmailDetails(
            caseData,
            claimantIndType.getClaimantFirstNames() + " " + claimantIndType.getClaimantLastName(),
            caseData.getEthosCaseReference(),
            getRespondentNames(caseData),
            hearingDate,
            finalCaseDetails.getId().toString()
        );

        ClaimantTse claimantTse = request.getClaimantTse();
        JSONObject documentJson = getDocumentDownload(authorization, caseData);

        notificationService.sendAcknowledgementEmailToClaimant(details, claimantTse);
        notificationService.sendAcknowledgementEmailToRespondents(details, documentJson, claimantTse);
        notificationService.sendAcknowledgementEmailToTribunal(details, claimantTse.getContactApplicationType());
    }

    private JSONObject getDocumentDownload(String authorization, CaseData caseData) throws NotificationClientException {
        List<DocumentTypeItem> tseFiles = caseData.getDocumentCollection().stream()
            .filter(n -> defaultIfEmpty(n.getValue().getShortDescription(), "").startsWith(TSE_FILENAME))
            .toList();

        if (tseFiles.isEmpty()) {
            return null;
        }

        String documentUrl = tseFiles.get(tseFiles.size() - 1).getValue().getUploadedDocument().getDocumentUrl();
        String docId = documentUrl.substring(documentUrl.lastIndexOf('/') + 1);

        log.info("Downloading pdf of TSE application");
        ByteArrayResource downloadDocument = caseDocumentService.downloadDocument(
            authorization,
            UUID.fromString(docId)
        ).getBody();

        if (downloadDocument == null) {
            return null;
        }

        return NotificationClient.prepareUpload(
            downloadDocument.getByteArray(),
            false,
            true,
            WEEKS_78
        );
    }

    /**
     * Update application state of Respondent's TSE app to be 'viewed'.
     *
     * @param authorization - authorization
     * @param request - request with application's id
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails changeApplicationStatus(String authorization, ChangeApplicationStatusRequest request) {
        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.UPDATE_APPLICATION_STATE
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        GenericTseApplicationTypeItem appToModify = TseApplicationHelper.getSelectedApplication(
            caseData.getGenericTseApplicationCollection(),
            request.getApplicationId()
        );

        if (appToModify == null) {
            throw new IllegalArgumentException("Application id provided is incorrect");
        }

        appToModify.getValue().setApplicationState(request.getNewStatus());

        return caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            caseDetailsConverter.caseDataContent(startEventResponse, caseData),
            request.getCaseTypeId()
        );
    }

    /**
     * Set admin response as viewed by the claimant.
     *
     * @param authorization - authorization
     * @param request - request with application and response ID
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails updateTribunalResponseAsViewed(String authorization, TribunalResponseViewedRequest request) {
        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.UPDATE_NOTIFICATION_STATE
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        GenericTseApplicationTypeItem selectedApplication = TseApplicationHelper.getSelectedApplication(
            caseData.getGenericTseApplicationCollection(),
            request.getAppId()
        );

        TseRespondTypeItem responseToUpdate = TseApplicationHelper.findResponse(
            selectedApplication,
            request.getResponseId()
        );

        if (responseToUpdate == null) {
            throw new IllegalArgumentException("Response id is invalid");
        }

        responseToUpdate.getValue().setViewedByClaimant(YES);

        return caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            caseDetailsConverter.caseDataContent(startEventResponse, caseData),
            request.getCaseTypeId()
        );
    }
}
