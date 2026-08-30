package dev.vlearning.orders.payments;

/** The failure body this consumer reads — the declined shape is the interesting one. */
public record PaymentError(
        String error,
        String message,
        String reason,
        String paymentId) {
}
