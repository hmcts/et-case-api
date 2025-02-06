package uk.gov.hmcts.reform.et.syaapi.service;


import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper;
import uk.gov.hmcts.reform.et.syaapi.model.TestData;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

class RespondToApplicationServiceTest {
    private static final String NOT_SET = "Not set";
    private static final String CLAIMANT = "Michael Jackson";
    private static final String CASE_ID = "1646225213651590";
    private static final String INITIAL_STATE = "initialState";
    private static final String YES = "Yes";

    @MockBean
    private RespondToApplicationService respondToApplicationService;
    @MockBean
    private CaseService caseService;
    @MockBean
    private NotificationService notificationService;
    @MockBean
    private CaseDocumentService caseDocumentService;
    @MockBean
    private CaseDetailsConverter caseDetailsConverter;
    @MockBean
    private FeatureToggleService featureToggleService;

    private final TestData testData;

    RespondToApplicationServiceTest() {
        testData = new TestData();
    }

    @BeforeEach
    void before() throws CaseDocumentException, DocumentGenerationException {
        caseService = mock(CaseService.class);
        notificationService = mock(NotificationService.class);
        caseDocumentService = mock(CaseDocumentService.class);
        caseDetailsConverter = mock(CaseDetailsConverter.class);
        featureToggleService = mock(FeatureToggleService.class);

        respondToApplicationService = new RespondToApplicationService(
            caseService,
            notificationService,
            caseDocumentService,
            caseDetailsConverter,
            featureToggleService);

        when(featureToggleService.isWorkAllocationEnabled()).thenReturn(true);

        when(caseService.triggerEvent(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getClaimantApplicationRequest().getCaseId()),
            eq(CaseEvent.SUBMIT_CLAIMANT_TSE),
            eq(testData.getClaimantApplicationRequest().getCaseTypeId()),
            any()
        )).thenReturn(testData.getCaseDetailsWithData());

        when(caseService.startUpdate(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(testData.getUpdateCaseEventResponse());
        DocumentTypeItem docType = DocumentTypeItem.builder().id("1").value(new DocumentType()).build();
        when(caseDocumentService.createDocumentTypeItem(any(), any())).thenReturn(docType);

        when(caseService.submitUpdate(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getClaimantApplicationRequest().getCaseId()),
            any(),
            eq(testData.getClaimantApplicationRequest().getCaseTypeId())
        )).thenReturn(testData.getCaseDetailsWithData());

        ResponseEntity<ByteArrayResource> responseEntity =
            new ResponseEntity<>(HttpStatus.OK);
        when(caseDocumentService.downloadDocument(eq(TEST_SERVICE_AUTH_TOKEN), any())).thenReturn(responseEntity);

    }

    @Nested
    class RespondToApplicationReplyToTribunal {
        RespondToApplicationRequest testRequest;
        StartEventResponse updateCaseEventResponse;

        @BeforeEach
        void setUp() {
            testRequest = testData.getRespondToApplicationRequest();
            updateCaseEventResponse = testData.getUpdateCaseEventResponse();
            when(caseService.startUpdate(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest.getCaseId(),
                testRequest.getCaseTypeId(),
                CaseEvent.CLAIMANT_TSE_RESPOND
            )).thenReturn(updateCaseEventResponse);
        }

        @Test
        void shouldSubmitResponseToApplication() {
            respondToApplicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldSubmitResponseToApplicationAndSendCopy() throws CaseDocumentException, DocumentGenerationException {
            testRequest.getResponse().setCopyToOtherParty(YES);

            respondToApplicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                eq(testRequest),
                any()
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldSubmitResponseToApplicationWhenOptionalFileNotProvided()
            throws CaseDocumentException, DocumentGenerationException {
            testRequest.setSupportingMaterialFile(null);
            TseRespondType response = testRequest.getResponse();
            response.setSupportingMaterial(null);
            response.setCopyToOtherParty(YES);
            response.setCopyNoGiveDetails(null);

            respondToApplicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                eq(testRequest),
                any()
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldNotSubmitResponseToApplication() {
            testRequest.setApplicationId("12");

            assertThrows(
                IllegalArgumentException.class,
                () -> respondToApplicationService.respondToApplication(
                    TEST_SERVICE_AUTH_TOKEN,
                    testRequest
                )
            );

            verify(caseDetailsConverter, times(0)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldThrowErrorWhenSavingApplication() {
            when(caseService.submitUpdate(
                eq(TEST_SERVICE_AUTH_TOKEN),
                eq(testRequest.getCaseId()),
                any(),
                any()
            )).thenThrow(new JSONException("Could not save response"));

            assertThrows(
                JSONException.class,
                () -> respondToApplicationService.respondToApplication(
                    TEST_SERVICE_AUTH_TOKEN,
                    testRequest
                )
            );
        }

        @Test
        void shouldSendResponseEmailsToClaimantWithCorrectParameters() {
            respondToApplicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            ArgumentCaptor<NotificationService.CoreEmailDetails> argument = ArgumentCaptor.forClass(NotificationService.CoreEmailDetails.class);
            verify(notificationService, times(1)).sendResponseEmailToClaimant(
                argument.capture(),
                eq("Amend response"),
                eq("No"),
                eq(false)
            );

            NotificationService.CoreEmailDetails coreEmailDetails = argument.getValue();
            assertThat(coreEmailDetails.caseData()).isNotNull();
            assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
            assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_ID); // says case_id but is case ethos number
            assertThat(coreEmailDetails.respondentNames()).isEmpty();
            assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
            assertThat(coreEmailDetails.caseId()).isEqualTo("12345");
        }

        @ParameterizedTest
        @MethodSource
        void testNewStateAndResponseRequired(boolean isRespondingToRequestOrOrder, String expectedApplicationState,
                                             String expectedClaimantResponseRequired) {
            testRequest.setRespondingToRequestOrOrder(isRespondingToRequestOrOrder);
            GenericTseApplicationType application = GenericTseApplicationType.builder()
                .claimantResponseRequired(YES).applicationState(INITIAL_STATE).build();

            MockedStatic<TseApplicationHelper> mockStatic = mockStatic(TseApplicationHelper.class);
            mockStatic.when(() -> TseApplicationHelper.getSelectedApplication(any(), any()))
                .thenReturn(GenericTseApplicationTypeItem.builder().value(application).build());

            respondToApplicationService.respondToApplication(TEST_SERVICE_AUTH_TOKEN, testRequest);

            assertThat(application.getApplicationState()).isEqualTo(expectedApplicationState);
            assertThat(application.getClaimantResponseRequired()).isEqualTo(expectedClaimantResponseRequired);

            mockStatic.close();
        }

        private static Stream<Arguments> testNewStateAndResponseRequired() {
            return Stream.of(
                Arguments.of(true, "inProgress", "No"),
                Arguments.of(false, INITIAL_STATE, YES)
            );
        }
    }
}
