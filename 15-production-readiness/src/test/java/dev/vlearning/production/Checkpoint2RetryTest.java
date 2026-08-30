package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2. One HTTP 503 followed by a healthy response is the fault retries were
 * invented for: transient, uncorrelated, and gone by the time you ask again.
 *
 * <p>Spring Framework 7 has this in core — {@code @Retryable} with
 * {@code maxRetries}, {@code delay}, {@code multiplier} and {@code jitter}. The
 * jitter matters more than it looks: without it, every instance that failed
 * together retries together, and you have built a synchronised stampede.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint2RetryTest extends AbstractProductionTest {

    @Test
    @DisplayName("a single transient failure is invisible to the caller")
    void transientFailureIsRetried() throws Exception {
        gatewayFailsThenRecovers(1);

        var response = checkout("order-retry");

        System.out.printf("%n  gateway calls: %d%n", meter.calls());
        assertThat(response.statusCode())
                .as("the second attempt succeeded, so the caller should see success")
                .isEqualTo(200);
        assertThat(response.body()).contains("CONFIRMED");
        assertThat(meter.calls())
                .as("exactly one retry: the first call failed, the second worked")
                .isEqualTo(2);
    }
}
