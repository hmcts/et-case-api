package uk.gov.hmcts.reform.et.syaapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mock;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantHearingPreference;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantIndType;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentSumType;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.HubLinksStatuses;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ChangeApplicationStatusRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.HubLinksStatusesRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.TribunalResponseViewedRequest;
import uk.gov.hmcts.reform.et.syaapi.service.FeatureToggleService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.ccd.sdk.type.YesOrNo.YES;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLISH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.helper.TseApplicationHelper.CLAIMANT_TITLE;

@SuppressWarnings({"PMD.LawOfDemeter", "PMD.LinguisticNaming", "PMD.TooManyMethods"})
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManageCaseControllerFunctionalTest extends FunctionalTestBase {

    private static final String STATE = "state";
    private static final String CASES_INITIATE_CASE = "/cases/initiate-case";
    private static final String RESPONDENT_NAME = "Boris Johnson";
    private static final String WAITING_FOR_THE_TRIBUNAL = "waitingForTheTribunal";
    private static final String NEW_STATUS = "newStatus";
    private static final String CASES_USER_CASE = "/cases/user-case";
    private static final String CASES_USER_CASES = "/cases/user-cases";
    private static final String CASES_UPDATE_CASE = "/cases/update-case";
    private static final String CASES_SUBMIT_CASE = "/cases/submit-case";
    private static final String CASES_UPDATE_HUB_LINKS_STATUSES = "/cases/update-hub-links-statuses";
    private static final String CASES_SUBMIT_CLAIMANT_APPLICATION = "/cases/submit-claimant-application";
    private static final String CASES_RESPOND_TO_APPLICATION = "/cases/respond-to-application";
    private static final String CASES_CHANGE_APPLICATION_STATUS = "/cases/change-application-status";
    private static final String CASES_TRIBUNAL_RESPONSE_VIEWED = "/cases/tribunal-response-viewed";
    private static final String INVALID_TOKEN = "invalid_token";
    private Long caseId;
    private static final String CASE_TYPE = "ET_EnglandWales";
    private static final String CLAIMANT_EMAIL = "citizen-user-test@test.co.uk";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SUBMITTED = "Submitted";
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, Object> caseData = new ConcurrentHashMap<>();
    private String appId;
    private String responseId;
    @Mock
    private FeatureToggleService featureToggleService;

    @Test
    @Order(1)
    void createCaseShouldReturnCaseData() {
        caseData.put("caseType", "Single");
        caseData.put("caseSource", "Manually Created");
        caseData.put("claimant", "claimant");
        caseData.put("receiptDate", "1970-04-02");
        objectMapper.registerModule(new JavaTimeModule());
        ClaimantIndType claimantIndType = new ClaimantIndType();
        claimantIndType.setClaimantFirstNames("Boris");
        claimantIndType.setClaimantLastName("Johnson");
        ClaimantHearingPreference hearingPreference = new ClaimantHearingPreference();
        hearingPreference.setContactLanguage(ENGLISH_LANGUAGE);
        when(featureToggleService.isWelshEnabled()).thenReturn(false);
        caseData.put("claimantIndType", claimantIndType);
        caseData.put("respondentCollection", List.of(createRespondentType()));
        caseData.put("claimantType", Map.of("claimant_email_address", CLAIMANT_EMAIL));
        caseData.put("claimantHearingPreference", hearingPreference);

        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .caseTypeId(CASE_TYPE)
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .post(CASES_INITIATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .extract().body().jsonPath();

        caseId = body.get("id");

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .post(CASES_INITIATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true);
    }

    @SneakyThrows
    @Test
    @Order(3)
    void getAllCaseDetails() {
        TimeUnit.SECONDS.sleep(5);
        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .get(CASES_USER_CASES)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("size()", is(2));
    }

    @SneakyThrows
    @Test
    @Order(4)
    void updateCaseShouldReturnUpdatedDraftCaseDetails() {
        TimeUnit.SECONDS.sleep(5);

        CaseRequest caseRequest = CaseRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_UPDATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.claimantType.claimant_email_address", equalTo(CLAIMANT_EMAIL));
    }

    @SneakyThrows
    @Test
    @Order(5)
    void submitCaseShouldReturnSubmittedCaseDetails() {
        TimeUnit.SECONDS.sleep(5);
        CaseRequest caseRequest = CaseRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_SUBMIT_CASE)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body(STATE, equalTo(SUBMITTED))
            .assertThat().body("case_data.noticeOfChangeAnswers0.respondentName", equalTo(RESPONDENT_NAME));
    }

    @Test
    @Order(6)
    void updateHubLinksStatusesShouldReturnCaseDetailsWithHubLinksStatuses() {
        HubLinksStatuses hubLinksStatuses = new HubLinksStatuses();
        hubLinksStatuses.setRespondentResponse(WAITING_FOR_THE_TRIBUNAL);
        caseData.put("hubLinksStatuses", hubLinksStatuses);

        HubLinksStatusesRequest hubLinksStatusesRequest = HubLinksStatusesRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .hubLinksStatuses(hubLinksStatuses)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(hubLinksStatusesRequest)
            .put(CASES_UPDATE_HUB_LINKS_STATUSES)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.hubLinksStatuses.respondentResponse", equalTo(WAITING_FOR_THE_TRIBUNAL));
    }

    @Test
    @Order(7)
    void submitClaimantApplicationShouldReturnCaseDetailsWithTseApplication() {
        ClaimantTse claimantTse = new ClaimantTse();
        claimantTse.setContactApplicationType("withdraw");

        ClaimantApplicationRequest claimantApplicationRequest = ClaimantApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .claimantTse(claimantTse)
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(claimantApplicationRequest)
            .put(CASES_SUBMIT_CLAIMANT_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.applicant", equalTo(CLAIMANT_TITLE))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.type",
                               equalTo("Withdraw all/part of claim"))
            .extract().body().jsonPath();

        CaseData caseDataWithTse = objectMapper.convertValue(body.get("case_data"), CaseData.class);
        appId = caseDataWithTse.getGenericTseApplicationCollection().get(0).getId();
    }

    @Test
    @Order(8)
    void respondToApplicationShouldReturnCaseDetailsWithTseAppWithResponse() {
        RespondToApplicationRequest respondToApplicationRequest = RespondToApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .applicationId(appId)
            .response(new TseRespondType())
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(respondToApplicationRequest)
            .put(CASES_RESPOND_TO_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.respondCollection[0].value.from",
                               equalTo(CLAIMANT_TITLE));
    }

    @Test
    @Order(9)
    void changeApplicationStatusShouldReturnCaseDetailsWithTseAppWithStatusUpdated() {
        ChangeApplicationStatusRequest changeApplicationStatusRequest = ChangeApplicationStatusRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .applicationId(appId)
            .newStatus(NEW_STATUS)
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(changeApplicationStatusRequest)
            .put(CASES_CHANGE_APPLICATION_STATUS)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.applicationState",
                               equalTo(NEW_STATUS))
            .extract().body().jsonPath();

        CaseData caseDataWithTse = objectMapper.convertValue(body.get("case_data"), CaseData.class);
        responseId = caseDataWithTse.getGenericTseApplicationCollection()
            .get(0).getValue().getRespondCollection().get(0).getId();
    }

    @Test
    @Order(10)
    void updateResponseAsViewedShouldReturnCaseDetailsWithTseResponseAsViewed() {
        TribunalResponseViewedRequest tribunalResponseViewedRequest = TribunalResponseViewedRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .appId(appId)
            .responseId(responseId)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(tribunalResponseViewedRequest)
            .put(CASES_TRIBUNAL_RESPONSE_VIEWED)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body(STATE, equalTo(SUBMITTED))
            .assertThat().body("case_data.genericTseApplicationCollection[0]"
                                   + ".value.respondCollection[0].value.viewedByClaimant",
                               equalTo(YES.getValue()))
            .extract().body().jsonPath();
    }

    @Test
    void createDraftCaseWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .post(CASES_INITIATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void getSingleCaseDetailsWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .post(CASES_USER_CASE)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void getAllCaseDetailsWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .get(CASES_USER_CASES)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void updateCaseWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_UPDATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void submitCaseWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_UPDATE_CASE)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void updateHubLinksStatusesWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_UPDATE_HUB_LINKS_STATUSES)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void submitClaimantApplicationWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_SUBMIT_CLAIMANT_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void respondToApplicationWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_RESPOND_TO_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void changeApplicationStatusWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_CHANGE_APPLICATION_STATUS)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }

    @Test
    void updateResponseAsViewedWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_TRIBUNAL_RESPONSE_VIEWED)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true)
            .extract().body().jsonPath();
    }


    private RespondentSumTypeItem createRespondentType() {
        RespondentSumType respondentSumType = new RespondentSumType();
        respondentSumType.setRespondentName(RESPONDENT_NAME);
        RespondentSumTypeItem respondentSumTypeItem = new RespondentSumTypeItem();
        respondentSumTypeItem.setValue(respondentSumType);

        return respondentSumTypeItem;
    }
}

