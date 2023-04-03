package uk.gov.hmcts.reform.et.syaapi.service.pdf;

import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.tika.Tika;
import org.elasticsearch.core.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.et.syaapi.model.TestData;
import uk.gov.hmcts.reform.et.syaapi.models.AcasCertificate;
import uk.gov.hmcts.reform.et.syaapi.service.util.ServiceUtil;
import uk.gov.hmcts.reform.et.syaapi.utils.ResourceLoader;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLISH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CloseResource"})
class PdfServiceTest {
    private static final Map<String, Optional<String>> PDF_VALUES = Map.of(
        PdfMapperConstants.TRIBUNAL_OFFICE, Optional.of("Manchester"),
        PdfMapperConstants.CASE_NUMBER, Optional.of("001"),
        PdfMapperConstants.DATE_RECEIVED, Optional.of("21-07-2022")
    );
    private static final Map<String, Optional<String>> PDF_VALUES_WITH_NULL = Map.of(
        PdfMapperConstants.TRIBUNAL_OFFICE, Optional.of("Manchester"),
        PdfMapperConstants.CASE_NUMBER, Optional.of("001"),
        PdfMapperConstants.DATE_RECEIVED, Optional.of("")
    );

    private TestData testData;

    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_NAME = "englishPdfTemplateSource";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH = "ET1_1122.pdf";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH_INVALID = "ET1_0722.pdf";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH_NOT_EXISTS = "invalid_english.pdf";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_NAME_WELSH = "welshPdfTemplateSource";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH = "CY_ET1_2222.pdf";
    private static final String PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH_NOT_EXISTS = "invalid_welsh.pdf";
    private static final String PDF_FILE_TIKA_CONTENT_TYPE = "application/pdf";

    private final AcasCertificate acasCertificate = ResourceLoader.fromString(
        "requests/acasCertificate.json",
        AcasCertificate.class
    );

    @Mock
    private PdfMapperService pdfMapperService;
    @InjectMocks
    private PdfService pdfService;

    @BeforeEach
    void beforeEach() {
        testData = new TestData();
        ReflectionTestUtils.setField(
            pdfService,
            PDF_TEMPLATE_SOURCE_ATTRIBUTE_NAME,
            PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH
        );
        ReflectionTestUtils.setField(
            pdfService,
            PDF_TEMPLATE_SOURCE_ATTRIBUTE_NAME_WELSH,
            PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH
        );
    }

    @SneakyThrows
    @Test
    void givenPdfValuesProducesAPdfDocument() {
        when(pdfMapperService.mapHeadersToPdf(testData.getCaseData())).thenReturn(PDF_VALUES);
        byte[] pdfBytes = pdfService.convertCaseToPdf(testData.getCaseData(),
                                                      PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH
        );
        try (PDDocument actualPdf = Loader.loadPDF(pdfBytes)) {
            Map<String, Optional<String>> actualPdfValues = processPdf(actualPdf);
            PDF_VALUES.forEach((k, v) -> assertThat(actualPdfValues).containsEntry(k, v));
        }
    }

    @SneakyThrows
    @Test
    void givenNullValuesProducesDocumentWithoutGivenValues() {
        when(pdfMapperService.mapHeadersToPdf(testData.getCaseData())).thenReturn(PDF_VALUES_WITH_NULL);
        byte[] pdfBytes = pdfService.convertCaseToPdf(testData.getCaseData(),
                                                      PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH
        );
        try (PDDocument actualPdf = Loader.loadPDF(pdfBytes)) {
            Map<String, Optional<String>> actualPdfValues = processPdf(actualPdf);
            PDF_VALUES_WITH_NULL.forEach((k, v) -> assertThat(actualPdfValues).containsEntry(k, v));
        }
    }

    private Map<String, Optional<String>> processPdf(PDDocument pdDocument) {
        PDDocumentCatalog pdDocumentCatalog = pdDocument.getDocumentCatalog();
        PDAcroForm pdfForm = pdDocumentCatalog.getAcroForm();
        Map<String, Optional<String>> returnFields = new ConcurrentHashMap<>();
        pdfForm.getFields().forEach(
            field -> {
                Tuple<String, String> fieldTuple = processField(field);
                returnFields.put(fieldTuple.v1(), Optional.ofNullable(fieldTuple.v2()));
            }
        );
        return returnFields;
    }

    private Tuple<String, String> processField(PDField field) {
        if (field instanceof PDNonTerminalField) {
            for (PDField child : ((PDNonTerminalField) field).getChildren()) {
                processField(child);
            }
        }

        return new Tuple<>(field.getFullyQualifiedName(), field.getValueAsString());
    }

    @SneakyThrows
    @Test
    void shouldCreateEnglishPdfFile() {
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        pdfService1.englishPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH;
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(), PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH);
        assertThat(pdfData).isNotEmpty();
        assertThat(new Tika().detect(pdfData)).isEqualTo(PDF_FILE_TIKA_CONTENT_TYPE);
    }

    @SneakyThrows
    @Test
    void shouldNotCreateEnglishPdfFileWhenEnglishPdfTemplateIsNull() {
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(), null);
        assertThat(pdfData).isEmpty();
    }

    @SneakyThrows
    @Test
    void shouldNotCreateEnglishPdfFileWhenEnglishPdfTemplateNotExists() {
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        pdfService1.englishPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH_NOT_EXISTS;
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(), null);
        assertThat(pdfData).isEmpty();
    }

    @SneakyThrows
    @Test
    void shouldThrowExceptionWhenPdfTemplateIsNotValid() {
        try (MockedStatic<ServiceUtil> mockedServiceUtil = Mockito.mockStatic(ServiceUtil.class)) {
            mockedServiceUtil.when(() -> ServiceUtil.findClaimantLanguage(testData.getCaseData()))
                .thenReturn(ENGLISH_LANGUAGE);
            PdfService pdfService1 = new PdfService(new PdfMapperService());
            pdfService1.createPdf(testData.getCaseData(), PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH_INVALID);
            mockedServiceUtil.verify(
                () -> ServiceUtil.logException(anyString(), anyString(), anyString(), anyString(), anyString()),
                atLeast(1)
            );
        }
    }

    @SneakyThrows
    @Test
    void shouldCreateWelshPdfFile() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        pdfService1.welshPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH;
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(), PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH);
        assertThat(pdfData).isNotEmpty();
        assertThat(new Tika().detect(pdfData)).isEqualTo(PDF_FILE_TIKA_CONTENT_TYPE);
    }

    @SneakyThrows
    @Test
    void shouldNotCreateWelshPdfFileWhenWelshPdfTemplateIsNull() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(), null);
        assertThat(pdfData).isEmpty();
    }

    @SneakyThrows
    @Test
    void shouldNotCreateWelshPdfFileWhenWelshPdfTemplateNotExists() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        pdfService1.welshPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH_NOT_EXISTS;
        byte[] pdfData = pdfService1.createPdf(testData.getCaseData(),
                                               PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH_NOT_EXISTS);
        assertThat(pdfData).isEmpty();
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileListFromCaseDataWhenUserInfoIsNull() {
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfService.convertCaseDataToPdfDecodedMultipartFile(testData.getCaseData(), null);
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileListWhenUserInfoIsNotNull() {
        testData.getCaseData().getClaimantIndType().setClaimantFirstNames(null);
        testData.getCaseData().getClaimantIndType().setClaimantLastName(null);
        UserInfo userInfo = testData.getUserInfo();
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfService.convertCaseDataToPdfDecodedMultipartFile(testData.getCaseData(), userInfo);
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @Test
    void shouldCreateOnlyEnglishPdfDecodedMultipartFileListWhenUserContactLanguageIsEnglish() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(null);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfService.convertCaseDataToPdfDecodedMultipartFile(testData.getCaseData(), null);
        assertThat(pdfDecodedMultipartFileList).hasSize(1);
    }

    @Test
    void shouldCreateEnglishAndWelshPdfDecodedMultipartFileFromCaseDataWhenUserContactLanguageIsWelsh() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
            pdfService.convertCaseDataToPdfDecodedMultipartFile(testData.getCaseData(), null);
        assertThat(pdfDecodedMultipartFileList).hasSize(2);
    }

    @Test
    void shouldNotCreatePdfDecodedMultipartFileFromCaseDataWhenBothWelshAndEnglishTemplateSourcesNotExist() {
        testData.getCaseData().getClaimantHearingPreference().setContactLanguage(WELSH_LANGUAGE);
        PdfService pdfService1 = new PdfService(new PdfMapperService());
        pdfService1.welshPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH_NOT_EXISTS;
        pdfService1.englishPdfTemplateSource = PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_WELSH_NOT_EXISTS;
        try (MockedStatic<ServiceUtil> mockedServiceUtil = Mockito.mockStatic(ServiceUtil.class)) {
            mockedServiceUtil.when(() -> ServiceUtil.findClaimantLanguage(testData.getCaseData()))
                .thenReturn(WELSH_LANGUAGE);
            List<PdfDecodedMultipartFile> pdfDecodedMultipartFileList =
                pdfService1.convertCaseDataToPdfDecodedMultipartFile(testData.getCaseData(), null);
            assertThat(pdfDecodedMultipartFileList).hasSize(0);
            mockedServiceUtil.verify(
                () -> ServiceUtil.logException(anyString(), anyString(), anyString(), anyString(), anyString()),
                times(2)
            );
        }
    }

    @Test
    void shouldCreatePdfDecodedMultipartFileFromCaseDataAndAcasCertificate() {
        List<AcasCertificate> acasCertificates = new ArrayList<>();
        acasCertificates.add(acasCertificate);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles =
            pdfService.convertAcasCertificatesToPdfDecodedMultipartFiles(testData.getCaseData(), acasCertificates);
        assertThat(pdfDecodedMultipartFiles).hasSize(1);
    }

    @Test
    void shouldNotCreateWhenCertificateDocumentNotFound() {
        List<AcasCertificate> acasCertificates = new ArrayList<>();
        AcasCertificate acasCert = new AcasCertificate();
        acasCert.setCertificateDocument("not found");
        acasCertificates.add(acasCert);
        List<PdfDecodedMultipartFile> pdfDecodedMultipartFiles =
            pdfService.convertAcasCertificatesToPdfDecodedMultipartFiles(testData.getCaseData(), acasCertificates);
        assertThat(pdfDecodedMultipartFiles).isEmpty();
    }

    @Test
    void shouldConvertCaseToPdfThrowPdfServiceExceptionWhenCreatePdfThrowsIoException() {
        try (MockedStatic<Loader> mockedLoader = Mockito.mockStatic(Loader.class)) {
            mockedLoader.when(() -> Loader.loadPDF(any(InputStream.class))).thenThrow(new IOException());
            PdfServiceException thrown = assertThrows(PdfServiceException.class, () ->
                pdfService.convertCaseToPdf(testData.getCaseData(), PDF_TEMPLATE_SOURCE_ATTRIBUTE_VALUE_ENGLISH));
            assertEquals("Failed to convert to PDF", thrown.getMessage());
        }
    }

    @SneakyThrows
    @Test
    void shouldThrowExceptionWhenInputStreamNotClosed() {
        try (MockedStatic<ServiceUtil> mockedServiceUtil = Mockito.mockStatic(ServiceUtil.class)) {
            InputStream is = Mockito.mock(InputStream.class);
            doThrow(new IOException("Test IOException")).when(is).close();
            PdfService.safeClose(is, testData.getCaseData());
            mockedServiceUtil.verify(
                () -> ServiceUtil.logException(anyString(), anyString(), anyString(), anyString(), anyString()),
                times(1)
            );
        }
    }
}
