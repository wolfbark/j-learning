package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6: the bill for step 3. The moment a request's work happens on threads
 * other than the request thread, {@link ThreadLocal} context stops arriving —
 * so the correlation id disappears from exactly the log lines you would want
 * during an incident.
 *
 * <p>{@code ScopedValue} (final in Java 25, JEP 506) is the replacement: bound
 * for the duration of a scope, inherited by threads forked inside it,
 * immutable, and cheap enough that a million virtual threads do not care.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Checkpoint6ContextPropagationTest extends AbstractLoomTest {

    @Test
    @DisplayName("every downstream call sees the request's correlation id")
    void contextSurvivesTheFanOut() throws Exception {
        var response = get("/customers/C-1", "trace-me-42");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"correlationId\":\"trace-me-42\"");

        System.out.printf("%n  downstream calls saw: %s%n", downstream.observedCorrelationIds());
        assertThat(downstream.observedCorrelationIds())
                .as("all three fan-out branches should see the request context, not null")
                .hasSize(3)
                .containsOnly("trace-me-42");
    }
}
