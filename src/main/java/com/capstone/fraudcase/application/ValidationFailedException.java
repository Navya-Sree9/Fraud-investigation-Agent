package com.capstone.fraudcase.application;

import java.util.Map;

/** Field-level validation failure (intake, review or follow-up) (field -> safe message). */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationFailedException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
