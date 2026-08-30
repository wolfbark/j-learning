package dev.vlearning.trips.flight;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.TripMessage.CancelFlight;
import dev.vlearning.trips.messages.TripMessage.ReserveFlight;
import dev.vlearning.trips.messages.TripMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The flight service's command inbox — its public API, if you squint past the
 * missing network. Anyone may drop a {@link ReserveFlight} or
 * {@link CancelFlight} here; in round 2 that someone is your orchestrator.
 * A {@code drop-next} chaos toggle makes the whole service go conveniently
 * deaf for one command.
 */
@Component
class FlightCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightCommandHandler.class);

    private final FlightService flights;
    private final TripMessageCodec codec;
    private final ChaosToggles chaos;

    FlightCommandHandler(FlightService flights, TripMessageCodec codec, ChaosToggles chaos) {
        this.flights = flights;
        this.codec = codec;
        this.chaos = chaos;
    }

    @KafkaListener(id = "flight-service", topics = "${trips.topics.flight-commands}")
    void handle(String message) {
        var command = codec.decode(message);
        if (chaos.consumeDropNext("flight")) {
            log.warn("flight: CHAOS — dropping {} without a reply", command);
            return;
        }
        switch (command) {
            case ReserveFlight(UUID tripId, String destination, BigDecimal price) ->
                    flights.reserve(tripId, destination, price);
            case CancelFlight(UUID tripId) -> flights.cancel(tripId);
            default -> log.warn("flight: ignoring {} — not addressed to this service",
                    command.getClass().getSimpleName());
        }
    }
}
