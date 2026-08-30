package dev.vlearning.banking.account;

/**
 * One account of one customer. Balances are whole cents held in a {@code long} —
 * money in a float is its own kind of lost update.
 */
public record Account(long id, String customer, Kind kind, long balance) {

    public enum Kind { CHECKING, SAVINGS }
}
