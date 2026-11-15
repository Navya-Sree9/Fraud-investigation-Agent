package com.capstone.fraudcase.domain;

/** Operational queues a reviewer may route a case to. The application never executes the route itself. */
public enum Route {
    FRAUD_INVESTIGATION, DISPUTE_OPERATIONS, PAYMENT_OPERATIONS, AML_INVESTIGATION, MANUAL_REVIEW
}
