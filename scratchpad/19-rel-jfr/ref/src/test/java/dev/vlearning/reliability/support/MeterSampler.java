package dev.vlearning.reliability.support;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * A gauge only tells you the truth at the instant you read it, and "connections
 * pending" is zero again by the time your test finishes. Polling it during the
 * workload and keeping the high-water mark is what a dashboard does for you, and
 * what you have to do by hand in a test.
 */
public final class MeterSampler implements AutoCloseable {

    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final AtomicReference<Double> peak = new AtomicReference<>(0.0);

    public MeterSampler(MeterRegistry registry, String gaugeName) {
        poller.scheduleAtFixedRate(() -> {
            var gauge = registry.find(gaugeName).gauge();
            if (gauge != null) {
                peak.accumulateAndGet(gauge.value(), Math::max);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    public double peak() {
        return peak.get();
    }

    @Override
    public void close() {
        poller.shutdownNow();
    }
}
