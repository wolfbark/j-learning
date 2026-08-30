package dev.vlearning.payments.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Validation, the authorization decision, and idempotency — the three things a
 * consumer actually depends on, and therefore the three things worth putting in
 * a contract.
 */
@Service
public class PaymentService {

    /** Above this, the (imaginary) acquirer says no. Deterministic on purpose: contracts hate randomness. */
    private static final Map<String, BigDecimal> LIMITS = Map.of(
            "USD", new BigDecimal("1000.00"),
            "EUR", new BigDecimal("1000.00"),
            "JPY", new BigDecimal("150000"));

    private static final String DECLINE_TOKEN_PREFIX = "tok_decline";

    private final PaymentRepository payments;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    public Payment get(String id) {
        return payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    /**
     * Authorizes, or returns the payment a previous identical request already created.
     *
     * @return the payment, and whether this call created it (the controller answers
     *         201 for a fresh authorization and 200 for a replay)
     */
    public Authorization authorize(String idempotencyKey, String orderId, BigDecimal amount,
                                   String currency, String cardToken) {
        validate(idempotencyKey, orderId, amount, currency, cardToken);
        BigDecimal normalized = Currencies.normalize(amount, currency);

        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new Authorization(replayOrConflict(existing.get(), idempotencyKey, orderId, normalized, currency), false);
        }

        var decision = decide(normalized, currency, cardToken);
        var payment = new Payment(
                "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                orderId, normalized, currency,
                decision.status(), decision.reason(),
                idempotencyKey, clock.instant());

        if (payments.insertIfAbsent(payment)) {
            return new Authorization(payment, true);
        }
        // Lost the race: the winner's row is authoritative.
        var winner = payments.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("idempotency key vanished: " + idempotencyKey));
        return new Authorization(replayOrConflict(winner, idempotencyKey, orderId, normalized, currency), false);
    }

    private Payment replayOrConflict(Payment stored, String idempotencyKey, String orderId,
                                     BigDecimal amount, String currency) {
        if (!stored.sameRequestAs(orderId, amount, currency)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        return stored;
    }

    private void validate(String idempotencyKey, String orderId, BigDecimal amount,
                          String currency, String cardToken) {
        if (isBlank(idempotencyKey)) {
            throw new PaymentValidationException("Idempotency-Key header is required");
        }
        if (isBlank(orderId)) {
            throw new PaymentValidationException("orderId is required");
        }
        if (isBlank(cardToken)) {
            throw new PaymentValidationException("cardToken is required");
        }
        if (!Currencies.isSupported(currency)) {
            throw new PaymentValidationException("unsupported currency: " + currency);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentValidationException("amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > Currencies.minorUnits(currency)) {
            throw new PaymentValidationException(
                    "amount has more decimals than " + currency + " allows");
        }
    }

    private Decision decide(BigDecimal amount, String currency, String cardToken) {
        if (cardToken.startsWith(DECLINE_TOKEN_PREFIX)) {
            return new Decision(PaymentStatus.DECLINED, "CARD_DECLINED");
        }
        if (amount.compareTo(LIMITS.get(currency)) > 0) {
            return new Decision(PaymentStatus.DECLINED, "LIMIT_EXCEEDED");
        }
        return new Decision(PaymentStatus.AUTHORIZED, null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record Authorization(Payment payment, boolean created) {
    }

    private record Decision(PaymentStatus status, String reason) {
    }
}
