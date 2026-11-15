CREATE SEQUENCE case_seq START WITH 1;
CREATE SEQUENCE evidence_seq START WITH 101;
CREATE SEQUENCE trace_seq START WITH 101;
CREATE SEQUENCE recommendation_seq START WITH 1;
CREATE SEQUENCE review_seq START WITH 1;
CREATE SEQUENCE run_seq START WITH 1;
CREATE SEQUENCE audit_seq START WITH 1;

CREATE TABLE fraud_case (
    case_id                  VARCHAR(32)  PRIMARY KEY,
    masked_customer_ref      VARCHAR(40)  NOT NULL,
    home_region              VARCHAR(40)  NOT NULL,
    status                   VARCHAR(24)  NOT NULL,
    intake_channel           VARCHAR(24)  NOT NULL,
    reported_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by               VARCHAR(64)  NOT NULL,
    synthetic_data_confirmed BOOLEAN      NOT NULL,
    review_required          BOOLEAN      NOT NULL,
    current_run              INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE transaction_claim (
    claim_id             VARCHAR(40)   PRIMARY KEY,
    case_id              VARCHAR(32)   NOT NULL UNIQUE REFERENCES fraud_case(case_id),
    transaction_ref      VARCHAR(40)   NOT NULL,
    amount               DECIMAL(15,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    occurred_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_status   VARCHAR(16)   NOT NULL,
    payment_channel      VARCHAR(24)   NOT NULL,
    masked_merchant      VARCHAR(80)   NOT NULL,
    issue_type           VARCHAR(32)   NOT NULL,
    customer_statement   VARCHAR(2000) NOT NULL,
    original_statement   VARCHAR(2000) NOT NULL,
    card_possession      VARCHAR(16)   NOT NULL,
    merchant_recognised  BOOLEAN       NOT NULL
);

CREATE TABLE investigation_run (
    run_id           VARCHAR(32)  PRIMARY KEY,
    case_id          VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    run_number       INT          NOT NULL,
    correlation_id   VARCHAR(64)  NOT NULL,
    requested_mode   VARCHAR(16)  NOT NULL,
    effective_mode   VARCHAR(64),
    status           VARCHAR(16)  NOT NULL,
    investigation_goal VARCHAR(400),
    plan_json        CLOB,
    policy_query     VARCHAR(1200),
    retrieval_status VARCHAR(16),
    top_k            INT,
    threshold        DOUBLE PRECISION,
    fallback_reason  VARCHAR(400),
    started_by       VARCHAR(64)  NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at     TIMESTAMP WITH TIME ZONE,
    model_latency_ms BIGINT,
    UNIQUE (case_id, run_number)
);

CREATE TABLE evidence_item (
    evidence_id   VARCHAR(32)   PRIMARY KEY,
    case_id       VARCHAR(32)   NOT NULL REFERENCES fraud_case(case_id),
    run_number    INT           NOT NULL,
    type          VARCHAR(24)   NOT NULL,
    severity      VARCHAR(8)    NOT NULL,
    value_summary VARCHAR(600)  NOT NULL,
    provenance    VARCHAR(24)   NOT NULL,
    source_ref    VARCHAR(64)   NOT NULL,
    available     BOOLEAN       NOT NULL,
    detail_json   CLOB,
    observed_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_evidence_case ON evidence_item(case_id, run_number);

CREATE TABLE tool_trace (
    trace_id          VARCHAR(32)  PRIMARY KEY,
    case_id           VARCHAR(32)  NOT NULL REFERENCES fraud_case(case_id),
    run_number        INT          NOT NULL,
    correlation_id    VARCHAR(64)  NOT NULL,
    tool_name         VARCHAR(48)  NOT NULL,
    invoked_by        VARCHAR(16)  NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    arguments_summary VARCHAR(400),
    status            VARCHAR(20)  NOT NULL,
    duration_ms       BIGINT       NOT NULL,
    result_summary    VARCHAR(600),
    result_json       CLOB,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_trace_case ON tool_trace(case_id, run_number);

CREATE TABLE recommendation (
    recommendation_id     VARCHAR(32)   PRIMARY KEY,
    case_id               VARCHAR(32)   NOT NULL REFERENCES fraud_case(case_id),
    run_number            INT           NOT NULL,
    mode                  VARCHAR(64)   NOT NULL,
    classification        VARCHAR(32)   NOT NULL,
    route                 VARCHAR(24)   NOT NULL,
    risk_level            VARCHAR(10)   NOT NULL,
    confidence            DOUBLE PRECISION NOT NULL,
    human_review_required BOOLEAN       NOT NULL,
    rationale             VARCHAR(2000) NOT NULL,
    evidence_ids_json     VARCHAR(2000) NOT NULL,
    missing_info_json     VARCHAR(4000) NOT NULL,
    rule_ids_json         VARCHAR(400)  NOT NULL,
    tool_trace_ids_json   VARCHAR(2000) NOT NULL,
    contradictions_json   VARCHAR(4000) NOT NULL,
    input_facts_json      VARCHAR(4000) NOT NULL,
    priority              VARCHAR(8),
    sla_target_at         TIMESTAMP WITH TIME ZONE,
    sla_trace_id          VARCHAR(32),
    output_hash           VARCHAR(64)   NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE policy_citation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id           VARCHAR(32)   NOT NULL REFERENCES fraud_case(case_id),
    run_number        INT           NOT NULL,
    recommendation_id VARCHAR(32),
    document_id       VARCHAR(32)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    version           VARCHAR(16)   NOT NULL,
    effective_date    DATE          NOT NULL,
    section           VARCHAR(80)   NOT NULL,
    chunk_id          VARCHAR(48)   NOT NULL,
    score             DOUBLE PRECISION NOT NULL,
    above_threshold   BOOLEAN       NOT NULL,
    cited             BOOLEAN       NOT NULL,
    passage           VARCHAR(4000) NOT NULL
);
CREATE INDEX ix_citation_case ON policy_citation(case_id, run_number);

CREATE TABLE review_disposition (
    review_id                 VARCHAR(32)   PRIMARY KEY,
    case_id                   VARCHAR(32)   NOT NULL REFERENCES fraud_case(case_id),
    recommendation_id         VARCHAR(32),
    reviewer_id               VARCHAR(64)   NOT NULL,
    disposition               VARCHAR(24)   NOT NULL,
    selected_route            VARCHAR(24),
    reason                    VARCHAR(2000) NOT NULL,
    missing_info_acknowledged BOOLEAN       NOT NULL,
    resulting_status          VARCHAR(24)   NOT NULL,
    recorded_at               TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE case_message (
    message_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id     VARCHAR(32)   NOT NULL REFERENCES fraud_case(case_id),
    role        VARCHAR(16)   NOT NULL,
    content     VARCHAR(4000) NOT NULL,
    author      VARCHAR(64),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_message_case ON case_message(case_id);

CREATE TABLE audit_event (
    event_id       VARCHAR(40)  PRIMARY KEY,
    sequence_no    BIGINT       NOT NULL UNIQUE,
    case_id        VARCHAR(32),
    event_type     VARCHAR(48)  NOT NULL,
    actor_type     VARCHAR(16)  NOT NULL,
    actor_id       VARCHAR(64)  NOT NULL,
    correlation_id VARCHAR(64),
    summary        VARCHAR(600) NOT NULL,
    payload_hash   VARCHAR(64)  NOT NULL,
    previous_hash  VARCHAR(64)  NOT NULL,
    chain_hash     VARCHAR(64)  NOT NULL,
    recorded_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_case ON audit_event(case_id);

CREATE TRIGGER audit_event_no_update BEFORE UPDATE ON audit_event FOR EACH ROW CALL "com.capstone.fraudcase.persistence.AppendOnlyAuditTrigger";
CREATE TRIGGER audit_event_no_delete BEFORE DELETE ON audit_event FOR EACH ROW CALL "com.capstone.fraudcase.persistence.AppendOnlyAuditTrigger";
