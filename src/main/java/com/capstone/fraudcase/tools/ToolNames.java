package com.capstone.fraudcase.tools;

import java.util.Set;

/** Approved tool catalogue (guide section 2.5). Anything not on this allow-list is rejected as OUT_OF_SCOPE. */
public final class ToolNames {

    public static final String TRANSACTION = "getTransactionDetails";
    public static final String DEVICE = "getDeviceSignals";
    public static final String LOCATION = "compareRecentLocations";
    public static final String MERCHANT = "getMerchantRiskProfile";
    public static final String PRIOR_DISPUTES = "getPriorDisputes";
    public static final String VELOCITY = "getTransactionVelocity";
    public static final String SCREENING = "runComplianceScreening";
    public static final String SLA = "calculateCaseSla";
    public static final String POLICY = "retrievePolicyEvidence";
    /** Application-only: never registered with a model. */
    public static final String AUDIT = "saveAuditEvent";

    public static final Set<String> ALLOW_LIST = Set.of(TRANSACTION, DEVICE, LOCATION, MERCHANT, PRIOR_DISPUTES, VELOCITY,
            SCREENING, SLA, POLICY, AUDIT);
    public static final Set<String> MODEL_CALLABLE = Set.of(TRANSACTION, DEVICE, LOCATION, MERCHANT, PRIOR_DISPUTES, VELOCITY,
            SCREENING, SLA, POLICY);

    private ToolNames() {
    }
}
