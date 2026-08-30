package dev.vlearning.coordination;

import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: a lock the holder cannot take to the grave.
 *
 * <p>An advisory lock is released when the transaction ends — which is perfect
 * for work that fits in a transaction and useless for work that does not. A
 * nightly billing run takes minutes; you are not holding a database transaction
 * open for minutes (project 20, step 8, and the connection it pins).
 *
 * <p>So the lock becomes a row with an expiry, and "do I hold it?" becomes a
 * conditional {@code UPDATE} — the same statement that solved project 21's
 * oversell, doing the same job: put the rule in the {@code WHERE} clause and let
 * the update count answer.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3LeaseTest extends AbstractIntegrationTest {

    private static final String LEASE = "nightly-billing";

    @Test
    void theLeaseGoesToExactlyOneOfTwoWorkers() {
        var first = leases.tryAcquire(LEASE, "pod-a", Duration.ofSeconds(30));
        var second = leases.tryAcquire(LEASE, "pod-b", Duration.ofSeconds(30));

        assertThat(first).isPresent();
        assertThat(second).as("the second UPDATE matched no rows, so it lost — no waiting involved").isEmpty();
        assertThat(leases.currentHolder(LEASE)).contains("pod-a");
    }

    @Test
    void anExpiredLeaseIsAvailableAgain_withNobodyToReleaseIt() {
        assertThat(leases.tryAcquire(LEASE, "pod-a", Duration.ofMillis(300))).isPresent();

        // pod-a is now, as far as anybody can tell, gone: no release call is
        // coming, no connection is going to close, nobody is going to notice.
        sleep(400);

        assertThat(leases.tryAcquire(LEASE, "pod-b", Duration.ofSeconds(30)))
                .as("the lease expired by itself, which is the only thing a dead process can do for you")
                .isPresent();
        assertThat(leases.currentHolder(LEASE)).contains("pod-b");
    }

    @Test
    void releasingEarlyIsAnOptimisation() {
        assertThat(leases.tryAcquire(LEASE, "pod-a", Duration.ofMinutes(10))).isPresent();
        leases.release(LEASE, "pod-a");

        assertThat(leases.currentHolder(LEASE)).isEmpty();
        assertThat(leases.tryAcquire(LEASE, "pod-b", Duration.ofSeconds(30)))
                .as("the next run starts now rather than in ten minutes")
                .isPresent();
    }

    @Test
    void onlyTheHolderCanRelease() {
        leases.tryAcquire(LEASE, "pod-a", Duration.ofMinutes(10));

        leases.release(LEASE, "pod-b");

        assertThat(leases.currentHolder(LEASE))
                .as("a worker that thinks it holds the lease must not be able to free somebody else's")
                .contains("pod-a");
    }

    @Test
    void oneWorkerRunsTheBilling_theOtherDoesNothing() {
        var podA = worker("pod-a");
        var podB = worker("pod-b");

        assertThat(podA.runWithLease(PERIOD, Duration.ofSeconds(30))).isTrue();
        assertThat(podB.runWithLease(PERIOD, Duration.ofSeconds(30))).isFalse();

        assertThat(gateway.totalCharged()).isEqualTo(TOTAL_OWED);
        assertThat(invoiceCount()).isEqualTo(CUSTOMERS);
    }
}
