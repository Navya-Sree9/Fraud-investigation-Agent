package com.capstone.fraudcase.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, String> {
    List<FraudCaseEntity> findAllByOrderByCreatedAtDesc();

    Optional<FraudCaseEntity> findByDedupeKey(String dedupeKey);
}
