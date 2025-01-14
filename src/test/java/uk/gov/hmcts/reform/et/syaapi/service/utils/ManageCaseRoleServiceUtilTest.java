package uk.gov.hmcts.reform.et.syaapi.service.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.ecm.common.model.ccd.CaseAssignmentUserRole;
import uk.gov.hmcts.ecm.common.model.ccd.ModifyCaseUserRolesRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.et.syaapi.constants.ManageCaseRoleConstants;
import uk.gov.hmcts.reform.et.syaapi.exception.ManageCaseRoleException;
import uk.gov.hmcts.reform.et.syaapi.model.CaseTestData;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.CASE_ID;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_CASE_USER_ROLE_CREATOR;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_CASE_USER_ROLE_DEFENDANT;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.TEST_CASE_USER_ROLE_INVALID;
import static uk.gov.hmcts.reform.et.syaapi.service.utils.TestConstants.USER_ID;

class ManageCaseRoleServiceUtilTest {

    private static final String AAC_URL_TEST_VALUE = "https://test.url.com";
    private static final String EXPECTED_AAC_URI_WITH_ONLY_CASE_DETAILS =
        "https://test.url.com/case-users?case_ids=1646225213651533&case_ids=1646225213651512";
    private static final String EXPECTED_AAC_URI_WITH_CASE_AND_USER_IDS =
        "https://test.url.com/case-users?case_ids=1646225213651533&case_ids=1646225213651512&"
            + "user_ids=123456789012345678901234567890&user_ids=123456789012345678901234567890";

    @ParameterizedTest
    @MethodSource("provideTheCreateAacSearchCaseUsersUriByCaseAndUserIdsTestData")
    void theCreateAacSearchCaseUsersUriByCaseAndUserIds(String aacUrl,
                                                        List<CaseDetails> caseDetailsList,
                                                        List<UserInfo> userInfoList) {
        if (StringUtils.isBlank(aacUrl) || CollectionUtils.isEmpty(caseDetailsList)) {
            assertThat(ManageCaseRoleServiceUtil.createAacSearchCaseUsersUriByCaseAndUserIds(
                aacUrl, caseDetailsList, userInfoList)).isEqualTo(StringUtils.EMPTY);
            return;
        }
        if (CollectionUtils.isEmpty(userInfoList)) {
            assertThat(ManageCaseRoleServiceUtil.createAacSearchCaseUsersUriByCaseAndUserIds(
                aacUrl, caseDetailsList, userInfoList)).isEqualTo(EXPECTED_AAC_URI_WITH_ONLY_CASE_DETAILS);
            return;
        }
        assertThat(ManageCaseRoleServiceUtil.createAacSearchCaseUsersUriByCaseAndUserIds(
            aacUrl, caseDetailsList, userInfoList)).isEqualTo(EXPECTED_AAC_URI_WITH_CASE_AND_USER_IDS);
    }

    private static Stream<Arguments> provideTheCreateAacSearchCaseUsersUriByCaseAndUserIdsTestData() {
        List<CaseDetails> caseDetailsList = new CaseTestData().getRequestCaseDataListScotland();
        UserInfo userInfo = new CaseTestData().getUserInfo();
        List<UserInfo> userInfoList = List.of(userInfo, userInfo);
        return Stream.of(Arguments.of(null, null, null),
                         Arguments.of(null, caseDetailsList, null),
                         Arguments.of(StringUtils.EMPTY, caseDetailsList, null),
                         Arguments.of(AAC_URL_TEST_VALUE, null, null),
                         Arguments.of(AAC_URL_TEST_VALUE, new ArrayList<>(), null),
                         Arguments.of(AAC_URL_TEST_VALUE, caseDetailsList, null),
                         Arguments.of(AAC_URL_TEST_VALUE, caseDetailsList, new ArrayList<>()),
                         Arguments.of(AAC_URL_TEST_VALUE, caseDetailsList, userInfoList));
    }

    @ParameterizedTest
    @MethodSource("provideTheFindCaseUserRoleTestData")
    void theFindCaseUserRole(CaseDetails caseDetails, CaseAssignmentUserRole caseAssignmentUserRole) {
        String actualCaseUserRole = ManageCaseRoleServiceUtil.findCaseUserRole(caseDetails, caseAssignmentUserRole);
        if (ObjectUtils.isNotEmpty(caseDetails)
            && ObjectUtils.isNotEmpty(caseDetails.getId())
            && ObjectUtils.isNotEmpty(caseAssignmentUserRole)
            && StringUtils.isNotEmpty(caseAssignmentUserRole.getCaseDataId())
            && caseDetails.getId().toString().equals(caseAssignmentUserRole.getCaseDataId())
            && (ManageCaseRoleConstants.CASE_USER_ROLE_CREATOR.equals(caseAssignmentUserRole.getCaseRole())
            ||  ManageCaseRoleConstants.CASE_USER_ROLE_DEFENDANT.equals(caseAssignmentUserRole.getCaseRole()))) {
            if (TEST_CASE_USER_ROLE_CREATOR.equals(caseAssignmentUserRole.getCaseRole())) {
                assertThat(actualCaseUserRole).isEqualTo(TEST_CASE_USER_ROLE_CREATOR);
            } else {
                assertThat(actualCaseUserRole).isEqualTo(TEST_CASE_USER_ROLE_DEFENDANT);
            }
        } else {
            assertThat(actualCaseUserRole).isEqualTo(StringUtils.EMPTY);
        }
    }

    private static Stream<Arguments> provideTheFindCaseUserRoleTestData() {
        CaseDetails caseDetails = new CaseTestData().getCaseDetails();
        CaseAssignmentUserRole caseAssignmentUserRoleCreator =
            CaseAssignmentUserRole.builder()
                .caseRole(TEST_CASE_USER_ROLE_CREATOR)
                .caseDataId(caseDetails.getId().toString())
                .userId(USER_ID).build();
        CaseAssignmentUserRole caseAssignmentUserRoleDefendant =
            CaseAssignmentUserRole.builder()
                .caseRole(TEST_CASE_USER_ROLE_DEFENDANT)
                .caseDataId(caseDetails.getId().toString())
                .userId(USER_ID).build();
        CaseDetails caseDetailsWithEmptyCaseId = new CaseTestData().getCaseDetails();
        caseDetailsWithEmptyCaseId.setId(null);
        CaseAssignmentUserRole caseAssignmentUserRoleWithEmptyCaseDataId =
            CaseAssignmentUserRole.builder()
                .caseRole(TEST_CASE_USER_ROLE_CREATOR).caseDataId(StringUtils.EMPTY).userId(USER_ID).build();
        CaseAssignmentUserRole caseAssignmentUserRoleWithInvalidCaseDataId =
            CaseAssignmentUserRole.builder()
                .caseRole(TEST_CASE_USER_ROLE_CREATOR).caseDataId(CASE_ID).userId(USER_ID).build();
        CaseAssignmentUserRole caseAssignmentUserRoleWithInvalidCaseUserRole =
            CaseAssignmentUserRole.builder()
                .caseRole(TEST_CASE_USER_ROLE_INVALID).caseDataId(CASE_ID).userId(USER_ID).build();
        return Stream.of(Arguments.of(null, caseAssignmentUserRoleCreator),
                         Arguments.of(caseDetailsWithEmptyCaseId, caseAssignmentUserRoleCreator),
                         Arguments.of(caseDetailsWithEmptyCaseId, caseAssignmentUserRoleDefendant),
                         Arguments.of(caseDetails, null),
                         Arguments.of(caseDetails, caseAssignmentUserRoleWithEmptyCaseDataId),
                         Arguments.of(caseDetails, caseAssignmentUserRoleWithInvalidCaseDataId),
                         Arguments.of(caseDetails, caseAssignmentUserRoleWithInvalidCaseUserRole));
    }

    @Test
    void theCheckModifyCaseUserRolesRequest() {
        assertThrows(ManageCaseRoleException.class, () -> ManageCaseRoleServiceUtil.checkModifyCaseUserRolesRequest(
            null));
        assertThrows(ManageCaseRoleException.class, () -> ManageCaseRoleServiceUtil.checkModifyCaseUserRolesRequest(
            ModifyCaseUserRolesRequest.builder().build()));
    }

    @Test
    void theIsCaseRoleAssignmentExceptionForSameUser() {
        assertThat(ManageCaseRoleServiceUtil.isCaseRoleAssignmentExceptionForSameUser(
            new Exception("Test Exception"))).isFalse();
        assertThat(ManageCaseRoleServiceUtil.isCaseRoleAssignmentExceptionForSameUser(
            new Exception("You have already been assigned to this case caseId, "))).isTrue();
    }
}
