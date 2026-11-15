package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Severity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One material fact or claim, with its provenance (intake record or tool trace ID). */
@Entity
@Table(name = "evidence_item")
public class EvidenceItemEntity {

    @Id
    private String evidenceId;
    private String caseId;
    private int runNumber;

    @Enumerated(EnumType.STRING)
    private EvidenceType type;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private String valueSummary;

    @Enumerated(EnumType.STRING)
    private ProvenanceType provenance;

    private String sourceRef;
    private boolean available;
    @Lob
    private String detailJson;
    private OffsetDateTime observedAt;

    protected EvidenceItemEntity() {
    }

    public EvidenceItemEntity(String evidenceId, String caseId, int runNumber, EvidenceType type, Severity severity,
                              String valueSummary, ProvenanceType provenance, String sourceRef, boolean available,
                              String detailJson, OffsetDateTime observedAt) {
        this.evidenceId = evidenceId;
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.type = type;
        this.severity = severity;
        this.valueSummary = valueSummary;
        this.provenance = provenance;
        this.sourceRef = sourceRef;
        this.available = available;
        this.detailJson = detailJson;
        this.observedAt = observedAt;
    }

    public String getEvidenceId() { return evidenceId; }
    public String getCaseId() { return caseId; }
    public int getRunNumber() { return runNumber; }
    public EvidenceType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getValueSummary() { return valueSummary; }
    public ProvenanceType getProvenance() { return provenance; }
    public String getSourceRef() { return sourceRef; }
    public boolean isAvailable() { return available; }
    public String getDetailJson() { return detailJson; }
    public OffsetDateTime getObservedAt() { return observedAt; }
}
