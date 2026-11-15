package com.capstone.fraudcase.knowledge;

import java.time.LocalDate;

/** A retrieved policy chunk with exact citation metadata and its similarity score. */
public record PolicyPassage(String documentId, String title, String version, LocalDate effectiveDate, String section,
                            String chunkId, double score, boolean aboveThreshold, String passage) {
}
