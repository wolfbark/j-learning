package dev.vlearning.ledger.domain;

/**
 * The facts. Everything that can ever be true about an account is expressed as one of these
 * immutable records — the same data-oriented style as project 01: a sealed interface whose
 * permitted records ARE the complete vocabulary, and every consumer switches over them
 * exhaustively. Add a new event type and the compiler walks you to every place that must
 * handle it.
 *
 * Events are named in the past tense because they already happened. They cannot fail and
 * cannot be rejected — rejection happens earlier, when a command is decided.
 *
 * Note what is NOT here: no timestamps, no version numbers. Those are storage metadata and
 * live in {@code StoredEvent} / the events table, not in the domain payload.
 */
public sealed interface AccountEvent {

    String accountId();

    record AccountOpened(String accountId, String owner) implements AccountEvent {
    }

    record MoneyDeposited(String accountId, long amountCents, String description) implements AccountEvent {
    }

    record MoneyWithdrawn(String accountId, long amountCents, String description) implements AccountEvent {
    }

    record AccountClosed(String accountId) implements AccountEvent {
    }

    // Step 6 adds FeeCharged and FeeRefunded here. The sealed interface is the registry:
    // EventSerde discovers types via getPermittedSubclasses(), and every switch over
    // AccountEvent will fail to compile until it handles the new facts. That is the point.
}
