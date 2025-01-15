package uk.gov.hmcts.reform.et.syaapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;

@Data
@Builder
@Jacksonized
public class RespondentApplicationRequest {
    @JsonProperty("case_id")
    private String caseId;
    @JsonProperty("case_type_id")
    private String caseTypeId;
    @JsonProperty("type_c")
    private boolean typeC;
    @JsonProperty("respondent_tse")
    private RespondentTse respondentTse;
}
