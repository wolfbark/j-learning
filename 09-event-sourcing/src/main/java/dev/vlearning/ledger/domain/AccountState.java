package dev.vlearning.ledger.domain;

/**
 * Current state of one account — derived, never stored as the source of truth. It exists
 * only as the left-fold of the account's events and is exactly as rich as the decide
 * function needs it to be: balance and status, nothing more. If a decision never reads a
 * field, the state doesn't need to carry it.
 */
public record AccountState(String accountId, String owner, long balanceCents, Status status) {

    public enum Status { NOT_OPENED, OPEN, CLOSED }

    /** The state of a stream with no events: the account does not exist yet. */
    public static final AccountState EMPTY = new AccountState(null, null, 0L, Status.NOT_OPENED);

    public AccountState withBalance(long newBalanceCents) {
        return new AccountState(accountId, owner, newBalanceCents, status);
    }
}
