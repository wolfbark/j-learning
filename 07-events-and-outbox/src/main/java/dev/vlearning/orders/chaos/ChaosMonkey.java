package dev.vlearning.orders.chaos;

import org.springframework.stereotype.Component;

/**
 * Failure injection for the dual-write demonstration. Tests arm a crash point
 * (or take the broker "down"), run the scenario, and reset afterwards.
 *
 * <p>In real life this role is played by a kill -9, an OOM, a redeploy or a
 * network partition — none of which schedule themselves around your two writes.
 */
@Component
public class ChaosMonkey {

    public enum CrashPoint {
        NONE,
        /** Crash after the event went to the broker, while the DB transaction is still open. */
        AFTER_SEND_BEFORE_COMMIT,
        /** Crash after the DB transaction committed, before the event goes to the broker. */
        AFTER_COMMIT_BEFORE_SEND
    }

    private volatile CrashPoint crashPoint = CrashPoint.NONE;
    private volatile boolean brokerDown = false;

    public void armCrash(CrashPoint point) {
        this.crashPoint = point;
    }

    public void breakBroker() {
        this.brokerDown = true;
    }

    public void healBroker() {
        this.brokerDown = false;
    }

    public void reset() {
        this.crashPoint = CrashPoint.NONE;
        this.brokerDown = false;
    }

    public CrashPoint crashPoint() {
        return crashPoint;
    }

    public boolean brokerDown() {
        return brokerDown;
    }

    /** Simulates the process dying right here. */
    public void crashNow() {
        throw new ChaosException("chaos monkey struck at " + crashPoint);
    }

    /** Crashes only if the given point is the one currently armed. */
    public void maybeCrash(CrashPoint point) {
        if (crashPoint == point) {
            crashNow();
        }
    }
}
