-- Approval requests and their ordered approval chain.
CREATE TABLE approval_request (
    id            UUID PRIMARY KEY,
    title         VARCHAR(200)   NOT NULL,
    category      VARCHAR(40)    NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency      VARCHAR(3)     NOT NULL,
    requester_id  VARCHAR(100)   NOT NULL,
    status        VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    current_step  INTEGER,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    -- a finished request has no current step; a pending one always does
    CONSTRAINT current_step_matches_status CHECK ((status = 'PENDING') = (current_step IS NOT NULL))
);
CREATE INDEX idx_request_requester ON approval_request (requester_id, created_at DESC);
CREATE INDEX idx_request_pending ON approval_request (current_step) WHERE status = 'PENDING';

CREATE TABLE approval_step (
    id             BIGSERIAL PRIMARY KEY,
    request_id     UUID         NOT NULL REFERENCES approval_request (id) ON DELETE CASCADE,
    step_order     INTEGER      NOT NULL,
    required_role  VARCHAR(20)  NOT NULL,
    decision       VARCHAR(16)  NOT NULL CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    decided_by     VARCHAR(100),
    decided_at     TIMESTAMPTZ,
    comment        VARCHAR(1000),
    CONSTRAINT uq_step_order UNIQUE (request_id, step_order)
);
CREATE INDEX idx_step_role ON approval_step (required_role, request_id);

-- Append-only history of who did what.
CREATE TABLE audit_event (
    id           BIGSERIAL PRIMARY KEY,
    request_id   UUID          NOT NULL REFERENCES approval_request (id) ON DELETE CASCADE,
    actor        VARCHAR(100)  NOT NULL,
    action       VARCHAR(30)   NOT NULL,
    detail       VARCHAR(1000),
    occurred_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_audit_request ON audit_event (request_id, id);

-- Transactional outbox: written in the same transaction as the state change,
-- relayed to Kafka afterwards so a broker outage never loses or invents an event.
CREATE TABLE outbox_event (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  UUID          NOT NULL,
    event_type    VARCHAR(60)   NOT NULL,
    payload       TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER       NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;

-- Stored responses for Idempotency-Key replays, scoped per user.
CREATE TABLE idempotency_record (
    user_id          VARCHAR(100) NOT NULL,
    idem_key         VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_status  INTEGER      NOT NULL,
    response_body    TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
