package uk.gov.hmcts.reform.et.syaapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MultiValuedMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.et.syaapi.annotation.ApiResponseGroup;
import uk.gov.hmcts.reform.et.syaapi.models.CaseDocumentAcasResponse;
import uk.gov.hmcts.reform.et.syaapi.service.CaseDocumentService;
import uk.gov.hmcts.reform.et.syaapi.service.CaseService;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.ResponseEntity.ok;
import static uk.gov.hmcts.reform.et.syaapi.constants.EtSyaConstants.AUTHORIZATION;

/**
 * REST Controller for ACAS to communicate with CCD through ET using Azure API Management.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@SuppressWarnings({"PMD.UnnecessaryAnnotationValueElement"})
public class AcasController {

    private final CaseService caseService;
    private final CaseDocumentService caseDocumentService;
    private final IdamClient idamClient;

    @Value("${caseWorkerUserName}")
    private String caseWorkerUserName;
    @Value("${caseWorkerPassword}")
    private String caseWorkerPassword;

    /**
     * Given a datetime, this method will return a list of caseIds which have been modified since the datetime
     * provided.
     *
     * @param userToken       used for IDAM Authentication
     * @param requestDateTime used for querying when a case was last updated
     * @return a list of case ids
     */
    @GetMapping(value = "/getLastModifiedCaseList")
    @Operation(summary = "Return a list of CCD case IDs from a provided date")
    @ApiResponseGroup
    public ResponseEntity<Object> getLastModifiedCaseList(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION) String userToken,
        @RequestParam(name = "datetime")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime requestDateTime) {
        return ok(caseService.getLastModifiedCasesId(userToken, requestDateTime));
    }

    /**
     * This method is used to fetch the raw case data from CCD from a list of CaseIds.
     *
     * @param authorisation used for IDAM authentication
     * @param caseIds       a list of CCD ids
     * @return a list of case data
     */
    @GetMapping(value = "/getCaseData")
    @Operation(summary = "Provide a JSON format of the case data for a specific CCD case")
    @ApiResponseGroup
    public ResponseEntity<Object> getCaseData(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorisation,
        @RequestParam(name = "caseIds") List<String> caseIds) {
        return ok(caseService.getCaseData(authorisation, caseIds));
    }

    /**
     * This method is used to retrieve a list of documents which are available to ACAS.
     *
     * @param authorisation used for IDAM authentication
     * @param caseId        ccd case id
     * @return a multi valued map containing a list of documents for ACAS
     */
    @GetMapping(value = "/getAcasDocuments")
    @Operation(summary = "Return a list of documents on a case")
    @ApiResponseGroup
    public ResponseEntity<Object> getAcasDocuments(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorisation,
        @RequestParam(name = "caseId") String caseId) {
        MultiValuedMap<String, CaseDocumentAcasResponse> body = caseService.retrieveAcasDocuments(caseId);
        return ok(body.asMap());
    }

    /**
     * This method downloads documents for ACAS. Due to permissions, we retrieve a new token which can view the document
     * and use that to retireve the document
     *
     * @param documentId UUID for the document in DM Store
     * @param authToken  idam token of ACAS to initially verify access to the API
     * @return document
     */
    @GetMapping("/downloadAcasDocuments")
    @Operation(summary = "Get a document from CDAM in binary format")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Case document not found")
    public ResponseEntity<ByteArrayResource> getDocumentBinaryContent(
        @RequestParam(name = "documentId") final UUID documentId,
        @RequestHeader(AUTHORIZATION) String authToken) {
        String accessToken = idamClient.getAccessToken(caseWorkerUserName, caseWorkerPassword);
        return caseDocumentService.downloadDocument(accessToken, documentId);
    }
}
