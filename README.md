# Intelligent Payment Fraud Investigation & Dispute Resolution Agent

An explainable agentic assistant that collects fraud evidence, retrieves applicable policy, recommends a case route
and **keeps the final disposition with a human reviewer**. It uses synthetic training data only.

> **Safety boundary:** the application may recommend a route, priority or evidence request. It never blocks a card,
> issues a refund, moves funds, files a report or contacts a customer. `humanReviewRequired` is always `true`, and the
> app refuses to start if configuration tries to change that.

## Technology

| Area | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.5.16, Maven |
| AI adapters | **Spring AI 1.1.8** (primary, default) and **LangChain4j 1.22.0** (comparison extension), both behind `FraudCaseProcessor` |
| Model | Anthropic Claude via an OpenAI-compatible gateway (`global.anthropic.claude-haiku-4-5-20251001-v1:0` by default; Sonnet 4.6 / Opus 4.5 selectable) |
| RAG | In-process all-MiniLM-L6-v2 embeddings (ONNX, offline) + in-memory vector store, Top-K 4, threshold 0.72 |
| Persistence | H2 (file DB in `./data`), Flyway, JPA; append-only audit enforced by DB trigger + hash chain |
| UI | Thymeleaf, Spring Security (form login + CSRF), strict CSP, no inline scripts |
| Fallback | Deterministic `RulesFraudCaseAgent` (rule IDs R-01…R-09), with no model dependency |

## Quick start

Prerequisites: JDK 21 and Maven 3.8+.

```bash
cp .env.example .env          # then set FRAUD_LLM_API_KEY (the .env file is git-ignored)
mvn clean package             # builds and runs all tests (no live model calls in tests)
java -jar target/fraud-case-agent-1.0.0.jar          # http://localhost:8080
PORT=8090 java -jar target/fraud-case-agent-1.0.0.jar  # if 8080 is taken
```

Development: `mvn spring-boot:run`. Tests only: `mvn test`. The evaluation report is written to `target/evaluation-report.md`.

### Demo users (synthetic)

| User | Password (default) | Persona (capstone guide) |
|---|---|---|
| `agent` | `agent-demo` | Service agent — guided intake only (masked, validated) |
| `investigator` | `investigator-demo` | Fraud investigator — intake + tool-driven enrichment + follow-ups |
| `reviewer` | `reviewer-demo` | Dispute reviewer — start review, dispositions with rationale, follow-ups, audit |
| `auditor` | `auditor-demo` | Team lead / auditor — read-only everything, global audit trail, health details |

Override them with `FRAUD_AGENT_PASSWORD`, `FRAUD_INVESTIGATOR_PASSWORD`, `FRAUD_REVIEWER_PASSWORD` and `FRAUD_AUDITOR_PASSWORD`.

## Configuration and mode selection

All settings live under `capstone.*` (see `application-example.yml`). The main environment variables are:

| Variable | Purpose | Default |
|---|---|---|
| `FRAUD_PROCESSOR_MODE` | `rules` \| `spring-ai` \| `langchain4j` | `spring-ai` |
| `FRAUD_LLM_BASE_URL` | gateway host (no `/v1`) | `https://llmgw-wp.tekstac.com` |
| `FRAUD_LLM_API_KEY` | gateway key (keep it in `.env`) | empty, so AI is unavailable and rules are used |
| `FRAUD_LLM_MODEL` | model ID | Claude Haiku 4.5 |

To use **Claude Sonnet**, set `FRAUD_LLM_MODEL=global.anthropic.claude-sonnet-4-6`.

Each investigation can also override the mode (UI dropdown or `{"mode":"rules"}` in the API body). If the selected
AI processor is unavailable, throws, or returns output that fails validation, the orchestrator records an audit event
and **falls back to rules mode**. Unexpected errors leave the case `INCOMPLETE` for manual handling.

## Using the UI

1. Sign in as `investigator`, open **New case**, then click **Load sample: unrecognised CNP purchase**, tick the synthetic confirmation and click **Create & start investigation**. (Sign in as `agent` to demo the service-agent hand-off path instead.)
2. **Investigation & tools** shows the bounded plan, the tool trace (status, duration, redacted result, trace ID, and who invoked each tool: APPLICATION or MODEL) and case-scoped follow-up questions.
3. **Evidence** keeps customer claims (USER_INTAKE), tool-observed evidence (TOOL_RESULT), security flags and generated summaries separate, each with its provenance.
4. **Policy citations** shows the query, Top-K, threshold, scores, chunk IDs, passages, the abstention state, and withdrawn or superseded versions.
5. **Recommendation** shows classification, risk, route, confidence, linked evidence, citations, missing information, contradictions, rules and input facts.
6. **Human review** — sign in as `reviewer`, click **Start review** (moves the case to UNDER_REVIEW), then submit a disposition with a mandatory rationale and checklist. **Audit** shows the hash-chained event log (also visible to `auditor`).

Other samples cover goods not received, a duplicate charge, contradictory signals, a device tool timeout and prompt injection.

## REST API (HTTP Basic)

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/cases` | Create and validate a case — idempotent: an identical customer+transaction+issue replays to the existing case (HTTP 200, audited as `INTAKE_REPLAYED`) |
| GET | `/api/cases`, `/api/cases/{id}` | List cases / get a case summary and state |
| POST | `/api/cases/{id}/investigations` | Start or restart analysis (`{"mode":"spring-ai"}` optional) |
| GET | `/api/cases/{id}/evidence` | Evidence and provenance |
| GET | `/api/cases/{id}/tool-traces` | Redacted tool trace |
| GET | `/api/cases/{id}/recommendation` | Latest validated recommendation |
| GET | `/api/cases/{id}/citations` | Retrieved policy passages and citation metadata |
| POST/GET | `/api/cases/{id}/messages` | Case-scoped follow-up / conversation |
| POST | `/api/cases/{id}/reviews/start` | Reviewer takes ownership (READY_FOR_REVIEW → UNDER_REVIEW) |
| POST/GET | `/api/cases/{id}/reviews` | Record / list reviewer dispositions |
| GET | `/api/cases/{id}/audit` | Audit events for the case |
| GET | `/api/policies` | Policy library and availability |
| GET | `/actuator/health` | DB, model-provider config and policy store (details require reviewer/auditor) |

```bash
curl -u agent:agent-demo -H 'Content-Type: application/json' -d @- localhost:8080/api/cases <<'EOF'
{"maskedCustomerRef":"CUST-••••-4821","transactionRef":"TXN-2026-784102","amount":48750.00,"currency":"INR",
 "occurredAt":"2026-11-06T08:42:00+05:30","status":"POSTED","channel":"CARD_NOT_PRESENT",
 "maskedMerchant":"NORTHSTAR DIGITAL ••••73","issueType":"MERCHANT_NOT_RECOGNISED",
 "customerStatement":"I do not recognise this purchase and still have my card.",
 "syntheticDataConfirmed":true,"reviewRequired":true}
EOF
curl -u investigator:investigator-demo -H 'Content-Type: application/json' -d '{"mode":"spring-ai"}' localhost:8080/api/cases/CASE-2026-0001/investigations
curl -u reviewer:reviewer-demo localhost:8080/api/cases/CASE-2026-0001/recommendation
curl -u reviewer:reviewer-demo -X POST localhost:8080/api/cases/CASE-2026-0001/reviews/start
curl -u reviewer:reviewer-demo -H 'Content-Type: application/json' \
  -d '{"disposition":"ACCEPT_RECOMMENDATION","reason":"Independent anomalies confirmed; fraud queue.","missingInfoAcknowledged":true}' \
  localhost:8080/api/cases/CASE-2026-0001/reviews
```

## Synthetic case set (fixtures/synthetic-evidence.json)

| Transaction | Scenario | Expected |
|---|---|---|
| TXN-2026-784102 | Guide sample: unrecognised CNP, new device, location anomaly, auth unavailable, velocity burst | UNAUTHORISED → FRAUD_INVESTIGATION |
| TXN-2026-510233 | Recognised merchant, goods not received | MERCHANT_DISPUTE → DISPUTE_OPERATIONS |
| TXN-2026-620871 | Duplicate fingerprint | DUPLICATE_PROCESSING → PAYMENT_OPERATIONS |
| TXN-MISSING-0001 | Transaction not found | INSUFFICIENT_EVIDENCE → MANUAL_REVIEW |
| TXN-2026-301990 | Loyalty-points question (no policy) | abstain → MANUAL_REVIEW |
| TXN-2026-730455 | Trusted device vs. impossible location | contradiction → MANUAL_REVIEW |
| TXN-2026-840119 | Trusted device, consistent location, auth success | GENUINE → MANUAL_REVIEW |
| TXN-2026-950007 | Device tool timeout + blocklisted merchant | TIMEOUT recorded; FRAUD_INVESTIGATION |
| TXN-2026-118204 | Prompt injection in the statement | flagged and ignored; FRAUD_INVESTIGATION |
| TXN-2026-777301 | Confirmed synthetic sanctions screening match | R-10 → AML_INVESTIGATION (recommendation only) |

Any other reference gets deterministic evidence derived from `SHA-256(seed | reference)`.

## Documentation

- `docs/ARCHITECTURE.md`: layers, components, data flow, mode selection, framework comparison
- `docs/PROMPTS.md`: prompt contract, variables and prompt-change log
- `docs/EVALUATION_AND_SELF_REVIEW.md`: test map T01–T12, evaluation results, limitations, residual risks, self-review answers
- `AGENTS.md`: rules for contributors and coding agents
