package dev.vlearning.coordination.worker;

import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A work queue in a table, handed out to a fleet.
 *
 * <p>It needs both mechanisms from this training at once, for two different
 * windows of time:
 *
 * <ul>
 *   <li><b>{@code FOR UPDATE SKIP LOCKED}</b> covers the instant of claiming, so
 *       that ten workers grabbing at the same moment take ten different jobs
 *       instead of queueing behind the first one (project 21, step 6);
 *   <li><b>a lease</b> ({@code claimed_until}) covers the minutes of processing,
 *       because the row lock dies with the claiming transaction and the work
 *       outlives it.
 * </ul>
 *
 * <p>A worker that crashes mid-job releases its row lock immediately and its
 * lease when it expires; the job comes back on its own. Nobody has to notice
 * that the worker died, which is the property you are actually buying.
 */
@Service
public class JobQueue {

    private final JdbcClient jdbc;

    JobQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Checkpoint 6: claim up to {@code max} jobs that are pending, or whose
     * previous claim has expired, without waiting for any job another worker is
     * claiming right now.
     */
    @Transactional
    public List<Long> claim(String workerId, int max, Duration lease) {
        throw new UnsupportedOperationException("""
                Checkpoint 6: select claimable ids with FOR UPDATE SKIP LOCKED, then mark them \
                CLAIMED by :workerId until now() + :lease, and return them""");
    }

    @Transactional
    public void complete(long jobId, String workerId) {
        jdbc.sql("UPDATE job SET status = 'DONE' WHERE id = :id AND claimed_by = :by")
                .param("id", jobId).param("by", workerId).update();
    }

    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return jdbc.sql("SELECT count(*) FROM job WHERE status = :status")
                .param("status", status).query(Long.class).single();
    }

    /** Force a claim to look abandoned, standing in for a worker that stopped existing. */
    @Transactional
    public void expireClaimOf(String workerId) {
        jdbc.sql("UPDATE job SET claimed_until = now() - interval '1 minute' WHERE claimed_by = :by")
                .param("by", workerId).update();
    }
}
