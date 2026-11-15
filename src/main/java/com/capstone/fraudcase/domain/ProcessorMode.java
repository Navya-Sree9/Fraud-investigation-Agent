package com.capstone.fraudcase.domain;

import java.util.Locale;

public enum ProcessorMode {
    RULES("rules"), SPRING_AI("spring-ai"), LANGCHAIN4J("langchain4j");

    private final String key;

    ProcessorMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ProcessorMode fromKey(String value) {
        String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "-");
        for (ProcessorMode mode : values()) {
            if (mode.key.equals(normalised)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown processor mode: " + value);
    }
}
