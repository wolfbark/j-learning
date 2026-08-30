package dev.vlearning.ticketing.seating;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Claiming a seat is claiming a row: find one nobody has, take it, mark it.
 * That is also the shape of every database-backed work queue you will ever
 * write, which is why this step is worth more than the chairs suggest.
 *
 * <p>Plain SQL, in a JPA project, on purpose: {@code FOR UPDATE SKIP LOCKED} is
 * a statement-level decision and reads better as one.
 */
@Repository
public class SeatRepository {

    private final JdbcClient jdbc;

    SeatRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The obvious version, and the one almost everybody writes first.
     *
     * <p>{@code LIMIT 1} is applied before the lock wait, so a caller that blocks
     * here and then finds the row no longer qualifies does not move on to the
     * next seat — it comes back with nothing at all, in a hall full of empty
     * chairs. Step 6 shows exactly that.
     */
    public Optional<Long> nextFreeSeatBlocking() {
        return jdbc.sql("""
                        SELECT id FROM seat
                         WHERE status = 'FREE'
                         ORDER BY id
                         LIMIT 1
                           FOR UPDATE
                        """)
                .query(Long.class).optional();
    }

    /**
     * Checkpoint 6: the same query, but a row somebody else is holding is not
     * something to wait for — it is something to step over.
     */
    public Optional<Long> nextFreeSeatSkippingLocked() {
        throw new UnsupportedOperationException(
                "Checkpoint 6: the same query with one more clause on the FOR UPDATE");
    }

    public void hold(long seatId, String heldBy) {
        jdbc.sql("UPDATE seat SET status = 'HELD', held_by = :by WHERE id = :id")
                .param("by", heldBy).param("id", seatId).update();
    }

    public long freeSeatCount() {
        return jdbc.sql("SELECT count(*) FROM seat WHERE status = 'FREE'").query(Long.class).single();
    }
}
