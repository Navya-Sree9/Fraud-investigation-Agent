package com.capstone.fraudcase.persistence;

import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Human-readable, database-sequence-backed identifiers (CASE-2026-0001, EV-101, TR-118, ...). */
@Component
public class IdGenerator {

    private final JdbcTemplate jdbc;

    public IdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String caseId(Year year) {
        return "CASE-%d-%04d".formatted(year.getValue(), next("case_seq"));
    }

    public String evidenceId() {
        return "EV-" + next("evidence_seq");
    }

    public String traceId() {
        return "TR-" + next("trace_seq");
    }

    public String recommendationId() {
        return "REC-" + next("recommendation_seq");
    }

    public String reviewId() {
        return "REV-" + next("review_seq");
    }

    public String runId() {
        return "RUN-" + next("run_seq");
    }

    public long auditSequence() {
        return next("audit_seq");
    }

    private long next(String sequence) {
        Long value = jdbc.queryForObject("SELECT NEXT VALUE FOR " + sequence, Long.class);
        if (value == null) {
            throw new IllegalStateException("Sequence returned no value: " + sequence);
        }
        return value;
    }
}
