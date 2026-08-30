package dev.vlearning.reliability.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two load models, about forty lines each, because the difference between them
 * is the difference between a load test that flatters you and one that tells you
 * something.
 *
 * <p><b>Closed model</b> — a fixed population of N users, each of whom waits for
 * its own response before thinking and asking again. Every commercial
 * "N concurrent users" harness works this way. Offered load is therefore a
 * <em>function of your response time</em>: when the service slows down, the
 * harness slows down with it. The queue can never exceed N.
 *
 * <p><b>Open model</b> — arrivals happen at a fixed rate whatever the service is
 * doing, which is how real traffic works: your users do not agree among
 * themselves to send fewer requests because you are having a bad day. When
 * arrival rate exceeds service rate the queue grows without limit, and latency
 * grows with it.
 *
 * <p>Both models here wait for outstanding requests to finish before computing
 * percentiles. Dropping unfinished requests instead — which is what a harness
 * does when it stops sampling at the end of the run — is the mechanical heart of
 * coordinated omission: the slowest requests are precisely the ones that get
 * excluded.
 */
public final class LoadHarness {

    public interface Op {
        Duration run() throws Exception;
    }

    public record LoadResult(String model, int arrivals, int completed, Duration p50, Duration p99,
                             Duration max, double throughputPerSecond) {

        @Override
        public String toString() {
            return "%s: arrivals=%d completed=%d p50=%dms p99=%dms max=%dms throughput=%.0f/s"
                    .formatted(model, arrivals, completed, p50.toMillis(), p99.toMillis(),
                            max.toMillis(), throughputPerSecond);
        }
    }

    private LoadHarness() {
    }

    /** {@code users} closed-loop clients, each thinking for {@code thinkTime} between requests. */
    public static LoadResult closedModel(int users, Duration thinkTime, Duration duration, Op op) {
        var latencies = new ConcurrentLinkedQueue<Duration>();
        var arrivals = new AtomicInteger();
        long deadline = System.nanoTime() + duration.toNanos();
        long startedAt = System.nanoTime();

        try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int user = 0; user < users; user++) {
                clients.submit(() -> {
                    while (System.nanoTime() < deadline) {
                        arrivals.incrementAndGet();
                        latencies.add(op.run());
                        Thread.sleep(thinkTime);
                    }
                    return null;
                });
            }
        }
        return summarise("closed", arrivals.get(), latencies,
                Duration.ofNanos(System.nanoTime() - startedAt));
    }

    /** Arrivals at a fixed rate, regardless of how the service is coping. */
    public static LoadResult openModel(double arrivalsPerSecond, Duration duration, Op op) {
        var latencies = new ConcurrentLinkedQueue<Duration>();
        var arrivals = new AtomicInteger();
        long intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / arrivalsPerSecond);
        long startedAt = System.nanoTime();
        long deadline = startedAt + duration.toNanos();

        try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
            long nextArrival = startedAt;
            while (nextArrival < deadline) {
                long waitNanos = nextArrival - System.nanoTime();
                if (waitNanos > 0) {
                    parkNanos(waitNanos);
                }
                arrivals.incrementAndGet();
                clients.submit(() -> latencies.add(op.run()));
                nextArrival += intervalNanos;
            }
            // Closing the executor waits for the backlog. Not waiting is how a
            // harness quietly deletes its own worst measurements.
        }
        return summarise("open", arrivals.get(), latencies,
                Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private static void parkNanos(long nanos) {
        try {
            Thread.sleep(Duration.ofNanos(nanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static LoadResult summarise(String model, int arrivals,
                                        ConcurrentLinkedQueue<Duration> latencies, Duration elapsed) {
        List<Duration> sorted = new ArrayList<>(latencies);
        sorted.sort(Duration::compareTo);
        if (sorted.isEmpty()) {
            return new LoadResult(model, arrivals, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0);
        }
        return new LoadResult(model, arrivals, sorted.size(),
                percentile(sorted, 0.50), percentile(sorted, 0.99),
                sorted.getLast(),
                sorted.size() / (elapsed.toNanos() / (double) TimeUnit.SECONDS.toNanos(1)));
    }

    private static Duration percentile(List<Duration> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }
}
