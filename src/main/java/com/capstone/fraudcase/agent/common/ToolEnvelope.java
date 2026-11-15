package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.ToolStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Structured tool result with provenance (toolResultId, observedAt, origin). Failures are represented as a status
 * with a safe message and a null result; provider or stack details are never exposed.
 */
public record ToolEnvelope(String toolResultId, String toolName, String invokedBy, ToolStatus status,
                           OffsetDateTime observedAt, String origin, String summary, Object result,
                           List<String> evidenceIds, long durationMs) {

    public boolean available() {
        return status == ToolStatus.SUCCESS;
    }

    @SuppressWarnings("unchecked")
    public <T> T resultAs(Class<T> type) {
        return type.isInstance(result) ? (T) result : null;
    }
}
