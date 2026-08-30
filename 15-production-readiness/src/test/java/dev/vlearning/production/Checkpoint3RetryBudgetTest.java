package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3, the uncomfortable half of retries: when the dependency is genuinely
 * down, every retry is extra load on a system that is already failing, and
 * every client is doing it at once. Retries turn a brownout into an outage —
 * this is the mechanism behind a large share of real cascading failures.
 *
 * <p>So retries must be bounded, backed off, jittered, and ideally budgeted.
 * The assertions below are about arithmetic you control: N requests must not
 * become an unbounded multiple of N calls.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint3RetryBudgetTest extends AbstractProductionTest {

    private static final int REQUESTS = 10;
    private static final int MAX_ATTEMPTS_PER_REQUEST = 3;

    @Test
    @DisplayName("one request against a dead gateway makes a bounded number of attempts")
    void attemptsPerRequestAreBounded() throws Exception {
        gatewayIsDown();

        var response = checkout("order-bounded");

        System.out.printf("%n  one request produced %d gateway calls%n", meter.calls());
        assertThat(response.statusCode()).isIn(502, 503);
        assertThat(meter.calls())
                .as("one initial attempt plus a bounded number of retries — never unbounded")
                .isBetween(2, MAX_ATTEMPTS_PER_REQUEST);
    }

    @Test
    @DisplayName("load does not get multiplied without limit")
    void amplificationIsBounded() {
        gatewayIsDown();

        var statuses = checkoutConcurrently(REQUESTS);
        System.out.printf("%n  %d requests produced %d gateway calls (amplification %.1fx)%n",
                REQUESTS, meter.calls(), meter.calls() / (double) REQUESTS);

        assertThat(statuses).allMatch(status -> status == 502 || status == 503);
        // Upper bound only: step 4 will add a breaker that pushes this number DOWN,
        // and this test has to keep passing when it does.
        assertThat(meter.calls())
                .as("%d requests must not exceed %d attempts each", REQUESTS, MAX_ATTEMPTS_PER_REQUEST)
                .isLessThanOrEqualTo(REQUESTS * MAX_ATTEMPTS_PER_REQUEST);
    }

    @Test
    @DisplayName("retries back off instead of firing immediately")
    void retriesBackOff() throws Exception {
        gatewayIsDown();

        long start = System.nanoTime();
        checkout("order-backoff");
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n  three attempts took %d ms%n", elapsed);
        assertThat(elapsed)
                .as("with a delay between attempts, three attempts cannot complete instantly")
                .isGreaterThan(100);
    }
}
