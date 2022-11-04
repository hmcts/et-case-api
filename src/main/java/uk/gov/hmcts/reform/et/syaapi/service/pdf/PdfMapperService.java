package uk.gov.hmcts.reform.et.syaapi.service.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import uk.gov.dwp.regex.InvalidPostcodeException;
import uk.gov.dwp.regex.PostCodeValidator;
import uk.gov.hmcts.et.common.model.ccd.Address;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.ClaimantOtherType;
import uk.gov.hmcts.et.common.model.ccd.types.NewEmploymentType;
import uk.gov.hmcts.et.common.model.ccd.types.RepresentedTypeC;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentSumType;
import uk.gov.hmcts.reform.et.syaapi.constants.ClaimTypesConstants;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.rightPad;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.NO;
import static uk.gov.hmcts.ecm.common.model.helper.Constants.YES;

/**
 * Maps Case Data attributes to fields within the PDF template.
 * Inputs that are accepted from the ET1 form can then be mapped to
 * the corresponding questions within the template PDF (ver. ET1_0922)
 * as described in {@link PdfMapperConstants}
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.GodClass",
    "PMD.CyclomaticComplexity", "PMD.TooManyMethods", "PMD.CognitiveComplexity", "PMD.CollapsibleIfStatements",
    "PMD.AvoidDeeplyNestedIfStmts", "PMD.NPathComplexity", "PMD.UselessParentheses"})
public class PdfMapperService {
    private static final String PREFIX_13_R5 = "13 R5";
    private static final String PREFIX_2_7_R3 = "2.7 R3";
    private static final String PREFIX_13_R4 = "13 R4";
    private static final String PREFIX_2_2 = "2.2";
    private static final String PREFIX_2_5_R2 = "2.5 R2";
    private static final String PREFIX_2_3 = "2.3";
    private static final String PREFIX_2_6 = "2.6";
    private static final String PREFIX_2_8 = "2.8";
    private static final String OTHER = "Other";
    private static final Map<String, String> TITLES = Map.of(
        "Mr", PdfMapperConstants.Q1_TITLE_MR,
        "Mrs", PdfMapperConstants.Q1_TITLE_MRS,
        "Miss", PdfMapperConstants.Q1_TITLE_MISS,
        "Ms", PdfMapperConstants.Q1_TITLE_MS,
        OTHER, PdfMapperConstants.Q1_TITLE_OTHER,
        "Other_Specify", PdfMapperConstants.Q1_TITLE_OTHER_SPECIFY

    );
    private static final Map<String, String> TITLE_MAP = Map.of(
        "Mr", "Mister",
        "Mrs", "Missus",
        "Miss", "Miss",
        "Ms", "Miz",
        OTHER, "Miz"
    );
    private static final String[] ADDRESS_PREFIX = {
        PREFIX_2_2,
        PREFIX_2_5_R2,
        PREFIX_2_7_R3,
        PREFIX_13_R4,
        PREFIX_13_R5
    };
    private static final String REP_ADDRESS_PREFIX = "11.3 Representative's address:";
    private static final String CLAIMANT_ADDRESS_PREFIX = "1.5";
    private static final int MULTIPLE_RESPONDENTS = 2;
    private static final int MAX_RESPONDENTS = 5;
    private static final String[] ACAS_PREFIX = {
        PREFIX_2_3,
        PREFIX_2_6,
        PREFIX_2_8,
        PREFIX_13_R4,
        PREFIX_13_R5
    };
    // This constant is defined because of the error in the pdf template file
    // The field for pay before tax options checked value in the pdf template
    // for annually apy before tax was monthly
    private static final String ANNUALLY = "annually";
    public static final String WEEKLY = "Weekly";
    public static final String MONTHLY = "Monthly";
    private static final String MONTHS = "Months";
    private static final String WEEKS = "Weeks";
    private static final String ANNUAL = "Annual";
    private static final String EMAIL = "Email";
    private static final String POST = "Post";
    private static final String FAX = "Fax";
    private static final String YES_LOWERCASE = "yes";
    private static final String NO_LOWERCASE = "no";
    public static final String NO_LONGER_WORKING = "No longer working";
    public static final String NOTICE = "Notice";


    /**
     * Maps the parameters within case data to the inputs of the PDF Template.
     *
     * @param caseData          the case data that is to be mapped to the inputs in the PDF Template.
     * @return                  a Map containing the values from case data with the
     *                          corresponding PDF input labels as a key.
     */
    public Map<String, Optional<String>> mapHeadersToPdf(CaseData caseData) {
        ConcurrentHashMap<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData == null) {
            return printFields;
        }
        try {
            printFields.putAll(printPersonalDetails(caseData));
        } catch (Exception e) {
            log.error("Exception occurred in PDF MAPPER while setting personal details \n" + e.getMessage(), e);
        }
        if (caseData.getClaimantRequests() != null
            && caseData.getClaimantRequests().getClaimDescription() != null) {
            printFields.put(
                PdfMapperConstants.Q8_CLAIM_DESCRIPTION,
                ofNullable(caseData.getClaimantRequests().getClaimDescription())
            );
        }
        try {
            if (caseData.getRepresentativeClaimantType() != null) {
                printFields.putAll(printRepresentative(caseData.getRepresentativeClaimantType()));
            }
        } catch (Exception e) {
            log.error("Exception occurred in PDF MAPPER while setting representative details \n" + e.getMessage(), e);
        }
        printFields.put(
            PdfMapperConstants.Q15_ADDITIONAL_INFORMATION,
            ofNullable(caseData.getEt1VettingAdditionalInformationTextArea())
        );
        try {
            printFields.put(PdfMapperConstants.TRIBUNAL_OFFICE, ofNullable(caseData.getManagingOffice()));
            printFields.put(PdfMapperConstants.CASE_NUMBER, ofNullable(caseData.getEthosCaseReference()));
            printFields.put(PdfMapperConstants.DATE_RECEIVED, ofNullable(caseData.getReceiptDate()));
            printFields.putAll(printHearingPreferences(caseData));
            printFields.putAll(printRespondentDetails(caseData));
            printFields.putAll(printMultipleClaimsDetails(caseData));
            printFields.putAll(printEmploymentDetails(caseData));
            printFields.putAll(printTypeAndDetailsOfClaim(caseData));
            printFields.putAll(printCompensation(caseData));
            printFields.putAll(printWhistleBlowing(caseData));
        } catch (Exception e) {
            log.error("Exception occurred in PDF MAPPER \n" + e.getMessage(), e);
        }
        return printFields;
    }

    public Map<String, Optional<String>> printPersonalDetails(CaseData caseData) {
        ConcurrentHashMap<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData.getClaimantIndType() != null
            && caseData.getClaimantIndType().getClaimantPreferredTitle() != null
            && TITLES.containsKey(caseData.getClaimantIndType().getClaimantPreferredTitle())) {
            printFields.put(
                TITLES.get(caseData.getClaimantIndType().getClaimantPreferredTitle()),
                ofNullable(TITLE_MAP.get(caseData.getClaimantIndType().getClaimantPreferredTitle()))
            );
            if (OTHER.equals(caseData.getClaimantIndType().getClaimantPreferredTitle())) {
                printFields.put(
                    TITLES.get("Other_Specify"),
                    ofNullable(String.valueOf(caseData.getClaimantIndType().getClaimantTitleOther()))
                );
            }
        }

        printFields.put(
            PdfMapperConstants.Q1_FIRST_NAME,
            ofNullable(caseData.getClaimantIndType().getClaimantFirstNames())
        );
        printFields.put(
            PdfMapperConstants.Q1_SURNAME,
            ofNullable(caseData.getClaimantIndType().getClaimantLastName())
        );
        LocalDate dob = LocalDate.parse(caseData.getClaimantIndType().getClaimantDateOfBirth());
        printFields.put(
            PdfMapperConstants.Q1_DOB_DAY,
            ofNullable(StringUtils.leftPad(String.valueOf(dob.getDayOfMonth()),
                                           2, "0"
            ))
        );
        printFields.put(
            PdfMapperConstants.Q1_DOB_MONTH,
            ofNullable(StringUtils.leftPad(String.valueOf(dob.getMonthValue()),
                                           2, "0"
            ))
        );
        printFields.put(PdfMapperConstants.Q1_DOB_YEAR, Optional.of(String.valueOf(dob.getYear())));
        if (caseData.getClaimantIndType() != null
            && caseData.getClaimantIndType().getClaimantSex() != null) {
            if ("Male".equals(caseData.getClaimantIndType().getClaimantSex())) {
                printFields.put(PdfMapperConstants.Q1_SEX_MALE, Optional.of("Yes"));
            } else if ("Female".equals(caseData.getClaimantIndType().getClaimantSex())) {
                printFields.put(PdfMapperConstants.Q1_SEX_FEMALE, Optional.of("female"));
            } else if ("Prefer not to say".equals(caseData.getClaimantIndType().getClaimantSex())) {
                printFields.put(PdfMapperConstants.Q1_SEX_PREFER_NOT_TO_SAY, Optional.of("prefer not to say"));
            }
        }
        if (caseData.getClaimantType() != null) {
            if (caseData.getClaimantType().getClaimantAddressUK() != null) {
                printFields.put(
                    String.format(PdfMapperConstants.RP2_HOUSE_NUMBER, CLAIMANT_ADDRESS_PREFIX),
                    ofNullable(caseData.getClaimantType().getClaimantAddressUK().getAddressLine1())
                );
                printFields.put(
                    String.format(PdfMapperConstants.QX_STREET, CLAIMANT_ADDRESS_PREFIX),
                    ofNullable(caseData.getClaimantType().getClaimantAddressUK().getAddressLine2())
                );
                printFields.put(
                    String.format(PdfMapperConstants.RP_POST_TOWN, CLAIMANT_ADDRESS_PREFIX),
                    ofNullable(caseData.getClaimantType().getClaimantAddressUK().getPostTown())
                );
                printFields.put(
                    String.format(PdfMapperConstants.QX_COUNTY, CLAIMANT_ADDRESS_PREFIX),
                    ofNullable(caseData.getClaimantType().getClaimantAddressUK().getCounty())
                );
                printFields.put(
                    String.format(PdfMapperConstants.QX_POSTCODE, CLAIMANT_ADDRESS_PREFIX),
                    ofNullable(formatPostcode(caseData.getClaimantType().getClaimantAddressUK().getPostCode()))
                );
            }
            printFields.put(
                String.format(PdfMapperConstants.QX_PHONE_NUMBER, "1.6"),
                ofNullable(caseData.getClaimantType().getClaimantPhoneNumber())
            );
            printFields.put(
                PdfMapperConstants.Q1_MOBILE_NUMBER,
                ofNullable(caseData.getClaimantType().getClaimantMobileNumber())
            );
            printFields.put(
                PdfMapperConstants.Q1_EMAIL,
                ofNullable(caseData.getClaimantType().getClaimantEmailAddress())
            );
            String contactPreference = caseData.getClaimantType().getClaimantContactPreference();

            if (EMAIL.equals(contactPreference)) {
                printFields.put(PdfMapperConstants.Q1_CONTACT_EMAIL, Optional.of(contactPreference));
            } else if ("Post".equals(contactPreference)) {
                printFields.put(PdfMapperConstants.Q1_CONTACT_POST, Optional.of(contactPreference));
            }
        }
        return printFields;
    }

    public Map<String, Optional<String>> printHearingPreferences(CaseData caseData) {
        ConcurrentHashMap<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData.getClaimantHearingPreference() != null) {
            if (caseData.getClaimantHearingPreference().getReasonableAdjustments() != null
                && YES.equals(caseData.getClaimantHearingPreference().getReasonableAdjustments())) {
                printFields.put(PdfMapperConstants.Q12_DISABILITY_YES, Optional.of(YES));
            } else {
                printFields.put(PdfMapperConstants.Q12_DISABILITY_NO, Optional.of(NO_LOWERCASE));
            }
            printFields.put(
                PdfMapperConstants.Q12_DISABILITY_DETAILS,
                ofNullable(caseData.getClaimantHearingPreference().getReasonableAdjustmentsDetail())
            );
        }
        if (caseData.getClaimantHearingPreference() == null
            || caseData.getClaimantHearingPreference().getHearingPreferences() == null
            || caseData.getClaimantHearingPreference().getHearingPreferences().isEmpty()) {
            printFields.put(PdfMapperConstants.I_CAN_TAKE_PART_IN_NO_HEARINGS, Optional.of(YES));
            return printFields;
        }
        if (!caseData.getClaimantHearingPreference().getHearingPreferences().contains("Video")
            && !caseData.getClaimantHearingPreference().getHearingPreferences().contains("Phone")) {
            printFields.put(
                PdfMapperConstants.I_CAN_TAKE_PART_IN_NO_HEARINGS,
                Optional.of(YES)
            );
            printFields.put(
                PdfMapperConstants.I_CAN_TAKE_PART_IN_NO_HEARINGS_EXPLAIN,
                ofNullable(caseData.getClaimantHearingPreference().getHearingAssistance())
            );
        }
        if (caseData.getClaimantHearingPreference() != null
            && caseData.getClaimantHearingPreference().getHearingPreferences() != null
            && caseData.getClaimantHearingPreference().getHearingPreferences().contains("Video")) {
            printFields.put(
                PdfMapperConstants.I_CAN_TAKE_PART_IN_VIDEO_HEARINGS,
                Optional.of(YES)
            );
        }
        if (caseData.getClaimantHearingPreference() != null
            && caseData.getClaimantHearingPreference().getHearingPreferences() != null
            && caseData.getClaimantHearingPreference().getHearingPreferences().contains("Phone")) {
            printFields.put(
                PdfMapperConstants.I_CAN_TAKE_PART_IN_PHONE_HEARINGS,
                Optional.of(YES)
            );
        }

        return printFields;
    }

    public Map<String, Optional<String>> printRespondentDetails(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        List<RespondentSumTypeItem> respondentSumTypeList = caseData.getRespondentCollection();
        if (respondentSumTypeList != null) {
            if (respondentSumTypeList.size() >= MULTIPLE_RESPONDENTS) {
                printFields.put(PdfMapperConstants.Q2_OTHER_RESPONDENTS, Optional.of(YES));
            }
            for (int i = 0; i < respondentSumTypeList.size() && i < MAX_RESPONDENTS; i++) {
                RespondentSumType respondent = respondentSumTypeList.get(i).getValue();
                if (i == 0) {
                    printFields.put(
                        PdfMapperConstants.Q2_EMPLOYER_NAME,
                        ofNullable(respondent.getRespondentName())
                    );
                } else {
                    printFields.put(
                        String.format(PdfMapperConstants.QX_NAME, ADDRESS_PREFIX[i]),
                        ofNullable(respondent.getRespondentName())
                    );
                }
                printFields.putAll(printRespondent(respondent, ADDRESS_PREFIX[i]));
                printFields.putAll(printRespondentAcas(respondent, ACAS_PREFIX[i]));
            }
        }
        if (caseData.getClaimantWorkAddress() != null
            && caseData.getClaimantWorkAddress().getClaimantWorkAddress() != null) {
            Address claimantWorkAddress = caseData.getClaimantWorkAddress().getClaimantWorkAddress();
            printFields.putAll(printWorkAddress(claimantWorkAddress));
        }
        return printFields;
    }

    private Map<String, Optional<String>> printRespondent(RespondentSumType respondent, String questionPrefix) {
        String respondentTownOrCityPrefix = PdfMapperConstants.RP_POST_TOWN;
        String respondentHouseNumberPrefix = PdfMapperConstants.QX_HOUSE_NUMBER;
        if (PREFIX_2_7_R3.equals(questionPrefix)
            || PREFIX_13_R5.equals(questionPrefix)
            || PREFIX_13_R4.equals(questionPrefix)
            || PREFIX_2_2.equals(questionPrefix)) {
            respondentHouseNumberPrefix = PdfMapperConstants.RP2_HOUSE_NUMBER;
        }
        if (PREFIX_2_5_R2.equals(questionPrefix)) {
            respondentTownOrCityPrefix = PdfMapperConstants.RP2_POST_TOWN;
            respondentHouseNumberPrefix = PdfMapperConstants.RP2_HOUSE_NUMBER;
        }

        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (respondent.getRespondentAddress() != null) {
            printFields.put(
                String.format(respondentHouseNumberPrefix, questionPrefix),
                ofNullable(respondent.getRespondentAddress().getAddressLine1())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_STREET, questionPrefix),
                ofNullable(respondent.getRespondentAddress().getAddressLine2())
            );
            printFields.put(
                String.format(respondentTownOrCityPrefix, questionPrefix),
                ofNullable(respondent.getRespondentAddress().getPostTown())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_COUNTY, questionPrefix),
                ofNullable(respondent.getRespondentAddress().getCounty())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_POSTCODE, questionPrefix),
                ofNullable(formatPostcode(respondent.getRespondentAddress().getPostCode()))
            );
        }

        return printFields;
    }

    private Map<String, Optional<String>> printRespondentAcas(RespondentSumType respondent,
                                                              String questionPrefix) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        String acasYesNo = respondent.getRespondentAcasQuestion().isEmpty() ? NO :
            respondent.getRespondentAcasQuestion();
        if (YES.equals(acasYesNo)) {
            if (PREFIX_2_8.equals(questionPrefix)) {
                printFields.put("2.8 yes", Optional.of(acasYesNo)
                );
            } else {
                printFields.put(
                    String.format(PdfMapperConstants.QX_HAVE_ACAS_YES, questionPrefix), Optional.of(acasYesNo)
                );
            }
            printFields.put(
                String.format(PdfMapperConstants.QX_ACAS_NUMBER, questionPrefix),
                ofNullable(respondent.getRespondentAcas())
            );
        } else {
            if (PREFIX_2_6.equals(questionPrefix) || PREFIX_13_R5.equals(questionPrefix)) {
                printFields.put(String.format(
                    PdfMapperConstants.QX_HAVE_ACAS_NO, questionPrefix), Optional.of(NO));
            } else if (PREFIX_2_8.equals(questionPrefix)) {
                printFields.put("2.8 no", Optional.of("Yes"));
            } else {
                printFields.put(String.format(
                    PdfMapperConstants.QX_HAVE_ACAS_NO, questionPrefix), Optional.of(NO_LOWERCASE));
            }
            switch (respondent.getRespondentAcasNo()) {
                case "Unfair Dismissal":
                    printFields.put(String.format(PdfMapperConstants.QX_ACAS_A1, questionPrefix), Optional.of(YES));
                    break;
                case "Another person":
                    printFields.put(String.format(PdfMapperConstants.QX_ACAS_A2, questionPrefix), Optional.of(YES));
                    break;
                case "No Power":
                    printFields.put(String.format(PdfMapperConstants.QX_ACAS_A3, questionPrefix), Optional.of(YES));
                    break;
                case "Employer already in touch":
                    printFields.put(String.format(PdfMapperConstants.QX_ACAS_A4, questionPrefix), Optional.of(YES));
                    break;
                default:
                    break;
            }
        }
        return printFields;
    }

    public Map<String, Optional<String>> printMultipleClaimsDetails(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if ("Multiple".equals(caseData.getEcmCaseType())) {
            printFields.put(PdfMapperConstants.Q3_MORE_CLAIMS_YES, Optional.of(YES));
        } else {
            printFields.put(PdfMapperConstants.Q3_MORE_CLAIMS_NO, Optional.of(NO));
        }
        return printFields;
    }

    private Map<String, Optional<String>> printWorkAddress(Address claimantWorkAddress) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        printFields.put(
            PdfMapperConstants.Q2_DIFFADDRESS_NUMBER,
            ofNullable(claimantWorkAddress.getAddressLine1())
        );
        printFields.put(
            PdfMapperConstants.Q2_DIFFADDRESS_STREET,
            ofNullable(claimantWorkAddress.getAddressLine2())
        );
        printFields.put(
            PdfMapperConstants.Q2_DIFFADDRESS_TOWN,
            ofNullable(claimantWorkAddress.getPostTown())
        );
        printFields.put(
            PdfMapperConstants.Q2_DIFFADDRESS_COUNTY,
            ofNullable(claimantWorkAddress.getCounty())
        );
        printFields.put(
            PdfMapperConstants.Q2_DIFFADDRESS_POSTCODE,
            ofNullable(formatPostcode(claimantWorkAddress.getPostCode()))
        );
        return printFields;
    }

    public Map<String, Optional<String>> printEmploymentDetails(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        ClaimantOtherType claimantOtherType = caseData.getClaimantOtherType();
        if (claimantOtherType != null) {
            if (YES.equals(claimantOtherType.getPastEmployer())) {
                printFields.put(PdfMapperConstants.Q4_EMPLOYED_BY_YES, Optional.of(YES));

                printFields.put(
                    PdfMapperConstants.Q5_EMPLOYMENT_START,
                    ofNullable(claimantOtherType.getClaimantEmployedFrom())
                );

                String stillWorking = NO_LONGER_WORKING.equals(claimantOtherType.getStillWorking()) ? NO :
                    YES;
                if (YES.equals(stillWorking)) {
                    printFields.put(PdfMapperConstants.Q5_CONTINUING_YES, Optional.of(stillWorking));
                    if (NOTICE.equals(claimantOtherType.getStillWorking())) {
                        printFields.put(
                            PdfMapperConstants.Q5_NOT_ENDED,
                            ofNullable(claimantOtherType.getClaimantEmployedNoticePeriod())
                        );
                    }

                } else {
                    printFields.put(PdfMapperConstants.Q5_CONTINUING_NO, Optional.of(NO_LOWERCASE));
                    printFields.put(
                        PdfMapperConstants.Q5_EMPLOYMENT_END,
                        ofNullable(claimantOtherType.getClaimantEmployedTo())
                    );
                }

                printFields.put(
                    PdfMapperConstants.Q5_DESCRIPTION,
                    ofNullable(claimantOtherType.getClaimantOccupation())
                );
                printFields.putAll(printRemuneration(claimantOtherType));

                if (caseData.getNewEmploymentType() != null) {
                    printNewEmploymentFields(caseData, printFields);
                }

            } else if (NO.equals(claimantOtherType.getPastEmployer())) {
                printFields.put(
                    PdfMapperConstants.Q4_EMPLOYED_BY_NO, Optional.of(YES));
            }
        }
        return printFields;
    }

    private void printNewEmploymentFields(CaseData caseData, Map<String, Optional<String>> printFields) {
        NewEmploymentType newEmploymentType = caseData.getNewEmploymentType();
        if (newEmploymentType.getNewJob() != null) {
            if (YES.equals(newEmploymentType.getNewJob())) {
                printFields.put(PdfMapperConstants.Q7_OTHER_JOB_YES, Optional.of(YES));
                printFields.put(
                    PdfMapperConstants.Q7_START_WORK,
                    ofNullable(newEmploymentType.getNewlyEmployedFrom())
                );
                printFields.put(
                    PdfMapperConstants.Q7_EARNING,
                    ofNullable(newEmploymentType.getNewPayBeforeTax())
                );
                printJobPayInterval(printFields, newEmploymentType);
            } else if (NO.equals(newEmploymentType.getNewJob())) {
                printFields.put(PdfMapperConstants.Q7_OTHER_JOB_NO, Optional.of(NO));
            }
        }
    }

    private void printJobPayInterval(Map<String, Optional<String>> printFields, NewEmploymentType newEmploymentType) {
        if (WEEKS.equals(newEmploymentType.getNewJobPayInterval())) {
            printFields.put(PdfMapperConstants.Q7_EARNING_WEEKLY, Optional.of(WEEKLY));
        } else if (MONTHS.equals(newEmploymentType.getNewJobPayInterval())) {
            printFields.put(PdfMapperConstants.Q7_EARNING_MONTHLY, Optional.of(MONTHLY));
        } else if (ANNUAL.equals(newEmploymentType.getNewJobPayInterval())) {
            printFields.put(PdfMapperConstants.Q7_EARNING_ANNUAL, Optional.of(ANNUALLY));
        }
    }

    private Map<String, Optional<String>> printRemuneration(ClaimantOtherType claimantOtherType) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        printFields.put(
            PdfMapperConstants.Q6_HOURS,
            ofNullable(claimantOtherType.getClaimantAverageWeeklyHours())
        );
        printFields.put(
            PdfMapperConstants.Q6_GROSS_PAY,
            ofNullable(claimantOtherType.getClaimantPayBeforeTax())
        );
        printFields.put(PdfMapperConstants.Q6_NET_PAY, ofNullable(claimantOtherType.getClaimantPayAfterTax()));
        if (claimantOtherType.getClaimantPayCycle() != null) {
            switch (claimantOtherType.getClaimantPayCycle()) {
                case WEEKS:
                    printFields.put(PdfMapperConstants.Q6_GROSS_PAY_WEEKLY, Optional.of(WEEKLY));
                    printFields.put(PdfMapperConstants.Q6_NET_PAY_WEEKLY, Optional.of(WEEKLY));
                    break;
                case MONTHS:
                    printFields.put(PdfMapperConstants.Q6_GROSS_PAY_MONTHLY, Optional.of(MONTHLY));
                    printFields.put(PdfMapperConstants.Q6_NET_PAY_MONTHLY, Optional.of(MONTHLY));
                    break;
                case ANNUAL:
                    printFields.put(PdfMapperConstants.Q6_GROSS_PAY_ANNUAL, Optional.of(ANNUALLY));
                    printFields.put(PdfMapperConstants.Q6_NET_PAY_ANNUAL, Optional.of(ANNUALLY));
                    break;
                default:
                    break;
            }
        }

        // Section 6.3
        if (claimantOtherType.getClaimantNoticePeriod() != null
            && NO_LONGER_WORKING.equals(claimantOtherType.getStillWorking())) {
            if (YES.equals(claimantOtherType.getClaimantNoticePeriod())) {
                printFields.put(
                    PdfMapperConstants.Q6_PAID_NOTICE_YES, Optional.of(YES)
                );
                String noticeUnit = claimantOtherType.getClaimantNoticePeriodUnit();
                if (WEEKS.equals(noticeUnit)) {
                    printFields.put(
                        PdfMapperConstants.Q6_NOTICE_WEEKS,
                        ofNullable(claimantOtherType.getClaimantNoticePeriodDuration())
                    );
                } else if (MONTHS.equals(noticeUnit)) {
                    printFields.put(
                        PdfMapperConstants.Q6_NOTICE_MONTHS,
                        ofNullable(claimantOtherType.getClaimantNoticePeriodDuration())
                    );
                }
            } else if (NO.equals(claimantOtherType.getClaimantNoticePeriod())) {
                printFields.put(PdfMapperConstants.Q6_PAID_NOTICE_NO, Optional.of(NO));
            }
        }


        // Section 6.4
        if (claimantOtherType.getClaimantPensionContribution() != null) {
            String pensionContributionYesNo = claimantOtherType.getClaimantPensionContribution().isEmpty() ? NO :
                claimantOtherType.getClaimantPensionContribution();
            if (YES.equals(pensionContributionYesNo)) {
                printFields.put(
                    PdfMapperConstants.Q6_PENSION_YES,
                    ofNullable(claimantOtherType.getClaimantPensionContribution())
                );
                printFields.put(
                    PdfMapperConstants.Q6_PENSION_WEEKLY,
                    ofNullable(claimantOtherType.getClaimantPensionWeeklyContribution())
                );

            } else {
                printFields.put(PdfMapperConstants.Q6_PENSION_NO, Optional.of(YES));
            }
        }

        printFields.put(
            PdfMapperConstants.Q6_OTHER_BENEFITS,
            ofNullable(claimantOtherType.getClaimantBenefitsDetail())
        );
        return printFields;
    }

    public Map<String, Optional<String>> printTypeAndDetailsOfClaim(CaseData caseData) {
        return new ConcurrentHashMap<>(retrieveTypeOfClaimsPrintFields(caseData));
    }

    private static Map<String, Optional<String>> retrieveTypeOfClaimsPrintFields(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData.getTypesOfClaim() == null || caseData.getTypesOfClaim().isEmpty()) {
            return printFields;
        }
        for (String typeOfClaim : caseData.getTypesOfClaim()) {
            mapPrintFields(printFields, typeOfClaim, caseData);
        }
        return printFields;
    }

    private static void mapPrintFields(Map<String, Optional<String>> printFields,
                                       String typeOfClaim,
                                       CaseData caseData) {
        switch (typeOfClaim) {
            case "discrimination":
                printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_DISCRIMINATION, Optional.of(YES));
                if (caseData.getClaimantRequests() != null
                    && caseData.getClaimantRequests().getDiscriminationClaims() != null) {
                    printFields.putAll(retrieveDiscriminationClaimsPrintFields(
                        caseData.getClaimantRequests().getDiscriminationClaims()));
                }
                break;
            case "payRelated":
                if (caseData.getClaimantRequests() != null && caseData.getClaimantRequests().getPayClaims() != null) {
                    printFields.putAll(retrievePayClaimsPrintFields(caseData.getClaimantRequests().getPayClaims()));
                }
                break;
            case "unfairDismissal":
                printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_UNFAIRLY_DISMISSED, Optional.of(YES));
                break;
            case "whistleBlowing":
                printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_WHISTLE_BLOWING, Optional.of(YES));
                break;
            case "otherTypesOfClaims":
                printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_OTHER_TYPES_OF_CLAIMS, Optional.of(YES));
                if (caseData.getClaimantRequests() != null) {
                    printFields.put(
                        PdfMapperConstants.Q8_ANOTHER_TYPE_OF_CLAIM_TEXT_AREA,
                        ofNullable(caseData.getClaimantRequests().getOtherClaim())
                    );
                }
                break;
            default:
                break;
        }
    }

    private static Map<String, Optional<String>> retrieveDiscriminationClaimsPrintFields(
        List<String> discriminationClaims) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        for (String discriminationType : discriminationClaims) {
            switch (discriminationType) {
                case ClaimTypesConstants.AGE:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_AGE, Optional.of(YES));
                    break;
                case ClaimTypesConstants.DISABILITY:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_DISABILITY, Optional.of(YES));
                    break;
                case ClaimTypesConstants.ETHNICITY:
                case ClaimTypesConstants.RACE:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_RACE, Optional.of(YES));
                    break;
                case ClaimTypesConstants.GENDER_REASSIGNMENT:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_GENDER_REASSIGNMENT, Optional.of(YES));
                    break;
                case ClaimTypesConstants.MARRIAGE_OR_CIVIL_PARTNERSHIP:
                    printFields.put(
                        PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_MARRIAGE_OR_CIVIL_PARTNERSHIP,
                        Optional.of(YES)
                    );
                    break;
                case ClaimTypesConstants.PREGNANCY_OR_MATERNITY:
                    printFields.put(
                        PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_PREGNANCY_OR_MATERNITY,
                        Optional.of(YES)
                    );
                    break;
                case ClaimTypesConstants.RELIGION_OR_BELIEF:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_RELIGION_OR_BELIEF, Optional.of(YES));
                    break;
                case ClaimTypesConstants.SEX:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_SEX, Optional.of(YES));
                    break;
                case ClaimTypesConstants.SEXUAL_ORIENTATION:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_DISCRIMINATION_SEXUAL_ORIENTATION, Optional.of(YES));
                    break;
                default:
                    break;
            }
        }
        return printFields;
    }

    private static Map<String, Optional<String>> retrievePayClaimsPrintFields(
        List<String> payClaims) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        for (String payClaimType : payClaims) {
            switch (payClaimType) {
                case ClaimTypesConstants.ARREARS:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_PAY_CLAIMS_ARREARS, Optional.of(YES));
                    checkIAmOwedBox(printFields);
                    break;
                case ClaimTypesConstants.HOLIDAY_PAY:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_PAY_CLAIMS_HOLIDAY_PAY, Optional.of(YES));
                    checkIAmOwedBox(printFields);
                    break;
                case ClaimTypesConstants.NOTICE_PAY:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_PAY_CLAIMS_NOTICE_PAY, Optional.of(YES));
                    checkIAmOwedBox(printFields);
                    break;
                case ClaimTypesConstants.OTHER_PAYMENTS:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_PAY_CLAIMS_OTHER_PAYMENTS, Optional.of(YES));
                    checkIAmOwedBox(printFields);
                    break;
                case ClaimTypesConstants.REDUNDANCY_PAY:
                    printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_REDUNDANCY_PAYMENT, Optional.of(YES));
                    break;
                default:
                    break;
            }
        }
        return printFields;
    }

    private static void checkIAmOwedBox(Map<String, Optional<String>> printFields) {
        if (!printFields.containsKey(PdfMapperConstants.Q8_TYPE_OF_CLAIM_I_AM_OWED)) {
            printFields.put(PdfMapperConstants.Q8_TYPE_OF_CLAIM_I_AM_OWED, Optional.of(YES));
        }
    }


    public Map<String, Optional<String>> printCompensation(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData.getClaimantRequests() != null
            && caseData.getClaimantRequests().getClaimOutcome() != null
            && !caseData.getClaimantRequests().getClaimOutcome().isEmpty()) {
            for (String claimOutcome : caseData.getClaimantRequests().getClaimOutcome()) {
                switch (claimOutcome) {
                    case "compensation":
                        printFields.put(
                            PdfMapperConstants.Q9_CLAIM_SUCCESSFUL_REQUEST_COMPENSATION,
                            Optional.of(YES_LOWERCASE)
                        );
                        break;
                    case "tribunal":
                        printFields.put(
                            PdfMapperConstants.Q9_CLAIM_SUCCESSFUL_REQUEST_DISCRIMINATION_RECOMMENDATION,
                            Optional.of(YES_LOWERCASE)
                        );
                        break;
                    case "oldJob":
                        printFields.put(
                            PdfMapperConstants.Q9_CLAIM_SUCCESSFUL_REQUEST_OLD_JOB_BACK_AND_COMPENSATION,
                            Optional.of(YES_LOWERCASE)
                        );
                        break;
                    case "anotherJob":
                        printFields.put(
                            PdfMapperConstants.Q9_CLAIM_SUCCESSFUL_REQUEST_ANOTHER_JOB,
                            Optional.of(YES_LOWERCASE)
                        );
                        break;
                    default:
                        break;
                }
            }
            String claimantCompensationText =
                caseData.getClaimantRequests().getClaimantCompensationText() == null ? "" :
                    caseData.getClaimantRequests().getClaimantCompensationText();
            String claimantCompensationAmount =
                caseData.getClaimantRequests().getClaimantCompensationAmount() == null ? "" :
                    caseData.getClaimantRequests().getClaimantCompensationAmount();
            printFields.put(
                PdfMapperConstants.Q9_WHAT_COMPENSATION_REMEDY_ARE_YOU_SEEKING,
                Optional.of(claimantCompensationText + " " + claimantCompensationAmount)
            );
        }

        return printFields;
    }

    public Map<String, Optional<String>> printWhistleBlowing(CaseData caseData) {
        Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
        if (caseData.getClaimantRequests() == null) {
            return printFields;
        }
        if (caseData.getClaimantRequests().getWhistleblowing() != null) {
            if (YES.equals(caseData.getClaimantRequests().getWhistleblowing())) {
                printFields.put(PdfMapperConstants.Q10_WHISTLE_BLOWING, Optional.of(YES_LOWERCASE));
                printFields.put(
                    PdfMapperConstants.Q10_WHISTLE_BLOWING_REGULATOR,
                    ofNullable(caseData.getClaimantRequests().getWhistleblowingAuthority())
                );
            }
        }

        return printFields;
    }

    public Map<String, Optional<String>> printRepresentative(RepresentedTypeC representativeClaimantType) {
        if (representativeClaimantType != null) {
            Map<String, Optional<String>> printFields = new ConcurrentHashMap<>();
            printFields.put(
                PdfMapperConstants.Q11_REP_NAME,
                ofNullable(representativeClaimantType.getNameOfRepresentative())
            );
            printFields.put(
                PdfMapperConstants.Q11_REP_ORG,
                ofNullable(representativeClaimantType.getNameOfOrganisation())
            );
            printFields.put(PdfMapperConstants.Q11_REP_NUMBER, Optional.of(""));
            Address repAddress = representativeClaimantType.getRepresentativeAddress();
            printFields.put(
                String.format(PdfMapperConstants.QX_HOUSE_NUMBER, REP_ADDRESS_PREFIX),
                ofNullable(repAddress.getAddressLine1())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_STREET, REP_ADDRESS_PREFIX),
                ofNullable(repAddress.getAddressLine2())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_POST_TOWN, REP_ADDRESS_PREFIX),
                ofNullable(repAddress.getPostTown())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_COUNTY, REP_ADDRESS_PREFIX),
                ofNullable(repAddress.getCounty())
            );
            printFields.put(
                String.format(PdfMapperConstants.QX_POSTCODE, REP_ADDRESS_PREFIX),
                ofNullable(formatPostcode(repAddress.getPostCode()))
            );
            printFields.put(
                PdfMapperConstants.Q11_PHONE_NUMBER,
                ofNullable(representativeClaimantType.getRepresentativePhoneNumber())
            );
            printFields.put(
                PdfMapperConstants.Q11_MOBILE_NUMBER,
                ofNullable(representativeClaimantType.getRepresentativeMobileNumber())
            );
            printFields.put(
                PdfMapperConstants.Q11_EMAIL,
                ofNullable(representativeClaimantType.getRepresentativeEmailAddress())
            );
            printFields.put(
                PdfMapperConstants.Q11_REFERENCE,
                ofNullable(representativeClaimantType.getRepresentativeReference())
            );
            if (representativeClaimantType.getRepresentativePreference() != null) {
                String representativePreference = representativeClaimantType.getRepresentativePreference();
                if (EMAIL.equals(representativePreference)) {
                    printFields.put(PdfMapperConstants.Q11_CONTACT_EMAIL, Optional.of(EMAIL));
                } else if ("Post".equals(representativePreference)) {
                    printFields.put(PdfMapperConstants.Q11_CONTACT_POST, Optional.of(POST));
                } else {
                    printFields.put(PdfMapperConstants.Q11_CONTACT_POST, Optional.of(FAX));
                }
            }

            return printFields;
        }
        return new HashMap<>();
    }

    private String formatPostcode(String postcode) {
        try {
            PostCodeValidator postCodeValidator = new PostCodeValidator(postcode);

            String outward = rightPad(postCodeValidator.returnOutwardCode(), 4, ' ');
            String inward = postCodeValidator.returnInwardCode();

            return outward + inward;
        } catch (InvalidPostcodeException e) {
            log.error("Exception occurred when formatting postcode " + postcode, e);
            return postcode;
        }
    }
}
