package dev.vlearning.trips;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import dev.vlearning.trips.messages.TripMessage.ReserveFlight;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The pristine scaffold's ground truth, in two halves:
 * the booking edge PUBLISHES, the participants RESPOND — and absolutely
 * nothing connects the two. Every trip is born PENDING and stays PENDING.
 * Steps 1 and 4 are two different ways to close that gap.
 */
class HappyPathEventFlowTest extends AbstractIntegrationTest {

    @Test
    void bookingAnnouncesTheTripButNothingCoordinatesIt() throws Exception {
        try (var probe = eventsProbe()) {
            UUID tripId = postTrip();

            var records = probe.awaitRecords(1, Duration.ofSeconds(30));
            assertThat(records.getFirst())
                    .contains("\"TripRequested\"")
                    .contains(tripId.toString());

            // The fact is on the topic; nobody reacts. This is the void your saga fills.
            assertThat(tripStatus(tripId)).isEqualTo("PENDING");
            assertThat(flightStatus(tripId)).isEmpty();
            assertThat(hotelStatus(tripId)).isEmpty();
            assertThat(paymentStatus(tripId)).isEmpty();
        }
    }

    @Test
    void participantsAnswerCommandsWithEvents() {
        try (var probe = eventsProbe()) {
            UUID tripId = UUID.randomUUID();
            bus.publish(topics.flightCommands(), new ReserveFlight(tripId, "Osaka", new BigDecimal("999.00")));

            var records = probe.awaitRecords(1, Duration.ofSeconds(30));
            assertThat(records.getFirst())
                    .contains("\"FlightReserved\"")
                    .contains(tripId.toString());
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(flightStatus(tripId)).contains("RESERVED"));
        }
    }
}
