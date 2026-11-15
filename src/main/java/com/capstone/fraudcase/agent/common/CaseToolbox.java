package com.capstone.fraudcase.agent.common;

import java.util.List;

/**
 * Read-only, case-bound tool access handed to model adapters. Arguments are validated against the current case;
 * out-of-scope references and calls beyond the configured budget return OUT_OF_SCOPE instead of executing.
 */
public interface CaseToolbox {

    ToolEnvelope getTransactionDetails(String transactionRef);

    ToolEnvelope getDeviceSignals(String maskedCustomerRef);

    ToolEnvelope compareRecentLocations(String maskedCustomerRef);

    ToolEnvelope getMerchantRiskProfile(String maskedMerchant);

    ToolEnvelope getPriorDisputes(String maskedCustomerRef);

    ToolEnvelope getTransactionVelocity(String maskedCustomerRef);

    ToolEnvelope runComplianceScreening(String maskedCustomerRef);

    ToolEnvelope calculateCaseSla(String route, String riskLevel);

    ToolEnvelope retrievePolicyEvidence(String query);

    /** Trace IDs of tool calls made through this toolbox (model-initiated). */
    List<String> traceIds();

    /** Policy passages returned by model-initiated retrievals that met the threshold (citable). */
    List<com.capstone.fraudcase.knowledge.PolicyPassage> extraCitablePassages();
}
