package com.capstone.fraudcase.domain;

/** Case lifecycle states (guide section 2.2). Transitions are enforced by {@link CaseStateMachine}. */
public enum CaseStatus {
    NEW, ENRICHING, INCOMPLETE, READY_FOR_REVIEW, UNDER_REVIEW, ROUTED, CLOSED
}
