package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.TseRespondTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SubmitStoredRespondToApplicationRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.CLAIMANT_TITLE;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.IN_PROGRESS;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.STORED;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.CLAIMANT_CORRESPONDENCE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.getApplicationDoc;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.getCurrentDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class StoredRespondToApplicationService {

    private final CaseService caseService;
    private final NotificationService notificationService;
    private final CaseDocumentService caseDocumentService;
    private final CaseDetailsConverter caseDetailsConverter;

    private static final String APP_ID_INCORRECT = "Application id provided is incorrect";
    private static final String RESPOND_ID_INCORRECT = "Respond id provided is incorrect";

    /**
     * Store Claimant Response to Application.
     *
     * @param authorization - authorization
     * @param request - response from the claimant
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails storeRespondToApplication(String authorization, RespondToApplicationRequest request) {
        String caseId = request.getCaseId();
        String caseTypeId = request.getCaseTypeId();

        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            caseId,
            caseTypeId,
            CaseEvent.STORE_CLAIMANT_TSE_RESPOND
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        // Update application
        GenericTseApplicationTypeItem appToModify = TseApplicationHelper.getSelectedApplication(
            caseData.getGenericTseApplicationCollection(), request.getApplicationId()
        );
        if (appToModify == null) {
            throw new IllegalArgumentException(APP_ID_INCORRECT);
        }

        GenericTseApplicationType appType = appToModify.getValue();
        appType.setClaimantResponseRequired(NO);
        appType.setApplicationState(STORED);

        // Add response to RespondStoredCollection
        setRespondentApplicationWithResponse(request, appType, caseData, caseDocumentService);

        // Update pdf
        createAndAddPdfOfResponse(authorization, request, caseData, appType);

        // Send email
        NotificationService.CoreEmailDetails details = notificationService.formatCoreEmailDetails(caseData, caseId);
        notificationService.sendStoredEmailToClaimant(details, appType.getType());

        return caseService.submitUpdate(
            authorization, caseId, caseDetailsConverter.caseDataContent(startEventResponse, caseData), caseTypeId);
    }

    private void setRespondentApplicationWithResponse(RespondToApplicationRequest request,
                                                            GenericTseApplicationType appType,
                                                            CaseData caseData,
                                                            CaseDocumentService caseDocumentService) {
        TseRespondType responseToAdd = request.getResponse();
        responseToAdd.setDate(TseApplicationHelper.formatCurrentDate(LocalDate.now()));
        responseToAdd.setFrom(CLAIMANT_TITLE);
        responseToAdd.setDateTime(getCurrentDateTime());
        responseToAdd.setApplicationType(appType.getType());

        // Supporting Material File
        if (request.getSupportingMaterialFile() != null) {
            DocumentTypeItem documentTypeItem = caseDocumentService.createDocumentTypeItem(
                CLAIMANT_CORRESPONDENCE,
                request.getSupportingMaterialFile()
            );
            documentTypeItem.getValue().setShortDescription("Response to " + appType.getType());

            responseToAdd.setSupportingMaterial(new ArrayList<>());
            responseToAdd.getSupportingMaterial().add(documentTypeItem);

            String applicationDoc = getApplicationDoc(appType);
            String extension = FilenameUtils.getExtension(request.getSupportingMaterialFile().getDocumentFilename());
            String docName = "Application %s - %s - Attachment.%s".formatted(
                appType.getNumber(),
                appType.getType(),
                extension
            );
            request.getSupportingMaterialFile().setDocumentFilename(docName);
            documentTypeItem = caseDocumentService.createDocumentTypeItem(applicationDoc,
                                                                          request.getSupportingMaterialFile());

            if (CollectionUtils.isEmpty(caseData.getDocumentCollection())) {
                caseData.setDocumentCollection(new ArrayList<>());
            }
            caseData.getDocumentCollection().add(documentTypeItem);
        }

        // Add to RespondStoredCollection
        if (CollectionUtils.isEmpty(appType.getRespondStoredCollection())) {
            appType.setRespondStoredCollection(new ArrayList<>());
        }
        appType.getRespondStoredCollection().add(TseRespondTypeItem.builder()
                                                   .id(UUID.randomUUID().toString())
                                                   .value(responseToAdd).build());
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
                    application.getType(),
                    CLAIMANT_TITLE
                );
            } catch (CaseDocumentException | DocumentGenerationException e) {
                log.error("Couldn't upload pdf of TSE application " + e.getMessage());
            }
        }
    }

    /**
     * Submit stored claimant response to Tribunal response.
     *
     * @param authorization - authorization
     * @param request - response from the claimant
     * @return the associated {@link CaseDetails} for the ID provided in request
     */
    public CaseDetails submitRespondToApplication(String authorization,
                                                  SubmitStoredRespondToApplicationRequest request) {
        String caseId = request.getCaseId();
        String caseTypeId = request.getCaseTypeId();

        StartEventResponse startEventResponse = caseService.startUpdate(
            authorization,
            caseId,
            caseTypeId,
            CaseEvent.SUBMIT_STORED_CLAIMANT_TSE_RESPOND
        );

        CaseData caseData = EmployeeObjectMapper
            .convertCaseDataMapToCaseDataObject(startEventResponse.getCaseDetails().getData());

        // Get selected GenericTseApplicationTypeItem
        GenericTseApplicationTypeItem appToModify = TseApplicationHelper.getSelectedApplication(
            caseData.getGenericTseApplicationCollection(), request.getApplicationId()
        );
        if (appToModify == null) {
            throw new IllegalArgumentException(APP_ID_INCORRECT);
        }

        // Get selected TseRespondTypeItem
        TseRespondTypeItem responseToAdd = TseApplicationHelper.getResponseInSelectedApplication(
            appToModify.getValue().getRespondStoredCollection(), request.getStoredRespondId()
        );
        if (responseToAdd == null) {
            throw new IllegalArgumentException(RESPOND_ID_INCORRECT);
        }

        // Update response
        responseToAdd.getValue().setDate(TseApplicationHelper.formatCurrentDate(LocalDate.now()));
        responseToAdd.getValue().setDateTime(getCurrentDateTime());

        // Add response to RespondCollection
        GenericTseApplicationType appType = appToModify.getValue();
        if (CollectionUtils.isEmpty(appType.getRespondCollection())) {
            appType.setRespondCollection(new ArrayList<>());
        }
        appType.getRespondCollection().add(responseToAdd);
        appType.setResponsesCount(String.valueOf(appType.getRespondCollection().size()));
        appType.setApplicationState(IN_PROGRESS);

        // Remove Stored Response
        appType.getRespondStoredCollection().removeIf(item -> item.getId().equals(request.getStoredRespondId()));

        // Send confirmation email
        boolean isRespondingToRequestOrOrder = true;
        sendResponseToApplicationEmails(appType, caseData, caseId, isRespondingToRequestOrOrder);

        return caseService.submitUpdate(
            authorization, caseId, caseDetailsConverter.caseDataContent(startEventResponse, caseData), caseTypeId);
    }

    private void sendResponseToApplicationEmails(
        GenericTseApplicationType application,
        CaseData caseData,
        String caseId,
        boolean isRespondingToRequestOrOrder
    ) {
        String type = application.getType();
        NotificationService.CoreEmailDetails details = notificationService.formatCoreEmailDetails(caseData, caseId);
        notificationService.sendResponseEmailToTribunal(details, type, isRespondingToRequestOrOrder);
        notificationService.sendSubmitStoredEmailToClaimant(details, type);
    }
}
