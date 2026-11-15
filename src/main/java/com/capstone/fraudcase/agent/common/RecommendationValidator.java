package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.agent.common.FraudCaseRecommendation.PolicyCitationRef;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ClassificationRoutes;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Application-side validation of every recommendation, regardless of processor (guide section 3.4). Rejects
 * invented evidence, trace IDs or citations; enforces human review and policy abstention; and replaces citation
 * metadata with the exact values recorded at retrieval time.
 */
@Component
public class RecommendationValidator {

    private static final Pattern PROCEDURAL_CLAIM = Pattern.compile(
            "POL-[A-Z]{2,5}-\\d{3}|\\b(per|under|according to|as required by|in line with)\\s+(the\\s+)?(policy|procedure)"
                    + "|\\b(policy|procedure)\\s+(requires|states|mandates|specifies|directs)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "\\b(card|account)\\s+(has been|was|is now)\\s+(blocked|frozen|cancelled|reissued)"
                    + "|\\brefund\\s+(has been|was)\\s+(issued|processed|credited)"
                    + "|\\b(funds|money)\\s+(have been|were)\\s+(moved|transferred|returned)"
                    + "|\\bcustomer\\s+(has been|was)\\s+(contacted|notified|emailed|called)"
                    + "|\\breport\\s+(has been|was)\\s+filed", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RATIONALE = 1500;

    /** Facts the output is allowed to reference. */
    public record Scope(String caseId, Set<String> evidenceIds, Set<String> traceIds, List<PolicyPassage> citablePassages,
                        boolean policyAbstained) {
    }

    public FraudCaseRecommendation validate(FraudCaseRecommendation rec, Scope scope) {
        List<String> errors = new ArrayList<>();
        if (rec == null) {
            throw new RecommendationValidationException(List.of("no recommendation produced"));
        }
        if (rec.caseId() == null || !rec.caseId().equals(scope.caseId())) {
            errors.add("caseId missing or does not match the case under investigation");
        }
        if (rec.classification() == null || rec.riskLevel() == null || rec.recommendedRoute() == null) {
            errors.add("classification, riskLevel and recommendedRoute are required");
        }
        if (rec.confidence() == null || rec.confidence().isNaN() || rec.confidence() < 0 || rec.confidence() > 1) {
            errors.add("confidence must be between 0 and 1");
        }
        if (!Boolean.TRUE.equals(rec.humanReviewRequired())) {
            errors.add("humanReviewRequired must be true");
        }
        if (rec.rationale() == null || rec.rationale().isBlank()) {
            errors.add("rationale is required");
        } else if (rec.rationale().length() > MAX_RATIONALE) {
            errors.add("rationale exceeds " + MAX_RATIONALE + " characters");
        } else if (COMPLETED_ACTION.matcher(rec.rationale()).find()) {
            errors.add("rationale claims a completed financial or customer action, which this assistant cannot perform");
        }
        if (rec.classification() != null && rec.recommendedRoute() != null
                && !ClassificationRoutes.isPermitted(rec.classification(), rec.recommendedRoute())) {
            errors.add("route " + rec.recommendedRoute() + " is not permitted for " + rec.classification());
        }

        List<String> evidenceIds = distinct(rec.evidenceIds());
        evidenceIds.stream().filter(id -> !scope.evidenceIds().contains(id))
                .forEach(id -> errors.add("unknown evidence ID '" + id + "' (evidence must not be invented)"));
        if (evidenceIds.isEmpty() && rec.classification() != Classification.INSUFFICIENT_EVIDENCE) {
            errors.add("at least one evidence ID is required unless the outcome is INSUFFICIENT_EVIDENCE");
        }
        List<String> traceIds = distinct(rec.toolTraceIds());
        traceIds.stream().filter(id -> !scope.traceIds().contains(id))
                .forEach(id -> errors.add("unknown tool trace ID '" + id + "'"));

        Map<String, PolicyPassage> citable = scope.citablePassages().stream()
                .collect(Collectors.toMap(PolicyPassage::chunkId, Function.identity(), (a, b) -> a));
        List<PolicyCitationRef> exact = new ArrayList<>();
        for (PolicyCitationRef ref : rec.policyCitations() == null ? List.<PolicyCitationRef>of() : rec.policyCitations()) {
            PolicyPassage passage = citable.get(ref.chunkId());
            if (passage == null || !passage.documentId().equals(ref.documentId()) || !passage.version().equals(ref.version())) {
                errors.add("citation " + ref.documentId() + "/" + ref.chunkId() + " does not match a retrieved passage above threshold");
            } else if (ref.score() == null || ref.score() < 0 || ref.score() > 1) {
                errors.add("citation score must be between 0 and 1");
            } else if (exact.stream().noneMatch(e -> e.chunkId().equals(ref.chunkId()))) {
                exact.add(new PolicyCitationRef(passage.documentId(), passage.version(), passage.section(), passage.chunkId(), passage.score()));
            }
        }
        if (exact.isEmpty() && rec.rationale() != null && PROCEDURAL_CLAIM.matcher(rec.rationale()).find()) {
            errors.add("rationale makes a procedural/policy claim without a policy citation");
        }
        if (scope.policyAbstained() && rec.recommendedRoute() != null && rec.recommendedRoute() != Route.MANUAL_REVIEW) {
            errors.add("no policy passage met the threshold, so the route must be MANUAL_REVIEW");
        }
        if (!errors.isEmpty()) {
            throw new RecommendationValidationException(errors);
        }
        return new FraudCaseRecommendation(rec.caseId(), rec.classification(), rec.riskLevel(), rec.recommendedRoute(),
                Math.round(rec.confidence() * 100) / 100.0, true, evidenceIds, exact, distinct(rec.missingInformation()),
                distinct(rec.ruleIds()), traceIds, distinct(rec.contradictions()), distinct(rec.inputFacts()), rec.rationale().trim());
    }

    private static List<String> distinct(List<String> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList()));
    }
}
