package dev.vlearning.ledger.eventstore;

/**
 * Somebody appended to the stream between our read and our write. The caller's decision was
 * made against stale state, so the write must not happen — reload, re-decide, retry (or give
 * up and return 409). This is optimistic concurrency: no locks held while thinking.
 */
public class ConcurrencyException extends RuntimeException {

    public ConcurrencyException(String streamId, long expectedVersion) {
        super("Concurrent append to stream '%s': expected version %d but the stream has moved on"
                .formatted(streamId, expectedVersion));
    }
}
