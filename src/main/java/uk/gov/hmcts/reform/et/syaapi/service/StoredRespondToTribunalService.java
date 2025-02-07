package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.PseResponseTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.PseResponseType;
import uk.gov.hmcts.et.common.model.ccd.types.RespondNotificationType;
import uk.gov.hmcts.et.common.model.ccd.types.SendNotificationType;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.models.SendNotificationAddResponseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SubmitStoredRespondToTribunalRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.STORED;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.VIEWED;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.YES;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.CLAIMANT_CORRESPONDENCE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;

@RequiredArgsConstructor
@Service
@Slf4j
public class StoredRespondToTribunalService {

    private final CaseService caseService;
    private final CaseDocumentService caseDocumentService;
    private final CaseDetailsConverter caseDetailsConverter;
    private final NotificationService notificationService;
    private final FeatureToggleService featureToggleService;

    private static final String RESPOND_ID_INCORRECT = "Respond id provided is incorrect";
    private static final String SEND_NOTIFICATION_ID_INCORRECT = "SendNotification Id is incorrect";
    private static final String RESPOND_EMPTY = "Respond collection is empty";

    /**
     * Store a pseResponse to a notification.
     *
     * @param authorization - authorization
     * @param request - request containing the response, and the notification details
     * @return the associated {@link CaseDetails}
     */
    public CaseDetails storeResponseSendNotification(String authorization, SendNotificationAddResponseRequest request) {
        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.STORE_PSE_RESPONSE
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        // get sendNotificationTypeItem
        var sendNotificationTypeItem =
            caseData.getSendNotificationCollection()
                .stream()
                .filter(notification -> notification.getId().equals(request.getSendNotificationId()))
                .findFirst();
        if (sendNotificationTypeItem.isEmpty()) {
            throw new IllegalArgumentException(SEND_NOTIFICATION_ID_INCORRECT);
        }

        SendNotificationType sendNotificationType = sendNotificationTypeItem.get().getValue();

        // store PseResponseTypeItem
        if (CollectionUtils.isEmpty(sendNotificationType.getRespondStoredCollection())) {
            sendNotificationTypeItem.get().getValue().setRespondStoredCollection(new ArrayList<>());
        }
        PseResponseType responseToAdd = getPseResponseType(request);
        PseResponseTypeItem pseResponseTypeItem =
            PseResponseTypeItem.builder().id(UUID.randomUUID().toString())
                .value(responseToAdd)
                .build();

        // update sendNotificationType
        if (CollectionUtils.isEmpty(sendNotificationType.getRespondStoredCollection())) {
            sendNotificationType.setRespondStoredCollection(new ArrayList<>());
        }
        sendNotificationType.getRespondStoredCollection().add(pseResponseTypeItem);
        sendNotificationType.setNotificationState(STORED);
        setResponsesAsRespondedTo(sendNotificationType.getRespondNotificationTypeCollection());

        CaseDataContent content = caseDetailsConverter.caseDataContent(startEventResponse, caseData);

        // send email
        NotificationService.CoreEmailDetails details =
            notificationService.formatCoreEmailDetails(caseData, request.getCaseId());
        notificationService.sendStoredEmailToClaimant(
            details,
            request.getPseResponseType().getResponse()
        );

        return caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            content,
            request.getCaseTypeId()
        );
    }

    private PseResponseType getPseResponseType(SendNotificationAddResponseRequest request) {
        PseResponseType pseResponseType = request.getPseResponseType();
        pseResponseType.setDate(TseApplicationHelper.formatCurrentDate(LocalDate.now()));
        pseResponseType.setFrom(CLAIMANT_TITLE);

        if (request.getSupportingMaterialFile() != null) {
            DocumentTypeItem documentTypeItem = caseDocumentService.createDocumentTypeItem(
                CLAIMANT_CORRESPONDENCE,
                request.getSupportingMaterialFile()
            );
            var documentTypeItems = new ArrayList<GenericTypeItem<DocumentType>>();
            documentTypeItems.add(documentTypeItem);
            pseResponseType.setSupportingMaterial(documentTypeItems);
            pseResponseType.setHasSupportingMaterial(YES);
        } else {
            pseResponseType.setHasSupportingMaterial(NO);
        }
        return pseResponseType;
    }

    private void setResponsesAsRespondedTo(List<GenericTypeItem<RespondNotificationType>> responses) {
        if (CollectionUtils.isEmpty(responses)) {
            return;
        }

        for (GenericTypeItem<RespondNotificationType> item : responses) {
            item.getValue().setIsClaimantResponseDue(null);
        }
    }

    /**
     * Submit stored Claimant Response to Tribunal Send Notification.
     *
     * @param authorization - authorization
     * @param request - response from the claimant
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails submitRespondToTribunal(String authorization, SubmitStoredRespondToTribunalRequest request) {
        String caseId = request.getCaseId();
        String caseTypeId = request.getCaseTypeId();

        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            caseId,
            caseTypeId,
            CaseEvent.SUBMIT_STORED_PSE_RESPONSE
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        // get selected SendNotificationType
        var sendNotificationTypeItem =
            caseData.getSendNotificationCollection()
                .stream()
                .filter(notification -> notification.getId().equals(request.getOrderId()))
                .findFirst();
        if (sendNotificationTypeItem.isEmpty()) {
            throw new IllegalArgumentException(SEND_NOTIFICATION_ID_INCORRECT);
        }
        SendNotificationType sendNotificationType = sendNotificationTypeItem.get().getValue();

        // get selected PseResponseTypeItem
        if (CollectionUtils.isEmpty(sendNotificationType.getRespondStoredCollection())) {
            throw new IllegalArgumentException(RESPOND_EMPTY);
        }
        PseResponseTypeItem responseToModify = getResponseInSelectedSendNotification(
            sendNotificationType.getRespondStoredCollection(), request.getStoredRespondId()
        );
        if (responseToModify == null) {
            throw new IllegalArgumentException(RESPOND_ID_INCORRECT);
        }

        // Update response details
        responseToModify.getValue().setDate(TseApplicationHelper.formatCurrentDate(LocalDate.now()));

        NotificationsHelper.updateWorkAllocationFields(
            featureToggleService.isEccEnabled(),
            responseToModify.getValue(),
            sendNotificationType.getSendNotificationSubject());

        // add response
        if (CollectionUtils.isEmpty(sendNotificationType.getRespondCollection())) {
            sendNotificationType.setRespondCollection(new ArrayList<>());
        }
        sendNotificationType.getRespondCollection().add(responseToModify);

        // Update sendNotificationType
        sendNotificationType.setSendNotificationResponsesCount(
            String.valueOf(sendNotificationType.getRespondCollection().size()));
        sendNotificationType.setNotificationState(VIEWED);

        // Remove Stored Response
        sendNotificationType.getRespondStoredCollection()
            .removeIf(item -> item.getId().equals(request.getStoredRespondId()));

        // Send confirmation emails
        sendEmailForRespondToTribunal(caseData, caseId, responseToModify.getValue().getResponse());

        return caseService.submitUpdate(
            authorization, caseId, caseDetailsConverter.caseDataContent(startEventResponse, caseData), caseTypeId);
    }

    private static PseResponseTypeItem getResponseInSelectedSendNotification(List<PseResponseTypeItem> responds,
                                                                             String respondId) {
        return responds.stream()
            .filter(a -> a.getId().equals(respondId))
            .findAny()
            .orElse(null);
    }

    private void sendEmailForRespondToTribunal(CaseData caseData, String caseId, String shortText) {
        notificationService.sendResponseNotificationEmailToTribunal(caseData, caseId);
        notificationService.sendSubmitStoredEmailToClaimant(
            notificationService.formatCoreEmailDetails(caseData, caseId), shortText);
    }
}
