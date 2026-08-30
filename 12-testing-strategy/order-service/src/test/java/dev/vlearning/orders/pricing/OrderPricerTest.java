package dev.vlearning.orders.pricing;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIVEN unit tests for the pricer. They pass. They are also the tests a hurried
 * developer (or a code-generating assistant asked for "unit tests for
 * OrderPricer") actually writes: one example per branch, all of them comfortably
 * inside the branch, none of them at an edge.
 *
 * <p>Step 1 asks what confidence is missing here. Step 7 makes PIT answer.
 */
class OrderPricerTest {

    private final OrderPricer pricer = new OrderPricer();

    private static List<OrderLine> line(int quantity, String unitPrice) {
        return List.of(new OrderLine("SKU-1", quantity, new BigDecimal(unitPrice)));
    }

    @Test
    void aSmallOrderGetsNoDiscountAndPaysShipping() {
        var quote = pricer.quote(line(1, "50.00"), "USD", null, false);

        assertThat(quote.subtotal()).isEqualByComparingTo("50.00");
        assertThat(quote.discount()).isEqualByComparingTo("0.00");
        assertThat(quote.shipping()).isEqualByComparingTo("4.99");
        assertThat(quote.total()).isEqualByComparingTo("54.99");
        assertThat(quote.appliedRule()).isEqualTo("NO_DISCOUNT");
    }

    @Test
    void theFirstTierGivesFivePercentAndFreeShipping() {
        var quote = pricer.quote(line(2, "125.00"), "USD", null, false);

        assertThat(quote.subtotal()).isEqualByComparingTo("250.00");
        assertThat(quote.discount()).isEqualByComparingTo("12.50");
        assertThat(quote.shipping()).isEqualByComparingTo("0.00");
        assertThat(quote.total()).isEqualByComparingTo("237.50");
        assertThat(quote.appliedRule()).isEqualTo("TIER_5");
    }

    @Test
    void theSecondTierGivesTenPercent() {
        var quote = pricer.quote(line(3, "200.00"), "USD", null, false);

        assertThat(quote.discount()).isEqualByComparingTo("60.00");
        assertThat(quote.total()).isEqualByComparingTo("540.00");
        assertThat(quote.appliedRule()).isEqualTo("TIER_10");
    }

    @Test
    void yenIsPricedWithoutDecimals() {
        var quote = pricer.quote(line(2, "2500"), "JPY", null, false);

        assertThat(quote.subtotal()).isEqualByComparingTo("5000");
        assertThat(quote.shipping()).isEqualByComparingTo("500");
        assertThat(quote.total()).isEqualByComparingTo("5500");
        assertThat(quote.total().scale()).isZero();
    }

    @Test
    void anOrderWithNoLinesIsRejected() {
        assertThatThrownBy(() -> pricer.quote(List.of(), "USD", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }
}
