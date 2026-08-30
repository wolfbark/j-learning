package dev.vlearning.orders;

import java.time.Duration;
import java.util.Map;

import dev.vlearning.orders.support.Json;
import dev.vlearning.orders.support.KafkaProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 4: the synchronous call is gone. Placing an order publishes an
 * OrderPlaced event to {@code orders.placed}; a ShipmentArranged event on
 * {@code shipments.arranged} later confirms the order. These tests define the
 * event contract — the JSON shapes below are what shipping-service's own
 * checkpoint expects, so the two services agree without sharing a line of code.
 *
 * Red until you make the switch. Note that the shipping stub is DOWN in the
 * first test: the whole point is that nobody calls it anymore.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4AsyncOrderTest extends AbstractIntegrationTest {

    @Test
    void placingAnOrderPublishesAnEventInsteadOfCalling() {
        stubShippingDown(); // shipping is dead — and it must not matter

        try (var probe = new KafkaProbe(KAFKA.getBootstrapServers(), ORDERS_PLACED_TOPIC)) {
            var response = placeOrder("ada", "mechanical keyboard", 2);

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.elapsedMillis()).isLessThan(2000);
            assertThat(response.json("$.status")).isEqualTo("SHIPPING_PENDING");

            var record = probe.awaitRecords(1, Duration.ofSeconds(10)).getFirst();
            assertThat(Json.read(record.value(), "$.orderId")).isEqualTo(response.json("$.orderId"));
            assertThat(Json.read(record.value(), "$.item")).isEqualTo("mechanical keyboard");
            assertThat(Json.read(record.value(), "$.quantity")).isEqualTo("2");

            SHIPPING.verify(0, postRequestedFor(urlEqualTo("/shipments")));
        }
    }

    @Test
    void aShipmentArrangedEventConfirmsTheOrder() {
        var response = placeOrder("grace", "rubber duck", 1);
        var orderId = response.json("$.orderId");

        KafkaProbe.produce(KAFKA.getBootstrapServers(), SHIPMENTS_ARRANGED_TOPIC, orderId, """
                {"orderId":"%s","shipmentId":"SHP-EVENT-7","status":"ARRANGED"}""".formatted(orderId),
                Map.of());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var fetched = getOrder(orderId);
            assertThat(fetched.json("$.status")).isEqualTo("CONFIRMED");
            assertThat(fetched.json("$.shipmentId")).isEqualTo("SHP-EVENT-7");
        });
    }
}
