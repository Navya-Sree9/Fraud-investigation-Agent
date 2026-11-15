package com.capstone.fraudcase.agent.common;

import java.util.List;

/** Raised when processor output does not satisfy the recommendation contract. Such output is never persisted. */
public class RecommendationValidationException extends RuntimeException {

    private final List<String> errors;

    public RecommendationValidationException(List<String> errors) {
        super("Recommendation rejected: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
