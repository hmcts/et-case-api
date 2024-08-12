package uk.gov.hmcts.reform.et.syaapi.helper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Converts {@link CaseDetails} to other case related classes using {@link ObjectMapper}.
 */
@Slf4j
@Service
public class CaseDetailsConverter {

    private final ObjectMapper objectMapper;

    /**
     * Constructor for @ObjectMapper class.
     *
     * @param objectMapper jackson {@link ObjectMapper} object to initialize object
     */
    public CaseDetailsConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    /**
     * For a case, Converts @CaseDetails.data to @CaseData object.
     *
     * @param caseDetails would represent CaseData for the case
     * @return caseData represent cases in java object model
     */
    public CaseData toCaseData(CaseDetails caseDetails) {
        if (caseDetails == null) {
            return null;
        }

        return objectMapper.convertValue(caseDetails.getData(), CaseData.class);
    }

    public CaseData getCaseData(Map<String, Object> caseData) {
        return objectMapper.convertValue(caseData, CaseData.class);
    }

    /**
     * Converts pre-submitted case related details to CaseDataContent which gets saved to CCD.
     *
     * @param startEventResponse associated case details updated
     * @param et1CaseData original json format represented object
     * @return {@link CaseDataContent} which returns overall contents of the case
     */
    public CaseDataContent et1ToCaseDataContent(StartEventResponse startEventResponse, Et1CaseData et1CaseData) {
        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder().id(startEventResponse.getEventId()).build())
            .data(et1CaseData)
            .build();
    }

    /**
     * Converts Case related details to CaseDataContent which gets saved to CCD.
     *
     * @param startEventResponse associated case details updated
     * @param caseData original json format represented object
     * @return {@link CaseDataContent} which returns overall contents of the case
     */
    public CaseDataContent caseDataContent(StartEventResponse startEventResponse, CaseData caseData) {
        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder().id(startEventResponse.getEventId()).build())
            .data(caseData)
            .build();
    }

    /**
     * Converts Request caseData field map to CaseData which is used to create CaseDataContent and save in CCD.
     *
     * @param requestData map object that contains the data field values of the request case details
     * @param latestData map object that contains the data field values of the updated case details
     *                   obtained from CCD api call
     * @return {@link CaseData} which returns latest CaseData object representing the contents of the Case Data
     */
    public CaseData getUpdatedCaseData(Map<String, Object> requestData, Map<String, Object> latestData) {
        if (requestData == null || latestData == null) {
            return null;
        }
        log.info("Request data map: {} \n", requestData.toString());
        log.info("Latest data map: {} \n", latestData.toString());
        Et1CaseData requestEt1CaseData = EmployeeObjectMapper.getEmploymentCaseData(requestData);
        log.info("Local Request-Et1CaseData: {} \n", requestEt1CaseData.toString());
        CaseData requestCaseData1 = getCaseData(requestData);
        log.info("Local Request-CaseData: {} \n", requestCaseData1.toString());

        Et1CaseData latestEt1CaseData = EmployeeObjectMapper.getEmploymentCaseData(latestData);
        log.info("Local Latest-Et1CaseData: {} \n", latestEt1CaseData.toString());
        CaseData latestCaseData1 = getCaseData(latestData);
        log.info("Local Latest-CaseData: {} \n", latestCaseData1.toString());

        CaseData requestCaseData = EmployeeObjectMapper.mapRequestCaseDataToCaseData(requestData);
        log.error("Re-CaseData: {} \n", requestCaseData.toString());
        CaseData latestCaseData = EmployeeObjectMapper.mapRequestCaseDataToCaseData(latestData);
        log.error("La-CaseData: {} \n", latestCaseData.toString());

        Class<?> sourceClass = requestCaseData.getClass();
        try {
            for (Field field : sourceClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object requestValue = field.get(requestCaseData);

                if (requestValue != null) {
                    field.set(latestCaseData, requestValue);
                }
                field.setAccessible(false);
            }
        } catch (IllegalAccessException e) {
            log.error(
                "Failed to copy the Non-Null field values of the request CaseData to the latest CaseData: {}",
                e.getMessage()
            );
        }
        return latestCaseData;
    }
}
