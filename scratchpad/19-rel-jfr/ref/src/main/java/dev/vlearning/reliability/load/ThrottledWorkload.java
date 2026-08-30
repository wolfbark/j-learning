package dev.vlearning.reliability.load;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * A unit of work with a hard capacity limit — four at a time, 25 ms each, so
 * 160 completions per second and not one more. Every real service has a number
 * like this somewhere (a pool, a licence, a downstream rate limit); most teams
 * have never measured theirs.
 *
 * <p>Step 4 offers this the same nominal load two different ways and watches
 * what the load model does to the answer.
 */
@Component
public class ThrottledWorkload {

    public static final int CAPACITY = 4;
    public static final Duration SERVICE_TIME = Duration.ofMillis(25);

    private final Semaphore capacity = new Semaphore(CAPACITY);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();

    /**
     * @return the latency the caller observed, queueing included — the only
     *         latency a user ever experiences.
     */
    public Duration handle() throws InterruptedException {
        long startedAt = System.nanoTime();
        peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            capacity.acquire();
            try {
                Thread.sleep(SERVICE_TIME);
            } finally {
                capacity.release();
            }
        } finally {
            inFlight.decrementAndGet();
            completed.incrementAndGet();
        }
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    /** Requests inside the service right now, being served or waiting. */
    public int inFlight() {
        return inFlight.get();
    }

    /** The high-water mark: this is the number the two load models disagree about. */
    public int peakInFlight() {
        return peakInFlight.get();
    }

    public int completed() {
        return completed.get();
    }

    public void reset() {
        peakInFlight.set(0);
        completed.set(0);
    }
}
