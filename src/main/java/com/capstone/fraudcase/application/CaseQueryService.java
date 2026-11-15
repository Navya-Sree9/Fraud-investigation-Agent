package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.AuditEventEntity;
import com.capstone.fraudcase.persistence.AuditEventRepository;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.EvidenceItemRepository;
import com.capstone.fraudcase.persistence.FraudCaseEntity;
import com.capstone.fraudcase.persistence.FraudCaseRepository;
import com.capstone.fraudcase.persistence.InvestigationRunEntity;
import com.capstone.fraudcase.persistence.InvestigationRunRepository;
import com.capstone.fraudcase.persistence.PolicyCitationEntity;
import com.capstone.fraudcase.persistence.PolicyCitationRepository;
import com.capstone.fraudcase.persistence.RecommendationEntity;
import com.capstone.fraudcase.persistence.RecommendationRepository;
import com.capstone.fraudcase.persistence.ReviewDispositionEntity;
import com.capstone.fraudcase.persistence.ReviewDispositionRepository;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import com.capstone.fraudcase.persistence.ToolTraceRepository;
import com.capstone.fraudcase.persistence.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.TransactionClaimRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for the API and UI. Always scoped to the latest run of one case. */
@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    public record CaseSummary(String caseId, CaseStatus status, String maskedCustomerRef, String homeRegion, String intakeChannel,
                              OffsetDateTime reportedAt, String createdBy, int currentRun, String transactionRef,
                              java.math.BigDecimal amount, String currency, OffsetDateTime occurredAt, String transactionStatus,
                              String paymentChannel, String maskedMerchant, String issueType, String customerStatement,
                              String cardPossession, boolean merchantRecognised, Set<CaseStatus> allowedNextStates,
                              boolean syntheticData, boolean reviewRequired) {
    }

    public record RecommendationView(String recommendationId, String caseId, int runNumber, String mode, String classification,
                                     String riskLevel, String recommendedRoute, double confidence, boolean humanReviewRequired,
                                     List<String> evidenceIds, List<CitationRef> policyCitations, List<String> missingInformation,
                                     List<String> ruleIds, List<String> toolTraceIds, List<String> contradictions,
                                     List<String> inputFacts, String rationale, String priority, OffsetDateTime slaTargetAt,
                                     String slaTraceId, String outputHash, OffsetDateTime createdAt) {
    }

    public record CitationRef(String documentId, String version, String section, String chunkId, double score) {
    }

    private final FraudCaseRepository cases;
    private final TransactionClaimRepository claims;
    private final EvidenceItemRepository evidence;
    private final ToolTraceRepository traces;
    private final RecommendationRepository recommendations;
    private final PolicyCitationRepository citations;
    private final ReviewDispositionRepository reviews;
    private final AuditEventRepository auditEvents;
    private final InvestigationRunRepository runs;
    private final ObjectMapper mapper;

    public CaseQueryService(FraudCaseRepository cases, TransactionClaimRepository claims, EvidenceItemRepository evidence,
                            ToolTraceRepository traces, RecommendationRepository recommendations, PolicyCitationRepository citations,
                            ReviewDispositionRepository reviews, AuditEventRepository auditEvents, InvestigationRunRepository runs,
                            ObjectMapper mapper) {
        this.cases = cases;
        this.claims = claims;
        this.evidence = evidence;
        this.traces = traces;
        this.recommendations = recommendations;
        this.citations = citations;
        this.reviews = reviews;
        this.auditEvents = auditEvents;
        this.runs = runs;
        this.mapper = mapper;
    }

    public List<CaseSummary> list() {
        return cases.findAllByOrderByCreatedAtDesc().stream().map(this::summary).toList();
    }

    public CaseSummary summary(String caseId) {
        return summary(load(caseId));
    }

    private CaseSummary summary(FraudCaseEntity c) {
        TransactionClaimEntity t = claims.findByCaseId(c.getCaseId()).orElseThrow(() -> new CaseNotFoundException(c.getCaseId()));
        return new CaseSummary(c.getCaseId(), c.getStatus(), c.getMaskedCustomerRef(), c.getHomeRegion(), c.getIntakeChannel().name(),
                c.getReportedAt(), c.getCreatedBy(), c.getCurrentRun(), t.getTransactionRef(), t.getAmount(), t.getCurrency(),
                t.getOccurredAt(), t.getTransactionStatus().name(), t.getPaymentChannel().name(), t.getMaskedMerchant(),
                t.getIssueType().name(), t.getCustomerStatement(), t.getCardPossession().name(), t.isMerchantRecognised(),
                CaseStateMachine.nextStates(c.getStatus()), c.isSyntheticDataConfirmed(), c.isReviewRequired());
    }

    public Optional<InvestigationRunEntity> latestRun(String caseId) {
        FraudCaseEntity c = load(caseId);
        return runs.findByCaseIdAndRunNumber(caseId, c.getCurrentRun());
    }

    public List<EvidenceItemEntity> evidence(String caseId) {
        return evidence.findByCaseIdAndRunNumberOrderByEvidenceIdAsc(caseId, load(caseId).getCurrentRun());
    }

    public List<ToolTraceEntity> traces(String caseId) {
        return traces.findByCaseIdAndRunNumberOrderByStartedAtAscTraceIdAsc(caseId, load(caseId).getCurrentRun());
    }

    public List<PolicyCitationEntity> citations(String caseId) {
        return citations.findByCaseIdAndRunNumberOrderByScoreDesc(caseId, load(caseId).getCurrentRun());
    }

    public List<ReviewDispositionEntity> reviews(String caseId) {
        load(caseId);
        return reviews.findByCaseIdOrderByRecordedAtAsc(caseId);
    }

    public List<AuditEventEntity> audit(String caseId) {
        load(caseId);
        return auditEvents.findByCaseIdOrderBySequenceNoAsc(caseId);
    }

    public Optional<RecommendationView> recommendation(String caseId) {
        FraudCaseEntity c = load(caseId);
        return recommendations.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .filter(r -> r.getRunNumber() == c.getCurrentRun()).findFirst().map(this::view);
    }

    private RecommendationView view(RecommendationEntity r) {
        List<CitationRef> refs = citations.findByCaseIdAndRunNumberOrderByScoreDesc(r.getCaseId(), r.getRunNumber()).stream()
                .filter(p -> p.isCited() && r.getRecommendationId().equals(p.getRecommendationId()))
                .map(p -> new CitationRef(p.getDocumentId(), p.getVersion(), p.getSection(), p.getChunkId(), p.getScore())).toList();
        return new RecommendationView(r.getRecommendationId(), r.getCaseId(), r.getRunNumber(), r.getMode(), r.getClassification().name(),
                r.getRiskLevel().name(), r.getRoute().name(), r.getConfidence(), r.isHumanReviewRequired(), list(r.getEvidenceIdsJson()),
                refs, list(r.getMissingInfoJson()), list(r.getRuleIdsJson()), list(r.getToolTraceIdsJson()),
                list(r.getContradictionsJson()), list(r.getInputFactsJson()), r.getRationale(), r.getPriority(), r.getSlaTargetAt(),
                r.getSlaTraceId(), r.getOutputHash(), r.getCreatedAt());
    }

    private List<String> list(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private FraudCaseEntity load(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    }
}
