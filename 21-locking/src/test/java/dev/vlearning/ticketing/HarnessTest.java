package dev.vlearning.ticketing;

import dev.vlearning.ticketing.booking.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Green on checkout: the container starts, schema.sql loads, Hibernate validates
 * its mappings against it, and one uncontended booking behaves.
 */
class HarnessTest extends AbstractIntegrationTest {

    @Autowired
    BookingService bookings;

    @Test
    void theConferenceIsOpenForBusiness() {
        assertThat(available(CONFERENCE)).isEqualTo(CAPACITY);
        assertThat(available(WORKSHOP)).isEqualTo(CAPACITY);
        assertThat(jdbc.sql("SELECT count(*) FROM seat").query(Long.class).single()).isEqualTo(SEATS);
    }

    @Test
    void aSingleBookingSellsASingleTicket() {
        bookings.book(CONFERENCE, "ada", 2);

        assertThat(available(CONFERENCE)).isEqualTo(8);
        assertThat(ticketsSold(CONFERENCE)).isEqualTo(2);
    }
}
