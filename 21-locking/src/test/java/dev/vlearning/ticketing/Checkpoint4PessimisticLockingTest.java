package dev.vlearning.ticketing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.support.Concurrently;
import dev.vlearning.ticketing.support.DbSession.Isolation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4: the other family — prevent instead of detect.
 *
 * <p>Nobody is refused, nobody retries; the second caller simply waits. That is
 * better for the customer and worse for your thread pool, and which one matters
 * depends entirely on how long the wait is.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4PessimisticLockingTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Test
    void theSecondBuyerWaitsInsteadOfFailing() throws Exception {
        var firstHasTheLock = new CountDownLatch(1);
        var releaseTheFirst = new CountDownLatch(1);
        interleaving.armAfterRead(() -> {
            firstHasTheLock.countDown();
            try {
                releaseTheFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var first = CompletableFuture.runAsync(() -> bookings.bookWithRowLock(CONFERENCE, "first", 1));
        firstHasTheLock.await();
        interleaving.reset();

        var second = CompletableFuture.runAsync(() -> bookings.bookWithRowLock(CONFERENCE, "second", 1));
        Thread.sleep(600);

        assertThat(second.isDone())
                .as("the second buyer is parked on the row lock, holding a request thread and a connection")
                .isFalse();

        releaseTheFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY - 2);
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(2);
    }

    @Test
    void tenConcurrentBuyersAllSucceed_oneAtATime() {
        var outcome = Concurrently.run(CAPACITY, buyer -> bookings.bookWithRowLock(CONFERENCE, "buyer" + buyer, 1));

        assertThat(outcome.failures())
                .as("no conflicts to resolve: everyone queued and everyone was served")
                .isEmpty();
        assertThat(available(CONFERENCE)).isZero();
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(CAPACITY);
    }

    @Test
    void noWait_refusesImmediatelyRatherThanQueueing() {
        try (var holder = session("holder")) {
            holder.begin(Isolation.READ_COMMITTED);
            holder.update("UPDATE ticket_type SET available = available - 1 WHERE id = ?", CONFERENCE);

            var outcome = Concurrently.run(1, buyer -> bookings.bookOrGiveUp(CONFERENCE, "impatient", 1));

            assertThat(outcome.failures()).hasSize(1);
            assertThat(outcome.failures().getFirst())
                    .as("SQLSTATE 55P03, lock_not_available — a bounded failure beats an unbounded wait")
                    .isInstanceOf(CannotAcquireLockException.class)
                    .rootCause().hasMessageContaining("could not obtain lock on row");
        }
    }
}
