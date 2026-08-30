package dev.vlearning.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment as stored. {@code declineReason} is null unless {@code status} is DECLINED.
 */
public record Payment(
        String id,
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String declineReason,
        String idempotencyKey,
        Instant createdAt) {

    public boolean sameRequestAs(String otherOrderId, BigDecimal otherAmount, String otherCurrency) {
        return orderId.equals(otherOrderId)
                && currency.equals(otherCurrency)
                && amount.compareTo(otherAmount) == 0;
    }
}
