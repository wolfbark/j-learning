package dev.vlearning.trips;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import dev.vlearning.trips.messages.TripMessage.ReserveHotel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 5 — the essence of durable execution, hand-rolled. The orchestrator
 * "crashes" while a saga is parked mid-flight; the world keeps moving (the
 * missing reply arrives); the orchestrator comes back, reads NOTHING but its
 * saga_instance row and the topic backlog, and finishes the job. If your
 * orchestrator keeps any per-saga state in fields instead of the table, this
 * is where that sin is punished.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
@ActiveProfiles("orchestration")
class Checkpoint5CrashResumeTest extends AbstractIntegrationTest {

    @Test
    void sagaResumesFromPersistedStateAfterTheOrchestratorCrashes() throws Exception {
        // A hotel outage parks the saga at AWAITING_HOTEL (as in checkpoint 4)...
        chaos.dropNext("hotel");
        var tripId = postTrip();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> sagaState(tripId).equals(Optional.of("AWAITING_HOTEL/RUNNING")));

        // ...and then the orchestrator dies.
        orchestratorSwitch.crash();

        // While it is down, ops re-delivers the lost command (same values as
        // postTrip books) — the hotel answers, the reply waits in the topic.
        bus.publish(topics.hotelCommands(), new ReserveHotel(tripId, "Lisbon", new BigDecimal("499.50")));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(hotelStatus(tripId)).contains("RESERVED"));

        // Nobody is driving: the trip cannot advance, but no progress is lost either.
        assertThat(tripStatus(tripId)).isEqualTo("PENDING");
        assertThat(sagaState(tripId)).contains("AWAITING_HOTEL/RUNNING");

        // Restart. No warm-up, no cache, no memory — just the row and the backlog.
        orchestratorSwitch.restart();

        awaitTripStatus(tripId, "CONFIRMED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(paymentStatus(tripId)).contains("CAPTURED");
            assertThat(sagaState(tripId)).contains("DONE/COMPLETED");
        });
    }
}
