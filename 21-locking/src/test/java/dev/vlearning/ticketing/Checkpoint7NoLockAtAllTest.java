package dev.vlearning.ticketing;

import dev.vlearning.ticketing.booking.BookingService;
import dev.vlearning.ticketing.booking.OptimisticRetry;
import dev.vlearning.ticketing.catalog.SoldOutException;
import dev.vlearning.ticketing.support.Concurrently;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 7: the version column, the row lock and the retry loop all exist to
 * protect a decision you made in Java. Do not make it in Java.
 *
 * <p>One conditional {@code UPDATE} puts the rule in the {@code WHERE} clause,
 * where the database evaluates and enforces it atomically. No read, no lock held
 * across application code, no version conflict, no retry — and the "did it
 * happen?" answer arrives as the update count.
 *
 * <p>The limit is real, and it is the reason the earlier steps exist: this only
 * works when the rule can be written as SQL over the row you are updating.
 * A rule that needs a call to a pricing service, or that spans rows the way
 * project 20's linked overdraft does, cannot be expressed this way.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7NoLockAtAllTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Autowired
    OptimisticRetry retry;

    @Test
    void twentyBuyersForTenTickets_exactlyTenSucceed() {
        retry.reset();

        var outcome = Concurrently.run(2 * CAPACITY, buyer ->
                bookings.bookAtomically(CONFERENCE, "buyer" + buyer, 1));

        assertThat(outcome.successes()).isEqualTo(CAPACITY);
        assertThat(outcome.failures()).hasSize(CAPACITY)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(SoldOutException.class));
        assertThat(available(CONFERENCE)).isZero();
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(CAPACITY);
        assertThat(retry.retryCount())
                .as("nothing conflicted, so nothing was retried and no work was thrown away")
                .isZero();
    }

    @Test
    void theVersionColumnIsNotEvenTouched() {
        bookings.bookAtomically(CONFERENCE, "ada", 1);

        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY - 1);
        assertThat(versionOf(CONFERENCE))
                .as("no entity was loaded, so JPA had nothing to version-check")
                .isZero();
    }

    @Test
    void theCheckConstraintIsTheBackstopUnderneathAllOfIt() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE ticket_type SET available = -1 WHERE id = :id")
                .param("id", CONFERENCE).update())
                .as("the last line of defence, and the only one no application bug can bypass")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("available_not_negative");
    }
}
