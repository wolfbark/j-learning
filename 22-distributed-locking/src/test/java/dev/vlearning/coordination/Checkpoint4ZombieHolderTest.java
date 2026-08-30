package dev.vlearning.coordination;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4: the lease expired and nobody told the holder.
 *
 * <p>This is the failure that makes distributed locking a genuinely hard
 * problem rather than a library choice. Every mechanism so far — advisory lock,
 * lease, anything you build on a timeout — has the same hole: a worker can be
 * paused between checking that it holds the lock and using it. A stop-the-world
 * GC, a hypervisor migration, a throttled container, a suspended laptop. Ten
 * seconds is nothing.
 *
 * <p>While it is paused, its lease expires. Another worker takes it, perfectly
 * legitimately. Then the first one wakes up and carries on, because from the
 * inside nothing happened at all.
 *
 * <p>These tests pass against the code as delivered. They pin the bug — and this
 * time there is no annotation that fixes it.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4ZombieHolderTest extends AbstractIntegrationTest {

    private static final Duration SHORT_LEASE = Duration.ofMillis(300);

    @Test
    void aPausedWorkerKeepsWorkingAfterItsLeaseIsGone() throws Exception {
        var podA = worker("pod-a");
        var podB = worker("pod-b");

        var podAHasTheLease = new CountDownLatch(1);
        var podBHasFinished = new CountDownLatch(1);
        podA.pauseAfterTakingTheLock(() -> {
            podAHasTheLease.countDown();
            try {
                podBHasFinished.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var paused = Thread.ofVirtual().start(() -> podA.runWithLease(PERIOD, SHORT_LEASE));
        podAHasTheLease.await();

        // pod-a's lease runs out while it is not running.
        sleep(400);
        assertThat(leases.currentHolder("nightly-billing"))
                .as("expired: as far as the lock service is concerned, nobody is billing")
                .isEmpty();

        assertThat(podB.runWithLease(PERIOD, Duration.ofSeconds(30)))
                .as("pod-b acquires it entirely legitimately and does the work")
                .isTrue();
        podBHasFinished.countDown();
        paused.join();

        assertThat(gateway.chargesFor("ada"))
                .as("and then the zombie finishes its run, having never done anything wrong")
                .isEqualTo(2);
        assertThat(gateway.totalCharged()).isEqualTo(2 * TOTAL_OWED);
    }

    @Test
    void theLockServiceIsCorrect_theSystemIsNot() throws Exception {
        var podA = worker("pod-a");
        var podAHasTheLease = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        podA.pauseAfterTakingTheLock(() -> {
            podAHasTheLease.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var paused = Thread.ofVirtual().start(() -> podA.runWithLease(PERIOD, SHORT_LEASE));
        podAHasTheLease.await();
        sleep(400);

        // Nobody lied, nothing is inconsistent, no bug was introduced: the lease
        // was granted for 300ms and 300ms passed. The mistake was believing that
        // "I acquired a lease" is the same statement as "I hold it now".
        assertThat(leases.currentHolder("nightly-billing")).isEmpty();
        assertThat(leases.peek("nightly-billing")).get()
                .extracting(lease -> lease.owner())
                .isEqualTo("pod-a");

        release.countDown();
        paused.join();
    }
}
