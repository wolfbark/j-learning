package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The baseline, and a permanent exhibit: this test pins
 * {@code spring.threads.virtual.enabled=false} in its own context, so it keeps
 * telling the truth about platform threads even after you switch the
 * application over in step 2.
 *
 * <p>Eight Tomcat threads, forty simultaneous users, three sequential 150 ms
 * calls each. Nothing is CPU-bound; nothing is broken; the machine is almost
 * entirely idle — and the queue is still five deep. That is thread scarcity,
 * and it is an accounting problem, not a physics problem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.threads.virtual.enabled=false")
class PlatformThreadCeilingTest extends AbstractLoomTest {

    private static final int USERS = 40;
    private static final int TOMCAT_THREADS = 8;

    @Test
    @DisplayName("with 8 platform threads, 40 users cannot get more than 8 requests in flight")
    void platformThreadsCapConcurrency() {
        var result = harness.fireConcurrently(USERS, url("/customers/C-1"));
        System.out.print(result.summary("platform threads, " + TOMCAT_THREADS + " max"));
        System.out.printf("  peak requests in flight: %d (tomcat max %d)%n",
                requests.meter().peak(), TOMCAT_THREADS);

        assertThat(result.successes()).isEqualTo(USERS);
        // The thread pool IS the ceiling: request 9 waits for one of the eight to finish.
        // Measured at the request, not downstream — after step 3 one request fans out to
        // three concurrent calls, and this exhibit must keep telling the truth.
        assertThat(requests.meter().peak())
                .as("requests in flight cannot exceed the platform thread pool")
                .isLessThanOrEqualTo(TOMCAT_THREADS);
    }
}
