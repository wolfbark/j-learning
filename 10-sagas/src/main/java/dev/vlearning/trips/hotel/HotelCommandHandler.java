package dev.vlearning.trips.hotel;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.TripMessage.CancelHotel;
import dev.vlearning.trips.messages.TripMessage.ReserveHotel;
import dev.vlearning.trips.messages.TripMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The hotel service's command inbox. {@code drop-next} chaos swallows the next
 * command without a reply — the silent outage that checkpoint 4 diagnoses with
 * one SELECT and checkpoint 3 diagnoses with grief.
 */
@Component
class HotelCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(HotelCommandHandler.class);

    private final HotelService hotels;
    private final TripMessageCodec codec;
    private final ChaosToggles chaos;

    HotelCommandHandler(HotelService hotels, TripMessageCodec codec, ChaosToggles chaos) {
        this.hotels = hotels;
        this.codec = codec;
        this.chaos = chaos;
    }

    @KafkaListener(id = "hotel-service", topics = "${trips.topics.hotel-commands}")
    void handle(String message) {
        var command = codec.decode(message);
        if (chaos.consumeDropNext("hotel")) {
            log.warn("hotel: CHAOS — dropping {} without a reply", command);
            return;
        }
        switch (command) {
            case ReserveHotel(UUID tripId, String destination, BigDecimal price) ->
                    hotels.reserve(tripId, destination, price);
            case CancelHotel(UUID tripId) -> hotels.cancel(tripId);
            default -> log.warn("hotel: ignoring {} — not addressed to this service",
                    command.getClass().getSimpleName());
        }
    }
}
