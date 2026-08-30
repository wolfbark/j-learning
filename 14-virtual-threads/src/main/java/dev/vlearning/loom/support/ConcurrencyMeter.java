package dev.vlearning.loom.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The measuring instrument for this whole lesson: how many things were actually
 * happening at the same time, and what was the high-water mark.
 *
 * <p>Throughput numbers are easy to misread — a slow test machine, a warm-up
 * effect, a GC pause. "How many requests were in flight simultaneously" is a
 * much harder number to argue with, and it is the number that changes when you
 * flip one property in step 2.
 */
public final class ConcurrencyMeter {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();

    public <T> T measure(ThrowingSupplier<T> work) throws Exception {
        int now = inFlight.incrementAndGet();
        peak.accumulateAndGet(now, Math::max);
        total.incrementAndGet();
        try {
            return work.get();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public int peak() {
        return peak.get();
    }

    public int total() {
        return total.get();
    }

    public int inFlight() {
        return inFlight.get();
    }

    public void reset() {
        peak.set(0);
        total.set(0);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
