package dev.vlearning.ticketing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.booking.OptimisticRetry;
import dev.vlearning.ticketing.support.Concurrently;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: a version conflict is not an answer to give a customer.
 *
 * <p>Ten buyers, ten tickets, every one of them entitled to a seat. Optimistic
 * locking makes nine of them fail; the retry makes all ten succeed without ever
 * overselling. Where optimistic locking earns its keep is precisely here — when
 * conflicts are rare enough that the retry is cheaper than the lock everyone
 * else would have waited on.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3RetryTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Autowired
    OptimisticRetry retry;

    @BeforeEach
    void resetCounters() {
        retry.reset();
    }

    @Test
    void everyEntitledBuyerGetsATicket() {
        forceEverybodyToReadBeforeAnybodyWrites();

        var outcome = Concurrently.run(CAPACITY, buyer ->
                retry.execute(() -> bookings.book(CONFERENCE, "buyer" + buyer, 1)));

        assertThat(outcome.failures()).isEmpty();
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(CAPACITY);
        assertThat(available(CONFERENCE)).isZero();
        assertThat(retry.retryCount())
                .as("if nothing was retried, the loop was not exercised")
                .isPositive();
    }

    @Test
    void theEleventhBuyerIsStillToldTheTruth() {
        Concurrently.run(CAPACITY, buyer -> retry.execute(() -> bookings.book(CONFERENCE, "buyer" + buyer, 1)));

        var outcome = Concurrently.run(1, buyer ->
                retry.execute(() -> bookings.book(CONFERENCE, "latecomer", 1)));

        assertThat(outcome.successes()).isZero();
        assertThat(outcome.failures()).hasSize(1);
        assertThat(outcome.failures().getFirst())
                .as("sold out is a business answer, not a conflict — retrying it forever would be a bug")
                .hasMessageContaining("conference");
        assertThat(available(CONFERENCE)).isZero();
    }

    /** Guarantees the conflict rather than hoping for it; retried attempts sail past. */
    private void forceEverybodyToReadBeforeAnybodyWrites() {
        var arrived = new AtomicInteger();
        var firstWaveHasRead = new CountDownLatch(CAPACITY);
        interleaving.armAfterRead(() -> {
            if (arrived.incrementAndGet() > CAPACITY) {
                return;
            }
            firstWaveHasRead.countDown();
            try {
                firstWaveHasRead.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
