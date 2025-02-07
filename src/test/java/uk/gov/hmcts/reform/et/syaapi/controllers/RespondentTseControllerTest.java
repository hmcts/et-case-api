package uk.gov.hmcts.reform.et.syaapi.controllers;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondentApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.service.ApplicationService;
import uk.gov.hmcts.reform.et.syaapi.service.VerifyTokenService;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

@WebMvcTest(controllers = {RespondentTseController.class})
@Import(RespondentTseController.class)
class RespondentTseControllerTest {
    private static final String CASE_ID = "1646225213651590";
    private static final String CASE_TYPE = "ET_Scotland";

    private final CaseDetails expectedDetails;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VerifyTokenService verifyTokenService;

    @MockBean
    private ApplicationService applicationService;

    RespondentTseControllerTest() {
        expectedDetails = ResourceLoader.fromString(
            "responses/caseDetails.json",
            CaseDetails.class
        );
    }

    @SneakyThrows
    @Test
    void shouldSubmitRespondentApplication() {
        RespondentApplicationRequest respondentApplicationRequest = RespondentApplicationRequest.builder()
            .caseId(CASE_ID)
            .caseTypeId(CASE_TYPE)
            .respondentTse(new RespondentTse())
            .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(applicationService.submitRespondentApplication(any(), any())).thenReturn(expectedDetails);

        mockMvc.perform(
            put("/respondentTSE/submit-respondent-application", CASE_ID)
                .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ResourceLoader.toJson(respondentApplicationRequest))
        ).andExpect(status().isOk());

        verify(applicationService, times(1)).submitRespondentApplication(
            TEST_SERVICE_AUTH_TOKEN,
            respondentApplicationRequest
        );
    }

    @SneakyThrows
    @Test
    void shouldRespondToClaimantApplication() {
        RespondToApplicationRequest respondToApplicationRequest = RespondToApplicationRequest.builder()
                .caseId(CASE_ID)
                .caseTypeId(CASE_TYPE)
                .build();

        // when
        when(verifyTokenService.verifyTokenSignature(any())).thenReturn(true);
        when(applicationService.respondToClaimantApplication(any(), any())).thenReturn(expectedDetails);

        mockMvc.perform(
                put("/respondentTSE/respond-to-claimant-application", CASE_ID)
                        .header(HttpHeaders.AUTHORIZATION, TEST_SERVICE_AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ResourceLoader.toJson(respondToApplicationRequest))
        ).andExpect(status().isOk());

        verify(applicationService, times(1)).respondToClaimantApplication(
                TEST_SERVICE_AUTH_TOKEN,
                respondToApplicationRequest
        );
    }
}
