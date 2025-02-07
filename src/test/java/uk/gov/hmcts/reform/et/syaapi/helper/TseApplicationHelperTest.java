package uk.gov.hmcts.reform.et.syaapi.helper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.TseAdminRecordDecisionTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.reform.et.syaapi.model.TestData;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.CaseDocumentService;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_FOR_A_JUDGMENT_TO_BE_RECONSIDERED_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_FOR_A_WITNESS_ORDER_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_AMEND_CLAIM;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_HAVE_A_LEGAL_OFFICER_DECISION_CONSIDERED_AFRESH_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_ORDER_THE_R_TO_DO_SOMETHING;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_POSTPONE_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_RESTRICT_PUBLICITY_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_STRIKE_OUT_ALL_OR_PART_OF_THE_RESPONSE;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.APP_TO_VARY_OR_REVOKE_AN_ORDER_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.CHANGE_OF_PARTYS_DETAILS;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.CONTACT_THE_TRIBUNAL_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.R_HAS_NOT_COMPLIED_WITH_AN_ORDER_C;
import static uk.gov.hmcts.ecm.common.model.helper.DocumentConstants.WITHDRAWAL_OF_ALL_OR_PART_CLAIM;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.setApplicationWithResponse;

class TseApplicationHelperTest {
    @MockBean
    private CaseDocumentService caseDocumentService;

    TestData data = new TestData();

    private static Stream<Arguments> checkApplicationDocMapping() {
        return Stream.of(
            Arguments.of("Withdraw all/part of claim", WITHDRAWAL_OF_ALL_OR_PART_CLAIM),
            Arguments.of("Change my personal details", CHANGE_OF_PARTYS_DETAILS),
            Arguments.of("Postpone a hearing", APP_TO_POSTPONE_C),
            Arguments.of("Vary/revoke an order", APP_TO_VARY_OR_REVOKE_AN_ORDER_C),
            Arguments.of("Consider a decision afresh", APP_TO_HAVE_A_LEGAL_OFFICER_DECISION_CONSIDERED_AFRESH_C),
            Arguments.of("Amend my claim", APP_TO_AMEND_CLAIM),
            Arguments.of("Order respondent to do something", APP_TO_ORDER_THE_R_TO_DO_SOMETHING),
            Arguments.of("Order a witness to attend", APP_FOR_A_WITNESS_ORDER_C),
            Arguments.of("Tell tribunal respondent not complied", R_HAS_NOT_COMPLIED_WITH_AN_ORDER_C),
            Arguments.of("Restrict publicity", APP_TO_RESTRICT_PUBLICITY_C),
            Arguments.of("Strike out all/part of response", APP_TO_STRIKE_OUT_ALL_OR_PART_OF_THE_RESPONSE),
            Arguments.of("Reconsider judgement", APP_FOR_A_JUDGMENT_TO_BE_RECONSIDERED_C),
            Arguments.of("Contact about something else", CONTACT_THE_TRIBUNAL_C)
        );
    }

    @ParameterizedTest
    @MethodSource()
    void checkApplicationDocMapping(String applicationType, String documentType) {
        CaseData caseData = data.getCaseData();
        caseData.getGenericTseApplicationCollection().get(0).getValue().setApplicant(CLAIMANT_TITLE);
        caseData.getGenericTseApplicationCollection().get(0).getValue().setType(applicationType);
        String expected = TseApplicationHelper.getApplicationDoc(caseData.getGenericTseApplicationCollection()
                                                                     .get(0).getValue());
        assertThat(expected).isEqualTo(documentType);
    }

    @Nested
    class GetSelectedApplication {
        private static final String ID = "testId";

        @Test
        void correctApplicationWhenExists() {
            GenericTseApplicationTypeItem expected = GenericTseApplicationTypeItem.builder().id(ID).build();
            List<GenericTseApplicationTypeItem> applications = List.of(expected);

            GenericTseApplicationTypeItem actual = TseApplicationHelper.getSelectedApplication(applications, ID);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void nullWhenDoesNotExist() {
            String wrongId = "wrongId";
            GenericTseApplicationTypeItem expected = GenericTseApplicationTypeItem.builder().id(ID).build();
            List<GenericTseApplicationTypeItem> applications = List.of(expected);

            GenericTseApplicationTypeItem actual = TseApplicationHelper.getSelectedApplication(applications, wrongId);

            assertThat(actual).isNull();
        }
    }

    @Nested
    class SetRespondentApplicationWithResponse {
        @Test
        void applicationStatusWaitingForTheTribunal() {
            RespondToApplicationRequest request = data.getRespondToApplicationRequest();
            GenericTseApplicationType app = GenericTseApplicationType.builder()
                .type("Amend response")
                .applicant("Respondent")
                .build();
            CaseData caseData = data.getCaseData();
            caseDocumentService = mock(CaseDocumentService.class);
            DocumentTypeItem docType = DocumentTypeItem.builder().id("1").value(new DocumentType()).build();
            when(caseDocumentService.createDocumentTypeItem(any(), any())).thenReturn(docType);

            setApplicationWithResponse(request, app, caseData, caseDocumentService,
                                       true, CLAIMANT_TITLE
            );

            Assertions.assertEquals("waitingForTheTribunal", app.getApplicationState());
            Assertions.assertEquals("Response to Amend response",
                caseData.getDocumentCollection().get(0).getValue().getShortDescription()
            );
        }
    }

    @Nested
    class FindAdminDecisions {
        @Test
        void shouldFindAdminDecision() {
            GenericTseApplicationTypeItem app = data.getCaseData().getGenericTseApplicationCollection().get(0);
            String decisionId = "777";

            TseAdminRecordDecisionTypeItem result = TseApplicationHelper.findAdminDecision(app, decisionId);

            assertThat(result.getId()).isEqualTo(decisionId);
        }

        @Test
        void shouldReturnNullIfDecisionNotFound() {
            GenericTseApplicationTypeItem app = data.getCaseData().getGenericTseApplicationCollection().get(0);
            String decisionId = "778";

            TseAdminRecordDecisionTypeItem result = TseApplicationHelper.findAdminDecision(app, decisionId);

            assertThat(result).isNull();
        }
    }

    @Nested
    class FindResponses {
        @Test
        void shouldFindAdminResponse() {
            GenericTseApplicationTypeItem app = data.getCaseData().getGenericTseApplicationCollection().get(0);
            String responseId = "777";

            var result = TseApplicationHelper.findResponse(app, responseId);

            assertThat(result.getId()).isEqualTo(responseId);
        }

        @Test
        void shouldReturnNullIfResponseNotFound() {
            GenericTseApplicationTypeItem app = data.getCaseData().getGenericTseApplicationCollection().get(0);
            String responseId = "778";

            var result = TseApplicationHelper.findResponse(app, responseId);

            assertThat(result).isNull();
        }
    }

    @Nested
    class FormatCurrentDate {
        @Test
        void shouldReturnDateTimeFormattedInUkPattern() {
            String expectedLocalDateTime = "07 Feb 2022";
            LocalDate data = LocalDate.of(2022, 2, 7);
            String actualLocalDateTime = TseApplicationHelper.formatCurrentDate(data);

            assertThat(actualLocalDateTime).isEqualTo(expectedLocalDateTime);
        }
    }
}
