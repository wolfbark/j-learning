package dev.vlearning.reliability.settlement;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logging as most services still do it: prose, interpolated values, and a
 * hopeful "we got here" line. Readable by a human staring at a terminal;
 * useless to a log query, and it retains data it has no business retaining.
 *
 * <p>This is step 1's refactoring subject.
 */
@Component
public class SettlementAuditLog {

    private static final Logger log = LoggerFactory.getLogger(SettlementAuditLog.class);

    /** Logged on the way in, on every single request, forever. */
    public void enteringSettle(String orderId) {
        log.info("SettlementService.settle() called for order {}", orderId);
    }

    /** The one line you would actually want at 03:00 — buried in prose, with PII. */
    public void settled(SettlementCommand command, Duration took, boolean success) {
        log.info("Settled order {} for customer {} (card {}) amount {} cents in {} ms - {}",
                command.orderId(),
                command.customerEmail(),
                command.cardNumber(),
                command.amountCents(),
                took.toMillis(),
                success ? "OK" : "FAILED");
    }
}
