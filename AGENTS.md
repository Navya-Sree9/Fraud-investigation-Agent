# AGENTS.md

## Technology
- Java 21, Spring Boot 3.5.16, Maven. Versions are pinned in `pom.xml` (Spring AI 1.1.8, LangChain4j 1.22.0, MiniLM embeddings 1.22.0-beta32).
- Package layout: `api`, `application`, `domain`, `agent/common`, `agent/rules`, `agent/springai`, `agent/langchain4j`, `tools`, `knowledge`, `persistence`, `security`, `observability`, `web`.
- AI framework types may appear **only** in `agent/springai`, `agent/langchain4j` and `knowledge/MiniLmEmbeddingProvider`.

## Verification (run before finishing any change)
- `mvn clean package` builds and runs all tests (about 2 minutes). All tests must pass.
- Tests never call a live model: `application-test.yml` forces `capstone.llm.api-key` to blank, and AI adapters are tested with `ScriptedModels`.
- Manual smoke test: `PORT=8090 java -jar target/fraud-case-agent-1.0.0.jar`, then `curl localhost:8090/actuator/health`.
- Port 8080 may already be used by a system Tomcat on the dev machine; use `PORT=8090`.

## Quality rules
- Every processor returns `FraudCaseRecommendation`, and the orchestrator validates it with `RecommendationValidator` before persisting. Never persist or render unvalidated output.
- State transitions go through `CaseStateService` only; never from model output.
- Tools are read-only, allow-listed (`ToolNames`), executed via `ToolGateway` (timeout, trace, redaction) and bound to the case (`BoundCaseToolbox`).
- Fixtures must stay deterministic (curated JSON + stable hash). Do not use `Random` or wall-clock values in tool outputs.
- Schema changes: this is a training project with a single `V1` migration; if a persistent `./data` DB exists, add `V2__...` instead of editing `V1`.
- Keep the Spring AI and LangChain4j adapters at parity: same prompts (`PromptLibrary`), parser, toolbox and validator.

## Security rules
- Never commit secrets. The gateway key belongs in the git-ignored `.env` (`FRAUD_LLM_API_KEY`) or the environment.
- No real customer data: masked references only (`CUST-••••-1234`), and intake rejects 12–19 digit sequences, markup and unknown JSON fields.
- Never add tools or code paths that block cards, refund, move funds, file reports or contact customers. `humanReviewRequired` stays `true`.
- Do not log prompts, responses or statements; log IDs, statuses, durations and hashes. Use `Redactor` for stored summaries.
- Audit events are append-only (DB trigger + hash chain). Store payload hashes, never payloads.
