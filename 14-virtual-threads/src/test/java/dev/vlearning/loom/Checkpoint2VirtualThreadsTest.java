package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2: the same forty users, the same eight-thread configuration, one extra
 * line of properties. Note that this test does NOT set the property — it reads
 * the application's real configuration, so it only passes once you have made
 * the change yourself.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint2VirtualThreadsTest extends AbstractLoomTest {

    private static final int USERS = 40;

    @Test
    @DisplayName("requests are served on virtual threads")
    void requestsRunOnVirtualThreads() throws Exception {
        var response = get("/customers/C-1", "vt-check");

        assertThat(response.body())
                .as("the request thread should be virtual once spring.threads.virtual.enabled=true")
                .contains("\"servedByVirtualThread\":true");
    }

    @Test
    @DisplayName("40 users are no longer queued behind 8 threads")
    void virtualThreadsRemoveTheThreadCeiling() {
        var result = harness.fireConcurrently(USERS, url("/customers/C-1"));
        System.out.print(result.summary("virtual threads"));
        System.out.printf("  peak requests in flight: %d (was capped at 8)%n", requests.meter().peak());

        assertThat(result.successes()).isEqualTo(USERS);
        assertThat(requests.meter().peak())
                .as("with a virtual thread per request, in-flight requests should blow past the old cap of 8")
                .isGreaterThan(20);
    }
}
