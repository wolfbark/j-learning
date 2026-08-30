package dev.vlearning.coordination.billing;

import java.time.Duration;

import dev.vlearning.coordination.locking.LeaseService;

/**
 * One pod.
 *
 * <p>Deliberately not a Spring bean: the tests create two of these to stand in
 * for two instances of your service, which is the situation every mechanism in
 * this project exists for. Two instances in one JVM are a weak simulation of two
 * JVMs — but they are strong enough to break a {@code synchronized} block, which
 * is step 1's entire point.
 */
public class BillingWorker {

    public static final String NIGHTLY_BILLING = "nightly-billing";

    private final Billing billing;
    private final LeaseService leases;
    private final String workerId;

    private Runnable pauseAfterTakingTheLock = () -> { };

    public BillingWorker(Billing billing, LeaseService leases, String workerId) {
        this.billing = billing;
        this.leases = leases;
        this.workerId = workerId;
    }

    public String workerId() {
        return workerId;
    }

    /**
     * What happens between taking the lock and using it.
     *
     * <p>In step 2 that is just "this worker is busy for a moment". In step 4 it
     * is a full GC, a hypervisor migration, a laptop lid, a container throttled
     * to nothing — a pause long enough to outlive the lease. The pause is
     * simulated; everything it causes is real. A no-op unless a test arms it.
     */
    public void pauseAfterTakingTheLock(Runnable pause) {
        this.pauseAfterTakingTheLock = pause;
    }

    /**
     * Step 1: guarded by the JVM, which is to say guarded against exactly one of
     * the things that can go wrong.
     */
    public synchronized void runUnprotected(String period) {
        billing.bill(period, workerId);
    }

    /** Step 2: only one worker gets past the advisory lock; the rest do nothing. */
    public boolean runWithAdvisoryLock(String period) {
        return billing.billIfLockAcquired(period, workerId, pauseAfterTakingTheLock);
    }

    /**
     * Steps 3 and 4: take a lease, then do the work. Note the gap between those
     * two sentences — that gap is the subject of step 4.
     */
    public boolean runWithLease(String period, Duration leaseDuration) {
        var lease = leases.tryAcquire(NIGHTLY_BILLING, workerId, leaseDuration);
        if (lease.isEmpty()) {
            return false;
        }
        pauseAfterTakingTheLock.run();
        billing.bill(period, workerId);
        return true;
    }

    /** Step 7: no coordination at all — just work that is safe to repeat. */
    public void runIdempotent(String period) {
        billing.billIdempotent(period, workerId);
    }

    /**
     * Step 5: the same, except the work carries proof of which lease it belongs
     * to, and the resource being protected checks it.
     */
    public boolean runWithFencedLease(String period, Duration leaseDuration) {
        var lease = leases.tryAcquire(NIGHTLY_BILLING, workerId, leaseDuration);
        if (lease.isEmpty()) {
            return false;
        }
        pauseAfterTakingTheLock.run();
        billing.billFenced(period, workerId, lease.get().fencingToken());
        return true;
    }
}
