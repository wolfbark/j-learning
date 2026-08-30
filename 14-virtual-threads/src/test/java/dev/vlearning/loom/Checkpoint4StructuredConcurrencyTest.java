package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4: a hand-rolled executor fan-out leaks. When one branch fails, the
 * others keep running — burning downstream capacity for a response nobody will
 * ever read, and outliving the request that started them.
 *
 * <p>Structured concurrency makes the lifetime of the subtasks a scope, so
 * failure cancels siblings and nothing escapes the block. This is still a
 * preview API in Java 25 (JEP 505; finalisation targeted for JDK 27) — the
 * build already passes {@code --enable-preview}.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint4StructuredConcurrencyTest extends AbstractLoomTest {

    @Test
    @DisplayName("when pricing fails, the siblings are cancelled instead of finishing")
    void failureCancelsSiblings() throws Exception {
        downstream.failPricing(true);
        try {
            long delay = downstream.delayMillis();

            long start = System.nanoTime();
            var response = get("/customers/C-1", "cancel-check");
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            System.out.printf("%n  failed in %d ms, cancellations: %d%n", elapsed, downstream.cancellations());

            assertThat(response.statusCode())
                    .as("a failed downstream call should surface as an error, not a half-built view")
                    .isGreaterThanOrEqualTo(500);
            assertThat(elapsed)
                    .as("the request should fail as soon as pricing does, not wait for the slow siblings")
                    .isLessThan(delay * 2);
            assertThat(downstream.cancellations())
                    .as("profile and inventory should have been interrupted by the scope")
                    .isGreaterThanOrEqualTo(1);
        } finally {
            downstream.failPricing(false);
        }
    }
}
