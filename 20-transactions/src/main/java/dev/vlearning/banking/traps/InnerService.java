package dev.vlearning.banking.traps;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InnerService {

    /**
     * Propagation is REQUIRED by default: this does not open a transaction of its
     * own, it joins the caller's — and when it fails, it marks the caller's
     * transaction rollback-only.
     */
    @Transactional
    public void alwaysFails() {
        throw new IllegalStateException("inner step failed");
    }
    /**
     * Checkpoint 6c: fails without touching the caller's transaction. One
     * annotation attribute away from {@link #alwaysFails()}.
     */
    public void failsInItsOwnTransaction() {
        throw new UnsupportedOperationException(
                "Checkpoint 6c: run this in a transaction of its own, then fail");
    }
}
