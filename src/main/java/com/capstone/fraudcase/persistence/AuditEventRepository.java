package com.capstone.fraudcase.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Deliberately exposes only insert and read operations: no update or delete methods exist. */
public interface AuditEventRepository extends Repository<AuditEventEntity, String> {
    AuditEventEntity save(AuditEventEntity event);

    List<AuditEventEntity> findByCaseIdOrderBySequenceNoAsc(String caseId);

    List<AuditEventEntity> findTop100ByOrderBySequenceNoDesc();

    Optional<AuditEventEntity> findTopByOrderBySequenceNoDesc();

    List<AuditEventEntity> findAllByOrderBySequenceNoAsc();

    long count();
}
