package dev.vlearning.coordination;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.vlearning.coordination.gateway.PaymentGateway.StaleTokenException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 5: since the zombie cannot be stopped, make its work refusable.
 *
 * <p>Every acquisition of the lease hands out a number that only ever goes up.
 * The worker presents that number with every action; the resource remembers the
 * highest number it has seen and refuses anything older. The zombie is still
 * running, still convinced, and now completely harmless — its token is stale, so
 * the charge is rejected at the door.
 *
 * <p>This is the argument in Martin Kleppmann's "How to do distributed locking",
 * and the reason a lock service alone cannot make you safe: safety requires the
 * <em>protected resource</em> to participate.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5FencingTest extends AbstractIntegrationTest {

    private static final String LEASE = "nightly-billing";

    @Test
    void everyAcquisitionGetsAHigherNumber() {
        long first = leases.tryAcquire(LEASE, "pod-a", Duration.ofMillis(200)).orElseThrow().fencingToken();
        sleep(250);
        long second = leases.tryAcquire(LEASE, "pod-b", Duration.ofMillis(200)).orElseThrow().fencingToken();
        sleep(250);
        long third = leases.tryAcquire(LEASE, "pod-c", Duration.ofSeconds(30)).orElseThrow().fencingToken();

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
        assertThat(leases.currentFencingToken(LEASE)).isEqualTo(third);
    }

    @Test
    void aFailedAcquisitionDoesNotBurnAToken() {
        long held = leases.tryAcquire(LEASE, "pod-a", Duration.ofSeconds(30)).orElseThrow().fencingToken();

        assertThat(leases.tryAcquire(LEASE, "pod-b", Duration.ofSeconds(30))).isEmpty();

        assertThat(leases.currentFencingToken(LEASE))
                .as("only a worker that actually took the lease gets a number")
                .isEqualTo(held);
    }

    @Test
    void theZombiesWorkIsRejectedAtTheDoor() throws Exception {
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

        var zombie = Thread.ofVirtual().start(() -> {
            try {
                podA.runWithFencedLease(PERIOD, Duration.ofMillis(300));
            } catch (StaleTokenException expected) {
                // the whole point
            }
        });
        podAHasTheLease.await();
        sleep(400);

        assertThat(podB.runWithFencedLease(PERIOD, Duration.ofSeconds(30))).isTrue();
        podBHasFinished.countDown();
        zombie.join();

        assertThat(gateway.chargesFor("ada"))
                .as("exactly one charge — the live worker's")
                .isEqualTo(1);
        assertThat(gateway.totalCharged()).isEqualTo(TOTAL_OWED);
        assertThat(gateway.charges()).allSatisfy(charge ->
                assertThat(charge.by()).isEqualTo("pod-b"));
    }

    @Test
    void aStaleTokenIsRefusedEvenWithNoZombieInSight() {
        long oldToken = leases.tryAcquire(LEASE, "pod-a", Duration.ofMillis(200)).orElseThrow().fencingToken();
        sleep(250);
        long newToken = leases.tryAcquire(LEASE, "pod-b", Duration.ofSeconds(30)).orElseThrow().fencingToken();

        gateway.chargeFenced(newToken, "ada", 1000, "pod-b");

        assertThatThrownBy(() -> gateway.chargeFenced(oldToken, "ada", 1000, "pod-a"))
                .isInstanceOf(StaleTokenException.class)
                .hasMessageContaining("has already been seen");
    }
}
