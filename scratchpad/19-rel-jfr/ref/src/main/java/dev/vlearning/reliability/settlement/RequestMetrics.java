package dev.vlearning.reliability.settlement;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Step 2's refactoring subject: a counter tagged with things that have no upper
 * bound. Every distinct combination of tag values is a separate time series in
 * your metrics backend, stored for the full retention period, and the count of
 * combinations is the <em>product</em> of the per-tag cardinalities.
 *
 * <p>Two of the three tags below are unbounded. The third is fine.
 */
@Component
public class RequestMetrics {

    public static final String REQUESTS = "settlement.requests";
    public static final String OUTCOMES = "settlement.outcomes";

    private final MeterRegistry registry;

    public RequestMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String userId, String orderId, int status) {
        Counter.builder(REQUESTS)
                .tag("path", "/settlements/{orderId}")
                .tag("status", status < 500 ? "2xx" : "5xx")
                .register(registry)
                .increment();
    }

    /**
     * The availability SLI's numerator and denominator. Two series, on purpose:
     * this is what a bounded tag looks like, and step 7 reads it.
     */
    public void recordOutcome(boolean success) {
        Counter.builder(OUTCOMES)
                .tag("outcome", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    /** How many distinct series the request counter has produced so far. */
    public int seriesCount() {
        return registry.find(REQUESTS).counters().size();
    }
}
