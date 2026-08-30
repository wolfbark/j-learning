package dev.vlearning.banking.transfer;

/**
 * Thrown when a withdrawal would push the customer's <em>combined</em> balance
 * below zero. This is the invariant steps 4 and 5 are about: it spans two rows,
 * which is exactly the kind of rule that survives no isolation level below
 * SERIALIZABLE.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String customer, long combined, long amount) {
        super("%s has %d cents combined; cannot withdraw %d".formatted(customer, combined, amount));
    }
}
