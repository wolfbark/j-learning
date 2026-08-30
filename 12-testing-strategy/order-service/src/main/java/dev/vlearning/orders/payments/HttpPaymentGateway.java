package dev.vlearning.orders.payments;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Translates the payment-service's HTTP vocabulary into this service's domain
 * vocabulary. Every assumption in here — 402 means declined, the declined body
 * carries {@code reason}, {@code status} is a string — is a claim about someone
 * else's code, and therefore belongs in a contract test rather than in a hope.
 */
@Component
public class HttpPaymentGateway implements PaymentPort {

    private final PaymentClient client;

    public HttpPaymentGateway(PaymentClient client) {
        this.client = client;
    }

    @Override
    public PaymentOutcome authorize(String idempotencyKey, String orderId, BigDecimal amount,
                                    String currency, String cardToken) {
        try {
            var response = client.authorize(idempotencyKey,
                    new AuthorizePaymentRequest(orderId, amount, currency, cardToken));
            return PaymentOutcome.approved(response.paymentId(), response.status());
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.PAYMENT_REQUIRED) {
                throw e;
            }
            var error = e.getResponseBodyAs(PaymentError.class);
            return error == null
                    ? PaymentOutcome.declined(null, "UNKNOWN")
                    : PaymentOutcome.declined(error.paymentId(), error.reason());
        }
    }

    @Override
    public PaymentResponse lookup(String paymentId) {
        return client.get(paymentId);
    }
}
