package dev.vlearning.payments.web;

import java.math.BigDecimal;

/**
 * The request body of {@code POST /payments}. The idempotency key travels in the
 * {@code Idempotency-Key} header, not here — headers are part of the contract too.
 */
public record AuthorizePaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency,
        String cardToken) {
}
