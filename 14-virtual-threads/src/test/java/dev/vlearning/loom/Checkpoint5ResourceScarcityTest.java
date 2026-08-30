package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5, the lesson's real punchline: Loom removed thread scarcity, and in
 * doing so it promoted your connection pool to chief bottleneck.
 *
 * <p>The given endpoint borrows a connection for the whole request, including
 * three remote calls that do not need one — the shape of an over-broad
 * {@code @Transactional}. With eight platform threads that bug was invisible
 * (the thread pool throttled everything first). With a virtual thread per
 * request, ten permits and 550 ms hold times are the entire ceiling.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint5ResourceScarcityTest extends AbstractLoomTest {

    private static final int USERS = 40;

    @Test
    @DisplayName("the pool saturates: threads are free, connections are not")
    void thePoolIsTheCeiling() {
        var result = harness.fireConcurrently(USERS, url("/customers/C-1/with-database"));
        System.out.print(result.summary("with database, pool of " + pool.size()));
        System.out.printf("  pool peak: %d/%d, average hold: %d ms%n",
                pool.peakConcurrentUse(), pool.size(), pool.averageHoldTime().toMillis());

        assertThat(result.successes()).isEqualTo(USERS);
        assertThat(pool.peakConcurrentUse())
                .as("every permit should be in use — this is what a saturated pool looks like")
                .isEqualTo(pool.size());
    }

    @Test
    @DisplayName("connections are held only for the queries, not for the remote calls")
    void criticalSectionIsNarrow() {
        long delay = downstream.delayMillis();

        var result = harness.fireConcurrently(USERS, url("/customers/C-1/with-database"));
        long averageHold = pool.averageHoldTime().toMillis();
        System.out.print(result.summary("narrowed critical section"));
        System.out.printf("  average hold: %d ms (remote calls alone cost %d ms)%n", averageHold, delay);

        assertThat(result.successes()).isEqualTo(USERS);
        assertThat(averageHold)
                .as("a connection should be held for the two ~50 ms queries, not across a %d ms remote call", delay)
                .isLessThan(delay);
        assertThat(result.totalMillis())
                .as("with the pool freed up, 40 users should not take four serialised waves")
                .isLessThan(delay * 8);
    }
}
