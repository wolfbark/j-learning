package dev.vlearning.coordination;

import dev.vlearning.coordination.support.Concurrently;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1: {@code synchronized} guards one JVM against itself, and nothing else
 * against anything.
 *
 * <p>These tests pass against the code as delivered — they pin the bug. The
 * uncomfortable part is the first one: a single-instance test of exactly this
 * code passes, forever, on every developer machine and in CI. The bug appears
 * the day somebody scales the deployment to two replicas, and it appears as
 * money.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1JvmLockTest extends AbstractIntegrationTest {

    @Test
    void oneInstanceIsPerfectlySafe() {
        var onlyPod = worker("pod-a");

        Concurrently.run(2, attempt -> onlyPod.runUnprotected(PERIOD));

        assertThat(gateway.charges())
                .as("the monitor did its job: two threads, one at a time")
                .hasSize(2 * CUSTOMERS);
        assertThat(gateway.chargesFor("ada")).isEqualTo(2);
    }

    @Test
    void twoInstancesChargeEverybodyTwice() {
        var podA = worker("pod-a");
        var podB = worker("pod-b");

        Concurrently.run(2, pod -> {
            if (pod == 0) {
                podA.runUnprotected(PERIOD);
            } else {
                podB.runUnprotected(PERIOD);
            }
        });

        assertThat(gateway.chargesFor("ada"))
                .as("two monitors, two critical sections, one customer charged twice")
                .isEqualTo(2);
        assertThat(gateway.totalCharged()).isEqualTo(2 * TOTAL_OWED);
        assertThat(invoiceCount()).isEqualTo(2L * CUSTOMERS);
    }

    @Test
    void andTheTransactionCannotHelpEither() {
        var podA = worker("pod-a");
        var podB = worker("pod-b");

        podA.runUnprotected(PERIOD);
        podB.runUnprotected(PERIOD);

        // Both transactions committed. Both were correct. The database has no
        // opinion about whether this work should have happened twice, because
        // nothing you wrote told it there was a rule.
        assertThat(gateway.totalCharged()).isEqualTo(2 * TOTAL_OWED);
    }
}
