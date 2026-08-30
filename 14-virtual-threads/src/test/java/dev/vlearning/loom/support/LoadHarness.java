package dev.vlearning.loom.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * A load generator small enough to read, so nobody has to install wrk to do
 * this lesson.
 *
 * <p>It runs every request on its own virtual thread, so the *client* is never
 * the bottleneck — a mistake that has invalidated more virtual-thread
 * benchmarks than any other.
 */
public final class LoadHarness {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Fire {@code count} GETs at {@code url} all at once and report what happened. */
    public Result fireConcurrently(int count, String url) {
        List<Callable<Sample>> calls = IntStream.range(0, count)
                .<Callable<Sample>>mapToObj(i -> () -> {
                    long start = System.nanoTime();
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .header("X-Correlation-Id", "load-" + i)
                                    .timeout(Duration.ofSeconds(60))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
                    return new Sample(response.statusCode(), (System.nanoTime() - start) / 1_000_000,
                            response.body());
                })
                .toList();

        long start = System.nanoTime();
        List<Sample> samples;
        try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Sample>> futures = calls.stream().map(clients::submit).toList();
            samples = futures.stream().map(LoadHarness::join).toList();
        }
        long totalMillis = (System.nanoTime() - start) / 1_000_000;
        return new Result(samples, totalMillis);
    }

    private static Sample join(Future<Sample> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return new Sample(-1, -1, "client error: " + e);
        }
    }

    public record Sample(int status, long millis, String body) {}

    public record Result(List<Sample> samples, long totalMillis) {

        public long successes() {
            return samples.stream().filter(s -> s.status() == 200).count();
        }

        public long percentileMillis(int percentile) {
            List<Long> sorted = samples.stream().map(Sample::millis).sorted().toList();
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
            return sorted.get(Math.max(0, index));
        }

        public double requestsPerSecond() {
            return totalMillis == 0 ? 0 : samples.size() * 1000.0 / totalMillis;
        }

        /** Printed by every load test — the lesson's actual output. */
        public String summary(String label) {
            return """

                    ── %s ─────────────────────────────────────
                      requests      : %d (%d ok)
                      wall clock    : %d ms
                      throughput    : %.1f req/s
                      p50 / p99     : %d ms / %d ms
                    """.formatted(label, samples().size(), successes(), totalMillis(),
                    requestsPerSecond(), percentileMillis(50), percentileMillis(99));
        }
    }
}
