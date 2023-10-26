package uk.gov.hmcts.reform.et.syaapi.helper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.TseAdminRecordDecisionTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.TseRespondTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.CaseDocumentService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.OLD_DATE_TIME_PATTERN;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.CLAIMANT_CORRESPONDENCE_DOCUMENT;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.UK_LOCAL_DATE_PATTERN;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor"})
public final class TseApplicationHelper {

    public static final String CLAIMANT = "Claimant";
    public static final String WAITING_FOR_TRIBUNAL = "waitingForTheTribunal";

    /**
     * Gives current date in string format.
     * @return current date in "dd MMM yyyy" format
     */
    public static String formatCurrentDate(LocalDate date) {
        return date.format(UK_LOCAL_DATE_PATTERN);
    }

    /**
     * Gives current datetime in string format.
     * @return current datetime in "yyyy-MM-dd'T'HH:mm:ss.SSS" format
     */
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(OLD_DATE_TIME_PATTERN);
    }

    /**
     * Finds the application by ID.
     *
     * @param applications - list of all applications attached to the case
     * @param applicationId - id of application we're trying to find
     * @return the {@link GenericTseApplicationTypeItem} to be updated
     */
    public static GenericTseApplicationTypeItem getSelectedApplication(
        List<GenericTseApplicationTypeItem> applications,
        String applicationId) {
        return applications.stream()
            .filter(a -> a.getId().equals(applicationId))
            .findAny()
            .orElse(null);
    }

    /**
     * Finds the admin decision by ID.
     *
     * @param selectedApplication - application to update
     * @param adminDecisionId - id of decision we're trying to find
     * @return the {@link TseAdminRecordDecisionTypeItem} to be updated
     */
    public static TseAdminRecordDecisionTypeItem findAdminDecision(GenericTseApplicationTypeItem selectedApplication,
                                                          String adminDecisionId) {
        return selectedApplication.getValue().getAdminDecision().stream()
            .filter(a -> a.getId().equals(adminDecisionId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Finds the response by ID.
     *
     * @param selectedApplication - application to update
     * @param responseId - id of decision we're trying to find
     * @return the {@link TseRespondTypeItem} to be updated
     */
    public static TseRespondTypeItem findResponse(GenericTseApplicationTypeItem selectedApplication,
                                                  String responseId) {
        return selectedApplication.getValue().getRespondCollection().stream()
            .filter(a -> a.getId().equals(responseId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Sets case data with claimant's response to the respondent.
     *
     * @param request - request from the claimant
     * @param appToModify - selected respondent application to respond to
     * @param caseData - case data
     * @param caseDocumentService - case document service to create pdf of response
     */
    public static void setRespondentApplicationWithResponse(RespondToApplicationRequest request,
                                                            GenericTseApplicationType appToModify,
                                                            CaseData caseData,
                                                            CaseDocumentService caseDocumentService) {
        if (CollectionUtils.isEmpty(appToModify.getRespondCollection())) {
            appToModify.setRespondCollection(new ArrayList<>());
        }
        TseRespondType responseToAdd = request.getResponse();
        responseToAdd.setDate(TseApplicationHelper.formatCurrentDate(LocalDate.now()));
        responseToAdd.setFrom(CLAIMANT);
        responseToAdd.setDateTime(getCurrentDateTime());
        responseToAdd.setApplicationType(appToModify.getType());

        if (request.getSupportingMaterialFile() != null) {
            DocumentTypeItem documentTypeItem = caseDocumentService.createDocumentTypeItem(
                CLAIMANT_CORRESPONDENCE_DOCUMENT,
                request.getSupportingMaterialFile()
            );
            documentTypeItem.getValue().setShortDescription("Response to " + appToModify.getType());

            if (caseData.getDocumentCollection() == null) {
                caseData.setDocumentCollection(new ArrayList<>());
            }
            caseData.getDocumentCollection().add(documentTypeItem);
            responseToAdd.setSupportingMaterial(new ArrayList<>());
            responseToAdd.getSupportingMaterial().add(documentTypeItem);
        }

        appToModify.getRespondCollection().add(TseRespondTypeItem.builder()
                                                   .id(UUID.randomUUID().toString())
                                                   .value(responseToAdd).build());
        appToModify.setResponsesCount(
            String.valueOf(appToModify.getRespondCollection().size()));
        appToModify.setApplicationState(WAITING_FOR_TRIBUNAL);
    }
}
