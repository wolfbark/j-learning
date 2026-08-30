package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1. The gateway takes five seconds. Without a timeout your request takes
 * five seconds too — and so does every other request queued behind whatever
 * resource it is holding. This is how a slow dependency becomes your outage.
 *
 * <p>A timeout is the only resilience pattern that is never optional: retries,
 * breakers and limits all assume calls eventually return.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint1TimeoutTest extends AbstractProductionTest {

    @Test
    @DisplayName("a hung gateway fails fast instead of hanging the request")
    void slowGatewayFailsFast() throws Exception {
        gatewayIsSlow(5_000);

        long start = System.nanoTime();
        var response = checkout("order-timeout");
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        long perAttempt = elapsed / Math.max(1, meter.calls());
        System.out.printf("%n  returned in %d ms over %d attempt(s) = %d ms each (gateway takes 5000 ms)%n",
                elapsed, meter.calls(), perAttempt);

        assertThat(elapsed)
                .as("the request must give up rather than wait out the gateway's 5 s")
                .isLessThan(5_000);
        // Per attempt, not total: once step 2 adds retries, three bounded attempts
        // legitimately cost three timeouts. A timeout bounds one call, and a retry
        // multiplies whatever it wraps — which is exactly step 3's warning.
        assertThat(perAttempt)
                .as("each individual attempt should be cut off after about a second")
                .isLessThan(2_000);
        assertThat(response.statusCode())
                .as("a timeout is a dependency failure, not a success")
                .isIn(502, 503);
    }
}
