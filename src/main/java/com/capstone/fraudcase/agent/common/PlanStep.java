package com.capstone.fraudcase.agent.common;

/** One step of the bounded investigation plan selected by the application. */
public record PlanStep(int order, String toolName, String purpose) {
}
