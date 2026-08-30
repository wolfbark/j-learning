package dev.vlearning.trips.payment;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage.PaymentCaptured;
import dev.vlearning.trips.messages.TripMessage.PaymentFailed;
import dev.vlearning.trips.messages.TripMessage.PaymentRefunded;
import dev.vlearning.trips.messages.TripTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The payment participant. A {@link PaymentFailed} here is the deep failure:
 * two reservations already exist and both must be unwound — in reverse order,
 * by whoever owns the coordination. {@link #refund} exists for the day the saga
 * grows a step AFTER payment (see the stretch goals); compensations, like
 * {@link #capture}'s failure path, are part of the participant's contract from
 * day one.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Payments payments;
    private final MessageBus bus;
    private final ChaosToggles chaos;
    private final TripTopics topics;

    public PaymentService(Payments payments, MessageBus bus, ChaosToggles chaos, TripTopics topics) {
        this.payments = payments;
        this.bus = bus;
        this.chaos = chaos;
        this.topics = topics;
    }

    public void capture(UUID tripId, BigDecimal amount) {
        if (chaos.consumeFailNext("payment")) {
            log.warn("payment [{}]: CHAOS — card declined for {}", tripId, amount);
            bus.publish(topics.events(), new PaymentFailed(tripId, "Card declined for " + amount));
            return;
        }
        payments.insertCaptured(tripId, amount);
        log.info("payment [{}]: captured {}", tripId, amount);
        bus.publish(topics.events(), new PaymentCaptured(tripId, amount));
    }

    public void refund(UUID tripId) {
        int refunded = payments.markRefunded(tripId);
        if (refunded == 0) {
            log.info("payment [{}]: nothing to refund — compensation is an idempotent no-op", tripId);
        } else {
            log.info("payment [{}]: refunded", tripId);
        }
        bus.publish(topics.events(), new PaymentRefunded(tripId));
    }
}
