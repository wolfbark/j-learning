package dev.vlearning.trips.flight;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage;
import dev.vlearning.trips.messages.TripMessage.FlightCancelled;
import dev.vlearning.trips.messages.TripMessage.FlightRejected;
import dev.vlearning.trips.messages.TripMessage.FlightReserved;
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

/**
 * Participant behavior, no containers: the flight service is a local
 * transaction plus an announcement, and its compensation is an idempotent
 * always-confirm. The learner never edits this service — but should read it.
 */
@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    private static final TripTopics TOPICS =
            new TripTopics("trips.events", "trips.flight.commands", "trips.hotel.commands", "trips.payment.commands");

    @Mock
    private FlightReservations reservations;

    @Mock
    private MessageBus bus;

    private final ChaosToggles chaos = new ChaosToggles();

    private FlightService flights;

    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        flights = new FlightService(reservations, bus, chaos, TOPICS);
    }

    @Test
    void reserveStoresTheReservationAndAnnouncesIt() {
        flights.reserve(tripId, "Lisbon", new BigDecimal("499.50"));

        verify(reservations).insertReserved(eq(tripId), anyString());
        var event = publishedEvent();
        assertThat(event).isInstanceOfSatisfying(FlightReserved.class, reserved -> {
            assertThat(reserved.tripId()).isEqualTo(tripId);
            assertThat(reserved.destination()).isEqualTo("Lisbon");
            assertThat(reserved.price()).isEqualByComparingTo("499.50");
            assertThat(reserved.flightNumber()).startsWith("VL-");
        });
    }

    @Test
    void chaosFailNextRejectsOnceWithoutReserving() {
        chaos.failNext("flight");

        flights.reserve(tripId, "Lisbon", new BigDecimal("499.50"));

        verify(reservations, never()).insertReserved(any(), any());
        assertThat(publishedEvent()).isInstanceOfSatisfying(FlightRejected.class,
                rejected -> assertThat(rejected.reason()).contains("Lisbon"));

        // one-shot: the retry succeeds
        flights.reserve(tripId, "Lisbon", new BigDecimal("499.50"));
        verify(reservations).insertReserved(eq(tripId), anyString());
    }

    @Test
    void cancelIsAnIdempotentCompensation() {
        when(reservations.markCancelled(tripId)).thenReturn(0); // nothing was ever reserved

        flights.cancel(tripId);

        // "the flight is not held" is true either way — compensation always confirms
        assertThat(publishedEvent()).isInstanceOf(FlightCancelled.class);
    }

    private TripMessage publishedEvent() {
        var captor = ArgumentCaptor.forClass(TripMessage.class);
        verify(bus).publish(eq(TOPICS.events()), captor.capture());
        return captor.getValue();
    }
}
