-- Read models (step 4). Also disposable: both tables can be rebuilt from the events table.
-- Step 6 adds a V4 migration of your own for monthly_fees_report.

CREATE TABLE account_balances (
    account_id    text PRIMARY KEY,
    owner         text        NOT NULL,
    balance_cents bigint      NOT NULL,
    status        text        NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- How far each projection has consumed the global feed.
CREATE TABLE projection_checkpoint (
    projection_name text PRIMARY KEY,
    position        bigint NOT NULL
);
