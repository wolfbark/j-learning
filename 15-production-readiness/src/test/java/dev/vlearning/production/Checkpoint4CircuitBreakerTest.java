package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4. Retries help a dependency that stumbles. A dependency that is *down*
 * needs the opposite: stop asking. A circuit breaker converts a slow, expensive,
 * repeated failure into a fast, cheap, local one — and gives the dependency room
 * to recover instead of a thundering herd on its knees.
 *
 * <p>The observable signature of a working breaker: the downstream call counter
 * stops rising while requests keep being answered.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint4CircuitBreakerTest extends AbstractProductionTest {

    @Test
    @DisplayName("once the breaker opens, the gateway stops being called")
    void breakerStopsTheCalls() throws Exception {
        gatewayIsDown();

        // Enough traffic to fill the sliding window and trip the breaker.
        for (int i = 0; i < 6; i++) {
            checkout("warmup-" + i);
        }
        int callsAfterTripping = meter.calls();

        for (int i = 0; i < 6; i++) {
            checkout("post-trip-" + i);
        }
        int callsAfterMore = meter.calls();

        System.out.printf("%n  calls before: %d, after 6 more requests: %d%n",
                callsAfterTripping, callsAfterMore);

        assertThat(callsAfterMore)
                .as("with the breaker open these requests should be rejected locally, "
                        + "adding no more than the odd half-open probe")
                .isLessThanOrEqualTo(callsAfterTripping + 2);
    }

    @Test
    @DisplayName("an open breaker fails fast and says so")
    void openBreakerFailsFast() throws Exception {
        gatewayIsDown();
        for (int i = 0; i < 6; i++) {
            checkout("trip-" + i);
        }

        long start = System.nanoTime();
        var response = checkout("rejected");
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n  rejected in %d ms with status %d%n", elapsed, response.statusCode());
        assertThat(elapsed)
                .as("a local rejection needs no network round trip and no retry delays")
                .isLessThan(150);
        assertThat(response.statusCode()).isEqualTo(503);
    }
}
