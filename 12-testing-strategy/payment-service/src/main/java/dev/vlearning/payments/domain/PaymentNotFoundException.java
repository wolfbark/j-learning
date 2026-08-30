package dev.vlearning.payments.domain;

/** No such payment id: HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String id) {
        super("no payment with id " + id);
    }
}
