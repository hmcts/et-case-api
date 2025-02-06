package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantIndType;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.IN_PROGRESS;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper.getRespondentNames;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.setRespondentApplicationWithResponse;

@RequiredArgsConstructor
@Service
@Slf4j
public class RespondToApplicationService {

    private final CaseService caseService;
    private final NotificationService notificationService;
    private final CaseDocumentService caseDocumentService;
    private final CaseDetailsConverter caseDetailsConverter;
    private final FeatureToggleService featureToggleService;

    /**
     * Submit Claimant Response to Respondent's request to Tell Something Else.
     *
     * @param authorization - authorization
     * @param request - response from the claimant
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails respondToApplication(String authorization, RespondToApplicationRequest request) {
        String caseId = request.getCaseId();
        String caseTypeId = request.getCaseTypeId();

        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            caseId,
            caseTypeId,
            CaseEvent.CLAIMANT_TSE_RESPOND
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        GenericTseApplicationTypeItem appToModify = TseApplicationHelper.getSelectedApplication(
            caseData.getGenericTseApplicationCollection(), request.getApplicationId()
        );

        if (appToModify == null) {
            throw new IllegalArgumentException("Application id provided is incorrect");
        }

        String copyToOtherParty = request.getResponse().getCopyToOtherParty();
        GenericTseApplicationType appType = appToModify.getValue();

        boolean isRespondingToTribunal = request.isRespondingToRequestOrOrder();
        if (isRespondingToTribunal) {
            appType.setApplicationState(IN_PROGRESS);
            appType.setClaimantResponseRequired(NO);
        }

        sendResponseToApplicationEmails(appType, caseData, caseId, copyToOtherParty, isRespondingToTribunal);

        boolean waEnabled = featureToggleService.isWorkAllocationEnabled();
        setRespondentApplicationWithResponse(request, appType, caseData, caseDocumentService, waEnabled);

        createAndAddPdfOfResponse(authorization, request, caseData, appType);

        return caseService.submitUpdate(
            authorization, caseId, caseDetailsConverter.caseDataContent(startEventResponse, caseData), caseTypeId);
    }

    private void sendResponseToApplicationEmails(
        GenericTseApplicationType application,
        CaseData caseData,
        String caseId,
        String copyToOtherParty,
        boolean isRespondingToRequestOrOrder
    ) {
        ClaimantIndType claimantIndType = caseData.getClaimantIndType();

        NotificationService.CoreEmailDetails details = new NotificationService.CoreEmailDetails(
            caseData,
            claimantIndType.getClaimantFirstNames() + " " + claimantIndType.getClaimantLastName(),
            caseData.getEthosCaseReference(),
            getRespondentNames(caseData),
            NotificationsHelper.getNearestHearingToReferral(caseData, "Not set"),
            caseId
        );
        String type = application.getType();

        notificationService.sendResponseEmailToTribunal(details, type, isRespondingToRequestOrOrder);
        notificationService.sendResponseEmailToClaimant(details, type, copyToOtherParty, isRespondingToRequestOrOrder);

        if (isRespondingToRequestOrOrder) {
            notificationService.sendReplyEmailToRespondent(
                caseData,
                caseData.getEthosCaseReference(),
                caseId,
                copyToOtherParty
            );
        } else {
            notificationService.sendResponseEmailToRespondent(details, type, copyToOtherParty);
        }
    }

    private void createAndAddPdfOfResponse(
        String authorization,
        RespondToApplicationRequest request,
        CaseData caseData,
        GenericTseApplicationType application
    ) {
        if (YES.equals(request.getResponse().getCopyToOtherParty())) {
            try {
                log.info("Uploading pdf of claimant response to application");
                caseService.createResponsePdf(
                    authorization,
                    caseData,
                    request,
                    application.getType()
                );
            } catch (CaseDocumentException | DocumentGenerationException e) {
                logTseApplicationDocumentUploadError(e);
            }
        }
    }

    private static void logTseApplicationDocumentUploadError(Exception exception) {
        log.error("Couldn't upload pdf of TSE application {}", exception.getMessage());
    }
}
