package uk.gov.hmcts.reform.et.syaapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.dwp.regex.InvalidPostcodeException;
import uk.gov.hmcts.ecm.common.model.helper.TribunalOffice;
import uk.gov.hmcts.et.common.model.ccd.CaseData;
import uk.gov.hmcts.et.common.model.ccd.Et1CaseData;
import uk.gov.hmcts.et.common.model.ccd.items.DocumentTypeItem;
import uk.gov.hmcts.et.common.model.ccd.items.RespondentSumTypeItem;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent;
import uk.gov.hmcts.reform.et.syaapi.helper.CaseDetailsConverter;
import uk.gov.hmcts.reform.et.syaapi.helper.EmployeeObjectMapper;
import uk.gov.hmcts.reform.et.syaapi.models.AcasCertificate;
import uk.gov.hmcts.reform.et.syaapi.models.CaseDocument;
import uk.gov.hmcts.reform.et.syaapi.models.CaseRequest;
import uk.gov.hmcts.reform.et.syaapi.notification.NotificationsProperties;
import uk.gov.hmcts.reform.et.syaapi.service.pdf.PdfService;
import uk.gov.hmcts.reform.et.syaapi.service.pdf.PdfServiceException;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static uk.gov.hmcts.ecm.common.model.helper.TribunalOffice.getCaseTypeId;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.CASE_FIELD_MANAGING_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.DEFAULT_TRIBUNAL_OFFICE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.ENGLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.JURISDICTION_ID;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.SCOTLAND_CASE_TYPE;
import static uk.gov.hmcts.reform.et.syaapi.enums.CaseEvent.INITIATE_CASE_DRAFT;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ExcessiveImports"})
public class CaseService {

    private final AuthTokenGenerator authTokenGenerator;

    private final CoreCaseDataApi ccdApiClient;

    private final IdamClient idamClient;

    private final PostcodeToOfficeService postcodeToOfficeService;

    private final PdfService pdfService;

    private final AcasService acasService;

    private final CaseDocumentService caseDocumentService;

    private final NotificationService notificationService;

    /**
     * Given a case id in the case request, this will retrieve the correct {@link CaseDetails}.
     *
     * @param caseRequest contains case id get the {@link CaseDetails} for
     * @return the associated {@link CaseDetails} for the ID provided
     */
    @Retryable({FeignException.class, RuntimeException.class})
    public CaseDetails getUserCase(String authorization, CaseRequest caseRequest) {
        return ccdApiClient.getCase(authorization, authTokenGenerator.generate(), caseRequest.getCaseId());
    }

    /**
     * Given a user derived from the authorisation token in the request,
     * this will get all cases {@link CaseDetails} for that user.
     *
     * @param authorization is used to get the {@link UserDetails} for the request
     * @return the associated {@link CaseDetails} for the ID provided
     */
    @Retryable({FeignException.class, RuntimeException.class})
    public List<CaseDetails> getAllUserCases(String authorization) {
        UserDetails userDetails = idamClient.getUserDetails(authorization);

        List<CaseDetails> scotlandCases = ccdApiClient.searchForCitizen(
            authorization, authTokenGenerator.generate(),
            userDetails.getId(), JURISDICTION_ID, SCOTLAND_CASE_TYPE, Collections.emptyMap());

        List<CaseDetails> englandCases = ccdApiClient.searchForCitizen(
            authorization, authTokenGenerator.generate(),
            userDetails.getId(), JURISDICTION_ID, ENGLAND_CASE_TYPE, Collections.emptyMap());

        return Stream.of(scotlandCases, englandCases).flatMap(Collection::stream).collect(toList());
    }

    /**
     * Given a caseID, this will retrieve the correct {@link CaseDetails}.
     *
     * @param authorization is used to find the {@link UserDetails} for request
     * @param caseRequest  case data for request
     * @return the associated {@link CaseDetails} if the case is created
     */
    @Retryable({FeignException.class, RuntimeException.class})
    public CaseDetails createCase(String authorization,
                                  CaseRequest caseRequest) {
        String s2sToken = authTokenGenerator.generate();
        String userId = idamClient.getUserDetails(authorization).getId();
        String caseType = getCaseType(getTribunalOfficeFromPostCode(caseRequest.getPostCode()));
        String eventTypeName = INITIATE_CASE_DRAFT.name();

        StartEventResponse ccdCase = ccdApiClient.startForCitizen(
            authorization,
            s2sToken,
            userId,
            JURISDICTION_ID,
            caseType,
            eventTypeName
        );

        Et1CaseData data = new EmployeeObjectMapper().getEmploymentCaseData(caseRequest.getCaseData());
        CaseDataContent caseDataContent = CaseDataContent.builder()
            .event(Event.builder().id(eventTypeName).build())
            .eventToken(ccdCase.getToken())
            .data(data)
            .build();

        return ccdApiClient.submitForCitizen(
            authorization,
            s2sToken,
            userId,
            JURISDICTION_ID,
            caseType,
            true,
            caseDataContent
        );
    }

    /**
     * Given a tribunal office, this will retrieve the correct case type id.
     *
     * @param office is used to get office name to find case type id
     * @return the associated case type id if the case type id is found by given office name
     */
    private String getCaseType(TribunalOffice office) {
        return getCaseTypeId(office.getOfficeName());
    }

    /**
     * Given a post code, this will retrieve the matching Tribunal Office. If no Tribunal Office found
     * returns the Default one which is LONDON_SOUTH
     *
     * @param postCode is used to find the closest Tribunal Office
     * @return the associated Tribunal Office if any found by the given postcode. If not returns default one
     */
    private TribunalOffice getTribunalOfficeFromPostCode(String postCode) {
        try {
            return postcodeToOfficeService.getTribunalOfficeFromPostcode(postCode)
                .orElse(DEFAULT_TRIBUNAL_OFFICE);
        } catch (InvalidPostcodeException e) {
            log.info("Failed to find tribunal office : {} ", e.getMessage());
            return DEFAULT_TRIBUNAL_OFFICE;
        }
    }

    private CaseData convertCaseRequestToCaseDataWithTribunalOffice(CaseRequest caseRequest) {
        caseRequest.getCaseData().put(CASE_FIELD_MANAGING_OFFICE,
                                      getTribunalOfficeFromPostCode(caseRequest.getPostCode()).getOfficeName());
        return EmployeeObjectMapper.mapCaseRequestToCaseData(caseRequest.getCaseData());
    }

    public CaseDetails updateCase(String authorization,
                                  CaseRequest caseRequest) {
        return triggerEvent(authorization, caseRequest.getCaseId(), CaseEvent.UPDATE_CASE_DRAFT,
                            caseRequest.getCaseTypeId(), caseRequest.getCaseData());
    }

    /**
     * Given Case Request, triggers submit case events for the case. Before submitting case events
     * sets managing office (tribunal office), created PDF file for the case and saves PDF file.
     *
     * @param authorization is used to seek the {UserDetails} for request
     * @param caseRequest is used to provide the caseId, caseTypeId and {@link CaseData} in JSON Format
     * @return the associated {@link CaseData} if the case is submitted
     */
    public CaseDetails submitCase(String authorization,
                                  CaseRequest caseRequest)
        throws PdfServiceException, CaseDocumentException, AcasException, InvalidAcasNumbersException {

        CaseData caseData = convertCaseRequestToCaseDataWithTribunalOffice(caseRequest);
        DocumentTypeItem documentTypeItem =
            caseDocumentService.uploadPdfFile(authorization, caseRequest.getCaseTypeId(),
                           pdfService.convertCaseDataToPdfDecodedMultipartFile(caseData),
                           caseData.getEcmCaseType());

        List<AcasCertificate> acasCertificateList = getCertificatesFromCase(caseData);
        log.info(String.valueOf(acasCertificateList));

        CaseDetails caseDetails = triggerEvent(authorization, caseRequest.getCaseId(), CaseEvent.SUBMIT_CASE_DRAFT,
                                               caseRequest.getCaseTypeId(), caseRequest.getCaseData());
        notificationService
            .sendSubmitCaseConfirmationEmail(new NotificationsProperties().getSampleEmailTemplateId(),
                                             caseData.getClaimantType().getClaimantEmailAddress(),
                                             caseData.getEcmCaseType(),
                                             caseData.getClaimantIndType().claimantFullName(),
                                             caseData.getClaimantIndType().getClaimantLastName(),
                                             documentTypeItem.getValue().getUploadedDocument().getDocumentUrl());
        caseDetails.getData().put("documentCollection", documentTypeItem);
        return caseDetails;
    }

    /**
     * Given a caseId, triggers update events for the case.
     *
     * @param authorization is used to seek the {@link UserDetails} for request
     * @param caseId used to retrieve get case details
     * @param caseType is used to determine if the case is for ET_EnglandWales or ET_Scotland
     * @param eventName is used to determine INITIATE_CASE_DRAFT or UPDATE_CASE_DRAFT
     * @param caseData is used to provide the {@link Et1CaseData} in json format
     * @return the associated {@link CaseData} if the case is updated
     */
    public CaseDetails triggerEvent(String authorization, String caseId, String caseType,
                                 CaseEvent eventName, Map<String, Object> caseData) {
        return triggerEvent(authorization, caseId, eventName, caseType, caseData);
    }

    /**
     * Given a caseId, initialization of trigger event to start and submit update for case.
     *
     * @param authorization is used to seek the {@link UserDetails} for request
     * @param caseId used to retrieve get case details
     * @param caseType is used to determine if the case is for ET_EnglandWales or ET_Scotland
     * @param eventName is used to determine INITIATE_CASE_DRAFT or UPDATE_CASE_DRAFT
     * @param caseData is used to provide the {@link Et1CaseData} in json format
     * @return the associated {@link CaseData} if the case is updated
     */
    public CaseDetails triggerEvent(String authorization, String caseId, CaseEvent eventName,
                                 String caseType, Map<String, Object> caseData) {
        ObjectMapper objectMapper = new ObjectMapper();
        CaseDetailsConverter caseDetailsConverter = new CaseDetailsConverter(objectMapper);
        EmployeeObjectMapper employeeObjectMapper = new EmployeeObjectMapper();
        StartEventResponse startEventResponse = startUpdate(authorization, caseId, caseType, eventName);
        return submitUpdate(authorization, caseId,
                            caseDetailsConverter.caseDataContent(startEventResponse,
                            employeeObjectMapper.getEmploymentCaseData(caseData)),
                            caseType);
    }

    /**
     * Given a caseId, start update for the case.
     *
     * @param authorization is used to seek the {@link UserDetails} for request
     * @param caseId used to retrieve get case details
     * @param caseType is used to determine if the case is for ET_EnglandWales or ET_Scotland
     * @param eventName is used to determine INITIATE_CASE_DRAFT or UPDATE_CASE_DRAFT
     * @return startEventResponse associated case details updated
     */
    public StartEventResponse startUpdate(String authorization, String caseId,
                                          String caseType, CaseEvent eventName) {
        String s2sToken = authTokenGenerator.generate();
        UserDetails userDetails = idamClient.getUserDetails(authorization);

        return ccdApiClient.startEventForCitizen(
            authorization,
            s2sToken,
            userDetails.getId(),
            JURISDICTION_ID,
            caseType,
            caseId,
            eventName.name()
        );
    }

    /**
     * Given a caseId, submit update for the case.
     *
     * @param authorization is used to seek the {@link UserDetails} for request
     * @param caseId used to retrieve get case details
     * @param caseDataContent provides overall content of the case
     * @param caseType is used to determine if the case is for ET_EnglandWales or ET_Scotland
     */
    public CaseDetails submitUpdate(String authorization, String caseId,
                                    CaseDataContent caseDataContent, String caseType) {
        UserDetails userDetails = idamClient.getUserDetails(authorization);
        String s2sToken = authTokenGenerator.generate();

        return ccdApiClient.submitEventForCitizen(
            authorization,
            s2sToken,
            userDetails.getId(),
            JURISDICTION_ID,
            caseType,
            caseId,
            true,
            caseDataContent
        );
    }

    /**
     * Given a list of caseIds, this method will return a list of case details.
     *
     * @param authorisation used for IDAM authentication for the query
     * @param caseIds       used as the query parameter
     * @return a list of case details
     */
    public List<CaseDetails> getCaseData(String authorisation, List<String> caseIds) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
            .filter(new TermsQueryBuilder("reference.keyword", caseIds));
        String query = new SearchSourceBuilder()
            .query(boolQueryBuilder)
            .toString();

        return searchEnglandScotlandCases(authorisation, query);
    }

    private List<CaseDetails> searchEnglandScotlandCases(String authorisation, String query) {
        List<CaseDetails> caseDetailsList = new ArrayList<>();
        caseDetailsList.addAll(searchCaseType(authorisation, ENGLAND_CASE_TYPE, query));
        caseDetailsList.addAll(searchCaseType(authorisation, SCOTLAND_CASE_TYPE, query));
        return caseDetailsList;
    }

    private List<CaseDetails> searchCaseType(String authorisation, String caseTypeId, String query) {
        List<CaseDetails> caseDetailsList = new ArrayList<>();
        SearchResult searchResult = ccdApiClient.searchCases(authorisation, authTokenGenerator.generate(),
                                                             caseTypeId, query);
        if (searchResult != null && !CollectionUtils.isEmpty(searchResult.getCases())) {
            caseDetailsList.addAll(searchResult.getCases());
        }
        return caseDetailsList;
    }

    private List<AcasCertificate> getCertificatesFromCase(CaseData caseData)
        throws AcasException, InvalidAcasNumbersException {
        List<RespondentSumTypeItem> respondentSumTypeItems = caseData.getRespondentCollection();

        switch (respondentSumTypeItems.size()) {
            case 1: {
                return acasService.getCertificates(respondentSumTypeItems.get(0).getValue().getRespondentAcas());
            }
            case 2: {
                return acasService.getCertificates(
                    respondentSumTypeItems.get(0).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(1).getValue().getRespondentAcas());
            }
            case 3: {
                return acasService.getCertificates(
                    respondentSumTypeItems.get(0).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(1).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(2).getValue().getRespondentAcas());
            }
            case 4: {
                return acasService.getCertificates(
                    respondentSumTypeItems.get(0).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(1).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(2).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(3).getValue().getRespondentAcas());
            }
            case 5: {
                return acasService.getCertificates(
                    respondentSumTypeItems.get(0).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(1).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(2).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(3).getValue().getRespondentAcas(),
                    respondentSumTypeItems.get(4).getValue().getRespondentAcas());
            }
            default:
                return List.of();
        }
    }
}
