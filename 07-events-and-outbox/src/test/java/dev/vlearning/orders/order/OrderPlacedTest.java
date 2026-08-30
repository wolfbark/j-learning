package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacedTest {

    private final Order order = Order.place("Grace Hopper",
            List.of(new OrderItem("COBOL-1", 3, new BigDecimal("10.00"))));

    @Test
    void carriesTheOrderState() {
        var event = OrderPlaced.from(order);

        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.customer()).isEqualTo("Grace Hopper");
        assertThat(event.total()).isEqualByComparingTo("30.00");
        assertThat(event.occurredAt()).isEqualTo(order.placedAt());
    }

    @Test
    void everyAnnouncementGetsItsOwnEventId() {
        var first = OrderPlaced.from(order);
        var second = OrderPlaced.from(order);

        assertThat(first.eventId()).isNotNull();
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.orderId()).isEqualTo(second.orderId());
    }
}
