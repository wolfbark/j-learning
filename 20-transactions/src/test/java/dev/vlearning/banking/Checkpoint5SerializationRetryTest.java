package dev.vlearning.banking;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.vlearning.banking.overdraft.OverdraftService;
import dev.vlearning.banking.support.Concurrently;
import dev.vlearning.banking.tx.SerializationRetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5: SERIALIZABLE is only half a solution. The other half is the retry.
 *
 * <p>Eight customers-service calls all read the same rows and all withdraw a
 * small amount that is comfortably affordable. Every one of them is legitimate,
 * so every one of them must eventually succeed — but at SERIALIZABLE some of
 * them will be aborted on the way, and it is the application's job to try again.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5SerializationRetryTest extends AbstractIntegrationTest {

    private static final int CALLERS = 8;
    private static final long EACH = 500;

    @Autowired
    OverdraftService overdraft;

    @Autowired
    SerializationRetry retry;

    @BeforeEach
    void resetCounters() {
        retry.reset();
    }

    @Test
    void everyLegitimateWithdrawalEventuallySucceeds() {
        forceAllCallersToReadBeforeAnyOfThemWrites();

        var outcome = Concurrently.run(CALLERS, worker -> retry.execute(() ->
                overdraft.withdraw(ADA, worker % 2 == 0 ? ADA_CHECKING : ADA_SAVINGS, EACH)));

        assertThat(outcome.failures()).isEmpty();
        assertThat(outcome.successes()).isEqualTo(CALLERS);
        assertThat(combined(ADA))
                .as("every withdrawal applied exactly once — no double-spend from a retry")
                .isEqualTo(10_000 - CALLERS * EACH);
        assertThat(retry.retryCount())
                .as("if nothing was ever retried, the retry loop was not exercised")
                .isPositive();
    }

    @Test
    void theRetryMustWrapTheWholeTransaction_notJustTheQuery() {
        // A serialisation failure is raised at COMMIT, which the proxy performs
        // after the service method has already returned. A try/catch inside
        // OverdraftService.withdraw could never see it.
        forceAllCallersToReadBeforeAnyOfThemWrites();

        Concurrently.run(CALLERS, worker -> retry.execute(() ->
                overdraft.withdraw(ADA, worker % 2 == 0 ? ADA_CHECKING : ADA_SAVINGS, EACH)));

        assertThat(combined(ADA)).isGreaterThanOrEqualTo(0);
    }

    /**
     * Guarantees a serialisation conflict rather than hoping for one. Only the
     * first wave of readers waits; retried attempts sail past the hook, or the
     * ones that were aborted would wait for partners that no longer exist.
     */
    private void forceAllCallersToReadBeforeAnyOfThemWrites() {
        var arrived = new AtomicInteger();
        var firstWaveHasRead = new CountDownLatch(CALLERS);
        interleaving.armAfterRead(() -> {
            if (arrived.incrementAndGet() > CALLERS) {
                return;
            }
            firstWaveHasRead.countDown();
            try {
                firstWaveHasRead.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
