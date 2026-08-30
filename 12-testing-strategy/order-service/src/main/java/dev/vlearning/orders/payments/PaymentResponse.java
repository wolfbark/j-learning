package dev.vlearning.orders.payments;

import java.math.BigDecimal;

/**
 * The success body this consumer reads. It deliberately does <em>not</em> mirror
 * the provider's record: a consumer declares only the fields it needs, and the
 * contract it publishes says exactly that. Fields the provider adds later are
 * ignored, which is what makes independent deploys possible.
 */
public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String status,
        String createdAt) {
}
