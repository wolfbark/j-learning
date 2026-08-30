package dev.vlearning.reliability.settlement;

import java.time.Duration;

import dev.vlearning.reliability.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SettlementAuditLog {

    private static final Logger log = LoggerFactory.getLogger(SettlementAuditLog.class);

    public void enteringSettle(String orderId) {
        log.atDebug().setMessage("settlement.started").addKeyValue("order_id", orderId).log();
    }

    public void settled(SettlementCommand command, Duration took, boolean success) {
        log.atInfo().setMessage("settlement.completed")
                .addKeyValue("order_id", command.orderId())
                .addKeyValue("user_id", command.userId())
                .addKeyValue("amount_cents", command.amountCents())
                .addKeyValue("duration_ms", took.toMillis())
                .addKeyValue("outcome", success ? "success" : "failure")
                .addKeyValue("correlation_id", CorrelationIdFilter.current())
                .log();
    }
}
