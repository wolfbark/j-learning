package dev.vlearning.ledger.domain;

/**
 * The requests. A command is an intent directed at one account — imperative mood, may be
 * rejected. Contrast with {@link AccountEvent}: past tense, already true, never rejected.
 */
public sealed interface AccountCommand {

    String accountId();

    record OpenAccount(String accountId, String owner) implements AccountCommand {
    }

    record Deposit(String accountId, long amountCents, String description) implements AccountCommand {
    }

    record Withdraw(String accountId, long amountCents, String description) implements AccountCommand {
    }

    record CloseAccount(String accountId) implements AccountCommand {
    }

    // Step 6 adds ChargeFee and RefundFee.
}
