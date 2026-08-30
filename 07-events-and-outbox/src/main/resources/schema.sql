CREATE TABLE IF NOT EXISTS orders (
    id        uuid PRIMARY KEY,
    customer  varchar(255)  NOT NULL,
    total     numeric(12,2) NOT NULL,
    placed_at timestamptz   NOT NULL
);

CREATE TABLE IF NOT EXISTS fulfillment_tasks (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    uuid         NOT NULL,
    customer    varchar(255) NOT NULL,
    received_at timestamptz  NOT NULL DEFAULT now()
);

-- Step 5 adds a processed_messages table here.
