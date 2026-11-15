package com.capstone.fraudcase.application;

import com.capstone.fraudcase.api.dto.ReviewRequest;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Disposition;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.FraudCaseEntity;
import com.capstone.fraudcase.persistence.FraudCaseRepository;
import com.capstone.fraudcase.persistence.IdGenerator;
import com.capstone.fraudcase.persistence.RecommendationEntity;
import com.capstone.fraudcase.persistence.RecommendationRepository;
import com.capstone.fraudcase.persistence.ReviewDispositionEntity;
import com.capstone.fraudcase.persistence.ReviewDispositionRepository;
import com.capstone.fraudcase.security.InputSafety;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the human disposition (US-06). The reviewer's decision, not the model's, sets the final route or closes the
 * case. No disposition triggers a financial, account-control or customer-contact action.
 */
@Service
public class ReviewService {

    public static final int MIN_REASON_LENGTH = 15;
    private static final Set<Disposition> REVIEW_DISPOSITIONS = EnumSet.of(Disposition.ACCEPT_RECOMMENDATION, Disposition.REROUTE,
            Disposition.ESCALATE, Disposition.REQUEST_INFORMATION, Disposition.CLOSE_OVERRIDE);

    public record ReviewOutcome(String reviewId, CaseStatus status, Route finalRoute) {
    }

    private final FraudCaseRepository cases;
    private final RecommendationRepository recommendations;
    private final ReviewDispositionRepository reviews;
    private final CaseStateService state;
    private final IdGenerator ids;
    private final AuditWriter audit;
    private final ObjectMapper mapper;

    public ReviewService(FraudCaseRepository cases, RecommendationRepository recommendations, ReviewDispositionRepository reviews,
                         CaseStateService state, IdGenerator ids, AuditWriter audit, ObjectMapper mapper) {
        this.cases = cases;
        this.recommendations = recommendations;
        this.reviews = reviews;
        this.state = state;
        this.ids = ids;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** READY_FOR_REVIEW -> UNDER_REVIEW: a dispute reviewer takes ownership of the recommendation. */
    @Transactional
    public void startReview(String caseId, String reviewerId) {
        if (recommendations.findByCaseIdOrderByCreatedAtDesc(caseId).isEmpty()) {
            throw new ValidationFailedException(Map.of("disposition", "no validated recommendation is available to review"));
        }
        state.transition(caseId, CaseStatus.UNDER_REVIEW, ActorType.USER, reviewerId, "review started by " + reviewerId);
    }

    @Transactional
    public ReviewOutcome submit(String caseId, ReviewRequest request, String reviewerId) {
        FraudCaseEntity c = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        RecommendationEntity rec = recommendations.findByCaseIdOrderByCreatedAtDesc(caseId).stream().findFirst().orElse(null);
        Map<String, String> errors = new LinkedHashMap<>();
        String reason = request.reason() == null ? "" : InputSafety.normalise(request.reason());
        if (request.disposition() == null) {
            errors.put("disposition", "select a disposition");
        }
        if (reason.length() < MIN_REASON_LENGTH) {
            errors.put("reason", "a reviewer rationale of at least " + MIN_REASON_LENGTH + " characters is required");
        } else if (reason.length() > 2000) {
            errors.put("reason", "rationale must be at most 2000 characters");
        }
        InputSafety.violations("reason", reason).forEach(m -> errors.putIfAbsent("reason", m));
        if (rec != null && !missing(rec).isEmpty() && !request.missingInfoAcknowledged()) {
            errors.put("missingInfoAcknowledged", "acknowledge the listed missing information before submitting");
        }
        CaseStatus status = c.getStatus();
        Disposition d = request.disposition();
        if (d != null) {
            boolean reviewable = status == CaseStatus.READY_FOR_REVIEW || status == CaseStatus.UNDER_REVIEW;
            boolean closable = status == CaseStatus.ROUTED || status == CaseStatus.INCOMPLETE;
            if (reviewable && !REVIEW_DISPOSITIONS.contains(d) || closable && d != Disposition.CLOSE || !reviewable && !closable) {
                errors.put("disposition", d + " is not allowed while the case is " + status);
            }
            if (d == Disposition.REROUTE && (request.selectedRoute() == null || rec != null && request.selectedRoute() == rec.getRoute())) {
                errors.put("selectedRoute", "choose a route different from the recommended route");
            }
            if (reviewable && rec == null) {
                errors.put("disposition", "no validated recommendation is available to review");
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        if (status == CaseStatus.READY_FOR_REVIEW) {
            state.transition(caseId, CaseStatus.UNDER_REVIEW, ActorType.USER, reviewerId, "review started");
        }
        Route finalRoute = switch (d) {
            case ACCEPT_RECOMMENDATION -> rec.getRoute();
            case REROUTE -> request.selectedRoute();
            case ESCALATE -> Route.FRAUD_INVESTIGATION;
            case REQUEST_INFORMATION -> Route.MANUAL_REVIEW;
            case CLOSE_OVERRIDE, CLOSE -> null;
        };
        CaseStatus next = finalRoute == null ? CaseStatus.CLOSED : CaseStatus.ROUTED;
        String reviewId = ids.reviewId();
        reviews.save(new ReviewDispositionEntity(reviewId, caseId, rec == null ? null : rec.getRecommendationId(), reviewerId, d,
                finalRoute, reason, request.missingInfoAcknowledged(), next, OffsetDateTime.now(ZoneOffset.UTC)));
        audit.append(caseId, "REVIEW_RECORDED", ActorType.USER, reviewerId,
                "%s by %s: %s%s (recommendation %s)".formatted(reviewId, reviewerId, d, finalRoute == null ? "" : " -> " + finalRoute,
                        rec == null ? "n/a" : rec.getRecommendationId() + " " + rec.getRoute()), reason);
        state.transition(caseId, next, ActorType.USER, reviewerId, d.name());
        return new ReviewOutcome(reviewId, next, finalRoute);
    }

    private List<String> missing(RecommendationEntity rec) {
        try {
            return mapper.readValue(rec.getMissingInfoJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
