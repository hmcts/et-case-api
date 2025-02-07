package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.ecm.common.service.pdf.PdfDecodedMultipartFile;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.RepresentedTypeRItem;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.et.common.model.ccd.types.RespondentTse;
import uk.gov.hmcts.et.common.model.ccd.types.UploadedDocumentType;
import uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse;
import uk.gov.hmcts.reform.et.syaapi.exception.NotificationException;
import uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.notification.NotificationsProperties;
import uk.gov.hmcts.reform.et.syaapi.service.utils.GenericServiceUtil;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.tika.utils.StringUtils.isBlank;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.APP_TYPE_MAP;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.CY_ABBREVIATED_MONTHS_MAP;
import static uk.gov.hmcts.et.common.model.ccd.types.citizenhub.ClaimantTse.CY_APP_TYPE_MAP;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.CASE_ID_NOT_FOUND;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.FILE_NOT_EXISTS;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.HEARING_DOCUMENTS_PATH;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ACAS_PDF1_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ACAS_PDF2_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ACAS_PDF3_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ACAS_PDF4_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ACAS_PDF5_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_CASE_ID;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_CASE_NUMBER_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_CLAIM_DESCRIPTION_FILE_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_DATEPLUS7_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ET1PDF_ENGLISH_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ET1PDF_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_ET1PDF_WELSH_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_EXUI_HEARING_DOCUMENTS_LINK;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_EXUI_LINK_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_FIRSTNAME_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_HEARING_DATE_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_LASTNAME_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_LINK_DOC_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_SHORTTEXT_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_PARAMS_SUBJECTLINE_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_SERVICE_OWNER_NAME_KEY;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SEND_EMAIL_SERVICE_OWNER_NAME_VALUE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.UK_LOCAL_DATE_PATTERN;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.UNASSIGNED_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE_PARAM;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.WELSH_LANGUAGE_PARAM_WITHOUT_FWDSLASH;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.YES;
import static uk.gov.hmcts.reform.et.syaapi.helper.NotificationsHelper.getRespondentNames;
import static uk.gov.service.notify.NotificationClient.prepareUpload;

/**
 * Holds details for sending email to user(s) provided template been created beforehand.
 */

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports", "PMD.CyclomaticComplexity", "PMD.GodClass"})
public class NotificationService {
    static final String NOT_SET = "Not set";

    private final NotificationClient notificationClient;
    private final NotificationsProperties notificationsProperties;
    private final FeatureToggleService featureToggleService;

    private final String[] typeA =
        {"strike", "amend", "non-compliance", "other", "postpone", "vary", "respondent", "publicity"};
    private final String[] typeB = {"withdraw", "change-details", "reconsider-decision", "reconsider-judgement"};
    private static final String TYPE_C = "witness";
    private static final String DONT_SEND_COPY = "No";
    public static final String HEARING_DATE_KEY = "hearingDate";
    private static final String NO_CLAIMANT_EMAIL_FOUND =
        "No claimant email found - Application response acknowledgment not being sent";
    private static final String HEARING_DATE_NOT_SET_WELSH = "Heb ei anfon";

    /**
     * Record containing core details of an email.
     *
     * @param caseData        existing case details
     * @param claimant        claimant's full name
     * @param caseNumber      ethos case reference
     * @param respondentNames concatenated respondent names
     * @param hearingDate     date of the nearest hearing
     * @param caseId          16 digit case id
     */
    record CoreEmailDetails(
        CaseData caseData,
        String claimant,
        String caseNumber,
        String respondentNames,
        String hearingDate,
        String caseId) {
    }

    /**
     * Given a caseId, initialization of trigger event to start and submit update for case.
     *
     * @param templateId  - pass gov notify template id for each use case
     * @param targetEmail - recipient target email id
     * @param parameters  - map of strings to add this to the template
     * @param reference   - reference string for email template
     * @return response from notification api
     */
    SendEmailResponse sendEmail(
        String templateId, String targetEmail, Map<String, String> parameters, String reference) {
        SendEmailResponse sendEmailResponse;
        try {
            sendEmailResponse = notificationClient.sendEmail(templateId, targetEmail, parameters, reference);
        } catch (NotificationClientException ne) {
            GenericServiceUtil.logException("Error while trying to sending notification to client",
                                            GenericServiceUtil.getStringValueFromStringMap(
                                                parameters,
                                                SEND_EMAIL_PARAMS_CASE_NUMBER_KEY
                                            ),
                                            ne.getMessage(),
                                            this.getClass().getName(), "sendEmail"
            );
            throw new NotificationException(ne);
        }
        return sendEmailResponse;
    }

    /**
     * Prepares case submission confirmation email content from user and case data & sends email to the user.
     *
     * @param caseRequest  top level non-modifiable case details
     * @param caseData     user provided data
     * @param userInfo     user details from Idam
     * @param casePdfFiles pdf files of the ET1 form according to selected language
     * @return Gov notify email format
     */
    SendEmailResponse sendSubmitCaseConfirmationEmail(CaseRequest caseRequest, CaseData caseData,
                                                      UserInfo userInfo,
                                                      List<PdfDecodedMultipartFile> casePdfFiles) {
        SendEmailResponse sendEmailResponse = null;
        if (GenericServiceUtil.hasPdfFile(casePdfFiles, 0)) {
            String firstName = GenericServiceUtil.findClaimantFirstNameByCaseDataUserInfo(caseData, userInfo);
            String lastName = GenericServiceUtil.findClaimantLastNameByCaseDataUserInfo(caseData, userInfo);
            String caseId = caseRequest.getCaseId() == null ? CASE_ID_NOT_FOUND : caseRequest.getCaseId();
            String selectedLanguage = GenericServiceUtil.findClaimantLanguage(caseData);

            boolean isWelsh = WELSH_LANGUAGE.equals(selectedLanguage);
            String emailTemplateId = isWelsh
                ? notificationsProperties.getCySubmitCaseEmailTemplateId()
                : notificationsProperties.getSubmitCaseEmailTemplateId();
            String citizenPortalLink = notificationsProperties.getCitizenPortalLink() + "%s"
                + (isWelsh ? WELSH_LANGUAGE_PARAM : "");

            byte[] et1Pdf = GenericServiceUtil.findPdfFileBySelectedLanguage(casePdfFiles, selectedLanguage);
            try {
                Map<String, Object> parameters = new ConcurrentHashMap<>();
                parameters.put(SEND_EMAIL_PARAMS_FIRSTNAME_KEY, firstName);
                parameters.put(SEND_EMAIL_PARAMS_LASTNAME_KEY, lastName);
                parameters.put(SEND_EMAIL_PARAMS_CASE_ID, caseId);
                parameters.put(SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY, String.format(citizenPortalLink, caseId));
                parameters.put(SEND_EMAIL_PARAMS_ET1PDF_LINK_KEY, prepareUpload(et1Pdf));

                String claimantEmailAddress = caseData.getClaimantType().getClaimantEmailAddress();
                sendEmailResponse = notificationClient.sendEmail(
                    emailTemplateId,
                    claimantEmailAddress,
                    parameters,
                    caseId
                );
            } catch (NotificationClientException ne) {
                GenericServiceUtil.logException("Submit case confirmation email was not sent to client.",
                                                caseData.getEthosCaseReference(), ne.getMessage(),
                                                this.getClass().getName(), "sendSubmitCaseConfirmationEmail"
                );
            }
        }
        return sendEmailResponse;
    }

    /**
     * Prepared doc upload error alert email content from user and case data then sends email to the service.
     *
     * @param caseRequest      top level non-modifiable case details
     * @param casePdfFiles     pdf copy of ET1 form content
     * @param acasCertificates pdf copy of Acas Certificates
     * @return Gov notify email format
     */
    SendEmailResponse sendDocUploadErrorEmail(CaseRequest caseRequest,
                                              List<PdfDecodedMultipartFile> casePdfFiles,
                                              List<PdfDecodedMultipartFile> acasCertificates,
                                              UploadedDocumentType claimDescriptionDocument) {
        SendEmailResponse sendEmailResponse = null;
        try {
            String caseNumber = caseRequest.getCaseId() == null ? CASE_ID_NOT_FOUND : caseRequest.getCaseId();
            Map<String, Object> parameters = new ConcurrentHashMap<>();
            parameters.put(SEND_EMAIL_SERVICE_OWNER_NAME_KEY, SEND_EMAIL_SERVICE_OWNER_NAME_VALUE);
            parameters.put(SEND_EMAIL_PARAMS_CASE_NUMBER_KEY, caseNumber);
            parameters.put(
                SEND_EMAIL_PARAMS_ET1PDF_ENGLISH_LINK_KEY,
                GenericServiceUtil.prepareUpload(casePdfFiles, 0)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ET1PDF_WELSH_LINK_KEY,
                GenericServiceUtil.prepareUpload(casePdfFiles, 1)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ACAS_PDF1_LINK_KEY,
                GenericServiceUtil.prepareUpload(acasCertificates, 0)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ACAS_PDF2_LINK_KEY,
                GenericServiceUtil.prepareUpload(acasCertificates, 1)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ACAS_PDF3_LINK_KEY,
                GenericServiceUtil.prepareUpload(acasCertificates, 2)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ACAS_PDF4_LINK_KEY,
                GenericServiceUtil.prepareUpload(acasCertificates, 3)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_ACAS_PDF5_LINK_KEY,
                GenericServiceUtil.prepareUpload(acasCertificates, 4)
            );
            parameters.put(
                SEND_EMAIL_PARAMS_CLAIM_DESCRIPTION_FILE_LINK_KEY,
                ObjectUtils.isNotEmpty(claimDescriptionDocument)
                    && StringUtils.isNotBlank(claimDescriptionDocument.getDocumentUrl())
                    ? claimDescriptionDocument.getDocumentUrl()
                    : FILE_NOT_EXISTS
            );

            String emailTemplateId = notificationsProperties.getSubmitCaseDocUploadErrorEmailTemplateId();

            // Send an alert email to the service owner
            sendEmailResponse = notificationClient.sendEmail(
                emailTemplateId,
                notificationsProperties.getEt1ServiceOwnerNotificationEmail(),
                parameters,
                caseNumber
            );

            // Send a copy alert email to ECM DTS core team
            notificationClient.sendEmail(
                emailTemplateId,
                notificationsProperties.getEt1EcmDtsCoreTeamSlackNotificationEmail(),
                parameters,
                caseNumber
            );
        } catch (NotificationClientException ne) {
            GenericServiceUtil.logException(
                "Case Documents Upload error - Failed to send document upload error message",
                caseRequest.getCaseId(), ne.getMessage(),
                this.getClass().getName(), "sendDocUploadErrorEmail"
            );
        }
        return sendEmailResponse;
    }

    /**
     * Format details of claimant request and retrieve case data, then send email.
     *
     * @param details             core details of the email
     * @param claimantApplication application request data
     * @return Gov notify email format
     */
    SendEmailResponse sendAcknowledgementEmailToClaimant(CoreEmailDetails details, ClaimantTse claimantApplication) {
        boolean isWelsh = isWelshLanguage(details.caseData());
        String hearingDate = getHearingDate(details.hearingDate(), isWelsh);
        Map<String, Object> claimantParameters = prepareEmailParameters(details, hearingDate, isWelsh);
        String emailToClaimantTemplate = getAndSetEmailTemplate(claimantApplication, hearingDate,
                                                                claimantParameters, isWelsh, false);
        return sendEmailToClaimant(details.caseData(), details.caseId(), emailToClaimantTemplate, claimantParameters);
    }

    /**
     * Format details of respondent request and retrieve case data, then send email to confirmation to respondent.
     *
     * @param details             core details of the email
     * @param respondentApplication application request data
     */
    void sendRespondentAppAcknowledgementEmailToRespondent(CoreEmailDetails details,
                                                                  RespondentTse respondentApplication) {
        CaseData caseData = details.caseData();
        // assuming respondent or respondent's representative is in the system. not both
        caseData.getRespondentCollection()
            .forEach(resp -> {
                boolean isWelsh = isWelshLanguage(resp);
                String hearingDate = getHearingDate(details.hearingDate(), isWelsh);
                Map<String, Object> respondentParameters = prepareEmailParameters(details, hearingDate, isWelsh);

                boolean isRespondentOrRep = StringUtils.isNotBlank(resp.getValue().getIdamId());
                String linkToCase = isRespondentOrRep
                    ? getRespondentPortalLink(details.caseId(), isWelsh)
                    : getRespondentRepPortalLink(details.caseId());
                respondentParameters.put(SEND_EMAIL_PARAMS_EXUI_LINK_KEY, linkToCase);

                String emailToRespondentTemplate = getAndSetEmailTemplate(respondentApplication, hearingDate,
                                           respondentParameters, isWelsh, true);
                String respondentEmailAddress = NotificationsHelper.getEmailAddressForRespondent(
                    caseData,
                    resp.getValue()
                );

                if (isNullOrEmpty(respondentEmailAddress)) {
                    log.info("Respondent does not not have an email address associated with their account");
                } else {
                    try {
                        notificationClient.sendEmail(
                            emailToRespondentTemplate,
                            respondentEmailAddress,
                            respondentParameters,
                            details.caseId()
                        );
                        log.info("Sent email to respondent");
                    } catch (NotificationClientException ne) {
                        throw new NotificationException(ne);
                    }
                }
            });
    }

    private Map<String, Object> prepareEmailParameters(CoreEmailDetails details, String hearingDate, boolean isWelsh) {
        Map<String, Object> parameters = new ConcurrentHashMap<>();
        parameters.put(HEARING_DATE_KEY, hearingDate);
        addCommonParameters(parameters, details.claimant(), details.respondentNames(), details.caseId(),
                            details.caseNumber());
        // citizenPortalLink
        String citizenPortalLink = getCitizenPortalLink(details.caseId(), isWelsh);
        parameters.put(SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY, citizenPortalLink);
        return parameters;
    }

    private boolean isWelshLanguage(CaseData caseData) {
        boolean welshFlagEnabled = featureToggleService.isWelshEnabled();
        log.info("Welsh feature flag is set to " + welshFlagEnabled);
        return welshFlagEnabled && WELSH_LANGUAGE.equals(caseData.getClaimantHearingPreference().getContactLanguage());
    }

    private boolean isWelshLanguage(RespondentSumTypeItem respondent) {
        boolean welshFlagEnabled = featureToggleService.isWelshEnabled();
        log.info("Welsh feature flag is set to " + welshFlagEnabled);
        return welshFlagEnabled && WELSH_LANGUAGE.equals(respondent.getValue().getEt3ResponseLanguagePreference());
    }

    private String getHearingDate(String hearingDate, boolean isWelsh) {
        if (NOT_SET.equals(hearingDate)) {
            return isWelsh ? HEARING_DATE_NOT_SET_WELSH : NOT_SET;
        }
        return isWelsh ? translateHearingDateToWelsh(hearingDate) : hearingDate;
    }

    private String getCitizenPortalLink(String caseId, boolean isWelsh) {
        return notificationsProperties.getCitizenPortalLink() + caseId + (isWelsh
            ? WELSH_LANGUAGE_PARAM_WITHOUT_FWDSLASH : "");
    }

    private String getRespondentPortalLink(String caseId, boolean isWelsh) {
        return notificationsProperties.getRespondentPortalLink() + caseId + (isWelsh
            ? WELSH_LANGUAGE_PARAM_WITHOUT_FWDSLASH : "");
    }

    private String getRespondentRepPortalLink(String caseId) {
        return notificationsProperties.getRespondentPortalLink() + caseId;
    }

    private SendEmailResponse sendEmailToClaimant(CaseData caseData, String caseId, String emailTemplate,
                                                  Map<String, Object> parameters) {
        try {
            String claimantEmail = caseData.getClaimantType().getClaimantEmailAddress();
            return notificationClient.sendEmail(emailTemplate, claimantEmail, parameters, caseId);
        } catch (NotificationClientException e) {
            log.error("Failed to send acknowledgment email to claimant", e);
            throw new NotificationException(e);
        }
    }

    private String translateHearingDateToWelsh(String hearingDate) {
        return CY_ABBREVIATED_MONTHS_MAP.entrySet().stream()
            .filter(entry -> hearingDate.contains(entry.getKey()))
            .findFirst()
            .map(entry -> hearingDate.replace(entry.getKey(), entry.getValue()))
            .orElse(hearingDate);
    }

    /**
     * Format details of claimant request and retrieve case data, then send email to confirmation to respondent.
     *
     * @param details             core details of the email
     * @param claimantApplication application request data
     */
    void sendAcknowledgementEmailToRespondents(CoreEmailDetails details,
                                               JSONObject documentJson,
                                               ClaimantTse claimantApplication) {
        if (TYPE_C.equals(claimantApplication.getContactApplicationType())
            || DONT_SEND_COPY.equals(claimantApplication.getCopyToOtherPartyYesOrNo())) {
            log.info("Acknowledgement email not sent to respondents for this application type");
            return;
        }

        Map<String, Object> respondentParameters = new ConcurrentHashMap<>();
        addCommonParameters(respondentParameters,
                            details.claimant(),
                            details.respondentNames(),
                            details.caseId(),
                            details.caseNumber());
        respondentParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate());
        respondentParameters.put(SEND_EMAIL_PARAMS_SHORTTEXT_KEY, APP_TYPE_MAP.get(
            claimantApplication.getContactApplicationType()));
        respondentParameters.put(SEND_EMAIL_PARAMS_DATEPLUS7_KEY,
                                 LocalDate.now().plusDays(7).format(UK_LOCAL_DATE_PATTERN));
        respondentParameters.put(SEND_EMAIL_PARAMS_LINK_DOC_KEY,
                                 Objects.requireNonNullElse(documentJson, ""));
        respondentParameters.put(SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
                                 notificationsProperties.getExuiCaseDetailsLink() + details.caseId());

        String emailToRespondentTemplate = Stream.of(typeB).anyMatch(
            appType -> Objects.equals(appType, claimantApplication.getContactApplicationType()))
            ? notificationsProperties.getRespondentTseEmailTypeBTemplateId()
            : notificationsProperties.getRespondentTseEmailTypeATemplateId();

        sendRespondentEmails(details.caseData(), details.caseId(), respondentParameters, emailToRespondentTemplate);
    }

    void sendRespondentAppAcknowledgementEmailToClaimant(CoreEmailDetails details,
                                               JSONObject documentJson,
                                               RespondentTse respondentTse) {
        if (TYPE_C.equals(respondentTse.getContactApplicationType())
            || DONT_SEND_COPY.equals(respondentTse.getCopyToOtherPartyYesOrNo())) {
            log.info("Acknowledgement email not sent to claimant for this application type");
            return;
        }
        Map<String, Object> claimantParameters = new ConcurrentHashMap<>();
        addCommonParameters(claimantParameters,
                            details.claimant(),
                            details.respondentNames(),
                            details.caseId(),
                            details.caseNumber());
        claimantParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate());
        claimantParameters.put(SEND_EMAIL_PARAMS_SHORTTEXT_KEY, APP_TYPE_MAP.get(
            respondentTse.getContactApplicationType()));
        claimantParameters.put(SEND_EMAIL_PARAMS_DATEPLUS7_KEY,
                                 LocalDate.now().plusDays(7).format(UK_LOCAL_DATE_PATTERN));
        claimantParameters.put(SEND_EMAIL_PARAMS_LINK_DOC_KEY,
                                 Objects.requireNonNullElse(documentJson, ""));
        // will have to handle claimant and claimant representative emails
        claimantParameters.put(SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY,
                                 notificationsProperties.getExuiCaseDetailsLink() + details.caseId());

        boolean isWelsh = isWelshLanguage(details.caseData());
        String emailToClaimantTemplate;
        if (Stream.of(typeB).anyMatch(appType -> Objects.equals(appType, respondentTse.getContactApplicationType()))) {
            emailToClaimantTemplate = isWelsh
                ? notificationsProperties.getCyRespondentTseTypeBClaimantAckTemplateId()
                : notificationsProperties.getRespondentTseTypeBClaimantAckTemplateId();
        } else {
            emailToClaimantTemplate = isWelsh
                ? notificationsProperties.getCyRespondentTseTypeAClaimantAckTemplateId()
                : notificationsProperties.getRespondentTseTypeAClaimantAckTemplateId();
        }
        sendEmailToClaimant(details.caseData(), details.caseId(), emailToClaimantTemplate, claimantParameters);
    }

    /**
     * Format details of claimant request and retrieve case data, then send email to confirmation to tribunal.
     *
     * @param details         core details of the email
     * @param applicationType type of application
     */
    void sendAcknowledgementEmailToTribunal(CoreEmailDetails details, String applicationType) {
        Map<String, Object> tribunalParameters = new ConcurrentHashMap<>();

        addCommonParameters(
            tribunalParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber,
            String.join(" ", details.caseNumber, APP_TYPE_MAP.get(applicationType))
        );
        tribunalParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            details.hearingDate
        );
        tribunalParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + details.caseId
        );

        String managingOffice = details.caseData.getManagingOffice();
        if (managingOffice.equals(UNASSIGNED_OFFICE) || isNullOrEmpty(managingOffice)) {
            log.info("Could not send email as no office has been assigned");
            return;
        }

        try {
            notificationClient.sendEmail(
                notificationsProperties.getTribunalAcknowledgementTemplateId(),
                details.caseData.getTribunalCorrespondenceEmail(),
                tribunalParameters,
                details.caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    /**
     * Format details of claimant request and retrieve case data, then send email to confirmation to tribunal.
     *
     * @param details                      core details of the email
     * @param applicationType              type of application
     * @param isRespondingToRequestOrOrder indicates whether the reply is to a tribunal order or not
     */
    void sendResponseEmailToTribunal(CoreEmailDetails details, String applicationType,
                                     boolean isRespondingToRequestOrOrder) {

        Map<String, Object> tribunalParameters = new ConcurrentHashMap<>();
        addCommonParameters(
            tribunalParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber,
            String.join(" ", details.caseNumber, applicationType),
            applicationType
        );

        tribunalParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            details.hearingDate
        );
        tribunalParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + details.caseId
        );

        String emailTemplate = isRespondingToRequestOrOrder
            ? notificationsProperties.getTseTribunalResponseToRequestTemplateId()
            : notificationsProperties.getTseTribunalResponseTemplateId();

        sendTribunalEmail(
            details.caseData,
            details.caseId,
            tribunalParameters,
            emailTemplate
        );
    }

    /**
     * Send acknowledgment email to the claimant when they are responding to
     * an application (type A/B) made by the Respondent.
     *
     * @param details                      core details of the email
     * @param applicationType              type of application
     * @param copyToOtherParty             whether to notify other party
     * @param isRespondingToRequestOrOrder indicates whether the reply is to a tribunal order or not
     */
    void sendResponseEmailToClaimant(CoreEmailDetails details,
                                               String applicationType,
                                               String copyToOtherParty,
                                               boolean isRespondingToRequestOrOrder) {
        if (TYPE_C.equals(applicationType)) {
            log.info("Type C application -  Claimant is only notified of "
                         + "Type A/B application responses, email not being sent");
            return;
        }
        String claimantEmailAddress = details.caseData.getClaimantType().getClaimantEmailAddress();
        if (isBlank(claimantEmailAddress)) {
            log.info(NO_CLAIMANT_EMAIL_FOUND);
            return;
        }
        Map<String, Object> claimantParameters = prepareResponseEmailCommonParameters(details, applicationType);

        claimantParameters.put(
            SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY,
            notificationsProperties.getCitizenPortalLink() + details.caseId
        );

        String emailToClaimantTemplate;
        if (isRespondingToRequestOrOrder) {
            emailToClaimantTemplate = DONT_SEND_COPY.equals(copyToOtherParty)
                ? notificationsProperties.getTseClaimantResponseToRequestNoTemplateId()
                : notificationsProperties.getTseClaimantResponseToRequestYesTemplateId();
        } else {
            emailToClaimantTemplate = DONT_SEND_COPY.equals(copyToOtherParty)
                ? notificationsProperties.getTseClaimantResponseNoTemplateId()
                : notificationsProperties.getTseClaimantResponseYesTemplateId();
        }

        try {
            notificationClient.sendEmail(
                emailToClaimantTemplate,
                claimantEmailAddress,
                claimantParameters,
                details.caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    /**
     * Send acknowledgment email to the respondent when they are responding to
     * an application (type A/B) made by the Claimant.
     *
     * @param details                      core details of the email
     * @param applicationType              type of application
     * @param copyToOtherParty             whether to notify other party
     * @param isRespondingToRequestOrOrder indicates whether the reply is to a tribunal order or not
     */
    void sendRespondentResponseEmailToRespondent(CoreEmailDetails details, String applicationType,
                                                 String copyToOtherParty, boolean isRespondingToRequestOrOrder) {
        if (TYPE_C.equals(applicationType)) {
            log.info("Type C application -  Respondent is only notified of "
                         + "Type A/B application responses, email not being sent");
            return;
        }

        Map<String, Object> emailParameters = prepareResponseEmailCommonParameters(details, applicationType);
        String emailTemplate = getRespondentResponseEmailTemplate(isRespondingToRequestOrOrder,
                                                                  copyToOtherParty);

        CaseData caseData = details.caseData();
        caseData.getRespondentCollection()
            .forEach(resp -> {
                boolean isRespondentOrRep = StringUtils.isNotBlank(resp.getValue().getIdamId());
                String linkToCase = isRespondentOrRep
                    ? getRespondentPortalLink(details.caseId(), false)
                    : getRespondentRepPortalLink(details.caseId());
                emailParameters.put(SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY, linkToCase);

                String respondentEmailAddress = NotificationsHelper.getEmailAddressForRespondent(
                    caseData,
                    resp.getValue()
                );

                if (isNullOrEmpty(respondentEmailAddress)) {
                    log.info("Respondent does not not have an email address associated with their account");
                } else {
                    try {
                        notificationClient.sendEmail(
                            emailTemplate,
                            respondentEmailAddress,
                            emailParameters,
                            details.caseId()
                        );
                        log.info("Sent email to respondent");
                    } catch (NotificationClientException ne) {
                        throw new NotificationException(ne);
                    }
                }
            });
    }

    private Map<String, Object> prepareResponseEmailCommonParameters(CoreEmailDetails details,
                                                                     String applicationType) {
        Map<String, Object> emailParameters = new ConcurrentHashMap<>();

        addCommonParameters(
            emailParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber,
            String.join(" ", details.caseNumber, applicationType),
            applicationType
        );
        emailParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate);

        return emailParameters;
    }

    private String getRespondentResponseEmailTemplate(boolean isRespondingToRequestOrOrder, String copyToOtherParty) {
        String emailTemplate;
        if (isRespondingToRequestOrOrder) {
            emailTemplate = DONT_SEND_COPY.equals(copyToOtherParty)
                ? notificationsProperties.getTseClaimantResponseToRequestNoTemplateId()
                : notificationsProperties.getTseClaimantResponseToRequestYesTemplateId();
        } else {
            emailTemplate = DONT_SEND_COPY.equals(copyToOtherParty)
                ? notificationsProperties.getTseClaimantResponseNoTemplateId()
                : notificationsProperties.getTseClaimantResponseYesTemplateId();
        }

        return emailTemplate;
    }

    /**
     * Send acknowledgment email to the respondent when they are responding to
     * an application (type A/B) made by the Respondent.
     *
     * @param details          core details of the email
     * @param applicationType  type of application
     * @param copyToOtherParty should copy response to other party
     */
    void sendResponseEmailToRespondent(CoreEmailDetails details, String applicationType, String copyToOtherParty) {
        if (TYPE_C.equals(applicationType) || DONT_SEND_COPY.equals(copyToOtherParty)) {
            log.info("Acknowledgement email not sent to respondents for this application type");
            return;
        }
        Map<String, Object> respondentParameters = new ConcurrentHashMap<>();

        addCommonParameters(
            respondentParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber,
            String.join(" ", details.caseNumber, applicationType),
            applicationType
        );
        respondentParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate);
        respondentParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + details.caseId
        );

        String emailToRespondentTemplate = notificationsProperties.getTseRespondentResponseTemplateId();

        sendRespondentEmails(details.caseData, details.caseId, respondentParameters, emailToRespondentTemplate);
    }

    /**
     * Email respondent when claimant responds to a request for info from the tribunal on a TSE application.
     *
     * @param caseData   existing case details
     * @param caseNumber ethos case reference
     * @param caseId     16 digit case id
     */
    void sendReplyEmailToRespondent(
        CaseData caseData,
        String caseNumber,
        String caseId,
        String copyToOtherParty
    ) {
        if (DONT_SEND_COPY.equals(copyToOtherParty)) {
            log.info("Answered no for Rule 92, not sending email to respondents");
            return;
        }

        Map<String, Object> respondentParameters = new ConcurrentHashMap<>();
        respondentParameters.put(SEND_EMAIL_PARAMS_CASE_NUMBER_KEY, caseNumber);
        respondentParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + caseId
        );

        String emailToRespondentTemplate = notificationsProperties.getTseReplyToTribunalToRespondentTemplateId();

        sendRespondentEmails(caseData, caseId, respondentParameters, emailToRespondentTemplate);
    }

    /**
     * Email claimant when respondent responds to a request for info from the tribunal on a TSE application.
     *
     * @param caseData   existing case details
     * @param caseNumber ethos case reference
     * @param caseId     16 digit case id
     */
    void sendReplyEmailToClaimant(
        CaseData caseData,
        String caseNumber,
        String caseId,
        String copyToOtherParty
    ) {
        if (DONT_SEND_COPY.equals(copyToOtherParty)) {
            log.info("Answered no for Rule 92, not sending email to claimant");
            return;
        }

        String claimantEmailAddress = caseData.getClaimantType().getClaimantEmailAddress();
        if (isBlank(claimantEmailAddress)) {
            log.info(NO_CLAIMANT_EMAIL_FOUND);
            return;
        }

        Map<String, Object> claimantParameters = new ConcurrentHashMap<>();
        claimantParameters.put(SEND_EMAIL_PARAMS_CASE_NUMBER_KEY, caseNumber);
        claimantParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getCitizenPortalLink() + caseId
        );

        String emailTemplate = notificationsProperties.getTseReplyToTribunalToRespondentTemplateId();

        try {
            notificationClient.sendEmail(
                emailTemplate,
                claimantEmailAddress,
                claimantParameters,
                caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    /**
     * Send acknowledgment email to the respondent when they are responding to
     * an application (type A/B) made by the Respondent.
     *
     * @param details          core details of the email
     * @param applicationType  type of application
     * @param copyToOtherParty should copy response to other party
     */
    void sendRespondentResponseEmailToClaimant(CoreEmailDetails details, String applicationType,
                                               String copyToOtherParty) {
        if (TYPE_C.equals(applicationType) || DONT_SEND_COPY.equals(copyToOtherParty)) {
            log.info("Acknowledgement email not sent to claimants for this application type");
            return;
        }

        String claimantEmailAddress = details.caseData.getClaimantType().getClaimantEmailAddress();
        if (isBlank(claimantEmailAddress)) {
            log.info(NO_CLAIMANT_EMAIL_FOUND);
            return;
        }

        Map<String, Object> claimantParameters = new ConcurrentHashMap<>();

        addCommonParameters(
            claimantParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber,
            String.join(" ", details.caseNumber, applicationType),
            applicationType
        );
        claimantParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate);
        claimantParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getCitizenPortalLink() + details.caseId
        );

        String emailTemplate = notificationsProperties.getTseRespondentResponseTemplateId();

        try {
            notificationClient.sendEmail(
                emailTemplate,
                claimantEmailAddress,
                claimantParameters,
                details.caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    void sendResponseNotificationEmailToTribunal(CaseData caseData, String caseId) {
        Map<String, Object> tribunalParameters = new ConcurrentHashMap<>();
        addCommonParameters(tribunalParameters, caseData, caseId);

        tribunalParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            NotificationsHelper.getNearestHearingToReferral(caseData, NOT_SET)
        );

        tribunalParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + caseId
        );

        sendTribunalEmail(
            caseData,
            caseId,
            tribunalParameters,
            notificationsProperties.getPseTribunalResponseTemplateId()
        );
    }

    void sendResponseNotificationEmailToRespondent(
        CaseData caseData,
        String caseId,
        String copyToOtherParty
    ) {

        if (DONT_SEND_COPY.equals(copyToOtherParty) || copyToOtherParty == null) {
            log.info("Acknowledgement email not sent to respondents");
            return;
        }

        Map<String, Object> respondentParameters = new ConcurrentHashMap<>();
        addCommonParameters(respondentParameters, caseData, caseId);

        respondentParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            NotificationsHelper.getNearestHearingToReferral(caseData, NOT_SET)
        );
        respondentParameters.put(
            SEND_EMAIL_PARAMS_EXUI_LINK_KEY,
            notificationsProperties.getExuiCaseDetailsLink() + caseId
        );

        sendRespondentEmails(caseData, caseId, respondentParameters,
                             notificationsProperties.getPseRespondentResponseTemplateId()
        );
    }

    void sendResponseNotificationEmailToClaimant(
        CaseData caseData,
        String caseId,
        String copyToOtherParty
    ) {

        if (isBlank(caseData.getClaimantType().getClaimantEmailAddress())) {
            log.info(NO_CLAIMANT_EMAIL_FOUND);
            return;
        }

        String emailToClaimantTemplate = DONT_SEND_COPY.equals(copyToOtherParty)
            ? notificationsProperties.getPseClaimantResponseNoTemplateId()
            : notificationsProperties.getPseClaimantResponseYesTemplateId();

        Map<String, Object> claimantParameters = new ConcurrentHashMap<>();
        addCommonParameters(claimantParameters, caseData, caseId);

        claimantParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            NotificationsHelper.getNearestHearingToReferral(caseData, NOT_SET)
        );
        claimantParameters.put(
            SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY,
            notificationsProperties.getCitizenPortalLink() + caseId
        );

        try {
            notificationClient.sendEmail(
                emailToClaimantTemplate,
                caseData.getClaimantType().getClaimantEmailAddress(),
                claimantParameters,
                caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    void sendStoredEmailToClaimant(CoreEmailDetails details, String shortText) {
        sendStoreConfirmationEmail(
            notificationsProperties.getClaimantTseEmailStoredTemplateId(),
            details,
            shortText
        );
    }

    void sendSubmitStoredEmailToClaimant(CoreEmailDetails details, String shortText) {
        sendStoreConfirmationEmail(
            notificationsProperties.getClaimantTseEmailSubmitStoredTemplateId(),
            details,
            shortText
        );
    }

    CoreEmailDetails formatCoreEmailDetails(CaseData caseData, String caseId) {
        return new CoreEmailDetails(
            caseData,
            caseData.getClaimantIndType().getClaimantFirstNames() + " "
                + caseData.getClaimantIndType().getClaimantLastName(),
            caseData.getEthosCaseReference(),
            getRespondentNames(caseData),
            NotificationsHelper.getNearestHearingToReferral(caseData, NOT_SET),
            caseId
        );
    }

    private void sendTribunalEmail(CaseData caseData,
                                   String caseId,
                                   Map<String, Object> tribunalParameters,
                                   String templateId) {
        String managingOffice = caseData.getManagingOffice();
        if (UNASSIGNED_OFFICE.equals(managingOffice) || isNullOrEmpty(managingOffice)) {
            log.info("Could not send email as no office has been assigned");
            return;
        }

        String tribunalEmail = caseData.getTribunalCorrespondenceEmail();
        if (isNullOrEmpty(tribunalEmail)) {
            log.info("Could not send email. No email found");
            return;
        }

        try {
            notificationClient.sendEmail(
                templateId,
                caseData.getTribunalCorrespondenceEmail(),
                tribunalParameters,
                caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    private void sendRespondentEmails(CaseData caseData, String caseId, Map<String, Object> respondentParameters,
                                      String emailToRespondentTemplate) {
        if (!isSystemUser(caseData.getRepCollection())) {
            log.info("No email sent to respondents as no representative is using the system");
            return;
        }

        caseData.getRespondentCollection()
            .forEach(resp -> {
                String respondentEmailAddress = NotificationsHelper.getEmailAddressForRespondent(
                    caseData,
                    resp.getValue()
                );
                if (isNullOrEmpty(respondentEmailAddress)) {
                    log.info("Respondent does not not have an email address associated with their account");
                } else {
                    try {
                        notificationClient.sendEmail(
                            emailToRespondentTemplate,
                            respondentEmailAddress,
                            respondentParameters,
                            caseId
                        );
                        log.info("Sent email to respondent");
                    } catch (NotificationClientException ne) {
                        throw new NotificationException(ne);
                    }
                }
            });
    }

    private boolean isSystemUser(List<RepresentedTypeRItem> repCollection) {
        return !CollectionUtils.isEmpty(repCollection)
            && repCollection.stream().anyMatch(rep -> YES.equals(rep.getValue().getMyHmctsYesNo()));
    }

    private void sendStoreConfirmationEmail(String emailToClaimantTemplate, CoreEmailDetails details,
                                            String shortText) {
        String claimantEmailAddress = details.caseData.getClaimantType().getClaimantEmailAddress();
        if (isBlank(claimantEmailAddress)) {
            log.info(NO_CLAIMANT_EMAIL_FOUND);
            return;
        }

        Map<String, Object> claimantParameters = new ConcurrentHashMap<>();

        addCommonParameters(
            claimantParameters,
            details.claimant,
            details.respondentNames,
            details.caseId,
            details.caseNumber
        );
        claimantParameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, details.hearingDate);
        claimantParameters.put(SEND_EMAIL_PARAMS_SHORTTEXT_KEY, shortText);
        claimantParameters.put(
            SEND_EMAIL_PARAMS_CITIZEN_PORTAL_LINK_KEY,
            notificationsProperties.getCitizenPortalLink() + details.caseId
        );

        try {
            notificationClient.sendEmail(
                emailToClaimantTemplate,
                claimantEmailAddress,
                claimantParameters,
                details.caseId
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }

    /**
     * Sends email to all respondents/legal reps plus the tribunal when the claimant submits a bundle.
     * Content of email is the same therefore the same template is used.
     *
     * @param caseData  existing case data
     * @param caseId    id of case
     * @param hearingId id of hearing
     */
    public void sendBundlesEmails(CaseData caseData,
                                  String caseId,
                                  String hearingId) {

        Map<String, Object> emailParameters = new ConcurrentHashMap<>();
        addCommonParameters(emailParameters, caseData, caseId);

        String hearingDate = NotificationsHelper.getEarliestDateForHearing(
            caseData.getHearingCollection(),
            hearingId
        );
        emailParameters.put(
            SEND_EMAIL_PARAMS_HEARING_DATE_KEY,
            hearingDate
        );

        emailParameters.put(
            SEND_EMAIL_PARAMS_EXUI_HEARING_DOCUMENTS_LINK,
            notificationsProperties.getExuiCaseDetailsLink() + caseId + HEARING_DOCUMENTS_PATH
        );

        sendTribunalEmail(
            caseData,
            caseId,
            emailParameters,
            notificationsProperties.getBundlesClaimantSubmittedNotificationTemplateId()
        );

        sendRespondentEmails(
            caseData,
            caseId,
            emailParameters,
            notificationsProperties.getBundlesClaimantSubmittedNotificationTemplateId()
        );
    }

    private static void addCommonParameters(Map<String, Object> parameters, String claimant, String respondentNames,
                                            String caseId, String caseNumber) {
        parameters.put("claimant", claimant);
        parameters.put("respondentNames", respondentNames);
        parameters.put(SEND_EMAIL_PARAMS_CASE_ID, caseId);
        parameters.put(SEND_EMAIL_PARAMS_CASE_NUMBER_KEY, caseNumber);
    }

    private static void addCommonParameters(Map<String, Object> parameters, String claimant, String respondentNames,
                                            String caseId, String caseNumber, String subjectLine) {
        addCommonParameters(parameters, claimant, respondentNames, caseId, caseNumber);
        parameters.put(SEND_EMAIL_PARAMS_SUBJECTLINE_KEY, subjectLine);
    }

    private static void addCommonParameters(Map<String, Object> parameters, String claimant, String respondentNames,
                                            String caseId, String caseNumber, String subjectLine, String shortText) {
        addCommonParameters(parameters, claimant, respondentNames, caseId, caseNumber, subjectLine);
        parameters.put(SEND_EMAIL_PARAMS_SHORTTEXT_KEY, shortText);
    }

    private static void addCommonParameters(Map<String, Object> parameters, CaseData caseData, String caseId) {
        String claimant = String.join(
            " ",
            caseData.getClaimantIndType().getClaimantFirstNames(),
            caseData.getClaimantIndType().getClaimantLastName()
        );
        String caseNumber = caseData.getEthosCaseReference();
        String respondentNames = getRespondentNames(caseData);

        addCommonParameters(parameters, claimant, respondentNames, caseId, caseNumber, caseNumber);
    }

    String getAndSetEmailTemplate(Object application, String hearingDate, Map<String, Object> parameters,
                                          boolean isWelsh, boolean isRespondent) {
        parameters.put(SEND_EMAIL_PARAMS_HEARING_DATE_KEY, hearingDate);
        Map<String, String> selectedMap = isWelsh ? CY_APP_TYPE_MAP : APP_TYPE_MAP;
        String applicationType = getApplicationType(application, isRespondent);
        String shortText = isRespondent ? applicationType : selectedMap.get(applicationType);
        parameters.put(SEND_EMAIL_PARAMS_SHORTTEXT_KEY, shortText);

        String copyToOtherParty = getCopyToOtherParty(application, isRespondent);

        return getTemplateId(applicationType, copyToOtherParty, isWelsh, isRespondent);
    }

    private String getApplicationType(Object application, boolean isRespondent) {
        return isRespondent
            ? ((RespondentTse) application).getContactApplicationType()
            : ((ClaimantTse) application).getContactApplicationType();
    }

    private String getCopyToOtherParty(Object application, boolean isRespondent) {
        return isRespondent
            ? ((RespondentTse) application).getCopyToOtherPartyYesOrNo()
            : ((ClaimantTse) application).getCopyToOtherPartyYesOrNo();
    }

    private String getTemplateId(String applicationType, String copyToOtherParty,
                                 boolean isWelsh, boolean isRespondent) {
        if (TYPE_C.equals(applicationType)) {
            return getTypeCTemplateId(isWelsh, isRespondent);
        }

        if (DONT_SEND_COPY.equals(copyToOtherParty)) {
            return getNoCopyTemplateId(isWelsh, isRespondent);
        }

        if (Arrays.asList(typeB).contains(applicationType)) {
            return getTypeBTemplateId(isWelsh, isRespondent);
        }

        return getTypeATemplateId(isWelsh, isRespondent);
    }

    private String getTypeCTemplateId(boolean isWelsh, boolean isRespondent) {
        if (isWelsh) {
            return isRespondent
                ? notificationsProperties.getCyRespondentTseTypeCRespAckTemplateId()
                : notificationsProperties.getCyClaimantTseEmailTypeCTemplateId();
        } else {
            return isRespondent
                ? notificationsProperties.getRespondentTseTypeCRespAckTemplateId()
                : notificationsProperties.getClaimantTseEmailTypeCTemplateId();
        }
    }

    private String getNoCopyTemplateId(boolean isWelsh, boolean isRespondent) {
        if (isWelsh) {
            return isRespondent
                ? notificationsProperties.getCyRespondentTseNoRespAckTemplateId()
                : notificationsProperties.getCyClaimantTseEmailNoTemplateId();
        } else {
            return isRespondent
                ? notificationsProperties.getRespondentTseNoRespAckTemplateId()
                : notificationsProperties.getClaimantTseEmailNoTemplateId();
        }
    }

    private String getTypeBTemplateId(boolean isWelsh, boolean isRespondent) {
        if (isWelsh) {
            return isRespondent
                ? notificationsProperties.getCyRespondentTseTypeBRespAckTemplateId()
                : notificationsProperties.getCyClaimantTseEmailTypeBTemplateId();
        } else {
            return isRespondent
                ? notificationsProperties.getRespondentTseTypeBRespAckTemplateId()
                : notificationsProperties.getClaimantTseEmailTypeBTemplateId();
        }
    }

    private String getTypeATemplateId(boolean isWelsh, boolean isRespondent) {
        if (isWelsh) {
            return isRespondent
                ? notificationsProperties.getCyRespondentTseTypeARespAckTemplateId()
                : notificationsProperties.getCyClaimantTseEmailTypeATemplateId();
        } else {
            return isRespondent
                ? notificationsProperties.getRespondentTseTypeARespAckTemplateId()
                : notificationsProperties.getClaimantTseEmailTypeATemplateId();
        }
    }

    public void sendEt3ConfirmationEmail(String email, CaseData caseData, String caseId) {
        Map<String, Object> parameters = new ConcurrentHashMap<>();
        parameters.put(SEND_EMAIL_PARAMS_CASE_NUMBER_KEY, caseData.getEthosCaseReference());
        parameters.put("claimant", caseData.getClaimant());
        parameters.put("list_of_respondents", getRespondentNames(caseData));
        parameters.put("linkToPortal",
                       notificationsProperties.getRespondentPortalLink() + "case-details/" + caseId);

        try {
            notificationClient.sendEmail(
                notificationsProperties.getEt3SubmissionConfirmationTemplateId(),
                email,
                parameters,
                UUID.randomUUID().toString()
            );
        } catch (NotificationClientException ne) {
            throw new NotificationException(ne);
        }
    }
}
