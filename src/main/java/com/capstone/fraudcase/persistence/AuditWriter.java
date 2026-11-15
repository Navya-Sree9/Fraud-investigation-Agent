package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.Hashing;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Append-only audit writer (tool: saveAuditEvent). Only a hash of the payload is stored; the readable
 * summary must already be redacted by the caller. Events are hash-chained for tamper evidence.
 */
@Component
public class AuditWriter {

    static final String GENESIS = "0".repeat(64);

    private final AuditEventRepository repository;
    private final IdGenerator ids;
    private final TransactionTemplate tx;

    public AuditWriter(AuditEventRepository repository, IdGenerator ids, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Commits inside the lock so concurrent appends can never fork the hash chain. */
    public synchronized AuditEventEntity append(String caseId, String eventType, ActorType actorType, String actorId,
                                                String summary, String payload) {
        String correlationId = MDC.get("correlationId");
        return tx.execute(status -> {
            long sequence = ids.auditSequence();
            String previous = repository.findTopByOrderBySequenceNoDesc().map(AuditEventEntity::getChainHash).orElse(GENESIS);
            String payloadHash = Hashing.sha256(payload == null ? "" : payload);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String eventId = "AE-%06d".formatted(sequence);
            String chain = chainHash(previous, eventId, sequence, caseId, eventType, actorId, payloadHash);
            return repository.save(new AuditEventEntity(eventId, sequence, caseId, eventType, actorType, actorId,
                    correlationId, truncate(summary), payloadHash, previous, chain, now));
        });
    }

    /** Recomputes the hash chain; returns the first broken event ID or null when intact. */
    @Transactional(readOnly = true)
    public String verifyChain() {
        String previous = GENESIS;
        List<AuditEventEntity> events = repository.findAllByOrderBySequenceNoAsc();
        for (AuditEventEntity e : events) {
            String expected = chainHash(previous, e.getEventId(), e.getSequenceNo(), e.getCaseId(), e.getEventType(),
                    e.getActorId(), e.getPayloadHash());
            if (!e.getPreviousHash().equals(previous) || !e.getChainHash().equals(expected)) {
                return e.getEventId();
            }
            previous = e.getChainHash();
        }
        return null;
    }

    private static String chainHash(String previous, String eventId, long sequence, String caseId, String eventType,
                                    String actorId, String payloadHash) {
        return Hashing.sha256(String.join("|", previous, eventId, Long.toString(sequence), String.valueOf(caseId),
                eventType, actorId, payloadHash));
    }

    private static String truncate(String value) {
        return value.length() > 600 ? value.substring(0, 597) + "..." : value;
    }
}
