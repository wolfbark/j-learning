package dev.vlearning.coordination;

import dev.vlearning.coordination.billing.Billing;
import dev.vlearning.coordination.locking.AdvisoryLock;
import dev.vlearning.coordination.support.Concurrently;
import dev.vlearning.coordination.support.DbSession.Isolation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2: mutual exclusion for the price of one function call.
 *
 * <p>An advisory lock is a lock on a number of your choosing. No table, no row,
 * no migration — and, in the transaction-scoped form, nothing that can be
 * leaked, because the commit releases it and so does the crash.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2AdvisoryLockTest extends AbstractIntegrationTest {

    @Autowired
    AdvisoryLock advisoryLock;

    @Test
    void whileSomebodyElseHoldsTheKey_thisRunDoesNothingAtAll() {
        try (var other = session("other-pod")) {
            other.begin(Isolation.READ_COMMITTED);
            assertThat(other.queryLong("SELECT pg_try_advisory_xact_lock(?)::int", Billing.BILLING_LOCK_KEY)).isEqualTo(1);

            assertThat(worker("pod-b").runWithAdvisoryLock(PERIOD))
                    .as("no wait, no exception, no work — just false")
                    .isFalse();
            assertThat(gateway.charges()).isEmpty();
            assertThat(invoiceCount()).isZero();
        }
    }

    @Test
    void theCommitReleasesIt_notYou() {
        try (var other = session("other-pod")) {
            other.begin(Isolation.READ_COMMITTED);
            assertThat(other.queryLong("SELECT pg_try_advisory_xact_lock(?)::int", Billing.BILLING_LOCK_KEY)).isEqualTo(1);
            assertThat(worker("pod-b").runWithAdvisoryLock(PERIOD)).isFalse();

            other.commit();
        }

        assertThat(worker("pod-b").runWithAdvisoryLock(PERIOD)).isTrue();
        assertThat(gateway.charges()).hasSize(CUSTOMERS);
        assertThat(advisoryLock.heldAdvisoryLockCount())
                .as("and our own transaction released it on the way out, with no unlock call anywhere")
                .isZero();
    }

    @Test
    void twoPodsRacing_exactlyOneBills() {
        var podA = worker("pod-a");
        var podB = worker("pod-b");
        // Whoever wins the lock stays in the critical section long enough for the
        // loser to have genuinely tried.
        podA.pauseAfterTakingTheLock(() -> sleep(400));
        podB.pauseAfterTakingTheLock(() -> sleep(400));

        var outcome = Concurrently.run(2, pod -> {
            boolean ran = pod == 0 ? podA.runWithAdvisoryLock(PERIOD) : podB.runWithAdvisoryLock(PERIOD);
            if (!ran) {
                throw new IllegalStateException("did not get the lock");
            }
        });

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(gateway.totalCharged()).isEqualTo(TOTAL_OWED);
        assertThat(invoiceCount()).isEqualTo(CUSTOMERS);
    }
}
