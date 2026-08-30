CREATE TABLE IF NOT EXISTS orders (
    id          uuid PRIMARY KEY,
    customer_id text        NOT NULL,
    item        text        NOT NULL,
    quantity    int         NOT NULL,
    status      text        NOT NULL,
    shipment_id text,
    placed_at   timestamptz NOT NULL
);
