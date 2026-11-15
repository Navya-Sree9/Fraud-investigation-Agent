package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.CaseToolbox;
import com.capstone.fraudcase.agent.common.ToolEnvelope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Spring AI tool registration over the case-bound, read-only toolbox. Methods are thin, typed delegates. */
public class SpringAiEvidenceTools {

    private final CaseToolbox toolbox;

    public SpringAiEvidenceTools(CaseToolbox toolbox) {
        this.toolbox = toolbox;
    }

    @Tool(name = "getTransactionDetails", description = "Read-only: transaction state, channel, amount and authentication outcome for this case's transaction.")
    public ToolEnvelope getTransactionDetails(@ToolParam(description = "Transaction reference of this case, e.g. TXN-2026-784102") String transactionRef) {
        return toolbox.getTransactionDetails(transactionRef);
    }

    @Tool(name = "getDeviceSignals", description = "Read-only: device novelty and trust indicators for this case's masked customer.")
    public ToolEnvelope getDeviceSignals(@ToolParam(description = "Masked customer reference of this case") String maskedCustomerRef) {
        return toolbox.getDeviceSignals(maskedCustomerRef);
    }

    @Tool(name = "compareRecentLocations", description = "Read-only: compares the transaction location with recent trusted locations.")
    public ToolEnvelope compareRecentLocations(@ToolParam(description = "Masked customer reference of this case") String maskedCustomerRef) {
        return toolbox.compareRecentLocations(maskedCustomerRef);
    }

    @Tool(name = "getMerchantRiskProfile", description = "Read-only: training merchant risk band, blocklist match and category.")
    public ToolEnvelope getMerchantRiskProfile(@ToolParam(description = "Masked merchant text of this case") String maskedMerchant) {
        return toolbox.getMerchantRiskProfile(maskedMerchant);
    }

    @Tool(name = "getPriorDisputes", description = "Read-only: summary of earlier disputes for this case's masked customer.")
    public ToolEnvelope getPriorDisputes(@ToolParam(description = "Masked customer reference of this case") String maskedCustomerRef) {
        return toolbox.getPriorDisputes(maskedCustomerRef);
    }

    @Tool(name = "getTransactionVelocity", description = "Read-only: transaction counts in trailing 10 min, 60 min and 24 h windows; flags bursts of 5+ in 10 minutes.")
    public ToolEnvelope getTransactionVelocity(@ToolParam(description = "Masked customer reference of this case") String maskedCustomerRef) {
        return toolbox.getTransactionVelocity(maskedCustomerRef);
    }

    @Tool(name = "runComplianceScreening", description = "Read-only: synthetic sanctions/PEP screening of this case's merchant. A confirmed match recommends AML escalation; nothing is actioned.")
    public ToolEnvelope runComplianceScreening(@ToolParam(description = "Masked customer reference of this case") String maskedCustomerRef) {
        return toolbox.runComplianceScreening(maskedCustomerRef);
    }

    @Tool(name = "calculateCaseSla", description = "Read-only: training SLA queue, priority and target time for a proposed route and risk level.")
    public ToolEnvelope calculateCaseSla(
            @ToolParam(description = "FRAUD_INVESTIGATION, DISPUTE_OPERATIONS, PAYMENT_OPERATIONS, AML_INVESTIGATION or MANUAL_REVIEW") String route,
            @ToolParam(description = "LOW, MEDIUM, HIGH or CRITICAL") String riskLevel) {
        return toolbox.calculateCaseSla(route, riskLevel);
    }

    @Tool(name = "retrievePolicyEvidence", description = "Read-only: searches approved synthetic policy passages; returns citation metadata and scores. Only passages above threshold may be cited.")
    public ToolEnvelope retrievePolicyEvidence(@ToolParam(description = "Short search query, max 500 characters") String query) {
        return toolbox.retrievePolicyEvidence(query);
    }
}
