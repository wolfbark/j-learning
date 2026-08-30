package dev.vlearning.ticketing.support;

import org.springframework.stereotype.Component;

/**
 * A test-controlled pause point, exactly as in {@code 20-transactions}: it turns
 * an interleaving that is merely possible into one that is certain. No-op in
 * production. A teaching device, not a pattern.
 */
@Component
public class Interleaving {

    private static final Runnable NOTHING = () -> { };

    private volatile Runnable afterRead = NOTHING;

    public void afterRead() {
        afterRead.run();
    }

    public void armAfterRead(Runnable hook) {
        this.afterRead = hook;
    }

    public void reset() {
        this.afterRead = NOTHING;
    }
}
