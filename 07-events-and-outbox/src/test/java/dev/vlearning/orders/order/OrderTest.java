package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderTest {

    @Test
    void computesTheTotalAcrossLines() {
        var order = Order.place("Ada Lovelace", List.of(
                new OrderItem("KB-42", 2, new BigDecimal("59.50")),
                new OrderItem("MOUSE-7", 1, new BigDecimal("25.00"))));

        assertThat(order.total()).isEqualByComparingTo("144.00");
    }

    @Test
    void assignsAnIdAndTimestamp() {
        var order = Order.place("Ada Lovelace", List.of(new OrderItem("KB-42", 1, BigDecimal.ONE)));

        assertThat(order.id()).isNotNull();
        assertThat(order.placedAt()).isNotNull();
    }

    @Test
    void rejectsBlankCustomer() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Order.place("  ", List.of(new OrderItem("KB-42", 1, BigDecimal.ONE))))
                .withMessageContaining("customer");
    }

    @Test
    void rejectsEmptyItemList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Order.place("Ada Lovelace", List.of()))
                .withMessageContaining("item");
    }

    @Test
    void itemsRejectNonPositiveQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderItem("KB-42", 0, BigDecimal.ONE))
                .withMessageContaining("quantity");
    }

    @Test
    void itemsRejectNegativePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderItem("KB-42", 1, new BigDecimal("-1")))
                .withMessageContaining("unitPrice");
    }
}
