package com.capstone.fraudcase.knowledge;

import java.time.LocalDate;
import java.util.List;

/** Parsed synthetic policy document with validated header metadata. */
public record PolicyDocument(String sourceFile, String documentId, String title, String version, LocalDate effectiveDate,
                             String owner, String classification, String status, String chunkPrefix,
                             List<PolicyChunk> chunks, Availability availability) {

    public enum Availability { ACTIVE, WITHDRAWN, SUPERSEDED, NOT_YET_EFFECTIVE }

    public PolicyDocument withAvailability(Availability value) {
        return new PolicyDocument(sourceFile, documentId, title, version, effectiveDate, owner, classification, status,
                chunkPrefix, chunks, value);
    }

    public boolean retrievable() {
        return availability == Availability.ACTIVE;
    }
}
