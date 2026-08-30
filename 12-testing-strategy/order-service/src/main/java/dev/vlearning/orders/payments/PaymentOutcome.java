package dev.vlearning.orders.payments;

/**
 * What the order side actually needs to know. "Declined" is a business answer,
 * not an exception: it is the provider working correctly.
 */
public record PaymentOutcome(boolean approved, String paymentId, String status, String declineReason) {

    public static PaymentOutcome approved(String paymentId, String status) {
        return new PaymentOutcome(true, paymentId, status, null);
    }

    public static PaymentOutcome declined(String paymentId, String reason) {
        return new PaymentOutcome(false, paymentId, "DECLINED", reason);
    }
}
