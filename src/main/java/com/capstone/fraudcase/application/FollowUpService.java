package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import com.capstone.fraudcase.agent.common.FollowUpContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.observability.Redactor;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.CaseMessageEntity;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import com.capstone.fraudcase.security.InputSafety;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Case-scoped follow-up questions (US-05). The brief contains only this case's facts, evidence, citations and
 * recommendation; memory is keyed by case ID. Answers are post-validated: unknown references are removed and claims
 * of completed actions are blocked. Follow-ups never change the recommendation or case state.
 */
@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);
    private static final Pattern REFERENCE = Pattern.compile("\\[([A-Za-z0-9]+-[A-Za-z0-9-]+)]");
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "\\b(has been|was|have been|were)\\s+(blocked|refunded|credited|transferred|contacted|notified|filed|frozen)\\b", Pattern.CASE_INSENSITIVE);

    public record Answer(String caseId, String answer, String mode, List<String> references, List<String> removedReferences) {
    }

    private final FraudCaseService caseService;
    private final CaseQueryService queries;
    private final ProcessorRegistry registry;
    private final CaseMemoryStore memory;
    private final AuditWriter audit;

    public FollowUpService(FraudCaseService caseService, CaseQueryService queries, ProcessorRegistry registry,
                           CaseMemoryStore memory, AuditWriter audit) {
        this.caseService = caseService;
        this.queries = queries;
        this.registry = registry;
        this.memory = memory;
        this.audit = audit;
    }

    public Answer ask(String caseId, String rawQuestion, String userId, String modeOverride) {
        String question = InputSafety.normalise(rawQuestion == null ? "" : rawQuestion);
        Map<String, String> errors = new LinkedHashMap<>();
        if (question.length() < 2 || question.length() > 500) {
            errors.put("question", "question must be 2-500 characters");
        }
        InputSafety.violations("question", question).forEach(m -> errors.putIfAbsent("question", m));
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        CaseFacts facts = caseService.facts(caseId);
        List<EvidenceItemEntity> evidence = queries.evidence(caseId);
        List<ToolTraceEntity> traces = queries.traces(caseId);
        List<PolicyCitationEntity> citations = queries.citations(caseId).stream().filter(PolicyCitationEntity::isAboveThreshold).toList();
        var rec = queries.recommendation(caseId);

        Set<String> known = new HashSet<>();
        evidence.forEach(e -> known.add(e.getEvidenceId()));
        traces.forEach(t -> known.add(t.getTraceId()));
        citations.forEach(c -> known.add(c.getChunkId()));
        List<String> evidenceLines = evidence.stream()
                .map(e -> "[%s] %s %s (%s, source %s): %s".formatted(e.getEvidenceId(), e.getType(), e.getSeverity(), e.getProvenance(),
                        e.getSourceRef(), e.getValueSummary())).toList();
        List<String> citationLines = citations.stream()
                .map(c -> "%s v%s s.%s [%s] score %.2f".formatted(c.getDocumentId(), c.getVersion(), c.getSection(), c.getChunkId(), c.getScore()))
                .toList();
        String recommendationLine = rec.map(r -> "Recommendation %s: %s, route %s, risk %s, confidence %.2f (%s). %s".formatted(
                r.recommendationId(), r.classification(), r.recommendedRoute(), r.riskLevel(), r.confidence(), r.mode(), r.rationale()))
                .orElse("No validated recommendation has been produced for this case yet.");
        List<String> missing = rec.map(r -> r.missingInformation()).orElse(List.of());
        String brief = """
                Case %s | issue %s | transaction %s %s %s | merchant %s | channel %s
                <customer_statement>%s</customer_statement>
                EVIDENCE:
                %s
                CITABLE POLICY PASSAGES:
                %s
                %s
                MISSING INFORMATION: %s
                TOOL TRACES: %s"""
                .formatted(caseId, facts.issueType(), facts.transactionRef(), facts.amount().toPlainString(), facts.currency(),
                        facts.maskedMerchant(), facts.paymentChannel(), escape(facts.customerStatement()),
                        String.join("\n", evidenceLines),
                        citations.isEmpty() ? "none" : citations.stream().map(c -> "<policy_passage chunkId=\"%s\">%s</policy_passage>"
                                .formatted(c.getChunkId(), escape(c.getPassage()))).collect(Collectors.joining("\n")),
                        recommendationLine, missing.isEmpty() ? "none" : String.join("; ", missing),
                        traces.stream().map(t -> t.getTraceId() + " " + t.getToolName() + " " + t.getStatus()).collect(Collectors.joining(", ")));
        FollowUpContext ctx = new FollowUpContext(caseId, question, brief, Set.copyOf(known),
                new FollowUpContext.FollowUpFacts(evidenceLines, citationLines, missing, recommendationLine));

        ProcessorMode mode = registry.resolve(modeOverride);
        FraudCaseProcessor processor = registry.get(mode).available() ? registry.get(mode) : registry.rules();
        String answer;
        try {
            answer = processor.answerFollowUp(ctx);
        } catch (RuntimeException e) {
            log.warn("Follow-up model call failed mode={} error={}", processor.mode().key(), e.getClass().getSimpleName());
            processor = registry.rules();
            answer = processor.answerFollowUp(ctx);
        }
        if (answer == null || answer.isBlank()) {
            answer = "No answer could be produced from the recorded evidence.";
        }
        if (COMPLETED_ACTION.matcher(answer).find()) {
            answer = "The answer was withheld because it described a completed financial or customer action, which this assistant cannot perform. "
                    + "Please rephrase the question.";
        }
        List<String> refs = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Matcher m = REFERENCE.matcher(answer);
        StringBuilder cleaned = new StringBuilder();
        while (m.find()) {
            String ref = m.group(1);
            if (known.contains(ref)) {
                refs.add(ref);
                m.appendReplacement(cleaned, Matcher.quoteReplacement(m.group()));
            } else {
                removed.add(ref);
                m.appendReplacement(cleaned, "[unverified reference removed]");
            }
        }
        m.appendTail(cleaned);
        String finalAnswer = Redactor.redact(cleaned.toString(), 4000);
        audit.append(caseId, "FOLLOW_UP_ANSWERED", ActorType.USER, userId, "Follow-up answered in " + processor.mode().key()
                + " mode; " + refs.size() + " verified reference(s)" + (removed.isEmpty() ? "" : ", " + removed.size() + " removed")
                + (InputSafety.injectionIndicators(question).isEmpty() ? "" : "; instruction-like text in question treated as data"),
                question + "|" + finalAnswer);
        return new Answer(caseId, finalAnswer, processor.mode().key(), refs, removed);
    }

    public List<CaseMessageEntity> conversation(String caseId) {
        caseService.facts(caseId);
        return memory.entries(caseId);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("<", "&lt;").replace(">", "&gt;");
    }
}
