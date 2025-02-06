package uk.gov.hmcts.reform.et.syaapi.controllers;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.HubLinksStatuses;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.HubLinksStatusesRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.ApplicationService;
import uk.gov.hmcts.reform.et.syaapi.service.CaseService;
import uk.gov.hmcts.reform.et.syaapi.service.HubLinkService;
import uk.gov.hmcts.reform.et.syaapi.service.ManageCaseRoleService;
import uk.gov.hmcts.reform.et.syaapi.service.RespondToApplicationService;
import uk.gov.hmcts.reform.et.syaapi.service.VerifyTokenService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SCOTLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.ManageCaseRoleConstants.CASE_USER_ROLE_CREATOR;
import static uk.gov.hmcts.reform.et.syaapi.constants.ManageCaseRoleConstants.CASE_USER_ROLE_DEFENDANT;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_FIRST_NAME;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_NAME;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SURNAME;

@WebMvcTest(
    controllers = {ManageCaseController.class}
)
@Import(ManageCaseController.class)
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
class ManageCaseControllerTest {

    private static final String CASE_ID = "1646225213651590";
    private static final String USER_ID = "1234";

    private static final String UPDATE_CASE_SUBMITTED_API_URL = "/cases/update-case-submitted";

    private final CaseDetails expectedDetails;

    private final List<CaseDetails> requestCaseDataList;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    @MockBean
    private IdamClient idamClient;

    @MockBean
    private ManageCaseRoleService manageCaseRoleService;

    @MockBean
    private VerifyTokenService verifyTokenService;

    @MockBean
    private ApplicationService applicationService;
    @MockBean
    private RespondToApplicationService respondToApplicationService;
    @MockBean
    private HubLinkService hubLinkService;

    ManageCaseControllerTest() {
        // Default constructor
        expectedDetails = ResourceLoader.fromString(
            "responses/caseDetails.json",
            CaseDetails.class
        );

        requestCaseDataList = ResourceLoader.fromStringToList(
            "responses/caseDetailsList.json",
            CaseDetails.class
        );
    }

    @Test
    @SneakyThrows
    void shouldGetCaseDetails() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseId(CASE_ID).build();

        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(manageCaseRoleService.getUserCaseByCaseUserRole(TEST_SERVICE_AUTH_TOKEN,
                                                             caseRequest.getCaseId(),
                                                             CASE_USER_ROLE_CREATOR))
            .thenReturn(expectedDetails);
        // when
        mockMvc.perform(post("/cases/user-case", CASE_ID)
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ResourceLoader.toJson(caseRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(expectedDetails.getId()))
            .andExpect(jsonPath("$.case_type_id").value(expectedDetails.getCaseTypeId()))
            .andExpect(jsonPath("$.jurisdiction").value(expectedDetails.getJurisdiction()))
            .andExpect(jsonPath("$.state").value(expectedDetails.getState()))
            .andExpect(jsonPath("$.created_date").exists())
            .andExpect(jsonPath("$.last_modified").exists());
    }

    @Test
    @SneakyThrows
    void shouldGetCaseDetailsByUser() {
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(UserInfo.builder().uid(USER_ID).build());
        when(manageCaseRoleService.getUserCasesByCaseUserRole(
            TEST_SERVICE_AUTH_TOKEN, CASE_USER_ROLE_CREATOR
        )).thenReturn(requestCaseDataList);

        // when
        mockMvc.perform(
                get("/cases/user-cases", SCOTLAND_CASE_TYPE)
                    .contentType(MediaType.APPLICATION_JSON)
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

    @Test
    @SneakyThrows
    void shouldGetCaseDetailsByDefendantUser() {
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(UserInfo.builder().uid(USER_ID).build());
        when(manageCaseRoleService.getUserCasesByCaseUserRole(
            TEST_SERVICE_AUTH_TOKEN, CASE_USER_ROLE_DEFENDANT
        )).thenReturn(requestCaseDataList);

        // when
        mockMvc.perform(
                get("/cases/user-cases?case_user_role=DEFENDANT", SCOTLAND_CASE_TYPE)
                    .contentType(MediaType.APPLICATION_JSON)
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

    @Test
    @SneakyThrows
    void shouldReturnBadRequestForNonExistingItem() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseId(CASE_ID).build();

        Request request = Request.create(
            Request.HttpMethod.GET, "/test", Collections.emptyMap(), null, new RequestTemplate());
        when(verifyTokenService.verifyTokenSignature(anyString())).thenReturn(true);
        when(manageCaseRoleService.getUserCaseByCaseUserRole(anyString(), anyString(), anyString())).thenThrow(
            new FeignException.BadRequest(
                "Bad request",
                request,
                "incorrect payload".getBytes(StandardCharsets.UTF_8),
                Collections.emptyMap()));
        mockMvc.perform(post("/cases/user-case")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ResourceLoader.toJson(caseRequest))
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Bad request - incorrect payload"));
    }

    @Test
    @SneakyThrows
    void shouldCreateDraftCase() {

        CaseRequest caseRequest = CaseRequest.builder().build();

        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(caseService.createCase(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        ))
            .thenReturn(expectedDetails);

        // when
        mockMvc.perform(post(
                            "/cases/initiate-case"
                        )
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ResourceLoader.toJson(caseRequest))
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

    @Test
    @SneakyThrows
    void shouldStartUpdateCase() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId("12")
            .build();

        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            "12",
            TEST_NAME,
            TEST_FIRST_NAME,
            TEST_SURNAME,
            null
        ));
        when(caseService.triggerEvent(
            TEST_SERVICE_AUTH_TOKEN,
            CASE_ID,
            CaseEvent.valueOf("UPDATE_CASE_DRAFT"),
            SCOTLAND_CASE_TYPE,
            null
        )).thenReturn(expectedDetails);

        // when
        mockMvc.perform(
            put("/cases/update-case", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(caseRequest))
        ).andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    void shouldStartSubmitCase() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId("12")
            .caseData(new HashMap<>())
            .build();

        // given
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            "12",
            TEST_NAME,
            TEST_FIRST_NAME,
            TEST_SURNAME,
            null
        ));

        when(caseService.triggerEvent(
            TEST_SERVICE_AUTH_TOKEN,
            CASE_ID,
            CaseEvent.valueOf("SUBMIT_CASE_DRAFT"),
            SCOTLAND_CASE_TYPE,
            null
        )).thenReturn(expectedDetails);

        // when
        mockMvc.perform(
            put("/cases/submit-case", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(caseRequest))
        ).andExpect(status().isOk());
    }

    @Test
    @SneakyThrows
    void shouldUpdateHubLinkStatus() {
        HubLinksStatuses hubLinksStatuses = new HubLinksStatuses();
        HubLinksStatusesRequest hubLinksStatusesRequest = HubLinksStatusesRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId(CASE_ID)
            .hubLinksStatuses(hubLinksStatuses)
            .build();

        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(idamClient.getUserInfo(TEST_SERVICE_AUTH_TOKEN)).thenReturn(new UserInfo(
            null,
            "12",
            TEST_NAME,
            "Joe",
            "Bloggs",
            null
        ));
        when(hubLinkService.updateHubLinkStatuses(hubLinksStatusesRequest,
                                                  TEST_SERVICE_AUTH_TOKEN,
                                                  CASE_USER_ROLE_CREATOR)).thenReturn(expectedDetails);

        mockMvc.perform(
            put("/cases/update-hub-links-statuses", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(hubLinksStatusesRequest))
        ).andExpect(status().isOk());
        verify(hubLinkService, times(1)).updateHubLinkStatuses(
            hubLinksStatusesRequest,
            TEST_SERVICE_AUTH_TOKEN,
            CASE_USER_ROLE_CREATOR
        );
    }

    @Test
    @SneakyThrows
    void shouldSubmitClaimantApplication() {
        ClaimantTse claimantTse = new ClaimantTse();
        ClaimantApplicationRequest claimantApplicationRequest = ClaimantApplicationRequest.builder()
            .caseId(CASE_ID)
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .claimantTse(claimantTse)
            .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);

        when(applicationService.submitApplication(anyString(), any(ClaimantApplicationRequest.class)))
            .thenReturn(expectedDetails);
        mockMvc.perform(
            put("/cases/submit-claimant-application", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(claimantApplicationRequest))
        ).andExpect(status().isOk());

        verify(applicationService, times(1)).submitApplication(
            TEST_SERVICE_AUTH_TOKEN,
            claimantApplicationRequest
        );
    }

    @Test
    @SneakyThrows
    void shouldRespondToRespondentApplication() {
        RespondToApplicationRequest caseRequest = RespondToApplicationRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId(CASE_ID)
            .applicationId("1234")
            .response(new TseRespondType())
            .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);

        when(applicationService.submitApplication(anyString(), any(ClaimantApplicationRequest.class)))
            .thenReturn(expectedDetails);
        mockMvc.perform(
            put("/cases/respond-to-application", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(caseRequest))
        ).andExpect(status().isOk());

        verify(respondToApplicationService, times(1)).respondToApplication(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );
    }

    @Test
    @SneakyThrows
    void shouldChangeApplicationStatus() {
        ChangeApplicationStatusRequest caseRequest = ChangeApplicationStatusRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId(CASE_ID)
            .applicationId("1234")
            .newStatus("viewed")
            .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);

        when(applicationService.submitApplication(anyString(), any(ClaimantApplicationRequest.class)))
            .thenReturn(expectedDetails);
        mockMvc.perform(
            put("/cases/change-application-status", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(caseRequest))
        ).andExpect(status().isOk());

        verify(applicationService, times(1)).changeApplicationStatus(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );
    }

    @Test
    @SneakyThrows
    void shouldUpdateTribunalResponse() {
        TribunalResponseViewedRequest caseRequest = TribunalResponseViewedRequest.builder()
            .caseTypeId(SCOTLAND_CASE_TYPE)
            .caseId(CASE_ID)
            .appId("123")
            .responseId("777")
            .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);

        when(applicationService.updateTribunalResponseAsViewed(any(), any())).thenReturn(expectedDetails);
        mockMvc.perform(
            put("/cases/tribunal-response-viewed", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(caseRequest))
        ).andExpect(status().isOk());

        verify(applicationService, times(1)).updateTribunalResponseAsViewed(
            TEST_SERVICE_AUTH_TOKEN,
            caseRequest
        );
    }

    @Test
    @SneakyThrows
    void updateCaseSubmitted() {
        CaseRequest caseRequest = new CaseTestData().getCaseRequest();
        CaseDetails caseDetails = new CaseTestData().getCaseDetails();
        when(verifyTokenService.verifyTokenSignature(TEST_SERVICE_AUTH_TOKEN)).thenReturn(true);
        when(caseService.updateCaseSubmitted(TEST_SERVICE_AUTH_TOKEN, caseRequest)).thenReturn(caseDetails);
        mockMvc.perform(post(UPDATE_CASE_SUBMITTED_API_URL)
                            .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ResourceLoader.toJson(caseRequest)))
            .andExpect(status().isOk());
    }
}
