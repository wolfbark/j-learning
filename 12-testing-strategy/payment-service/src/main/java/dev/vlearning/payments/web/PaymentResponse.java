package dev.vlearning.payments.web;

import java.math.BigDecimal;

import dev.vlearning.payments.domain.Payment;

/**
 * The success body of both {@code POST /payments} and {@code GET /payments/{id}}.
 *
 * <p>{@code createdAt} is a String holding an ISO-8601 instant rather than an
 * {@code Instant}: the wire format of a timestamp is a contract decision, and
 * spelling it out here means no Jackson configuration change can quietly alter it.
 */
public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String status,
        String createdAt) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                payment.status().name(),
                payment.createdAt().toString());
    }
}
