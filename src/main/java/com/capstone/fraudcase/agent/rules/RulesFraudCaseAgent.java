package com.capstone.fraudcase.agent.rules;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import com.capstone.fraudcase.agent.common.EvidenceView;
import com.capstone.fraudcase.agent.common.FollowUpContext;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.FraudCaseRecommendation;
import com.capstone.fraudcase.agent.common.FraudCaseRecommendation.PolicyCitationRef;
import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.ClassificationRoutes;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import com.capstone.fraudcase.tools.ToolNames;
import com.capstone.fraudcase.tools.ToolResults.DeviceSignals;
import com.capstone.fraudcase.tools.ToolResults.LocationComparison;
import com.capstone.fraudcase.tools.ToolResults.MerchantRiskProfile;
import com.capstone.fraudcase.tools.ToolResults.PriorDisputes;
import com.capstone.fraudcase.tools.ToolResults.ScreeningResult;
import com.capstone.fraudcase.tools.ToolResults.TransactionDetails;
import com.capstone.fraudcase.tools.ToolResults.TransactionVelocity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback processor (guide section 3.2). No model dependency; every result carries rule IDs and the
 * exact input facts used.
 *
 * <pre>
 * R-01 mandatory evidence missing            -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * R-02 duplicate fingerprint confirmed       -> DUPLICATE_PROCESSING / PAYMENT_OPERATIONS
 * R-04 recognised merchant + service issue   -> MERCHANT_DISPUTE / DISPUTE_OPERATIONS
 * R-03 >= 2 independent high anomalies       -> UNAUTHORISED or SUSPICIOUS / FRAUD_INVESTIGATION
 * R-06 contradictory device/location signals -> SUSPICIOUS_TRANSACTION / MANUAL_REVIEW, reduced confidence
 * R-08 single high anomaly + denial          -> SUSPICIOUS_TRANSACTION / FRAUD_INVESTIGATION
 * R-07 authorised-activity indicators        -> GENUINE_TRANSACTION / MANUAL_REVIEW
 * R-09 otherwise                             -> INSUFFICIENT_EVIDENCE / MANUAL_REVIEW
 * R-05 no policy passage above threshold     -> route overridden to MANUAL_REVIEW, no procedure asserted
 * R-10 confirmed synthetic screening match   -> route overridden to AML_INVESTIGATION, risk raised, human decides
 * R-11 potential screening match             -> recorded as pending adjudication in missing information
 * </pre>
 */
@Component
public class RulesFraudCaseAgent implements FraudCaseProcessor {

    private final CaseMemoryStore memory;

    public RulesFraudCaseAgent(CaseMemoryStore memory) {
        this.memory = memory;
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.RULES;
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext ctx) {
        CaseFacts facts = ctx.facts();
        List<String> missing = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        List<String> inputFacts = new ArrayList<>();
        List<String> rules = new ArrayList<>();
        Set<String> evidence = new LinkedHashSet<>();
        ctx.evidenceId(EvidenceType.CUSTOMER_STATEMENT).ifPresent(evidence::add);

        inputFacts.add("intake.issueType=" + facts.issueType());
        inputFacts.add("intake.merchantRecognised=" + facts.merchantRecognised());
        inputFacts.add("intake.cardPossession=" + facts.cardPossession());
        inputFacts.add("intake.customerStatementPresent=" + facts.hasStatement());

        for (ToolEnvelope t : ctx.toolOutcomes()) {
            if (!t.available() && !ToolNames.POLICY.equals(t.toolName())) {
                missing.add(label(t.toolName()) + " unavailable (" + t.status() + ", " + t.toolResultId() + ")");
                inputFacts.add(t.toolName() + ".status=" + t.status());
            }
        }
        Optional<TransactionDetails> txn = ctx.result(ToolNames.TRANSACTION, TransactionDetails.class);
        Optional<DeviceSignals> device = ctx.result(ToolNames.DEVICE, DeviceSignals.class);
        Optional<LocationComparison> location = ctx.result(ToolNames.LOCATION, LocationComparison.class);
        Optional<MerchantRiskProfile> merchant = ctx.result(ToolNames.MERCHANT, MerchantRiskProfile.class);
        Optional<PriorDisputes> prior = ctx.result(ToolNames.PRIOR_DISPUTES, PriorDisputes.class);
        Optional<TransactionVelocity> velocity = ctx.result(ToolNames.VELOCITY, TransactionVelocity.class);
        Optional<ScreeningResult> screening = ctx.result(ToolNames.SCREENING, ScreeningResult.class);

        txn.ifPresent(t -> {
            inputFacts.add("transaction.authOutcome=" + t.authOutcome());
            inputFacts.add("transaction.duplicateFingerprintMatch=" + t.duplicateFingerprintMatch());
            if (t.authUnavailable()) {
                missing.add("authentication challenge outcome");
            }
        });
        device.ifPresent(d -> inputFacts.add("device.deviceKnown=" + d.deviceKnown() + ", trustLevel=" + d.trustLevel()));
        location.ifPresent(l -> inputFacts.add("location.anomalyLevel=" + l.anomalyLevel() + ", distanceKm=" + l.distanceKm()));
        merchant.ifPresent(m -> inputFacts.add("merchant.riskBand=" + m.riskBand() + ", blocklistMatch=" + m.blocklistMatch()));
        prior.ifPresent(p -> inputFacts.add("priorDisputes.count=" + p.count() + ", similarCount=" + p.similarCount()));
        velocity.ifPresent(v -> inputFacts.add("velocity.count10Min=" + v.count10Min() + ", count60Min=" + v.count60Min()
                + ", count24h=" + v.count24h() + ", burst=" + v.burst()));
        screening.ifPresent(s -> {
            inputFacts.add("screening.status=" + s.status() + ", listType=" + s.listType() + ", matchScore=" + s.matchScore());
            if (s.potentialMatch()) {
                missing.add("adjudication of potential " + s.listType() + " screening match");
            }
        });
        if (!facts.hasStatement()) {
            missing.add("customer statement");
        }

        Map<String, String> highs = new LinkedHashMap<>();
        device.filter(d -> !d.deviceKnown() && "LOW".equals(d.trustLevel()))
                .ifPresent(d -> highs.put(ref(ctx, EvidenceType.DEVICE), "device not previously observed, trust LOW"));
        location.filter(l -> "HIGH".equals(l.anomalyLevel()))
                .ifPresent(l -> highs.put(ref(ctx, EvidenceType.LOCATION), "location " + l.distanceKm() + " km from trusted activity"));
        merchant.filter(MerchantRiskProfile::blocklistMatch)
                .ifPresent(m -> highs.put(ref(ctx, EvidenceType.MERCHANT), "merchant on training blocklist"));
        txn.filter(t -> t.authOutcome() != null && t.authOutcome().startsWith("FAILED"))
                .ifPresent(t -> highs.put(ref(ctx, EvidenceType.TRANSACTION), "authentication challenge failed"));
        velocity.filter(TransactionVelocity::burst)
                .ifPresent(v -> highs.put(ref(ctx, EvidenceType.VELOCITY),
                        "velocity burst: %d transactions in 10 minutes".formatted(v.count10Min())));
        screening.filter(ScreeningResult::confirmedMatch)
                .ifPresent(s -> highs.put(ref(ctx, EvidenceType.COMPLIANCE),
                        "confirmed " + s.listType() + " screening match (score %.2f)".formatted(s.matchScore())));

        if (device.map(DeviceSignals::trusted).orElse(false) && location.map(l -> "HIGH".equals(l.anomalyLevel())).orElse(false)) {
            contradictions.add("Trusted long-standing device [%s] conflicts with high location anomaly [%s]"
                    .formatted(ref(ctx, EvidenceType.DEVICE), ref(ctx, EvidenceType.LOCATION)));
        }

        boolean denies = facts.issueType().deniesAuthorisation();
        Classification classification;
        RiskLevel risk;
        double confidence;
        String reason;

        if (txn.isEmpty() || !facts.hasStatement()) {
            rules.add("R-01");
            classification = Classification.INSUFFICIENT_EVIDENCE;
            risk = RiskLevel.MEDIUM;
            confidence = 0.4;
            ctx.evidenceId(EvidenceType.TRANSACTION).ifPresent(evidence::add);
            reason = "Mandatory evidence is missing (transaction record or customer statement); no facts were assumed.";
        } else if (txn.get().duplicateFingerprintMatch()) {
            rules.add("R-02");
            classification = Classification.DUPLICATE_PROCESSING;
            risk = RiskLevel.MEDIUM;
            confidence = 0.9;
            evidence.add(ref(ctx, EvidenceType.TRANSACTION));
            reason = "Transaction record confirms a matching payment fingerprint [%s] (duplicate of %s)."
                    .formatted(ref(ctx, EvidenceType.TRANSACTION), txn.get().duplicateOfRef());
        } else if (facts.merchantRecognised() && facts.issueType().isMerchantServiceIssue()) {
            rules.add("R-04");
            classification = Classification.MERCHANT_DISPUTE;
            risk = highs.isEmpty() ? RiskLevel.LOW : RiskLevel.MEDIUM;
            confidence = highs.isEmpty() ? 0.85 : 0.7;
            evidence.add(ref(ctx, EvidenceType.TRANSACTION));
            ctx.evidenceId(EvidenceType.MERCHANT).ifPresent(evidence::add);
            reason = "Customer recognises the merchant and disputes %s, which is a merchant service issue."
                    .formatted(facts.issueType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        } else if (highs.size() >= 2) {
            rules.add("R-03");
            classification = denies && !facts.merchantRecognised() ? Classification.UNAUTHORISED_TRANSACTION : Classification.SUSPICIOUS_TRANSACTION;
            risk = highs.size() >= 3 ? RiskLevel.CRITICAL : RiskLevel.HIGH;
            confidence = Math.min(0.92, 0.82 + 0.05 * (highs.size() - 2));
            evidence.addAll(highs.keySet());
            reason = "%d independently sourced high-severity anomalies: %s.".formatted(highs.size(), describe(highs))
                    + (denies ? " Customer denies the transaction." : "");
        } else if (!contradictions.isEmpty()) {
            rules.add("R-06");
            classification = Classification.SUSPICIOUS_TRANSACTION;
            risk = RiskLevel.MEDIUM;
            confidence = 0.5;
            evidence.add(ref(ctx, EvidenceType.DEVICE));
            evidence.add(ref(ctx, EvidenceType.LOCATION));
            reason = "Device and location signals contradict each other; an investigator must reconcile them before any fraud classification.";
        } else if (highs.size() == 1 && denies) {
            rules.add("R-08");
            classification = Classification.SUSPICIOUS_TRANSACTION;
            risk = RiskLevel.MEDIUM;
            confidence = 0.65;
            evidence.addAll(highs.keySet());
            reason = "One high-severity anomaly (%s) with a customer denial; insufficient for an unauthorised classification.".formatted(describe(highs));
        } else if (denies && device.map(DeviceSignals::trusted).orElse(false)
                && location.map(l -> "NONE".equals(l.anomalyLevel()) || "LOW".equals(l.anomalyLevel())).orElse(false)
                && "SUCCESS".equals(txn.get().authOutcome())) {
            rules.add("R-07");
            classification = Classification.GENUINE_TRANSACTION;
            risk = RiskLevel.LOW;
            confidence = 0.7;
            evidence.add(ref(ctx, EvidenceType.DEVICE));
            evidence.add(ref(ctx, EvidenceType.LOCATION));
            evidence.add(ref(ctx, EvidenceType.TRANSACTION));
            ctx.evidenceId(EvidenceType.PRIOR_DISPUTES).ifPresent(evidence::add);
            reason = "Trusted device, consistent location and successful authentication indicate customer-authorised activity"
                    + prior.filter(p -> p.similarCount() > 0).map(p -> "; " + p.similarCount() + " similar earlier disputes").orElse("") + ".";
        } else {
            rules.add("R-09");
            classification = Classification.INSUFFICIENT_EVIDENCE;
            risk = RiskLevel.MEDIUM;
            confidence = 0.45;
            ctx.evidenceId(EvidenceType.TRANSACTION).ifPresent(evidence::add);
            reason = "Evidence does not support a specific classification.";
        }

        Route route = rules.contains("R-06") ? Route.MANUAL_REVIEW : ClassificationRoutes.defaultRoute(classification);
        if (screening.map(ScreeningResult::confirmedMatch).orElse(false)) {
            rules.add("R-10");
            route = Route.AML_INVESTIGATION;
            risk = risk.compareTo(RiskLevel.HIGH) < 0 ? RiskLevel.HIGH : risk;
        }
        List<PolicyCitationRef> citations = new ArrayList<>();
        String policySentence;
        if (ctx.policy().hasCitations()) {
            List<PolicyPassage> cited = ctx.policy().cited().stream().limit(2).toList();
            cited.forEach(p -> citations.add(new PolicyCitationRef(p.documentId(), p.version(), p.section(), p.chunkId(), p.score())));
            policySentence = " Retrieved guidance: " + cited.stream()
                    .map(p -> "%s v%s s.%s [%s]".formatted(p.documentId(), p.version(), p.section().split(" ")[0], p.chunkId()))
                    .collect(Collectors.joining("; ")) + ".";
        } else {
            rules.add("R-05");
            route = Route.MANUAL_REVIEW;
            confidence -= 0.15;
            missing.add("applicable policy passage above similarity threshold (retrieval " + ctx.policy().status() + ")");
            policySentence = " No policy passage met the similarity threshold, so no procedure is asserted.";
        }
        long gaps = missing.stream().filter(m -> !m.startsWith("applicable policy")).count();
        confidence = Math.max(0.2, Math.min(0.95, confidence - 0.05 * gaps));
        String contradictionNote = contradictions.isEmpty() || rules.contains("R-06") ? "" : " Contradictions recorded for the reviewer.";
        String rationale = "Rule " + String.join(", ", rules) + ": " + reason + contradictionNote + policySentence
                + " Recommended route " + route + " for human review; no automated action taken.";

        List<String> traceIds = ctx.toolOutcomes().stream().map(ToolEnvelope::toolResultId).toList();
        evidence.remove(null);
        evidence.remove("n/a");
        return new FraudCaseRecommendation(ctx.caseId(), classification, risk, route, Math.round(confidence * 100) / 100.0,
                true, List.copyOf(evidence), citations, missing, rules, traceIds, contradictions, inputFacts, rationale);
    }

    @Override
    public String answerFollowUp(FollowUpContext ctx) {
        String q = ctx.question().toLowerCase(Locale.ROOT);
        var f = ctx.facts();
        String answer;
        if (q.contains("policy") || q.contains("citation") || q.contains("procedure")) {
            answer = f.citationLines().isEmpty() ? "No policy passage met the similarity threshold for this case; no procedure is asserted."
                    : "Policy passages cited for this case: " + String.join("; ", f.citationLines());
        } else if (q.contains("missing") || q.contains("gap") || q.contains("unavailable")) {
            answer = f.missingInformation().isEmpty() ? "No missing information was recorded for this case."
                    : "Missing information: " + String.join("; ", f.missingInformation());
        } else if (q.contains("recommend") || q.contains("route") || q.contains("why")) {
            answer = f.recommendationLine();
        } else {
            List<String> matches = f.evidenceLines().stream()
                    .filter(line -> keywords(q).stream().anyMatch(k -> line.toLowerCase(Locale.ROOT).contains(k))).toList();
            answer = matches.isEmpty()
                    ? "That is not available in the recorded evidence for this case. Recorded evidence: " + String.join("; ", f.evidenceLines())
                    : String.join("; ", matches);
        }
        String text = "[rules mode] " + answer;
        memory.append(ctx.caseId(), "USER", ctx.question(), null);
        memory.append(ctx.caseId(), "ASSISTANT", text, "rules");
        return text;
    }

    private static List<String> keywords(String q) {
        return java.util.Arrays.stream(q.split("[^a-z0-9-]+")).filter(w -> w.length() > 3).toList();
    }

    private static String ref(FraudCaseContext ctx, EvidenceType type) {
        return ctx.evidence(type).stream().filter(EvidenceView::available).map(EvidenceView::evidenceId).findFirst()
                .orElse(ctx.evidenceId(type).orElse("n/a"));
    }

    private static String describe(Map<String, String> highs) {
        return highs.entrySet().stream().map(e -> e.getValue() + " [" + e.getKey() + "]").collect(Collectors.joining("; "));
    }

    static String label(String tool) {
        return switch (tool) {
            case ToolNames.TRANSACTION -> "transaction record";
            case ToolNames.DEVICE -> "device signals";
            case ToolNames.LOCATION -> "location comparison";
            case ToolNames.MERCHANT -> "merchant risk profile";
            case ToolNames.PRIOR_DISPUTES -> "prior dispute history";
            case ToolNames.VELOCITY -> "transaction velocity";
            case ToolNames.SCREENING -> "compliance screening";
            case ToolNames.SLA -> "SLA calculation";
            default -> tool;
        };
    }
}
