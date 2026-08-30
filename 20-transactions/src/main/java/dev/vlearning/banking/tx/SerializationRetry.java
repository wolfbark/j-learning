package dev.vlearning.banking.tx;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * SERIALIZABLE on Postgres is optimistic: instead of blocking, the database lets
 * transactions run and aborts one of them at commit time when the schedule turns
 * out not to be serialisable — SQLSTATE {@code 40001}.
 *
 * <p>That is not an error in the "something is broken" sense, it is the price of
 * the guarantee, and it is <em>your</em> job to pay it. Any code path running at
 * SERIALIZABLE without a retry loop is a code path that fails under load.
 *
 * <p>Checkpoint 5: make {@link #execute(Supplier)} run the unit of work again
 * from the top when the database reports a serialisation failure. Retrying is
 * only safe because the whole unit of work re-runs — including the reads.
 */
@Component
public class SerializationRetry {

    public static final int MAX_ATTEMPTS = 10;

    private final AtomicInteger retries = new AtomicInteger();

    public <T> T execute(Supplier<T> work) {
        throw new UnsupportedOperationException(
                "Checkpoint 5: retry `work` while the database reports a serialisation failure, "
                        + "up to MAX_ATTEMPTS, counting each retry with retried()");
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    /** Call once per retry so the checkpoint test can prove retrying actually happened. */
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
