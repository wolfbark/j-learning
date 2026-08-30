package dev.vlearning.trips.hotel;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage.HotelCancelled;
import dev.vlearning.trips.messages.TripMessage.HotelRejected;
import dev.vlearning.trips.messages.TripMessage.HotelReserved;
import dev.vlearning.trips.messages.TripTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The hotel participant — the saga's favorite failure point. With
 * {@code fail-next} armed it answers {@link HotelRejected} instead of
 * reserving; by then the flight is already booked, and SOMEBODY has to notice
 * and undo it. Who that somebody is — scattered listeners or one orchestrator —
 * is the whole lesson.
 */
@Service
public class HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelService.class);

    private final HotelReservations reservations;
    private final MessageBus bus;
    private final ChaosToggles chaos;
    private final TripTopics topics;

    public HotelService(HotelReservations reservations, MessageBus bus, ChaosToggles chaos, TripTopics topics) {
        this.reservations = reservations;
        this.bus = bus;
        this.chaos = chaos;
        this.topics = topics;
    }

    public void reserve(UUID tripId, String destination, BigDecimal price) {
        if (chaos.consumeFailNext("hotel")) {
            log.warn("hotel [{}]: CHAOS — no rooms in {}", tripId, destination);
            bus.publish(topics.events(), new HotelRejected(tripId, "No rooms left in " + destination));
            return;
        }
        String hotelName = "Grand Hotel " + destination;
        reservations.insertReserved(tripId, hotelName);
        log.info("hotel [{}]: reserved a room at {}", tripId, hotelName);
        bus.publish(topics.events(), new HotelReserved(tripId, hotelName, price));
    }

    public void cancel(UUID tripId) {
        int cancelled = reservations.markCancelled(tripId);
        if (cancelled == 0) {
            log.info("hotel [{}]: nothing to cancel — compensation is an idempotent no-op", tripId);
        } else {
            log.info("hotel [{}]: reservation cancelled", tripId);
        }
        bus.publish(topics.events(), new HotelCancelled(tripId));
    }
}
