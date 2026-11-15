package com.capstone.fraudcase.knowledge;

import java.time.LocalDate;

/** One ingested chunk with the metadata required for exact citation. */
public record PolicyChunk(String documentId, String title, String version, LocalDate effectiveDate, String owner,
                          String status, String section, String chunkId, String text) {
}
