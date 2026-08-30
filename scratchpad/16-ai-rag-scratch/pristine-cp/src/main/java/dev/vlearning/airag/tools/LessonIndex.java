package dev.vlearning.airag.tools;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * A curated index of the sixteen projects, exposed to the model as a callable tool.
 *
 * <p>This is the honest use case for tool calling: retrieval answers "what does the material say",
 * a tool answers "which lesson is it in" — a lookup over a small authoritative list that no amount
 * of embedding similarity does reliably.
 *
 * <p>Step 5 is one annotation and one wiring change. The invocation counter is not production code;
 * it is here so the checkpoint can prove the model really called the method.
 */
@Component
public class LessonIndex {

    public record Lesson(String slug, String title, String topics) {
    }

    private static final List<Lesson> LESSONS = List.of(
            new Lesson("01-modern-java", "Modern Java", "records sealed interfaces pattern matching switch streams optional text blocks"),
            new Lesson("02-tdd", "Test-Driven Development", "tdd red green refactor classicist mockist outside-in london school"),
            new Lesson("03-vertical-slices", "Vertical Slice Architecture", "feature folders slices cqrs-lite package by feature"),
            new Lesson("04-hexagonal-architecture", "Hexagonal Architecture", "ports adapters dependency inversion clean architecture onion"),
            new Lesson("05-ddd", "Domain-Driven Design", "aggregate bounded context ubiquitous language value object entity repository"),
            new Lesson("06-modular-monolith", "Modular Monolith", "spring modulith modules boundaries archunit module verification"),
            new Lesson("07-events-and-outbox", "Events and the Outbox Pattern", "transactional outbox at-least-once idempotency kafka publishing dual write"),
            new Lesson("08-cqrs", "CQRS", "command query separation read model write model projections"),
            new Lesson("09-event-sourcing", "Event Sourcing", "event store append-only optimistic concurrency fold rehydration snapshots projections dcb"),
            new Lesson("10-sagas", "Sagas", "orchestration choreography compensation temporal long-running process"),
            new Lesson("11-microservices", "Microservices", "service boundaries resilience circuit breaker contract testing distributed"),
            new Lesson("12-testing-strategy", "Testing Strategy", "test pyramid honeycomb integration tests mutation testing pitest testcontainers"),
            new Lesson("13-bdd", "Behaviour-Driven Development", "cucumber gherkin given when then specification by example living documentation"),
            new Lesson("14-virtual-threads", "Virtual Threads", "loom virtual threads structured concurrency pinning scoped values throughput"),
            new Lesson("15-production-readiness", "Production Readiness", "observability opentelemetry metrics tracing resilience health graceful shutdown"),
            new Lesson("16-ai-backend", "AI in the Backend", "spring ai rag embeddings vector store pgvector chunking tool calling structured output mcp"));

    private final AtomicInteger invocations = new AtomicInteger();

    /**
     * Step 5: annotate this method so the model can call it.
     *
     * <pre>{@code
     * @Tool(description = "Find which numbered lesson of the training repository covers a topic")
     * }</pre>
     */
    public String whichLessonCovers(String topic) {
        this.invocations.incrementAndGet();
        String needle = topic == null ? "" : topic.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            return "No topic given.";
        }
        var matches = LESSONS.stream()
                .filter(lesson -> lesson.topics().contains(needle)
                        || lesson.title().toLowerCase(Locale.ROOT).contains(needle)
                        || lesson.slug().contains(needle))
                .toList();
        if (matches.isEmpty()) {
            var words = needle.split("\\s+");
            matches = LESSONS.stream()
                    .filter(lesson -> java.util.Arrays.stream(words)
                            .anyMatch(word -> word.length() >= 4 && lesson.topics().contains(word)))
                    .toList();
        }
        if (matches.isEmpty()) {
            return "No lesson in this repository covers '" + topic + "'.";
        }
        return matches.stream()
                .map(lesson -> lesson.slug() + " (" + lesson.title() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    public int invocationCount() {
        return this.invocations.get();
    }

    public void resetInvocationCount() {
        this.invocations.set(0);
    }

    public static List<Lesson> lessons() {
        return LESSONS;
    }
}
