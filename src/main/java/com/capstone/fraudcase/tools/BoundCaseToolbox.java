package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.agent.common.CaseToolbox;
import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.domain.RiskLevel;
import com.capstone.fraudcase.domain.Route;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.knowledge.PolicyPassage;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.knowledge.PolicyRetrievalResult;
import com.capstone.fraudcase.tools.ToolResults.PolicyEvidence;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Model-facing toolbox bound to one case and run. Every argument is validated against the case under investigation,
 * so a model cannot query another customer, transaction or merchant. All calls are traced as invokedBy=MODEL.
 */
public final class BoundCaseToolbox implements CaseToolbox {

    private static final String MODEL = "MODEL";

    private final ToolGateway gateway;
    private final EvidenceTools tools;
    private final PolicyRetriever retriever;
    private final RunScope scope;
    private final int topK;
    private final double threshold;
    private final List<PolicyPassage> extraCitable = new CopyOnWriteArrayList<>();

    public BoundCaseToolbox(ToolGateway gateway, EvidenceTools tools, PolicyRetriever retriever, RunScope scope, int topK, double threshold) {
        this.gateway = gateway;
        this.tools = tools;
        this.retriever = retriever;
        this.scope = scope;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public ToolEnvelope getTransactionDetails(String transactionRef) {
        return gateway.execute(scope, ToolNames.TRANSACTION, MODEL, Map.of("transactionRef", String.valueOf(transactionRef)), () -> {
            requireSame(transactionRef, scope.facts().transactionRef(), "transaction");
            return tools.getTransactionDetails(scope.facts());
        });
    }

    @Override
    public ToolEnvelope getDeviceSignals(String maskedCustomerRef) {
        return gateway.execute(scope, ToolNames.DEVICE, MODEL, Map.of("maskedCustomerRef", String.valueOf(maskedCustomerRef)), () -> {
            requireSame(maskedCustomerRef, scope.facts().maskedCustomerRef(), "customer");
            return tools.getDeviceSignals(scope.facts());
        });
    }

    @Override
    public ToolEnvelope compareRecentLocations(String maskedCustomerRef) {
        return gateway.execute(scope, ToolNames.LOCATION, MODEL, Map.of("maskedCustomerRef", String.valueOf(maskedCustomerRef)), () -> {
            requireSame(maskedCustomerRef, scope.facts().maskedCustomerRef(), "customer");
            return tools.compareRecentLocations(scope.facts());
        });
    }

    @Override
    public ToolEnvelope getMerchantRiskProfile(String maskedMerchant) {
        return gateway.execute(scope, ToolNames.MERCHANT, MODEL, Map.of("maskedMerchant", String.valueOf(maskedMerchant)), () -> {
            requireSame(maskedMerchant, scope.facts().maskedMerchant(), "merchant");
            return tools.getMerchantRiskProfile(scope.facts());
        });
    }

    @Override
    public ToolEnvelope getPriorDisputes(String maskedCustomerRef) {
        return gateway.execute(scope, ToolNames.PRIOR_DISPUTES, MODEL, Map.of("maskedCustomerRef", String.valueOf(maskedCustomerRef)), () -> {
            requireSame(maskedCustomerRef, scope.facts().maskedCustomerRef(), "customer");
            return tools.getPriorDisputes(scope.facts());
        });
    }

    @Override
    public ToolEnvelope getTransactionVelocity(String maskedCustomerRef) {
        return gateway.execute(scope, ToolNames.VELOCITY, MODEL, Map.of("maskedCustomerRef", String.valueOf(maskedCustomerRef)), () -> {
            requireSame(maskedCustomerRef, scope.facts().maskedCustomerRef(), "customer");
            return tools.getTransactionVelocity(scope.facts());
        });
    }

    @Override
    public ToolEnvelope runComplianceScreening(String maskedCustomerRef) {
        return gateway.execute(scope, ToolNames.SCREENING, MODEL, Map.of("maskedCustomerRef", String.valueOf(maskedCustomerRef)), () -> {
            requireSame(maskedCustomerRef, scope.facts().maskedCustomerRef(), "customer");
            return tools.runComplianceScreening(scope.facts());
        });
    }

    @Override
    public ToolEnvelope calculateCaseSla(String route, String riskLevel) {
        return gateway.execute(scope, ToolNames.SLA, MODEL, Map.of("route", String.valueOf(route), "riskLevel", String.valueOf(riskLevel)),
                () -> tools.calculateCaseSla(parse(Route.class, route), parse(RiskLevel.class, riskLevel), scope.facts().reportedAt()));
    }

    @Override
    public ToolEnvelope retrievePolicyEvidence(String query) {
        return gateway.execute(scope, ToolNames.POLICY, MODEL, Map.of("query", String.valueOf(query)), () -> {
            if (query == null || query.isBlank() || query.length() > 500) {
                throw new IllegalArgumentException("query must be 1-500 characters");
            }
            PolicyRetrievalResult r = retriever.retrieve(query, topK, threshold);
            if (r.status() == PolicyRetrievalResult.Status.UNAVAILABLE) {
                throw new ToolUnavailableException(ToolStatus.FAILED, "Policy store unavailable");
            }
            extraCitable.addAll(r.cited());
            return new PolicyEvidence(query, topK, threshold, !r.hasCitations(), r.candidates());
        });
    }

    @Override
    public List<String> traceIds() {
        return scope.modelTraceIds();
    }

    @Override
    public List<PolicyPassage> extraCitablePassages() {
        return List.copyOf(extraCitable);
    }

    private static void requireSame(String supplied, String expected, String what) {
        if (supplied == null || !normalise(supplied).equals(normalise(expected))) {
            throw new ToolUnavailableException(ToolStatus.OUT_OF_SCOPE, "Requested " + what + " reference is not part of this case");
        }
    }

    private static String normalise(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName());
        }
    }
}
