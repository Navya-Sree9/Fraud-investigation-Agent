package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, String> {
    List<RecommendationEntity> findByCaseIdOrderByCreatedAtDesc(String caseId);
}
