package dev.vlearning.reliability.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres for the whole run, started lazily by whichever step 6 test class
 * loads first. Steps 1–5 and 7 need no database and never start it.
 */
public abstract class PostgresTestBase {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public record Call(boolean ok, Duration took, String error) {
    }

    public record Outcomes(List<Call> calls) {

        public long failures() {
            return calls.stream().filter(call -> !call.ok()).count();
        }

        public Duration slowest() {
            return calls.stream().map(Call::took).max(Duration::compareTo).orElse(Duration.ZERO);
        }

        public boolean anyErrorMentions(String... fragments) {
            return calls.stream().filter(call -> !call.ok()).anyMatch(call -> {
                String message = call.error() == null ? "" : call.error().toLowerCase();
                for (String fragment : fragments) {
                    if (message.contains(fragment.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        public String toString() {
            return "calls=%d failures=%d slowest=%dms".formatted(calls.size(), failures(),
                    slowest().toMillis());
        }
    }

    /** Fire {@code concurrency} report aggregates at once and record what happened to each. */
    protected static Outcomes driveConcurrently(int concurrency, Consumer<String> aggregate) {
        try (var pool = Executors.newFixedThreadPool(concurrency)) {
            var futures = IntStream.range(0, concurrency)
                    .mapToObj(i -> pool.submit(() -> {
                        long startedAt = System.nanoTime();
                        try {
                            aggregate.accept("R-" + i);
                            return new Call(true, elapsed(startedAt), null);
                        } catch (Exception e) {
                            return new Call(false, elapsed(startedAt), e.getMessage());
                        }
                    }))
                    .toList();
            return new Outcomes(futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    return new Call(false, Duration.ZERO, e.getMessage());
                }
            }).toList());
        }
    }

    private static Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }
}
