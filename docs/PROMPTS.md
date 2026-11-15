# Prompt contract

Resources (shared by both AI adapters through `PromptLibrary`):

| File | Role |
|---|---|
| `prompts/fraud-system.txt` | Role (triage assistant, not decision maker), grounding, untrusted-data tags, tool limits, prohibited actions, abstention, route mapping, human review, concise rationale, JSON-only output |
| `prompts/fraud-user.txt` | Template with `{{investigationGoal}}`, `{{caseSummary}}`, `{{plan}}`, `{{toolEvidence}}`, `{{policyContext}}`, `{{topK}}`, `{{threshold}}`, `{{retrievalStatus}}`, `{{securityNotes}}`, `{{outputSchema}}` |
| `prompts/output-schema.json` | JSON Schema for `FraudCaseRecommendation` |
| `prompts/follow-up-system.txt` | Case-scoped follow-up rules + `{{caseBrief}}` |

| Variable | Origin |
|---|---|
| caseSummary | Validated intake (`CaseFacts`); the statement is wrapped in `<customer_statement>` and HTML-escaped |
| investigationGoal | `InvestigationPlanner` (application constant per issue type) |
| toolEvidence | Evidence items with IDs, severity, provenance and source trace |
| policyContext | Retrieved passages wrapped in `<policy_passage CITABLE|NOT_CITABLE_BELOW_THRESHOLD ...>` with exact metadata |
| conversationContext | Case memory (follow-ups only) |
| guardrails | System prompt + `RecommendationValidator` |

Model output is always parsed by `RecommendationParser` and validated by `RecommendationValidator`. The prompt is
guidance; the validator is the control.

## Prompt-change log

| Version | Date | Change | Reason |
|---|---|---|---|
| 1.0 | 2026-11-15 | Initial system/user templates, schema, follow-up prompt | Capstone contract |
| 1.1 | 2026-11-15 | Added CITABLE / NOT_CITABLE_BELOW_THRESHOLD markers and an explicit "no passage met threshold → MANUAL_REVIEW" line | Prevent citing below-threshold passages (T05) |
| 1.2 | 2026-11-15 | Added untrusted `<customer_statement>` / `<policy_passage>` tags, plus HTML-escaping and a SECURITY NOTES section | Prompt-injection handling (T11) |
| 1.3 | 2026-11-15 | "Call calculateCaseSla once for the proposed route; call other tools only to fill a named gap" | Bounded tool use; reduce unnecessary calls |
