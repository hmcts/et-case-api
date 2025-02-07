package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.PseResponseTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.RepresentedTypeRItem;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantHearingPreference;
import uk.gov.hmcts.et.common.model.ccd.types.PseResponseType;
import uk.gov.hmcts.et.common.model.ccd.types.RepresentedTypeR;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentSumType;
import uk.gov.hmcts.et.common.model.ccd.types.SendNotificationType;
import uk.gov.hmcts.et.common.model.ccd.types.SendNotificationTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.exception.NotificationException;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.notification.NotificationsProperties;
import uk.gov.hmcts.reform.et.syaapi.service.NotificationService.CoreEmailDetails;
import uk.gov.hmcts.reform.et.syaapi.service.utils.GenericServiceUtil;
import uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.CY_ABBREVIATED_MONTHS_MAP;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.UNASSIGNED_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.service.NotificationService.HEARING_DATE_KEY;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.ENGLISH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.NOTIFICATION_CONFIRMATION_ID;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SUBMIT_CASE_PDF_FILE_RESPONSE;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.WELSH_LANGUAGE;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports"})
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class NotificationServiceTest {
    public static final String CLAIMANT = "Michael Jackson";
    public static final String NOT_SET = "Not set";
    public static final String TEST_RESPONDENT = "Test Respondent";
    private static final String WITNESS = "witness";
    private static final String DATE_DAY = "12";
    private static final String DATE_YEAR = "2024";
    private static final String CHANGE_DETAILS_APPLICATION_TYPE = "Change my personal details";
    public static final String SEND_EMAIL_PARAMS_DATE_PLUS7_KEY = "datePlus7";
    private final ConcurrentHashMap<String, String> parameters = new ConcurrentHashMap<>();

    @InjectMocks
    private NotificationService notificationService;
    private Map<String, Object> params;
    private CaseData caseData;
    @Mock
    private ClaimantTse claimantApplication;
    private NotificationClient notificationClient;
    @Mock
    private NotificationsProperties notificationsProperties;
    private CaseTestData caseTestData;
    @Mock
    private CoreEmailDetails details;
    @Mock
    private FeatureToggleService featureToggleService;
    private ClaimantHearingPreference claimantHearingPreference;
    @Captor
    ArgumentCaptor<Map<String, Object>> respondentParametersCaptor;
    @Captor
    ArgumentCaptor<Map<String, Object>> claimantParametersCaptor;
    @Mock
    RespondentSumType respondentSumTypeMock;
    @Mock
    RespondentSumTypeItem respondentSumTypeItemMock;

    @BeforeEach
    void before() throws NotificationClientException {
        parameters.put("firstname", "test");
        parameters.put("references", "123456789");
        notificationClient = mock(NotificationClient.class);
        notificationsProperties = mock(NotificationsProperties.class);
        notificationService = new NotificationService(
            notificationClient, notificationsProperties, featureToggleService);
        given(notificationClient.sendEmail(anyString(), anyString(), any(), anyString()))
            .willReturn(TestConstants.INPUT_SEND_EMAIL_RESPONSE);
        given(notificationsProperties.getCySubmitCaseEmailTemplateId())
            .willReturn(TestConstants.WELSH_DUMMY_PDF_TEMPLATE_ID);
        given(notificationsProperties.getSubmitCaseEmailTemplateId())
            .willReturn(TestConstants.SUBMIT_CASE_CONFIRMATION_EMAIL_TEMPLATE_ID);
        given(notificationsProperties.getCitizenPortalLink()).willReturn(TestConstants.REFERENCE_STRING);
        given(notificationsProperties.getClaimantTseEmailNoTemplateId()).willReturn("No");
        given(notificationsProperties.getCyClaimantTseEmailTypeATemplateId())
            .willReturn("CY_APPLICATION_ACKNOWLEDGEMENT_TYPE_A_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getCyClaimantTseEmailTypeBTemplateId())
            .willReturn("CY_APPLICATION_ACKNOWLEDGEMENT_TYPE_B_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getCyClaimantTseEmailNoTemplateId())
            .willReturn("CY_APPLICATION_ACKNOWLEDGEMENT_NO_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getClaimantTseEmailTypeATemplateId())
            .willReturn("APPLICATION_ACKNOWLEDGEMENT_TYPE_A_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getClaimantTseEmailTypeBTemplateId())
            .willReturn("APPLICATION_ACKNOWLEDGEMENT_TYPE_B_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getClaimantTseEmailNoTemplateId())
            .willReturn("APPLICATION_ACKNOWLEDGEMENT_NO_EMAIL_TEMPLATE_ID");
        given(notificationsProperties.getClaimantTseEmailTypeCTemplateId()).willReturn("C");
        given(notificationsProperties.getTribunalAcknowledgementTemplateId()).willReturn("Tribunal");
        given(notificationsProperties.getRespondentTseEmailTypeATemplateId()).willReturn("A");
        given(notificationsProperties.getRespondentTseEmailTypeBTemplateId()).willReturn("B");
        // todo add pse / tse?
        given(notificationsProperties.getPseClaimantResponseYesTemplateId())
            .willReturn("claimantResponseYesTemplateId");
        given(notificationsProperties.getPseClaimantResponseNoTemplateId())
            .willReturn("claimantResponseNoTemplateId");

        given(notificationsProperties.getTseTribunalResponseToRequestTemplateId())
            .willReturn("tseTribunalResponseToRequestTemplateId");
        given(notificationsProperties.getTseClaimantResponseToRequestYesTemplateId())
            .willReturn("tseClaimantResponseToRequestYesTemplateId");
        given(notificationsProperties.getTseClaimantResponseToRequestNoTemplateId())
            .willReturn("tseClaimantResponseToRequestNoTemplateId");
        given(notificationsProperties.getClaimantTseEmailStoredTemplateId())
            .willReturn("claimantTseEmailStoredTemplateId");
        given(notificationsProperties.getClaimantTseEmailSubmitStoredTemplateId())
            .willReturn("claimantTseEmailSubmitStoredTemplateId");
        caseData = new CaseData();
        caseTestData = new CaseTestData();
        caseTestData.getCaseData().setRepCollection(List.of(
            RepresentedTypeRItem.builder()
                .value(RepresentedTypeR.builder()
                           .myHmctsYesNo(YES)
                           .respRepName("RespRepName")
                           .build())
                .build()
        ));
    }

    @SneakyThrows
    @Test
    void shouldSendEmailByMockingResponse() {
        SendEmailResponse sendEmailResponse = mockSendEmailResponse();
        assertThat(sendEmailResponse.getReference()).isPresent();
    }

    @SneakyThrows
    @Test
    void shouldRetrieveTemplateIdCorrectly() {
        SendEmailResponse sendEmailResponse = mockSendEmailResponse();
        assertThat(sendEmailResponse.getTemplateId()).isEqualTo(
            UUID.fromString(TestConstants.UUID_DUMMY_STRING));
    }

    @SneakyThrows
    @Test
    void shouldRetrieveEmailIdCorrectly() {
        SendEmailResponse sendEmailResponse = mockSendEmailResponse();
        assertThat(sendEmailResponse.getFromEmail()).isNotEmpty();
    }

    @SneakyThrows
    @Test
    void shouldRetrieveNotificationIdCorrectly() {
        SendEmailResponse sendEmailResponse = mockSendEmailResponse();
        assertThat(sendEmailResponse.getNotificationId()).isEqualTo(
            UUID.fromString("f30b2148-b1a6-4c0d-8a10-50109c96dc2c"));
    }

    @Test
    void ifTargetEmailIsNullWillThrowNotificationException() throws NotificationClientException {
        given(notificationClient.sendEmail(anyString(), nullable(String.class), any(), anyString()))
            .willThrow(new NotificationClientException("email_address is a required property"));
        assertThatThrownBy(
            () -> notificationService.sendEmail(TestConstants.TEST_TEMPLATE_API_KEY, null, parameters,
                                                TestConstants.REFERENCE_STRING))
            .isInstanceOf(NotificationException.class)
            .hasMessageContaining("email_address is a required property");
    }

    @Test
    void ifTemplateIdIsNullWillThrowNotificationException() throws NotificationClientException {
        given(notificationClient.sendEmail(nullable(String.class), anyString(), any(), anyString()))
            .willThrow(new NotificationClientException("template_id is a required property"));
        assertThatThrownBy(
            () -> notificationService.sendEmail(null, TestConstants.TEST_EMAIL, parameters,
                                                TestConstants.REFERENCE_STRING))
            .isInstanceOf(NotificationException.class)
            .hasMessageContaining("template_id is a required property");
    }

    @Test
    void ifTemplateNotFoundWillThrowNotificationException() throws NotificationClientException {

        given(notificationClient.sendEmail(anyString(), anyString(), any(), anyString()))
            .willThrow(new NotificationClientException("Template not found"));
        assertThatThrownBy(
            () -> notificationService.sendEmail(TestConstants.TEST_TEMPLATE_API_KEY, TestConstants.TEST_EMAIL,
                                                parameters, TestConstants.REFERENCE_STRING))
            .isInstanceOf(NotificationException.class)
            .hasMessageContaining("Template not found");
    }

    @SneakyThrows
    private SendEmailResponse mockSendEmailResponse() {
        notificationClient = mock(NotificationClient.class);
        notificationService = new NotificationService(
            notificationClient, notificationsProperties, featureToggleService);
        doReturn(TestConstants.INPUT_SEND_EMAIL_RESPONSE).when(notificationClient)
            .sendEmail(TestConstants.TEST_TEMPLATE_API_KEY,
                       TestConstants.TEST_EMAIL, parameters, TestConstants.REFERENCE_STRING);
        return notificationService.sendEmail(TestConstants.TEST_TEMPLATE_API_KEY,
                                             TestConstants.TEST_EMAIL, parameters, TestConstants.REFERENCE_STRING
        );
    }

    @ParameterizedTest
    @MethodSource("retrieveSubmitCaseConfirmationEmailPdfFilesArguments")
    void shouldTestSubmitCaseConfirmationWithGivenPdfFilesArguments(List<PdfDecodedMultipartFile> pdfFiles,
                                                                    String expectedValue) {

        caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(null);
        SendEmailResponse response = notificationService.sendSubmitCaseConfirmationEmail(
            caseTestData.getCaseRequest(),
            caseTestData.getCaseData(),
            caseTestData.getUserInfo(),
            pdfFiles
        );
        String actualValue = response == null ? TestConstants.EMPTY_RESPONSE : response.getBody();
        assertThat(actualValue).isEqualTo(expectedValue);
    }

    @SneakyThrows
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567890111213141516"})
    void shouldSendSubmitCaseConfirmationEmailWithGivenCaseIdsEvenItIsEmpty(String caseId) {
        when(notificationClient.sendEmail(
            anyString(),
            anyString(),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        )).thenReturn(TestConstants.SEND_EMAIL_RESPONSE_ENGLISH);
        caseTestData.getCaseRequest().setCaseId(caseId);
        caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(null);
        List<PdfDecodedMultipartFile> casePdfFiles = new ArrayList<>();
        casePdfFiles.add(TestConstants.PDF_DECODED_MULTIPART_FILE1);
        SendEmailResponse response = notificationService.sendSubmitCaseConfirmationEmail(
            caseTestData.getCaseRequest(),
            caseTestData.getCaseData(),
            caseTestData.getUserInfo(),
            casePdfFiles
        );
        assertThat(response.getBody()).isEqualTo(TestConstants.SEND_NOTIFICATION_NO_LANGUAGE_RESPONSE_BODY);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"|" + TestConstants.SEND_NOTIFICATION_NO_LANGUAGE_RESPONSE_BODY,
        "  |" + TestConstants.SEND_NOTIFICATION_NO_LANGUAGE_RESPONSE_BODY,
        TestConstants.UUID_DUMMY_STRING + "|" + TestConstants.SEND_NOTIFICATION_NO_LANGUAGE_RESPONSE_BODY,
        WELSH_LANGUAGE + "|" + TestConstants.SEND_NOTIFICATION_WELSH_RESPONSE_BODY,
        ENGLISH_LANGUAGE + "|" + TestConstants.SEND_NOTIFICATION_ENGLISH_RESPONSE_BODY},
        delimiter = '|')
    void shouldSendSubmitCaseConfirmationEmailAccordingToSelectedLanguage(String selectedLanguage,
                                                                          String expectedBody) {
        when(notificationClient.sendEmail(
            anyString(),
            anyString(),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        )).thenReturn(
            WELSH_LANGUAGE.equals(selectedLanguage) ? TestConstants.SEND_EMAIL_RESPONSE_WELSH
                : ENGLISH_LANGUAGE.equals(selectedLanguage) ? TestConstants.SEND_EMAIL_RESPONSE_ENGLISH
                : TestConstants.INPUT_SEND_EMAIL_RESPONSE);
        caseTestData.getCaseRequest().setCaseId(caseTestData.getExpectedDetails().getId().toString());
        caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(selectedLanguage);
        List<PdfDecodedMultipartFile> casePdfFiles = new ArrayList<>();
        casePdfFiles.add(TestConstants.PDF_DECODED_MULTIPART_FILE1);
        SendEmailResponse response = notificationService.sendSubmitCaseConfirmationEmail(
            caseTestData.getCaseRequest(),
            caseTestData.getCaseData(),
            caseTestData.getUserInfo(),
            casePdfFiles
        );
        assertThat(response.getBody()).isEqualTo(expectedBody);
    }

    @SneakyThrows
    @Test
    void shouldThrowExceptionWhenSubmitCaseConfirmationEmailNotSent() {
        try (MockedStatic<GenericServiceUtil> mockedServiceUtil = Mockito.mockStatic(GenericServiceUtil.class)) {
            when(notificationClient.sendEmail(
                anyString(),
                anyString(),
                any(),
                anyString()
            )).thenThrow(NotificationClientException.class);
            List<PdfDecodedMultipartFile> casePdfFiles = new ArrayList<>();
            casePdfFiles.add(TestConstants.PDF_DECODED_MULTIPART_FILE1);
            mockedServiceUtil.when(() -> GenericServiceUtil.hasPdfFile(casePdfFiles, 0)).thenReturn(true);
            mockedServiceUtil.when(() -> GenericServiceUtil.findClaimantLanguage(caseTestData.getCaseData()))
                .thenReturn(ENGLISH_LANGUAGE);
            mockedServiceUtil.when(() -> GenericServiceUtil.findClaimantFirstNameByCaseDataUserInfo(any(), any()))
                .thenReturn(caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames());
            mockedServiceUtil.when(() -> GenericServiceUtil.findClaimantLastNameByCaseDataUserInfo(any(), any()))
                .thenReturn(caseTestData.getCaseData().getClaimantIndType().getClaimantLastName());
            mockedServiceUtil.when(() -> GenericServiceUtil.findPdfFileBySelectedLanguage(any(), anyString()))
                .thenReturn(TEST_SUBMIT_CASE_PDF_FILE_RESPONSE.getBytes());
            notificationService.sendSubmitCaseConfirmationEmail(
                caseTestData.getCaseRequest(),
                caseTestData.getCaseData(),
                caseTestData.getUserInfo(),
                casePdfFiles
            );
            mockedServiceUtil.verify(
                () -> GenericServiceUtil.logException(
                    anyString(),
                    anyString(),
                    eq(null),
                    anyString(),
                    anyString()
                ),
                times(1)
            );
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("retrieveSendDocUploadErrorEmailPdfFilesArguments")
    void shouldSuccessfullySendDocUploadErrorEmailWithGivenPdfFilesList(List<PdfDecodedMultipartFile> casePdfFiles,
                                                                        List<PdfDecodedMultipartFile> acasCertificates,
                                                                        UploadedDocumentType claimDescriptionDocument) {
        when(notificationsProperties.getEt1ServiceOwnerNotificationEmail()).thenReturn(TestConstants.TEST_EMAIL);
        when(notificationsProperties.getEt1EcmDtsCoreTeamSlackNotificationEmail()).thenReturn(TestConstants.TEST_EMAIL);
        when(notificationsProperties.getSubmitCaseDocUploadErrorEmailTemplateId())
            .thenReturn(TestConstants.DOC_UPLOAD_ERROR_EMAIL_TEMPLATE_ID);
        when(notificationClient.sendEmail(TestConstants.TEST_TEMPLATE_API_KEY, TestConstants.TEST_EMAIL, parameters,
                                          TestConstants.REFERENCE_STRING
        ))
            .thenReturn(TestConstants.SEND_EMAIL_RESPONSE_DOC_UPLOAD_FAILURE);

        CaseRequest caseRequest = CaseRequest.builder().build();
        caseRequest.setCaseId("1_231_231");

        SendEmailResponse sendEmailResponse = notificationService.sendDocUploadErrorEmail(
            caseRequest,
            casePdfFiles,
            acasCertificates,
            claimDescriptionDocument
        );
        assertThat(sendEmailResponse.getFromEmail()).isPresent();
        assertThat(sendEmailResponse.getFromEmail()).asString()
            .isEqualTo("Optional[" + TestConstants.TEST_EMAIL + "]");
    }

    @SneakyThrows
    @Test
    void shouldThrowNotificationExceptionWhenNotAbleToSendDocUploadErrorEmail() {
        try (MockedStatic<GenericServiceUtil> mockedServiceUtil = Mockito.mockStatic(GenericServiceUtil.class)) {
            when(notificationClient.sendEmail(any(), any(), any(), any())).thenThrow(new NotificationClientException(
                new Exception("Error while trying to send doc upload error notification to service owner")));
            when(notificationsProperties.getSubmitCaseDocUploadErrorEmailTemplateId()).thenReturn(null);
            when(notificationsProperties.getEt1EcmDtsCoreTeamSlackNotificationEmail()).thenReturn(null);
            mockedServiceUtil.when(() -> GenericServiceUtil.prepareUpload(any(), anyInt()))
                .thenReturn(TestConstants.FILE_NOT_EXISTS);
            UploadedDocumentType claimDescriptionDocument = new UploadedDocumentType();
            notificationService.sendDocUploadErrorEmail(
                caseTestData.getCaseRequest(),
                List.of(TestConstants.PDF_DECODED_MULTIPART_FILE1),
                List.of(TestConstants.PDF_DECODED_MULTIPART_FILE1),
                claimDescriptionDocument
            );
            mockedServiceUtil.verify(
                () -> GenericServiceUtil.logException(
                    anyString(),
                    eq("1646225213651590"),
                    anyString(),
                    anyString(),
                    anyString()
                ),
                times(1)
            );
        }
    }

    private static Stream<Arguments> retrieveSubmitCaseConfirmationEmailPdfFilesArguments() {
        return CaseTestData.generateSubmitCaseConfirmationEmailPdfFilesArguments();
    }

    private static Stream<Arguments> retrieveSendDocUploadErrorEmailPdfFilesArguments() {
        return CaseTestData.generateSendDocUploadErrorEmailPdfFilesArguments();
    }

    @Nested
    class SendAcknowledgementEmailToClaimant {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendCopyYesEmail() throws NotificationClientException, IOException {
            when(notificationClient.sendEmail(
                eq(YES),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            assertThat(notificationService.sendAcknowledgementEmailToClaimant(
                details,
                caseTestData.getClaimantApplication()
            ).getNotificationId()).isEqualTo(NOTIFICATION_CONFIRMATION_ID);
        }

        @Test
        void shouldSendCopyNoEmail() throws NotificationClientException, IOException {
            caseTestData.getClaimantApplication().setCopyToOtherPartyYesOrNo("No");
            when(notificationClient.sendEmail(
                eq("No"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            assertThat(notificationService.sendAcknowledgementEmailToClaimant(
                details,
                caseTestData.getClaimantApplication()
            ).getNotificationId()).isEqualTo(NOTIFICATION_CONFIRMATION_ID);
        }

        @Test
        void shouldSendTypeCEmail() throws NotificationClientException, IOException {
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            when(notificationClient.sendEmail(
                eq("C"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            assertThat(notificationService.sendAcknowledgementEmailToClaimant(
                details,
                caseTestData.getClaimantApplication()
            ).getNotificationId()).isEqualTo(NOTIFICATION_CONFIRMATION_ID);
        }
    }

    @Nested
    class SendAcknowledgementEmailToClaimantWelsh {

        @ParameterizedTest
        @MethodSource("monthTranslations")
        void shouldTranslateHearingDateToWelsh(
            String englishMonth, String welshMonth) throws NotificationClientException {
            String hearingDate = DATE_DAY + " " + englishMonth + " " + DATE_YEAR;
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                hearingDate,
                caseTestData.getExpectedDetails().getId().toString()
            );
            when(featureToggleService.isWelshEnabled()).thenReturn(true);
            when(notificationsProperties.getCyClaimantTseEmailTypeCTemplateId()).thenReturn(
                "ExpectedEmailTemplateIdForWelsh");
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
            when(notificationClient.sendEmail(
                anyString(),
                anyString(),
                claimantParametersCaptor.capture(),
                anyString()
            ))
                .thenReturn(mock(SendEmailResponse.class));
            notificationService.sendAcknowledgementEmailToClaimant(details, caseTestData.getClaimantApplication());

            Map<String, Object> capturedClaimantParameters = claimantParametersCaptor.getValue();
            String translatedHearingDate = capturedClaimantParameters.get(HEARING_DATE_KEY).toString();
            assertThat(translatedHearingDate).isEqualTo(DATE_DAY + " " + welshMonth + " " + DATE_YEAR);
        }

        static Stream<Arguments> monthTranslations() {
            return CY_ABBREVIATED_MONTHS_MAP.entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @Nested
    class HandlingNotSetHearingDateBasedOnLanguage {

        @ParameterizedTest
        @CsvSource({
            "Welsh, true, Heb ei anfon",
            "Welsh, false, Not set",
            "English, true, Not set",
            "English, false, Not set"
        })
        void shouldHandleNotSetHearingDateBasedOnLanguage(
            String language, Boolean isWelshEnabled, String expectedOutcome) throws NotificationClientException {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                    NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
            when(featureToggleService.isWelshEnabled()).thenReturn(isWelshEnabled);
            caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(language);
            when(notificationsProperties.getCyClaimantTseEmailTypeCTemplateId()).thenReturn(
                "ExpectedEmailTemplateIdForWelsh");
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            when(notificationClient.sendEmail(
                anyString(),
                anyString(),
                claimantParametersCaptor.capture(),
                anyString()
            )).thenReturn(mock(SendEmailResponse.class));

            notificationService.sendAcknowledgementEmailToClaimant(details, caseTestData.getClaimantApplication());

            verify(notificationClient).sendEmail(
                anyString(),
                anyString(),
                claimantParametersCaptor.capture(),
                anyString()
            );

            Map<String, Object> capturedClaimantParameters = claimantParametersCaptor.getValue();
            String actualHearingDate = capturedClaimantParameters.get(HEARING_DATE_KEY).toString();
            assertThat(actualHearingDate).isEqualTo(expectedOutcome);
        }
    }

    @Nested
    class SendAcknowledgementEmailToRespondents {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                "Test Respondent Organisation -1-, Mehmet Tahir Dede, Abuzer Kadayif, Kate Winslet, Jeniffer Lopez",
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendEmailToRespondentTypeBPersonalisationCheck() throws NotificationClientException {
            caseTestData.getClaimantApplication().setContactApplicationType("strike");
            notificationService.sendAcknowledgementEmailToRespondents(
                details,
                null,
                caseTestData.getClaimantApplication()
            );

            verify(notificationClient, times(5)).sendEmail(any(), any(),
                                                           respondentParametersCaptor.capture(), any()
            );
            Map<String, Object> respondentParameters = respondentParametersCaptor.getValue();
            Object targetParameter = respondentParameters.get(SEND_EMAIL_PARAMS_DATE_PLUS7_KEY);
            String[] dateParts = targetParameter.toString().split(" ");
            String[] plusSevenDays = LocalDate.now().plusDays(7)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy")).split(" ");
            assertThat(dateParts[0]).isEqualTo(plusSevenDays[0]);
            assertThat(dateParts[1]).isEqualTo(plusSevenDays[1]);
            assertThat(dateParts[2]).isEqualTo(plusSevenDays[2]);
        }

        @Test
        void shouldSendEmailToRespondentTypeB() throws NotificationClientException {
            notificationService.sendAcknowledgementEmailToRespondents(
                details,
                null,
                caseTestData.getClaimantApplication()
            );

            verify(notificationClient, times(5)).sendEmail(
                eq("B"),
                any(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @SneakyThrows
        @Test
        void shouldSendEmailToRespondentTypeA() {
            caseTestData.getClaimantApplication().setContactApplicationType("strike");
            notificationService.sendAcknowledgementEmailToRespondents(
                details,
                null,
                caseTestData.getClaimantApplication()
            );

            verify(notificationClient, times(5)).sendEmail(
                eq("A"),
                any(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendEmailToRespondentTypeC() throws NotificationClientException {
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            notificationService.sendAcknowledgementEmailToRespondents(
                details,
                null,
                caseTestData.getClaimantApplication()
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendEmailToRespondentForClaimantEccResponse() throws NotificationClientException {

            List<PseResponseTypeItem> pseResponseItem = List.of(PseResponseTypeItem.builder().id("12345").value(
                PseResponseType.builder()
                    .from(CLAIMANT)
                    .hasSupportingMaterial(TestConstants.NO)
                    .response("Some response text")
                    .responseState(null)
                    .copyToOtherParty(null)
                    .build()).build()
            );
            List<SendNotificationTypeItem> notificationItem = new ArrayList<>();
            notificationItem.add(
                SendNotificationTypeItem.builder()
                    .id("12345")
                    .value(SendNotificationType.builder()
                               .respondCollection(pseResponseItem)
                               .build())
                    .build()
            );
            caseTestData.getCaseData().setSendNotificationCollection(notificationItem);
            notificationService.sendAcknowledgementEmailToRespondents(
                details,
                null,
                caseTestData.getClaimantApplication()
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Nested
    class SendAcknowledgementEmailToTribunal {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendEmailToTribunalTypeAOrB() throws NotificationClientException {
            notificationService.sendAcknowledgementEmailToTribunal(
                details,
                caseTestData.getClaimantApplication().getContactApplicationType()
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );

        }

        @Test
        void shouldSendEmailToTribunalTypeC() throws NotificationClientException {
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            notificationService.sendAcknowledgementEmailToTribunal(
                details,
                caseTestData.getClaimantApplication().getContactApplicationType()
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendEmailToTribunalUnassignedManagingOffice() throws NotificationClientException {
            caseTestData.getCaseData().setManagingOffice(UNASSIGNED_OFFICE);
            notificationService.sendAcknowledgementEmailToTribunal(
                details,
                caseTestData.getClaimantApplication().getContactApplicationType()
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                any(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Nested
    class SetTribunalCorrespondenceEmail {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendResponseEmailToTribunal() throws NotificationClientException {
            caseTestData.getCaseData().setTribunalCorrespondenceEmail("tribunal@test.com");
            notificationService.sendResponseEmailToTribunal(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                false
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseEmailToTribunal() throws NotificationClientException {
            caseTestData.getCaseData().setManagingOffice(UNASSIGNED_OFFICE);
            notificationService.sendResponseEmailToTribunal(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                false
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldSendResponseToRequestEmailToTribunal() throws NotificationClientException {
            caseTestData.getCaseData().setTribunalCorrespondenceEmail("tribunal@test.com");
            notificationService.sendResponseEmailToTribunal(details, CHANGE_DETAILS_APPLICATION_TYPE, true);

            verify(notificationClient, times(1)).sendEmail(
                eq("tseTribunalResponseToRequestTemplateId"),
                eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Nested
    class SendResponseEmailToClaimant {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendResponseEmailToClaimant() throws NotificationClientException {
            notificationService.sendResponseEmailToClaimant(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                "No",
                false
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseWhenClaimantEmailDoesNotExist() throws NotificationClientException {
            caseTestData.getCaseData().getClaimantType().setClaimantEmailAddress("");
            notificationService.sendResponseEmailToClaimant(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                "No",
                false
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseEmailToClaimantForTypeCApplication() throws NotificationClientException {
            notificationService.sendResponseEmailToClaimant(
                details,
                WITNESS,
                "No",
                false
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @ParameterizedTest
        @MethodSource("responseToRequestArguments")
        void sendResponseToRequestNotificationEmailToClaimant(String copyToOtherParty, String template)
            throws NotificationClientException {
            notificationService.sendResponseEmailToClaimant(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                copyToOtherParty,
                true
            );

            verify(notificationClient, times(1)).sendEmail(
                eq(template),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        private static Stream<Arguments> responseToRequestArguments() {
            return Stream.of(
                Arguments.of(YES, "tseClaimantResponseToRequestYesTemplateId"),
                Arguments.of("No", "tseClaimantResponseToRequestNoTemplateId")
            );
        }
    }

    @Nested
    class SendReplyEmailToRespondent {
        CaseData caseData;

        @BeforeEach
        void setUp() {
            caseData = caseTestData.getCaseData();
            RespondentSumTypeItem respondent = new RespondentSumTypeItem();
            respondent.setValue(RespondentSumType.builder()
                                    .respondentEmail("email")
                                    .respondentName("RespondentName")
                                    .build());
            caseData.setRespondentCollection(List.of(respondent));
        }

        @Test
        void givenRule92YesSendEmailToRespondent() throws NotificationClientException {
            notificationService.sendReplyEmailToRespondent(
                caseData,
                "1",
                caseTestData.getExpectedDetails().getId().toString(),
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq("email"),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void givenRule92NoDoNotSendEmailToRespondent() throws NotificationClientException {
            notificationService.sendReplyEmailToRespondent(
                caseData,
                "1",
                caseTestData.getExpectedDetails().getId().toString(),
                "No"
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq("email"),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Nested
    class SendResponseEmailToRespondent {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendResponseEmailToRespondent() throws NotificationClientException {
            notificationService.sendResponseEmailToRespondent(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getRespondentCollection().get(0).getValue().getRespondentEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseEmailToRespondent() throws NotificationClientException {
            notificationService.sendResponseEmailToRespondent(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                NO
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getRespondentCollection().get(0).getValue().getRespondentEmail()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldSendResponseEmailToRespondentResp() throws NotificationClientException {
            RespondentSumTypeItem respondentSumTypeItem = new RespondentSumTypeItem();
            respondentSumTypeItem.setValue(RespondentSumType.builder()
                                               .respondentEmail("test@resRep.com")
                                               .respondentName("RespondentName")
                                               .build());
            respondentSumTypeItem.setId(String.valueOf(UUID.randomUUID()));

            caseData = caseTestData.getCaseData();
            caseData.getRespondentCollection().add(respondentSumTypeItem);

            notificationService.sendResponseEmailToRespondent(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                eq("test@resRep.com"),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseWhenRespondentEmailDoesNotExist() throws NotificationClientException {
            caseTestData.getCaseData().getClaimantType().setClaimantEmailAddress("");
            notificationService.sendResponseEmailToRespondent(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseEmailToRespondentForTypeCApplication() throws NotificationClientException {
            notificationService.sendResponseEmailToRespondent(
                details,
                WITNESS,
                YES
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Test
    void sendResponseNotificationEmailToTribunal() throws NotificationClientException {
        caseTestData.getCaseData().setTribunalCorrespondenceEmail("tribunal@test.com");
        notificationService.sendResponseNotificationEmailToTribunal(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString()
        );

        verify(notificationClient, times(1)).sendEmail(
            any(),
            eq(caseTestData.getCaseData().getTribunalCorrespondenceEmail()),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        );
    }

    @Test
    void sendNotResponseNotificationEmailToTribunalMissingEmail() throws NotificationClientException {
        caseTestData.getCaseData().setTribunalCorrespondenceEmail(null);
        notificationService.sendResponseNotificationEmailToTribunal(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString()
        );

        verify(notificationClient, times(0)).sendEmail(
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void sendResponseNotificationEmailToRespondent() throws NotificationClientException {
        notificationService.sendResponseNotificationEmailToRespondent(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            YES
        );

        verify(notificationClient, times(1)).sendEmail(
            any(),
            eq(caseTestData.getCaseData().getRespondentCollection().get(0).getValue().getRespondentEmail()),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        );
    }

    @Test
    void sendResponseNotificationEmailToRespondentNotSystemUser() throws NotificationClientException {
        caseTestData.getCaseData().setRepCollection(null);

        notificationService.sendResponseNotificationEmailToRespondent(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            YES
        );

        verify(notificationClient, times(0)).sendEmail(any(), any(), any(), any());
    }

    @Test
    void sendNotResponseNotificationEmailToRespondentDoNotCopy() throws NotificationClientException {
        notificationService.sendResponseNotificationEmailToRespondent(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            NO
        );

        verify(notificationClient, times(0)).sendEmail(
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void sendNotResponseNotificationEmailToRespondentMissingEmail() throws NotificationClientException {
        for (RespondentSumTypeItem respondentSumTypeItem : caseTestData.getCaseData().getRespondentCollection()) {
            respondentSumTypeItem.getValue().setRespondentEmail(null);
        }
        notificationService.sendResponseNotificationEmailToRespondent(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            YES
        );

        verify(notificationClient, times(0)).sendEmail(
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void sendResponseNotificationEmailToClaimant() throws NotificationClientException {
        notificationService.sendResponseNotificationEmailToClaimant(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            YES
        );

        verify(notificationClient, times(1)).sendEmail(
            eq("claimantResponseYesTemplateId"),
            eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        );
    }

    @Test
    void sendResponseNotificationEmailToClaimantDoNotCopy() throws NotificationClientException {
        notificationService.sendResponseNotificationEmailToClaimant(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            NO
        );

        verify(notificationClient, times(1)).sendEmail(
            eq("claimantResponseNoTemplateId"),
            eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        );
    }

    @Test
    void sendNotResponseNotificationEmailToClaimantMissingEmail() throws NotificationClientException {
        caseTestData.getCaseData().getClaimantType().setClaimantEmailAddress(null);
        notificationService.sendResponseNotificationEmailToClaimant(
            caseTestData.getCaseData(),
            caseTestData.getExpectedDetails().getId().toString(),
            YES
        );

        verify(notificationClient, times(0)).sendEmail(
            any(),
            any(),
            any(),
            any()
        );
    }

    @Nested
    class SendStoreAcknowledgementEmail {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                "Test Respondent Organisation -1-, Mehmet Tahir Dede, Abuzer Kadayif, Kate Winslet, Jeniffer Lopez",
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void sendStoredEmailToClaimant() throws NotificationClientException {
            notificationService.sendStoredEmailToClaimant(
                details,
                "shortText"
            );

            verify(notificationClient, times(1)).sendEmail(
                eq("claimantTseEmailStoredTemplateId"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void sendSubmitStoredEmailToClaimant() throws NotificationClientException {
            notificationService.sendSubmitStoredEmailToClaimant(
                details,
                "shortText"
            );

            verify(notificationClient, times(1)).sendEmail(
                eq("claimantTseEmailSubmitStoredTemplateId"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Test
    void shouldSendBundlesEmailToAll() throws NotificationClientException {
        given(notificationsProperties.getBundlesClaimantSubmittedNotificationTemplateId()
        ).willReturn("bundlesClaimantSubmittedNotificationTemplateId");

        caseData = caseTestData.getCaseData();
        String futureDate = LocalDateTime.now().plusDays(5).toString();
        caseData.getHearingCollection().get(0).getValue().getHearingDateCollection().get(0).getValue().setListedDate(
            futureDate);

        notificationService.sendBundlesEmails(
            caseData,
            caseTestData.getExpectedDetails().getId().toString(),
            "123345"
        );

        // Sends to 5 respondents + 1 to tribunal
        verify(notificationClient, times(6)).sendEmail(
            eq("bundlesClaimantSubmittedNotificationTemplateId"),
            any(),
            any(),
            eq(caseTestData.getExpectedDetails().getId().toString())
        );
    }

    @BeforeEach
    void setUp() {
        claimantHearingPreference = new ClaimantHearingPreference();
        params = new HashMap<>();

        String hearingDate = "12 Jan 2023";
        when(details.hearingDate()).thenReturn(hearingDate);
        caseData.setClaimantHearingPreference(claimantHearingPreference);
    }

    @ParameterizedTest
    @CsvSource({
        "true, strike, Yes, CY_APPLICATION_ACKNOWLEDGEMENT_TYPE_A_EMAIL_TEMPLATE_ID",
        "false, strike, Yes, APPLICATION_ACKNOWLEDGEMENT_TYPE_A_EMAIL_TEMPLATE_ID",
        "true, withdraw, Yes, CY_APPLICATION_ACKNOWLEDGEMENT_TYPE_B_EMAIL_TEMPLATE_ID",
        "false, withdraw, Yes, APPLICATION_ACKNOWLEDGEMENT_TYPE_B_EMAIL_TEMPLATE_ID",
        "true, strike, No, CY_APPLICATION_ACKNOWLEDGEMENT_NO_EMAIL_TEMPLATE_ID",
        "false, strike, No, APPLICATION_ACKNOWLEDGEMENT_NO_EMAIL_TEMPLATE_ID"
    })
    void shouldReturnExpectedEmailTemplateForRule92(
        Boolean isWelsh, String contactType, String copyTo, String expectedTemplateId) {

        when(claimantApplication.getContactApplicationType()).thenReturn(contactType);
        when(claimantApplication.getCopyToOtherPartyYesOrNo()).thenReturn(copyTo);

        String emailTemplate = notificationService.getAndSetEmailTemplate(
            claimantApplication, details.hearingDate(), params, isWelsh, false);

        assertEquals(expectedTemplateId, emailTemplate);
    }

    @ParameterizedTest
    @CsvSource({
        "true, Welsh, CY_APPLICATION_ACKNOWLEDGEMENT_TYPE_C_EMAIL_TEMPLATE_ID",
        "false, Welsh, APPLICATION_ACKNOWLEDGEMENT_TYPE_C_EMAIL_TEMPLATE_ID",
        "true, English, APPLICATION_ACKNOWLEDGEMENT_TYPE_C_EMAIL_TEMPLATE_ID",
        "false, English, APPLICATION_ACKNOWLEDGEMENT_TYPE_C_EMAIL_TEMPLATE_ID"
    })
    void shouldReturnExpectedEmailTemplateForTypeC(
        Boolean welshFlagEnabled, String language, String expectedTemplateId) {

        when(featureToggleService.isWelshEnabled()).thenReturn(welshFlagEnabled);
        claimantHearingPreference.setContactLanguage(language);

        if (WELSH_LANGUAGE.equals(language) && welshFlagEnabled) {
            when(notificationsProperties.getCyClaimantTseEmailTypeCTemplateId()).thenReturn(expectedTemplateId);
        } else if (ENGLISH_LANGUAGE.equals(language) || !welshFlagEnabled) {
            when(notificationsProperties.getClaimantTseEmailTypeCTemplateId()).thenReturn(expectedTemplateId);
        }

        String emailTemplate = WELSH_LANGUAGE.equals(language) && welshFlagEnabled
            ? notificationsProperties.getCyClaimantTseEmailTypeCTemplateId()
            : notificationsProperties.getClaimantTseEmailTypeCTemplateId();

        assertEquals(expectedTemplateId, emailTemplate);
    }

    @Nested
    class SendRepAppAcknowledgementEmailToRespondent {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                "TEST_RESPONDENT_1, TEST_RESPONDENT_2, "
                    + "TEST_RESPONDENT_3, TEST_RESPONDENT_4, TEST_RESPONDENT_5",
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendCopyYesEmail() throws NotificationClientException, IOException {
            when(notificationClient.sendEmail(
                eq(YES),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            notificationService.sendRespondentAppAcknowledgementEmailToRespondent(
                details,
                caseTestData.getRespondentApplication());

            verify(notificationClient, times(5)).sendEmail(
                any(),
                any(),
                respondentParametersCaptor.capture(),
                any()
            );
        }

        @Test
        void shouldSendCopyNoEmail() throws NotificationClientException, IOException {
            caseTestData.getClaimantApplication().setCopyToOtherPartyYesOrNo("No");
            when(notificationClient.sendEmail(
                eq("No"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            notificationService.sendRespondentAppAcknowledgementEmailToRespondent(
                details,
                caseTestData.getRespondentApplication());

            verify(notificationClient, times(5)).sendEmail(
                any(),
                any(),
                respondentParametersCaptor.capture(),
                any()
            );
        }

        @Test
        void shouldSendTypeCEmail() throws NotificationClientException, IOException {
            caseTestData.getClaimantApplication().setContactApplicationType(WITNESS);
            when(notificationClient.sendEmail(
                eq("C"),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            )).thenReturn(caseTestData.getSendEmailResponse());

            notificationService.sendRespondentAppAcknowledgementEmailToRespondent(
                details,
                caseTestData.getRespondentApplication());

            verify(notificationClient, times(5)).sendEmail(
                any(),
                any(),
                respondentParametersCaptor.capture(),
                any()
            );
        }
    }

    @Nested
    class SendRepAppAcknowledgementEmailToRespondentWelsh {

        @ParameterizedTest
        @MethodSource("monthTranslations")
        void shouldTranslateHearingDateToWelsh(
            String englishMonth, String welshMonth) throws NotificationClientException {
            String hearingDate = DATE_DAY + " " + englishMonth + " " + DATE_YEAR;
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                hearingDate,
                caseTestData.getExpectedDetails().getId().toString()
            );
            setLanguagePreference(details.caseData(), WELSH_LANGUAGE);

            when(featureToggleService.isWelshEnabled()).thenReturn(true);
            when(notificationsProperties.getRespondentTseTypeCRespAckTemplateId()).thenReturn(
                "ExpectedEmailTemplateIdForWelsh");
            caseTestData.getRespondentApplication().setContactApplicationType(WITNESS);
            when(notificationClient.sendEmail(
                anyString(),
                anyString(),
                any(),
                anyString()
            ))
                .thenReturn(mock(SendEmailResponse.class));

            notificationService.sendRespondentAppAcknowledgementEmailToRespondent(
                details, caseTestData.getRespondentApplication());

            List<Map<String, Object>> capturedParameters = respondentParametersCaptor.getAllValues();
            for (Map<String, Object> params : capturedParameters) {
                assertEquals(DATE_DAY + " " + welshMonth + " " + DATE_YEAR, params.get(HEARING_DATE_KEY));
            }
        }

        static Stream<Arguments> monthTranslations() {
            return CY_ABBREVIATED_MONTHS_MAP.entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }

        static void setLanguagePreference(CaseData caseData, String languagePreference) {
            caseData.getRespondentCollection().forEach(respondentSumTypeItem -> {
                RespondentSumType respondentSumType = respondentSumTypeItem.getValue();
                respondentSumType.setEt3ResponseLanguagePreference(languagePreference);
            });
        }
    }

    @Nested
    class SendRespondentResponseEmailToRespondent {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendRespondentResponseEmailToRespondent() throws NotificationClientException {
            notificationService.sendRespondentResponseEmailToRespondent(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                "No",
                false
            );

            verify(notificationClient, times(5)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendRespondentResponseEmailToRespondentForTypeCApplication() throws NotificationClientException {
            notificationService.sendRespondentResponseEmailToRespondent(
                details,
                WITNESS,
                "No",
                false
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @ParameterizedTest
        @MethodSource("responseToRequestArguments")
        void sendResponseToRequestNotificationEmailToRespondent(String copyToOtherParty, String template)
            throws NotificationClientException {
            notificationService.sendResponseEmailToClaimant(
                details,
                CHANGE_DETAILS_APPLICATION_TYPE,
                copyToOtherParty,
                true
            );

            verify(notificationClient, times(1)).sendEmail(
                eq(template),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        private static Stream<Arguments> responseToRequestArguments() {
            return Stream.of(
                Arguments.of(YES, "tseClaimantResponseToRequestYesTemplateId"),
                Arguments.of("No", "tseClaimantResponseToRequestNoTemplateId")
            );
        }
    }

    @Nested
    class SendRespondentResponseEmailToClaimant {
        @BeforeEach
        void setUp() {

            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void givenRule92YesSendEmailToClaimant() throws NotificationClientException {
            notificationService.sendRespondentResponseEmailToClaimant(
                details,
                caseTestData.getExpectedDetails().getId().toString(),
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void givenRule92NoDoNotSendEmailToClaimant() throws NotificationClientException {
            notificationService.sendRespondentResponseEmailToClaimant(
                details,
                caseTestData.getExpectedDetails().getId().toString(),
                "No"
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }

    @Nested
    class SendReplyEmailToClaimant {
        @BeforeEach
        void setUp() {
            details = new CoreEmailDetails(
                caseTestData.getCaseData(),
                CLAIMANT,
                "1",
                TEST_RESPONDENT,
                NOT_SET,
                caseTestData.getExpectedDetails().getId().toString()
            );
        }

        @Test
        void shouldSendResponseEmailToClaimant() throws NotificationClientException {
            notificationService.sendReplyEmailToClaimant(
                details.caseData(),
                details.caseId(),
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(CHANGE_DETAILS_APPLICATION_TYPE)
            );
        }

        @Test
        void shouldNotSendResponseEmailToRespondent() throws NotificationClientException {
            notificationService.sendReplyEmailToClaimant(
                details.caseData(),
                details.caseId(),
                CHANGE_DETAILS_APPLICATION_TYPE,
                NO
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldSendResponseEmailToRespondentResp() throws NotificationClientException {

            notificationService.sendReplyEmailToClaimant(
                details.caseData(),
                details.caseId(),
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(1)).sendEmail(
                any(),
                anyString(),
                any(),
                eq(CHANGE_DETAILS_APPLICATION_TYPE)
            );
        }

        @Test
        void shouldNotSendResponseWhenRespondentEmailDoesNotExist() throws NotificationClientException {
            caseTestData.getCaseData().getClaimantType().setClaimantEmailAddress("");
            notificationService.sendReplyEmailToClaimant(
                details.caseData(),
                details.caseId(),
                CHANGE_DETAILS_APPLICATION_TYPE,
                YES
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }

        @Test
        void shouldNotSendResponseEmailToRespondentForTypeCApplication() throws NotificationClientException {
            notificationService.sendResponseEmailToRespondent(
                details,
                WITNESS,
                YES
            );

            verify(notificationClient, times(0)).sendEmail(
                any(),
                eq(caseTestData.getCaseData().getClaimantType().getClaimantEmailAddress()),
                any(),
                eq(caseTestData.getExpectedDetails().getId().toString())
            );
        }
    }
}

