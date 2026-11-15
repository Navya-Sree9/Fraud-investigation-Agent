package com.capstone.fraudcase.domain;

/** Reviewer dispositions (guide section 7.6). None of them executes a financial or customer-facing action. */
public enum Disposition {
    /** Accept the recommended route. */
    ACCEPT_RECOMMENDATION,
    /** Send to a different queue than recommended. */
    REROUTE,
    /** Escalate to fraud investigation at elevated priority. */
    ESCALATE,
    /** Ask for more information; routed to manual review awaiting evidence. */
    REQUEST_INFORMATION,
    /** Reject/override the recommendation and close the case. */
    CLOSE_OVERRIDE,
    /** Close a routed or incomplete case with a final disposition. */
    CLOSE
}
