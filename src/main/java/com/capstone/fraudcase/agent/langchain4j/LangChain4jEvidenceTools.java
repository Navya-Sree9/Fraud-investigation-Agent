package com.capstone.fraudcase.agent.langchain4j;

import com.capstone.fraudcase.agent.common.CaseToolbox;
import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/** LangChain4j @Tool registration over the same case-bound, read-only toolbox used by the Spring AI adapter. */
public class LangChain4jEvidenceTools {

    private final CaseToolbox toolbox;
    private final ObjectMapper mapper;

    public LangChain4jEvidenceTools(CaseToolbox toolbox, ObjectMapper mapper) {
        this.toolbox = toolbox;
        this.mapper = mapper;
    }

    @Tool(name = "getTransactionDetails", value = "Read-only: transaction state, channel, amount and authentication outcome for this case's transaction.")
    public String getTransactionDetails(@P("Transaction reference of this case, e.g. TXN-2026-784102") String transactionRef) {
        return json(toolbox.getTransactionDetails(transactionRef));
    }

    @Tool(name = "getDeviceSignals", value = "Read-only: device novelty and trust indicators for this case's masked customer.")
    public String getDeviceSignals(@P("Masked customer reference of this case") String maskedCustomerRef) {
        return json(toolbox.getDeviceSignals(maskedCustomerRef));
    }

    @Tool(name = "compareRecentLocations", value = "Read-only: compares the transaction location with recent trusted locations.")
    public String compareRecentLocations(@P("Masked customer reference of this case") String maskedCustomerRef) {
        return json(toolbox.compareRecentLocations(maskedCustomerRef));
    }

    @Tool(name = "getMerchantRiskProfile", value = "Read-only: training merchant risk band, blocklist match and category.")
    public String getMerchantRiskProfile(@P("Masked merchant text of this case") String maskedMerchant) {
        return json(toolbox.getMerchantRiskProfile(maskedMerchant));
    }

    @Tool(name = "getPriorDisputes", value = "Read-only: summary of earlier disputes for this case's masked customer.")
    public String getPriorDisputes(@P("Masked customer reference of this case") String maskedCustomerRef) {
        return json(toolbox.getPriorDisputes(maskedCustomerRef));
    }

    @Tool(name = "getTransactionVelocity", value = "Read-only: transaction counts in trailing 10 min, 60 min and 24 h windows; flags bursts of 5+ in 10 minutes.")
    public String getTransactionVelocity(@P("Masked customer reference of this case") String maskedCustomerRef) {
        return json(toolbox.getTransactionVelocity(maskedCustomerRef));
    }

    @Tool(name = "runComplianceScreening", value = "Read-only: synthetic sanctions/PEP screening of this case's merchant. A confirmed match recommends AML escalation; nothing is actioned.")
    public String runComplianceScreening(@P("Masked customer reference of this case") String maskedCustomerRef) {
        return json(toolbox.runComplianceScreening(maskedCustomerRef));
    }

    @Tool(name = "calculateCaseSla", value = "Read-only: training SLA queue, priority and target time for a proposed route and risk level.")
    public String calculateCaseSla(@P("FRAUD_INVESTIGATION, DISPUTE_OPERATIONS, PAYMENT_OPERATIONS, AML_INVESTIGATION or MANUAL_REVIEW") String route,
                                   @P("LOW, MEDIUM, HIGH or CRITICAL") String riskLevel) {
        return json(toolbox.calculateCaseSla(route, riskLevel));
    }

    @Tool(name = "retrievePolicyEvidence", value = "Read-only: searches approved synthetic policy passages; returns citation metadata and scores. Only passages above threshold may be cited.")
    public String retrievePolicyEvidence(@P("Short search query, max 500 characters") String query) {
        return json(toolbox.retrievePolicyEvidence(query));
    }

    private String json(ToolEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            return "{\"status\":\"FAILED\",\"summary\":\"Result could not be serialised\"}";
        }
    }
}
