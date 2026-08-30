package dev.vlearning.orders.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import dev.vlearning.orders.payments.PaymentOutcome;
import dev.vlearning.orders.payments.PaymentPort;
import dev.vlearning.orders.payments.PaymentResponse;

/**
 * A hand-written stub — no mocking framework. It records what it was asked, which
 * is all the given unit test needs.
 *
 * <p>What it cannot tell you: whether the real payment-service would have accepted
 * that request, or whether its answer would deserialize. That gap is precisely
 * what the Pact test in step 3 closes.
 */
public class StubPaymentPort implements PaymentPort {

    public record Call(String idempotencyKey, String orderId, BigDecimal amount, String currency, String cardToken) {
    }

    public final List<Call> calls = new ArrayList<>();

    private PaymentOutcome nextOutcome = PaymentOutcome.approved("pay_stub", "AUTHORIZED");

    public StubPaymentPort willDecline(String reason) {
        this.nextOutcome = PaymentOutcome.declined("pay_stub_declined", reason);
        return this;
    }

    @Override
    public PaymentOutcome authorize(String idempotencyKey, String orderId, BigDecimal amount,
                                    String currency, String cardToken) {
        calls.add(new Call(idempotencyKey, orderId, amount, currency, cardToken));
        return nextOutcome;
    }

    @Override
    public PaymentResponse lookup(String paymentId) {
        return new PaymentResponse(paymentId, "order-stub", BigDecimal.ONE, "USD",
                "AUTHORIZED", "2026-08-25T10:15:30Z");
    }
}
