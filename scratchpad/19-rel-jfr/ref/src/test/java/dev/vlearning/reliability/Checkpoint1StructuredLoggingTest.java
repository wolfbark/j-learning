package dev.vlearning.reliability;

import java.time.Duration;

import dev.vlearning.reliability.settlement.SettlementAuditLog;
import dev.vlearning.reliability.settlement.SettlementCommand;
import dev.vlearning.reliability.support.LogCapture;
import dev.vlearning.reliability.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1. A log line is an API for future-you at 03:00 with a support ticket in
 * one hand. This checkpoint asserts the two properties that make it one: stable
 * machine-readable fields, and nothing in there that a court would call personal
 * data.
 */
class Checkpoint1StructuredLoggingTest {

    private static final SettlementCommand COMMAND = new SettlementCommand(
            "ORD-9001", "U-4242", "ada@example.com", "4111111111111111", 19_900L);

    private final SettlementAuditLog audit = new SettlementAuditLog();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("the settlement event is queryable: stable name, stable fields")
    void eventIsQueryable() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-abc123");

        try (var logs = LogCapture.on(SettlementAuditLog.class)) {
            audit.settled(COMMAND, Duration.ofMillis(184), true);

            assertThat(logs.operationalEvents())
                    .as("one settlement, one event")
                    .hasSize(1);
            var event = logs.operationalEvents().getFirst();

            assertThat(event.getMessage())
                    .as("the message is the event's name, not a sentence: you will "
                            + "group by it, and prose does not group")
                    .isEqualTo("settlement.completed");

            var fields = LogCapture.fields(event);
            assertThat(fields)
                    .as("fields carry the values — as key/value pairs or MDC, either is fine")
                    .containsKeys("order_id", "user_id", "outcome", "duration_ms", "correlation_id");
            assertThat(fields.get("order_id")).isEqualTo("ORD-9001");
            assertThat(fields.get("outcome")).isEqualTo("success");
            assertThat(fields.get("duration_ms")).isEqualTo("184");
            assertThat(fields.get("correlation_id")).isEqualTo("corr-abc123");
        }
    }

    @Test
    @DisplayName("no personal data reaches the log, in fields or in prose")
    void noPersonalData() {
        try (var logs = LogCapture.on(SettlementAuditLog.class)) {
            audit.settled(COMMAND, Duration.ofMillis(184), false);

            var event = logs.operationalEvents().getFirst();
            var fields = LogCapture.fields(event);

            assertThat(fields.keySet())
                    .as("logs are copied, shipped, indexed and retained; PII in them is PII "
                            + "in five systems you have not thought about")
                    .doesNotContain("customer_email", "email", "card_number", "card", "pan",
                            "customer_name");
            assertThat(fields.values()).noneMatch(value ->
                    value.contains("@") || value.contains("4111"));
            assertThat(event.getFormattedMessage())
                    .doesNotContain("ada@example.com")
                    .doesNotContain("4111");
            assertThat(fields.get("outcome")).isEqualTo("failure");
        }
    }

    @Test
    @DisplayName("the theatre line is gone from production levels")
    void noTheatre() {
        try (var logs = LogCapture.on(SettlementAuditLog.class)) {
            audit.enteringSettle("ORD-9001");

            assertThat(logs.operationalEvents())
                    .as("\"we got here\" at INFO, once per request, is pure cost: it is never "
                            + "the line that explains an incident. Delete it or demote it to DEBUG.")
                    .isEmpty();
        }
    }
}
