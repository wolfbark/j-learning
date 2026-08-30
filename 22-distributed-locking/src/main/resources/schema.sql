DROP TABLE IF EXISTS invoice;
DROP TABLE IF EXISTS job;
DROP TABLE IF EXISTS job_lock;
DROP TABLE IF EXISTS customer;

CREATE TABLE customer (
    id     BIGINT      PRIMARY KEY,
    name   VARCHAR(64) NOT NULL,
    amount BIGINT      NOT NULL
);

CREATE TABLE invoice (
    id       BIGSERIAL   PRIMARY KEY,
    customer VARCHAR(64) NOT NULL,
    period   VARCHAR(16) NOT NULL,
    amount   BIGINT      NOT NULL,
    issued_by VARCHAR(64) NOT NULL
);

-- The lease table. Note what it is not: there is no "locked" boolean, because a
-- boolean cannot be un-set by a process that has died. A lease expires on its
-- own, which is the only property that survives a crash.
CREATE TABLE job_lock (
    name          VARCHAR(64)  PRIMARY KEY,
    locked_until  TIMESTAMPTZ  NOT NULL,
    locked_by     VARCHAR(64),
    fencing_token BIGINT       NOT NULL DEFAULT 0
);

-- Step 6: work handed out to a fleet of workers.
CREATE TABLE job (
    id           BIGINT      PRIMARY KEY,
    payload      VARCHAR(64) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    claimed_by   VARCHAR(64),
    claimed_until TIMESTAMPTZ
);

CREATE INDEX idx_job_status ON job (status);
