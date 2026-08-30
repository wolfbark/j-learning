package dev.vlearning.coordination;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.vlearning.coordination.support.Concurrently;
import dev.vlearning.coordination.support.DbSession.Isolation;
import dev.vlearning.coordination.worker.JobQueue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6: a fleet sharing one table of work.
 *
 * <p>Two mechanisms, two windows of time, and you need both. {@code FOR UPDATE
 * SKIP LOCKED} covers the <em>instant</em> of claiming, so that workers grabbing
 * simultaneously take different jobs instead of queueing. A lease covers the
 * <em>minutes</em> of processing, because the row lock dies with the claiming
 * transaction and the work outlives it.
 *
 * <p>Together they buy the property worth having: a worker that disappears
 * mid-job costs you a delay, not a lost job, and nobody has to notice it died.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6CompetingWorkersTest extends AbstractIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    JobQueue queue;

    @Test
    void aClaimStepsOverWhateverSomebodyElseIsClaimingRightNow() {
        try (var otherWorker = session("other-worker")) {
            otherWorker.begin(Isolation.READ_COMMITTED);
            otherWorker.queryLong("SELECT id FROM job WHERE id = 1 FOR UPDATE");

            assertThat(queue.claim("worker-1", 2, LEASE))
                    .as("job 1 is spoken for; take the next two rather than waiting")
                    .containsExactly(2L, 3L);
        }
    }

    @Test
    void fourWorkersClaimFourDisjointSetsOfWork() {
        List<Long> everythingClaimed = new CopyOnWriteArrayList<>();

        var outcome = Concurrently.run(4, worker ->
                everythingClaimed.addAll(queue.claim("worker-" + worker, 2, LEASE)));

        assertThat(outcome.failures()).isEmpty();
        assertThat(everythingClaimed)
                .as("eight jobs claimed, each by exactly one worker")
                .hasSize(8).doesNotHaveDuplicates();
        assertThat(queue.countByStatus("PENDING")).isEqualTo(2);
    }

    @Test
    void anAbandonedClaimComesBackWithoutAnybodyNoticingTheCrash() {
        var claimedByTheDoomed = queue.claim("worker-doomed", 3, LEASE);
        assertThat(claimedByTheDoomed).hasSize(3);

        // worker-doomed stops existing here. No shutdown hook runs, no release
        // call happens, and nothing tells the queue anything at all.
        queue.expireClaimOf("worker-doomed");

        assertThat(queue.claim("worker-healthy", 3, LEASE))
                .as("the lease ran out, so the work is claimable again")
                .containsExactlyElementsOf(claimedByTheDoomed);
    }

    @Test
    void aFinishedJobIsNotHandedOutAgain() {
        var claimed = queue.claim("worker-1", 2, LEASE);
        claimed.forEach(job -> queue.complete(job, "worker-1"));

        queue.expireClaimOf("worker-1");

        assertThat(queue.claim("worker-2", 10, LEASE))
                .as("DONE is DONE, expired lease or not")
                .doesNotContainAnyElementsOf(claimed);
        assertThat(queue.countByStatus("DONE")).isEqualTo(2);
    }
}
