package dev.vlearning.reliability;

import java.util.concurrent.TimeUnit;
import dev.vlearning.reliability.settlement.LatencyProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NumbersTest {
    @Test
    void print() {
        var registry = new SimpleMeterRegistry();
        var timer = LatencyProfile.timer(registry);
        LatencyProfile.bimodalSample(2_000, 0.10, 20_260_825L).forEach(timer::record);
        var s = timer.takeSnapshot();
        System.out.printf("NUMBERS mean=%.1fms max=%.1fms%n", s.mean(TimeUnit.MILLISECONDS), s.max(TimeUnit.MILLISECONDS));
        for (var v : s.percentileValues()) {
            System.out.printf("NUMBERS p%.0f=%.1fms%n", v.percentile() * 100, v.value(TimeUnit.MILLISECONDS));
        }
    }
}
