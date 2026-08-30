package dev.vlearning.production.gateway;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Counts calls that actually left this process, and how many were in flight at
 * once.
 *
 * <p>Both numbers are the point of several checkpoints: a retry that triples
 * your downstream load shows up here, and a circuit breaker's whole value is
 * that this counter *stops going up* while requests keep being answered.
 */
@Component
public class GatewayMeter {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();

    public void started() {
        calls.incrementAndGet();
        peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
    }

    public void finished() {
        inFlight.decrementAndGet();
    }

    public int calls() {
        return calls.get();
    }

    public int peakInFlight() {
        return peakInFlight.get();
    }

    public void reset() {
        calls.set(0);
        inFlight.set(0);
        peakInFlight.set(0);
    }
}
