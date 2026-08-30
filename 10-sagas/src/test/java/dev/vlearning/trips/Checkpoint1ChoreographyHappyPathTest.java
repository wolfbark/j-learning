package dev.vlearning.trips;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 1 — choreography, happy path. Four small event-reaction listeners
 * (one per service package, @Profile("choreography")) and the trip flows
 * flight → hotel → payment → CONFIRMED with no coordinator anywhere.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
@ActiveProfiles("choreography")
class Checkpoint1ChoreographyHappyPathTest extends AbstractIntegrationTest {

    @Test
    void happyPathEndsConfirmedWithAllThreeBookingsInPlace() throws Exception {
        UUID tripId = postTrip();

        awaitTripStatus(tripId, "CONFIRMED");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(flightStatus(tripId)).contains("RESERVED");
            assertThat(hotelStatus(tripId)).contains("RESERVED");
            assertThat(paymentStatus(tripId)).contains("CAPTURED");
        });

        // No coordinator state exists anywhere. Enjoy it while it's a feature —
        // step 3 will show you the invoice.
        assertThat(sagaState(tripId)).isEmpty();
    }
}
