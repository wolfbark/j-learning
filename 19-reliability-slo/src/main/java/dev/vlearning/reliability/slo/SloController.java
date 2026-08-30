package dev.vlearning.reliability.slo;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.vlearning.reliability.settlement.LatencyProfile;
import dev.vlearning.reliability.settlement.RequestMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two service level indicators computed from the meters this service already
 * has. The availability one works today. The latency one returns {@code null}
 * until step 3 gives the timer buckets to count — which is the whole point:
 * <em>an SLI you cannot compute is not an SLI</em>, and "the average was fine"
 * is not a substitute.
 */
@RestController
public class SloController {

    /** The promise: 99.9% of settlements succeed. */
    public static final double AVAILABILITY_OBJECTIVE = 0.999;
    /** The promise: 99% of settlements complete inside the latency SLO. */
    public static final double LATENCY_OBJECTIVE = 0.99;

    private final MeterRegistry registry;
    private final LatencyProfile latency;

    public SloController(MeterRegistry registry, LatencyProfile latency) {
        this.registry = registry;
        this.latency = latency;
    }

    @GetMapping("/slo")
    public Map<String, Object> slo() {
        double good = outcomes("success");
        double bad = outcomes("failure");
        double valid = good + bad;

        var body = new LinkedHashMap<String, Object>();
        body.put("validEvents", valid);
        body.put("goodEvents", good);
        body.put("availabilitySli", valid == 0 ? null : good / valid);
        body.put("availabilityObjective", AVAILABILITY_OBJECTIVE);
        body.put("latencySli", latencySli());
        body.put("latencyObjective", LATENCY_OBJECTIVE);
        body.put("latencySloMillis", LatencyProfile.LATENCY_SLO.toMillis());
        return body;
    }

    private double outcomes(String outcome) {
        var counter = registry.find(RequestMetrics.OUTCOMES).tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    /**
     * Share of settlements inside the latency SLO, read straight out of the
     * timer's histogram — no percentile estimation involved, which is exactly
     * why an SLO bucket is worth configuring. Caveat worth knowing: the bucket
     * counts live in a rolling window while {@code count()} is lifetime, so over
     * long runs this ratio drifts. For alerting, do this arithmetic in your
     * metrics backend over an explicit range.
     */
    private Double latencySli() {
        var snapshot = latency.timer().takeSnapshot();
        if (snapshot.count() == 0) {
            return null;
        }
        long sloNanos = LatencyProfile.LATENCY_SLO.toNanos();
        for (CountAtBucket bucket : snapshot.histogramCounts()) {
            if (Math.round(bucket.bucket()) == sloNanos) {
                return bucket.count() / (double) snapshot.count();
            }
        }
        return null;
    }
}
