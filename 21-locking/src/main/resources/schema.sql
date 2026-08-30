DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS ticket_type;

CREATE TABLE ticket_type (
    id        BIGINT       PRIMARY KEY,
    name      VARCHAR(64)  NOT NULL,
    price     BIGINT       NOT NULL,
    available INT          NOT NULL,
    version   BIGINT       NOT NULL DEFAULT 0,

    -- The backstop, not the solution. Step 1 oversells the conference without
    -- ever violating this: writing "9" when the true answer is "8" is legal.
    CONSTRAINT available_not_negative CHECK (available >= 0)
);

CREATE TABLE booking (
    id             BIGSERIAL   PRIMARY KEY,
    ticket_type_id BIGINT      NOT NULL REFERENCES ticket_type (id),
    attendee       VARCHAR(64) NOT NULL,
    quantity       INT         NOT NULL
);

-- Step 6's subject: a work queue that happens to be made of chairs.
CREATE TABLE seat (
    id      BIGINT      PRIMARY KEY,
    section VARCHAR(16) NOT NULL,
    label   VARCHAR(16) NOT NULL,
    status  VARCHAR(16) NOT NULL,
    held_by VARCHAR(64)
);

CREATE INDEX idx_seat_status ON seat (status);
