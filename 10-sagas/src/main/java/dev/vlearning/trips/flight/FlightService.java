package dev.vlearning.trips.flight;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage.FlightCancelled;
import dev.vlearning.trips.messages.TripMessage.FlightRejected;
import dev.vlearning.trips.messages.TripMessage.FlightReserved;
import dev.vlearning.trips.messages.TripTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The flight participant: a local transaction plus an announcement. Note the
 * compensation contract of {@link #cancel}: it must be safe to call whether or
 * not a reservation exists (sagas retry, messages get redelivered), and it
 * always confirms with {@code FlightCancelled} — "the flight is not held" is
 * true either way.
 */
@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final FlightReservations reservations;
    private final MessageBus bus;
    private final ChaosToggles chaos;
    private final TripTopics topics;

    public FlightService(FlightReservations reservations, MessageBus bus, ChaosToggles chaos, TripTopics topics) {
        this.reservations = reservations;
        this.bus = bus;
        this.chaos = chaos;
        this.topics = topics;
    }

    public void reserve(UUID tripId, String destination, BigDecimal price) {
        if (chaos.consumeFailNext("flight")) {
            log.warn("flight [{}]: CHAOS — rejecting reservation to {}", tripId, destination);
            bus.publish(topics.events(), new FlightRejected(tripId, "No seats left to " + destination));
            return;
        }
        String flightNumber = "VL-%04d".formatted(ThreadLocalRandom.current().nextInt(10_000));
        reservations.insertReserved(tripId, flightNumber);
        log.info("flight [{}]: reserved {} to {}", tripId, flightNumber, destination);
        bus.publish(topics.events(), new FlightReserved(tripId, flightNumber, destination, price));
    }

    public void cancel(UUID tripId) {
        int cancelled = reservations.markCancelled(tripId);
        if (cancelled == 0) {
            log.info("flight [{}]: nothing to cancel — compensation is an idempotent no-op", tripId);
        } else {
            log.info("flight [{}]: reservation cancelled", tripId);
        }
        bus.publish(topics.events(), new FlightCancelled(tripId));
    }
}
