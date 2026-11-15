package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.ToolStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Redacted record of one tool execution. Arguments are stored as a hash plus a masked summary. */
@Entity
@Table(name = "tool_trace")
public class ToolTraceEntity {

    @Id
    private String traceId;
    private String caseId;
    private int runNumber;
    private String correlationId;
    private String toolName;
    private String invokedBy;
    private String requestHash;
    private String argumentsSummary;

    @Enumerated(EnumType.STRING)
    private ToolStatus status;

    private long durationMs;
    private String resultSummary;
    @Lob
    private String resultJson;
    private OffsetDateTime startedAt;

    protected ToolTraceEntity() {
    }

    public ToolTraceEntity(String traceId, String caseId, int runNumber, String correlationId, String toolName,
                           String invokedBy, String requestHash, String argumentsSummary, ToolStatus status,
                           long durationMs, String resultSummary, String resultJson, OffsetDateTime startedAt) {
        this.traceId = traceId;
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.correlationId = correlationId;
        this.toolName = toolName;
        this.invokedBy = invokedBy;
        this.requestHash = requestHash;
        this.argumentsSummary = argumentsSummary;
        this.status = status;
        this.durationMs = durationMs;
        this.resultSummary = resultSummary;
        this.resultJson = resultJson;
        this.startedAt = startedAt;
    }

    public String getTraceId() { return traceId; }
    public String getCaseId() { return caseId; }
    public int getRunNumber() { return runNumber; }
    public String getCorrelationId() { return correlationId; }
    public String getToolName() { return toolName; }
    public String getInvokedBy() { return invokedBy; }
    public String getRequestHash() { return requestHash; }
    public String getArgumentsSummary() { return argumentsSummary; }
    public ToolStatus getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public String getResultSummary() { return resultSummary; }
    public String getResultJson() { return resultJson; }
    public OffsetDateTime getStartedAt() { return startedAt; }
}
