package dev.vlearning.loom.aggregate;

import dev.vlearning.loom.downstream.DownstreamService.Inventory;
import dev.vlearning.loom.downstream.DownstreamService.Pricing;
import dev.vlearning.loom.downstream.DownstreamService.Profile;

/**
 * What the endpoint returns. {@code servedByVirtualThread} and
 * {@code threadName} are not production fields — they are the lesson's
 * instrumentation, so a test (and you, with curl) can see what kind of thread
 * actually ran the request.
 */
public record CustomerView(
        String customerId,
        Profile profile,
        Inventory inventory,
        Pricing pricing,
        String correlationId,
        boolean servedByVirtualThread,
        String threadName,
        long elapsedMillis) {

    public static CustomerView of(String customerId, Profile profile, Inventory inventory, Pricing pricing,
                                  String correlationId, long elapsedMillis) {
        Thread current = Thread.currentThread();
        return new CustomerView(customerId, profile, inventory, pricing, correlationId,
                current.isVirtual(), current.toString(), elapsedMillis);
    }
}
