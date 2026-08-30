package dev.vlearning.shipping;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import dev.vlearning.shipping.support.Json;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 4, shipping's side of the bargain: consume OrderPlaced from
 * {@code orders.placed}, arrange the shipment, announce ShipmentArranged on
 * {@code shipments.arranged}. The JSON shapes here mirror order-service's
 * Checkpoint4 exactly — the topic is the contract, not a shared jar.
 *
 * Red until you write the listener.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4ShippingConsumerTest extends AbstractShippingKafkaTest {

    @Test
    void anOrderPlacedEventProducesAShipmentAndAnAnnouncement() {
        var orderId = UUID.randomUUID();

        try (var probe = probeOn(SHIPMENTS_ARRANGED_TOPIC)) {
            produceOrderPlaced(orderId.toString(), "mechanical keyboard", 2, Map.of());

            var record = probe.awaitRecords(1, Duration.ofSeconds(15)).getFirst();
            assertThat(Json.read(record.value(), "$.orderId")).isEqualTo(orderId.toString());
            assertThat(Json.read(record.value(), "$.shipmentId")).startsWith("SHP-");
            assertThat(Json.read(record.value(), "$.status")).isEqualTo("ARRANGED");
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(store.findByOrderId(orderId)).isPresent());
    }

    @Test
    void eachOrderGetsItsOwnShipment() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        try (var probe = probeOn(SHIPMENTS_ARRANGED_TOPIC)) {
            produceOrderPlaced(first.toString(), "keyboard", 1, Map.of());
            produceOrderPlaced(second.toString(), "duck", 4, Map.of());

            var records = probe.awaitRecords(2, Duration.ofSeconds(15));
            assertThat(records)
                    .extracting(record -> Json.read(record.value(), "$.orderId"))
                    .containsExactlyInAnyOrder(first.toString(), second.toString());
        }
    }
}
