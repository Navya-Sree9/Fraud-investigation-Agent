---
documentId: POL-ESC-011
title: Fraud Escalation and Case Priority Standard
version: 1.0
effectiveDate: 2026-09-01
owner: Payment Fraud Operations
classification: SYNTHETIC_TRAINING
status: ACTIVE
chunkPrefix: esc
---
## 2.1 Priority and SLA targets
Training SLA targets by risk level: critical risk is priority P1 with a four hour target, high risk is P2 with a twenty-four hour target, medium risk is P3 with a seventy-two hour target and low risk is P4 with a five day target. Payment Operations reconciliation adds one day. Targets are measured from the time the case was reported.

## 2.2 Escalation to fraud investigation
A reviewer may escalate a case to the Fraud Investigation queue at elevated priority when high-severity signals such as a blocklisted merchant, a failed authentication challenge, implausible travel between locations or a new untrusted device are observed. Escalation is a routing decision recorded by a human; it does not block cards or move funds.

## 2.3 Compliance screening and AML escalation
When the synthetic compliance-screening tool returns a confirmed sanctions or PEP list match for a case party, the case must be recommended to the AML_INVESTIGATION queue at high priority or above. A potential match is recorded as missing adjudication and must not itself change the route. AML escalation is a routing recommendation only: the system never files a regulatory report, freezes funds or contacts authorities automatically.
