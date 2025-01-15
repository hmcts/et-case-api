package uk.gov.hmcts.reform.et.syaapi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the different case events as an enumerator.
 * Used as a parameter in triggerEvent function
 */
@Getter
@RequiredArgsConstructor
public enum CaseEvent {
    INITIATE_CASE_DRAFT,
    UPDATE_CASE_DRAFT,
    SUBMIT_CASE_DRAFT,
    UPDATE_CASE_SUBMITTED,
    SUBMIT_CLAIMANT_TSE,
    SUBMIT_RESPONDENT_TSE,
    STORE_CLAIMANT_TSE,
    STORE_CLAIMANT_TSE_RESPOND,
    SUBMIT_STORED_CLAIMANT_TSE_RESPOND,
    CLAIMANT_TSE_RESPOND,
    RESPONDENT_TSE_RESPOND,
    UPDATE_APPLICATION_STATE,
    UPDATE_NOTIFICATION_STATE,
    UPDATE_NOTIFICATION_RESPONSE,
    STORE_PSE_RESPONSE,
    SUBMIT_STORED_PSE_RESPONSE,
    UPDATE_ADMIN_DECISION_STATE,
    UPDATE_HUBLINK_STATUS,
    SUBMIT_CLAIMANT_BUNDLES,
    SUBMIT_ET3_FORM,
    UPDATE_ET3_FORM,
}
