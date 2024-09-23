package uk.gov.hmcts.reform.et.syaapi.search;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import uk.gov.hmcts.reform.et.syaapi.models.FindCaseForRoleModificationRequest;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

public final class ElasticSearchQueryBuilder {

    private static final String FIELD_NAME_RESPONDENT_ORGANISATION
        = "data.respondentCollection.value.respondentOrganisation.keyword";
    private static final String FIELD_NAME_RESPONDENT_NAME
        = "data.respondentCollection.value.respondent_name.keyword";
    private static final String FIELD_NAME_RESPONDENT = "data.respondent.keyword";
    private static final String FIELD_NAME_SUBMISSION_REFERENCE = "reference.keyword";
    private static final String FIELD_NAME_CLAIMANT_FIRST_NAMES = "data.claimantIndType.claimant_first_names.keyword";
    private static final String FIELD_NAME_CLAIMANT_LAST_NAME = "data.claimantIndType.claimant_last_name.keyword";
    private static final String FIELD_NAME_CLAIMANT_FULL_NAME = "data.claimant.keyword";
    private static final int ES_SIZE = 1;

    private ElasticSearchQueryBuilder() {
        // Access through static methods
    }

    /**
     * Generates query to search case by caseSubmissionReference, respondentName, claimantFirstNames,
     * and claimantLastName. This query is used to check if the respondent's entered data for self
     * assignment exists or not.
     * Compares respondent name with the fields, respondent name and organisation name. Respondent name field in the
     * database json object's value is always equals the respondent name when type of respondent is individual and
     * always equals to organisation name when type of respondent is organisation. But it may also have the name as
     * combination of respondent first name, space, respondent last name. That is why it is added as or statement to
     * both organisation name and respondent name.
     * @param findCaseForRoleModificationRequest is the parameter object which has caseSubmissionReference,
     *                                           respondentName, claimantFirstNames and claimantLastName
     * @return the string value of the elastic search query
     */
    public static String buildByFindCaseForRoleModificationRequest(
        FindCaseForRoleModificationRequest findCaseForRoleModificationRequest
    ) {
        // Respondent Queries
        BoolQueryBuilder boolQueryForRespondentOrganisationName = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_RESPONDENT_ORGANISATION,
                                  findCaseForRoleModificationRequest.getRespondentName()));
        BoolQueryBuilder boolQueryForRespondentName = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_RESPONDENT_NAME, findCaseForRoleModificationRequest.getRespondentName()));
        // Respondent name is the field that always has one of the names. If it is an organisation it has the
        // organisation name or if it is an individual has the individual name. Just added that field as an
        // or to both organisation and respondent names.
        BoolQueryBuilder boolQueryForRespondent = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_RESPONDENT, findCaseForRoleModificationRequest.getRespondentName()));


        // Claimant Queries
        BoolQueryBuilder boolQueryForClaimantFirstNames = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_CLAIMANT_FIRST_NAMES,
                                  findCaseForRoleModificationRequest.getClaimantFirstNames()));
        BoolQueryBuilder boolQueryForClaimantLastName = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_CLAIMANT_LAST_NAME,
                                  findCaseForRoleModificationRequest.getClaimantLastName()));
        BoolQueryBuilder boolQueryForClaimantFullName = boolQuery().filter(
            new MatchQueryBuilder(FIELD_NAME_CLAIMANT_FULL_NAME,
                                  findCaseForRoleModificationRequest.getClaimantFirstNames()
                                      + StringUtils.SPACE
                                      + findCaseForRoleModificationRequest.getClaimantLastName()));
        // Total Query
        BoolQueryBuilder boolQueryBuilder = boolQuery()
            .must(new MatchQueryBuilder(FIELD_NAME_SUBMISSION_REFERENCE,
                                        findCaseForRoleModificationRequest.getCaseSubmissionReference()))
            .filter(boolQuery()
                        .should(boolQueryForRespondentOrganisationName)
                        .should(boolQueryForRespondentName)
                        .should(boolQueryForRespondent))
            .filter(boolQuery()
                        .should(boolQuery()
                                    .must(boolQueryForClaimantFirstNames)
                                    .must(boolQueryForClaimantLastName))
                        .should(boolQueryForClaimantFullName));
        return new SearchSourceBuilder()
            .size(ES_SIZE)
            .query(boolQueryBuilder).toString();
    }

    /**
     * This query is used to get the specific case with the entered case submission reference to find the case.
     * that will be assigned to the respondent.
     * @param submissionReference submissionReference of the case
     * @return the string value of the elastic search query
     */
    public static String buildBySubmissionReference(String submissionReference) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
            .must(new MatchQueryBuilder(FIELD_NAME_SUBMISSION_REFERENCE, submissionReference));
        return new SearchSourceBuilder()
            .size(ES_SIZE)
            .query(boolQueryBuilder).toString();
    }
}