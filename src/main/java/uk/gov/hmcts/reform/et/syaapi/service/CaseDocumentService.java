package uk.gov.hmcts.reform.et.syaapi.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.model.Classification;
import uk.gov.hmcts.reform.et.syaapi.models.CaseDocument;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CaseDocumentService provides access to the document upload service API, used to upload documents that are
 * associated to a specific case record held in CCD.
 * <p>
 * This relies upon the following configurations to be set at an environment level:
 * <ul>
 *     <li>CASE_DOCUMENT_AM_URL</li>
 * </ul>
 */
@Service
public class CaseDocumentService {
    private static final String JURISDICTION = "EMPLOYMENT";

    private static final String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    private static final String FILE_NAME_REGEX_PATTERN = "^[\\w-_ ]{1,256}+\\.[A-Za-z]{3,4}$";

    private static final String HTTPS_URL_REGEX_PATTERN =
        "^(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)?[\\w\\d]+([\\-\\.]{1}[\\w\\d]+)" +
            "*(\\.[a-z]{2,5})?(:\\d{1,5})?(\\/.*)?$";

    private static final String UPLOAD_FILE_EXCEPTION_MESSAGE = "Document management failed uploading file: ";

    private final RestTemplate restTemplate;

    private final AuthTokenGenerator authTokenGenerator;

    private final String caseDocApiUrl;

    /**
     * Default constructor with injected parameters
     *
     * @param restTemplate       the {@link RestTemplate} to be used to connect with the Case Document API
     * @param authTokenGenerator the {@link AuthTokenGenerator} used to generate tokens for communicating with the
     *                           Case Document API
     * @param caseDocApiUrl      the URL to call the Case Document API
     */
    public CaseDocumentService(RestTemplate restTemplate,
                               AuthTokenGenerator authTokenGenerator,
                               @Value("${case_document_am.url}/cases/documents}") String caseDocApiUrl) {
        this.restTemplate = restTemplate;
        this.authTokenGenerator = authTokenGenerator;
        this.caseDocApiUrl = caseDocApiUrl;
    }

    /**
     * When given a file to upload, this call with upload the file to the CCD document API.
     * return a URI pointing to the uploaded file if successful.
     *
     * @param authToken the caller's bearer token used to verify the caller
     * @param caseTypeId defines the area the file belongs to e.g. ET_EnglandWales
     * @param file the file to be uploaded
     * @return the URL of the document we have just uploaded
     * @throws CaseDocumentException  if a problem occurs whilst uploading the document via API
     */
    public URI uploadDocument(String authToken, String caseTypeId, MultipartFile file) throws CaseDocumentException{
        try {
            validateFile(file);

            ResponseEntity<DocumentUploadResponse> response = getDocumentUploadResponseResponseEntity(
                authToken, caseTypeId, file);

            CaseDocument caseDocument = validateDocument(
                Objects.requireNonNull(response.getBody()), file.getOriginalFilename());

            return getUriFromFile(caseDocument);
        } catch (RestClientException | IOException e) {
            throw new CaseDocumentException("Failed to upload Case Document", e);
        }
    }

    private URI getUriFromFile(CaseDocument caseDocument) throws CaseDocumentException {
        try {
            return URI.create(caseDocument.getLinks().get("self").get("href"));
        } catch (NullPointerException e) {
            throw new CaseDocumentException("Failed to generate Case Document URI", e);
        }
    }

    @Retryable(IOException.class)
    private ResponseEntity<DocumentUploadResponse> getDocumentUploadResponseResponseEntity(String authToken,
                                                                                           String caseTypeId,
                                                                                           MultipartFile file)
        throws IOException {
        MultiValueMap<String, Object> body = generateUploadRequest(
            caseTypeId, file);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, getHttpHeaders(authToken));

        return restTemplate.exchange(
            caseDocApiUrl,
            HttpMethod.POST,
            request,
            DocumentUploadResponse.class
        );
    }

    @Recover
    ResponseEntity<DocumentUploadResponse> recover(IOException e) {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders getHttpHeaders(String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, authToken);
        headers.add(SERVICE_AUTHORIZATION, authTokenGenerator.generate());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private void validateFile(MultipartFile file) throws CaseDocumentException {
        String filename = file.getOriginalFilename();
        if(filename == null || filename.isEmpty()) {
            throw new CaseDocumentException("File does not pass validation");
        }

        Pattern pattern = Pattern.compile(FILE_NAME_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(filename);
        if(!matcher.matches()) {
            throw new CaseDocumentException("File does not pass validation");
        }
    }

    private CaseDocument validateDocument(DocumentUploadResponse response, String originalFilename)
        throws CaseDocumentException {
        if(response.documents == null || response.documents.isEmpty()) {
            throw new CaseDocumentException(UPLOAD_FILE_EXCEPTION_MESSAGE + originalFilename);
        }

        CaseDocument document = response.getDocuments().stream()
            .findFirst()
            .orElseThrow(() -> new CaseDocumentException(UPLOAD_FILE_EXCEPTION_MESSAGE + originalFilename));

        String uri = getUriFromFile(document).toString();

        Pattern pattern = Pattern.compile(HTTPS_URL_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(uri);
        if(!matcher.matches()) {
            throw new CaseDocumentException(UPLOAD_FILE_EXCEPTION_MESSAGE + originalFilename);
        }

        return document;
    }

    private MultiValueMap<String, Object> generateUploadRequest(String caseTypeId,
                                                                MultipartFile file) throws IOException {
        ByteArrayResource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", fileAsResource);
        body.add("classification", Classification.PUBLIC.toString());
        body.add("caseTypeId", caseTypeId);
        body.add("jurisdictionId", JURISDICTION);

        return body;
    }

    @Data
    private static class DocumentUploadResponse {
        private List<CaseDocument> documents;
    }
}
