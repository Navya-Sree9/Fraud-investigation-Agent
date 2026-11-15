package com.capstone.fraudcase.web;

import com.capstone.fraudcase.agent.common.PlanStep;
import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.application.FollowUpService;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.application.ProcessorRegistry;
import com.capstone.fraudcase.application.ReviewService;
import com.capstone.fraudcase.application.ValidationFailedException;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.CardPossession;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.Disposition;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.IntakeChannel;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.domain.PaymentChannel;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.TransactionStatus;
import com.capstone.fraudcase.knowledge.PolicyIngestionService;
import com.capstone.fraudcase.persistence.AuditEventRepository;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import com.capstone.fraudcase.security.Roles;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Thymeleaf screens: intake, investigation, evidence, citations, recommendation, review and audit (guide section 7). */
@Controller
public class CaseWebController {

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;
    private final CaseQueryService queries;
    private final ReviewService reviews;
    private final FollowUpService followUps;
    private final ProcessorRegistry registry;
    private final PolicyIngestionService policies;
    private final AuditEventRepository auditEvents;
    private final AuditWriter auditWriter;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final CapstoneProperties properties;

    public CaseWebController(FraudCaseService caseService, InvestigationOrchestrator orchestrator, CaseQueryService queries,
                             ReviewService reviews, FollowUpService followUps, ProcessorRegistry registry,
                             PolicyIngestionService policies, AuditEventRepository auditEvents, AuditWriter auditWriter,
                             Validator validator, ObjectMapper mapper, CapstoneProperties properties) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
        this.queries = queries;
        this.reviews = reviews;
        this.followUps = followUps;
        this.registry = registry;
        this.policies = policies;
        this.auditEvents = auditEvents;
        this.auditWriter = auditWriter;
        this.validator = validator;
        this.mapper = mapper;
        this.properties = properties;
    }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("configuredMode", registry.configuredMode().key());
        model.addAttribute("llmModel", properties.llm().model());
        model.addAttribute("modeAvailability", registry.availability());
    }

    @GetMapping("/")
    String home() {
        return "redirect:/cases";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/cases")
    String list(Model model) {
        model.addAttribute("cases", queries.list());
        return "cases/list";
    }

    @GetMapping("/cases/new")
    String newCase(Model model) {
        model.addAttribute("form", new CaseForm());
        intakeOptions(model);
        return "cases/new";
    }

    @PostMapping("/cases")
    String create(@ModelAttribute("form") CaseForm form, @RequestParam(defaultValue = "create") String action, Authentication principal,
                  Model model, RedirectAttributes redirect) {
        Map<String, String> errors = new LinkedHashMap<>();
        CreateCaseRequest request = toRequest(form, errors);
        if (request != null) {
            for (ConstraintViolation<CreateCaseRequest> v : validator.validate(request)) {
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage());
            }
        }
        if (errors.isEmpty()) {
            try {
                FraudCaseService.IntakeResult intake = caseService.createCase(request, principal.getName());
                String caseId = intake.caseId();
                boolean investigator = principal.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + Roles.INVESTIGATOR));
                if (intake.replayed()) {
                    redirect.addFlashAttribute("notice", "An identical case already exists (" + caseId + ") — intake deduplicated, opened the existing case.");
                } else if ("investigate".equals(action) && investigator) {
                    InvestigationOrchestrator.Outcome outcome = orchestrator.investigate(caseId, principal.getName(), null);
                    redirect.addFlashAttribute("notice", outcomeNotice(outcome));
                } else {
                    redirect.addFlashAttribute("notice", "Case " + caseId + " created with status NEW. A fraud investigator can now start enrichment.");
                }
                return "redirect:/cases/" + caseId;
            } catch (ValidationFailedException e) {
                errors.putAll(e.fieldErrors());
            }
        }
        model.addAttribute("errors", errors);
        intakeOptions(model);
        return "cases/new";
    }

    @GetMapping("/cases/{caseId}")
    String investigation(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "investigation");
        List<ToolTraceEntity> traces = queries.traces(caseId);
        model.addAttribute("traces", traces);
        model.addAttribute("journey", CaseJourney.steps(queries.summary(caseId), queries.latestRun(caseId).orElse(null), traces,
                queries.recommendation(caseId).orElse(null), queries.reviews(caseId).size(), queries.audit(caseId).size()));
        model.addAttribute("plan", queries.latestRun(caseId).map(r -> plan(r.getPlanJson())).orElse(List.of()));
        model.addAttribute("messages", followUps.conversation(caseId));
        model.addAttribute("modes", List.of("rules", "spring-ai", "langchain4j"));
        model.addAttribute("canInvestigate", CaseStateMachine.canTransition(queries.summary(caseId).status(),
                com.capstone.fraudcase.domain.CaseStatus.ENRICHING));
        return "cases/investigation";
    }

    @PostMapping("/cases/{caseId}/investigate")
    String investigate(@PathVariable String caseId, @RequestParam(required = false) String mode, Principal principal,
                       RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("notice", outcomeNotice(orchestrator.investigate(caseId, principal.getName(), mode)));
        } catch (CaseStateMachine.InvalidStateTransitionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }

    @PostMapping("/cases/{caseId}/messages")
    String ask(@PathVariable String caseId, @RequestParam String question, Principal principal, RedirectAttributes redirect) {
        try {
            FollowUpService.Answer answer = followUps.ask(caseId, question, principal.getName(), null);
            if (!answer.removedReferences().isEmpty()) {
                redirect.addFlashAttribute("notice", "Unverified references were removed from the answer: " + answer.removedReferences());
            }
        } catch (ValidationFailedException e) {
            redirect.addFlashAttribute("error", String.join("; ", e.fieldErrors().values()));
        }
        return "redirect:/cases/" + caseId + "#follow-up";
    }

    @GetMapping("/cases/{caseId}/evidence")
    String evidence(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "evidence");
        List<EvidenceItemEntity> items = queries.evidence(caseId);
        model.addAttribute("claims", items.stream().filter(e -> e.getProvenance() == ProvenanceType.USER_INTAKE).toList());
        model.addAttribute("observed", items.stream().filter(e -> e.getProvenance() == ProvenanceType.TOOL_RESULT).toList());
        model.addAttribute("flags", items.stream().filter(e -> e.getType() == EvidenceType.SECURITY_FLAG).toList());
        model.addAttribute("tracesById", queries.traces(caseId).stream().collect(Collectors.toMap(ToolTraceEntity::getTraceId, Function.identity())));
        return "cases/evidence";
    }

    @GetMapping("/cases/{caseId}/citations")
    String citations(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "citations");
        model.addAttribute("citations", queries.citations(caseId));
        model.addAttribute("unavailablePolicies", policies.documents().stream().filter(d -> !d.retrievable()).toList());
        return "cases/citations";
    }

    @GetMapping("/cases/{caseId}/recommendation")
    String recommendation(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "recommendation");
        model.addAttribute("evidenceById", queries.evidence(caseId).stream()
                .collect(Collectors.toMap(EvidenceItemEntity::getEvidenceId, Function.identity())));
        return "cases/recommendation";
    }

    @GetMapping("/cases/{caseId}/review")
    String review(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "review");
        model.addAttribute("reviews", queries.reviews(caseId));
        model.addAttribute("dispositions", Disposition.values());
        model.addAttribute("routes", Route.values());
        List<com.capstone.fraudcase.persistence.AuditEventEntity> audit = queries.audit(caseId);
        model.addAttribute("auditPreview", audit.subList(Math.max(0, audit.size() - 5), audit.size()));
        return "cases/review";
    }

    @PostMapping("/cases/{caseId}/review/start")
    String startReview(@PathVariable String caseId, Principal principal, RedirectAttributes redirect) {
        try {
            reviews.startReview(caseId, principal.getName());
            redirect.addFlashAttribute("notice", "Review started: case is now UNDER_REVIEW and assigned to " + principal.getName() + ".");
        } catch (CaseStateMachine.InvalidStateTransitionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (ValidationFailedException e) {
            redirect.addFlashAttribute("error", String.join("; ", e.fieldErrors().values()));
        }
        return "redirect:/cases/" + caseId + "/review";
    }

    @PostMapping("/cases/{caseId}/review")
    String submitReview(@PathVariable String caseId, @RequestParam(required = false) Disposition disposition,
                        @RequestParam(required = false) Route selectedRoute, @RequestParam(defaultValue = "") String reason,
                        @RequestParam(defaultValue = "false") boolean missingInfoAcknowledged,
                        @RequestParam(defaultValue = "false") boolean evidenceReviewed,
                        @RequestParam(defaultValue = "false") boolean citationsReviewed,
                        Principal principal, Model model, RedirectAttributes redirect) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!evidenceReviewed || !citationsReviewed) {
            errors.put("checklist", "confirm you reviewed the evidence and the policy citations");
        }
        if (errors.isEmpty()) {
            try {
                ReviewService.ReviewOutcome outcome = reviews.submit(caseId,
                        new ReviewRequest(disposition, selectedRoute, reason, missingInfoAcknowledged), principal.getName());
                redirect.addFlashAttribute("notice", "Review " + outcome.reviewId() + " recorded; case is now " + outcome.status()
                        + (outcome.finalRoute() == null ? "" : " (route " + outcome.finalRoute() + ")") + ". No automated action was taken.");
                return "redirect:/cases/" + caseId + "/review";
            } catch (ValidationFailedException e) {
                errors.putAll(e.fieldErrors());
            }
        }
        model.addAttribute("errors", errors);
        model.addAttribute("reasonValue", reason);
        return review(caseId, model);
    }

    @GetMapping("/cases/{caseId}/audit")
    String caseAudit(@PathVariable String caseId, Model model) {
        caseHeader(caseId, model, "audit");
        model.addAttribute("events", queries.audit(caseId));
        model.addAttribute("chainBreak", auditWriter.verifyChain());
        return "cases/audit";
    }

    @GetMapping("/audit")
    String audit(Model model) {
        model.addAttribute("events", auditEvents.findTop100ByOrderBySequenceNoDesc());
        model.addAttribute("chainBreak", auditWriter.verifyChain());
        return "audit";
    }

    @GetMapping("/policies")
    String policyLibrary(Model model) {
        model.addAttribute("documents", policies.documents());
        model.addAttribute("rejected", policies.rejected());
        model.addAttribute("topK", properties.retrieval().topK());
        model.addAttribute("threshold", properties.retrieval().similarityThreshold());
        return "policies";
    }

    private void caseHeader(String caseId, Model model, String tab) {
        CaseQueryService.CaseSummary summary = queries.summary(caseId);
        model.addAttribute("c", summary);
        model.addAttribute("lifecycle", CaseJourney.lifecycle(summary.status(), queries.audit(caseId)));
        model.addAttribute("run", queries.latestRun(caseId).orElse(null));
        model.addAttribute("rec", queries.recommendation(caseId).orElse(null));
        model.addAttribute("tab", tab);
    }

    private void intakeOptions(Model model) {
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute("channels", PaymentChannel.values());
        model.addAttribute("issueTypes", IssueType.values());
        model.addAttribute("possessions", CardPossession.values());
        model.addAttribute("intakeChannels", IntakeChannel.values());
    }

    private List<PlanStep> plan(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String outcomeNotice(InvestigationOrchestrator.Outcome o) {
        return "Run " + o.runNumber() + " finished: case is " + o.status()
                + (o.effectiveMode() == null ? "" : " (processor: " + o.effectiveMode() + ")")
                + (o.fallbackReason() == null ? "" : ". Note: " + o.fallbackReason());
    }

    private static CreateCaseRequest toRequest(CaseForm f, Map<String, String> errors) {
        BigDecimal amount = null;
        OffsetDateTime occurred = null;
        try {
            amount = f.getAmount() == null || f.getAmount().isBlank() ? null : new BigDecimal(f.getAmount().trim());
        } catch (NumberFormatException e) {
            errors.put("amount", "must be a number");
        }
        try {
            occurred = f.getOccurredAt() == null || f.getOccurredAt().isBlank() ? null
                    : LocalDateTime.parse(f.getOccurredAt()).atOffset(ZoneOffset.of(f.getUtcOffset()));
        } catch (RuntimeException e) {
            errors.put("occurredAt", "must be a valid date and time");
        }
        return new CreateCaseRequest(blankToNull(f.getMaskedCustomerRef()), blankToNull(f.getHomeRegion()), blankToNull(f.getTransactionRef()),
                amount, blankToNull(f.getCurrency()), occurred, enumOrNull(TransactionStatus.class, f.getStatus(), "status", errors),
                enumOrNull(PaymentChannel.class, f.getChannel(), "channel", errors), blankToNull(f.getMaskedMerchant()),
                enumOrNull(IssueType.class, f.getIssueType(), "issueType", errors), blankToNull(f.getCustomerStatement()),
                enumOrNull(CardPossession.class, f.getCardPossession(), "cardPossession", errors),
                f.getMerchantRecognised() == null || f.getMerchantRecognised().isBlank() ? null : Boolean.valueOf(f.getMerchantRecognised()),
                enumOrNull(IntakeChannel.class, f.getIntakeChannel(), "intakeChannel", errors),
                f.isSyntheticDataConfirmed(), f.isReviewRequired());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value, String field, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            errors.put(field, "unknown value");
            return null;
        }
    }
}
