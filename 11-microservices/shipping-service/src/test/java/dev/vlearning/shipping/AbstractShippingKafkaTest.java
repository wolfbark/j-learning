package dev.vlearning.shipping;

import dev.vlearning.shipping.chaos.ChaosMode;
import dev.vlearning.shipping.chaos.ChaosState;
import dev.vlearning.shipping.shipment.ShipmentStore;
import dev.vlearning.shipping.support.KafkaProbe;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;

/**
 * Container plumbing for the event-driven checkpoints: one Kafka broker for
 * the whole run. Extended only by the step 4/5 checkpoint tests, so the
 * pristine scaffold never pays for the broker.
 */
@SpringBootTest
public abstract class AbstractShippingKafkaTest {

    public static final String ORDERS_PLACED_TOPIC = "orders.placed";
    public static final String SHIPMENTS_ARRANGED_TOPIC = "shipments.arranged";

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected ShipmentStore store;

    @Autowired
    protected ChaosState chaos;

    @BeforeEach
    void cleanSlate() {
        chaos.set(ChaosMode.OK);
        store.clear();
    }

    protected KafkaProbe probeOn(String topic) {
        return new KafkaProbe(KAFKA.getBootstrapServers(), topic);
    }

    protected void produceOrderPlaced(String orderId, String item, int quantity, Map<String, String> headers) {
        KafkaProbe.produce(KAFKA.getBootstrapServers(), ORDERS_PLACED_TOPIC, orderId, """
                {"orderId":"%s","item":"%s","quantity":%d}""".formatted(orderId, item, quantity), headers);
    }
}
