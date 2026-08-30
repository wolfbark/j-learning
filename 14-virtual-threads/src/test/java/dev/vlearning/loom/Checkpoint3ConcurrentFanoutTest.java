package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: cheap threads change what "reasonable code" means. Three independent
 * calls should cost one call's latency, not three — and now that a thread costs
 * about a kilobyte, there is no budget argument against doing it.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint3ConcurrentFanoutTest extends AbstractLoomTest {

    @Test
    @DisplayName("one request runs its three downstream calls concurrently")
    void fanOutIsConcurrent() throws Exception {
        var response = get("/customers/C-1", "fanout-check");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(downstream.meter().total()).isEqualTo(3);
        assertThat(downstream.meter().peak())
                .as("all three independent calls should be in flight at once")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("latency is now the slowest call, not the sum of all three")
    void latencyIsMaxNotSum() throws Exception {
        long delay = downstream.delayMillis();

        long start = System.nanoTime();
        get("/customers/C-1", "latency-check");
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n  one request: %d ms (sequential would be ~%d ms)%n", elapsed, delay * 3);
        assertThat(elapsed)
                .as("three concurrent %d ms calls should finish well inside the %d ms sequential cost",
                        delay, delay * 3)
                .isLessThan(delay * 2);
    }
}
