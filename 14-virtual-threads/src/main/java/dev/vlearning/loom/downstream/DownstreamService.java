package dev.vlearning.loom.downstream;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import dev.vlearning.loom.support.ConcurrencyMeter;
import dev.vlearning.loom.support.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Three "remote" services that each take a fixed, honest amount of wall-clock
 * time and hold no CPU while doing it — i.e. the shape of almost every call a
 * business backend makes.
 *
 * <p>{@code Thread.sleep} is the right simulation here: since JEP 491 (Java 24)
 * it parks a virtual thread and releases the carrier, exactly like a socket
 * read would. Pre-24 this lesson would have needed real sockets to avoid
 * pinning artefacts — one reason virtual-thread benchmarks from 2023 are worth
 * re-running before you trust them.
 */
@Service
public class DownstreamService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamService.class);

    private final long delayMillis;
    private final ConcurrencyMeter meter = new ConcurrencyMeter();
    private final AtomicInteger cancellations = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> observedCorrelationIds = new ConcurrentLinkedQueue<>();
    private volatile boolean pricingFails;

    public DownstreamService(@Value("${loom.downstream-delay-ms:150}") long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public Profile fetchProfile(String customerId) throws InterruptedException {
        return call("profile", () -> new Profile(customerId, "Customer " + customerId, "GOLD"));
    }

    public Inventory fetchInventory(String customerId) throws InterruptedException {
        return call("inventory", () -> new Inventory(42, true));
    }

    /** The one that can be told to fail, for step 4's cancellation demonstration. */
    public Pricing fetchPricing(String customerId) throws InterruptedException {
        if (pricingFails) {
            // Fail *after* a beat, so the sibling calls are definitely in flight when
            // this one blows up — otherwise step 4 could pass without cancelling anything.
            Thread.sleep(delayMillis / 5);
            throw new DownstreamFailure("pricing service is down");
        }
        return call("pricing", () -> new Pricing("EUR", 199_00));
    }

    private <T> T call(String name, java.util.function.Supplier<T> result) throws InterruptedException {
        // What request context can this call see? On the request thread: the id.
        // On a thread forked by a fan-out: whatever the context mechanism managed
        // to carry across the boundary — which is the whole of step 6.
        observedCorrelationIds.add(String.valueOf(CorrelationId.get()));
        try {
            return meter.measure(() -> {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    // Structured concurrency cancels siblings by interrupting them.
                    // Counting that is how step 4 proves cancellation actually happened.
                    cancellations.incrementAndGet();
                    log.debug("{} call cancelled", name);
                    Thread.currentThread().interrupt();
                    throw e;
                }
                return result.get();
            });
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public long delayMillis() {
        return delayMillis;
    }

    public ConcurrencyMeter meter() {
        return meter;
    }

    public int cancellations() {
        return cancellations.get();
    }

    /** The request context every downstream call actually saw, in call order. */
    public List<String> observedCorrelationIds() {
        return List.copyOf(observedCorrelationIds);
    }

    public void failPricing(boolean fail) {
        this.pricingFails = fail;
    }

    public void reset() {
        meter.reset();
        cancellations.set(0);
        observedCorrelationIds.clear();
        pricingFails = false;
    }

    public record Profile(String customerId, String name, String tier) {}

    public record Inventory(int availableItems, boolean canShipToday) {}

    public record Pricing(String currency, int totalCents) {}

    public static class DownstreamFailure extends RuntimeException {
        public DownstreamFailure(String message) {
            super(message);
        }
    }
}
