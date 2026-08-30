package dev.vlearning.ticketing;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.support.Concurrently;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5: two locks, two customers, opposite order.
 *
 * <p>Deadlock is not an exotic failure — it is what happens when two perfectly
 * ordinary transactions each hold what the other needs next. Postgres notices
 * after {@code deadlock_timeout} (1 s by default), picks a victim and aborts it,
 * which is the only thing it can do. Your job is not to handle deadlocks; it is
 * to make them impossible by always taking locks in the same order.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5DeadlockTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    /**
     * Whoever gets there first holds their first lock for half a second. That is
     * all it takes: it guarantees the second customer is inside the same window,
     * without the two of them having to rendezvous.
     */
    @BeforeEach
    void makeTheFirstCustomerDawdle() {
        var firstArrival = new AtomicBoolean(true);
        interleaving.armAfterRead(() -> {
            if (firstArrival.compareAndSet(true, false)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Test
    void oppositeOrder_deadlocksAndOneCustomerIsSacrificed() {
        var outcome = Concurrently.run(2, customer -> {
            if (customer == 0) {
                bookings.bookBundle(CONFERENCE, WORKSHOP, "ada");
            } else {
                bookings.bookBundle(WORKSHOP, CONFERENCE, "linus");
            }
        });

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.failures()).hasSize(1);
        assertThat(outcome.failures().getFirst())
                .as("SQLSTATE 40P01 — the database broke the tie, because nothing else could")
                .isInstanceOf(CannotAcquireLockException.class)
                .rootCause().hasMessageContaining("deadlock detected");
    }

    @Test
    void aConsistentLockOrderMakesTheDeadlockImpossible() {
        var outcome = Concurrently.run(2, customer -> {
            if (customer == 0) {
                bookings.bookBundleSafely(CONFERENCE, WORKSHOP, "ada");
            } else {
                bookings.bookBundleSafely(WORKSHOP, CONFERENCE, "linus");
            }
        });

        assertThat(outcome.failures())
                .as("the second customer waits for the first and is then served — waiting is not deadlock")
                .isEmpty();
        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY - 2);
        assertThat(available(WORKSHOP)).isEqualTo(CAPACITY - 2);
    }
}
