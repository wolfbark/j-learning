package dev.vlearning.trips.hotel;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage;
import dev.vlearning.trips.messages.TripMessage.HotelCancelled;
import dev.vlearning.trips.messages.TripMessage.HotelRejected;
import dev.vlearning.trips.messages.TripMessage.HotelReserved;
import dev.vlearning.trips.messages.TripTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    private static final TripTopics TOPICS =
            new TripTopics("trips.events", "trips.flight.commands", "trips.hotel.commands", "trips.payment.commands");

    @Mock
    private HotelReservations reservations;

    @Mock
    private MessageBus bus;

    private final ChaosToggles chaos = new ChaosToggles();

    private HotelService hotels;

    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hotels = new HotelService(reservations, bus, chaos, TOPICS);
    }

    @Test
    void reserveStoresTheReservationAndAnnouncesIt() {
        hotels.reserve(tripId, "Lisbon", new BigDecimal("499.50"));

        verify(reservations).insertReserved(eq(tripId), anyString());
        assertThat(publishedEvent()).isInstanceOfSatisfying(HotelReserved.class, reserved -> {
            assertThat(reserved.hotelName()).contains("Lisbon");
            assertThat(reserved.price()).isEqualByComparingTo("499.50");
        });
    }

    @Test
    void chaosFailNextRejectsWithoutReserving() {
        chaos.failNext("hotel");

        hotels.reserve(tripId, "Lisbon", new BigDecimal("499.50"));

        verify(reservations, never()).insertReserved(any(), any());
        assertThat(publishedEvent()).isInstanceOfSatisfying(HotelRejected.class,
                rejected -> assertThat(rejected.reason()).contains("Lisbon"));
    }

    @Test
    void cancelIsAnIdempotentCompensation() {
        when(reservations.markCancelled(tripId)).thenReturn(0);

        hotels.cancel(tripId);

        assertThat(publishedEvent()).isInstanceOf(HotelCancelled.class);
    }

    private TripMessage publishedEvent() {
        var captor = ArgumentCaptor.forClass(TripMessage.class);
        verify(bus).publish(eq(TOPICS.events()), captor.capture());
        return captor.getValue();
    }
}
