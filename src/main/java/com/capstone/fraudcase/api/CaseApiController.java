package com.capstone.fraudcase.api;

import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.application.CaseNotFoundException;
import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.application.FollowUpService;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.application.ReviewService;
import com.capstone.fraudcase.knowledge.PolicyDocument;
import com.capstone.fraudcase.knowledge.PolicyIngestionService;
import com.capstone.fraudcase.persistence.AuditEventEntity;
import com.capstone.fraudcase.persistence.CaseMessageEntity;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.ReviewDispositionEntity;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API (guide section 5.1). Identity always comes from the authenticated principal, never from the payload. */
@RestController
@RequestMapping("/api")
public class CaseApiController {

    public record InvestigationRequest(String mode) {
    }

    public record MessageRequest(String question, String mode) {
    }

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;
    private final CaseQueryService queries;
    private final ReviewService reviews;
    private final FollowUpService followUps;
    private final PolicyIngestionService policies;

    public CaseApiController(FraudCaseService caseService, InvestigationOrchestrator orchestrator, CaseQueryService queries,
                             ReviewService reviews, FollowUpService followUps, PolicyIngestionService policies) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
        this.queries = queries;
        this.reviews = reviews;
        this.followUps = followUps;
        this.policies = policies;
    }

    @PostMapping("/cases")
    public ResponseEntity<CaseQueryService.CaseSummary> create(@Valid @RequestBody CreateCaseRequest request, Principal principal) {
        FraudCaseService.IntakeResult intake = caseService.createCase(request, principal.getName());
        URI location = URI.create("/api/cases/" + intake.caseId());
        return intake.replayed()
                ? ResponseEntity.ok().location(location).body(queries.summary(intake.caseId()))
                : ResponseEntity.created(location).body(queries.summary(intake.caseId()));
    }

    @GetMapping("/cases")
    public List<CaseQueryService.CaseSummary> list() {
        return queries.list();
    }

    @GetMapping("/cases/{caseId}")
    public CaseQueryService.CaseSummary get(@PathVariable String caseId) {
        return queries.summary(caseId);
    }

    @PostMapping("/cases/{caseId}/investigations")
    public InvestigationOrchestrator.Outcome investigate(@PathVariable String caseId,
                                                         @RequestBody(required = false) InvestigationRequest request,
                                                         Principal principal) {
        queries.summary(caseId);
        return orchestrator.investigate(caseId, principal.getName(), request == null ? null : request.mode());
    }

    @GetMapping("/cases/{caseId}/evidence")
    public List<EvidenceItemEntity> evidence(@PathVariable String caseId) {
        return queries.evidence(caseId);
    }

    @GetMapping("/cases/{caseId}/tool-traces")
    public List<ToolTraceEntity> traces(@PathVariable String caseId) {
        return queries.traces(caseId);
    }

    @GetMapping("/cases/{caseId}/recommendation")
    public CaseQueryService.RecommendationView recommendation(@PathVariable String caseId) {
        return queries.recommendation(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    @GetMapping("/cases/{caseId}/citations")
    public List<PolicyCitationEntity> citations(@PathVariable String caseId) {
        return queries.citations(caseId);
    }

    @PostMapping("/cases/{caseId}/messages")
    public FollowUpService.Answer ask(@PathVariable String caseId, @RequestBody MessageRequest request, Principal principal) {
        return followUps.ask(caseId, request.question(), principal.getName(), request.mode());
    }

    @GetMapping("/cases/{caseId}/messages")
    public List<CaseMessageEntity> messages(@PathVariable String caseId) {
        return followUps.conversation(caseId);
    }

    @PostMapping("/cases/{caseId}/reviews")
    public ReviewService.ReviewOutcome review(@PathVariable String caseId, @RequestBody ReviewRequest request, Principal principal) {
        return reviews.submit(caseId, request, principal.getName());
    }

    @PostMapping("/cases/{caseId}/reviews/start")
    public CaseQueryService.CaseSummary startReview(@PathVariable String caseId, Principal principal) {
        reviews.startReview(caseId, principal.getName());
        return queries.summary(caseId);
    }

    @GetMapping("/cases/{caseId}/reviews")
    public List<ReviewDispositionEntity> reviewHistory(@PathVariable String caseId) {
        return queries.reviews(caseId);
    }

    @GetMapping("/cases/{caseId}/audit")
    public List<AuditEventEntity> audit(@PathVariable String caseId) {
        return queries.audit(caseId);
    }

    @GetMapping("/policies")
    public Map<String, Object> policies() {
        return Map.of("documents", policies.documents().stream().map(this::policySummary).toList(), "rejected", policies.rejected());
    }

    private Map<String, Object> policySummary(PolicyDocument d) {
        return Map.of("documentId", d.documentId(), "title", d.title(), "version", d.version(), "effectiveDate", d.effectiveDate(),
                "owner", d.owner(), "availability", d.availability(), "chunks", d.chunks().size());
    }
}
