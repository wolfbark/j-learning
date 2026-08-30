package dev.vlearning.coordination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7: the only thing on this list that actually holds.
 *
 * <p>Look back at what each mechanism bought. The advisory lock and the lease
 * reduce how often two workers overlap. Fencing tokens make the overlap
 * harmless — if you can change the resource, which you usually cannot. None of
 * them makes duplicate execution impossible, because nothing can: the worker can
 * always be paused, the network can always deliver twice, and the retry you
 * added last month will always run again.
 *
 * <p>Idempotent work makes duplicate execution <em>not matter</em>. That is a
 * weaker promise and a much stronger position, and it is the same conclusion
 * {@code 07-events-and-outbox} reaches about at-least-once delivery: stop trying
 * to make it happen once, make happening twice a non-event.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7IdempotencyTest extends AbstractIntegrationTest {

    @Test
    void runningTheWholeBillingTwiceBillsEverybodyOnce() {
        worker("pod-a").runIdempotent(PERIOD);
        worker("pod-a").runIdempotent(PERIOD);

        assertThat(gateway.chargesFor("ada")).isEqualTo(1);
        assertThat(gateway.totalCharged()).isEqualTo(TOTAL_OWED);
        assertThat(invoiceCountFor("ada")).isEqualTo(1);
        assertThat(invoiceCount()).isEqualTo(CUSTOMERS);
    }

    @Test
    void andItDoesNotMatterWhichWorkerRunsIt() {
        worker("pod-a").runIdempotent(PERIOD);
        worker("pod-b").runIdempotent(PERIOD);
        worker("pod-c").runIdempotent(PERIOD);

        assertThat(gateway.charges())
                .as("no lock, no lease, no token, no coordination of any kind — and it is right")
                .hasSize(CUSTOMERS);
        assertThat(invoiceCount()).isEqualTo(CUSTOMERS);
    }

    @Test
    void theKeyIdentifiesTheWork_notTheAttempt() {
        worker("pod-a").runIdempotent(PERIOD);
        worker("pod-a").runIdempotent("2026-09");

        assertThat(gateway.chargesFor("ada"))
                .as("September is a different piece of work and must go through")
                .isEqualTo(2);
        assertThat(invoiceCount()).isEqualTo(2L * CUSTOMERS);
    }
}
