package dev.vlearning.coordination.locking;

import java.time.Duration;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ShedLock, JobRunr's lock, Kubernetes' {@code Lease} object and half the
 * in-house schedulers ever written are this table and this conditional
 * {@code UPDATE}. Forty lines is enough to see every trade-off in it.
 *
 * <p>The acquisition is the conditional update from project 21, step 7: put the
 * rule in the {@code WHERE} clause and let the update count answer the question.
 * Nothing is locked, nothing waits, and two workers racing for the same lease
 * cannot both win, because the second one's {@code UPDATE} matches no rows.
 */
@Service
public class LeaseService {

    private final JdbcClient jdbc;

    LeaseService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Checkpoint 3: take the named lease for {@code duration} if it is unheld or
     * expired, and return it. Return {@link Optional#empty()} if somebody else
     * holds a lease that has not run out.
     *
     * <p>Checkpoint 5 comes back to this method: the acquisition must also
     * increment {@code fencing_token} and return the new value.
     *
     * <p>Note whose clock decides all of this. Every timestamp here is the
     * <em>database's</em> {@code now()}, never the worker's — a fleet of workers
     * has as many opinions about the time as it has machines.
     */
    @Transactional
    public Optional<Lease> tryAcquire(String name, String owner, Duration duration) {
        throw new UnsupportedOperationException(
                "Checkpoint 3: UPDATE job_lock SET locked_until = now() + :duration, locked_by = :owner "
                        + "WHERE name = :name AND locked_until < now() — then return the row you won");
    }

    /**
     * Give it back early. An optimisation, never a correctness mechanism: if this
     * never runs — because the process died, which is the case you are designing
     * for — the lease still expires.
     */
    @Transactional
    public void release(String name, String owner) {
        jdbc.sql("UPDATE job_lock SET locked_until = now(), locked_by = NULL WHERE name = :name AND locked_by = :owner")
                .param("name", name).param("owner", owner).update();
    }

    @Transactional(readOnly = true)
    public Optional<String> currentHolder(String name) {
        return jdbc.sql("SELECT locked_by FROM job_lock WHERE name = :name AND locked_until > now()")
                .param("name", name).query(String.class).optional();
    }

    @Transactional(readOnly = true)
    public long currentFencingToken(String name) {
        return jdbc.sql("SELECT fencing_token FROM job_lock WHERE name = :name")
                .param("name", name).query(Long.class).single();
    }

    /** Read the lease row whatever its state — for tests and for looking at. */
    @Transactional(readOnly = true)
    public Optional<Lease> peek(String name) {
        return jdbc.sql("SELECT name, locked_by, locked_until, fencing_token FROM job_lock WHERE name = :name")
                .param("name", name)
                .query((rs, n) -> new Lease(rs.getString("name"), rs.getString("locked_by"),
                        rs.getTimestamp("locked_until").toInstant(), rs.getLong("fencing_token")))
                .optional();
    }
}
