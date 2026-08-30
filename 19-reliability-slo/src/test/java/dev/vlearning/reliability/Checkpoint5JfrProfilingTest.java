package dev.vlearning.reliability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import dev.vlearning.reliability.profiling.JfrProfiler;
import dev.vlearning.reliability.profiling.ReportRenderer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5. Metrics tell you a report render is slow. They cannot tell you it is
 * slow because every caller queues on one monitor, or that it allocates 25 MB
 * per call. A profiler can, it ships with the JDK, and it costs a couple of
 * percent — so there is no excuse for guessing.
 *
 * <p>Verified on this machine (JDK 25): {@code jdk.JavaMonitorEnter} and
 * {@code jdk.ObjectAllocationSample} both carry stack traces that name the
 * offending class. {@code jdk.ExecutionSample} does too, but a two-second run
 * produces very few samples — it needs a longer recording to be useful.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5JfrProfilingTest {

    @TempDir
    Path recordings;

    @Test
    @DisplayName("the recording names the class responsible for contention and allocation")
    void findsThePlantedHotspots() throws Exception {
        var renderer = new ReportRenderer(new ChaosSwitch());
        var profiler = new JfrProfiler();

        Path recording = profiler.recordWhile(recordings.resolve("reports.jfr"),
                () -> driveRenderLoad(renderer));

        assertThat(Files.size(recording))
                .as("a dumped recording is a real file you can hand to JDK Mission Control")
                .isGreaterThan(1_000L);

        var hotspots = profiler.attributeTo(recording, "ReportRenderer");
        System.out.println("hotspots = " + hotspots);

        assertThat(hotspots)
                .as("both planted problems are attributable to the class, by stack trace")
                .containsKeys("jdk.JavaMonitorEnter", "jdk.ObjectAllocationSample");

        assertThat(hotspots.get("jdk.JavaMonitorEnter").count())
                .as("every caller waits for the same monitor — this is the number that "
                        + "explains a latency curve no metric could")
                .isGreaterThan(5L);
        assertThat(hotspots.get("jdk.JavaMonitorEnter").total())
                .as("and this is how much wall-clock time it cost")
                .isGreaterThan(Duration.ofMillis(50));

        assertThat(hotspots.get("jdk.ObjectAllocationSample").count())
                .as("sampled allocation points at the method doing the churning")
                .isGreaterThan(5L);
    }

    /** Platform threads on purpose: contention is the subject, not scheduling. */
    private static void driveRenderLoad(ReportRenderer renderer) {
        try (var pool = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 48).forEach(i -> pool.submit(() -> renderer.render("R-" + i)));
        }
    }
}
