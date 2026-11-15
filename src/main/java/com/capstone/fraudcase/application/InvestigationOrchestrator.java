package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.EvidenceView;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.FraudCaseRecommendation;
import com.capstone.fraudcase.agent.common.PlanStep;
import com.capstone.fraudcase.agent.common.RecommendationValidationException;
import com.capstone.fraudcase.agent.common.RecommendationValidator;
import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.Hashing;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Severity;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import com.capstone.fraudcase.knowledge.PolicyRetrievalResult;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.observability.Redactor;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.IdGenerator;
import com.capstone.fraudcase.persistence.InvestigationRunEntity;
import com.capstone.fraudcase.persistence.InvestigationRunRepository;
import com.capstone.fraudcase.persistence.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.RecommendationEntity;
import com.capstone.fraudcase.persistence.RecommendationRepository;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import com.capstone.fraudcase.persistence.ToolTraceRepository;
import com.capstone.fraudcase.security.InputSafety;
import com.capstone.fraudcase.tools.BoundCaseToolbox;
import com.capstone.fraudcase.tools.EvidenceTools;
import com.capstone.fraudcase.tools.RunScope;
import com.capstone.fraudcase.tools.ToolGateway;
import com.capstone.fraudcase.tools.ToolNames;
import com.capstone.fraudcase.tools.ToolResults.CaseSla;
import com.capstone.fraudcase.tools.ToolResults.PolicyEvidence;
import com.capstone.fraudcase.tools.ToolUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Bounded plan -> act -> retrieve -> assess -> recommend loop (guide section 2.1). The application selects and runs
 * the planned tools, retrieves policy, invokes the configured processor, validates its output and owns all state
 * transitions. Any model failure or invalid output falls back to the deterministic rules processor; unexpected
 * errors leave the case INCOMPLETE for manual handling.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    public record Outcome(String caseId, int runNumber, CaseStatus status, String effectiveMode, String recommendationId,
                          String fallbackReason) {
    }

    private final FraudCaseService caseService;
    private final CaseStateService state;
    private final InvestigationPlanner planner;
    private final EvidenceMapper evidenceMapper;
    private final EvidenceTools tools;
    private final ToolGateway gateway;
    private final PolicyRetriever retriever;
    private final ProcessorRegistry registry;
    private final RecommendationValidator validator;
    private final IdGenerator ids;
    private final AuditWriter audit;
    private final EvidenceItemRepository evidenceRepo;
    private final InvestigationRunRepository runs;
    private final PolicyCitationRepository citations;
    private final RecommendationRepository recommendations;
    private final ToolTraceRepository traces;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final CapstoneProperties properties;

    public InvestigationOrchestrator(FraudCaseService caseService, CaseStateService state, InvestigationPlanner planner,
                                     EvidenceMapper evidenceMapper, EvidenceTools tools, ToolGateway gateway,
                                     PolicyRetriever retriever, ProcessorRegistry registry, RecommendationValidator validator,
                                     IdGenerator ids, AuditWriter audit, EvidenceItemRepository evidenceRepo,
                                     InvestigationRunRepository runs, PolicyCitationRepository citations,
                                     RecommendationRepository recommendations, ToolTraceRepository traces,
                                     ObjectMapper mapper, MeterRegistry meters, CapstoneProperties properties) {
        this.caseService = caseService;
        this.state = state;
        this.planner = planner;
        this.evidenceMapper = evidenceMapper;
        this.tools = tools;
        this.gateway = gateway;
        this.retriever = retriever;
        this.registry = registry;
        this.validator = validator;
        this.ids = ids;
        this.audit = audit;
        this.evidenceRepo = evidenceRepo;
        this.runs = runs;
        this.citations = citations;
        this.recommendations = recommendations;
        this.traces = traces;
        this.mapper = mapper;
        this.meters = meters;
        this.properties = properties;
    }

    public Outcome investigate(String caseId, String actorId, String modeOverride) {
        ProcessorMode requested = registry.resolve(modeOverride);
        String correlationId = MDC.get("correlationId") != null ? MDC.get("correlationId") : UUID.randomUUID().toString();
        String previousCorrelation = MDC.get("correlationId");
        MDC.put("correlationId", correlationId);
        long started = System.nanoTime();
        int runNumber = state.startEnrichment(caseId, actorId);
        InvestigationRunEntity run = runs.save(new InvestigationRunEntity(ids.runId(), caseId, runNumber, correlationId,
                requested.key(), actorId, now()));
        audit.append(caseId, "INVESTIGATION_STARTED", ActorType.USER, actorId,
                "Run " + runNumber + " started; requested mode " + requested.key(), run.getRunId());
        try {
            return execute(caseId, actorId, requested, run);
        } catch (RuntimeException e) {
            log.error("Investigation failed case={} run={} error={}", caseId, runNumber, e.getClass().getSimpleName());
            run.complete("FAILED", null, "Unexpected error (" + e.getClass().getSimpleName() + ")", null, now());
            runs.save(run);
            audit.append(caseId, "INVESTIGATION_FAILED", ActorType.SYSTEM, "orchestrator",
                    "Investigation failed safely (" + e.getClass().getSimpleName() + "); case set to INCOMPLETE for manual handling", e.getClass().getName());
            state.transition(caseId, CaseStatus.INCOMPLETE, ActorType.SYSTEM, "orchestrator", "investigation error");
            return new Outcome(caseId, runNumber, CaseStatus.INCOMPLETE, null, null, "Investigation error; manual review required");
        } finally {
            meters.timer("fraud.investigation.duration", "mode", requested.key()).record(Duration.ofNanos(System.nanoTime() - started));
            if (previousCorrelation == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previousCorrelation);
            }
        }
    }

    private Outcome execute(String caseId, String actorId, ProcessorMode requested, InvestigationRunEntity run) {
        int runNumber = run.getRunNumber();
        CaseFacts facts = caseService.facts(caseId);
        InvestigationPlanner.Plan plan = planner.plan(facts);
        run.setPlan(plan.goal(), toJson(plan.steps()));
        runs.save(run);
        audit.append(caseId, "PLAN_CREATED", ActorType.SYSTEM, "planner", "Bounded plan with " + plan.steps().size() + " steps: "
                + plan.steps().stream().map(PlanStep::toolName).collect(Collectors.joining(", ")), toJson(plan.steps()));

        RunScope scope = new RunScope(caseId, runNumber, run.getCorrelationId(), facts);
        List<EvidenceView> evidence = new ArrayList<>();
        List<String> securityFlags = new ArrayList<>();
        evidence.add(saveEvidence(caseId, runNumber, EvidenceType.CUSTOMER_STATEMENT, Severity.INFO,
                "Customer claim (unverified): \"" + abbreviate(facts.customerStatement(), 280) + "\" | merchantRecognised="
                        + facts.merchantRecognised() + ", cardPossession=" + facts.cardPossession(),
                ProvenanceType.USER_INTAKE, "INTAKE:CLM-" + caseId, true));
        List<String> indicators = InputSafety.injectionIndicators(facts.customerStatement());
        if (!indicators.isEmpty()) {
            String flag = "Customer statement contains instruction-like text (" + String.join("; ", indicators)
                    + "). It is treated strictly as data; tool boundaries and human review are unchanged.";
            securityFlags.add(flag);
            evidence.add(saveEvidence(caseId, runNumber, EvidenceType.SECURITY_FLAG, Severity.MEDIUM, flag, ProvenanceType.SYSTEM,
                    "INPUT_SAFETY", true));
            audit.append(caseId, "PROMPT_INJECTION_FLAGGED", ActorType.SYSTEM, "input-safety", "Instruction-like text detected in customer statement; treated as data", facts.customerStatement());
        }

        List<ToolEnvelope> outcomes = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            Supplier<?> call = switch (step.toolName()) {
                case ToolNames.TRANSACTION -> () -> tools.getTransactionDetails(facts);
                case ToolNames.DEVICE -> () -> tools.getDeviceSignals(facts);
                case ToolNames.LOCATION -> () -> tools.compareRecentLocations(facts);
                case ToolNames.MERCHANT -> () -> tools.getMerchantRiskProfile(facts);
                case ToolNames.PRIOR_DISPUTES -> () -> tools.getPriorDisputes(facts);
                case ToolNames.VELOCITY -> () -> tools.getTransactionVelocity(facts);
                case ToolNames.SCREENING -> () -> tools.runComplianceScreening(facts);
                default -> null;
            };
            if (call == null) {
                continue;
            }
            ToolEnvelope envelope = gateway.execute(scope, step.toolName(), "APPLICATION", arguments(step.toolName(), facts), call);
            EvidenceMapper.Draft draft = evidenceMapper.fromTool(envelope);
            EvidenceView view = saveEvidence(caseId, runNumber, draft.type(), draft.severity(), draft.summary(), draft.provenance(),
                    draft.sourceRef(), draft.available());
            evidence.add(view);
            scope.linkEvidence(step.toolName(), view.evidenceId());
            outcomes.add(new ToolEnvelope(envelope.toolResultId(), envelope.toolName(), envelope.invokedBy(), envelope.status(),
                    envelope.observedAt(), envelope.origin(), envelope.summary(), envelope.result(), List.of(view.evidenceId()), envelope.durationMs()));
        }

        int topK = properties.retrieval().topK();
        double threshold = properties.retrieval().similarityThreshold();
        String query = policyQuery(facts, evidence);
        AtomicReference<PolicyRetrievalResult> retrieval = new AtomicReference<>(PolicyRetrievalResult.unavailable(query, topK, threshold));
        ToolEnvelope policyEnvelope = gateway.execute(scope, ToolNames.POLICY, "APPLICATION", Map.of("query", query, "topK", topK, "threshold", threshold), () -> {
            PolicyRetrievalResult r = retriever.retrieve(query, topK, threshold);
            retrieval.set(r);
            if (r.status() == PolicyRetrievalResult.Status.UNAVAILABLE) {
                throw new ToolUnavailableException(ToolStatus.FAILED, "Policy store unavailable");
            }
            return new PolicyEvidence(query, topK, threshold, !r.hasCitations(), r.candidates());
        });
        outcomes.add(policyEnvelope);
        PolicyRetrievalResult policy = policyEnvelope.available() ? retrieval.get() : PolicyRetrievalResult.unavailable(query, topK, threshold);
        List<PolicyCitationEntity> candidateRows = policy.candidates().stream()
                .map(p -> citations.save(new PolicyCitationEntity(caseId, runNumber, p.documentId(), p.title(), p.version(),
                        p.effectiveDate(), p.section(), p.chunkId(), p.score(), p.aboveThreshold(), p.passage())))
                .toList();
        run.setRetrieval(query, policy.status().name(), topK, threshold);
        runs.save(run);
        audit.append(caseId, policy.hasCitations() ? "POLICY_RETRIEVED" : "POLICY_ABSTAINED", ActorType.SYSTEM, "retriever",
                "%d candidate passage(s), %d above threshold %.2f (status %s)".formatted(policy.candidates().size(), policy.cited().size(),
                        threshold, policy.status()), query);

        BoundCaseToolbox toolbox = new BoundCaseToolbox(gateway, tools, retriever, scope, topK, threshold);
        FraudCaseContext context = new FraudCaseContext(caseId, runNumber, run.getCorrelationId(), facts, plan.goal(), plan.steps(),
                List.copyOf(evidence), List.copyOf(outcomes), policy, List.copyOf(securityFlags), toolbox);

        FraudCaseProcessor processor = registry.get(requested);
        String fallbackReason = null;
        String effectiveMode;
        Long modelLatency = null;
        FraudCaseRecommendation validated;
        if (!processor.available()) {
            fallbackReason = requested.key() + " processor unavailable (model not configured)";
            processor = registry.rules();
        }
        try {
            long t0 = System.nanoTime();
            FraudCaseRecommendation raw = processor.analyze(context);
            modelLatency = processor.mode() == ProcessorMode.RULES ? null : Duration.ofNanos(System.nanoTime() - t0).toMillis();
            if (modelLatency != null) {
                meters.timer("fraud.model.latency", "mode", processor.mode().key()).record(Duration.ofMillis(modelLatency));
            }
            validated = validator.validate(raw, validationScope(context, toolbox));
            effectiveMode = fallbackReason == null ? processor.mode().key() : "rules (fallback from " + requested.key() + ")";
        } catch (RecommendationValidationException e) {
            if (processor.mode() == ProcessorMode.RULES) {
                throw e;
            }
            meters.counter("fraud.recommendation.rejected", "mode", processor.mode().key()).increment();
            fallbackReason = "Model output rejected by validation: " + abbreviate(String.join("; ", e.errors()), 300);
            audit.append(caseId, "MODEL_OUTPUT_REJECTED", ActorType.MODEL, processor.mode().key(), Redactor.redact(fallbackReason, 600), String.join("|", e.errors()));
            validated = rulesFallback(context, toolbox);
            effectiveMode = "rules (fallback from " + requested.key() + ")";
        } catch (RuntimeException e) {
            if (processor.mode() == ProcessorMode.RULES) {
                throw e;
            }
            meters.counter("fraud.model.failure", "mode", processor.mode().key()).increment();
            log.warn("Model call failed mode={} error={}", processor.mode().key(), e.getClass().getSimpleName());
            fallbackReason = "Model unavailable (" + e.getClass().getSimpleName() + ")";
            audit.append(caseId, "MODEL_UNAVAILABLE", ActorType.SYSTEM, "orchestrator", fallbackReason + "; deterministic rules applied", e.getClass().getName());
            validated = rulesFallback(context, toolbox);
            effectiveMode = "rules (fallback from " + requested.key() + ")";
        }
        if (fallbackReason != null && !properties.fallbackToRulesOnModelFailure() && effectiveMode.startsWith("rules (fallback")) {
            run.complete("INCOMPLETE", null, fallbackReason, modelLatency, now());
            runs.save(run);
            state.transition(caseId, CaseStatus.INCOMPLETE, ActorType.SYSTEM, "orchestrator", "model unavailable; manual review");
            return new Outcome(caseId, runNumber, CaseStatus.INCOMPLETE, null, null, fallbackReason);
        }

        Set<String> traceIds = new LinkedHashSet<>(validated.toolTraceIds());
        traceIds.addAll(toolbox.traceIds());
        validated = validated.withToolTraceIds(List.copyOf(traceIds));

        FraudCaseRecommendation finalRec = validated;
        ToolEnvelope sla = gateway.execute(scope, ToolNames.SLA, "APPLICATION",
                Map.of("route", finalRec.recommendedRoute().name(), "riskLevel", finalRec.riskLevel().name()),
                () -> tools.calculateCaseSla(finalRec.recommendedRoute(), finalRec.riskLevel(), facts.reportedAt()));
        String recommendationId = persistRecommendation(caseId, runNumber, effectiveMode, validated, candidateRows, toolbox, scope, sla);

        CaseStatus next = validated.classification() == Classification.INSUFFICIENT_EVIDENCE ? CaseStatus.INCOMPLETE : CaseStatus.READY_FOR_REVIEW;
        run.complete("COMPLETED", effectiveMode, fallbackReason, modelLatency, now());
        runs.save(run);
        state.transition(caseId, next, ActorType.SYSTEM, "orchestrator", "recommendation " + recommendationId + " ready; human review required");
        return new Outcome(caseId, runNumber, next, effectiveMode, recommendationId, fallbackReason);
    }

    private FraudCaseRecommendation rulesFallback(FraudCaseContext context, BoundCaseToolbox toolbox) {
        return validator.validate(registry.rules().analyze(context), validationScope(context, toolbox));
    }

    private RecommendationValidator.Scope validationScope(FraudCaseContext context, BoundCaseToolbox toolbox) {
        Set<String> evidenceIds = context.evidence().stream().map(EvidenceView::evidenceId).collect(Collectors.toSet());
        Set<String> traceIds = traces.findByCaseIdAndRunNumberOrderByStartedAtAscTraceIdAsc(context.caseId(), context.runNumber())
                .stream().map(ToolTraceEntity::getTraceId).collect(Collectors.toSet());
        List<PolicyPassage> citable = new ArrayList<>(context.policy().cited());
        citable.addAll(toolbox.extraCitablePassages());
        return new RecommendationValidator.Scope(context.caseId(), evidenceIds, traceIds, citable, citable.isEmpty());
    }

    private String persistRecommendation(String caseId, int runNumber, String mode, FraudCaseRecommendation rec,
                                         List<PolicyCitationEntity> candidateRows, BoundCaseToolbox toolbox, RunScope scope,
                                         ToolEnvelope sla) {
        String recommendationId = ids.recommendationId();
        String json = toJson(rec);
        RecommendationEntity entity = new RecommendationEntity(recommendationId, caseId, runNumber, mode, rec.classification(),
                rec.recommendedRoute(), rec.riskLevel(), rec.confidence(), rec.rationale(), toJson(rec.evidenceIds()),
                toJson(rec.missingInformation()), toJson(rec.ruleIds()), toJson(rec.toolTraceIds()), toJson(rec.contradictions()),
                toJson(rec.inputFacts()), Hashing.sha256(json), now());
        CaseSla slaResult = sla.resultAs(CaseSla.class);
        if (slaResult != null) {
            entity.setSla(slaResult.priority(), slaResult.targetAt(), sla.toolResultId());
        }
        recommendations.save(entity);
        for (FraudCaseRecommendation.PolicyCitationRef ref : rec.policyCitations()) {
            PolicyCitationEntity row = candidateRows.stream().filter(c -> c.getChunkId().equals(ref.chunkId())).findFirst()
                    .orElseGet(() -> toolbox.extraCitablePassages().stream().filter(p -> p.chunkId().equals(ref.chunkId())).findFirst()
                            .map(p -> new PolicyCitationEntity(caseId, runNumber, p.documentId(), p.title(), p.version(), p.effectiveDate(),
                                    p.section(), p.chunkId(), p.score(), true, p.passage()))
                            .orElseThrow());
            row.markCited(recommendationId);
            citations.save(row);
        }
        audit.append(caseId, "RECOMMENDATION_RECORDED", "rules".equals(mode) || mode.startsWith("rules") ? ActorType.RULES_ENGINE : ActorType.MODEL, mode,
                "%s %s -> %s, risk %s, confidence %.2f, %d citation(s), %d missing item(s); human review required".formatted(recommendationId,
                        rec.classification(), rec.recommendedRoute(), rec.riskLevel(), rec.confidence(), rec.policyCitations().size(),
                        rec.missingInformation().size()), json);
        return recommendationId;
    }

    private EvidenceView saveEvidence(String caseId, int runNumber, EvidenceType type, Severity severity, String summary,
                                      ProvenanceType provenance, String sourceRef, boolean available) {
        String id = ids.evidenceId();
        OffsetDateTime at = now();
        String safeSummary = Redactor.redact(summary, 600);
        evidenceRepo.save(new EvidenceItemEntity(id, caseId, runNumber, type, severity, safeSummary, provenance, sourceRef, available, null, at));
        return new EvidenceView(id, type, severity, safeSummary, provenance, sourceRef, available, at);
    }

    /** Builds the retrieval query from the case type, evidence indicators and investigation goal (RAG step 5). */
    static String policyQuery(CaseFacts facts, List<EvidenceView> evidence) {
        IssueType issue = facts.issueType();
        StringBuilder q = new StringBuilder();
        if (issue.deniesAuthorisation()) {
            q.append("Customer denies recognising the merchant of a ").append(facts.paymentChannel() == com.capstone.fraudcase.domain.PaymentChannel.CARD_NOT_PRESENT
                    ? "card-not-present online" : facts.paymentChannel().name().toLowerCase().replace('_', ' ')).append(" transaction; initial fraud routing.");
        } else if (issue == IssueType.DUPLICATE_CHARGE) {
            q.append("Customer charged twice; duplicate payment fingerprint and duplicate posting routing.");
        } else if (issue.isMerchantServiceIssue()) {
            q.append("Customer recognises the merchant and disputes ")
                    .append(switch (issue) {
                        case GOODS_NOT_RECEIVED -> "goods or services not received (fulfilment)";
                        case CANCELLED_NOT_REFUNDED -> "a cancelled order or subscription not refunded";
                        default -> "an incorrect amount charged";
                    }).append("; merchant dispute routing.");
        } else {
            q.append(abbreviate(facts.customerStatement(), 300));
        }
        for (EvidenceView e : evidence) {
            if (!e.available()) {
                q.append(" Source system unavailable for ").append(e.type().name().toLowerCase()).append('.');
            } else if (e.severity() == Severity.HIGH && e.type() == EvidenceType.DEVICE) {
                q.append(" Device not previously observed for the customer.");
            } else if (e.severity() == Severity.HIGH && e.type() == EvidenceType.LOCATION) {
                q.append(" Location inconsistent with recent trusted activity.");
            } else if (e.severity() == Severity.HIGH && e.type() == EvidenceType.COMPLIANCE) {
                q.append(" Compliance screening returned a confirmed synthetic sanctions list match; AML escalation routing.");
            } else if (e.severity() == Severity.MEDIUM && e.type() == EvidenceType.COMPLIANCE) {
                q.append(" Compliance screening returned a potential list match needing adjudication.");
            } else if (e.type() == EvidenceType.TRANSACTION && e.summary().contains("not available")) {
                q.append(" Authentication challenge outcome unavailable.");
            }
        }
        return q.toString().trim();
    }

    private static Map<String, ?> arguments(String toolName, CaseFacts facts) {
        return switch (toolName) {
            case ToolNames.TRANSACTION -> Map.of("transactionRef", facts.transactionRef());
            case ToolNames.MERCHANT -> Map.of("maskedMerchant", facts.maskedMerchant());
            default -> Map.of("maskedCustomerRef", facts.maskedCustomerRef(), "transactionRef", facts.transactionRef());
        };
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Serialisation failed", e);
        }
    }

    private static String abbreviate(String text, int max) {
        return text == null ? "" : text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
