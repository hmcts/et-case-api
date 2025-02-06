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
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantHearingPreference;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantIndType;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentSumType;
import uk.gov.hmcts.et.common.model.ccd.types.TseRespondType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.models.ClaimantApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.RespondToApplicationRequest;
import uk.gov.hmcts.reform.et.syaapi.models.SubmitStoredRespondToApplicationRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.IN_PROGRESS;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.OPEN_STATE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLISH_LANGUAGE;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StoreCaseControllerFunctionalTest extends FunctionalTestBase {

    private static final String CASES_INITIATE_CASE = "/cases/initiate-case";
    private static final String CASES_SUBMIT_CASE = "/cases/submit-case";
    private static final String CASES_STORE_CLAIMANT_APPLICATION = "/store/store-claimant-application";
    private static final String CASES_SUBMIT_CLAIMANT_APPLICATION = "/cases/submit-claimant-application";
    private static final String CASES_STORE_RESPOND_TO_APPLICATION = "/store/store-respond-to-application";
    private static final String CASES_SUBMIT_STORED_RESPOND_TO_APPLICATION =
        "/store/submit-stored-respond-to-application";
    private static final String RESPONDENT_NAME = "Boris Johnson";
    private static final String INVALID_TOKEN = "invalid_token";
    private static final String CASE_TYPE = "ET_EnglandWales";
    private static final String CLAIMANT_EMAIL = "citizen-user-test@test.co.uk";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CASE_DATA = "case_data";
    private Long caseId;
    private String appId;
    private String responseId;
    private ClaimantTse storedClaimantTse;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, Object> caseData = new ConcurrentHashMap<>();

    @Test
    @Order(1)
    void createCaseShouldReturnCaseData() {
        caseData.put("caseType", "Single");
        caseData.put("caseSource", "Manually Created");
        caseData.put("claimant", "claimant");
        caseData.put("receiptDate", "1970-04-02");
        objectMapper.registerModule(new JavaTimeModule());
        ClaimantHearingPreference claimantHearingPreference = new ClaimantHearingPreference();
        claimantHearingPreference.setContactLanguage(ENGLISH_LANGUAGE);
        caseData.put("claimantHearingPreference", claimantHearingPreference);

        ClaimantIndType claimantIndType = new ClaimantIndType();
        claimantIndType.setClaimantFirstNames("Boris");
        claimantIndType.setClaimantLastName("Johnson");
        caseData.put("claimantIndType", claimantIndType);

        caseData.put("respondentCollection", List.of(createRespondentType()));
        caseData.put("claimantType", Map.of("claimant_email_address", CLAIMANT_EMAIL));

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
    @Order(2)
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
            .log().all(true);
    }

    @Test
    @Order(3)
    void storeClaimantApplicationShouldReturnCaseDetailsWithStoredApplication() {
        ClaimantTse claimantTse = new ClaimantTse();
        claimantTse.setContactApplicationType("withdraw");
        storedClaimantTse = claimantTse;

        ClaimantApplicationRequest caseRequest = ClaimantApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .claimantTse(claimantTse)
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_STORE_CLAIMANT_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .extract().body().jsonPath();

        CaseData caseDataWithTse = objectMapper.convertValue(body.get(CASE_DATA), CaseData.class);
        storedClaimantTse.setStoredApplicationId(caseDataWithTse.getTseApplicationStoredCollection().get(0).getId());
    }

    @Test
    @Order(4)
    void submitStoredClaimantApplicationShouldReturnCaseDetails() {
        ClaimantApplicationRequest caseRequest = ClaimantApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .claimantTse(storedClaimantTse)
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_SUBMIT_CLAIMANT_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.applicationState",
                               equalTo(IN_PROGRESS))
            .assertThat().body("case_data.genericTseApplicationCollection[0].value.status",
                               equalTo(OPEN_STATE))
            .extract().body().jsonPath();

        CaseData caseDataWithTse = objectMapper.convertValue(body.get(CASE_DATA), CaseData.class);
        appId = caseDataWithTse.getGenericTseApplicationCollection().get(0).getId();
    }

    @Test
    @Order(5)
    void storeRespondToApplicationShouldReturnCaseDetailsWithTseAppWithResponse() {
        RespondToApplicationRequest caseRequest = RespondToApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .applicationId(appId)
            .response(new TseRespondType())
            .build();

        JsonPath body = RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_STORE_RESPOND_TO_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .extract().body().jsonPath();

        CaseData caseDataWithTse = objectMapper.convertValue(body.get(CASE_DATA), CaseData.class);
        responseId = caseDataWithTse.getGenericTseApplicationCollection()
            .get(0).getValue().getRespondStoredCollection().get(0).getId();
    }

    @Test
    @Order(6)
    void submitStoredRespondToApplicationShouldReturnCaseDetails() {
        SubmitStoredRespondToApplicationRequest caseRequest = SubmitStoredRespondToApplicationRequest.builder()
            .caseId(caseId.toString())
            .caseTypeId(CASE_TYPE)
            .applicationId(appId)
            .storedRespondId(responseId)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, userToken))
            .body(caseRequest)
            .put(CASES_SUBMIT_STORED_RESPOND_TO_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .log().all(true)
            .assertThat().body("id", equalTo(caseId));
    }

    @Test
    void storeClaimantApplicationWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_STORE_CLAIMANT_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true);
    }

    @Test
    void submitStoredRespondToApplicationWithInvalidAuthTokenShouldReturn403() {
        CaseRequest caseRequest = CaseRequest.builder()
            .caseData(caseData)
            .build();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(new Header(AUTHORIZATION, INVALID_TOKEN))
            .body(caseRequest)
            .put(CASES_SUBMIT_STORED_RESPOND_TO_APPLICATION)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN)
            .log().all(true);
    }

    private RespondentSumTypeItem createRespondentType() {
        RespondentSumType respondentSumType = new RespondentSumType();
        respondentSumType.setRespondentName(RESPONDENT_NAME);
        RespondentSumTypeItem respondentSumTypeItem = new RespondentSumTypeItem();
        respondentSumTypeItem.setValue(respondentSumType);

        return respondentSumTypeItem;
    }
}
