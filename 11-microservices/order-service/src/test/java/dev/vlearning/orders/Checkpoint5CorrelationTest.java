package dev.vlearning.orders;

import java.time.Duration;

import dev.vlearning.orders.support.KafkaProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5: one id follows one customer action across the process boundary.
 * The correlation id that arrived on the HTTP request must ride along as a
 * header on the OrderPlaced record — so shipping-service (and your grep) can
 * pick it up on the far side.
 *
 * Requires step 4 to be done: events are the transport being traced.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5CorrelationTest extends AbstractIntegrationTest {

    @Test
    void theCorrelationIdRidesOnTheEvent() {
        try (var probe = new KafkaProbe(KAFKA.getBootstrapServers(), ORDERS_PLACED_TOPIC)) {
            placeOrder("ada", "keyboard", 1, "corr-e2e-1");

            var record = probe.awaitRecords(1, Duration.ofSeconds(10)).getFirst();
            assertThat(KafkaProbe.header(record, "X-Correlation-Id")).isEqualTo("corr-e2e-1");
        }
    }

    @Test
    void aCorrelationIdIsMintedWhenTheCallerSendsNone() {
        try (var probe = new KafkaProbe(KAFKA.getBootstrapServers(), ORDERS_PLACED_TOPIC)) {
            placeOrder("grace", "duck", 1);

            var record = probe.awaitRecords(1, Duration.ofSeconds(10)).getFirst();
            assertThat(KafkaProbe.header(record, "X-Correlation-Id")).isNotBlank();
        }
    }
}
