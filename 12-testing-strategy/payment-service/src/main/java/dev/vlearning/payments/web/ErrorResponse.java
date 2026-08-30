package dev.vlearning.payments.web;

/**
 * The failure body for every non-2xx answer. One shape for all of them, because
 * a consumer that must parse three different error shapes will parse none of them
 * correctly. {@code reason} and {@code paymentId} are null where they don't apply.
 */
public record ErrorResponse(
        String error,
        String message,
        String reason,
        String paymentId) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null, null);
    }

    public static ErrorResponse declined(String paymentId, String reason) {
        return new ErrorResponse("payment_declined", "the payment was declined", reason, paymentId);
    }
}
