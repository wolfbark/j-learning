package dev.vlearning.loom.aggregate;

import java.util.Map;
import java.util.UUID;

import dev.vlearning.loom.downstream.DownstreamService;
import dev.vlearning.loom.pool.ScarcePool;
import dev.vlearning.loom.support.CorrelationId;
import dev.vlearning.loom.support.RequestConcurrencyFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AggregatorController {

    private final AggregatorService aggregator;
    private final DownstreamService downstream;
    private final ScarcePool pool;
    private final RequestConcurrencyFilter requests;

    public AggregatorController(AggregatorService aggregator, DownstreamService downstream, ScarcePool pool,
                                RequestConcurrencyFilter requests) {
        this.aggregator = aggregator;
        this.downstream = downstream;
        this.pool = pool;
        this.requests = requests;
    }

    @GetMapping("/customers/{customerId}")
    public CustomerView customer(@PathVariable String customerId,
                                 @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId)
            throws Exception {
        CorrelationId.set(correlationId != null ? correlationId : UUID.randomUUID().toString());
        try {
            return aggregator.load(customerId);
        } finally {
            CorrelationId.clear();
        }
    }

    @GetMapping("/customers/{customerId}/with-database")
    public CustomerView customerWithDatabase(@PathVariable String customerId,
                                             @RequestHeader(value = "X-Correlation-Id", required = false)
                                             String correlationId) throws Exception {
        CorrelationId.set(correlationId != null ? correlationId : UUID.randomUUID().toString());
        try {
            return aggregator.loadWithDatabase(customerId);
        } finally {
            CorrelationId.clear();
        }
    }

    /** Instrumentation endpoints — the lesson's dashboard. */
    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        return Map.of(
                "requestPeakConcurrency", requests.meter().peak(),
                "downstreamPeakConcurrency", downstream.meter().peak(),
                "downstreamCalls", downstream.meter().total(),
                "downstreamCancellations", downstream.cancellations(),
                "downstreamDelayMillis", downstream.delayMillis(),
                "poolSize", pool.size(),
                "poolPeakConcurrentUse", pool.peakConcurrentUse(),
                "poolAverageHoldMillis", pool.averageHoldTime().toMillis());
    }

    @PostMapping("/diagnostics/reset")
    public Map<String, String> reset() {
        downstream.reset();
        pool.reset();
        requests.meter().reset();
        return Map.of("status", "reset");
    }

    @PostMapping("/chaos/pricing")
    public Map<String, Object> failPricing(@RequestParam boolean fail) {
        downstream.failPricing(fail);
        return Map.of("pricingFails", fail);
    }
}
