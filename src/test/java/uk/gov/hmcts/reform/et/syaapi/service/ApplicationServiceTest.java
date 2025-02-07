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
import uk.gov.hmcts.et.common.model.ccd.CaseData;
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
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.NotificationService.CoreEmailDetails;
import uk.gov.service.notify.NotificationClientException;

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
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.RESPONDENT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

@SuppressWarnings({"PMD.SingularField", "PMD.TooManyMethods"})
class ApplicationServiceTest {

    public static final String RESPONDENT_LIST =
        "Test Respondent Organisation -1-, Mehmet Tahir Dede, Abuzer Kadayif, Kate Winslet, Jeniffer Lopez";
    public static final String NOT_SET = "Not set";
    public static final String CASE_REF = "123456/2022";
    public static final String CLAIMANT = "Michael Jackson";
    public static final String CASE_ID = "1646225213651590";
    public static final String INITIAL_STATE = "initialState";
    public static final String YES = "Yes";

    @MockBean
    private CaseService caseService;
    @MockBean
    private NotificationService notificationService;
    @MockBean
    private CaseDocumentService caseDocumentService;
    @MockBean
    private ApplicationService applicationService;
    @MockBean
    private CaseDetailsConverter caseDetailsConverter;
    @MockBean
    private FeatureToggleService featureToggleService;

    private final TestData testData;

    ApplicationServiceTest() {
        testData = new TestData();
    }

    @BeforeEach
    void before() throws CaseDocumentException, DocumentGenerationException {
        caseService = mock(CaseService.class);
        notificationService = mock(NotificationService.class);
        caseDocumentService = mock(CaseDocumentService.class);
        caseDetailsConverter = mock(CaseDetailsConverter.class);
        featureToggleService = mock(FeatureToggleService.class);

        applicationService = new ApplicationService(
            caseService,
            notificationService,
            caseDocumentService,
            caseDetailsConverter,
            featureToggleService);

        when(featureToggleService.isWorkAllocationEnabled()).thenReturn(true);

        doNothing().when(caseService).uploadTseSupportingDocument(any(), any(), any(), any());
        doNothing().when(caseService).uploadTseCyaAsPdf(any(), any(), any(), any());

        when(caseService.triggerEvent(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getClaimantApplicationRequest().getCaseId()),
            eq(CaseEvent.SUBMIT_CLAIMANT_TSE),
            eq(testData.getClaimantApplicationRequest().getCaseTypeId()),
            any()
        )).thenReturn(testData.getCaseDetailsWithData());

        when(caseService.submitUpdate(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getClaimantApplicationRequest().getCaseId()),
            any(),
            eq(testData.getClaimantApplicationRequest().getCaseTypeId())
        )).thenReturn(testData.getCaseDetailsWithData());

        when(caseService.submitUpdate(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getRespondentApplicationRequest().getCaseId()),
            any(),
            eq(testData.getRespondentApplicationRequest().getCaseTypeId())
        )).thenReturn(testData.getCaseDetailsWithData());

        when(caseService.triggerEvent(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(testData.getRespondentApplicationRequest().getCaseId()),
            eq(CaseEvent.SUBMIT_RESPONDENT_TSE),
            eq(testData.getRespondentApplicationRequest().getCaseTypeId()),
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

        ResponseEntity<ByteArrayResource> responseEntity =
            new ResponseEntity<>(HttpStatus.OK);
        when(caseDocumentService.downloadDocument(eq(TEST_SERVICE_AUTH_TOKEN), any())).thenReturn(responseEntity);

    }

    @Test
    void shouldSendClaimantEmailWithCorrectParameters() throws NotificationClientException {
        applicationService.submitApplication(TEST_SERVICE_AUTH_TOKEN,
                                             testData.getClaimantApplicationRequest());

        ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
        verify(notificationService, times(1)).sendAcknowledgementEmailToClaimant(
            argument.capture(),
            any()
        );

        CoreEmailDetails coreEmailDetails = argument.getValue();
        assertThat(coreEmailDetails.caseData()).isNotNull();
        assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
        assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_REF);
        assertThat(coreEmailDetails.respondentNames()).isEqualTo(RESPONDENT_LIST);
        assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
        assertThat(coreEmailDetails.caseId()).isEqualTo(CASE_ID);
    }

    @Nested
    class SubmitApplication {
        @Test
        void shouldSendRespondentEmailWithCorrectParameters() throws NotificationClientException {
            byte[] bytes = "Sample".getBytes();
            ResponseEntity<ByteArrayResource> responseEntity =
                new ResponseEntity<>(new ByteArrayResource(bytes), HttpStatus.OK);

            when(caseDocumentService.downloadDocument(eq(TEST_SERVICE_AUTH_TOKEN), any())).thenReturn(responseEntity);

            applicationService.submitApplication(TEST_SERVICE_AUTH_TOKEN,
                                                 testData.getClaimantApplicationRequest());

            ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
            verify(notificationService, times(1)).sendAcknowledgementEmailToRespondents(
                argument.capture(),
                any(),
                any()
            );

            CoreEmailDetails coreEmailDetails = argument.getValue();
            assertThat(coreEmailDetails.caseData()).isNotNull();
            assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
            assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_REF);
            assertThat(coreEmailDetails.respondentNames()).isEqualTo(RESPONDENT_LIST);
            assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
            assertThat(coreEmailDetails.caseId()).isEqualTo(CASE_ID);
        }

        @Test
        void shouldSendRespondentEmailWithNoSupportingDocument() throws NotificationClientException {
            applicationService.submitApplication(TEST_SERVICE_AUTH_TOKEN,
                                                 testData.getClaimantApplicationRequest());

            ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
            verify(notificationService, times(1)).sendAcknowledgementEmailToRespondents(
                argument.capture(),
                eq(null),
                any()
            );

            CoreEmailDetails coreEmailDetails = argument.getValue();
            assertThat(coreEmailDetails.caseData()).isNotNull();
            assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
            assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_REF);
            assertThat(coreEmailDetails.respondentNames()).isEqualTo(RESPONDENT_LIST);
            assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
            assertThat(coreEmailDetails.caseId()).isEqualTo(CASE_ID);
        }
    }

    @Test
    void shouldSendTribunalEmailWithCorrectParameters() throws NotificationClientException {
        applicationService.submitApplication(TEST_SERVICE_AUTH_TOKEN,
                                             testData.getClaimantApplicationRequest());

        ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
        verify(notificationService, times(1)).sendAcknowledgementEmailToTribunal(
            argument.capture(),
            any()
        );

        CoreEmailDetails coreEmailDetails = argument.getValue();
        assertThat(coreEmailDetails.caseData()).isNotNull();
        assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
        assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_REF);
        assertThat(coreEmailDetails.respondentNames()).isEqualTo(RESPONDENT_LIST);
        assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
        assertThat(coreEmailDetails.caseId()).isEqualTo(CASE_ID);
    }

    @Test
    void shouldMarkApplicationAsViewed() {
        ChangeApplicationStatusRequest testRequest = testData.getChangeApplicationStatusRequest();

        when(caseService.startUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            testRequest.getCaseId(),
            testRequest.getCaseTypeId(),
            CaseEvent.UPDATE_APPLICATION_STATE
        )).thenReturn(testData.getUpdateCaseEventResponse());

        applicationService.changeApplicationStatus(TEST_SERVICE_AUTH_TOKEN, testRequest);

        ArgumentCaptor<CaseData> argumentCaptor = ArgumentCaptor.forClass(CaseData.class);
        verify(caseDetailsConverter).caseDataContent(any(), argumentCaptor.capture());

        String actualState
            = argumentCaptor.getValue().getGenericTseApplicationCollection().get(0).getValue().getApplicationState();
        assertThat(actualState).isEqualTo("viewed");
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
            applicationService.respondToApplication(
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

            applicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                eq(testRequest),
                any(),
                eq(CLAIMANT_TITLE)
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

            applicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                eq(testRequest),
                any(),
                eq(CLAIMANT_TITLE)
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
                () -> applicationService.respondToApplication(
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
                () -> applicationService.respondToApplication(
                    TEST_SERVICE_AUTH_TOKEN,
                    testRequest
                )
            );
        }

        @Test
        void shouldSendResponseEmailsToClaimantWithCorrectParameters() {
            applicationService.respondToApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
            verify(notificationService, times(1)).sendResponseEmailToClaimant(
                argument.capture(),
                eq("Amend response"),
                eq("No"),
                eq(false)
            );

            CoreEmailDetails coreEmailDetails = argument.getValue();
            assertThat(coreEmailDetails.caseData()).isNotNull();
            assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
            assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_ID); // says case_id but is case ethos number
            assertThat(coreEmailDetails.respondentNames()).isEmpty();
            assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
            assertThat(coreEmailDetails.caseId()).isEqualTo("12345");
        }

        @Test
        void shouldFindAndUpdateCase() {
            applicationService.updateTribunalResponseAsViewed(
                TEST_SERVICE_AUTH_TOKEN,
                testData.getResponseViewedRequest()
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
            verify(caseService, times(1)).submitUpdate(
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        void shouldNotUpdateCaseAndThrowException() {
            TribunalResponseViewedRequest testRequest = testData.getResponseViewedRequest();
            testRequest.setResponseId("778");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                applicationService.updateTribunalResponseAsViewed(
                    TEST_SERVICE_AUTH_TOKEN,
                    testRequest
                ));
            assertThat(exception.getMessage()).isEqualTo("Response id is invalid");
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

            applicationService.respondToApplication(TEST_SERVICE_AUTH_TOKEN, testRequest);

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

    @Test
    void shouldSendRespondentEmailWithCorrectParameters() throws NotificationClientException {
        applicationService.submitRespondentApplication(TEST_SERVICE_AUTH_TOKEN,
                                             testData.getRespondentApplicationRequest());

        ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
        verify(notificationService, times(1)).sendRespondentAppAcknowledgementEmailToRespondent(
            argument.capture(),
            any()
        );

        CoreEmailDetails coreEmailDetails = argument.getValue();
        assertThat(coreEmailDetails.caseData()).isNotNull();
        assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
        assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_REF);
        assertThat(coreEmailDetails.respondentNames()).isEqualTo(RESPONDENT_LIST);
        assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
        assertThat(coreEmailDetails.caseId()).isEqualTo(CASE_ID);
    }

    @Nested
    class RespondToClaimantApplicationTests {
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
                CaseEvent.RESPONDENT_TSE_RESPOND
            )).thenReturn(updateCaseEventResponse);
        }

        @Test
        void shouldSubmitResponseToClaimantApplication() {
            applicationService.respondToClaimantApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldSubmitResponseToClaimantApplicationAndSendCopy()
            throws CaseDocumentException, DocumentGenerationException {

            testRequest.getResponse().setCopyToOtherParty(YES);

            applicationService.respondToClaimantApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                any(),
                any(),
                eq(RESPONDENT_TITLE)
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldSubmitResponseToClaimantApplicationWhenOptionalFileNotProvided()
            throws CaseDocumentException, DocumentGenerationException {
            testRequest.setSupportingMaterialFile(null);
            TseRespondType response = testRequest.getResponse();
            response.setSupportingMaterial(null);
            response.setCopyToOtherParty(YES);
            response.setCopyNoGiveDetails(null);

            applicationService.respondToClaimantApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            verify(caseService, times(1)).createResponsePdf(
                eq(TEST_SERVICE_AUTH_TOKEN),
                any(),
                eq(testRequest),
                any(),
                eq(RESPONDENT_TITLE)
            );

            verify(caseDetailsConverter, times(1)).caseDataContent(
                any(),
                any()
            );
        }

        @Test
        void shouldNotSubmitResponseToClaimantApplication() {
            testRequest.setApplicationId("12");

            assertThrows(
                IllegalArgumentException.class,
                () -> applicationService.respondToClaimantApplication(
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
        void shouldThrowErrorWhenSavingClaimantApplication() {
            when(caseService.submitUpdate(
                eq(TEST_SERVICE_AUTH_TOKEN),
                eq(testRequest.getCaseId()),
                any(),
                any()
            )).thenThrow(new JSONException("Could not save response"));

            assertThrows(
                JSONException.class,
                () -> applicationService.respondToClaimantApplication(
                    TEST_SERVICE_AUTH_TOKEN,
                    testRequest
                )
            );
        }

        @Test
        void shouldSendResponseEmailsToClaimantWithCorrectParameters() {
            applicationService.respondToClaimantApplication(
                TEST_SERVICE_AUTH_TOKEN,
                testRequest
            );

            ArgumentCaptor<CoreEmailDetails> argument = ArgumentCaptor.forClass(CoreEmailDetails.class);
            verify(notificationService, times(1)).sendRespondentResponseEmailToRespondent(
                argument.capture(),
                eq("Amend response"),
                eq("No"),
                eq(false)
            );

            CoreEmailDetails coreEmailDetails = argument.getValue();
            assertThat(coreEmailDetails.caseData()).isNotNull();
            assertThat(coreEmailDetails.claimant()).isEqualTo(CLAIMANT);
            assertThat(coreEmailDetails.caseNumber()).isEqualTo(CASE_ID); // says case_id but is case ethos number
            assertThat(coreEmailDetails.respondentNames()).isEmpty();
            assertThat(coreEmailDetails.hearingDate()).isEqualTo(NOT_SET);
            assertThat(coreEmailDetails.caseId()).isEqualTo("12345");
        }

        @ParameterizedTest
        @MethodSource
        void testNewStateAndResponseRequiredForClaimant(boolean isRespondingToRequestOrOrder,
                                                        String expectedApplicationState,
                                                        String expectedRespondentResponseRequired) {
            testRequest.setRespondingToRequestOrOrder(isRespondingToRequestOrOrder);
            GenericTseApplicationType application = GenericTseApplicationType.builder()
                .respondentResponseRequired(YES).applicationState(INITIAL_STATE).build();

            MockedStatic<TseApplicationHelper> mockStatic = mockStatic(TseApplicationHelper.class);
            mockStatic.when(() -> TseApplicationHelper.getSelectedApplication(any(), any()))
                .thenReturn(GenericTseApplicationTypeItem.builder().value(application).build());

            applicationService.respondToClaimantApplication(TEST_SERVICE_AUTH_TOKEN, testRequest);

            assertThat(application.getApplicationState()).isEqualTo(expectedApplicationState);
            assertThat(application.getRespondentResponseRequired()).isEqualTo(expectedRespondentResponseRequired);

            mockStatic.close();
        }

        private static Stream<Arguments> testNewStateAndResponseRequiredForClaimant() {
            return Stream.of(
                Arguments.of(true, "inProgress", "No"),
                Arguments.of(false, INITIAL_STATE, YES)
            );
        }
    }
}
