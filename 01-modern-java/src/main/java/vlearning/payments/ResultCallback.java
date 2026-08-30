package vlearning.payments;

public interface ResultCallback {

    void onResult(PaymentRequest request, PaymentResult result);
}
