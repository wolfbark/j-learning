package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5. A bulkhead: cap how much of your own capacity one dependency may
 * consume, so a slow gateway degrades checkout instead of taking down every
 * unrelated endpoint with it.
 *
 * <p>This matters far more since virtual threads (project 14). When threads were
 * the scarce resource, the thread pool was an accidental bulkhead. Now that a
 * thread costs nothing, 10 000 requests will all cheerfully pile into a dying
 * dependency unless you say otherwise — and {@code @ConcurrencyLimit} is how you
 * say it.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint5ConcurrencyLimitTest extends AbstractProductionTest {

    private static final int LIMIT = 5;

    @Test
    @DisplayName("no more than the configured number of calls reach the gateway at once")
    void concurrencyIsCapped() {
        gatewayIsSlow(300);

        var statuses = checkoutConcurrently(20);
        System.out.printf("%n  peak concurrent gateway calls: %d (limit %d)%n",
                meter.peakInFlight(), LIMIT);

        assertThat(statuses).filteredOn(status -> status == 200).hasSizeGreaterThan(0);
        assertThat(meter.peakInFlight())
                .as("the bulkhead should hold in-flight calls at or below %d", LIMIT)
                .isLessThanOrEqualTo(LIMIT);
    }
}
