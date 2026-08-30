package dev.vlearning.banking.support;

import org.springframework.stereotype.Component;

/**
 * A test-controlled pause point, in main code for the same reason
 * {@code 07-events-and-outbox} has a chaos monkey: races that only show up
 * "sometimes under load" teach nothing. Armed from a test, this turns an
 * interleaving that is merely <em>possible</em> into one that is
 * <em>certain</em> — the anomaly happens on every run, on your laptop.
 *
 * <p>In production the hook is a no-op. Nothing here changes behaviour; it only
 * decides <em>when</em> the second transaction gets its turn.
 */
@Component
public class Interleaving {

    private static final Runnable NOTHING = () -> { };

    private volatile Runnable afterRead = NOTHING;

    /** Called by services between "read the current value" and "write the new one". */
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
