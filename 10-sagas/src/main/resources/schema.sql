-- Four "services", four data ownerships. In production these would be four
-- databases; here they are four tables that no foreign key is allowed to
-- connect — each service knows only its own slice of the truth, which is
-- exactly why "where is booking #42 stuck?" is hard in step 3.

-- booking service
CREATE TABLE IF NOT EXISTS trips (
    trip_id     uuid PRIMARY KEY,
    traveller   text NOT NULL,
    destination text NOT NULL,
    price       numeric(10, 2) NOT NULL,
    status      text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- flight service
CREATE TABLE IF NOT EXISTS flight_reservations (
    trip_id       uuid PRIMARY KEY,
    flight_number text NOT NULL,
    status        text NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- hotel service
CREATE TABLE IF NOT EXISTS hotel_reservations (
    trip_id    uuid PRIMARY KEY,
    hotel_name text NOT NULL,
    status     text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- payment service
CREATE TABLE IF NOT EXISTS payments (
    trip_id    uuid PRIMARY KEY,
    amount     numeric(10, 2) NOT NULL,
    status     text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- the orchestrator's memory (round 2). One row per saga; current_step and
-- status use the SagaStep / SagaStatus enum names.
CREATE TABLE IF NOT EXISTS saga_instance (
    trip_id      uuid PRIMARY KEY,
    current_step text NOT NULL,
    status       text NOT NULL,
    updated_at   timestamptz NOT NULL DEFAULT now()
);
