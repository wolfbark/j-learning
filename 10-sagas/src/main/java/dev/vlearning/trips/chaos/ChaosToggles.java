package dev.vlearning.trips.chaos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * One-shot failure injection per simulated service. Two flavors, and the
 * difference between them IS the lesson:
 *
 * <ul>
 *   <li><b>fail-next</b> — the service answers, but with a rejection event
 *       ({@code HotelRejected}, …). A loud business failure: something can react.</li>
 *   <li><b>drop-next</b> — the service swallows the next command without a
 *       reply, as if it were down or the message timed out. A silent failure:
 *       nothing reacts, the saga just… stops. Step 3 and checkpoint 4 live here.</li>
 * </ul>
 *
 * Each toggle disarms itself after firing once, so the retry that follows in a
 * test (or your curl session) succeeds.
 */
@Component
public class ChaosToggles {

    public static final Set<String> SERVICES = Set.of("flight", "hotel", "payment");

    private final Map<String, AtomicBoolean> failNext =
            Map.of("flight", new AtomicBoolean(), "hotel", new AtomicBoolean(), "payment", new AtomicBoolean());
    private final Map<String, AtomicBoolean> dropNext =
            Map.of("flight", new AtomicBoolean(), "hotel", new AtomicBoolean(), "payment", new AtomicBoolean());

    public void failNext(String service) {
        toggle(failNext, service).set(true);
    }

    public void dropNext(String service) {
        toggle(dropNext, service).set(true);
    }

    /** True exactly once after {@link #failNext} was armed for this service. */
    public boolean consumeFailNext(String service) {
        return toggle(failNext, service).getAndSet(false);
    }

    /** True exactly once after {@link #dropNext} was armed for this service. */
    public boolean consumeDropNext(String service) {
        return toggle(dropNext, service).getAndSet(false);
    }

    public void reset() {
        failNext.values().forEach(flag -> flag.set(false));
        dropNext.values().forEach(flag -> flag.set(false));
    }

    private static AtomicBoolean toggle(Map<String, AtomicBoolean> toggles, String service) {
        var flag = toggles.get(service);
        if (flag == null) {
            throw new IllegalArgumentException("Unknown service '%s' — one of %s".formatted(service, SERVICES));
        }
        return flag;
    }
}
