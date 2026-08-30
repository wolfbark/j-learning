package dev.vlearning.ledger.application;

import java.util.List;

import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.domain.AccountState;

/**
 * What the application layer needs from persistence: give me current state (plus the
 * version it was derived from, for the optimistic append), and append new facts. HOW state
 * is obtained — full replay, or snapshot + tail (step 5) — is an implementation detail.
 */
public interface AccountRepository {

    Loaded load(String accountId);

    void append(String accountId, long expectedVersion, List<AccountEvent> newEvents);

    /** State plus the stream version it reflects — decide against this, append at this. */
    record Loaded(AccountState state, long version) {
    }
}
