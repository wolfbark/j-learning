package dev.vlearning.orders.payments;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the payment-service. In production Boot builds the
 * proxy from {@code spring.http.serviceclient.payments.*}; in the Pact consumer
 * test the same interface is proxied onto Pact's mock server. Same code path,
 * same serializer, same headers — that is what makes the contract trustworthy.
 */
@HttpExchange("/payments")
public interface PaymentClient {

    @PostExchange
    PaymentResponse authorize(@RequestHeader("Idempotency-Key") String idempotencyKey,
                              @RequestBody AuthorizePaymentRequest request);

    @GetExchange("/{paymentId}")
    PaymentResponse get(@PathVariable String paymentId);
}
