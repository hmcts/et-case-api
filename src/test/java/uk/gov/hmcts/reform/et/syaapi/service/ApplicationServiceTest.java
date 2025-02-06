package uk.gov.hmcts.reform.et.syaapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.model.TestData;
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.NotificationService.CoreEmailDetails;
import uk.gov.service.notify.NotificationClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

class ApplicationServiceTest {

    public static final String RESPONDENT_LIST =
        "Test Respondent Organisation -1-, Mehmet Tahir Dede, Abuzer Kadayif, Kate Winslet, Jeniffer Lopez";
    public static final String NOT_SET = "Not set";
    public static final String CASE_REF = "123456/2022";
    public static final String CLAIMANT = "Michael Jackson";
    public static final String CASE_ID = "1646225213651590";

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

        applicationService = new ApplicationService(
            caseService,
            notificationService,
            caseDocumentService,
            caseDetailsConverter);

        doNothing().when(caseService).uploadTseSupportingDocument(any(), any(), any());
        doNothing().when(caseService).uploadTseCyaAsPdf(any(), any(), any(), any());

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
    class updateTribunalResponseAsViewed{
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
    }
}
