package uk.gov.hmcts.reform.et.syaapi.service.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.ecm.common.helpers.UtilHelper;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.reform.et.syaapi.models.GenericTseApplication;

import java.time.LocalDate;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.RESPONDENT_TITLE;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.YES;

class RespondentTseUtilTest {

    private static Stream<Arguments> checkGetGenericTseApplicationFromRespondentTse() {
        String applicationText = "Test details";
        String documentFilename = "test-Document-Filename.pdf";
        String copyToOtherPartyText = "No details";
        return Stream.of(
            Arguments.of(applicationText, documentFilename, YES, null),
            Arguments.of(applicationText, null, YES, null),
            Arguments.of(null, documentFilename, YES, null),
            Arguments.of(applicationText, documentFilename, NO, copyToOtherPartyText),
            Arguments.of(applicationText, null, NO, copyToOtherPartyText),
            Arguments.of(null, documentFilename, NO, copyToOtherPartyText)
        );
    }

    @ParameterizedTest
    @MethodSource()
    void checkGetGenericTseApplicationFromRespondentTse(
        String applicationText,
        String documentFilename,
        String copyToOtherPartyYesOrNo,
        String copyToOtherPartyText
    ) {
        String caseReference = "1234/456";
        RespondentTse respondentTse =
            getRespondentTse(applicationText, documentFilename, copyToOtherPartyYesOrNo, copyToOtherPartyText);

        GenericTseApplication actualGenericTseApplication =
            RespondentTseUtil.getGenericTseApplicationFromRespondentTse(respondentTse, caseReference);

        assertThat(actualGenericTseApplication.getCaseNumber())
            .isEqualTo(caseReference);
        assertThat(actualGenericTseApplication.getApplicant())
            .isEqualTo(RESPONDENT_TITLE);
        assertThat(actualGenericTseApplication.getApplicationType())
            .isEqualTo("Amend response");
        assertThat(actualGenericTseApplication.getApplicationDate())
            .isEqualTo(UtilHelper.formatCurrentDate(LocalDate.now()));
        assertThat(actualGenericTseApplication.getTellOrAskTribunal())
            .isEqualTo(applicationText);
        assertThat(actualGenericTseApplication.getSupportingEvidence())
            .isEqualTo(documentFilename);
        assertThat(actualGenericTseApplication.getCopyToOtherPartyYesOrNo())
            .isEqualTo(copyToOtherPartyYesOrNo);
        assertThat(actualGenericTseApplication.getCopyToOtherPartyText())
            .isEqualTo(copyToOtherPartyText);
    }

    private static RespondentTse getRespondentTse(String applicationText, String documentFilename,
                                                  String copyToOtherPartyYesOrNo, String copyToOtherPartyText) {
        RespondentTse respondentTse = new RespondentTse();
        respondentTse.setContactApplicationText(applicationText);
        respondentTse.setContactApplicationType("Amend response");
        respondentTse.setCopyToOtherPartyYesOrNo(copyToOtherPartyYesOrNo);
        respondentTse.setCopyToOtherPartyText(copyToOtherPartyText);

        if (!isNullOrEmpty(documentFilename)) {
            UploadedDocumentType docType = new UploadedDocumentType();
            docType.setDocumentFilename("test-Document-Filename.pdf");
            respondentTse.setContactApplicationFile(docType);
        }

        return respondentTse;
    }
}
