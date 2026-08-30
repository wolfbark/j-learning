package dev.vlearning.trips;

import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 2 — choreography, compensation. A saga isn't the happy chain; it's the
 * promise that every local transaction has an undo, and that SOMETHING runs the
 * undos when a later step refuses. In choreography that something is more
 * listeners: participants reacting to each other's failure events.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
@ActiveProfiles("choreography")
class Checkpoint2ChoreographyCompensationTest extends AbstractIntegrationTest {

    @Test
    void hotelRejectionCancelsTheFlightAndRejectsTheTrip() throws Exception {
        chaos.failNext("hotel");

        var tripId = postTrip();

        awaitTripStatus(tripId, "REJECTED");
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(flightStatus(tripId)).contains("CANCELLED"));
        assertThat(hotelStatus(tripId)).isEmpty();   // never reserved
        assertThat(paymentStatus(tripId)).isEmpty(); // never reached
    }

    @Test
    void paymentFailureUnwindsHotelAndFlight() throws Exception {
        chaos.failNext("payment");

        var tripId = postTrip();

        awaitTripStatus(tripId, "REJECTED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(flightStatus(tripId)).contains("CANCELLED");
            assertThat(hotelStatus(tripId)).contains("CANCELLED");
        });
        assertThat(paymentStatus(tripId)).isEmpty(); // the capture never happened
    }

    @Test
    void flightRejectionRejectsTheTripWithNothingToCompensate() throws Exception {
        chaos.failNext("flight");

        var tripId = postTrip();

        awaitTripStatus(tripId, "REJECTED");
        assertThat(flightStatus(tripId)).isEmpty();
        assertThat(hotelStatus(tripId)).isEmpty();
        assertThat(paymentStatus(tripId)).isEmpty();
    }
}
