package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.knowledge.PolicyRetrievalResult;
import java.util.List;
import java.util.Optional;

/** Everything a processor may use: validated intake facts, collected tool evidence and retrieved policy. */
public record FraudCaseContext(
        String caseId,
        int runNumber,
        String correlationId,
        CaseFacts facts,
        String investigationGoal,
        List<PlanStep> plan,
        List<EvidenceView> evidence,
        List<ToolEnvelope> toolOutcomes,
        PolicyRetrievalResult policy,
        List<String> securityFlags,
        CaseToolbox toolbox) {

    public Optional<ToolEnvelope> outcome(String toolName) {
        return toolOutcomes.stream().filter(t -> t.toolName().equals(toolName)).reduce((a, b) -> b);
    }

    public <T> Optional<T> result(String toolName, Class<T> type) {
        return outcome(toolName).filter(ToolEnvelope::available).map(t -> t.resultAs(type));
    }

    public List<EvidenceView> evidence(EvidenceType type) {
        return evidence.stream().filter(e -> e.type() == type).toList();
    }

    public Optional<String> evidenceId(EvidenceType type) {
        return evidence(type).stream().map(EvidenceView::evidenceId).findFirst();
    }
}
