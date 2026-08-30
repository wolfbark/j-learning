package dev.vlearning.orders.order;

import java.time.Instant;
import java.util.UUID;

public record Order(
        UUID id,
        String customerId,
        String item,
        int quantity,
        OrderStatus status,
        String shipmentId,
        Instant placedAt) {

    public static Order placed(String customerId, String item, int quantity) {
        return new Order(UUID.randomUUID(), customerId, item, quantity, OrderStatus.PLACED, null, Instant.now());
    }
}
