package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.Disposition;
import com.capstone.fraudcase.domain.Route;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Human reviewer decision. The reason is mandatory and the record is immutable once written. */
@Entity
@Table(name = "review_disposition")
public class ReviewDispositionEntity {

    @Id
    private String reviewId;
    private String caseId;
    private String recommendationId;
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    private Disposition disposition;

    @Enumerated(EnumType.STRING)
    private Route selectedRoute;

    private String reason;
    private boolean missingInfoAcknowledged;

    @Enumerated(EnumType.STRING)
    private CaseStatus resultingStatus;

    private OffsetDateTime recordedAt;

    protected ReviewDispositionEntity() {
    }

    public ReviewDispositionEntity(String reviewId, String caseId, String recommendationId, String reviewerId,
                                   Disposition disposition, Route selectedRoute, String reason,
                                   boolean missingInfoAcknowledged, CaseStatus resultingStatus, OffsetDateTime recordedAt) {
        this.reviewId = reviewId;
        this.caseId = caseId;
        this.recommendationId = recommendationId;
        this.reviewerId = reviewerId;
        this.disposition = disposition;
        this.selectedRoute = selectedRoute;
        this.reason = reason;
        this.missingInfoAcknowledged = missingInfoAcknowledged;
        this.resultingStatus = resultingStatus;
        this.recordedAt = recordedAt;
    }

    public String getReviewId() { return reviewId; }
    public String getCaseId() { return caseId; }
    public String getRecommendationId() { return recommendationId; }
    public String getReviewerId() { return reviewerId; }
    public Disposition getDisposition() { return disposition; }
    public Route getSelectedRoute() { return selectedRoute; }
    public String getReason() { return reason; }
    public boolean isMissingInfoAcknowledged() { return missingInfoAcknowledged; }
    public CaseStatus getResultingStatus() { return resultingStatus; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
