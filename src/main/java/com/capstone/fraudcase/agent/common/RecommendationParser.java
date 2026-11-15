package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.agent.common.FraudCaseRecommendation.PolicyCitationRef;
import com.capstone.fraudcase.domain.Classification;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Strict JSON-to-DTO mapping for model output. Unknown enum values, wrong types and malformed citations are reported
 * as validation errors instead of being coerced. Shared by every AI adapter so the schema cannot diverge.
 */
@Component
public class RecommendationParser {

    private final ObjectMapper mapper;

    public RecommendationParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public FraudCaseRecommendation parse(String raw) {
        List<String> errors = new ArrayList<>();
        JsonNode root = readObject(raw, errors);
        if (root == null) {
            throw new RecommendationValidationException(errors);
        }
        FraudCaseRecommendation result = new FraudCaseRecommendation(
                text(root, "caseId", errors),
                enumValue(root, "classification", Classification.class, errors),
                enumValue(root, "riskLevel", RiskLevel.class, errors),
                enumValue(root, "recommendedRoute", Route.class, errors),
                number(root, "confidence", errors),
                bool(root, "humanReviewRequired", errors),
                strings(root, "evidenceIds", errors),
                citations(root, errors),
                strings(root, "missingInformation", errors),
                strings(root, "ruleIds", errors),
                strings(root, "toolTraceIds", errors),
                strings(root, "contradictions", errors),
                strings(root, "inputFacts", errors),
                text(root, "rationale", errors));
        if (!errors.isEmpty()) {
            throw new RecommendationValidationException(errors);
        }
        return result;
    }

    private JsonNode readObject(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add("empty model output");
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            errors.add("model output is not a JSON object");
            return null;
        }
        try {
            JsonNode node = mapper.readTree(raw.substring(start, end + 1));
            if (!node.isObject()) {
                errors.add("model output is not a JSON object");
                return null;
            }
            return node;
        } catch (Exception e) {
            errors.add("model output is malformed JSON");
            return null;
        }
    }

    private static String text(JsonNode root, String field, List<String> errors) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            errors.add("missing field '" + field + "'");
            return null;
        }
        if (!n.isTextual()) {
            errors.add("field '" + field + "' must be a string");
            return null;
        }
        return n.asText();
    }

    private static <E extends Enum<E>> E enumValue(JsonNode root, String field, Class<E> type, List<String> errors) {
        String value = text(root, field, errors);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            errors.add("unknown " + field + " value '" + truncate(value) + "'");
            return null;
        }
    }

    private static Double number(JsonNode root, String field, List<String> errors) {
        JsonNode n = root.get(field);
        if (n == null || !n.isNumber()) {
            errors.add("field '" + field + "' must be a number");
            return null;
        }
        return n.asDouble();
    }

    private static Boolean bool(JsonNode root, String field, List<String> errors) {
        JsonNode n = root.get(field);
        if (n == null || !n.isBoolean()) {
            errors.add("field '" + field + "' must be a boolean");
            return null;
        }
        return n.asBoolean();
    }

    private static List<String> strings(JsonNode root, String field, List<String> errors) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return List.of();
        }
        if (!n.isArray()) {
            errors.add("field '" + field + "' must be an array of strings");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : n) {
            if (!item.isTextual()) {
                errors.add("field '" + field + "' must contain only strings");
                return List.of();
            }
            values.add(item.asText());
        }
        return values;
    }

    private static List<PolicyCitationRef> citations(JsonNode root, List<String> errors) {
        JsonNode n = root.get("policyCitations");
        if (n == null || n.isNull()) {
            return List.of();
        }
        if (!n.isArray()) {
            errors.add("field 'policyCitations' must be an array");
            return List.of();
        }
        List<PolicyCitationRef> refs = new ArrayList<>();
        for (JsonNode c : n) {
            if (!c.isObject() || !c.path("documentId").isTextual() || !c.path("version").isTextual()
                    || !c.path("chunkId").isTextual() || !c.path("section").isTextual() || !c.path("score").isNumber()) {
                errors.add("malformed policy citation (documentId, version, section, chunkId and numeric score are required)");
                return List.of();
            }
            refs.add(new PolicyCitationRef(c.get("documentId").asText(), c.get("version").asText(),
                    c.get("section").asText(), c.get("chunkId").asText(), c.get("score").asDouble()));
        }
        return refs;
    }

    private static String truncate(String value) {
        return value.length() > 40 ? value.substring(0, 40) + "..." : value;
    }
}
