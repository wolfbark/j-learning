package vlearning.payments;

public abstract class PaymentResult {

    private final String paymentId;

    protected PaymentResult(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
