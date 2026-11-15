package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.domain.ProcessorMode;

/** Common processor contract (guide section 4.3). Implementations must never execute financial or customer actions. */
public interface FraudCaseProcessor {

    FraudCaseRecommendation analyze(FraudCaseContext context);

    /** Case-scoped follow-up answer. Memory is keyed by {@code context.caseId()} and never shared across cases. */
    String answerFollowUp(FollowUpContext context);

    ProcessorMode mode();

    /** False when the processor cannot run (for example, no model credentials configured). */
    default boolean available() {
        return true;
    }
}
