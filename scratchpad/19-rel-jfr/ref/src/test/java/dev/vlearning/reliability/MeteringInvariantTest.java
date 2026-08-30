package dev.vlearning.reliability;

import dev.vlearning.reliability.settlement.RequestMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Always on, and the constraint that makes step 2 interesting: bounding
 * cardinality must not lose the count. Anyone can make a metric cheap by
 * deleting it.
 */
class MeteringInvariantTest {

    @Test
    @DisplayName("every request is counted exactly once, however it is tagged")
    void noRequestIsLost() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RequestMetrics(registry);

        for (int user = 0; user < 5; user++) {
            for (int order = 0; order < 5; order++) {
                metrics.recordRequest("U-" + user, "ORD-" + order, 200);
            }
        }

        double total = registry.find(RequestMetrics.REQUESTS).counters().stream()
                .mapToDouble(Counter::count).sum();
        assertThat(total).isEqualTo(25.0);
    }
}
