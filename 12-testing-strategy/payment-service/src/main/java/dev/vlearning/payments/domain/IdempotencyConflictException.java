package dev.vlearning.payments.domain;

/**
 * The idempotency key was reused for a <em>different</em> request: HTTP 409.
 * Replaying the same request is fine (and returns the original payment); reusing
 * a key for a different amount is a client bug we must not paper over.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("idempotency key already used for a different request: " + key);
    }
}
