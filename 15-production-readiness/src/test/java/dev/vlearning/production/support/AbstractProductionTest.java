package dev.vlearning.production.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.vlearning.production.gateway.GatewayMeter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A real HTTP dependency (WireMock) behind a real HTTP client, because timeouts,
 * retries and breakers are all about socket behaviour that in-process fakes
 * cannot reproduce.
 */
public abstract class AbstractProductionTest {

    protected static final WireMockServer GATEWAY =
            new WireMockServer(options().dynamicPort().http2PlainDisabled(true));

    static {
        GATEWAY.start();
    }

    @DynamicPropertySource
    static void gatewayUrl(DynamicPropertyRegistry registry) {
        registry.add("gateway.base-url", () -> "http://localhost:" + GATEWAY.port());
    }

    @AfterAll
    static void resetStubs() {
        GATEWAY.resetAll();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected GatewayMeter meter;

    @Autowired
    protected MeterRegistry meters;

    /**
     * A circuit breaker is deliberately stateful, and Spring caches the context
     * across test classes — so a breaker tripped by one test would fail every
     * test that follows it with zero downstream calls. Resetting it here is not
     * ceremony; it is the difference between a suite that tests your code and a
     * suite that tests the residue of the previous test.
     */
    @Autowired
    protected CircuitBreaker breaker;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @BeforeEach
    void reset() {
        GATEWAY.resetAll();
        meter.reset();
        breaker.reset();
    }

    protected HttpResponse<String> checkout(String orderId) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/checkout"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"orderId\":\"" + orderId + "\",\"amountCents\":19900}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Fire {@code count} checkouts at once; returns the status codes. */
    protected List<Integer> checkoutConcurrently(int count) {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, count)
                    .mapToObj(i -> pool.submit(() -> checkout("order-" + i).statusCode()))
                    .toList();
            return futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return -1;
                }
            }).toList();
        }
    }

    private static final String APPROVED =
            "{\"authorizationCode\":\"AUTH-1\",\"status\":\"APPROVED\"}";

    /** The gateway behaves. */
    protected static void gatewayRespondsOk() {
        GATEWAY.stubFor(WireMock.post("/authorize").willReturn(WireMock.okJson(APPROVED)));
    }

    /** The gateway answers, eventually — the case a timeout is for. */
    protected static void gatewayIsSlow(int delayMillis) {
        GATEWAY.stubFor(WireMock.post("/authorize")
                .willReturn(WireMock.okJson(APPROVED).withFixedDelay(delayMillis)));
    }

    /** The gateway is broken, consistently — the case a breaker is for. */
    protected static void gatewayIsDown() {
        GATEWAY.stubFor(WireMock.post("/authorize").willReturn(WireMock.serviceUnavailable()));
    }

    /**
     * Fails {@code failures} times, then works — the transient fault a retry is
     * actually for, expressed with WireMock scenario states.
     */
    protected static void gatewayFailsThenRecovers(int failures) {
        String scenario = "flaky";
        for (int i = 0; i < failures; i++) {
            GATEWAY.stubFor(WireMock.post("/authorize")
                    .inScenario(scenario)
                    .whenScenarioStateIs(i == 0 ? com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
                            : "attempt-" + i)
                    .willReturn(WireMock.serviceUnavailable())
                    .willSetStateTo("attempt-" + (i + 1)));
        }
        GATEWAY.stubFor(WireMock.post("/authorize")
                .inScenario(scenario)
                .whenScenarioStateIs("attempt-" + failures)
                .willReturn(WireMock.okJson(APPROVED)));
    }
}
