package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6. Resilience without observability is a guess: you cannot tune a retry
 * budget or a breaker threshold you cannot see. This checkpoint asks for the
 * minimum that makes the previous five steps operable — a timed observation for
 * the business operation, and a trace id you can carry into a log query.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint6ObservabilityTest extends AbstractProductionTest {

    @Test
    @DisplayName("checkout is a first-class observation, with a trace id in the response")
    void checkoutIsObserved() throws Exception {
        gatewayRespondsOk();

        var response = checkout("order-observed");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("the trace id belongs in the response: it is how a support ticket "
                        + "becomes a trace lookup")
                .containsPattern("\"traceId\":\"[0-9a-f]{16,}\"");

        var timer = meters.find("checkout").timer();
        assertThat(timer)
                .as("an Observation named 'checkout' should produce a timer")
                .isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the circuit breaker's state is on the metrics endpoint")
    void breakerStateIsVisible() {
        assertThat(meters.find("resilience4j.circuitbreaker.state").gauges())
                .as("breaker state must be dashboard-visible — 'open' is an incident")
                .isNotEmpty();
    }
}
