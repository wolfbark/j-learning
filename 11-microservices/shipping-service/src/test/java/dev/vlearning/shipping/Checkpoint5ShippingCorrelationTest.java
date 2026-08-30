package dev.vlearning.shipping;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import dev.vlearning.shipping.support.KafkaProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5: the correlation id that arrived as a Kafka header on OrderPlaced
 * must (a) be visible in this service's logs via MDC and (b) ride on the
 * outgoing ShipmentArranged record — the id survives every hop, or it is
 * useless.
 *
 * Requires the step 4 listener; red until you propagate the header.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5ShippingCorrelationTest extends AbstractShippingKafkaTest {

    @Test
    void theCorrelationIdSurvivesTheHopThroughThisService() {
        var orderId = UUID.randomUUID();

        try (var probe = probeOn(SHIPMENTS_ARRANGED_TOPIC)) {
            produceOrderPlaced(orderId.toString(), "keyboard", 1,
                    Map.of("X-Correlation-Id", "corr-hop-7"));

            var record = probe.awaitRecords(1, Duration.ofSeconds(15)).getFirst();
            assertThat(KafkaProbe.header(record, "X-Correlation-Id"))
                    .as("the reply must carry the same correlation id the request arrived with")
                    .isEqualTo("corr-hop-7");
        }
    }
}
