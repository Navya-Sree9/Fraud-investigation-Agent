package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDispositionRepository extends JpaRepository<ReviewDispositionEntity, String> {
    List<ReviewDispositionEntity> findByCaseIdOrderByRecordedAtAsc(String caseId);
}
