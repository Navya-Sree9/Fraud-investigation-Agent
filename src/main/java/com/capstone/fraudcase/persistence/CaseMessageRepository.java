package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CaseMessageRepository extends JpaRepository<CaseMessageEntity, Long> {
    List<CaseMessageEntity> findByCaseIdOrderByMessageIdAsc(String caseId);

    @Transactional
    void deleteByCaseId(String caseId);
}
