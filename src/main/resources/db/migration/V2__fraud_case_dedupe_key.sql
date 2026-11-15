-- Idempotent intake (G-1): dedupe_key = SHA-256(maskedCustomerRef|transactionRef|issueType) computed at intake.
-- Unique index turns a concurrent duplicate insert into a constraint violation (mapped to HTTP 409);
-- a sequential duplicate is replayed to the existing case by FraudCaseService. Pre-existing rows keep NULL
-- and simply do not participate in deduplication.
ALTER TABLE fraud_case ADD COLUMN dedupe_key VARCHAR(64);
CREATE UNIQUE INDEX uq_fraud_case_dedupe_key ON fraud_case (dedupe_key);
