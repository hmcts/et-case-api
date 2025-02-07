package uk.gov.hmcts.reform.et.syaapi.model;

import lombok.Data;
import org.junit.jupiter.params.provider.Arguments;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et3Request;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantIndType;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceUtil;
import uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants;
import uk.gov.hmcts.reform.et.syaapi.service.utils.data.TestDataProvider;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.service.notify.SendEmailResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.MICHAEL;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST;

@Data
@SuppressWarnings({"PMD.TooManyFields"})
public final class CaseTestData {

    private final CaseData caseData = ResourceLoader.fromString(
        "requests/caseData.json",
        CaseData.class
    );
    private final CaseRequest caseDataWithClaimTypes = ResourceLoader.fromString(
        "requests/caseDataWithClaimTypes.json",
        CaseRequest.class
    );


    private final ClaimantTse claimantApplication = ResourceLoader.fromString(
        "responses/claimantTse.json",
        ClaimantTse.class
    );
    private final RespondentTse respondentApplication = ResourceLoader.fromString(
        "responses/respondentTse.json",
        RespondentTse.class
    );
    private final CaseRequest caseRequest = ResourceLoader.fromString(
        "requests/caseRequest.json",
        CaseRequest.class
    );
    private final CaseRequest caseRequestWithoutManagingAddress = ResourceLoader.fromString(
        "requests/caseDataWithoutManagingAddress.json",
        CaseRequest.class
    );
    private final CaseRequest emptyCaseRequest = ResourceLoader.fromString(
        "requests/noManagingOfficeAndRespondentsAddressCaseRequest.json",
        CaseRequest.class
    );
    private final CaseRequest englandWalesRequest = ResourceLoader.fromString(
        "requests/caseRequestEnglandWales.json",
        CaseRequest.class
    );
    private final List<CaseDetails> expectedCaseDataListCombined = ResourceLoader.fromStringToList(
        "responses/caseDetailsCombined.json",
        CaseDetails.class
    );
    private final CaseDetails expectedDetails = ResourceLoader.fromString(
        "responses/caseDetails.json",
        CaseDetails.class
    );
    private final List<CaseDetails> requestCaseDataList = ResourceLoader.fromStringToList(
        "responses/caseDetailsList.json",
        CaseDetails.class
    );
    private final List<CaseDetails> requestCaseDataListEngland = ResourceLoader.fromStringToList(
        "responses/caseDetailsEngland.json",
        CaseDetails.class
    );
    private final List<CaseDetails> requestCaseDataListScotland = ResourceLoader.fromStringToList(
        "responses/caseDetailsScotland.json",
        CaseDetails.class
    );

    private final StartEventResponse startEventResponse = ResourceLoader.fromString(
        "responses/startEventResponse.json",
        StartEventResponse.class
    );
    private final List<DocumentTypeItem> uploadDocumentResponse = ResourceLoader.fromStringToList(
        "responses/documentTypeItemList.json",
        DocumentTypeItem.class
    );
    private final UserInfo userInfo = ResourceLoader.fromString(
        "responses/userInfo.json",
        UserInfo.class
    );
    private final List<CaseDetails> requestCaseDataListEnglandAcas = ResourceLoader.fromStringToList(
        "responses/caseDetailsEnglandAcasDocs.json",
        CaseDetails.class
    );

    private final RespondToApplicationRequest respondToApplicationRequest = ResourceLoader.fromString(
        "requests/respondToApplication.json",
        RespondToApplicationRequest.class
    );

    private final CaseDetails caseDetails = ResourceLoader.fromString(
        "responses/caseDetails.json",
        CaseDetails.class
    );

    private final CaseDetails caseDetailsWithCaseData = ResourceLoader.fromString(
        "responses/caseDetailsWithCaseData.json",
        CaseDetails.class
    );

    private final ClaimantTse claimantTse = ResourceLoader.fromString(
        "requests/claimantTse.json",
        ClaimantTse.class
    );

    private final RespondentTse respondentTse = ResourceLoader.fromString(
        "requests/respondentTse.json",
        RespondentTse.class
    );

    private final Et3Request et3Request = ResourceLoader.fromString(
        "requests/et3-request-data.json",
        Et3Request.class
    );

    public SendEmailResponse getSendEmailResponse() throws IOException {
        String sendEmailResponseStringVal = ResourceUtil.resourceAsString(
            "responses/sendEmailResponse.json"
        );
        return new SendEmailResponse(sendEmailResponseStringVal);
    }

    public static Stream<Arguments> generateSubmitCaseConfirmationEmailPdfFilesArguments() {
        return Stream.of(
            Arguments.of(null, TestConstants.EMPTY_RESPONSE),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST, TestConstants.EMPTY_RESPONSE),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, TestConstants.EMPTY_RESPONSE),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST, TestConstants.EMPTY_RESPONSE),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.TEST_SUBMIT_CASE_PDF_FILE_RESPONSE)
        );
    }

    public static Stream<Arguments> generateSendDocUploadErrorEmailPdfFilesArguments() {
        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.NULL_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.NULL_UPLOADED_DOCUMENT_TYPE_FILE),
            Arguments.of(TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.EMPTY_UPLOADED_DOCUMENT_TYPE_FILE),
            Arguments.of(TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.EMPTY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.EMPTY_UPLOADED_DOCUMENT_TYPE),
            Arguments.of(NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         NOT_EMPTY_BYTE_ARRAY_PDF_DECODED_MULTIPART_FILE_LIST,
                         TestConstants.NOT_EMPTY_UPLOADED_DOCUMENT_TYPE_FILE)
        );
    }

    public Map<String, Object> getCaseRequestCaseDataMap() {
        Et1CaseData et1CaseData = ResourceLoader.fromString("requests/caseData.json", Et1CaseData.class);
        Map<String, Object> requestCaseData = new ConcurrentHashMap<>();
        requestCaseData.put("typesOfClaim", et1CaseData.getTypesOfClaim());
        requestCaseData.put("caseType", et1CaseData.getEcmCaseType());
        requestCaseData.put("caseSource", et1CaseData.getCaseSource());
        requestCaseData.put("claimantRepresentedQuestion", et1CaseData.getClaimantRepresentedQuestion());
        requestCaseData.put("jurCodesCollection", et1CaseData.getJurCodesCollection());
        requestCaseData.put("claimantIndType", et1CaseData.getClaimantIndType());
        requestCaseData.put("claimantType", et1CaseData.getClaimantType());
        requestCaseData.put("representativeClaimantType", et1CaseData.getRepresentativeClaimantType());
        requestCaseData.put("claimantOtherType", et1CaseData.getClaimantOtherType());
        requestCaseData.put("respondentCollection", et1CaseData.getRespondentCollection());
        requestCaseData.put("claimantWorkAddress", et1CaseData.getClaimantWorkAddress());
        requestCaseData.put("caseNotes", et1CaseData.getCaseNotes());
        requestCaseData.put("managingOffice", et1CaseData.getManagingOffice());
        requestCaseData.put("newEmploymentType", et1CaseData.getNewEmploymentType());
        requestCaseData.put("claimantRequests", et1CaseData.getClaimantRequests());
        requestCaseData.put("claimantHearingPreference", et1CaseData.getClaimantHearingPreference());
        requestCaseData.put("claimantTaskListChecks", et1CaseData.getClaimantTaskListChecks());
        return requestCaseData;
    }

    public static Stream<Arguments> generateCaseDataUserInfoArgumentsForTestingFirstNames() {

        CaseData caseDataNullClaimantIndType =
            TestDataProvider.generateTestCaseDataByFirstNames(TestConstants.CLAIMANT_IND_TYPE_NULL,
                                                              TestConstants.EMPTY_STRING);
        CaseData caseDataEmptyClaimantIndType =
            TestDataProvider.generateTestCaseDataByFirstNames(TestConstants.CLAIMANT_IND_TYPE_EMPTY,
                                                              TestConstants.EMPTY_STRING);
        CaseData caseDataNullClaimantFirstNames =
            TestDataProvider.generateTestCaseDataByFirstNames(TestConstants.CLAIMANT_IND_TYPE_FILLED,
                                                              TestConstants.NULL_STRING);
        CaseData caseDataEmptyClaimantFirstNames =
            TestDataProvider.generateTestCaseDataByFirstNames(TestConstants.CLAIMANT_IND_TYPE_FILLED,
                                                              TestConstants.EMPTY_STRING);
        CaseData caseDataNotEmptyClaimantFirstNames =
            TestDataProvider.generateTestCaseDataByFirstNames(TestConstants.CLAIMANT_IND_TYPE_FILLED, MICHAEL);

        CaseTestData tmpCaseTestData = new CaseTestData();

        return Stream.of(
            Arguments.of(caseDataNullClaimantIndType, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getGivenName()),
            Arguments.of(caseDataEmptyClaimantIndType, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getGivenName()),
            Arguments.of(caseDataNullClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getGivenName()),
            Arguments.of(caseDataEmptyClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getGivenName()),
            Arguments.of(caseDataNotEmptyClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getCaseData().getClaimantIndType().getClaimantFirstNames())
        );
    }

    public static Stream<Arguments> generateCaseDataUserInfoArgumentsForTestingLastName() {

        CaseData caseDataNullClaimantIndType = new CaseTestData().getCaseData();
        caseDataNullClaimantIndType.setClaimantIndType(null);

        CaseData caseDataEmptyClaimantIndType = new CaseTestData().getCaseData();
        caseDataEmptyClaimantIndType.setClaimantIndType(new ClaimantIndType());

        CaseData caseDataNullClaimantFirstNames = new CaseTestData().getCaseData();
        caseDataNullClaimantFirstNames.getClaimantIndType().setClaimantLastName(TestConstants.NULL_STRING);

        CaseData caseDataEmptyClaimantFirstNames = new CaseTestData().getCaseData();
        caseDataEmptyClaimantFirstNames.getClaimantIndType().setClaimantLastName(TestConstants.EMPTY_STRING);

        CaseData caseDataNotEmptyClaimantFirstNames = new CaseTestData().getCaseData();

        CaseTestData tmpCaseTestData = new CaseTestData();

        return Stream.of(
            Arguments.of(caseDataNullClaimantIndType, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getFamilyName()),
            Arguments.of(caseDataEmptyClaimantIndType, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getFamilyName()),
            Arguments.of(caseDataNullClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getFamilyName()),
            Arguments.of(caseDataEmptyClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getUserInfo().getFamilyName()),
            Arguments.of(caseDataNotEmptyClaimantFirstNames, tmpCaseTestData.getUserInfo(),
                         tmpCaseTestData.getCaseData().getClaimantIndType().getClaimantLastName())
        );
    }

    public CaseDataContent getUpdateCaseDataContent() {
        return CaseDataContent.builder()
            .event(Event.builder().id(TestConstants.UPDATE_CASE_DRAFT).build())
            .eventToken(getStartEventResponse().getToken())
            .data(getCaseRequestCaseDataMap())
            .build();
    }

    public SearchResult getSearchResultRequestCaseDataListScotland() {
        SearchResult searchResult = SearchResult.builder().build();
        searchResult.setCases(getRequestCaseDataListScotland());
        searchResult.setTotal(2);
        return searchResult;
    }

    public SearchResult getSearchResultRequestCaseDataListEngland() {
        SearchResult searchResult = SearchResult.builder().build();
        searchResult.setCases(getRequestCaseDataListEngland());
        searchResult.setTotal(1);
        return searchResult;
    }

}
