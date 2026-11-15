package com.capstone.fraudcase.security;

/**
 * Personas from the capstone brief (section 1.2), mapped to least-privilege roles.
 *
 * <pre>
 * Persona              Role           May do
 * Service agent        SERVICE_AGENT  capture a masked case (guided intake, validation, missing-information list)
 * Fraud investigator   INVESTIGATOR   capture cases, run tool-driven enrichment, read evidence/trace, ask follow-ups
 * Dispute reviewer     REVIEWER       start review, record dispositions (route / close) with rationale, ask follow-ups
 * Team lead / auditor  AUDITOR        read everything, audit trail, hash-chain verification, health details
 * </pre>
 * Every role can read case screens; no role can execute a financial or customer-facing action.
 */
public final class Roles {

    public static final String SERVICE_AGENT = "SERVICE_AGENT";
    public static final String INVESTIGATOR = "INVESTIGATOR";
    public static final String REVIEWER = "REVIEWER";
    public static final String AUDITOR = "AUDITOR";

    private Roles() {
    }
}
