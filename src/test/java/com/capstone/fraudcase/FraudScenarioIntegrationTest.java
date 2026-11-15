package com.capstone.fraudcase;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.application.CaseQueryService.RecommendationView;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.EvidenceType;
import com.capstone.fraudcase.domain.Severity;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.persistence.EvidenceItemEntity;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** End-to-end scenarios T01-T08 and T11 in rules mode (no model dependency). */
@SpringBootTest
@ActiveProfiles("test")
class FraudScenarioIntegrationTest {

    @Autowired FraudCaseService cases;
    @Autowired InvestigationOrchestrator orchestrator;
    @Autowired CaseQueryService queries;

    private record Run(String caseId, InvestigationOrchestrator.Outcome outcome, RecommendationView rec) {
    }

    private Run run(CreateCaseRequest request) {
        String caseId = cases.createCase(TestCases.unique(request), "analyst").caseId();
        InvestigationOrchestrator.Outcome outcome = orchestrator.investigate(caseId, "analyst", "rules");
        return new Run(caseId, outcome, queries.recommendation(caseId).orElseThrow());
    }

    @Test
    void t01_unrecognisedCnpPurchaseRoutesToFraudInvestigationWithCitationsAndMissingAuth() {
        Run r = run(TestCases.sample());
        assertThat(r.rec().classification()).isIn("UNAUTHORISED_TRANSACTION", "SUSPICIOUS_TRANSACTION");
        assertThat(r.rec().recommendedRoute()).isEqualTo("FRAUD_INVESTIGATION");
        assertThat(r.rec().riskLevel()).isIn("HIGH", "CRITICAL");
        assertThat(r.rec().humanReviewRequired()).isTrue();
        assertThat(r.rec().missingInformation()).contains("authentication challenge outcome");
        assertThat(r.rec().policyCitations()).isNotEmpty();
        assertThat(r.rec().policyCitations()).anyMatch(c -> c.documentId().equals("POL-CNP-017") && c.version().equals("3.2"));
        assertThat(r.rec().ruleIds()).contains("R-03");
        assertThat(r.outcome().status()).isEqualTo(CaseStatus.READY_FOR_REVIEW);
        List<String> tools = queries.traces(r.caseId()).stream().map(ToolTraceEntity::getToolName).distinct().toList();
        assertThat(tools).contains("getTransactionDetails", "getDeviceSignals", "compareRecentLocations", "getMerchantRiskProfile",
                "getPriorDisputes", "getTransactionVelocity", "runComplianceScreening", "retrievePolicyEvidence", "calculateCaseSla");
        assertThat(r.rec().priority()).isIn("P1", "P2");
    }

    @Test
    void t02_recognisedMerchantFulfilmentIsMerchantDispute() {
        Run r = run(TestCases.merchantDispute());
        assertThat(r.rec().classification()).isEqualTo("MERCHANT_DISPUTE");
        assertThat(r.rec().recommendedRoute()).isEqualTo("DISPUTE_OPERATIONS");
        assertThat(r.rec().policyCitations()).anyMatch(c -> c.documentId().equals("POL-MDR-009"));
    }

    @Test
    void t03_duplicateFingerprintRoutesToPaymentOperations() {
        Run r = run(TestCases.duplicate());
        assertThat(r.rec().classification()).isEqualTo("DUPLICATE_PROCESSING");
        assertThat(r.rec().recommendedRoute()).isEqualTo("PAYMENT_OPERATIONS");
        assertThat(r.rec().policyCitations()).anyMatch(c -> c.documentId().equals("POL-DUP-004"));
    }

    @Test
    void t04_missingTransactionIsInsufficientEvidenceWithoutInventedFacts() {
        Run r = run(TestCases.missingTransaction());
        assertThat(r.rec().classification()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(r.rec().recommendedRoute()).isEqualTo("MANUAL_REVIEW");
        assertThat(r.rec().missingInformation()).anyMatch(m -> m.startsWith("transaction record unavailable (NOT_FOUND"));
        assertThat(r.outcome().status()).isEqualTo(CaseStatus.INCOMPLETE);
        EvidenceItemEntity txn = queries.evidence(r.caseId()).stream().filter(e -> e.getType() == EvidenceType.TRANSACTION).findFirst().orElseThrow();
        assertThat(txn.isAvailable()).isFalse();
        assertThat(txn.getValueSummary()).contains("No value assumed");
    }

    @Test
    void t05_noPolicyAboveThresholdAbstainsAndRoutesToManualReview() {
        Run r = run(TestCases.noPolicy());
        assertThat(r.rec().policyCitations()).isEmpty();
        assertThat(r.rec().recommendedRoute()).isEqualTo("MANUAL_REVIEW");
        assertThat(r.rec().ruleIds()).contains("R-05");
        assertThat(r.rec().rationale()).contains("no procedure is asserted").doesNotContain("POL-");
        assertThat(queries.latestRun(r.caseId()).orElseThrow().getRetrievalStatus()).isEqualTo("ABSTAINED");
    }

    @Test
    void t06_contradictoryDeviceAndLocationReducesConfidenceAndGoesToManualReview() {
        Run r = run(TestCases.contradictory());
        assertThat(r.rec().contradictions()).isNotEmpty();
        assertThat(r.rec().recommendedRoute()).isEqualTo("MANUAL_REVIEW");
        assertThat(r.rec().confidence()).isLessThanOrEqualTo(0.5);
        assertThat(r.rec().ruleIds()).contains("R-06");
    }

    @Test
    void t08_toolTimeoutBecomesStructuredUnavailableEvidenceAndMissingInformation() {
        Run r = run(TestCases.deviceTimeout());
        ToolTraceEntity device = queries.traces(r.caseId()).stream().filter(t -> t.getToolName().equals("getDeviceSignals")).findFirst().orElseThrow();
        assertThat(device.getStatus()).isEqualTo(ToolStatus.TIMEOUT);
        assertThat(r.rec().missingInformation()).anyMatch(m -> m.startsWith("device signals unavailable (TIMEOUT"));
        assertThat(r.rec().classification()).isEqualTo("UNAUTHORISED_TRANSACTION");
    }

    @Test
    void t11_promptInjectionInStatementIsFlaggedAndIgnored() {
        Run r = run(TestCases.injection());
        assertThat(queries.evidence(r.caseId())).anyMatch(e -> e.getType() == EvidenceType.SECURITY_FLAG);
        assertThat(r.rec().classification()).isNotEqualTo("GENUINE_TRANSACTION");
        assertThat(r.rec().humanReviewRequired()).isTrue();
        assertThat(r.rec().rationale().toLowerCase()).doesNotContain("refund issued").contains("no automated action");
        assertThat(queries.audit(r.caseId())).anyMatch(a -> a.getEventType().equals("PROMPT_INJECTION_FLAGGED"));
    }

    @Test
    void genuineActivityIndicatorsRouteToManualReview() {
        Run r = run(TestCases.genuine());
        assertThat(r.rec().classification()).isEqualTo("GENUINE_TRANSACTION");
        assertThat(r.rec().recommendedRoute()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void velocityBurstIsRecordedAsHighSeverityEvidence() {
        Run r = run(TestCases.sample());
        assertThat(queries.evidence(r.caseId())).anyMatch(e -> e.getType() == EvidenceType.VELOCITY && e.getSeverity() == Severity.HIGH
                && e.getValueSummary().contains("6 transaction(s) in 10 min"));
    }

    @Test
    void t12_confirmedScreeningMatchRecommendsAmlInvestigationWithoutActing() {
        Run r = run(TestCases.screeningMatch());
        assertThat(r.rec().recommendedRoute()).isEqualTo("AML_INVESTIGATION");
        assertThat(r.rec().classification()).isIn("SUSPICIOUS_TRANSACTION", "UNAUTHORISED_TRANSACTION");
        assertThat(r.rec().riskLevel()).isIn("HIGH", "CRITICAL");
        assertThat(r.rec().ruleIds()).contains("R-10");
        assertThat(r.rec().humanReviewRequired()).isTrue();
        assertThat(r.rec().policyCitations()).anyMatch(c -> c.documentId().equals("POL-ESC-011"));
        assertThat(queries.evidence(r.caseId())).anyMatch(e -> e.getType() == EvidenceType.COMPLIANCE && e.getSeverity() == Severity.HIGH);
        assertThat(r.rec().rationale()).contains("no automated action");
    }

    @Test
    void identicalInputProducesIdenticalToolEvidence() {
        Run a = run(TestCases.sample());
        Run b = run(TestCases.sample());
        List<String> ea = queries.evidence(a.caseId()).stream().map(e -> e.getType() + "|" + e.getSeverity() + "|" + e.getValueSummary()).toList();
        List<String> eb = queries.evidence(b.caseId()).stream().map(e -> e.getType() + "|" + e.getSeverity() + "|" + e.getValueSummary()).toList();
        assertThat(ea).isEqualTo(eb);
        assertThat(a.rec().classification()).isEqualTo(b.rec().classification());
        assertThat(a.rec().confidence()).isEqualTo(b.rec().confidence());
    }
}
