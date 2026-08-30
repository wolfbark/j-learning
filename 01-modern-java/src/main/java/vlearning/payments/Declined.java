package vlearning.payments;

public class Declined extends PaymentResult {

    private final String reason;

    public Declined(String paymentId, String reason) {
        super(paymentId);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
