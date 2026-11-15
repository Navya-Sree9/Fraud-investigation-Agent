package com.capstone.fraudcase.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Curated synthetic overrides keyed by transaction reference (fixtures/synthetic-evidence.json). Values not present
 * in the fixture are derived from a stable hash, so identical input always yields identical evidence.
 */
@Component
public class SyntheticFixtureStore {

    private final Map<String, Map<String, Map<String, Object>>> fixtures;

    public SyntheticFixtureStore(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = new ClassPathResource("fixtures/synthetic-evidence.json").getInputStream()) {
            Map<String, Object> root = objectMapper.readValue(in, new TypeReference<>() {
            });
            this.fixtures = objectMapper.convertValue(root.get("transactions"), new TypeReference<>() {
            });
        }
    }

    public Fixture forTransaction(String transactionRef, String tool) {
        return new Fixture(Optional.ofNullable(fixtures.get(transactionRef)).map(m -> m.get(tool)).orElse(Map.of()));
    }

    public Map<String, Map<String, Map<String, Object>>> all() {
        return fixtures;
    }

    /** Typed accessor over one tool override block. */
    public record Fixture(Map<String, Object> values) {
        public String simulate() {
            return string("simulate", null);
        }

        public String string(String key, String fallback) {
            Object v = values.get(key);
            return v == null ? fallback : v.toString();
        }

        public boolean bool(String key, boolean fallback) {
            Object v = values.get(key);
            return v == null ? fallback : Boolean.parseBoolean(v.toString());
        }

        public double number(String key, double fallback) {
            Object v = values.get(key);
            return v instanceof Number n ? n.doubleValue() : v == null ? fallback : Double.parseDouble(v.toString());
        }

        public boolean has(String key) {
            return values.containsKey(key);
        }
    }
}
