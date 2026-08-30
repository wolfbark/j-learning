package dev.vlearning.orders.payments;

import java.math.BigDecimal;

/**
 * The order side depends on this, not on HTTP — which is why the given unit test
 * can substitute a five-line fake and why the Pact test can substitute a mock
 * HTTP server without either of them knowing about the other.
 */
public interface PaymentPort {

    PaymentOutcome authorize(String idempotencyKey, String orderId, BigDecimal amount,
                             String currency, String cardToken);

    PaymentResponse lookup(String paymentId);
}
