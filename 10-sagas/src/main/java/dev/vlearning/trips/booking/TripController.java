package dev.vlearning.trips.booking;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage.TripRequested;
import dev.vlearning.trips.messages.TripTopics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer-facing edge of the saga. {@code POST /trips} answers 202 — not
 * 201 — because nothing is booked yet: the trip is recorded PENDING and a
 * {@link TripRequested} fact is thrown over the wall. In the pristine scaffold
 * that is where the story ends; whether anything ever reacts is precisely the
 * coordination problem you are here to solve.
 */
@RestController
@RequestMapping("/trips")
public class TripController {

    private final TripRepository trips;
    private final MessageBus bus;
    private final TripTopics topics;

    public TripController(TripRepository trips, MessageBus bus, TripTopics topics) {
        this.trips = trips;
        this.bus = bus;
        this.topics = topics;
    }

    @PostMapping
    public ResponseEntity<TripResponse> book(@RequestBody BookTripRequest request) {
        UUID tripId = UUID.randomUUID();
        trips.insertPending(tripId, request.traveller(), request.destination(), request.price());
        bus.publish(topics.events(),
                new TripRequested(tripId, request.traveller(), request.destination(), request.price()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new TripResponse(tripId, request.traveller(), request.destination(), request.price(),
                        TripStatus.PENDING));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> status(@PathVariable UUID tripId) {
        return ResponseEntity.of(trips.find(tripId)
                .map(trip -> new TripResponse(trip.tripId(), trip.traveller(), trip.destination(), trip.price(),
                        trip.status())));
    }

    public record BookTripRequest(String traveller, String destination, BigDecimal price) {}

    public record TripResponse(UUID tripId, String traveller, String destination, BigDecimal price,
            TripStatus status) {}
}
