package com.capstone.fraudcase;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Labelled evaluation (guide section 9.3) over the synthetic case set in rules mode. Writes
 * target/evaluation-report.md with schema validity, classification/route accuracy, citation correctness,
 * abstention, tool coverage, human-review rate and latency.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationSuiteTest {

    record Label(String name, CreateCaseRequest request, Set<String> classifications, String route, String expectedDocument) {
    }

    @Autowired FraudCaseService cases;
    @Autowired InvestigationOrchestrator orchestrator;
    @Autowired CaseQueryService queries;

    @Test
    void labelledSuiteMeetsQualityBar() throws Exception {
        List<Label> labels = List.of(
                new Label("T01 unrecognised CNP", TestCases.sample(), Set.of("UNAUTHORISED_TRANSACTION", "SUSPICIOUS_TRANSACTION"), "FRAUD_INVESTIGATION", "POL-CNP-017"),
                new Label("T02 goods not received", TestCases.merchantDispute(), Set.of("MERCHANT_DISPUTE"), "DISPUTE_OPERATIONS", "POL-MDR-009"),
                new Label("T03 duplicate", TestCases.duplicate(), Set.of("DUPLICATE_PROCESSING"), "PAYMENT_OPERATIONS", "POL-DUP-004"),
                new Label("T04 missing txn", TestCases.missingTransaction(), Set.of("INSUFFICIENT_EVIDENCE"), "MANUAL_REVIEW", null),
                new Label("T05 no policy", TestCases.noPolicy(), Set.of("INSUFFICIENT_EVIDENCE"), "MANUAL_REVIEW", "ABSTAIN"),
                new Label("T06 contradictory", TestCases.contradictory(), Set.of("SUSPICIOUS_TRANSACTION"), "MANUAL_REVIEW", null),
                new Label("Genuine activity", TestCases.genuine(), Set.of("GENUINE_TRANSACTION"), "MANUAL_REVIEW", null),
                new Label("T08 tool timeout", TestCases.deviceTimeout(), Set.of("UNAUTHORISED_TRANSACTION"), "FRAUD_INVESTIGATION", "POL-CNP-017"),
                new Label("T12 screening match", TestCases.screeningMatch(), Set.of("SUSPICIOUS_TRANSACTION", "UNAUTHORISED_TRANSACTION"), "AML_INVESTIGATION", "POL-ESC-011"),
                new Label("T11 injection", TestCases.injection(), Set.of("UNAUTHORISED_TRANSACTION", "SUSPICIOUS_TRANSACTION"), "FRAUD_INVESTIGATION", "POL-CNP-017"));

        int valid = 0, classOk = 0, routeOk = 0, citationChecks = 0, citationOk = 0, abstainChecks = 0, abstainOk = 0, review = 0;
        Set<String> toolsUsed = new HashSet<>();
        List<Long> latencies = new ArrayList<>();
        StringBuilder rows = new StringBuilder();
        for (Label l : labels) {
            String caseId = cases.createCase(TestCases.unique(l.request()), "evaluator").caseId();
            long t0 = System.nanoTime();
            orchestrator.investigate(caseId, "evaluator", "rules");
            latencies.add((System.nanoTime() - t0) / 1_000_000);
            var rec = queries.recommendation(caseId);
            if (rec.isEmpty()) {
                rows.append("| %s | no recommendation | | | |\n".formatted(l.name()));
                continue;
            }
            var r = rec.get();
            valid++;
            boolean c = l.classifications().contains(r.classification());
            boolean ro = l.route().equals(r.recommendedRoute());
            classOk += c ? 1 : 0;
            routeOk += ro ? 1 : 0;
            review += r.humanReviewRequired() ? 1 : 0;
            if ("ABSTAIN".equals(l.expectedDocument())) {
                abstainChecks++;
                abstainOk += r.policyCitations().isEmpty() && "MANUAL_REVIEW".equals(r.recommendedRoute()) ? 1 : 0;
            } else if (l.expectedDocument() != null) {
                citationChecks++;
                citationOk += r.policyCitations().stream().anyMatch(p -> p.documentId().equals(l.expectedDocument())) ? 1 : 0;
            }
            queries.traces(caseId).stream().map(ToolTraceEntity::getToolName).forEach(toolsUsed::add);
            rows.append("| %s | %s | %s | %s | %.2f |\n".formatted(l.name(), r.classification(), r.recommendedRoute(),
                    r.policyCitations().isEmpty() ? "abstained" : r.policyCitations().getFirst().chunkId(), r.confidence()));
        }
        latencies.sort(Long::compare);
        long median = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        int n = labels.size();
        String report = """
                # Evaluation report (rules mode, synthetic labelled set)

                | Measure | Result |
                |---|---|
                | Schema-valid recommendations | %d/%d |
                | Classification accuracy | %d/%d |
                | Route accuracy | %d/%d |
                | Citation correctness (expected document cited) | %d/%d |
                | Abstention pass rate | %d/%d |
                | Human-review flag rate | %d/%d |
                | Distinct tools invoked | %d %s |
                | Latency median / p95 | %d ms / %d ms |

                | Case | Classification | Route | Top citation | Confidence |
                |---|---|---|---|---|
                %s""".formatted(valid, n, classOk, n, routeOk, n, citationOk, citationChecks, abstainOk, abstainChecks, review, n,
                toolsUsed.size(), toolsUsed.stream().sorted().toList(), median, p95, rows);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/evaluation-report.md"), report);
        System.out.println(report);

        assertThat(valid).isEqualTo(n);
        assertThat(classOk).isEqualTo(n);
        assertThat(routeOk).isEqualTo(n);
        assertThat(citationOk).isEqualTo(citationChecks);
        assertThat(abstainOk).isEqualTo(abstainChecks);
        assertThat(review).isEqualTo(n);
        assertThat(toolsUsed).hasSizeGreaterThanOrEqualTo(4);
    }
}
