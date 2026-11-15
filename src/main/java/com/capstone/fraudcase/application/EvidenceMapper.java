package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Severity;
import com.capstone.fraudcase.tools.ToolNames;
import com.capstone.fraudcase.tools.ToolResults.DeviceSignals;
import com.capstone.fraudcase.tools.ToolResults.LocationComparison;
import com.capstone.fraudcase.tools.ToolResults.MerchantRiskProfile;
import com.capstone.fraudcase.tools.ToolResults.PriorDisputes;
import com.capstone.fraudcase.tools.ToolResults.ScreeningResult;
import com.capstone.fraudcase.tools.ToolResults.TransactionDetails;
import com.capstone.fraudcase.tools.ToolResults.TransactionVelocity;
import org.springframework.stereotype.Component;

/** Turns tool envelopes into evidence items with a severity. Unavailable tools become explicit unavailable evidence. */
@Component
public class EvidenceMapper {

    public record Draft(EvidenceType type, Severity severity, String summary, ProvenanceType provenance, String sourceRef, boolean available) {
    }

    public Draft fromTool(ToolEnvelope t) {
        EvidenceType type = typeOf(t.toolName());
        if (!t.available()) {
            return new Draft(type, Severity.INFO, "Unavailable (" + t.status() + "): " + t.summary() + ". No value assumed.",
                    ProvenanceType.TOOL_RESULT, t.toolResultId(), false);
        }
        Object r = t.result();
        Severity severity = switch (r) {
            case TransactionDetails d when d.duplicateFingerprintMatch() -> Severity.HIGH;
            case TransactionDetails d when d.authOutcome() != null && d.authOutcome().startsWith("FAILED") -> Severity.HIGH;
            case TransactionDetails d when d.authUnavailable() -> Severity.MEDIUM;
            case TransactionDetails d -> Severity.INFO;
            case DeviceSignals d when !d.deviceKnown() && "LOW".equals(d.trustLevel()) -> Severity.HIGH;
            case DeviceSignals d when !d.trusted() -> Severity.MEDIUM;
            case DeviceSignals d -> Severity.INFO;
            case LocationComparison l -> switch (l.anomalyLevel()) {
                case "HIGH" -> Severity.HIGH;
                case "MEDIUM" -> Severity.MEDIUM;
                case "LOW" -> Severity.LOW;
                default -> Severity.INFO;
            };
            case MerchantRiskProfile m when m.blocklistMatch() -> Severity.HIGH;
            case MerchantRiskProfile m when "HIGH".equals(m.riskBand()) -> Severity.MEDIUM;
            case MerchantRiskProfile m -> Severity.LOW;
            case PriorDisputes p when p.similarCount() >= 2 -> Severity.MEDIUM;
            case PriorDisputes p -> Severity.INFO;
            case TransactionVelocity v when v.burst() -> Severity.HIGH;
            case TransactionVelocity v -> Severity.INFO;
            case ScreeningResult s when s.confirmedMatch() -> Severity.HIGH;
            case ScreeningResult s when s.potentialMatch() -> Severity.MEDIUM;
            case ScreeningResult s -> Severity.INFO;
            default -> Severity.INFO;
        };
        return new Draft(type, severity, t.summary(), ProvenanceType.TOOL_RESULT, t.toolResultId(), true);
    }

    static EvidenceType typeOf(String toolName) {
        return switch (toolName) {
            case ToolNames.TRANSACTION -> EvidenceType.TRANSACTION;
            case ToolNames.DEVICE -> EvidenceType.DEVICE;
            case ToolNames.LOCATION -> EvidenceType.LOCATION;
            case ToolNames.MERCHANT -> EvidenceType.MERCHANT;
            case ToolNames.PRIOR_DISPUTES -> EvidenceType.PRIOR_DISPUTES;
            case ToolNames.VELOCITY -> EvidenceType.VELOCITY;
            case ToolNames.SCREENING -> EvidenceType.COMPLIANCE;
            case ToolNames.SLA -> EvidenceType.SLA;
            default -> EvidenceType.POLICY;
        };
    }
}
