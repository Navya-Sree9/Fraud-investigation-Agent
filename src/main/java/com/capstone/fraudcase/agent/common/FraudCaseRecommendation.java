package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import java.util.List;

/**
 * The single structured recommendation contract shared by every processor (guide section 3.3).
 * {@code contradictions} and {@code inputFacts} are additive fields used to show contradictory signals and the
 * exact facts a deterministic rule relied on.
 */
public record FraudCaseRecommendation(
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        Route recommendedRoute,
        Double confidence,
        Boolean humanReviewRequired,
        List<String> evidenceIds,
        List<PolicyCitationRef> policyCitations,
        List<String> missingInformation,
        List<String> ruleIds,
        List<String> toolTraceIds,
        List<String> contradictions,
        List<String> inputFacts,
        String rationale) {

    public record PolicyCitationRef(String documentId, String version, String section, String chunkId, Double score) {
    }

    public FraudCaseRecommendation withCitations(List<PolicyCitationRef> citations) {
        return new FraudCaseRecommendation(caseId, classification, riskLevel, recommendedRoute, confidence,
                humanReviewRequired, evidenceIds, citations, missingInformation, ruleIds, toolTraceIds, contradictions,
                inputFacts, rationale);
    }

    public FraudCaseRecommendation withToolTraceIds(List<String> traceIds) {
        return new FraudCaseRecommendation(caseId, classification, riskLevel, recommendedRoute, confidence,
                humanReviewRequired, evidenceIds, policyCitations, missingInformation, ruleIds, traceIds, contradictions,
                inputFacts, rationale);
    }

    public FraudCaseRecommendation withMissingInformation(List<String> missing) {
        return new FraudCaseRecommendation(caseId, classification, riskLevel, recommendedRoute, confidence,
                humanReviewRequired, evidenceIds, policyCitations, missing, ruleIds, toolTraceIds, contradictions,
                inputFacts, rationale);
    }
}
