package dev.vlearning.ticketing.booking;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Optimistic locking does not prevent a conflict; it detects one and refuses the
 * write. Somebody then has to decide what to do about it, and for a customer who
 * did nothing wrong the answer is almost always "quietly do it again".
 *
 * <p>Checkpoint 3: run the unit of work again from the top when the version
 * check fails. Note the two conditions this must satisfy, both of which the
 * tests check:
 *
 * <ul>
 *   <li>it has to wrap the <em>whole transactional call</em> — the version check
 *       happens at flush, which Spring performs after your service method has
 *       returned;
 *   <li>the retry has to re-read, or it will just re-submit the same stale
 *       version and fail forever.
 * </ul>
 */
@Component
public class OptimisticRetry {

    public static final int MAX_ATTEMPTS = 20;

    private final AtomicInteger retries = new AtomicInteger();

    public <T> T execute(Supplier<T> work) {
        throw new UnsupportedOperationException(
                "Checkpoint 3: retry `work` while it fails the version check, up to MAX_ATTEMPTS, "
                        + "counting each retry with retried()");
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    protected void retried() {
        retries.incrementAndGet();
    }

    public int retryCount() {
        return retries.get();
    }

    public void reset() {
        retries.set(0);
    }
}
