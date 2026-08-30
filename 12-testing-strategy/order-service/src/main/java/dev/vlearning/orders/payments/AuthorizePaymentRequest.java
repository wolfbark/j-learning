package dev.vlearning.orders.payments;

import java.math.BigDecimal;

/** The body this consumer sends to {@code POST /payments}. */
public record AuthorizePaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency,
        String cardToken) {
}
