-- The entire event store schema. One table, append-only: no UPDATE or DELETE ever runs
-- against it. Read the PRIMARY KEY carefully — it IS the optimistic lock:
--
--   two writers who both saw version N will both INSERT (stream_id, N+1);
--   the unique index lets exactly one commit and hands the other a duplicate-key error.
--
-- No SELECT ... FOR UPDATE, no version column to bump, no lost-update window. The
-- consistency mechanism is the physical layout of the data.

CREATE TABLE events (
    global_seq  bigint GENERATED ALWAYS AS IDENTITY,  -- store-wide order, feeds projections
    stream_id   text        NOT NULL,                 -- one account = one stream
    version     bigint      NOT NULL,                 -- position in the stream, from 1
    type        text        NOT NULL,                 -- e.g. 'MoneyDeposited'
    payload     jsonb       NOT NULL,                 -- the event record, serialized
    occurred_at timestamptz NOT NULL DEFAULT now(),   -- storage metadata, not business time
    PRIMARY KEY (stream_id, version)
);

CREATE UNIQUE INDEX events_global_seq_idx ON events (global_seq);
