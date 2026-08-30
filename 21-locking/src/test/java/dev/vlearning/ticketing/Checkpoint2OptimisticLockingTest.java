package dev.vlearning.ticketing;

import java.util.concurrent.CyclicBarrier;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.support.Concurrently;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2: one annotation turns silent corruption into a loud, correct failure.
 *
 * <p>Optimistic locking takes no locks and blocks nobody. Every write carries
 * {@code AND version = ?}; a write that updates zero rows means somebody moved
 * first, and JPA turns that into an exception rather than a shrug.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2OptimisticLockingTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Test
    void theLoserOfARaceIsToldSo() {
        var bothHaveRead = new CyclicBarrier(2);
        interleaving.armAfterRead(() -> {
            try {
                bothHaveRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var outcome = Concurrently.run(2, buyer -> bookings.book(CONFERENCE, "buyer" + buyer, 1));

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.failures()).singleElement()
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY - 1);
        assertThat(ticketsSold(CONFERENCE))
                .as("the losing booking row rolled back with its transaction")
                .isEqualTo(1);
    }

    @Test
    void everySuccessfulWriteBumpsTheVersion() {
        bookings.book(CONFERENCE, "ada", 1);
        assertThat(versionOf(CONFERENCE)).isEqualTo(1);

        bookings.book(CONFERENCE, "linus", 1);
        assertThat(versionOf(CONFERENCE)).isEqualTo(2);
    }

    @Test
    void tenConcurrentBuyersNeverOversell() {
        var everybodyHasRead = new CyclicBarrier(CAPACITY);
        interleaving.armAfterRead(() -> {
            try {
                everybodyHasRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var outcome = Concurrently.run(CAPACITY, buyer -> bookings.book(CONFERENCE, "buyer" + buyer, 1));

        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY - outcome.successes());
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(outcome.successes());
        assertThat(outcome.failures())
                .as("under a perfectly synchronised stampede, almost everybody loses")
                .isNotEmpty();
    }
}
