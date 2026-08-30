package dev.vlearning.ledger.application;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("No such account: " + accountId);
    }
}
