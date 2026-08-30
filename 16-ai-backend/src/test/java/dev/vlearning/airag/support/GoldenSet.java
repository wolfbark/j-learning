package dev.vlearning.airag.support;

import java.util.List;

import dev.vlearning.airag.eval.EvalCase;

/**
 * The golden set: questions a competent index of this corpus must answer from the right place.
 * Hand-written, deliberately small, and the single most valuable artefact in a RAG project — it is
 * the only thing that turns "the answers feel better now" into a number.
 *
 * <p>{@code expectedHeading} is matched as a substring of the chunk's heading path, so a chunker
 * that records {@code "Methodologies > 4. Testing Strategy ... > Key Java tools"} satisfies
 * {@code "Testing Strategy"}.
 */
public final class GoldenSet {

    public static final List<EvalCase> CASES = List.of(
            new EvalCase("How do I publish events without a dual write, using an outbox and idempotent consumers?",
                    "event-driven.md", "Transactional Outbox"),
            new EvalCase("What is the difference between choreography and orchestration for distributed transactions?",
                    "event-driven.md", "Sagas"),
            new EvalCase("When should I use ports and adapters instead of a layered architecture?",
                    "architecture-styles.md", "Hexagonal"),
            new EvalCase("Which Java tools do I use for a test pyramid with Testcontainers and mutation testing?",
                    "methodologies.md", "Testing Strategy"),
            new EvalCase("What does pinning mean for virtual threads and which JEP fixed it?",
                    "platform-and-production.md", "Virtual threads"),
            new EvalCase("What is MCP and which Spring AI version supports tool calling?",
                    "platform-and-production.md", "AI in Java Backends"),
            new EvalCase("Why is a snapshot in an event store never the truth?",
                    "09-event-sourcing.md", "Core concepts"));

    private GoldenSet() {
    }
}
