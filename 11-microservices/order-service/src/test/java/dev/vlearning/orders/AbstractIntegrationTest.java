package dev.vlearning.orders;

import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.vlearning.orders.support.HttpResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Shared plumbing: one Postgres, one Kafka, one WireMock playing the
 * shipping-service — started once for the whole run (singleton-container
 * pattern). Tests talk to the application over real HTTP on a random port,
 * because this lesson is about what real sockets do under failure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    public static final String ORDERS_PLACED_TOPIC = "orders.placed";
    public static final String SHIPMENTS_ARRANGED_TOPIC = "shipments.arranged";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");
    // h2c (plaintext HTTP/2) is off: the JDK HttpClient's h2c upgrade trips over
    // WireMock's Jetty and cancels streams; plain HTTP/1.1 is what we want anyway.
    protected static final WireMockServer SHIPPING =
            new WireMockServer(options().dynamicPort().http2PlainDisabled(true));

    static {
        POSTGRES.start();
        KAFKA.start();
        SHIPPING.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.http.serviceclient.shipping.base-url", SHIPPING::baseUrl);
    }

    @LocalServerPort
    int port;

    @Autowired
    protected JdbcClient jdbc;

    protected RestClient http;

    @BeforeEach
    void cleanSlate() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { /* status is asserted, not thrown */ })
                .build();
        jdbc.sql("DELETE FROM orders").update();
        SHIPPING.resetAll();
        stubShippingOk();
    }

    // --- the stubbed shipping-service -------------------------------------

    /** Shipping answers instantly and happily. */
    protected void stubShippingOk() {
        SHIPPING.stubFor(post(urlEqualTo("/shipments")).willReturn(shipmentCreated(0)));
    }

    /** Shipping answers happily — after {@code delayMillis} of dead air. */
    protected void stubShippingSlow(int delayMillis) {
        SHIPPING.stubFor(post(urlEqualTo("/shipments")).willReturn(shipmentCreated(delayMillis)));
    }

    /** Shipping is down: every call gets a 503. */
    protected void stubShippingDown() {
        SHIPPING.stubFor(post(urlEqualTo("/shipments"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"shipping is down\"}")
                        .withHeader("Content-Type", "application/json")));
    }

    protected static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder shipmentCreated(int delayMillis) {
        return aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"shipmentId\":\"SHP-TEST-1\",\"orderId\":null,\"status\":\"ARRANGED\"}")
                .withFixedDelay(delayMillis);
    }

    // --- talking to the order-service over real HTTP -----------------------

    protected HttpResult placeOrder(String customerId, String item, int quantity) {
        return placeOrder(customerId, item, quantity, null);
    }

    protected HttpResult placeOrder(String customerId, String item, int quantity, String correlationId) {
        var json = """
                {"customerId":"%s","item":"%s","quantity":%d}""".formatted(customerId, item, quantity);
        long start = System.nanoTime();
        var response = http.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (correlationId != null) {
                        headers.add("X-Correlation-Id", correlationId);
                    }
                })
                .body(json)
                .retrieve()
                .toEntity(String.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        return new HttpResult(response.getStatusCode().value(),
                response.getBody(), response.getHeaders(), elapsedMillis);
    }

    protected HttpResult getOrder(String orderId) {
        long start = System.nanoTime();
        var response = http.get().uri("/orders/{id}", orderId)
                .retrieve()
                .toEntity(String.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        return new HttpResult(response.getStatusCode().value(),
                response.getBody(), response.getHeaders(), elapsedMillis);
    }

    protected static String randomOrderId() {
        return UUID.randomUUID().toString();
    }
}
