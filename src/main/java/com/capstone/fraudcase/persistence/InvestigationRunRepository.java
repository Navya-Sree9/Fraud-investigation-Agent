package com.capstone.fraudcase.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRunRepository extends JpaRepository<InvestigationRunEntity, String> {
    Optional<InvestigationRunEntity> findByCaseIdAndRunNumber(String caseId, int runNumber);

    List<InvestigationRunEntity> findByCaseIdOrderByRunNumberDesc(String caseId);
}
