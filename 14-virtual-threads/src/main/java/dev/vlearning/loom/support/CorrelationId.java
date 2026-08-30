package dev.vlearning.loom.support;

/**
 * Request-scoped context, 2015 edition: a {@link ThreadLocal}.
 *
 * <p>This works perfectly as long as the thread that sets the value is the
 * thread that reads it. The moment step 3 hands the work to *other* threads —
 * which is the entire point of a fan-out — the context vanishes, and your logs
 * lose the correlation id exactly when a request gets interesting.
 *
 * <p>Step 6 replaces this with {@code ScopedValue} (final in Java 25). Do not
 * "fix" it by passing the id as a parameter everywhere: that works, but it
 * teaches nothing about why Loom needed a new context primitive.
 */
public final class CorrelationId {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() {
    }

    public static void set(String id) {
        CURRENT.set(id);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
