package uk.gov.hmcts.reform.et.syaapi.enums;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.AWAITING_SUBMISSION_TO_HMCTS;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.SUBMITTED;

@Import(CaseEvent.class)
class CaseStateTest {

    @Test
    void shouldGetDraftStateName() {
        assertThat(String.valueOf(CaseState.DRAFT)).isEqualToIgnoringCase(DRAFT);
    }

    @Test
    void shouldGetAwaitSubmissionName() {
        assertThat(String.valueOf(CaseState.AWAITING_SUBMISSION_TO_HMCTS)).isEqualToIgnoringCase(
            AWAITING_SUBMISSION_TO_HMCTS);
    }

    @Test
    void shouldGetSubmitCaseStateName() {
        assertThat(String.valueOf(CaseState.SUBMITTED)).isEqualToIgnoringCase(SUBMITTED);
    }

}
