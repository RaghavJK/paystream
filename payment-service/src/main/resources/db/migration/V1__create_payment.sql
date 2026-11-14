CREATE TABLE payment (
    id               UUID PRIMARY KEY,
    debtor_account   VARCHAR(64)    NOT NULL,
    creditor_account VARCHAR(64)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    status           VARCHAR(16)    NOT NULL,
    risk_score       INTEGER,
    risk_reason      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_payment_created_at ON payment (created_at DESC);
