package uk.gov.hmcts.reform.et.syaapi.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.service.utils.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaseDetailsConverterTest {

    private final CaseDetails expectedDetails = ResourceLoader.fromString(
        "responses/caseDetails.json",
        CaseDetails.class
    );

    private final StartEventResponse startEventResponse = ResourceLoader.fromString(
        "responses/startEventResponse.json",
        StartEventResponse.class
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CaseDetailsConverter caseDetailsConverter;

    @Autowired
    private Et1CaseData et1CaseData;

    @BeforeEach
    void setUp() {
        et1CaseData = new Et1CaseData();
        et1CaseData.setCaseNotes("TEST_STRING");
        et1CaseData.setCaseSource("MANUALLY_CREATED");
        caseDetailsConverter = new CaseDetailsConverter(objectMapper);
    }

    @Test
    void shouldGetCaseDetailsConverter() {
        caseDetailsConverter.et1ToCaseDataContent(startEventResponse, et1CaseData);
        assertThat(caseDetailsConverter.toCaseData(expectedDetails).getCaseSource())
            .isEqualToIgnoringCase("Manually Created");
    }

    @Test
    void shouldConvertDataToCaseDataWithNullCaseDetails() {
        assertNull(caseDetailsConverter.toCaseData(null));
    }

    @Test
    void shouldConvertCaseDetailsDataToCaseData() {
        caseDetailsConverter.et1ToCaseDataContent(startEventResponse, et1CaseData);
        assertNotNull(caseDetailsConverter.toCaseData(expectedDetails));
    }

    @Test
    void shouldGetCaseDataNull() {
        caseDetailsConverter.getCaseData(expectedDetails.getData());
        assertNotNull(caseDetailsConverter.getCaseData(expectedDetails.getData()));
        assertEquals(caseDetailsConverter.getCaseData(expectedDetails.getData()).getClass(), CaseData.class);
    }

    @Test
    void shouldConvertCaseDataToCaseDataContent() {
        CaseData caseData = new CaseData();
        CaseDataContent caseDataContent = caseDetailsConverter.caseDataContent(startEventResponse, caseData);
        assertNotNull(caseDataContent);
        assertEquals(startEventResponse.getToken(), caseDataContent.getEventToken());
        assertEquals(startEventResponse.getEventId(), caseDataContent.getEvent().getId());
        assertEquals(caseData, caseDataContent.getData());
    }

    @Test
    void shouldConvertEt1CaseDataToCaseDataContent() {
        CaseDataContent caseDataContent = caseDetailsConverter.et1ToCaseDataContent(startEventResponse, et1CaseData);
        assertNotNull(caseDataContent);
        assertEquals(startEventResponse.getToken(), caseDataContent.getEventToken());
        assertEquals(startEventResponse.getEventId(), caseDataContent.getEvent().getId());
        assertEquals(et1CaseData, caseDataContent.getData());
    }

}
