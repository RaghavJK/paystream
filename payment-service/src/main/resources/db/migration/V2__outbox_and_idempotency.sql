-- Transactional outbox: an event row is written in the same transaction as the business change.
CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    sent_at      TIMESTAMPTZ
);

-- Partial index: the relay only ever looks for unsent rows.
CREATE INDEX idx_outbox_unsent ON outbox (id) WHERE sent_at IS NULL;

-- One row per client-supplied Idempotency-Key. The primary key is what stops concurrent duplicates.
CREATE TABLE idempotency_record (
    idem_key     VARCHAR(100) PRIMARY KEY,
    payment_id   UUID         NOT NULL REFERENCES payment (id),
    request_hash VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
