package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The fact that an order was placed. Immutable, past tense, addressed to
 * nobody in particular — an event, not a command.
 *
 * <p>The {@code eventId} identifies this <em>announcement</em>, not the order:
 * a redelivery carries the same eventId, a second order for the same customer
 * does not. Step 5 turns that property into consumer-side deduplication.
 */
public record OrderPlaced(UUID eventId, UUID orderId, String customer, BigDecimal total, Instant occurredAt) {

    public static final String TOPIC = "orders.OrderPlaced";

    public static OrderPlaced from(Order order) {
        return new OrderPlaced(UUID.randomUUID(), order.id(), order.customer(), order.total(), order.placedAt());
    }
}
