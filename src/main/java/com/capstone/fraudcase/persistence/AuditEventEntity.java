package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.ActorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Append-only audit event. Stores a payload hash rather than the sensitive payload, and a hash chain
 * (previousHash -> chainHash) so tampering with any earlier event is detectable.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    private String eventId;
    private long sequenceNo;
    private String caseId;
    private String eventType;

    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    private String actorId;
    private String correlationId;
    private String summary;
    private String payloadHash;
    private String previousHash;
    private String chainHash;
    private OffsetDateTime recordedAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(String eventId, long sequenceNo, String caseId, String eventType, ActorType actorType,
                            String actorId, String correlationId, String summary, String payloadHash,
                            String previousHash, String chainHash, OffsetDateTime recordedAt) {
        this.eventId = eventId;
        this.sequenceNo = sequenceNo;
        this.caseId = caseId;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.summary = summary;
        this.payloadHash = payloadHash;
        this.previousHash = previousHash;
        this.chainHash = chainHash;
        this.recordedAt = recordedAt;
    }

    public String getEventId() { return eventId; }
    public long getSequenceNo() { return sequenceNo; }
    public String getCaseId() { return caseId; }
    public String getEventType() { return eventType; }
    public ActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getCorrelationId() { return correlationId; }
    public String getSummary() { return summary; }
    public String getPayloadHash() { return payloadHash; }
    public String getPreviousHash() { return previousHash; }
    public String getChainHash() { return chainHash; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
