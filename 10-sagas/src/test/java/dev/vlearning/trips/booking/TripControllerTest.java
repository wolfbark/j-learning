package dev.vlearning.trips.booking;

import java.math.BigDecimal;

import dev.vlearning.trips.booking.TripController.BookTripRequest;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage;
import dev.vlearning.trips.messages.TripMessage.TripRequested;
import dev.vlearning.trips.messages.TripTopics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    private static final TripTopics TOPICS =
            new TripTopics("trips.events", "trips.flight.commands", "trips.hotel.commands", "trips.payment.commands");

    @Mock
    private TripRepository trips;

    @Mock
    private MessageBus bus;

    @Test
    void bookingIsRecordedPendingAndAnnounced() {
        var controller = new TripController(trips, bus, TOPICS);

        var response = controller.book(new BookTripRequest("Ada Lovelace", "Lisbon", new BigDecimal("499.50")));

        var body = response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(202); // accepted, not booked
        assertThat(body.status()).isEqualTo(TripStatus.PENDING);
        verify(trips).insertPending(body.tripId(), "Ada Lovelace", "Lisbon", new BigDecimal("499.50"));

        var captor = ArgumentCaptor.forClass(TripMessage.class);
        verify(bus).publish(eq(TOPICS.events()), captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(TripRequested.class, requested -> {
            assertThat(requested.tripId()).isEqualTo(body.tripId());
            assertThat(requested.destination()).isEqualTo("Lisbon");
            assertThat(requested.price()).isEqualByComparingTo("499.50");
        });
    }
}
