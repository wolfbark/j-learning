package dev.vlearning.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.vlearning.orders.chaos.ChaosMonkey;
import dev.vlearning.orders.order.OrderItem;
import dev.vlearning.orders.order.OrderPlaced;
import dev.vlearning.orders.support.KafkaProbe;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared plumbing for every container-backed test: one Postgres, one Kafka,
 * one Spring context for the whole test run (singleton-container pattern).
 * Each test starts from clean tables and a calm chaos monkey.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected ChaosMonkey chaos;

    @BeforeEach
    void cleanSlate() {
        chaos.reset();
        jdbc.sql("DELETE FROM fulfillment_tasks").update();
        jdbc.sql("DELETE FROM orders").update();
        jdbc.sql("DELETE FROM event_publication").update();
    }

    protected KafkaProbe newProbe() {
        return new KafkaProbe(KAFKA.getBootstrapServers(), OrderPlaced.TOPIC);
    }

    protected long orderCount() {
        return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
    }

    protected boolean orderExists(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE id = :id")
                .param("id", orderId).query(Long.class).single() > 0;
    }

    protected long fulfillmentTaskCount(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM fulfillment_tasks WHERE order_id = :id")
                .param("id", orderId).query(Long.class).single();
    }

    /** Rows in the outbox that were committed but not yet delivered to the broker. */
    protected long incompletePublicationCount() {
        return jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL")
                .query(Long.class).single();
    }

    protected static List<OrderItem> someItems() {
        return List.of(new OrderItem("KB-42", 2, new BigDecimal("59.50")),
                new OrderItem("MOUSE-7", 1, new BigDecimal("25.00")));
    }
}
