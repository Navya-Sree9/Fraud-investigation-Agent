package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyCitationRepository extends JpaRepository<PolicyCitationEntity, Long> {
    List<PolicyCitationEntity> findByCaseIdAndRunNumberOrderByScoreDesc(String caseId, int runNumber);
}
