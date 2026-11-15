package com.capstone.fraudcase.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.fraudcase.TestCases;
import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.application.CaseQueryService.RecommendationView;
import com.capstone.fraudcase.application.FollowUpService;
import com.capstone.fraudcase.application.FraudCaseService;
import com.capstone.fraudcase.application.InvestigationOrchestrator;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Adapter contract: both AI adapters return the same schema and invariants as rules mode; invalid output (T09)
 * and model outages (T07) fail safely to the rules processor; follow-up memory is isolated by case (T10).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ScriptedModels.Config.class)
class AiAdapterContractTest {

    @Autowired FraudCaseService cases;
    @Autowired InvestigationOrchestrator orchestrator;
    @Autowired CaseQueryService queries;
    @Autowired FollowUpService followUps;

    @BeforeEach
    void reset() {
        ScriptedModels.behaviour = ScriptedModels.Behaviour.VALID;
        ScriptedModels.springPrompts.clear();
        ScriptedModels.lc4jPrompts.clear();
    }

    @AfterEach
    void restore() {
        ScriptedModels.behaviour = ScriptedModels.Behaviour.VALID;
    }

    private RecommendationView investigate(String mode, String[] caseIdOut) {
        String caseId = cases.createCase(TestCases.unique(TestCases.sample()), "analyst").caseId();
        caseIdOut[0] = caseId;
        orchestrator.investigate(caseId, "analyst", mode);
        return queries.recommendation(caseId).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring-ai", "langchain4j", "rules"})
    void everyProcessorReturnsTheSameValidatedContract(String mode) {
        String[] id = new String[1];
        RecommendationView rec = investigate(mode, id);
        assertThat(rec.mode()).isEqualTo(mode);
        assertThat(rec.humanReviewRequired()).isTrue();
        assertThat(rec.recommendedRoute()).isEqualTo("FRAUD_INVESTIGATION");
        assertThat(rec.classification()).isIn("UNAUTHORISED_TRANSACTION", "SUSPICIOUS_TRANSACTION");
        assertThat(rec.evidenceIds()).isNotEmpty();
        assertThat(rec.policyCitations()).isNotEmpty().allSatisfy(c -> {
            assertThat(c.documentId()).isNotBlank();
            assertThat(c.chunkId()).isNotBlank();
            assertThat(c.score()).isBetween(0.0, 1.0);
        });
        assertThat(rec.missingInformation()).contains("authentication challenge outcome");
    }

    @org.junit.jupiter.api.Test
    void promptContractIsIdenticalAcrossFrameworksAndTreatsStatementAsData() {
        String[] id = new String[1];
        investigate("spring-ai", id);
        investigate("langchain4j", id);
        String spring = ScriptedModels.springPrompts.getFirst();
        String lc4j = ScriptedModels.lc4jPrompts.getFirst();
        assertThat(spring).contains("TRIAGE ASSISTANT", "<customer_statement", "OUTPUT SCHEMA", "CITABLE");
        assertThat(lc4j).contains("TRIAGE ASSISTANT", "<customer_statement", "OUTPUT SCHEMA", "CITABLE");
    }

    @org.junit.jupiter.api.Test
    void langChain4jToolCallIsExecutedThroughTheGatewayAndTraced() {
        String[] id = new String[1];
        RecommendationView rec = investigate("langchain4j", id);
        ToolTraceEntity modelCall = queries.traces(id[0]).stream().filter(t -> "MODEL".equals(t.getInvokedBy())).findFirst().orElseThrow();
        assertThat(modelCall.getToolName()).isEqualTo("calculateCaseSla");
        assertThat(rec.toolTraceIds()).contains(modelCall.getTraceId());
    }

    @org.junit.jupiter.api.Test
    void t09_unknownEnumIsRejectedAndNotPersistedAsModelRecommendation() {
        ScriptedModels.behaviour = ScriptedModels.Behaviour.UNKNOWN_ENUM;
        String[] id = new String[1];
        RecommendationView rec = investigate("spring-ai", id);
        assertThat(rec.mode()).isEqualTo("rules (fallback from spring-ai)");
        assertThat(rec.classification()).isNotEqualTo("PROBABLY_FRAUD");
        assertThat(queries.audit(id[0])).anyMatch(a -> a.getEventType().equals("MODEL_OUTPUT_REJECTED")
                && a.getSummary().contains("unknown classification value 'PROBABLY_FRAUD'"));
    }

    @org.junit.jupiter.api.Test
    void inventedEvidenceIdIsRejected() {
        ScriptedModels.behaviour = ScriptedModels.Behaviour.INVENTED_EVIDENCE;
        String[] id = new String[1];
        RecommendationView rec = investigate("langchain4j", id);
        assertThat(rec.mode()).startsWith("rules (fallback");
        assertThat(rec.evidenceIds()).doesNotContain("EV-999999");
        assertThat(queries.audit(id[0])).anyMatch(a -> a.getSummary().contains("unknown evidence ID 'EV-999999'"));
    }

    @org.junit.jupiter.api.Test
    void t07_modelOutageFallsBackToRulesWithoutLeakingDetails() {
        ScriptedModels.behaviour = ScriptedModels.Behaviour.THROW;
        String[] id = new String[1];
        RecommendationView rec = investigate("spring-ai", id);
        assertThat(rec.mode()).isEqualTo("rules (fallback from spring-ai)");
        assertThat(queries.latestRun(id[0]).orElseThrow().getFallbackReason()).isEqualTo("Model unavailable (IllegalStateException)")
                .doesNotContain("simulated model endpoint");
        assertThat(queries.audit(id[0])).anyMatch(a -> a.getEventType().equals("MODEL_UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring-ai", "langchain4j", "rules"})
    void t10_factFromCaseAIsNotRecalledInCaseB(String mode) {
        String caseA = cases.createCase(TestCases.unique(TestCases.sample()), "analyst").caseId();
        String caseB = cases.createCase(TestCases.unique(TestCases.merchantDispute()), "analyst").caseId();
        followUps.ask(caseA, "Remember this codeword for the investigation: PELICAN", "analyst", mode);
        FollowUpService.Answer inA = followUps.ask(caseA, "What codeword did I mention?", "analyst", mode);
        FollowUpService.Answer inB = followUps.ask(caseB, "What codeword did I mention?", "analyst", mode);

        assertThat(followUps.conversation(caseB)).noneMatch(m -> m.getContent().contains("PELICAN"));
        assertThat(inB.answer()).doesNotContain("PELICAN");
        if (!"rules".equals(mode)) {
            assertThat(inA.answer()).contains("PELICAN");
            var prompts = "spring-ai".equals(mode) ? ScriptedModels.springPrompts : ScriptedModels.lc4jPrompts;
            assertThat(prompts.getLast()).contains(caseB).doesNotContain("PELICAN");
        }
    }
}
