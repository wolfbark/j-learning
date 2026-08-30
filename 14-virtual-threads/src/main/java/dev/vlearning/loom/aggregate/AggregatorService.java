package dev.vlearning.loom.aggregate;

import dev.vlearning.loom.downstream.DownstreamService;
import dev.vlearning.loom.pool.ScarcePool;
import dev.vlearning.loom.support.CorrelationId;
import org.springframework.stereotype.Service;

/**
 * The subject of this lesson. Three independent downstream calls, made one
 * after another, because that is how everybody writes it the first time.
 *
 * <p>Nothing here is "wrong" in a correctness sense — and that is the point.
 * Sequential I/O is a *latency* bug that no test catches and no exception
 * reports. Steps 3 and 4 fix it; steps 2 and 5 deal with what happens when
 * hundreds of users hit it at once.
 */
@Service
public class AggregatorService {

    private final DownstreamService downstream;
    private final ScarcePool pool;

    public AggregatorService(DownstreamService downstream, ScarcePool pool) {
        this.downstream = downstream;
        this.pool = pool;
    }

    /**
     * Sequential fan-out: total latency is the SUM of three independent calls.
     * Steps 3 and 4 turn the sum into a max.
     */
    public CustomerView load(String customerId) throws Exception {
        long start = System.nanoTime();

        var profile = downstream.fetchProfile(customerId);
        var inventory = downstream.fetchInventory(customerId);
        var pricing = downstream.fetchPricing(customerId);

        return CustomerView.of(customerId, profile, inventory, pricing, CorrelationId.get(), elapsedMillis(start));
    }

    /**
     * The same view, but the customer's data is also persisted/read through the
     * connection pool — and the connection is borrowed for the WHOLE request,
     * including the three remote calls that do not need it.
     *
     * <p>This is one of the most common throughput bugs in real Spring
     * applications (an over-broad {@code @Transactional}, usually). With
     * platform threads it hides behind the thread pool; with virtual threads it
     * becomes THE bottleneck, loudly. Step 5 is about narrowing it.
     */
    public CustomerView loadWithDatabase(String customerId) throws Exception {
        long start = System.nanoTime();

        return pool.withConnection(connection -> {
            connection.runQuery();

            var profile = downstream.fetchProfile(customerId);
            var inventory = downstream.fetchInventory(customerId);
            var pricing = downstream.fetchPricing(customerId);

            connection.runQuery();

            return CustomerView.of(customerId, profile, inventory, pricing, CorrelationId.get(),
                    elapsedMillis(start));
        });
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
