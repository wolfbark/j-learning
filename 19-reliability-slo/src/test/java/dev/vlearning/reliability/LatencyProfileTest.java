package dev.vlearning.reliability;

import java.time.Duration;

import dev.vlearning.reliability.settlement.LatencyProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Always on. The distribution this service produces is the premise of step 3, so
 * it is worth pinning: mostly fast, one in ten terrible, and a mean that sits in
 * a region where almost no actual request lives.
 */
class LatencyProfileTest {

    @Test
    @DisplayName("the latency distribution is bimodal, and the mean describes neither mode")
    void bimodal() {
        var sample = LatencyProfile.bimodalSample(2_000, LatencyProfile.DEFAULT_PATHOLOGICAL_SHARE,
                20_260_825L);

        long slow = sample.stream().filter(d -> d.toMillis() > 500).count();
        assertThat(slow).isBetween(150L, 250L);

        double meanMillis = sample.stream().mapToLong(Duration::toMillis).average().orElseThrow();
        assertThat(meanMillis)
                .as("the mean sits between the two modes: no request is anywhere near it")
                .isBetween(80.0, 160.0);
        assertThat(meanMillis)
                .as("and it is comfortably inside the SLO, which is the trap")
                .isLessThan(LatencyProfile.LATENCY_SLO.toMillis());
    }

    @Test
    @DisplayName("the settlement timer keeps its name across every refactoring")
    void timerName() {
        var registry = new SimpleMeterRegistry();
        LatencyProfile.timer(registry).record(Duration.ofMillis(10));

        assertThat(registry.find("settlement.latency").timer()).isNotNull();
    }
}
