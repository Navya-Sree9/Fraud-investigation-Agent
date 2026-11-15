package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.knowledge.PolicyDocument.Availability;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a policy markdown file: a front-matter header with mandatory metadata followed by "## <section> <title>"
 * blocks. Chunks follow section boundaries so a rule is never split across unrelated chunks; long sections are split
 * on paragraph boundaries only.
 */
public final class PolicyDocumentParser {

    static final List<String> REQUIRED = List.of("documentId", "title", "version", "effectiveDate", "owner", "classification", "status", "chunkPrefix");
    private static final Pattern SECTION = Pattern.compile("^##\\s+(\\d+(?:\\.\\d+)?)\\s+(.+)$");
    private static final int MAX_CHUNK_CHARS = 1200;

    private PolicyDocumentParser() {
    }

    public static PolicyDocument parse(String sourceFile, String content) {
        String normalised = content.replace("\r\n", "\n");
        if (!normalised.startsWith("---\n")) {
            throw new InvalidPolicyException(sourceFile + ": missing metadata header");
        }
        int end = normalised.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new InvalidPolicyException(sourceFile + ": unterminated metadata header");
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (String line : normalised.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        for (String key : REQUIRED) {
            if (meta.getOrDefault(key, "").isBlank()) {
                throw new InvalidPolicyException(sourceFile + ": missing metadata '" + key + "'");
            }
        }
        if (!"SYNTHETIC_TRAINING".equals(meta.get("classification"))) {
            throw new InvalidPolicyException(sourceFile + ": only SYNTHETIC_TRAINING policies may be ingested");
        }
        LocalDate effective;
        try {
            effective = LocalDate.parse(meta.get("effectiveDate"));
        } catch (DateTimeParseException e) {
            throw new InvalidPolicyException(sourceFile + ": invalid effectiveDate");
        }
        List<PolicyChunk> chunks = chunk(meta, effective, normalised.substring(end + 5));
        if (chunks.isEmpty()) {
            throw new InvalidPolicyException(sourceFile + ": no sections found");
        }
        Availability availability = "WITHDRAWN".equalsIgnoreCase(meta.get("status")) ? Availability.WITHDRAWN : Availability.ACTIVE;
        return new PolicyDocument(sourceFile, meta.get("documentId"), meta.get("title"), meta.get("version"), effective,
                meta.get("owner"), meta.get("classification"), meta.get("status"), meta.get("chunkPrefix"), chunks, availability);
    }

    private static List<PolicyChunk> chunk(Map<String, String> meta, LocalDate effective, String body) {
        List<PolicyChunk> chunks = new ArrayList<>();
        String section = null;
        StringBuilder text = new StringBuilder();
        for (String line : body.split("\n")) {
            Matcher m = SECTION.matcher(line.trim());
            if (m.matches()) {
                addSection(chunks, meta, effective, section, text.toString());
                section = m.group(1) + " " + m.group(2).trim();
                text.setLength(0);
            } else if (section != null) {
                text.append(line).append('\n');
            }
        }
        addSection(chunks, meta, effective, section, text.toString());
        return chunks;
    }

    private static void addSection(List<PolicyChunk> chunks, Map<String, String> meta, LocalDate effective, String section, String text) {
        if (section == null || text.isBlank()) {
            return;
        }
        List<String> parts = splitParagraphs(text.trim());
        String baseId = meta.get("chunkPrefix") + "-" + sectionCode(section);
        for (int i = 0; i < parts.size(); i++) {
            String chunkId = parts.size() == 1 ? baseId : baseId + "-" + (char) ('a' + i);
            chunks.add(new PolicyChunk(meta.get("documentId"), meta.get("title"), meta.get("version"), effective,
                    meta.get("owner"), meta.get("status"), section, chunkId, parts.get(i)));
        }
    }

    private static List<String> splitParagraphs(String text) {
        if (text.length() <= MAX_CHUNK_CHARS) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\n\\s*\n")) {
            if (current.length() > 0 && current.length() + paragraph.length() > MAX_CHUNK_CHARS) {
                parts.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(paragraph).append("\n\n");
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    /** "3.2 Initial fraud routing" -> "032"; "4 Scope" -> "040". */
    static String sectionCode(String section) {
        String number = section.split("\\s+")[0];
        String[] pieces = number.split("\\.");
        int major = Integer.parseInt(pieces[0]);
        int minor = pieces.length > 1 ? Integer.parseInt(pieces[1]) : 0;
        return "%03d".formatted(major * 10 + minor);
    }

    public static class InvalidPolicyException extends RuntimeException {
        public InvalidPolicyException(String message) {
            super(message);
        }
    }
}
