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
 * Step 5: the idempotent consumer. Delivery stays at-least-once — nothing on
 * the producer side changed. The consumer simply remembers which eventIds it
 * has already processed (processed_messages table, same local transaction as
 * the fulfillment write) and treats a redelivery as a no-op.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5IdempotentConsumerTest extends AbstractIntegrationTest {

    @Autowired
    KafkaTemplate<String, byte[]> kafka;

    @Autowired
    JsonMapper json;

    @Test
    void redeliveredEvent_isProcessedExactlyOnce() {
        var event = new OrderPlaced(UUID.randomUUID(), UUID.randomUUID(),
                "Deja Vu Inc.", new BigDecimal("42.00"), Instant.now());
        var payload = json.writeValueAsBytes(event);

        kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), payload);
        kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), payload);

        // exactly one fulfillment task — and it STAYS one while the duplicate
        // is delivered and discarded (the condition must hold for 3 straight seconds)
        await().atMost(Duration.ofSeconds(20)).during(Duration.ofSeconds(3))
                .until(() -> fulfillmentTaskCount(event.orderId()) == 1);

        // the consumer remembered the announcement, not the order
        assertThat(processedCount(event.eventId())).isEqualTo(1);
    }

    private long processedCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_messages WHERE event_id = :id")
                .param("id", eventId).query(Long.class).single();
    }
}
