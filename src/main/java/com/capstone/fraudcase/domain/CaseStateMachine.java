package com.capstone.fraudcase.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Allowed case transitions (guide section 2.2). State changes are owned by application services, never by model output. */
public final class CaseStateMachine {

    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = new EnumMap<>(CaseStatus.class);

    static {
        ALLOWED.put(CaseStatus.NEW, EnumSet.of(CaseStatus.ENRICHING));
        ALLOWED.put(CaseStatus.ENRICHING, EnumSet.of(CaseStatus.READY_FOR_REVIEW, CaseStatus.INCOMPLETE));
        ALLOWED.put(CaseStatus.INCOMPLETE, EnumSet.of(CaseStatus.ENRICHING, CaseStatus.CLOSED));
        ALLOWED.put(CaseStatus.READY_FOR_REVIEW, EnumSet.of(CaseStatus.UNDER_REVIEW));
        ALLOWED.put(CaseStatus.UNDER_REVIEW, EnumSet.of(CaseStatus.ROUTED, CaseStatus.CLOSED));
        ALLOWED.put(CaseStatus.ROUTED, EnumSet.of(CaseStatus.CLOSED));
        ALLOWED.put(CaseStatus.CLOSED, EnumSet.noneOf(CaseStatus.class));
    }

    private CaseStateMachine() {
    }

    public static boolean canTransition(CaseStatus from, CaseStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void requireTransition(CaseStatus from, CaseStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    public static Set<CaseStatus> nextStates(CaseStatus from) {
        return EnumSet.copyOf(ALLOWED.get(from).isEmpty() ? EnumSet.noneOf(CaseStatus.class) : ALLOWED.get(from));
    }

    public static class InvalidStateTransitionException extends RuntimeException {
        public InvalidStateTransitionException(CaseStatus from, CaseStatus to) {
            super("Case cannot move from " + from + " to " + to);
        }
    }
}
