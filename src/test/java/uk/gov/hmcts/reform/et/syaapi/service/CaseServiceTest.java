package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.ecm.common.service.PostcodeToOfficeService;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.JurCodesTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.DocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.JurCodesType;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.constants.JurisdictionCodesConstants;
import uk.gov.hmcts.reform.et.syaapi.helper.JurisdictionCodesMapper;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.notification.NotificationsProperties;
import uk.gov.hmcts.reform.et.syaapi.service.pdf.PdfUploadService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.service.notify.SendEmailResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.ENGLANDWALES_CASE_TYPE_ID;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.SUBMITTED;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.YES;
import static uk.gov.hmcts.reform.et.syaapi.constants.DocumentCategoryConstants.ET1_PDF_DOC_CATEGORY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.DRAFT_EVENT_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ET1_ONLINE_SUBMISSION;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.JURISDICTION_ID;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SCOTLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.UPDATE_CASE_DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.UPDATE_CASE_SUBMITTED;
import static uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper.convertCaseDataMapToCaseDataObject;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.RESPONDENT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.CASE_ID;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.SUBMIT_CASE_DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_NAME;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.USER_ID;

@EqualsAndHashCode
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports", "PMD.AvoidDuplicateLiterals", "PMD.TooManyFields"})
class CaseServiceTest {

    @Mock
    private PostcodeToOfficeService postcodeToOfficeService;
    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private CoreCaseDataApi ccdApiClient;
    @Mock
    private IdamClient idamClient;
    @Mock
    private JurisdictionCodesMapper jurisdictionCodesMapper;
    @Mock
    private PdfUploadService pdfUploadService;
    @Mock
    private AcasService acasService;
    @Mock
    private CaseDocumentService caseDocumentService;
    @Mock
    private DocumentGenerationService documentGenerationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CaseOfficeService assignCaseToLocalOfficeService;
    @Mock
    private FeatureToggleService featureToggle;
    @Mock
    private ManageCaseRoleService manageCaseRoleService;
    @Spy
    private NotificationsProperties notificationsProperties;
    @InjectMocks
    private CaseService caseService;
    private SendEmailResponse sendEmailResponse;
    private final CaseTestData caseTestData;
    public static final String TEST = "test";
    private static final byte[] TSE_PDF_BYTES = TEST.getBytes();
    private static final String TSE_PDF_NAME = "contact_about_something_else.pdf";
    private static final String PDF_FILE_TIKA_CONTENT_TYPE = "application/pdf";
    private static final String TSE_PDF_DESCRIPTION = "Test description";

    private final PdfDecodedMultipartFile tsePdfMultipartFileMock = new PdfDecodedMultipartFile(
        TSE_PDF_BYTES,
        TSE_PDF_NAME,
        PDF_FILE_TIKA_CONTENT_TYPE,
        TSE_PDF_DESCRIPTION
    );

    CaseServiceTest() {
        caseTestData = new CaseTestData();
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        if (!testInfo.getDisplayName().startsWith("submitCase")) {
            return;
        }
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames(),
            caseTestData.getCaseData().getClaimantIndType().getClaimantLastName(),
            null
        ));

        caseTestData.getCaseRequest().setCaseId("1668421480426211");
        when(ccdApiClient.submitEventForCitizen(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION_ID),
            eq(SCOTLAND_CASE_TYPE),
            any(String.class),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(caseTestData.getExpectedDetails());

        when(ccdApiClient.startEventForCitizen(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION_ID),
            eq(SCOTLAND_CASE_TYPE),
            any(String.class),
            any(String.class)
        )).thenReturn(caseTestData.getStartEventResponse());

        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            new PdfDecodedMultipartFile(
                new byte[0],
                TEST,
                TEST,
                TEST
            );

        when(pdfUploadService.convertAcasCertificatesToPdfDecodedMultipartFiles(any(), any()))
            .thenReturn(List.of(pdfDecodedMultipartFile));

        when(pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(any(), any()))
            .thenReturn(List.of(pdfDecodedMultipartFile));

        when(acasService.getAcasCertificatesByCaseData(any())).thenReturn(List.of());

        when(assignCaseToLocalOfficeService.convertCaseRequestToCaseDataWithTribunalOffice(any()))
            .thenReturn(caseTestData.getCaseData());
        sendEmailResponse
            = new SendEmailResponse("""
                                        {
                                          "id": "8835039a-3544-439b-a3da-882490d959eb",
                                          "reference": "TEST_EMAIL_ALERT",
                                          "template": {
                                            "id": "8835039a-3544-439b-a3da-882490d959eb",
                                            "version": "3",
                                            "uri": "TEST"
                                          },
                                          "content": {
                                            "body": "Dear test, Please see your detail as 123456789. Regards, ET Team.",
                                            "subject": "ET Test email created",
                                            "from_email": "TEST@GMAIL.COM"
                                          }
                                        }
                                        """);
        when(notificationService.sendSubmitCaseConfirmationEmail(any(), any(), any(), any()))
            .thenReturn(sendEmailResponse);
    }

    @Test
    void shouldCreateNewDraftCaseInCcd() {

        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames(),
            caseTestData.getCaseData().getClaimantIndType().getClaimantLastName(),
            null
        ));
        when(ccdApiClient.startForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            SCOTLAND_CASE_TYPE,
            DRAFT_EVENT_TYPE
        )).thenReturn(
            caseTestData.getStartEventResponse());

        when(ccdApiClient.submitForCitizen(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION_ID),
            eq(SCOTLAND_CASE_TYPE),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(caseTestData.getExpectedDetails());

        CaseRequest caseRequest = caseTestData.getCaseRequest();

        CaseDetails caseDetails = caseService.createCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );

        assertEquals(caseTestData.getExpectedDetails(), caseDetails);
    }

    @Test
    void shouldCreateNewDraftCaseInCcdWithPostCode() {

        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames(),
            caseTestData.getCaseData().getClaimantIndType().getClaimantLastName(),
            null
        ));
        when(ccdApiClient.startForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            ENGLAND_CASE_TYPE,
            DRAFT_EVENT_TYPE
        )).thenReturn(
            caseTestData.getStartEventResponse());

        when(ccdApiClient.submitForCitizen(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION_ID),
            eq(ENGLAND_CASE_TYPE),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(caseTestData.getExpectedDetails());

        CaseRequest caseRequest = caseTestData.getCaseRequest();
        caseRequest.setCaseTypeId(null);

        CaseDetails caseDetails = caseService.createCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );

        assertEquals(caseTestData.getExpectedDetails(), caseDetails);
    }

    @Test
    void shouldStartUpdateCaseInCcd() {
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames(),
            caseTestData.getCaseData().getClaimantIndType().getClaimantLastName(),
            null
        ));

        when(ccdApiClient.startEventForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            SCOTLAND_CASE_TYPE,
            caseTestData.getCaseRequest().getCaseId(),
            String.valueOf(UPDATE_CASE_DRAFT)
        )).thenReturn(
            caseTestData.getStartEventResponse());

        StartEventResponse eventResponse = caseService.startUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest().getCaseId(),
            SCOTLAND_CASE_TYPE,
            UPDATE_CASE_DRAFT
        );

        assertEquals(SCOTLAND_CASE_TYPE, eventResponse.getCaseDetails().getCaseTypeId());
    }

    @Test
    void shouldSubmitUpdateCaseInCcd() {
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            caseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames(),
            caseTestData.getCaseData().getClaimantIndType().getClaimantLastName(),
            null
        ));
        when(ccdApiClient.submitEventForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            SCOTLAND_CASE_TYPE,
            caseTestData.getCaseRequest().getCaseId(),
            true,
            caseTestData.getUpdateCaseDataContent()
        )).thenReturn(caseTestData.getExpectedDetails());

        CaseDetails caseDetails = caseService.submitUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest().getCaseId(),
            caseTestData.getUpdateCaseDataContent(),
            SCOTLAND_CASE_TYPE
        );

        assertEquals(caseDetails, caseTestData.getExpectedDetails());
    }

    @SneakyThrows
    @Test
    void submitCaseShouldAddSupportingDocumentToDocumentCollection() {
        when(caseDocumentService.uploadAllDocuments(any(), any(), any(), any(), any()))
            .thenReturn(List.of(createDocumentTypeItem()));

        CaseDetails caseDetails = caseService.submitCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest()
        );

        assertEquals(1, ((ArrayList<?>)caseDetails.getData().get("documentCollection")).size());
        List<?> docCollection = (List<?>) caseDetails.getData().get("documentCollection");

        assertEquals("DocumentType(typeOfDocument="
             + "Other, uploadedDocument=UploadedDocumentType(documentBinaryUrl=http://document.url/2333482f-1eb9-44f1"
             + "-9b78-f5d8f0c74b15/binary, documentFilename=filename, documentUrl=http://document.binary"
             + ".url/2333482f-1eb9-44f1-9b78-f5d8f0c74b15, categoryId=C11, uploadTimestamp=null), "
             + "ownerDocument=null, creationDate=null, "
             + "shortDescription=null, topLevelDocuments=null, startingClaimDocuments=null, "
             + "responseClaimDocuments=null, initialConsiderationDocuments=null, "
             + "caseManagementDocuments=null, withdrawalSettledDocuments=null, hearingsDocuments=null, "
             + "judgmentAndReasonsDocuments=null, reconsiderationDocuments=null, miscDocuments=null, "
             + "documentType=null, dateOfCorrespondence=null, docNumber=null, tornadoEmbeddedPdfUrl=null, "
             + "excludeFromDcf=null, documentIndex=null)",
            ((DocumentTypeItem) docCollection.get(0)).getValue().toString());
    }

    @Test
    @SneakyThrows
    void submitCaseShouldSendErrorEmail() {
        when(caseDocumentService.uploadAllDocuments(any(), any(), any(), any(), any()))
            .thenThrow(new CaseDocumentException("Failed to upload documents"));

        when(notificationService.sendDocUploadErrorEmail(any(), any(), any(), any()))
            .thenReturn(sendEmailResponse);

        caseService.submitCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest()
        );

        verify(notificationService, times(1))
            .sendDocUploadErrorEmail(any(), any(), any(), any());
    }

    @SneakyThrows
    @Test
    void submitCaseShouldSetEt1OnlineSubmission() {
        CaseDetails caseDetails = caseService.submitCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest()
        );

        assertEquals(YES, caseDetails.getData().get(ET1_ONLINE_SUBMISSION));
    }

    private DocumentTypeItem createDocumentTypeItem() {
        UploadedDocumentType uploadedDocumentType = new UploadedDocumentType();
        uploadedDocumentType.setDocumentFilename("filename");
        uploadedDocumentType.setDocumentUrl("http://document.binary.url/2333482f-1eb9-44f1-9b78-f5d8f0c74b15");
        uploadedDocumentType.setDocumentBinaryUrl("http://document.url/2333482f-1eb9-44f1-9b78-f5d8f0c74b15/binary");
        uploadedDocumentType.setCategoryId(ET1_PDF_DOC_CATEGORY);
        DocumentTypeItem documentTypeItem = new DocumentTypeItem();
        documentTypeItem.setId(UUID.randomUUID().toString());

        DocumentType documentType = new DocumentType();
        documentType.setTypeOfDocument("Other");
        documentType.setUploadedDocument(uploadedDocumentType);

        documentTypeItem.setValue(documentType);
        return documentTypeItem;
    }

    @Nested
    class UploadTseSupportingDocument {
        @Test
        void setsShortDescriptionCorrectly() {
            CaseDetails caseDetails = CaseDetails.builder().data(new HashMap<>()).build();
            String contactApplicationType = "withdraw";
            caseService.uploadTseSupportingDocument(caseDetails, new UploadedDocumentType(),
                                                    contactApplicationType, CLAIMANT_TITLE);

            CaseData caseData = convertCaseDataMapToCaseDataObject(caseDetails.getData());
            String actual = caseData.getDocumentCollection().get(0).getValue().getShortDescription();
            String expected = "Withdraw all/part of claim";
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void setsShortDescriptionCorrectlyRespondentTse() {
            CaseDetails caseDetails = CaseDetails.builder().data(new HashMap<>()).build();
            String contactApplicationType = "Amend response";
            caseService.uploadTseSupportingDocument(caseDetails, new UploadedDocumentType(),
                                                    contactApplicationType, RESPONDENT_TITLE
            );

            CaseData caseData = convertCaseDataMapToCaseDataObject(caseDetails.getData());
            String actual = caseData.getDocumentCollection().get(0).getValue().getShortDescription();
            assertThat(actual).isEqualTo(contactApplicationType);
        }
    }

    @Test
    void shouldInvokeCaseEnrichmentWithJurCodesInSubmitEvent() {
        List<JurCodesTypeItem> expectedItems = mockJurCodesTypeItems();
        caseTestData.getStartEventResponse().setEventId(SUBMIT_CASE_DRAFT);
        CaseData caseData = convertCaseDataMapToCaseDataObject(caseTestData.getCaseDataWithClaimTypes()
                                                                                  .getCaseData());
        caseData.setJurCodesCollection(expectedItems);
        caseData.setFeeGroupReference(CASE_ID);

        CaseDataContent expectedEnrichedData = CaseDataContent.builder()
            .event(Event.builder().id(SUBMIT_CASE_DRAFT).build())
            .eventToken(caseTestData.getStartEventResponse().getToken())
            .data(caseData)
            .build();

        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            USER_ID,
            TEST_NAME,
            TestConstants.TEST_FIRST_NAME,
            TestConstants.TEST_SURNAME,
            null
        ));

        when(ccdApiClient.startEventForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            ENGLAND_CASE_TYPE,
            CASE_ID,
            SUBMIT_CASE_DRAFT
        )).thenReturn(caseTestData.getStartEventResponse());

        lenient().when(ccdApiClient.submitEventForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            USER_ID,
            JURISDICTION_ID,
            ENGLAND_CASE_TYPE,
            CASE_ID,
            true,
            expectedEnrichedData
        )).thenReturn(caseTestData.getExpectedDetails());

        when(jurisdictionCodesMapper.mapToJurCodes(any())).thenReturn(expectedItems);

        caseService.triggerEventForSubmitCase(
            TEST_SERVICE_AUTH_TOKEN,
            CaseRequest.builder()
                .caseId(CASE_ID)
                .caseTypeId(ENGLANDWALES_CASE_TYPE_ID)
                .caseData(new HashMap<>())
                .build()
        );

        verify(ccdApiClient).submitEventForCitizen(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any()
        );
    }

    @Test
    void shouldInvokeClaimantTsePdf()
        throws DocumentGenerationException {
        when(pdfUploadService.convertClaimantTseIntoMultipartFile(any(), any(), anyString())).thenReturn(
            tsePdfMultipartFileMock);

        assertDoesNotThrow(() ->
                               caseService.uploadTseCyaAsPdf(
                                   TEST_SERVICE_AUTH_TOKEN,
                                   caseTestData.getCaseDetails(),
                                   caseTestData.getClaimantTse(),
                                   "TEST"
                               )
        );
    }

    @SneakyThrows
    @Test
    void givenPdfServiceErrorProducesDocumentGenerationException() {
        when(pdfUploadService.convertClaimantTseIntoMultipartFile(any(), any(), anyString())).thenThrow(
            new DocumentGenerationException(TEST));

        assertThrows(DocumentGenerationException.class, () -> caseService.uploadTseCyaAsPdf(
            "", caseTestData.getCaseDetails(), caseTestData.getClaimantTse(), ""));
    }

    @Test
    void submitCaseCitizenDocGenerationToggleEnabled() throws CaseDocumentException {
        when(featureToggle.citizenEt1Generation()).thenReturn(true);
        caseService.submitCase(TEST_SERVICE_AUTH_TOKEN, caseTestData.getCaseRequest());

        verify(notificationService, never()).sendSubmitCaseConfirmationEmail(any(), any(), any(), any());
        verify(pdfUploadService, never()).convertCaseDataToPdfDecodedMultipartFile(any(), any());
        verify(caseDocumentService, never()).uploadAllDocuments(any(), any(), any(), any(), any());

    }

    @Test
    @SneakyThrows
    void submitCaseCcdFailsButStillSubmits() {
        when(featureToggle.citizenEt1Generation()).thenReturn(true);
        when(ccdApiClient.submitEventForCitizen(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any())).thenThrow(new RuntimeException("Submission failed"));
        when(ccdApiClient.getCase(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            caseTestData.getCaseRequest().getCaseId()
        )).thenReturn(caseTestData.getExpectedDetails());
        caseTestData.getExpectedDetails().setState(SUBMITTED);
        try {
            caseService.submitCase(TEST_SERVICE_AUTH_TOKEN, caseTestData.getCaseRequest());
        } catch (Exception e) {
            verify(ccdApiClient, times(1)).getCase(any(), any(), any());

        }
    }

    private List<JurCodesTypeItem> mockJurCodesTypeItems() {
        JurCodesTypeItem item = new JurCodesTypeItem();
        JurCodesType type = new JurCodesType();
        type.setJuridictionCodesList(JurisdictionCodesConstants.BOC);
        item.setValue(type);
        return List.of(item);
    }

    @Test
    void theUpdateCaseSubmitted() {
        CaseRequest caseRequest = new CaseTestData().getCaseRequest();
        CaseDetails caseDetails = new CaseTestData().getCaseDetails();
        StartEventResponse startEventResponse = new CaseTestData().getStartEventResponse();
        UserInfo userInfo = new CaseTestData().getUserInfo();
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(userInfo);
        when(ccdApiClient.startEventForCitizen(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            userInfo.getUid(),
            JURISDICTION_ID,
            SCOTLAND_CASE_TYPE,
            caseRequest.getCaseId(),
            UPDATE_CASE_SUBMITTED.name()
        )).thenReturn(startEventResponse);
        when(ccdApiClient.submitEventForCitizen(
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN),
            eq(userInfo.getUid()),
            eq(JURISDICTION_ID),
            eq(SCOTLAND_CASE_TYPE),
            eq(null),
            eq(true),
            any(CaseDataContent.class)
            )).thenReturn(caseDetails);
        when(caseService.triggerEvent(TEST_SERVICE_AUTH_TOKEN,
                                      caseRequest.getCaseId(),
                                      UPDATE_CASE_SUBMITTED,
                                      SCOTLAND_CASE_TYPE,
                                      caseRequest.getCaseData())).thenReturn(caseDetails);
        assertThat(caseService.updateCaseSubmitted(TEST_SERVICE_AUTH_TOKEN, caseRequest)).isEqualTo(caseDetails);
    }

    @Test
    void shouldInvokeRespondentTsePdf()
        throws DocumentGenerationException {
        when(pdfUploadService.convertRespondentTseIntoMultipartFile(any(), any(), anyString())).thenReturn(
            tsePdfMultipartFileMock);

        assertDoesNotThrow(() ->
                               caseService.uploadRespondentTseAsPdf(
                                   TEST_SERVICE_AUTH_TOKEN,
                                   caseTestData.getCaseDetails(),
                                   caseTestData.getRespondentTse(),
                                   "TEST"
                               )
        );
    }

    @SneakyThrows
    @Test
    void givenPdfServiceErrorProducesDocumentGenerationExceptionForRespondentTse() {
        when(pdfUploadService.convertRespondentTseIntoMultipartFile(any(), any(), anyString())).thenThrow(
            new DocumentGenerationException(TEST));

        assertThrows(DocumentGenerationException.class, () -> caseService.uploadRespondentTseAsPdf(
            "", caseTestData.getCaseDetails(), caseTestData.getRespondentTse(), ""));
    }
}
