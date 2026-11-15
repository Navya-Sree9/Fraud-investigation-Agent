# Evaluation, limitations and self-review

## Automated tests (48, `mvn test`, no live services)

| ID | Scenario | Test |
|---|---|---|
| T01 | Merchant denied, new device, location anomaly, policy match | `FraudScenarioIntegrationTest.t01_*`, `AiAdapterContractTest.everyProcessor*` |
| T02 | Recognised merchant, fulfilment | `t02_*` |
| T03 | Duplicate fingerprint | `t03_*` |
| T04 | Missing transaction / statement | `t04_*` (both integration and API validation) |
| T05 | No policy above threshold | `t05_*`, `PolicyRetrievalTest.unrelatedQueryAbstains` |
| T06 | Contradictory device/location | `t06_*` |
| T07 | Model endpoint unavailable | `AiAdapterContractTest.t07_*` |
| T08 | Tool timeout / not found | `t08_*`, `t04_*` |
| T09 | Malformed / unknown enum | `RecommendationContractTest.t09_*`, `AiAdapterContractTest.t09_*`, `inventedEvidenceIdIsRejected` |
| T10 | Case A memory queried from Case B | `AiAdapterContractTest.t10_*` (Spring AI, LangChain4j, rules) |
| T11 | Prompt injection in statement | `t11_*` (+ live Claude check below) |
| T12 | Override without reason | `CaseApiSecurityTest.t12_*`, `uiFormPostsRequireCsrfToken` |

Other layers covered: validator/parser units, the state machine, tool determinism, scope and budget, redaction,
the append-only audit trigger and hash chain, version filtering (withdrawn, superseded, not yet effective), metadata
validation, paraphrase retrieval, RBAC, CSRF, unknown-field rejection, and rendering of all six screens.

## Labelled evaluation (rules mode), from `target/evaluation-report.md`

| Measure | Result |
|---|---|
| Schema-valid | 10/10 |
| Classification / route accuracy | 10/10 / 10/10 |
| Citation correctness | 6/6 |
| Abstention | 1/1 |
| Human-review flag | 10/10 |
| Distinct tools | 9 |
| Latency median / p95 | ~155 ms / ~557 ms |

## Live checks with Claude (Haiku 4.5 via gateway, 2026-11-15)

| Case | Mode | Result |
|---|---|---|
| Guide sample | spring-ai | UNAUTHORISED_TRANSACTION → FRAUD_INVESTIGATION, 0.92, cites cnp-032/033/031, auth outcome listed as missing, ~12 s |
| Goods not received | langchain4j | MERCHANT_DISPUTE → DISPUTE_OPERATIONS, cites mdr-021/022, model called `calculateCaseSla` (traced as MODEL) |
| Prompt injection | spring-ai | Instructions ignored; FRAUD_INVESTIGATION; SECURITY_FLAG evidence recorded |
| Follow-up | spring-ai | Grounded answer citing [EV-104] [EV-102] [cnp-033]; all references verified |
| Loyalty question | rules | Abstained, MANUAL_REVIEW, no procedure asserted |

Model confidence is self-reported and was higher than the rules engine's calibrated values (0.92 vs 0.77). Reviewers should treat it as advisory.

## Limitations and residual risks

- Embeddings come from a small general-purpose model (MiniLM). The 0.72 threshold was calibrated on this policy pack only, and paraphrases far from policy wording may abstain.
- The vector store is in memory and policies are re-embedded at startup (15 chunks, under 1 s).
- Model output can include free-text items (e.g. missing-information phrasing or rule-like IDs such as `POL-CNP-017:3.2` in `ruleIds`). These are validated for safety, not for semantics.
- Prompt-injection detection is pattern-based (flagging only). The real protection is structural: case text is passed only as data, tools are read-only and case-bound, output is validated, and a human reviews every case.
- H2 file DB and in-memory demo users are for the lab only. A sandbox would need a managed DB, SSO/IAM, per-case authorisation, secrets management, key rotation, retention policies and model risk review.
- The gateway key was supplied for this lab; rotate it after the course.

## Self-review answers

1. **Agentic, not just generative:** the app plans a bounded set of tools, acts through them, observes structured results and retrieves policy. The model can call further case-bound tools before producing a typed, validated recommendation. State changes stay in application code.
2. **Fact sources:** intake facts are USER_INTAKE (evidence provenance), tools are TOOL_RESULT with trace IDs, policy is POLICY_RETRIEVAL with chunk metadata, reviewer entries are REVIEWER_ENTRY in `review_disposition`, and the model text is labelled MODEL_SUMMARY.
3. **Failures:** a tool failure produces structured unavailable evidence plus a missing-information entry. A model failure or invalid output falls back to rules and is audited. A DB failure surfaces a safe error. An empty or unavailable vector store makes retrieval report UNAVAILABLE, which forces abstention and MANUAL_REVIEW. Health endpoints show each of these states.
4. **Case isolation:** memory is keyed by case ID in DB and adapters, prompt briefs are built per case, toolbox arguments are checked against the case, and the T10 tests run for all three processors.
5. **Absent, stale or contradictory policy:** abstention means no citations, MANUAL_REVIEW and a validator-enforced ban on procedural claims. Stale (withdrawn/superseded) versions are never embedded. Contradictory signals trigger R-06, reduced confidence and MANUAL_REVIEW.
6. **Recommendation, not decision:** no tool can take action, `humanReviewRequired` is enforced by the validator and the startup guard, and only a REVIEWER disposition changes the route.
7. **Traceability:** UI → `CaseWebController` → `InvestigationOrchestrator` → `ToolGateway`/`PolicyRetriever` → `FraudCaseProcessor` → `RecommendationValidator` → repositories + `AuditWriter`, all under one correlation ID.
8. **Both frameworks:** domain, prompts, toolbox, parser, validator, memory store and tests are identical. Only the wiring differs (see ARCHITECTURE.md).
9. **Before a bank sandbox:** real IAM/SSO and per-case ABAC, a managed DB with WORM audit storage, a vetted embedding model and vector DB, red-team testing, model risk governance, DLP on prompts, and monitoring/alerting on invalid-output and fallback rates.
