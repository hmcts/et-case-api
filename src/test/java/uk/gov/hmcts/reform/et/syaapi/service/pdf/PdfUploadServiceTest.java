package uk.gov.hmcts.reform.et.syaapi.service.pdf;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.ecm.common.service.pdf.PdfService;
import uk.gov.hmcts.et.common.model.ccd.items.GenericTseApplicationType;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;
import uk.gov.hmcts.reform.et.syaapi.models.AcasCertificate;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationException;
import uk.gov.hmcts.reform.et.syaapi.service.DocumentGenerationService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.SAMPLE_BYTE_ARRAY;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"PMD.TooManyMethods"})
class PdfUploadServiceTest {
    private CaseTestData caseTestData;
    private final AcasCertificate acasCertificate = ResourceLoader.fromString(
        "requests/acasCertificate.json",
        AcasCertificate.class
    );
    private PdfUploadService pdfUploadService;
    @Mock
    private PdfService pdfService;
    @Mock
    private DocumentGenerationService documentGenerationService;

    private static final String CLIENT_TYPE_CLAIMANT = "claimant";
    private static final String SUBMIT_ET1_CITIZEN = "submitET1Citizen";
    private static final String CUSTOM_DOC_NAME = "customDocName.pdf";

    @BeforeEach
    @SneakyThrows
    void beforeEach() {
        caseTestData = new CaseTestData();
        pdfUploadService = new PdfUploadService(pdfService, documentGenerationService);
        pdfUploadService.englishPdfTemplateSource = "ET1_0224.pdf";
        pdfUploadService.welshPdfTemplateSource = "CY_ET1_0224.pdf";
        when(pdfService.convertCaseToPdf(any(),
                                         anyString(),
                                         anyString(),
                                         eq(CLIENT_TYPE_CLAIMANT),
                                         eq(SUBMIT_ET1_CITIZEN))).thenReturn(SAMPLE_BYTE_ARRAY);
    }

    @Test
    @SneakyThrows
    void shouldCreatePdfDecodedMultipartFileListFromCaseDataWhenUserInfoIsNull() {
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseTestData.getCaseData(), null);
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileListWhenUserInfoIsNotNull() {
        caseTestData.getCaseData().getClaimantIndType().setClaimantFirstNames(null);
        caseTestData.getCaseData().getClaimantIndType().setClaimantLastName(null);
        UserInfo userInfo = caseTestData.getUserInfo();
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseTestData.getCaseData(), userInfo);
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userInfoExists", "userInfoNotExists"})
    void shouldCreatePdfDecodedMultipartFileListFromCaseDataAccordingToUserInfo(String userInfoFlag) {
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList;
        if ("userInfoExists".equals(userInfoFlag)) {
            pdfDecodedMultipartFileList =
                pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseTestData.getCaseData(),
                                                                    caseTestData.getUserInfo());
        } else {
            pdfDecodedMultipartFileList =
                pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseTestData.getCaseData(), null);
        }
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @Test
    void shouldCreateEnglishAndWelshPdfDecodedMultipartFileFromCaseDataWhenUserContactLanguageIsWelsh() {
        caseTestData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfUploadService.convertCaseDataToPdfDecodedMultipartFile(caseTestData.getCaseData(), null);
        assertThat(pdfDecodedMultipartFileList).hasSize(2);
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromCaseDataAndAcasCertificate() {
        List<AcasCertificate> acasCertificates = new ArrayList<>();
        acasCertificates.add(acasCertificate);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles =
            pdfUploadService.convertAcasCertificatesToPdfDecodedMultipartFiles(
                caseTestData.getCaseData(), acasCertificates);
        assertThat(pdfDecodedMultipartFiles).hasSize(1);
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromTseApplication() throws DocumentGenerationException {
        caseTestData.getCaseData().setClaimantTse(new ClaimantTse());
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertClaimantTseIntoMultipartFile(
                caseTestData.getClaimantTse(),
                caseTestData.getCaseData().getEthosCaseReference(),
                CUSTOM_DOC_NAME);

        assertThat(pdfDecodedMultipartFile).isNotNull();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromTseApplicationNoSupportingFile() throws DocumentGenerationException {
        caseTestData.getClaimantTse().setContactApplicationFile(null);
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertClaimantTseIntoMultipartFile(
                caseTestData.getClaimantTse(),
                caseTestData.getCaseData().getEthosCaseReference(),
                CUSTOM_DOC_NAME);

        assertThat(pdfDecodedMultipartFile).isNotNull();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromClaimantResponse() throws DocumentGenerationException {
        RespondToApplicationRequest request = caseTestData.getRespondToApplicationRequest();
        GenericTseApplicationType application =
            caseTestData.getCaseData().getGenericTseApplicationCollection().get(0).getValue();
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertApplicationResponseIntoMultipartFile(request, "Response to app",
                                                                "6000001/2023", application,
                                                                         CLAIMANT_TITLE);
        assertThat(pdfDecodedMultipartFile).isNotNull();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromClaimantResponseNoSupportingFile() throws DocumentGenerationException {
        RespondToApplicationRequest request = caseTestData.getRespondToApplicationRequest();
        GenericTseApplicationType application =
            caseTestData.getCaseData().getGenericTseApplicationCollection().get(0).getValue();
        request.getResponse().setHasSupportingMaterial("No");
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertApplicationResponseIntoMultipartFile(request, "Response to app",
                                                                "6000001/2023", application,
                                                                         CLAIMANT_TITLE);
        assertThat(pdfDecodedMultipartFile).isNotNull();
    }

    @Test
    void shouldNotCreateWhenCertificateDocumentNotFound() {
        List<AcasCertificate> acasCertificates = new ArrayList<>();
        AcasCertificate acasCert = new AcasCertificate();
        acasCert.setCertificateDocument("not found");
        acasCertificates.add(acasCert);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles =
            pdfUploadService.convertAcasCertificatesToPdfDecodedMultipartFiles(
                caseTestData.getCaseData(), acasCertificates);
        assertThat(pdfDecodedMultipartFiles).isEmpty();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromRespTseApplication() throws DocumentGenerationException {
        caseTestData.getCaseData().setRespondentTse(new RespondentTse());
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertRespondentTseIntoMultipartFile(
                caseTestData.getRespondentTse(),
                caseTestData.getCaseData().getEthosCaseReference(),
                CUSTOM_DOC_NAME);

        assertThat(pdfDecodedMultipartFile).isNotNull();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromRespTseApplicationNoSupportingFile()
        throws DocumentGenerationException {
        caseTestData.getRespondentTse().setContactApplicationFile(null);
        PdfDecodedMultipartFile pdfDecodedMultipartFile =
            pdfUploadService.convertRespondentTseIntoMultipartFile(
                caseTestData.getRespondentTse(),
                caseTestData.getCaseData().getEthosCaseReference(),
                CUSTOM_DOC_NAME);

        assertThat(pdfDecodedMultipartFile).isNotNull();
    }
}
