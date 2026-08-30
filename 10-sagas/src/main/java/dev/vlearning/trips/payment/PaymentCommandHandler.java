package dev.vlearning.trips.payment;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.TripMessage.CapturePayment;
import dev.vlearning.trips.messages.TripMessage.RefundPayment;
import dev.vlearning.trips.messages.TripMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The payment service's command inbox. */
@Component
class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final PaymentService payments;
    private final TripMessageCodec codec;
    private final ChaosToggles chaos;

    PaymentCommandHandler(PaymentService payments, TripMessageCodec codec, ChaosToggles chaos) {
        this.payments = payments;
        this.codec = codec;
        this.chaos = chaos;
    }

    @KafkaListener(id = "payment-service", topics = "${trips.topics.payment-commands}")
    void handle(String message) {
        var command = codec.decode(message);
        if (chaos.consumeDropNext("payment")) {
            log.warn("payment: CHAOS — dropping {} without a reply", command);
            return;
        }
        switch (command) {
            case CapturePayment(UUID tripId, BigDecimal amount) -> payments.capture(tripId, amount);
            case RefundPayment(UUID tripId) -> payments.refund(tripId);
            default -> log.warn("payment: ignoring {} — not addressed to this service",
                    command.getClass().getSimpleName());
        }
    }
}
