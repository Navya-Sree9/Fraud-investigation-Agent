package com.capstone.fraudcase.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One bounded investigation attempt (plan, act, retrieve, assess, recommend) for a case. */
@Entity
@Table(name = "investigation_run")
public class InvestigationRunEntity {

    @Id
    private String runId;
    private String caseId;
    private int runNumber;
    private String correlationId;
    private String requestedMode;
    private String effectiveMode;
    private String status;
    private String investigationGoal;
    @Lob
    private String planJson;
    private String policyQuery;
    private String retrievalStatus;
    @Column(name = "top_k")
    private Integer topK;
    private Double threshold;
    private String fallbackReason;
    private String startedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Long modelLatencyMs;

    protected InvestigationRunEntity() {
    }

    public InvestigationRunEntity(String runId, String caseId, int runNumber, String correlationId, String requestedMode,
                                  String startedBy, OffsetDateTime startedAt) {
        this.runId = runId;
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.correlationId = correlationId;
        this.requestedMode = requestedMode;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.status = "RUNNING";
    }

    public String getRunId() { return runId; }
    public String getCaseId() { return caseId; }
    public int getRunNumber() { return runNumber; }
    public String getCorrelationId() { return correlationId; }
    public String getRequestedMode() { return requestedMode; }
    public String getEffectiveMode() { return effectiveMode; }
    public String getStatus() { return status; }
    public String getInvestigationGoal() { return investigationGoal; }
    public String getPlanJson() { return planJson; }
    public String getPolicyQuery() { return policyQuery; }
    public String getRetrievalStatus() { return retrievalStatus; }
    public Integer getTopK() { return topK; }
    public Double getThreshold() { return threshold; }
    public String getFallbackReason() { return fallbackReason; }
    public String getStartedBy() { return startedBy; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Long getModelLatencyMs() { return modelLatencyMs; }

    public void setPlan(String goal, String planJson) {
        this.investigationGoal = goal;
        this.planJson = planJson;
    }

    public void setRetrieval(String query, String status, int topK, double threshold) {
        this.policyQuery = query;
        this.retrievalStatus = status;
        this.topK = topK;
        this.threshold = threshold;
    }

    public void complete(String status, String effectiveMode, String fallbackReason, Long modelLatencyMs, OffsetDateTime at) {
        this.status = status;
        this.effectiveMode = effectiveMode;
        this.fallbackReason = fallbackReason;
        this.modelLatencyMs = modelLatencyMs;
        this.completedAt = at;
    }
}
