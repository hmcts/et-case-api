package uk.gov.hmcts.reform.et.syaapi.service.utils;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.ecm.common.helpers.UtilHelper;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.reform.et.syaapi.models.GenericTseApplication;

import java.time.LocalDate;

import static uk.gov.hmcts.ecm.common.model.helper.Constants.RESPONDENT_TITLE;

@Slf4j
public final class RespondentTseUtil {
    private RespondentTseUtil() {
    }

    public static GenericTseApplication getGenericTseApplicationFromRespondentTse(RespondentTse respondentTse,
                                                                                  String caseReference) {
        if (respondentTse == null) {
            return null;
        }

        UploadedDocumentType contactApplicationFile = respondentTse.getContactApplicationFile();
        String supportingEvidence =
            contactApplicationFile != null
                ? contactApplicationFile.getDocumentFilename()
                : null;

        return GenericTseApplication.builder()
            .caseNumber(caseReference)
            .applicant(RESPONDENT_TITLE)
            .applicationType(respondentTse.getContactApplicationType())
            .applicationDate(UtilHelper.formatCurrentDate(LocalDate.now()))
            .tellOrAskTribunal(respondentTse.getContactApplicationText())
            .supportingEvidence(supportingEvidence)
            .copyToOtherPartyYesOrNo(respondentTse.getCopyToOtherPartyYesOrNo())
            .copyToOtherPartyText(respondentTse.getCopyToOtherPartyText())
            .build();
    }
}
