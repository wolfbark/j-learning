package dev.vlearning.coordination.locking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Postgres will hold a lock on an arbitrary number for you. No table, no row, no
 * schema — you pick a constant and agree, by convention, what it means.
 *
 * <p>There are two families, and the difference is the whole lesson:
 *
 * <ul>
 *   <li>{@code pg_advisory_lock(key)} is held by the <b>session</b> until it is
 *       explicitly unlocked or the connection ends. With a connection pool, the
 *       connection goes back to the pool <em>still holding it</em>, and the next
 *       borrower of that connection inherits a lock nobody knows about.
 *   <li>{@code pg_try_advisory_xact_lock(key)} is held by the <b>transaction</b>
 *       and released by commit or rollback, including the rollback that happens
 *       when the process dies. Nothing to leak, nothing to forget.
 * </ul>
 *
 * <p>Prefer the transaction-scoped one. Always.
 */
@Service
public class AdvisoryLock {

    private final JdbcClient jdbc;

    AdvisoryLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Checkpoint 2: take the lock if it is free, and say so; never wait for it.
     * Must be called inside a transaction — the lock's lifetime is that
     * transaction's lifetime.
     */
    public boolean tryLockForThisTransaction(long key) {
        throw new UnsupportedOperationException(
                "Checkpoint 2: SELECT pg_try_advisory_xact_lock(:key)");
    }

    /** How many advisory locks the server is currently holding, for tests to look at. */
    public long heldAdvisoryLockCount() {
        return jdbc.sql("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'")
                .query(Long.class).single();
    }
}
