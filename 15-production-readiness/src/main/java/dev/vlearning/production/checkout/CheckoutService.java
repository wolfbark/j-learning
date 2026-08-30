package dev.vlearning.production.checkout;

import dev.vlearning.production.gateway.PaymentGateway;
import org.springframework.stereotype.Service;

/**
 * The method under pressure for this entire lesson.
 *
 * <p>It is correct, and it is one bad afternoon at the payment provider away
 * from taking your service down with it. Every guided step adds exactly one
 * mitigation here — in the order that actually works:
 *
 * <ol>
 *   <li>a timeout, so a hung dependency cannot hold your resources hostage</li>
 *   <li>a bounded retry with backoff and jitter, for genuinely transient faults</li>
 *   <li>a concurrency limit, so a slow dependency cannot consume everything</li>
 *   <li>a circuit breaker, so a dead dependency stops being asked</li>
 * </ol>
 *
 * <p>The order is not arbitrary: a retry without a timeout multiplies a hang,
 * and a breaker without a retry trips on faults that would have healed.
 */
@Service
public class CheckoutService {

    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public CheckoutResult checkout(String orderId, long amountCents) {
        var authorization = gateway.authorize(orderId, amountCents);
        return new CheckoutResult(orderId, "CONFIRMED", authorization.authorizationCode(), null);
    }

    public record CheckoutResult(String orderId, String status, String authorizationCode, String traceId) {}
}
