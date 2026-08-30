package dev.vlearning.reliability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import dev.vlearning.reliability.settlement.RequestMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Always on. Whatever steps 1–7 do to logging, metrics, buckets and pools, the
 * service still has to settle a settlement and still has to hand back a
 * correlation id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettlementContractTest {

    @LocalServerPort
    int port;

    @Autowired
    MeterRegistry meters;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Test
    @DisplayName("a settlement succeeds and echoes the caller's correlation id")
    void settles() throws Exception {
        var response = post("ORD-1001", "U-1001", "given-correlation-id");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"orderId\":\"ORD-1001\"", "\"status\":\"SETTLED\"");
        assertThat(response.headers().firstValue("X-Correlation-Id"))
                .contains("given-correlation-id");
        assertThat(response.body()).contains("given-correlation-id");
    }

    @Test
    @DisplayName("the availability SLI's counters exist and move")
    void countsOutcomes() throws Exception {
        double before = successes();

        post("ORD-1002", "U-1002", null);

        assertThat(successes()).isGreaterThan(before);
    }

    private double successes() {
        var counter = meters.find(RequestMetrics.OUTCOMES).tag("outcome", "success").counter();
        return counter == null ? 0 : counter.count();
    }

    private HttpResponse<String> post(String orderId, String userId, String correlationId)
            throws Exception {
        var body = """
                {"orderId":"%s","userId":"%s","customerEmail":"ada@example.com",
                 "cardNumber":"4111111111111111","amountCents":19900}
                """.formatted(orderId, userId);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/settlements"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
