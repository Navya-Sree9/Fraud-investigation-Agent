package com.capstone.fraudcase.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionClaimRepository extends JpaRepository<TransactionClaimEntity, String> {
    Optional<TransactionClaimEntity> findByCaseId(String caseId);
}
