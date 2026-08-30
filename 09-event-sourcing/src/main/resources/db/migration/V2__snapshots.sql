-- Snapshots: a cached fold at a known version, one per stream (step 5).
-- Disposable by design — `TRUNCATE snapshots` loses nothing but speed.

CREATE TABLE snapshots (
    stream_id text PRIMARY KEY,
    version   bigint      NOT NULL,
    state     jsonb       NOT NULL,
    taken_at  timestamptz NOT NULL DEFAULT now()
);
