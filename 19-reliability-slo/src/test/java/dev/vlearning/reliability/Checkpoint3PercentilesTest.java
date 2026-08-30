package dev.vlearning.reliability;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import dev.vlearning.reliability.settlement.LatencyProfile;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3. The mean is inside the SLO. The 99th percentile is nearly four times
 * the SLO. Both numbers describe the same 2 000 requests, and only one of them
 * describes anybody's experience.
 *
 * <p>This is the exact situation a dashboard hides when it graphs
 * {@code rate(sum)/rate(count)}, which is what a timer without buckets can
 * offer it.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3PercentilesTest {

    private static final long SLO_MILLIS = LatencyProfile.LATENCY_SLO.toMillis();

    private HistogramSnapshot recordTheRealDistribution() {
        var registry = new SimpleMeterRegistry();
        var timer = LatencyProfile.timer(registry);
        LatencyProfile.bimodalSample(2_000, LatencyProfile.DEFAULT_PATHOLOGICAL_SHARE, 20_260_825L)
                .forEach(timer::record);
        return timer.takeSnapshot();
    }

    @Test
    @DisplayName("the mean is fine and the p99 is a disaster — from the same data")
    void averagesLie() {
        var snapshot = recordTheRealDistribution();

        assertThat(snapshot.mean(TimeUnit.MILLISECONDS))
                .as("this is the number on the dashboard everyone is happy with")
                .isLessThan(SLO_MILLIS);

        assertThat(snapshot.percentileValues())
                .as("a timer with no percentiles configured can only be averaged; "
                        + "publishPercentiles(...) is what puts client-side quantiles here")
                .isNotEmpty();

        double p99Millis = percentile(snapshot, 0.99);
        assertThat(p99Millis)
                .as("one settlement in ten takes ~900ms, so the p99 cannot be inside a 250ms SLO")
                .isGreaterThan(SLO_MILLIS);
    }

    @Test
    @DisplayName("the SLO bucket answers the only question that matters: how many users missed it")
    void sloBucketCountsTheUnhappy() {
        var snapshot = recordTheRealDistribution();

        System.out.println("buckets = " + Arrays.toString(snapshot.histogramCounts()));
        assertThat(snapshot.histogramCounts().length)
                .as("explicit bucket boundaries around the promise. Note what a "
                        + "SimpleMeterRegistry does NOT give you: publishPercentileHistogram() "
                        + "only materialises its bucket ladder on registries that support "
                        + "aggregable percentiles (Prometheus). Here you get exactly the "
                        + "boundaries you asked for — which is the ones that matter anyway.")
                .isGreaterThanOrEqualTo(3);

        CountAtBucket sloBucket = Arrays.stream(snapshot.histogramCounts())
                .filter(bucket -> Math.round(bucket.bucket()) == LatencyProfile.LATENCY_SLO.toNanos())
                .findFirst()
                .orElse(null);
        assertThat(sloBucket)
                .as("serviceLevelObjectives(%s) adds a bucket boundary exactly at the promise, "
                        + "so the SLI is a division and not an estimate", LatencyProfile.LATENCY_SLO)
                .isNotNull();

        double insideSlo = sloBucket.count() / (double) snapshot.count();
        assertThat(insideSlo)
                .as("about 90%% of settlements meet the SLO — which fails a 99%% objective "
                        + "while the mean says everything is fine")
                .isBetween(0.80, 0.95)
                .isLessThan(0.99);
    }

    private static double percentile(HistogramSnapshot snapshot, double percentile) {
        return Arrays.stream(snapshot.percentileValues())
                .filter(value -> Math.abs(value.percentile() - percentile) < 1e-9)
                .findFirst()
                .map(value -> value.value(TimeUnit.MILLISECONDS))
                .orElseGet(() -> {
                    throw new AssertionError("no p" + percentile + " published; found "
                            + Arrays.stream(snapshot.percentileValues())
                            .map(ValueAtPercentile::percentile).toList());
                });
    }
}
