package vlearning.payments;

public class Retryable extends PaymentResult {

    private final String reason;
    private final int retryAfterSeconds;

    public Retryable(String paymentId, String reason, int retryAfterSeconds) {
        super(paymentId);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getReason() {
        return reason;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
