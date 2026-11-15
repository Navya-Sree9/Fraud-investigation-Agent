# Architecture

## Layered view

```
┌──────────────────────────── Experience ────────────────────────────┐
│ Thymeleaf UI (web/CaseWebController)   REST API (api/CaseApiController) │
└───────────────┬────────────────────────────────────┬───────────────┘
                │ Spring Security (roles, CSRF, CSP)  │ CorrelationIdFilter
┌───────────────▼──────────── Application ───────────▼───────────────┐
│ FraudCaseService (intake)   InvestigationOrchestrator (plan→act→     │
│ CaseStateService (state machine)   retrieve→assess→recommend)        │
│ ReviewService (human disposition)  FollowUpService  CaseQueryService │
│ InvestigationPlanner  EvidenceMapper  ProcessorRegistry              │
└──────┬───────────────────┬──────────────────────┬──────────────────┘
       │                   │ FraudCaseProcessor   │
┌──────▼──────┐  ┌─────────▼────────────────────────────────────────┐
│ Tools       │  │ agent/common: FraudCaseContext, CaseToolbox,      │
│ ToolGateway │  │   RecommendationParser, RecommendationValidator,  │
│ EvidenceTools│ │   PromptLibrary, CasePromptRenderer, CaseMemoryStore│
│ BoundCase-  │  ├──────────────┬────────────────┬──────────────────┤
│  Toolbox    │  │ agent/rules  │ agent/springai │ agent/langchain4j│
│ Fixtures    │  │ Rules agent  │ ChatClient,    │ AiServices, @Tool,│
└──────┬──────┘  │ (R-01..R-09) │ @Tool, memory  │ ChatMemoryStore   │
       │         └──────────────┴───────┬────────┴──────────────────┘
┌──────▼──────────── Knowledge ─────────▼──┐   ┌──── Persistence ─────┐
│ PolicyDocumentParser → PolicyIngestion-  │   │ JPA entities, Flyway │
│ Service → MiniLM embeddings → Policy-    │   │ AuditWriter (hash    │
│ VectorStore → PolicyRetriever (Top-K,    │   │ chain, DB trigger)   │
│ threshold, abstain)                      │   │ H2                   │
└──────────────────────────────────────────┘   └──────────────────────┘
```

The domain (`domain/`) holds framework-neutral enums, `CaseFacts`, `CaseStateMachine`, `ClassificationRoutes` and `Hashing`.

## Data flow for one investigation

1. **Intake** (`POST /api/cases`): bean validation, masking patterns, a PAN/markup check, and rejection of unknown fields. The original and sanitised statements are both stored; audit `CASE_CREATED` records a hash only.
2. **Plan**: `CaseStateService.startEnrichment` (NEW/INCOMPLETE → ENRICHING) runs, then `InvestigationPlanner` creates a bounded plan of at most 8 steps based on the issue type.
3. **Act**: the planned tools run through `ToolGateway` (allow-list, 2.5 s timeout, structured failures, redacted `ToolTrace`). Each result becomes an `EvidenceItem` with severity and provenance. Unavailable tools become explicit unavailable evidence.
4. **Retrieve**: a query is built from the issue type and evidence indicators, then `retrievePolicyEvidence` runs (Top-K 4, threshold 0.72). Every candidate is stored with an above/below-threshold flag; withdrawn and superseded versions are never embedded.
5. **Assess/Recommend**: the selected `FraudCaseProcessor` receives a `FraudCaseContext`. AI adapters get identical prompts and a `BoundCaseToolbox`, so a model can call read-only tools for this case only, within a budget of 8 calls. Each call is traced as `invokedBy=MODEL`.
6. **Validate**: `RecommendationParser` (strict JSON → DTO) and `RecommendationValidator` check enums, the confidence range, `humanReviewRequired`, known evidence/trace IDs, and that citations match retrieved passages (exact metadata is substituted). They also check for procedural claims without citations, abstention → MANUAL_REVIEW, the route mapping, and claims of completed actions. On failure the orchestrator audits `MODEL_OUTPUT_REJECTED`/`MODEL_UNAVAILABLE` and falls back to rules.
7. **Persist**: SLA is calculated for the route, then the recommendation and cited passages are saved. The case moves to READY_FOR_REVIEW (or INCOMPLETE for insufficient evidence).
8. **Review**: `ReviewService` requires a rationale of at least 15 characters, acknowledgement of missing information and the REVIEWER role. It records the disposition and moves the case to UNDER_REVIEW → ROUTED/CLOSED. Everything is audited.

## Mode selection

`ProcessorRegistry` maps `ProcessorMode` to its processor. The default is `capstone.processor-mode`; each run can override it.
The AI chat model beans (`SpringAiConfig`, `LangChain4jConfig`) are created only when `capstone.llm.api-key` is non-blank,
so with no key the AI processors report `available() == false` and rules mode is used.

## Memory

`CaseMemoryStore` holds a bounded (12-message) conversation in the DB, keyed by case ID, so it survives restarts.
Spring AI uses `MessageWindowChatMemory` + `MessageChatMemoryAdvisor` over `SpringAiCaseChatMemoryRepository`.
LangChain4j uses `MessageWindowChatMemory` + `@MemoryId` over `LangChain4jCaseChatMemoryStore`. Analysis calls are
stateless; only short follow-up turns are kept, so full case payloads are not stored in long-lived memory.

## Spring AI vs LangChain4j (same fixtures, model, prompts and validator)

| Aspect | Spring AI | LangChain4j |
|---|---|---|
| Wiring | `ChatClient.prompt().system().user().tools()` | `AiServices.builder(...).tools().chatMemoryProvider()` |
| Tools | `@Tool`/`@ToolParam` returning `ToolEnvelope` | `@Tool`/`@P` returning JSON string |
| Memory | `ChatMemoryRepository` + advisor, conversation-ID param | `ChatMemoryStore` + `@MemoryId`; the system message had to be kept transiently because the store persists only user/assistant turns |
| Observed live | Valid, grounded output for the sample case in about 12 s | Valid output, with a real model tool call (`calculateCaseSla`) |
| Complexity | Slightly less code for memory | Tool-loop control (`maxSequentialToolsInvocations`) is explicit |

Neither framework is claimed to be superior; business rules, schema and validation are identical by construction.
