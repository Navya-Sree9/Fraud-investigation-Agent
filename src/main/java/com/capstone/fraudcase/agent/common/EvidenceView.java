package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.ProvenanceType;
import com.capstone.fraudcase.domain.Severity;
import java.time.OffsetDateTime;

/** Evidence item as presented to processors: identifier, severity, concise value and provenance. */
public record EvidenceView(String evidenceId, EvidenceType type, Severity severity, String summary,
                           ProvenanceType provenance, String sourceRef, boolean available, OffsetDateTime observedAt) {
}
