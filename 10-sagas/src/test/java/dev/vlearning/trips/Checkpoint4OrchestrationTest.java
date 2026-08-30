package dev.vlearning.trips;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 4 — orchestration. Same participants, same outcomes as checkpoints 1–2;
 * what changes is WHERE the flow lives. Your TripSagaOrchestrator
 * (@Profile("orchestration"), listener id "orchestrator") drives commands and
 * reacts to replies, persisting every transition in saga_instance — so the
 * question that took a whole investigation in step 3 becomes one SELECT.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@ActiveProfiles("orchestration")
class Checkpoint4OrchestrationTest extends AbstractIntegrationTest {

    @Test
    void happyPathEndsConfirmedAndTheSagaRecordsIt() throws Exception {
        var tripId = postTrip();

        awaitTripStatus(tripId, "CONFIRMED");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(flightStatus(tripId)).contains("RESERVED");
            assertThat(hotelStatus(tripId)).contains("RESERVED");
            assertThat(paymentStatus(tripId)).contains("CAPTURED");
            assertThat(sagaState(tripId)).contains("DONE/COMPLETED");
        });
    }

    @Test
    void hotelRejectionCompensatesInReverseAndTheSagaRecordsThatToo() throws Exception {
        chaos.failNext("hotel");

        var tripId = postTrip();

        awaitTripStatus(tripId, "REJECTED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(flightStatus(tripId)).contains("CANCELLED");
            assertThat(sagaState(tripId)).contains("DONE/COMPENSATED");
        });
        assertThat(hotelStatus(tripId)).isEmpty();
        assertThat(paymentStatus(tripId)).isEmpty();
    }

    @Test
    void whereIsItStuckIsNowOneSelect() throws Exception {
        chaos.dropNext("hotel"); // the hotel service goes silently deaf for one command

        var tripId = postTrip();

        // The saga parks exactly where the reply went missing — and SAYS so.
        // Compare with the step 3 investigation, which had to infer this from
        // the ABSENCE of rows across three services' tables.
        await().atMost(Duration.ofSeconds(30))
                .during(Duration.ofSeconds(2)) // parked, not just passing through
                .until(() -> sagaState(tripId).equals(Optional.of("AWAITING_HOTEL/RUNNING")));

        assertThat(tripStatus(tripId)).isEqualTo("PENDING");
        assertThat(flightStatus(tripId)).contains("RESERVED");
        assertThat(hotelStatus(tripId)).isEmpty();
    }
}
