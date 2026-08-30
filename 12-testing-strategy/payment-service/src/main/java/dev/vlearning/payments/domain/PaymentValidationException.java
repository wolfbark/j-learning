package dev.vlearning.payments.domain;

/** The request could not be understood: HTTP 400. */
public class PaymentValidationException extends RuntimeException {

    public PaymentValidationException(String message) {
        super(message);
    }
}
