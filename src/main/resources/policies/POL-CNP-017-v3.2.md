---
documentId: POL-CNP-017
title: Card-Not-Present Dispute Handling Procedure
version: 3.2
effectiveDate: 2026-10-01
owner: Payment Fraud Operations
classification: SYNTHETIC_TRAINING
status: ACTIVE
chunkPrefix: cnp
---
## 3.1 Evidence collection for card-not-present disputes
For any card-not-present (online, e-commerce) transaction the customer disputes, the analyst must collect the transaction record including the authentication outcome, device signals for the customer, a comparison of the transaction location against recent trusted locations, the merchant risk profile and the customer's prior dispute history before a route is recommended. Customer statements are recorded as claims and must not be treated as verified evidence.

## 3.2 Initial fraud routing
When a customer denies recognising the merchant of a card-not-present transaction and two or more independently sourced high-severity anomalies are present, such as a device not previously observed for the customer together with a location inconsistent with recent trusted activity, the case should be recommended for the Fraud Investigation queue at high priority. Possession of the physical card by the customer does not rule out unauthorised card-not-present use. The recommendation is advisory; a human reviewer confirms the route.

## 3.3 Missing authentication outcome
If the authentication challenge outcome (for example 3-D Secure result) is unavailable for an online card-not-present transaction, the analyst must record it as missing information and must not assume that authentication succeeded or failed. The case may still be routed for fraud review when other independent anomalies are present, but the gap must remain visible to the reviewer.

## 3.4 Single anomaly or contradictory signals
When only one high-severity anomaly is present, or device and location signals contradict each other (for example a trusted, long-standing device used from an implausible distant location), the case should not be classified as unauthorised on that basis alone. Record the contradiction, reduce confidence and refer the case for manual review so an investigator can reconcile the signals.

## 4.1 Prohibited automated actions
The investigation assistant must not block or reissue a card, issue a refund or provisional credit, move funds, file a regulatory report or contact the customer. These actions are performed only by authorised staff after a recorded human disposition.
