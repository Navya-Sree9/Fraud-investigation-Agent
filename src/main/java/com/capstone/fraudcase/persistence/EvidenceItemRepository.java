package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, String> {
    List<EvidenceItemEntity> findByCaseIdAndRunNumberOrderByEvidenceIdAsc(String caseId, int runNumber);
}
