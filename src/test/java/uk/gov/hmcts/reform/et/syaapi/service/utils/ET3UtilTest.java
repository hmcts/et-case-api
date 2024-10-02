package uk.gov.hmcts.reform.et.syaapi.service.utils;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.et.common.model.ccd.Et3Request;
import uk.gov.hmcts.reform.et.syaapi.exception.ET3Exception;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_AUTHORISATION_TOKEN_BLANK;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_CASE_TYPE_BLANK;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_REQUEST_EMPTY;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_REQUEST_TYPE_BLANK;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_RESPONDENT_EMPTY;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_RESPONDENT_IDAM_ID_IS_BLANK;
import static uk.gov.hmcts.reform.et.syaapi.constants.ResponseConstants.EXCEPTION_ET3_SUBMISSION_REFERENCE_BLANK;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_SERVICE_AUTH_TOKEN;

class ET3UtilTest {

    private static final String EXCEPTION_PREFIX = "java.lang.Exception: ";

    @ParameterizedTest
    @MethodSource("provideCheckModifyEt3DataParametersTestData")
    void theCheckModifyEt3DataParameters(String authorisation, Et3Request et3Request) {
        if (StringUtils.isBlank(authorisation)) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_AUTHORISATION_TOKEN_BLANK);
            return;
        }
        if (ObjectUtils.isEmpty(et3Request)) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_ET3_REQUEST_EMPTY);
            return;
        }
        if (StringUtils.isBlank(et3Request.getCaseSubmissionReference())) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(
                               EXCEPTION_PREFIX + EXCEPTION_ET3_SUBMISSION_REFERENCE_BLANK);
            return;
        }
        if (StringUtils.isBlank(et3Request.getCaseTypeId())) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_ET3_CASE_TYPE_BLANK);
            return;
        }
        if (StringUtils.isBlank(et3Request.getRequestType())) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_ET3_REQUEST_TYPE_BLANK);
            return;
        }
        if (ObjectUtils.isEmpty(et3Request.getRespondent())) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_ET3_RESPONDENT_EMPTY);
            return;
        }
        if (StringUtils.isBlank(et3Request.getRespondent().getIdamId())) {
            assertThat(assertThrows(ET3Exception.class, () ->
                ET3Util.checkModifyEt3DataParameters(authorisation, et3Request))
                           .getMessage()).isEqualTo(EXCEPTION_PREFIX + EXCEPTION_ET3_RESPONDENT_IDAM_ID_IS_BLANK);
            return;
        }
        assertDoesNotThrow(() -> ET3Util.checkModifyEt3DataParameters(authorisation, et3Request));
    }

    private static Stream<Arguments> provideCheckModifyEt3DataParametersTestData() {
        Et3Request et3RequestBlankSubmissionReference = new CaseTestData().getEt3Request();
        et3RequestBlankSubmissionReference.setCaseSubmissionReference(StringUtils.SPACE);
        Et3Request et3RequestBlankCaseTypeId = new CaseTestData().getEt3Request();
        et3RequestBlankCaseTypeId.setCaseTypeId(StringUtils.SPACE);
        Et3Request et3RequestBlankRequestType = new CaseTestData().getEt3Request();
        et3RequestBlankRequestType.setRequestType(StringUtils.SPACE);
        Et3Request et3RequestNullRespondent = new CaseTestData().getEt3Request();
        et3RequestNullRespondent.setRespondent(null);
        Et3Request et3RequestBlankIdamId = new CaseTestData().getEt3Request();
        et3RequestBlankIdamId.getRespondent().setIdamId(StringUtils.SPACE);
        Et3Request validEt3Request = new CaseTestData().getEt3Request();
        return Stream.of(Arguments.of(null, validEt3Request),
                         Arguments.of(StringUtils.EMPTY, validEt3Request),
                         Arguments.of(StringUtils.SPACE, validEt3Request),
                         Arguments.of(TEST_SERVICE_AUTH_TOKEN, et3RequestBlankSubmissionReference),
                         Arguments.of(TEST_SERVICE_AUTH_TOKEN, et3RequestBlankCaseTypeId),
                         Arguments.of(TEST_SERVICE_AUTH_TOKEN, et3RequestBlankRequestType),
                         Arguments.of(TEST_SERVICE_AUTH_TOKEN, et3RequestNullRespondent),
                         Arguments.of(TEST_SERVICE_AUTH_TOKEN, et3RequestBlankIdamId));
    }


}
