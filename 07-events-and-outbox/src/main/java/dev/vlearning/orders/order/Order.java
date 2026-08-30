package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A placed order. Items are kept in memory for total calculation; only the
 * aggregate row is persisted — the persistence model is deliberately minimal
 * because this lesson lives between the database and the broker, not inside
 * the aggregate.
 */
public record Order(UUID id, String customer, List<OrderItem> items, Instant placedAt) {

    public Order {
        items = List.copyOf(items);
    }

    public static Order place(String customer, List<OrderItem> items) {
        if (customer == null || customer.isBlank()) {
            throw new IllegalArgumentException("customer must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one item");
        }
        return new Order(UUID.randomUUID(), customer, items, Instant.now());
    }

    public BigDecimal total() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
