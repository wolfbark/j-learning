package dev.vlearning.orders.pricing;

import java.math.BigDecimal;

/**
 * @param appliedRule which discount rule won — exposed because "why is this the
 *                    price?" is the question support actually asks
 */
public record PriceQuote(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        String currency,
        String appliedRule) {
}
