package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.PseResponseTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.PseResponseType;
import uk.gov.hmcts.et.common.model.ccd.types.SendNotificationTypeItem;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.models.SendNotificationAddResponseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SendNotificationStateUpdateRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.YES;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.CLAIMANT_CORRESPONDENCE_DOCUMENT;

@Service
@RequiredArgsConstructor
public class SendNotificationService {

    private final CaseService caseService;
    private final CaseDocumentService caseDocumentService;
    private final CaseDetailsConverter caseDetailsConverter;
    private static final String VIEWED = "viewed";

    public CaseDetails updateSendNotificationState(String authorization, SendNotificationStateUpdateRequest request) {
        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.UPDATE_NOTIFICATION_STATE
        );

        CaseData caseData = EmployeeObjectMapper
            .mapRequestCaseDataToCaseData(startEventResponse.getCaseDetails().getData());

        List<SendNotificationTypeItem> notifications = caseData.getSendNotificationCollection();
        for (SendNotificationTypeItem item : notifications) {
            if (item.getId().equals(request.getSendNotificationId())) {
                item.getValue().setNotificationState(VIEWED);
                break;
            }
        }

        CaseDataContent content = caseDetailsConverter.caseDataContent(startEventResponse, caseData);

        return caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            content,
            request.getCaseTypeId()
        );
    }

    /**
     * Adds a pseResponse to a notification.
     *
     * @param authorization - authorization
     * @param request - request containing the response, and the notification details
     * @return the associated {@link CaseDetails}
     */
    public CaseDetails addResponseSendNotification(String authorization, SendNotificationAddResponseRequest request) {
        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            request.getCaseId(),
            request.getCaseTypeId(),
            CaseEvent.UPDATE_NOTIFICATION_RESPONSE
        );

        CaseData caseData = EmployeeObjectMapper
            .mapRequestCaseDataToCaseData(startEventResponse.getCaseDetails().getData());
        var sendNotificationTypeItem =
            caseData.getSendNotificationCollection()
                .stream()
                .filter(notification -> notification.getId().equals(request.getSendNotificationId()))
                .findFirst();
        if (sendNotificationTypeItem.isEmpty()) {
            throw new IllegalArgumentException("SendNotification Id is incorrect");
        }
        var pseRespondCollection = sendNotificationTypeItem.get().getValue().getRespondCollection();
        if (pseRespondCollection == null) {
            pseRespondCollection = new ArrayList<>();
        }

        PseResponseType pseResponseType = request.getPseResponseType();
        if (request.getSupportingMaterialFile() != null) {
            DocumentTypeItem documentTypeItem = caseDocumentService.createDocumentTypeItem(
                CLAIMANT_CORRESPONDENCE_DOCUMENT,
                request.getSupportingMaterialFile()
            );
            var documentTypeItems = new ArrayList<DocumentTypeItem>();
            documentTypeItems.add(documentTypeItem);
            pseResponseType.setSupportingMaterial(documentTypeItems);
            pseResponseType.setHasSupportingMaterial(YES);
        } else {
            pseResponseType.setHasSupportingMaterial(NO);
        }
        PseResponseTypeItem pseResponseTypeItem =
            PseResponseTypeItem.builder().id(UUID.randomUUID().toString())
            .value(pseResponseType)
            .build();

        pseRespondCollection.add(pseResponseTypeItem);

        CaseDataContent content = caseDetailsConverter.caseDataContent(startEventResponse, caseData);
        return caseService.submitUpdate(
            authorization,
            request.getCaseId(),
            content,
            request.getCaseTypeId()
        );
    }

}
