package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.IntakeChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/** Fraud case header. Holds masked references only; never raw card or account numbers. */
@Entity
@Table(name = "fraud_case")
public class FraudCaseEntity {

    @Id
    @Column(name = "case_id")
    private String caseId;

    private String maskedCustomerRef;
    private String homeRegion;

    @Enumerated(EnumType.STRING)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    private IntakeChannel intakeChannel;

    private OffsetDateTime reportedAt;
    private String createdBy;

    @Column(name = "dedupe_key")
    private String dedupeKey;
    private boolean syntheticDataConfirmed;
    private boolean reviewRequired;
    private int currentRun;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    protected FraudCaseEntity() {
    }

    public FraudCaseEntity(String caseId, String maskedCustomerRef, String homeRegion, IntakeChannel intakeChannel,
                           OffsetDateTime reportedAt, String createdBy, String dedupeKey) {
        this.caseId = caseId;
        this.maskedCustomerRef = maskedCustomerRef;
        this.homeRegion = homeRegion;
        this.intakeChannel = intakeChannel;
        this.reportedAt = reportedAt;
        this.createdBy = createdBy;
        this.dedupeKey = dedupeKey;
        this.status = CaseStatus.NEW;
        this.syntheticDataConfirmed = true;
        this.reviewRequired = true;
        this.createdAt = reportedAt;
        this.updatedAt = reportedAt;
    }

    public String getCaseId() { return caseId; }
    public String getMaskedCustomerRef() { return maskedCustomerRef; }
    public String getHomeRegion() { return homeRegion; }
    public CaseStatus getStatus() { return status; }
    public IntakeChannel getIntakeChannel() { return intakeChannel; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getDedupeKey() { return dedupeKey; }
    public boolean isSyntheticDataConfirmed() { return syntheticDataConfirmed; }
    public boolean isReviewRequired() { return reviewRequired; }
    public int getCurrentRun() { return currentRun; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(CaseStatus status, OffsetDateTime at) {
        this.status = status;
        this.updatedAt = at;
    }

    public int nextRun() {
        return ++currentRun;
    }
}
