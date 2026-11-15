# Capstone Self-Evaluation Report

## 1. Executive Summary
- Project: Intelligent Payment Fraud Investigation & Dispute Resolution Agent (`com.capstone:fraud-case-agent` 1.0.0)
- Primary framework: Spring AI 1.1.8 (`OpenAiChatModel` against an OpenAI-compatible gateway fronting Anthropic Claude)
- Secondary framework: LangChain4j 1.22.0 (AI Services + `dev.langchain4j.model.openai.OpenAiChatModel`), plus a deterministic `rules` processor as the safety baseline
- Build status: `mvn clean test` → **BUILD SUCCESS** (verified 2026-11-15, ~60 s)
- Test status: **52 tests run, 0 failures, 0 errors, 0 skipped**
- Overall score: 95/100
- Percentage: 95%
- Readiness: Demo Ready
- Scoring model used: Both frameworks present → metric 4 and metric 5 each scored out of 10; total remains 100.

## 2. Repository and Technology Overview
| Area | Finding | Evidence |
|---|---|---|
| Application name / objective | `fraud-case-agent` — agentic investigation of payment-fraud and transaction-dispute cases producing advisory recommendations; final decision always a human reviewer's | `pom.xml`, `FraudCaseApplication.java`, `README.md` |
| Java / Spring Boot | Java 21 (`release 21`), Spring Boot 3.5.16 | `pom.xml` |
| Build tool | Maven (no wrapper present — `mvn` required) | `pom.xml` |
| AI framework | **Both** Spring AI and LangChain4j, behind a framework-neutral contract | `agent/common/FraudCaseProcessor.java`, `agent/springai/*`, `agent/langchain4j/*` |
| Model provider / config | OpenAI-compatible gateway `https://llmgw-wp.tekstac.com`; model `global.anthropic.claude-haiku-4-5-20251001-v1:0` default (Sonnet/Opus IDs supported via env). Key via `FRAUD_LLM_API_KEY`, never in source | `application.yml` (`capstone.llm`), `.env` (git-ignored), `.env.example` |
| Database / persistence | H2 (file + in-mem for tests), Spring Data JPA, Flyway migration `V1__fraud_case_schema.sql`; 10 JPA repositories | `persistence/*`, `db/migration/` |
| Vector store / embeddings | Custom in-memory cosine-similarity store (`PolicyVectorStore`); local all-MiniLM-L6-v2 ONNX embeddings via `langchain4j-embeddings-all-minilm-l6-v2` — no remote embedding call needed | `knowledge/PolicyVectorStore.java`, `knowledge/MiniLmEmbeddingProvider.java` |
| UI technology | Thymeleaf + `thymeleaf-extras-springsecurity6`, external CSS/JS only (strict CSP) | `templates/`, `static/` |
| MCP / external integration | None — not applicable; tool boundary is an internal allow-listed toolbox instead | `tools/BoundCaseToolbox.java`, `tools/ToolGateway.java` |
| Main entry point | `com.capstone.fraudcase.FraudCaseApplication` | `FraudCaseApplication.java` |
| Framework-neutral interfaces | `FraudCaseProcessor` (contract), `PolicyRetriever`, `CaseMemoryStore`, `EvidenceTools`/`CaseToolbox`, `CaseStateMachine`, `RecommendationParser`/`RecommendationValidator` | `agent/common/`, `knowledge/`, `tools/` |
| Configuration modes | `capstone.processor-mode` = `rules` \| `spring-ai` \| `langchain4j` (env `FRAUD_PROCESSOR_MODE`); per-run mode selectable in UI/API | `application.yml:56`, `ProcessorRegistry.java`, `ProcessorMode.java` |
| Personas | Four guide personas as least-privilege roles: `agent` (service agent), `investigator`, `reviewer`, `auditor` | `security/Roles.java`, `security/SecurityConfig.java` |

## 3. Build and Test Results
| Command | Result | Details |
|---|---|---|
| `mvn clean test` | **BUILD SUCCESS** (~60 s) | **52 tests, 0 failures, 0 errors, 0 skipped** (Surefire) |
| `mvn package` | Success | `target/fraud-case-agent-1.0.0.jar` ~278 MB (embeds MiniLM ONNX) |
| Compile warnings | 0 | Deprecated `maxSequentialToolsInvocations` replaced by `maxToolCallingRoundTrips` (post-evaluation fix) |
| Coverage | Not configured | No JaCoCo/plugin — coverage percentage unavailable (gap) |
| External-dependency failures | None | Tests never call the live model (`application-test.yml` blanks `api-key`; scripted models used) |

Test classes: `FraudScenarioIntegrationTest` (T01–T08, T11–T12 + velocity-burst evidence test), `CaseApiSecurityTest` (US-01/06, T04, T12, persona boundaries, start-review), `AiAdapterContractTest` (parity, memory isolation, fallback), `RecommendationContractTest`, `PolicyRetrievalTest` (RAG incl. abstention/withdrawn), `AuditAndToolContractTest` (append-only, hash chain, redaction, timeout), `EvaluationSuiteTest` (labelled-set metrics → `target/evaluation-report.md`).

## 4. Evaluation Scorecard
| # | Metric | Maximum | Score | Status | Evaluator Comments | Evidence |
|---|---|---:|---:|---|---|---|
| 1 | BFS fraud and dispute use case | 10 | 10 | Implemented | Full intake→lifecycle→advisory-recommendation→human-review loop; all four required classifications plus `MERCHANT_DISPUTE`/`DUPLICATE_PROCESSING`; separate queues for fraud, dispute ops, payment ops and AML (`AML_INVESTIGATION` route added post-evaluation, R-10-driven, verified in T12); no loan-underwriting terms anywhere. | `domain/Classification.java`, `domain/CaseStatus.java`, `domain/CaseStateMachine.java`, `domain/Route.java` |
| 2 | Architecture and maintainability | 12 | 11 | Implemented | Clean layered packages (api/application/agent/tools/knowledge/persistence/security/web/observability); adapters isolated behind `FraudCaseProcessor`; shared parser/validator/orchestrator so business rules are defined once; typed enums throughout. Deduction: minor remaining coupling — the API controller composes query-service DTOs directly (no separate response layer). | Package tree; `ProcessorRegistry.java`; `RecommendationValidator.java` |
| 3 | Fraud investigation workflow and deterministic tools | 15 | 15 | Implemented | 9 allow-listed, read-only tools over deterministic fixtures — transaction, device, location, merchant, prior disputes, **transaction velocity (10/60-min/24-h windows, 5+/10-min burst indicator)** and **synthetic sanctions/PEP screening** added post-evaluation; per-call timeout (TIMEOUT recorded, evidence marked unavailable — verified live TR-139); trace IDs, provenance, redaction; validator rejects invented evidence IDs so the model cannot fabricate tool output. | `tools/EvidenceTools.java`, `ToolGateway.java`, `fixtures/synthetic-evidence.json` |
| 4 | Spring AI implementation | 10 | 9 | Implemented | `OpenAiChatModel` built from external config; `@Tool` methods on a case-bound toolbox; system+user prompt templates; validated JSON→DTO parse; `ChatMemoryRepository` keyed by case ID; failure→rules fallback audited (`MODEL_OUTPUT_REJECTED`). Deduction: uses OpenAI-chat adapter rather than native Anthropic support; structured output via validated parsing rather than `entity()`. | `agent/springai/SpringAiConfig.java`, `SpringAiEvidenceTools.java`, `SpringAiFraudCaseAgent.java` |
| 5 | LangChain4j implementation | 10 | 9 | Implemented | `AiServices` interfaces with `@SystemMessage`/`@UserMessage`/`@MemoryId`; `MessageWindowChatMemory` + case-keyed `ChatMemoryStore`; `@Tool` registration over the same toolbox; live tool call observed end-to-end; same validated DTO contract. Deduction: deprecated API warning; retrieval injected in prompt rather than a `RetrievalAugmentor`. | `agent/langchain4j/LangChain4jFraudCaseAgent.java`, `FraudInvestigationService.java` |
| 6 | RAG, embeddings, vector retrieval, and citations | 15 | 13 | Implemented | 6 synthetic policies incl. a WITHDRAWN version that is excluded from retrieval/citation; front-matter metadata (documentId, title, version, effectiveDate, owner, status); MiniLM embeddings; configurable top-K + similarity threshold; citations persisted with doc/section/chunk/score; abstention path emits `POLICY_ABSTAINED` and yields INSUFFICIENT_EVIDENCE (verified live). Deductions: no jurisdiction metadata; no per-chunk effective-date expiry logic beyond status flag; corpus is small (15-ish chunks/doc). | `knowledge/*`, `policies/`, live `POLICY_ABSTAINED` event (§6) |
| 7 | Structured output, memory, and human review | 8 | 8 | Implemented | Typed recommendation DTO with enums; schema validation before persistence (unknown values/invented IDs/unsupported citations/unsafe action claims rejected); bounded case-scoped memory (max 12, isolation tested); dispositions ACCEPT/REROUTE/ESCALATE/REQUEST_INFORMATION/CLOSE_OVERRIDE/CLOSE with mandatory ≥15-char rationale and missing-info acknowledgement; explicit Start-review step. | `RecommendationValidator.java`, `ReviewService.java`, `CaseMemoryStore.java` |
| 8 | Security, privacy, responsible AI, and auditability | 8 | 8 | Implemented | Secret-pattern scan clean (see §9); `.env` git-ignored; synthetic-only banner + fixtures; `Redactor` strips PAN/e-mail/keys from logs and audit details; injection rejected at intake and flagged when inside statements; read-only case-bound tools; 4-persona least privilege enforced (403s verified); correlation IDs; append-only hash-chained audit with DB triggers; model/mode recorded per run. | `security/InputSafety.java`, `Redactor.java`, `SecurityConfig.java`, `V1__fraud_case_schema.sql:177-178` |
| 9 | Testing, reliability, and observability | 8 | 8 | Implemented | 52 tests covering tools, boundaries, validation, parsing, RAG/no-answer, tool failure, memory isolation, both-adapter contract/parity, audit append-only, eval metrics; correlation-ID filter; tool-call latency logging; actuator health (db/modelProvider/policyStore); intake idempotency with audited replay. Deductions: no coverage tooling; no model retry policy (deliberate safe-failover instead). | `src/test/`, `observability/`, `FraudHealthIndicators.java` |
| 10 | UI, documentation, and developer experience | 4 | 4 | Implemented | 11 Thymeleaf screens incl. the guide's six, journey + lifecycle visualizations, loading/empty/error states, skip-link/ARIA labels, one-click samples; README + 3 docs now consistent with the four-persona model (post-evaluation fix). | `templates/`, `static/`, `README.md` |
| | **Total** | **100** | **95** | | | |

## 5. Framework Evaluation
### 5.1 Spring AI
`SpringAiFraudCaseAgent` builds an `OpenAiChatModel` (`SpringAiConfig`) pointed at `capstone.llm.base-url` + `api-key` from environment. Evidence tools are exposed as `@Tool`/`@ToolParam` methods on `SpringAiEvidenceTools`, delegating to the case-bound `ToolGateway`. Prompts render from `prompts/fraud-system.txt` + `fraud-user.txt`; raw model text is parsed by `RecommendationParser` and validated by `RecommendationValidator` before anything persists. Follow-up chat uses a `ChatMemoryRepository` backed by `CaseMemoryStore` (conversation ID = case ID, window = 12). Verified live: case CASE-2026-0006 (spring-ai) → UNAUTHORISED_TRANSACTION → FRAUD_INVESTIGATION, 3 real policy citations, device-timeout recorded as missing information, ~40 s wall time including a tool timeout.

### 5.2 LangChain4j
`LangChain4jFraudCaseAgent` builds two `AiServices` interfaces — `Analyzer` (stateless, with `@SystemMessage(fromResource=...)` + `@Tool` toolbox) and `FollowUp` (`@MemoryId` + `MessageWindowChatMemory` over `LangChain4jCaseChatMemoryStore`). Verified live earlier in the session: merchant-dispute case → MERCHANT_DISPUTE → DISPUTE_OPERATIONS with a real `calculateCaseSla` tool call appearing in the trace. Uses `dev.langchain4j.model.openai.OpenAiChatModel` (OpenAI-compatible gateway). One deprecated-API compile warning — cosmetic, flagged for cleanup.

### 5.3 Framework Parity
Both adapters implement `FraudCaseProcessor`, consume the same `FraudCaseContext`, share the same prompts, toolbox, parser, validator and fallback path. `AiAdapterContractTest` runs the identical contract suite against both (schema validity, invented-evidence rejection, memory isolation via the PELICAN codeword case, malformed-output rejection). Differences observed live: Spring AI ~12–40 s per run; LangChain4j similar order; LC4j made an extra `calculateCaseSla` call autonomously while Spring AI relied on the pre-executed plan — both within the 8-call bound, equivalent business outcome and citations. Token usage is not captured in audit metadata (latency is) — noted as a gap.

## 6. Acceptance Scenario Results
| Scenario | Mode | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| New device + unusual location + high amount (guide sample, TXN-2026-784102) | spring-ai | Suspicious/unauthorised w/ cited evidence | UNAUTHORISED_TRANSACTION → FRAUD_INVESTIGATION, HIGH, POL-CNP-017 3.1/3.2/3.3 cited, auth outcome flagged missing | **Pass** | CASE-2026-0006 recommendation + audit |
| Same scenario, deterministic baseline | rules | Same classification family | UNAUTHORISED_TRANSACTION → FRAUD_INVESTIGATION, conf 0.77 | **Pass** | CASE-2026-0005; eval T01 |
| Evidence service unavailable (TXN-MISSING-0001) | rules | INSUFFICIENT_EVIDENCE or more-info request | INSUFFICIENT_EVIDENCE → MANUAL_REVIEW, missing info lists transaction lookup failure | **Pass** | CASE-2026-0007 |
| Tool timeout mid-run (TXN-2026-950007) | spring-ai | Degrade gracefully, keep gap visible | TIMEOUT traced (TR-139), EV-134 `available=false`, gap in missing info, case still READY_FOR_REVIEW | **Pass** | CASE-2026-0006 tool-traces |
| Question absent from policy corpus (loyalty points, TXN-2026-301990/WALLET) | rules | Explicit no-information response | Retrieval `POLICY_ABSTAINED` (0/4 above 0.72), 0 citations, INSUFFICIENT_EVIDENCE → MANUAL_REVIEW | **Pass** | CASE-2026-0010 audit |
| Policy question with active source (follow-up on 0005) | rules | Grounded answer w/ title, section, version | Answer lists POL-CNP-017 v3.2 §3.1–3.4 with chunk IDs + scores; only case-scoped refs | **Pass** | Follow-up response, refs cnp-031…034 |
| Retired policy contains matching text | rules/tests | Retired doc excluded | `POL-CNP-017-v3.1-withdrawn.md` parsed as WITHDRAWN, never indexed/citable | **Pass** | `PolicyDocumentParser.java:61`, `PolicyRetrievalTest`, policy library UI shows WITHDRAWN |
| Model returns malformed output | contract tests | Validated retry or safe failure | `RecommendationValidator` rejects → `MODEL_OUTPUT_REJECTED` audited → rules fallback produces valid rec | **Pass** | `AiAdapterContractTest`, `InvestigationOrchestrator.java:239-264` |
| Two separate case IDs — memory isolation | contract tests | No cross-case recall | Codeword PELICAN stored in case A not recalled in case B | **Pass** | `AiAdapterContractTest` (both adapters) |
| Prompt injection in statement (TXN-2026-118204) | rules + spring-ai | Treated as data; constraints retained | Injection ignored; security flag recorded; UNAUTHORISED_TRANSACTION; no refund possible | **Pass** | CASE-2026-0009 (rules); prior live spring-ai run |
| Same case submitted twice | API | Idempotent / duplicate protection | First POST → 201 CASE-2026-0013; identical re-POST → **200, same caseId** + `INTAKE_REPLAYED` audit event (post-evaluation fix, G-1 resolved) | **Pass (fixed)** | Live API calls; `FraudCaseService`, V2 migration |
| Genuine/low-risk signals | rules (eval) | Genuine or low-risk | GENUINE_TRANSACTION → MANUAL_REVIEW (route still requires human) | **Pass** | `EvaluationSuiteTest` row "Genuine activity" |
| Mandatory human approval | all | No auto-action | Every recommendation `humanReviewRequired=true`; dispositions require rationale; ROUTED is advisory only | **Pass** | `human-review-required: true`, `ReviewService` |
| ≥5 transactions in 10 min (velocity, TXN-2026-784102) | rules | Velocity indicator recorded | `getTransactionVelocity` returns 6/10-min burst → HIGH-severity VELOCITY evidence, cited in anomalies | **Pass (added)** | `EvidenceTools.getTransactionVelocity`, eval T01, `velocityBurstIsRecordedAsHighSeverityEvidence` |
| Confirmed sanctions/PEP match (TXN-2026-777301) | rules | Mandatory escalation recommendation, no auto-action | `runComplianceScreening` → CONFIRMED_MATCH on SANCTIONS_SYNTHETIC (0.94) → R-10 overrides route to AML_INVESTIGATION, risk HIGH, cites POL-ESC-011 §2.3; human still decides | **Pass (added)** | Eval row "T12 screening match", `t12_confirmedScreeningMatchRecommendsAmlInvestigationWithoutActing` |

## 7. Strengths
1. **Safety architecture is real, not cosmetic.** Recommendations are validated before persistence (invented evidence IDs, unsupported citations, unsafe action claims all rejected), tools are read-only and case-bound, every adverse action is impossible by construction (`allow-financial-actions: false` enforced by `SafetyConfigurationGuard`), and a failed model call or bad output degrades to deterministic rules with the fallback audited — verified live and in contract tests.
2. **Honest RAG with verifiable abstention.** Withdrawn policy is structurally excluded, citations carry document/section/chunk/score, and a below-threshold retrieval produces a persisted `POLICY_ABSTAINED` event and INSUFFICIENT_EVIDENCE — demonstrated live (CASE-2026-0010), not just claimed.
3. **Genuine dual-framework parity behind one contract.** Both Spring AI and LangChain4j run the same prompts, toolbox, DTO and validator; the same contract tests (including case-memory isolation) execute against scripted models for both; live end-to-end runs verified for each.

## 8. Gaps and Risks
| Priority | Gap or Risk | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|
| High | **G-1: ~~No idempotency on case intake~~ — RESOLVED post-evaluation.** `dedupe_key` (SHA-256 of maskedCustomerRef\|transactionRef\|issueType) with unique index; identical intake replays to the existing case (HTTP 200) with an `INTAKE_REPLAYED` audit event; concurrent race → 409. Verified live (CASE-2026-0013) and in `identicalIntakeReplaysToExistingCaseInsteadOfDuplicating` test. | — | `V2__fraud_case_dedupe_key.sql`, `FraudCaseService.java` | Done |
| Medium | **G-2: ~~Missing rubric tools/signals~~ — RESOLVED post-evaluation.** Added `getTransactionVelocity` (burst indicator at 5+/10 min) and `runComplianceScreening` (synthetic sanctions/PEP) as case-bound read-only tools on the allow-list and both adapter toolboxes; `Route.AML_INVESTIGATION` added, reached via rule R-10 on CONFIRMED_MATCH (POL-ESC-011 §2.3); potential matches surface as missing adjudication. Verified: eval T12, 2 new scenario tests, fixture `TXN-2026-777301`. | — | `ToolNames.java`, `EvidenceTools.java`, `RulesFraudCaseAgent.java` R-10/R-11, `Route.java` | Done |
| Medium | **G-3: ~~Documentation drift~~ — RESOLVED post-evaluation.** README persona table, UI walkthrough, curl examples, API table (incl. `reviews/start` + idempotent intake), `application-example.yml` and `.env.example` all updated to `agent`/`investigator`/`reviewer`/`auditor` and `FRAUD_AGENT/INVESTIGATOR/REVIEWER/AUDITOR_PASSWORD`. | — | `README.md`, `application-example.yml`, `.env.example` | Done |
| Medium | **G-4: No token-usage or model-version capture in audit metadata.** Latency and mode are stored; tokens and resolved model ID are not. | Rubric asks model/framework/version in audit; cost analysis impossible | `RecommendationEntity`, run records | Persist `ChatResponse` usage + resolved model string on the run/recommendation |
| Low | **G-5: No coverage tooling or retry semantics.** JaCoCo absent; model failures fail over to rules with no retry/backoff. | Coverage % unverifiable; transient gateway blips lose AI output | `pom.xml` | Add JaCoCo; optional single bounded retry before fallback (document why failover is preferred) |
| Low | **G-6: ~~Deprecated LangChain4j API usage~~ — RESOLVED post-evaluation** (`maxToolCallingRoundTrips`). | — | `LangChain4jFraudCaseAgent.java` | Done |
| Low | **G-7: Model-reported confidence is inflated vs the rules engine** (0.92 vs 0.77 on identical facts). | Over-confident UI signal for reviewers | Live runs; `docs/EVALUATION_AND_SELF_REVIEW.md` | Calibrate or display model confidence alongside rule-derived confidence |

## 9. Security and Responsible AI Findings
| Severity | Finding | File or Component | Remediation |
|---|---|---|---|
| Info | Secret-pattern scan across `src/`, docs, configs found **no committed credentials**; only hit is a synthetic `sk-…` string used as redactor test input | `AuditAndToolContractTest.java:102` | None — intentional test data |
| Info | API key lives in `.env` (git-ignored, loaded via `spring.config.import`); `application.yml`/`application-example.yml` use `${FRAUD_LLM_API_KEY}` placeholders; `CapstoneProperties.Llm.toString()` masks the key | `application.yml:73`, `.gitignore` | None |
| Info | Persona passwords are env-overridable with `-demo` defaults, clearly labelled synthetic; no real PII anywhere (fixtures and policies tagged `SYNTHETIC_TRAINING`) | `application.yml:78-83`, `SyntheticFixtureStore` | Optional: document rotation guidance for non-lab use |
| Low | ~~Stale docs list old password variable names~~ — resolved with G-3 | `application-example.yml`, `.env.example` | Done |
| Info | Audit trail stores hashes/summaries, not raw statements or rationales; UI escapes output; strict CSP, no inline JS; CSRF on UI, HTTP Basic on `/api/**`; H2 console disabled | `AuditWriter`, `SecurityConfig.java` | None |

## 10. Recommended Improvements
### Critical
- None blocking.

### High
1. ~~Add intake idempotency~~ — **done** (`dedupe_key` unique index + audited replay; see G-1).

### Medium
2. ~~Sync README/example configs to the four-persona model~~ — **done** (G-3).
3. ~~Add `getTransactionVelocity` tool and a synthetic screening fixture; add `AML_INVESTIGATION` route~~ — **done** (G-2).
4. Persist token usage and resolved model ID on `InvestigationRun`/`Recommendation`. (G-4)

### Low
5. Add JaCoCo to `pom.xml`; document the deliberate no-retry failover policy (or add one bounded retry). (G-5)
6. ~~Migrate off the deprecated LangChain4j builder API~~ — **done** (G-6).
7. Surface rule-derived confidence next to model-reported confidence in the UI. (G-7)

## 11. Definition-of-Done Checklist
- [x] Fraud case intake works with synthetic data (validated, masked; 4 personas)
- [x] Deterministic tools provide traceable evidence (7 tools, traces, provenance, timeouts)
- [x] Structured recommendations are validated (before persistence; failures fall back to rules)
- [x] Policy answers are grounded and cited (doc/section/chunk/score; abstention path verified)
- [x] Human review is mandatory (start-review + rationale + acknowledgement enforced)
- [x] Conversation/case memory is isolated (case-keyed stores; contract-tested both adapters)
- [x] Secrets are not stored in the repository (scan clean; `.env` ignored)
- [x] Error and no-information states are handled safely (TIMEOUT/NOT_FOUND/abstention verified)
- [x] Automated tests pass (49/49)
- [x] README enables another participant to run the project

## 12. Final Verdict
**Demo Ready.** The capstone satisfies its brief end-to-end and, more importantly, proves the safety properties it claims: every check in the definition-of-done list is backed by a passing test or a verified live run rather than documentation. The dual-framework design behind a single validated contract, the enforced persona separation, the hash-chained append-only audit, and the honest abstention/failure behavior (all reproduced during this evaluation) put it clearly above demo-bar. Since the initial pass, the High finding (intake idempotency), the deprecated LangChain4j API, the persona doc drift, and the missing velocity/screening tools with the AML escalation route were all remediated and re-verified (52/52 tests). The remaining open items — token/model audit metadata, coverage tooling, and confidence calibration — are real but non-blocking refinements; none undermines the core requirement that the system recommend, never act, and always leave the decision to a human reviewer.
