package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds the prompt variables (guide section 6.2) from a FraudCaseContext. Framework-neutral. */
@Component
public class CasePromptRenderer {

    private final PromptLibrary prompts;

    public CasePromptRenderer(PromptLibrary prompts) {
        this.prompts = prompts;
    }

    public String systemPrompt() {
        return prompts.raw(PromptLibrary.SYSTEM);
    }

    public String userPrompt(FraudCaseContext ctx) {
        return prompts.render(PromptLibrary.USER, variables(ctx));
    }

    public Map<String, String> variables(FraudCaseContext ctx) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("investigationGoal", ctx.investigationGoal());
        v.put("caseSummary", caseSummary(ctx));
        v.put("plan", ctx.plan().stream().map(p -> p.order() + ". " + p.toolName() + " - " + p.purpose()).collect(Collectors.joining("\n")));
        v.put("toolEvidence", toolEvidence(ctx));
        v.put("policyContext", policyContext(ctx));
        v.put("topK", Integer.toString(ctx.policy().topK()));
        v.put("threshold", Double.toString(ctx.policy().threshold()));
        v.put("retrievalStatus", ctx.policy().status().name());
        v.put("securityNotes", ctx.securityFlags().isEmpty() ? "none" : String.join("\n", ctx.securityFlags()));
        v.put("outputSchema", prompts.raw(PromptLibrary.SCHEMA));
        return v;
    }

    String caseSummary(FraudCaseContext ctx) {
        CaseFacts f = ctx.facts();
        String statementId = ctx.evidenceId(EvidenceType.CUSTOMER_STATEMENT).orElse("n/a");
        return """
                caseId: %s
                reportedAt: %s | intakeChannel: %s
                customer: %s | homeRegion: %s
                transaction: %s | amount: %s %s | occurredAt: %s | status: %s | channel: %s | merchant: %s
                claim: issueType=%s | cardPossession=%s | merchantRecognised=%s
                <customer_statement evidenceId="%s">%s</customer_statement>"""
                .formatted(f.caseId(), f.reportedAt(), f.intakeChannel(), f.maskedCustomerRef(), f.homeRegion(),
                        f.transactionRef(), f.amount().toPlainString(), f.currency(), f.occurredAt(), f.transactionStatus(),
                        f.paymentChannel(), f.maskedMerchant(), f.issueType(), f.cardPossession(), f.merchantRecognised(),
                        statementId, escape(f.customerStatement()));
    }

    String toolEvidence(FraudCaseContext ctx) {
        return ctx.evidence().stream()
                .filter(e -> e.type() != EvidenceType.CUSTOMER_STATEMENT)
                .map(e -> "[%s] %s severity=%s provenance=%s source=%s%s: %s".formatted(e.evidenceId(), e.type(), e.severity(),
                        e.provenance(), e.sourceRef(), e.available() ? "" : " UNAVAILABLE (do not assume values)", e.summary()))
                .collect(Collectors.joining("\n"));
    }

    String policyContext(FraudCaseContext ctx) {
        if (ctx.policy().candidates().isEmpty()) {
            return "NO POLICY PASSAGES (retrieval " + ctx.policy().status() + "). Do not describe any procedure; route MANUAL_REVIEW.";
        }
        String passages = ctx.policy().candidates().stream().map(this::passage).collect(Collectors.joining("\n"));
        return ctx.policy().hasCitations() ? passages
                : passages + "\nNO PASSAGE MET THE THRESHOLD. Do not cite or describe any procedure; route MANUAL_REVIEW.";
    }

    private String passage(PolicyPassage p) {
        return "<policy_passage %s documentId=\"%s\" version=\"%s\" section=\"%s\" chunkId=\"%s\" score=\"%.4f\">%s</policy_passage>"
                .formatted(p.aboveThreshold() ? "CITABLE" : "NOT_CITABLE_BELOW_THRESHOLD", p.documentId(), p.version(),
                        p.section(), p.chunkId(), p.score(), escape(p.passage()));
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("<", "&lt;").replace(">", "&gt;");
    }
}
