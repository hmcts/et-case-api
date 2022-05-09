package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.EqualsAndHashCode;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.regex.InvalidPostcodeException;
import uk.gov.hmcts.ecm.common.model.helper.TribunalOffice;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.client.CcdApiClient;
import uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.search.Query;
import uk.gov.hmcts.reform.et.syaapi.utils.ResourceLoader;
import uk.gov.hmcts.reform.et.syaapi.utils.ResourceUtil;
import uk.gov.hmcts.reform.et.syaapi.utils.TestConstants;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.UPDATE_CASE_DRAFT;
import static uk.gov.hmcts.reform.et.syaapi.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

@EqualsAndHashCode
@ExtendWith(MockitoExtension.class)
class CaseServiceTest {
    private static final String CASE_TYPE = "ET_Scotland";
    private static final String CASE_ID = "TEST_CASE_ID";

    @Mock
    private PostcodeToOfficeService postcodeToOfficeService;

    private final CaseDetails expectedDetails = ResourceLoader.fromString(
        "responses/caseDetails.json",
        CaseDetails.class
    );
    private final StartEventResponse startEventResponse = ResourceLoader.fromString(
        "responses/startEventResponse.json",
        StartEventResponse.class
    );
    private final String requestCaseData = ResourceUtil.resourceAsString(
        "requests/caseData.json"
    );
    private final Et1CaseData caseData = ResourceLoader.fromString(
        "requests/caseData.json",
        Et1CaseData.class
    );

    private final List<CaseDetails> requestCaseDataList = ResourceLoader.fromStringToList(
        "responses/caseDetailsList.json",
        CaseDetails.class
    );

    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private CcdApiClient ccdApiClient;
    @Mock
    private IdamClient idamClient;

    @InjectMocks
    private CaseService caseService;
    @Mock
    private CaseDetailsConverter caseDetailsConverter;

    @Mock
    SearchResult searchResult;

    CaseServiceTest() throws IOException {
        // Default constructor
    }

    @Test
    void shouldGetUserCase() {
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(ccdApiClient.getCase(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            CASE_ID
        )).thenReturn(expectedDetails);

        CaseRequest caseRequest = CaseRequest.builder()
            .caseId(CASE_ID).build();

        CaseDetails caseDetails = caseService.getUserCase(TEST_SERVICE_AUTH_TOKEN, caseRequest);

        assertEquals(expectedDetails, caseDetails);
    }

    @Test
    void shouldGetAllUserCases() {
        searchResult.setCases(requestCaseDataList);
        String searchString = "{\"match_all\": {}}";
        Query query = new Query(QueryBuilders.wrapperQuery(searchString), 0);

        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(ccdApiClient.searchCases(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            query.toString()
        )).thenReturn(searchResult);

        CaseRequest caseRequest = CaseRequest.builder()
            .caseTypeId(EtSyaConstants.SCOTLAND_CASE_TYPE).build();

        List<CaseDetails> expectedDataList = caseService.getAllUserCases(TEST_SERVICE_AUTH_TOKEN, caseRequest);
        assertEquals(searchResult.getCases(), expectedDataList);
    }

    @Test
    void shouldCreateNewDraftCaseInCcd() throws InvalidPostcodeException {
        CaseDataContent caseDataContent = CaseDataContent.builder()
            .event(Event.builder().id(EtSyaConstants.DRAFT_EVENT_TYPE).build())
            .eventToken(startEventResponse.getToken())
            .data(caseData)
            .build();
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserDetails(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserDetails(
            "12",
            "test@gmail.com",
            "Joe",
            "Bloggs",
            null
        ));
        when(ccdApiClient.startForCaseworker(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            "12",
            EtSyaConstants.JURISDICTION_ID,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            EtSyaConstants.DRAFT_EVENT_TYPE
        )).thenReturn(
            startEventResponse);

        when(ccdApiClient.submitForCaseworker(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            "12",
            EtSyaConstants.JURISDICTION_ID,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            true,
            caseDataContent
        )).thenReturn(expectedDetails);

        when(postcodeToOfficeService.getTribunalOfficeFromPostcode(any()))
            .thenReturn(Optional.of(TribunalOffice.ABERDEEN));

        CaseRequest caseRequest = CaseRequest.builder()
            .postCode("AB10 1AH")
            .caseData(new HashMap<>())
            .build();

        CaseDetails caseDetails = caseService.createCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );

        assertEquals(expectedDetails, caseDetails);
    }

    @Test
    void shouldStartUpdateCaseInCcd() {
        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserDetails(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserDetails(
            "12",
            "test@gmail.com",
            "Joe",
            "Bloggs",
            null
        ));

        when(ccdApiClient.startEventForCaseWorker(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            "12",
            EtSyaConstants.JURISDICTION_ID,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            CASE_ID,
            TestConstants.UPDATE_CASE_DRAFT
        )).thenReturn(
            startEventResponse);

        StartEventResponse eventResponse = caseService.startUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            CASE_ID,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            UPDATE_CASE_DRAFT
        );

        assertEquals(eventResponse.getCaseDetails().getCaseTypeId(), CASE_TYPE);
    }

    @Test
    void shouldSubmitUpdateCaseInCcd() {
        CaseDataContent caseDataContent = CaseDataContent.builder()
            .event(Event.builder().id(String.valueOf(UPDATE_CASE_DRAFT)).build())
            .eventToken(startEventResponse.getToken())
            .data(caseData)
            .build();

        when(authTokenGenerator.generate()).thenReturn(TEST_SERVICE_AUTH_TOKEN);
        when(idamClient.getUserDetails(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserDetails(
            "12",
            "test@gmail.com",
            "Joe",
            "Bloggs",
            null
        ));

        when(ccdApiClient.submitEventForCaseWorker(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            "12",
            EtSyaConstants.JURISDICTION_ID,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            CASE_ID,
            true,
            caseDataContent
        )).thenReturn(expectedDetails);

        CaseDetails caseDetails = caseService.submitUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            CASE_ID,
            caseDataContent,
            EtSyaConstants.SCOTLAND_CASE_TYPE
        );

        assertEquals(caseDetails, expectedDetails);
    }
}
