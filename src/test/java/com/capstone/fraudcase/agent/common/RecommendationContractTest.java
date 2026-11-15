package com.capstone.fraudcase.agent.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstone.fraudcase.agent.common.FraudCaseRecommendation.PolicyCitationRef;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Parser + validator unit tests (guide section 3.4, scenario T09). */
class RecommendationContractTest {

    private final RecommendationParser parser = new RecommendationParser(new ObjectMapper());
    private final RecommendationValidator validator = new RecommendationValidator();
    private final PolicyPassage passage = new PolicyPassage("POL-CNP-017", "CNP", "3.2", LocalDate.of(2026, 10, 1),
            "3.2 Initial fraud routing", "cnp-032", 0.83, true, "text");
    private final RecommendationValidator.Scope scope = new RecommendationValidator.Scope("CASE-2026-0042",
            Set.of("EV-101", "EV-102"), Set.of("TR-118"), List.of(passage), false);

    private FraudCaseRecommendation valid() {
        return new FraudCaseRecommendation("CASE-2026-0042", Classification.UNAUTHORISED_TRANSACTION, RiskLevel.HIGH,
                Route.FRAUD_INVESTIGATION, 0.86, true, List.of("EV-101", "EV-102"),
                List.of(new PolicyCitationRef("POL-CNP-017", "3.2", "wrong section", "cnp-032", 0.5)),
                List.of("authentication challenge outcome"), List.of(), List.of("TR-118"), List.of(), List.of(),
                "Route for human fraud review per POL-CNP-017 [cnp-032]; no automated action taken.");
    }

    @Test
    void parsesGuideExampleWithMarkdownFences() {
        String raw = """
                ```json
                {"caseId":"CASE-2026-0042","classification":"UNAUTHORISED_TRANSACTION","riskLevel":"HIGH",
                 "recommendedRoute":"FRAUD_INVESTIGATION","confidence":0.86,"humanReviewRequired":true,
                 "evidenceIds":["EV-101"],"policyCitations":[{"documentId":"POL-CNP-017","version":"3.2","section":"3.2","chunkId":"cnp-032","score":0.89}],
                 "missingInformation":["authentication challenge outcome"],"ruleIds":[],"toolTraceIds":["TR-118"],
                 "rationale":"Route for human fraud review; no automated action taken."}
                ```""";
        FraudCaseRecommendation rec = parser.parse(raw);
        assertThat(rec.classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(rec.policyCitations()).hasSize(1);
    }

    @Test
    void t09_rejectsUnknownEnumConfidenceTypeAndMalformedCitation() {
        assertThatThrownBy(() -> parser.parse("{\"caseId\":\"C\",\"classification\":\"FRAUD\",\"riskLevel\":\"HIGH\",\"recommendedRoute\":\"X\","
                + "\"confidence\":\"high\",\"humanReviewRequired\":true,\"policyCitations\":[{\"documentId\":\"P\"}],\"rationale\":\"r\"}"))
                .isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).errors()).contains(
                        "unknown classification value 'FRAUD'", "unknown recommendedRoute value 'X'",
                        "field 'confidence' must be a number"));
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void replacesCitationMetadataWithExactRetrievedValues() {
        FraudCaseRecommendation rec = validator.validate(valid(), scope);
        assertThat(rec.policyCitations()).containsExactly(new PolicyCitationRef("POL-CNP-017", "3.2", "3.2 Initial fraud routing", "cnp-032", 0.83));
    }

    @Test
    void rejectsHumanReviewFalseConfidenceRangeAndCaseMismatch() {
        FraudCaseRecommendation v = valid();
        FraudCaseRecommendation bad = new FraudCaseRecommendation("CASE-OTHER", v.classification(), v.riskLevel(), v.recommendedRoute(),
                1.4, false, v.evidenceIds(), v.policyCitations(), v.missingInformation(), v.ruleIds(), v.toolTraceIds(),
                v.contradictions(), v.inputFacts(), v.rationale());
        assertThatThrownBy(() -> validator.validate(bad, scope)).isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).errors()).contains(
                        "humanReviewRequired must be true", "confidence must be between 0 and 1",
                        "caseId missing or does not match the case under investigation"));
    }

    @Test
    void rejectsInventedEvidenceTracesAndCitations() {
        FraudCaseRecommendation v = valid();
        FraudCaseRecommendation bad = new FraudCaseRecommendation(v.caseId(), v.classification(), v.riskLevel(), v.recommendedRoute(),
                0.8, true, List.of("EV-404"), List.of(new PolicyCitationRef("POL-FAKE-001", "1.0", "1", "fake-010", 0.9)),
                List.of(), List.of(), List.of("TR-999"), List.of(), List.of(), "Grounded rationale without procedure.");
        assertThatThrownBy(() -> validator.validate(bad, scope)).isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).errors()).hasSize(3));
    }

    @Test
    void rejectsProcedureClaimWithoutCitationAndNonManualRouteUnderAbstention() {
        FraudCaseRecommendation v = valid();
        FraudCaseRecommendation bad = new FraudCaseRecommendation(v.caseId(), v.classification(), v.riskLevel(), Route.FRAUD_INVESTIGATION,
                0.8, true, v.evidenceIds(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "According to the policy this must go to fraud.");
        var abstained = new RecommendationValidator.Scope(scope.caseId(), scope.evidenceIds(), scope.traceIds(), List.of(), true);
        assertThatThrownBy(() -> validator.validate(bad, abstained)).isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).errors()).contains(
                        "rationale makes a procedural/policy claim without a policy citation",
                        "no policy passage met the threshold, so the route must be MANUAL_REVIEW"));
    }

    @Test
    void rejectsClaimsOfCompletedFinancialActionsAndInconsistentRoutes() {
        FraudCaseRecommendation v = valid();
        FraudCaseRecommendation bad = new FraudCaseRecommendation(v.caseId(), Classification.MERCHANT_DISPUTE, v.riskLevel(),
                Route.PAYMENT_OPERATIONS, 0.8, true, v.evidenceIds(), v.policyCitations(), List.of(), List.of(), List.of(), List.of(),
                List.of(), "The card has been blocked and a refund was issued.");
        assertThatThrownBy(() -> validator.validate(bad, scope)).isInstanceOf(RecommendationValidationException.class)
                .satisfies(e -> assertThat(((RecommendationValidationException) e).errors()).anyMatch(m -> m.contains("completed financial"))
                        .anyMatch(m -> m.contains("not permitted for MERCHANT_DISPUTE")));
    }

    @Test
    void insufficientEvidenceMayHaveNoEvidenceIds() {
        FraudCaseRecommendation rec = new FraudCaseRecommendation("CASE-2026-0042", Classification.INSUFFICIENT_EVIDENCE, RiskLevel.MEDIUM,
                Route.MANUAL_REVIEW, 0.3, true, List.of(), List.of(), List.of("transaction record"), List.of("R-01"), List.of(), List.of(),
                List.of(), "Mandatory evidence missing; manual review.");
        assertThat(validator.validate(rec, scope).classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
    }
}
