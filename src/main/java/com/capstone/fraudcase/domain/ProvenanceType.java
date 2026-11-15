package com.capstone.fraudcase.domain;

/** Where a fact came from. Every persisted fact carries one of these. */
public enum ProvenanceType {
    USER_INTAKE, TOOL_RESULT, POLICY_RETRIEVAL, MODEL_SUMMARY, RULES_ENGINE, REVIEWER_ENTRY, SYSTEM
}
