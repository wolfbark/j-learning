package dev.vlearning.payments;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres for the whole test run (singleton-container pattern: a static
 * container started in a static initializer, never stopped — Ryuk reaps it when
 * the JVM exits). Boot runs {@code schema.sql} against it at context startup, so
 * these tests exercise the real DDL, the real types, and the real unique index.
 *
 * <p>Note what is <em>not</em> here: no mocked repository, no H2. The whole point
 * of the honeycomb shape is that this class is cheap enough to be the default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    protected RestClient http;

    @BeforeEach
    void cleanSlate() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // Status is asserted, not thrown: these tests care about the wire contract.
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
        jdbc.sql("DELETE FROM payments").update();
    }

    // --- talking to the payment-service over real HTTP ---------------------

    protected ResponseEntity<String> authorize(String idempotencyKey, String orderId,
                                               String amount, String currency, String cardToken) {
        var json = """
                {"orderId":"%s","amount":%s,"currency":"%s","cardToken":"%s"}"""
                .formatted(orderId, amount, currency, cardToken);
        return http.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (idempotencyKey != null) {
                        headers.add("Idempotency-Key", idempotencyKey);
                    }
                })
                .body(json)
                .retrieve()
                .toEntity(String.class);
    }

    protected ResponseEntity<String> getPayment(String id) {
        return http.get().uri("/payments/{id}", id).retrieve().toEntity(String.class);
    }

    protected long countPayments() {
        return jdbc.sql("SELECT count(*) FROM payments").query(Long.class).single();
    }

    protected BigDecimal storedAmount(String id) {
        return jdbc.sql("SELECT amount FROM payments WHERE id = :id").param("id", id)
                .query(BigDecimal.class).single();
    }
}
