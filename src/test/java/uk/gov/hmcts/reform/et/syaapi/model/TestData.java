package uk.gov.hmcts.reform.et.syaapi.model;

import lombok.Data;
import org.junit.jupiter.params.provider.Arguments;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.models.AdminDecisionNotificationStateUpdateRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantBundlesRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondentApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SendNotificationAddResponseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SendNotificationStateUpdateRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;
import uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants;
import uk.gov.hmcts.reform.et.syaapi.service.utils.data.TestDataProvider;

import java.util.stream.Stream;

import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.MULTIPLE_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TRUE;

@Data
@SuppressWarnings("PMD.TooManyFields")
public final class TestData {
    private final CaseDataContent submitCaseDataContent = ResourceLoader.fromString(
        "requests/submitCaseDataContent.json",
        CaseDataContent.class
    );

    private final StartEventResponse updateCaseEventResponse = ResourceLoader.fromString(
        "responses/updateCaseEventResponse.json",
        StartEventResponse.class
    );

    private final StartEventResponse updateCaseEventResponseWithClaimantResponse = ResourceLoader.fromString(
        "responses/updateCaseEventResponseWithClaimantResponse.json",
        StartEventResponse.class
    );

    private final StartEventResponse startEventResponse = ResourceLoader.fromString(
        "responses/startEventResponse.json",
        StartEventResponse.class
    );

    private final StartEventResponse sendNotificationCollectionResponse = ResourceLoader.fromString(
        "responses/sendNotificationCollectionResponse.json",
        StartEventResponse.class
    );

    private final RespondToApplicationRequest respondToApplicationRequest = ResourceLoader.fromString(
        "requests/respondToApplication.json",
        RespondToApplicationRequest.class
    );

    private final CaseDetails caseDetailsWithData = ResourceLoader.fromString(
        "responses/caseDetailsWithCaseData.json",
        CaseDetails.class
    );

    private final CaseData caseData = ResourceLoader.fromString(
        "requests/caseData.json",
        CaseData.class
    );

    private final ChangeApplicationStatusRequest changeApplicationStatusRequest = ResourceLoader.fromString(
        "requests/viewAnApplication.json",
        ChangeApplicationStatusRequest.class
    );

    private final ClaimantApplicationRequest claimantApplicationRequest = ResourceLoader.fromString(
        "requests/claimantTseRequest.json",
        ClaimantApplicationRequest.class
    );

    private final RespondentApplicationRequest respondentApplicationRequest = ResourceLoader.fromString(
        "requests/respondentTseRequest.json",
        RespondentApplicationRequest.class
    );

    private final SendNotificationAddResponseRequest sendNotificationAddResponseRequest = ResourceLoader.fromString(
        "requests/SendNotificationAddResponseRequest.json",
        SendNotificationAddResponseRequest.class
    );
    private final SendNotificationStateUpdateRequest sendNotificationStateUpdateRequest = ResourceLoader.fromString(
        "requests/sendNotificationStateUpdateRequest.json",
        SendNotificationStateUpdateRequest.class
    );

    private final AdminDecisionNotificationStateUpdateRequest adminDecisionNotificationStateUpdateRequest =
        ResourceLoader.fromString(
            "requests/adminNotificationUpdateRequest.json",
            AdminDecisionNotificationStateUpdateRequest.class
        );

    private final TribunalResponseViewedRequest responseViewedRequest =
        ResourceLoader.fromString(
            "requests/tribunalResponseViewedRequest.json",
            TribunalResponseViewedRequest.class
        );

    private final ClaimantBundlesRequest claimantBundlesRequest = ResourceLoader.fromString(
        "requests/claimantBundlesRequest.json",
        ClaimantBundlesRequest.class
    );

    public static Stream<Arguments> generateCaseDataArgumentsForTheTestFindClaimantLanguage() {
        CaseData caseDataNullClaimantHearingPreferences = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TRUE, TestConstants.NULL_STRING);
        CaseData caseDataNullContactLanguage = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TestConstants.FALSE,
                                                                            TestConstants.NULL_STRING);
        CaseData caseDataEmptyContactLanguage = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TestConstants.FALSE,
                                                                            TestConstants.EMPTY_STRING);
        CaseData caseDataInvalidContactLanguage = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TestConstants.FALSE,
                                                                            TestConstants.INVALID_LANGUAGE);
        CaseData caseDataWelshContactLanguage = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TestConstants.FALSE,
                                                                            TestConstants.WELSH_LANGUAGE);
        CaseData caseDataEnglishContactLanguage = TestDataProvider
            .generateTestCaseDataByClaimantHearingPreferenceContactLanguage(TestConstants.FALSE,
                                                                            TestConstants.ENGLISH_LANGUAGE);

        return Stream.of(
            Arguments.of(caseDataNullClaimantHearingPreferences, TestConstants.ENGLISH_LANGUAGE),
            Arguments.of(caseDataNullContactLanguage, TestConstants.ENGLISH_LANGUAGE),
            Arguments.of(caseDataEmptyContactLanguage, TestConstants.ENGLISH_LANGUAGE),
            Arguments.of(caseDataInvalidContactLanguage, TestConstants.ENGLISH_LANGUAGE),
            Arguments.of(caseDataWelshContactLanguage, TestConstants.WELSH_LANGUAGE),
            Arguments.of(caseDataEnglishContactLanguage, TestConstants.ENGLISH_LANGUAGE)
        );
    }

    public static Stream<Arguments> generatePdfFileListForTestingHasPdfFileByGivenIndex() {
        return Stream.of(
            Arguments.of(null, 0, false),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST, 0, false),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0, false),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0, false),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0, true)
        );
    }

    public static Stream<Arguments> generatePdfFileListForTestingPrepareUploadByGivenIndex() {
        return Stream.of(
            Arguments.of(null, 0, TestConstants.FILE_NOT_EXISTS),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST, 0,
                         TestConstants.FILE_NOT_EXISTS),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0,
                         TestConstants.FILE_NOT_EXISTS),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0,
                         TestConstants.FILE_NOT_EXISTS),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, 0,
                         TestConstants.PREPARE_PDF_UPLOAD_JSON_OBJECT)
        );
    }

    public static Stream<Arguments> generatePdfFileListForTestingFindPdfFileBySelectedLanguage() {
        return Stream.of(
            Arguments.of(null, TestConstants.WELSH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(null, TestConstants.ENGLISH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST, TestConstants.WELSH_LANGUAGE,
                         TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST, TestConstants.ENGLISH_LANGUAGE,
                         TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.WELSH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.ENGLISH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.WELSH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.ENGLISH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.WELSH_LANGUAGE, TestConstants.EMPTY_BYTE_ARRAY),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.ENGLISH_LANGUAGE,
                         NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST.get(0).getBytes()),
            Arguments.of(MULTIPLE_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.WELSH_LANGUAGE,
                         MULTIPLE_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST.get(1).getBytes()),
            Arguments.of(MULTIPLE_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.ENGLISH_LANGUAGE,
                         MULTIPLE_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST.get(0).getBytes())
        );
    }

}
