package dev.vlearning.ticketing;

import java.util.concurrent.CyclicBarrier;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.support.Concurrently;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1: sell the conference twice over, with transactions, in JPA, without a
 * single error.
 *
 * <p>These tests pass against the code as delivered. They pin the bug; they do
 * not fix it. After step 2 adds one annotation, all three MUST fail — that is
 * the repair working. Re-disable the class at that point; it stays in the repo
 * as the exhibit of what you fixed, the same way {@code 07-events-and-outbox}
 * keeps its dual-write tests.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1OversellTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Test
    void tenConcurrentBuyers_takeTenTicketsAndDecrementTheCounterOnce() {
        var everybodyHasRead = new CyclicBarrier(CAPACITY);
        interleaving.armAfterRead(() -> {
            try {
                everybodyHasRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var outcome = Concurrently.run(CAPACITY, buyer -> bookings.book(CONFERENCE, "buyer" + buyer, 1));

        assertThat(outcome.failures()).as("every buyer is told they have a ticket").isEmpty();
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(CAPACITY);
        assertThat(available(CONFERENCE))
                .as("ten tickets sold, and the counter believes nine are left")
                .isEqualTo(CAPACITY - 1);
    }

    @Test
    void theCheckConstraintCannotSaveYou() {
        var everybodyHasRead = new CyclicBarrier(CAPACITY);
        interleaving.armAfterRead(() -> {
            try {
                everybodyHasRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        Concurrently.run(CAPACITY, buyer -> bookings.book(CONFERENCE, "buyer" + buyer, 1));

        // schema.sql carries CHECK (available >= 0). It was never violated: the
        // last writer wrote 9, which is a perfectly legal number. A constraint
        // can only reject a value it can see, and the wrong value looks fine.
        assertThat(available(CONFERENCE)).isNotNegative();
    }

    @Test
    void versionIsInTheSchemaButNothingIsUsingIt() {
        bookings.book(CONFERENCE, "ada", 1);

        assertThat(versionOf(CONFERENCE))
                .as("the column is there; step 2 is one annotation")
                .isZero();
    }
}
