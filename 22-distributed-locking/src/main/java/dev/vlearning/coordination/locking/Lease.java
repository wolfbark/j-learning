package dev.vlearning.coordination.locking;

import java.time.Instant;

/**
 * Permission to act, for a while.
 *
 * <p>A lease is not a lock. A lock is held until released, which means a holder
 * that dies holds it forever. A lease expires on its own, which is the only
 * property that survives a process disappearing — and, as step 4 shows, the
 * property that makes it possible for two workers to believe they hold it at
 * once.
 *
 * @param fencingToken monotonically increasing, one per successful acquisition.
 *                     Meaningless until somebody checks it — step 5.
 */
public record Lease(String name, String owner, Instant until, long fencingToken) {
}
