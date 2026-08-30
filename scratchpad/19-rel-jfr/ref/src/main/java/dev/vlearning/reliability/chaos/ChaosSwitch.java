package dev.vlearning.reliability.chaos;

import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * The switch step 7's incident drill flips. Nothing here is clever: each mode
 * degrades exactly one dimension, and the drill is to name which one from
 * metrics and logs alone.
 */
@Component
public class ChaosSwitch {

    public enum Mode {
        /** Healthy. 10% of settlements are slow, which is the normal shape of this service. */
        NONE,
        /** The downstream got slow: the pathological tail grows from 10% to 60%. */
        SLOW_DEPENDENCY,
        /** The downstream started refusing: 80% of settlements fail. */
        FAILING_DEPENDENCY,
        /** Every report aggregate takes the pathological database path. */
        POOL_HOG,
        /** The render lock is held eight times longer than usual. */
        LOCK_STORM
    }

    private volatile Mode mode = Mode.NONE;

    public Mode mode() {
        return mode;
    }

    public void set(Mode mode) {
        this.mode = mode;
    }

    public void reset() {
        this.mode = Mode.NONE;
    }

    /** Share of settlements that take the slow path. */
    public double pathologicalShare() {
        return mode == Mode.SLOW_DEPENDENCY ? 0.60 : 0.10;
    }

    /** Deterministic per-order failure, so a drill is reproducible. */
    public boolean shouldFail(String orderId) {
        return mode == Mode.FAILING_DEPENDENCY && Math.floorMod(orderId.hashCode(), 5) != 0;
    }

    /** How long the report renderer holds its lock. */
    public Duration lockHold() {
        return mode == Mode.LOCK_STORM ? Duration.ofMillis(40) : Duration.ofMillis(5);
    }

    /** Share of report aggregates that hit the expensive query. */
    public double databasePathologicalShare() {
        return mode == Mode.POOL_HOG ? 1.0 : 0.2;
    }
}
