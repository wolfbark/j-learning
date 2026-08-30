package dev.vlearning.ledger.eventstore;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.domain.AccountEvent;

/**
 * YOUR WORK, steps 2–4. The Postgres implementation of the {@link EventStore} contract,
 * against the {@code events} table from {@code V1__event_store.sql}.
 *
 * The one non-negotiable: append must be atomic and race-safe WITHOUT SELECT-then-INSERT
 * locking. The PRIMARY KEY (stream_id, version) does it for you — two writers that both
 * read version N will both try to INSERT version N+1, and Postgres lets exactly one win.
 * Your job is to attempt the insert and translate the loser's unique-key violation
 * (Spring surfaces it as {@link org.springframework.dao.DuplicateKeyException}) into a
 * {@link ConcurrencyException}.
 */
public class PostgresEventStore implements EventStore {

    private final JdbcClient jdbc;
    private final EventSerde serde;

    public PostgresEventStore(JdbcClient jdbc, EventSerde serde) {
        this.jdbc = jdbc;
        this.serde = serde;
    }

    @Override
    public void append(String streamId, long expectedVersion, List<AccountEvent> events) {
        // TODO Step 2: one multi-row INSERT assigning versions expectedVersion+1, +2, …
        //   - a single statement is atomic: all rows or none, no transaction plumbing needed
        //   - payload column is jsonb: bind the JSON string through CAST(? AS jsonb)
        //   - catch DuplicateKeyException -> throw new ConcurrencyException(streamId, expectedVersion)
        throw new UnsupportedOperationException("TODO Step 2 — implement optimistic append");
    }

    @Override
    public List<StoredEvent> readStream(String streamId) {
        // TODO Step 3: SELECT … WHERE stream_id = ? ORDER BY version
        //   Map rows back through serde.fromJson(type, payload). For occurred_at, read an
        //   OffsetDateTime and call toInstant() — the driver handles timestamptz that way.
        throw new UnsupportedOperationException("TODO Step 3 — implement readStream");
    }

    @Override
    public List<StoredEvent> readStream(String streamId, long afterVersion) {
        // TODO Step 3 (used by snapshots in step 5): same as above, AND version > ?
        throw new UnsupportedOperationException("TODO Step 3 — implement readStream after version");
    }

    @Override
    public List<StoredEvent> readAll(long afterGlobalSequence, int limit) {
        // TODO Step 4: WHERE global_seq > ? ORDER BY global_seq LIMIT ? — the projection feed
        throw new UnsupportedOperationException("TODO Step 4 — implement readAll");
    }
}
