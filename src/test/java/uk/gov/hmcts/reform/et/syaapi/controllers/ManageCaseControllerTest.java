package uk.gov.hmcts.reform.et.syaapi.controllers;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import lombok.SneakyThrows;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.search.Query;
import uk.gov.hmcts.reform.et.syaapi.service.CaseService;
import uk.gov.hmcts.reform.et.syaapi.service.VerifyTokenService;
import uk.gov.hmcts.reform.et.syaapi.utils.ResourceLoader;
import uk.gov.hmcts.reform.et.syaapi.utils.ResourceUtil;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.TEST_CASE_ID;
import static uk.gov.hmcts.reform.et.syaapi.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

@WebMvcTest(
    controllers = {ManageCaseController.class}
)
@Import(ManageCaseController.class)
class ManageCaseControllerTest {

    private static final String CASE_ID = "1646225213651590";
    private static final String USER_ID = "1234";
    private static final String CASE_TYPE = "ET_Scotland";

    private final CaseDetails expectedDetails = ResourceLoader.fromString(
        "responses/caseDetails.json",
        CaseDetails.class
    );
    private final String requestCaseData = ResourceUtil.resourceAsString(
        "requests/caseData.json"
    );

    private final List<CaseDetails> requestCaseDataList = ResourceLoader.fromStringToList(
        "responses/caseDetailsList.json",
        CaseDetails.class
    );

    private final StartEventResponse startEventResponse = ResourceLoader.fromString(
        "responses/startEventResponse.json",
        StartEventResponse.class
    );

    private final CaseData caseData = ResourceLoader.fromString(
        "requests/caseData.json",
        CaseData.class
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    @MockBean
    private IdamClient idamClient;

    @MockBean
    private VerifyTokenService verifyTokenService;

    @MockBean
    private CaseDetailsConverter caseDetailsConverter;

    ManageCaseControllerTest() throws IOException {
        // Default constructor
    }

    @SneakyThrows
    @Test
    void shouldGetCaseDetails() {
        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(caseService.getCaseData(TEST_SERVICE_AUTH_TOKEN, CASE_ID))
            .thenReturn(expectedDetails);

        // when
        mockMvc.perform(get("/caseDetails/{caseId}", CASE_ID)
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(expectedDetails.getId()))
            .andExpect(jsonPath("$.case_type_id").value(expectedDetails.getCaseTypeId()))
            .andExpect(jsonPath("$.jurisdiction").value(expectedDetails.getJurisdiction()))
            .andExpect(jsonPath("$.state").value(expectedDetails.getState()))
            .andExpect(jsonPath("$.created_date").exists())
            .andExpect(jsonPath("$.last_modified").exists());
    }

    @SneakyThrows
    @Test
    void shouldGetCaseDetailsByUser() {
        // given
        String searchString = "{\"match_all\": {}}";
        Query query = new Query(QueryBuilders.wrapperQuery(searchString), 0);

        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserDetails(TEST_SERVICE_AUTH_TOKEN)).thenReturn(UserDetails.builder().id(USER_ID).build());
        when(caseService.getCaseDataByUser(
            TEST_SERVICE_AUTH_TOKEN,
            CASE_TYPE, query.toString()
        ))
            .thenReturn(requestCaseDataList);

        // when
        mockMvc.perform(
                get("/caseTypes/{caseType}/cases", CASE_TYPE)
                    .contentType(MediaType.APPLICATION_JSON).content(searchString)
                    .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN))
            // then
            .andExpect(status().isOk())
            .andExpect(jsonPath("[0].case_type_id").value(requestCaseDataList.get(0).getCaseTypeId()))
            .andExpect(jsonPath("[0].jurisdiction").value(requestCaseDataList.get(0).getJurisdiction()))
            .andExpect(jsonPath("[0].state").value(requestCaseDataList.get(0).getState()))
            .andExpect(jsonPath("[0].created_date").exists())
            .andExpect(jsonPath("[0].last_modified").exists())
            .andExpect(jsonPath("[1].case_type_id").value(requestCaseDataList.get(1).getCaseTypeId()));
    }

    @SneakyThrows
    @Test
    void shouldReturnBadRequestForNonExistingItem() {
        Request request = Request.create(
            Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(caseService.getCaseData(any(), any())).thenThrow(new FeignException.BadRequest(
            "Bad request",
            request,
            "incorrect payload".getBytes(StandardCharsets.UTF_8),
            Collections.emptyMap()
        ));
        mockMvc.perform(get("/caseDetails/{caseId}", CASE_ID)
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Bad request - incorrect payload"));
    }

    @SneakyThrows
    @Test
    void shouldCreateDraftCase() {
        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(caseService.createCase(
            TEST_SERVICE_AUTH_TOKEN,
            EtSyaConstants.SCOTLAND_CASE_TYPE,
            EtSyaConstants.DRAFT_EVENT_TYPE,
            new HashMap<>()
        ))
            .thenReturn(expectedDetails);

        // when
        mockMvc.perform(post(
                            "/case-type/{caseType}/event-type/{eventType}/case",
                            EtSyaConstants.SCOTLAND_CASE_TYPE,
                            EtSyaConstants.DRAFT_EVENT_TYPE
                        )
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                            .content(requestCaseData)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.case_type_id").value(expectedDetails.getCaseTypeId()))
            .andExpect(jsonPath("$.id").value(expectedDetails.getId()))
            .andExpect(jsonPath("$.jurisdiction").value(expectedDetails.getJurisdiction()))
            .andExpect(jsonPath("$.state").value(expectedDetails.getState()))
            .andExpect(jsonPath("$.case_data.caseType").value("Single"))
            .andExpect(jsonPath("$.case_data.caseSource").value("Manually Created"))
            .andExpect(jsonPath("$.created_date").exists())
            .andExpect(jsonPath("$.last_modified").exists());
    }

    @SneakyThrows
    @Test
    void shouldStartUpdateCase() {
        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserDetails(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserDetails(
            "12",
            "test@gmail.com",
            "Joe",
            "Bloggs",
            null
        ));

        when(caseService.startUpdate(
                 TEST_SERVICE_AUTH_TOKEN,
                 TEST_CASE_ID,
                 EtSyaConstants.SCOTLAND_CASE_TYPE,
                 CaseEvent.UPDATE_CASE_DRAFT
             )
        ).thenReturn(
            startEventResponse);

        when(caseService.submitUpdate(
            TEST_SERVICE_AUTH_TOKEN,
            TEST_CASE_ID,
            caseDetailsConverter.caseDataContent(startEventResponse, null),
            EtSyaConstants.SCOTLAND_CASE_TYPE, caseDetailsConverter)
        ).thenReturn(caseData);

        // when
        mockMvc.perform(put(
                "/case-type/{caseType}/event-type/{eventType}/{caseId}}",
                EtSyaConstants.SCOTLAND_CASE_TYPE,
                CaseEvent.UPDATE_CASE_DRAFT,
                TEST_CASE_ID
                        )
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                            .content(requestCaseData)
            )
            .andExpect(status().isOk());
    }

}
