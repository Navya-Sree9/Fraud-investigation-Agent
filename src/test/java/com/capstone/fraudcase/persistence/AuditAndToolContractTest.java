package com.capstone.fraudcase.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CardPossession;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.domain.IntakeChannel;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.domain.PaymentChannel;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.domain.TransactionStatus;
import com.capstone.fraudcase.knowledge.PolicyRetriever;
import com.capstone.fraudcase.observability.Redactor;
import com.capstone.fraudcase.tools.BoundCaseToolbox;
import com.capstone.fraudcase.tools.EvidenceTools;
import com.capstone.fraudcase.tools.RunScope;
import com.capstone.fraudcase.tools.ToolGateway;
import com.capstone.fraudcase.tools.ToolNames;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Repository and tool-contract tests: append-only audit, hash chain, tool scope, budget, determinism and redaction. */
@SpringBootTest
@ActiveProfiles("test")
class AuditAndToolContractTest {

    @Autowired AuditWriter audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired EvidenceTools tools;
    @Autowired ToolGateway gateway;
    @Autowired PolicyRetriever retriever;

    private static CaseFacts facts(String txn) {
        return new CaseFacts("CASE-T", "CUST-••••-4821", "IN-MH Mumbai", OffsetDateTime.parse("2026-11-06T09:00:00Z"), IntakeChannel.PHONE,
                "analyst", txn, new BigDecimal("100.00"), "INR", OffsetDateTime.parse("2026-11-06T08:42:00+05:30"), TransactionStatus.POSTED,
                PaymentChannel.CARD_NOT_PRESENT, "NORTHSTAR DIGITAL ••••73", IssueType.MERCHANT_NOT_RECOGNISED, "statement text here",
                CardPossession.IN_POSSESSION, false);
    }

    @Test
    void auditEventsAreAppendOnlyAndHashChained() {
        AuditEventEntity e = audit.append(null, "TEST_EVENT", ActorType.SYSTEM, "test", "summary", "secret payload");
        assertThat(e.getPayloadHash()).hasSize(64).isNotEqualTo("secret payload");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET summary = 'tampered' WHERE event_id = ?", e.getEventId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE event_id = ?", e.getEventId()))
                .hasMessageContaining("append-only");
        assertThat(audit.verifyChain()).isNull();
    }

    @Test
    void stateMachineAllowsOnlyDocumentedTransitions() {
        assertThat(CaseStateMachine.canTransition(CaseStatus.NEW, CaseStatus.ENRICHING)).isTrue();
        assertThat(CaseStateMachine.canTransition(CaseStatus.NEW, CaseStatus.CLOSED)).isFalse();
        assertThat(CaseStateMachine.canTransition(CaseStatus.READY_FOR_REVIEW, CaseStatus.ROUTED)).isFalse();
        assertThat(CaseStateMachine.canTransition(CaseStatus.UNDER_REVIEW, CaseStatus.ROUTED)).isTrue();
        assertThat(CaseStateMachine.nextStates(CaseStatus.CLOSED)).isEmpty();
    }

    @Test
    void toolsAreDeterministicForIdenticalInput() {
        assertThat(tools.getDeviceSignals(facts("TXN-2026-000111"))).isEqualTo(tools.getDeviceSignals(facts("TXN-2026-000111")));
        assertThat(tools.compareRecentLocations(facts("TXN-2026-000111"))).isEqualTo(tools.compareRecentLocations(facts("TXN-2026-000111")));
        assertThat(tools.getMerchantRiskProfile(facts("TXN-2026-000111"))).isEqualTo(tools.getMerchantRiskProfile(facts("TXN-2026-000111")));
        assertThat(tools.getTransactionVelocity(facts("TXN-2026-784102"))).isEqualTo(tools.getTransactionVelocity(facts("TXN-2026-784102")));
        assertThat(tools.getTransactionVelocity(facts("TXN-2026-784102")).burst()).isTrue();
        assertThat(tools.runComplianceScreening(facts("TXN-2026-777301"))).isEqualTo(tools.runComplianceScreening(facts("TXN-2026-777301")));
        assertThat(tools.runComplianceScreening(facts("TXN-2026-777301")).confirmedMatch()).isTrue();
    }

    @Test
    void modelToolboxRejectsOutOfScopeArgumentsInvalidEnumsAndBudgetOverrun() {
        jdbc.update("INSERT INTO fraud_case (case_id, masked_customer_ref, home_region, status, intake_channel, reported_at, created_by, "
                + "synthetic_data_confirmed, review_required, current_run, created_at, updated_at) VALUES ('CASE-T','CUST-••••-4821','IN','NEW',"
                + "'PHONE', CURRENT_TIMESTAMP, 'analyst', TRUE, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        RunScope scope = new RunScope("CASE-T", 1, "corr-test-1", facts("TXN-2026-784102"));
        BoundCaseToolbox box = new BoundCaseToolbox(gateway, tools, retriever, scope, 4, 0.72);
        assertThat(box.getTransactionDetails("TXN-2026-999999").status()).isEqualTo(ToolStatus.OUT_OF_SCOPE);
        assertThat(box.getDeviceSignals("CUST-••••-0000").status()).isEqualTo(ToolStatus.OUT_OF_SCOPE);
        assertThat(box.getDeviceSignals("CUST-****-4821").status()).isEqualTo(ToolStatus.SUCCESS);
        assertThat(box.calculateCaseSla("REFUND_CUSTOMER", "HIGH").status()).isEqualTo(ToolStatus.INVALID_ARGUMENT);
        ToolEnvelope last = null;
        for (int i = 0; i < 6; i++) {
            last = box.getPriorDisputes("CUST-••••-4821");
        }
        assertThat(last.status()).isEqualTo(ToolStatus.OUT_OF_SCOPE);
        assertThat(last.summary()).contains("budget");
        assertThat(box.traceIds()).hasSize(10);
        ToolEnvelope auditAttempt = gateway.execute(scope, ToolNames.AUDIT, "MODEL", Map.of(), () -> "x");
        assertThat(auditAttempt.status()).isEqualTo(ToolStatus.OUT_OF_SCOPE);
    }

    @Test
    void redactorMasksCardNumbersEmailsAndSecrets() {
        String out = Redactor.redact("card 4111-1111-1111-1111 mail a.b@example.com key sk-abcdefghijk ref TXN-2026-784102", 500);
        assertThat(out).doesNotContain("4111").doesNotContain("example.com").doesNotContain("sk-abcdef").contains("TXN-2026-784102");
    }
}
