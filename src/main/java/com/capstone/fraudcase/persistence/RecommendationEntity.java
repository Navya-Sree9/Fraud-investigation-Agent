package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A validated recommendation. Only output that passed RecommendationValidator is ever persisted here. */
@Entity
@Table(name = "recommendation")
public class RecommendationEntity {

    @Id
    private String recommendationId;
    private String caseId;
    private int runNumber;
    private String mode;

    @Enumerated(EnumType.STRING)
    private Classification classification;

    @Enumerated(EnumType.STRING)
    private Route route;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private double confidence;
    private boolean humanReviewRequired;
    private String rationale;
    private String evidenceIdsJson;
    private String missingInfoJson;
    private String ruleIdsJson;
    private String toolTraceIdsJson;
    private String contradictionsJson;
    private String inputFactsJson;
    private String priority;
    private OffsetDateTime slaTargetAt;
    private String slaTraceId;
    private String outputHash;
    private OffsetDateTime createdAt;

    protected RecommendationEntity() {
    }

    public RecommendationEntity(String recommendationId, String caseId, int runNumber, String mode,
                                Classification classification, Route route, RiskLevel riskLevel, double confidence,
                                String rationale, String evidenceIdsJson, String missingInfoJson, String ruleIdsJson,
                                String toolTraceIdsJson, String contradictionsJson, String inputFactsJson,
                                String outputHash, OffsetDateTime createdAt) {
        this.recommendationId = recommendationId;
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.mode = mode;
        this.classification = classification;
        this.route = route;
        this.riskLevel = riskLevel;
        this.confidence = confidence;
        this.humanReviewRequired = true;
        this.rationale = rationale;
        this.evidenceIdsJson = evidenceIdsJson;
        this.missingInfoJson = missingInfoJson;
        this.ruleIdsJson = ruleIdsJson;
        this.toolTraceIdsJson = toolTraceIdsJson;
        this.contradictionsJson = contradictionsJson;
        this.inputFactsJson = inputFactsJson;
        this.outputHash = outputHash;
        this.createdAt = createdAt;
    }

    public void setSla(String priority, OffsetDateTime targetAt, String traceId) {
        this.priority = priority;
        this.slaTargetAt = targetAt;
        this.slaTraceId = traceId;
    }

    public String getRecommendationId() { return recommendationId; }
    public String getCaseId() { return caseId; }
    public int getRunNumber() { return runNumber; }
    public String getMode() { return mode; }
    public Classification getClassification() { return classification; }
    public Route getRoute() { return route; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public double getConfidence() { return confidence; }
    public boolean isHumanReviewRequired() { return humanReviewRequired; }
    public String getRationale() { return rationale; }
    public String getEvidenceIdsJson() { return evidenceIdsJson; }
    public String getMissingInfoJson() { return missingInfoJson; }
    public String getRuleIdsJson() { return ruleIdsJson; }
    public String getToolTraceIdsJson() { return toolTraceIdsJson; }
    public String getContradictionsJson() { return contradictionsJson; }
    public String getInputFactsJson() { return inputFactsJson; }
    public String getPriority() { return priority; }
    public OffsetDateTime getSlaTargetAt() { return slaTargetAt; }
    public String getSlaTraceId() { return slaTraceId; }
    public String getOutputHash() { return outputHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
