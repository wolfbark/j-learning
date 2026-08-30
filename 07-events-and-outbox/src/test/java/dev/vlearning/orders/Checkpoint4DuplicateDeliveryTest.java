package dev.vlearning.orders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.vlearning.orders.order.OrderPlaced;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Step 4: at-least-once means duplicates. The outbox guarantees the event
 * gets OUT at least once — a relay retry after a timeout, a consumer crash
 * after processing but before committing offsets, a rebalance: all of them
 * deliver the same event again. Same eventId, same payload, delivered twice.
 *
 * This test PASSES against the naive consumer: it pins the double-count bug.
 * After step 5 makes the consumer idempotent, it MUST fail — re-disable it
 * then, like Checkpoint 2.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4DuplicateDeliveryTest extends AbstractIntegrationTest {

    @Autowired
    KafkaTemplate<String, byte[]> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void redeliveredEvent_getsFulfilledTwice() {
        var event = new OrderPlaced(UUID.randomUUID(), UUID.randomUUID(),
                "Deja Vu Inc.", new BigDecimal("42.00"), Instant.now());
        var payload = json.writeValueAsBytes(event);

        // the same announcement, delivered twice — this is a REdelivery
        // (identical eventId), not a second order
        kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), payload);
        kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), payload);

        // one order placed, two fulfillment tasks: somebody ships twice
        await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertThat(fulfillmentTaskCount(event.orderId())).isEqualTo(2));
    }
}
