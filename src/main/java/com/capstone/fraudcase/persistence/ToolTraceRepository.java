package com.capstone.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolTraceRepository extends JpaRepository<ToolTraceEntity, String> {
    List<ToolTraceEntity> findByCaseIdAndRunNumberOrderByStartedAtAscTraceIdAsc(String caseId, int runNumber);

    List<ToolTraceEntity> findTop200ByOrderByStartedAtDesc();
}
