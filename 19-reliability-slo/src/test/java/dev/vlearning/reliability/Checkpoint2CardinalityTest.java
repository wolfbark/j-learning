package dev.vlearning.reliability;

import java.util.Set;
import java.util.stream.Collectors;

import dev.vlearning.reliability.settlement.RequestMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2. The outage you cause yourself. Every distinct tag-value combination is
 * a time series: stored, indexed, and multiplied by every other tag. Two
 * unbounded tags do not add, they multiply.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2CardinalityTest {

    /** Whatever the tagging scheme is, this many series is a metrics bill, not a signal. */
    private static final int MAX_SERIES = 20;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RequestMetrics metrics = new RequestMetrics(registry);

    @Test
    @DisplayName("500 distinct users produce a bounded number of series")
    void cardinalityIsBounded() {
        for (int i = 0; i < 500; i++) {
            metrics.recordRequest("U-" + (10_000 + i), "ORD-" + (500_000 + i), i % 17 == 0 ? 502 : 200);
        }

        assertThat(metrics.seriesCount())
                .as("500 users × 500 order ids × 2 statuses is half a million series in the "
                        + "unbounded version — the arithmetic is a product, not a sum")
                .isLessThanOrEqualTo(MAX_SERIES);
    }

    @Test
    @DisplayName("bounding cardinality does not lose the count")
    void countIsPreserved() {
        for (int i = 0; i < 500; i++) {
            metrics.recordRequest("U-" + (10_000 + i), "ORD-" + (500_000 + i), 200);
        }

        double total = registry.find(RequestMetrics.REQUESTS).counters().stream()
                .mapToDouble(Counter::count).sum();
        assertThat(total)
                .as("aggregate, do not drop: a bucketed tag still counts every request")
                .isEqualTo(500.0);
    }

    @Test
    @DisplayName("no tag value is a raw identifier")
    void noIdentifiersInTags() {
        metrics.recordRequest("U-10001", "ORD-500001", 200);

        Set<String> tagValues = registry.find(RequestMetrics.REQUESTS).counters().stream()
                .flatMap(counter -> counter.getId().getTags().stream())
                .map(Tag::getValue)
                .collect(Collectors.toSet());

        assertThat(tagValues)
                .as("high-cardinality values belong in logs and traces, where they are "
                        + "stored once per event, not in metrics, where they are stored forever "
                        + "per series")
                .noneMatch(value -> value.contains("ORD-") || value.contains("U-1"));
    }
}
