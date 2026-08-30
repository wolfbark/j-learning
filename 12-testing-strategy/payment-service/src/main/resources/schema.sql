CREATE TABLE IF NOT EXISTS payments (
    id              text          PRIMARY KEY,
    order_id        text          NOT NULL,
    amount          numeric(19,4) NOT NULL,
    currency        text          NOT NULL,
    status          text          NOT NULL,
    decline_reason  text,
    idempotency_key text          NOT NULL,
    created_at      timestamptz   NOT NULL
);

-- The idempotency guarantee is a database constraint, not a hope. Two concurrent
-- requests with the same key cannot both insert; the loser reads the winner's row.
CREATE UNIQUE INDEX IF NOT EXISTS payments_idempotency_key_uq ON payments (idempotency_key);
