package dev.vlearning.ledger.domain;

/**
 * A business rule said no. Thrown by decide, mapped to 422 at the API edge.
 * Distinct from {@code ConcurrencyException} (someone else got there first, 409):
 * one is a domain verdict, the other is an infrastructure race.
 */
public class CommandRejectedException extends RuntimeException {

    public CommandRejectedException(String message) {
        super(message);
    }
}
