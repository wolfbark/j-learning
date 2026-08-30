package dev.vlearning.reliability.settlement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * The latency of this service is bimodal: most settlements are fast, one in ten
 * takes the slow path (a cache miss, a cold partition, a chatty downstream —
 * pick your favourite). Real services look like this. Almost nothing looks like
 * a normal distribution.
 *
 * <p>Step 3's refactoring subject is {@link #timer(MeterRegistry)}: a timer that
 * publishes count, total and max, which is exactly enough to compute a mean and
 * not nearly enough to answer "are we meeting the SLO?".
 */
@Component
public class LatencyProfile {

    /** The promise: 99% of settlements complete inside this. */
    public static final Duration LATENCY_SLO = Duration.ofMillis(250);

    public static final Duration FAST = Duration.ofMillis(20);
    public static final Duration PATHOLOGICAL = Duration.ofMillis(900);
    public static final double DEFAULT_PATHOLOGICAL_SHARE = 0.10;

    private final Timer timer;

    public LatencyProfile(MeterRegistry registry) {
        this.timer = timer(registry);
    }

    /**
     * STEP 3 LIVES HERE. As written, the only distribution statistics that leave
     * this process are count, sum and max — a mean, in other words.
     */
    public static Timer timer(MeterRegistry registry) {
        return Timer.builder("settlement.latency")
                .description("End-to-end settlement latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100), LATENCY_SLO, Duration.ofSeconds(1))
                .register(registry);
    }

    public void record(Duration observed) {
        timer.record(observed);
    }

    public Timer timer() {
        return timer;
    }

    /** One draw from the distribution. */
    public Duration next(double pathologicalShare) {
        return draw(ThreadLocalRandom.current(), pathologicalShare);
    }

    /**
     * A reproducible sample of the same distribution. Step 3 replays this into a
     * timer instead of living through it, so the checkpoint runs in
     * milliseconds — which is also, honestly, why that test cannot show you
     * coordinated omission. See the lesson text.
     */
    public static List<Duration> bimodalSample(int count, double pathologicalShare, long seed) {
        var random = new Random(seed);
        var sample = new ArrayList<Duration>(count);
        for (int i = 0; i < count; i++) {
            sample.add(draw(random, pathologicalShare));
        }
        return List.copyOf(sample);
    }

    private static Duration draw(Random random, double pathologicalShare) {
        boolean slow = random.nextDouble() < pathologicalShare;
        Duration base = slow ? PATHOLOGICAL : FAST;
        long jitterMillis = slow ? random.nextInt(-50, 51) : random.nextInt(-5, 11);
        return base.plusMillis(jitterMillis);
    }
}
