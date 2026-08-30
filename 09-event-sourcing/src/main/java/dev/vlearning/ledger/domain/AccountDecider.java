package dev.vlearning.ledger.domain;

import java.util.List;

import dev.vlearning.ledger.domain.AccountCommand.CloseAccount;
import dev.vlearning.ledger.domain.AccountCommand.Deposit;
import dev.vlearning.ledger.domain.AccountCommand.OpenAccount;
import dev.vlearning.ledger.domain.AccountCommand.Withdraw;
import dev.vlearning.ledger.domain.AccountEvent.AccountClosed;
import dev.vlearning.ledger.domain.AccountEvent.AccountOpened;
import dev.vlearning.ledger.domain.AccountEvent.MoneyDeposited;
import dev.vlearning.ledger.domain.AccountEvent.MoneyWithdrawn;
import dev.vlearning.ledger.domain.AccountState.Status;

/**
 * The entire banking domain, as two pure functions:
 *
 * <pre>
 *   decide : (Command, State) -&gt; List&lt;Event&gt;     what new facts does this intent produce?
 *   evolve : (State, Event)   -&gt; State           what does this fact do to the state?
 * </pre>
 *
 * plus {@code fold}, which is just evolve repeated from EMPTY — replaying history.
 *
 * No I/O, no framework, no clock, no mutable fields. That is why every business rule in
 * this project is tested in {@code AccountDeciderTest} without a database, a container, or
 * a Spring context. The event store you build in steps 2–3 never touches these functions;
 * it only carries their inputs and outputs.
 */
public final class AccountDecider {

    private AccountDecider() {
    }

    /** Decide which events a command produces given current state — or reject it. */
    public static List<AccountEvent> decide(AccountCommand command, AccountState state) {
        return switch (command) {
            case OpenAccount c -> {
                if (state.status() != Status.NOT_OPENED) {
                    throw new CommandRejectedException("Account %s already exists".formatted(c.accountId()));
                }
                yield List.of(new AccountOpened(c.accountId(), c.owner()));
            }
            case Deposit c -> {
                requireOpen(state, "deposit");
                requirePositive(c.amountCents());
                yield List.of(new MoneyDeposited(c.accountId(), c.amountCents(), c.description()));
            }
            case Withdraw c -> {
                requireOpen(state, "withdraw");
                requirePositive(c.amountCents());
                if (c.amountCents() > state.balanceCents()) {
                    throw new CommandRejectedException(
                            "Overdraft rejected: balance is %d cents, requested %d".formatted(
                                    state.balanceCents(), c.amountCents()));
                }
                yield List.of(new MoneyWithdrawn(c.accountId(), c.amountCents(), c.description()));
            }
            case CloseAccount c -> {
                requireOpen(state, "close");
                if (state.balanceCents() != 0) {
                    throw new CommandRejectedException(
                            "Cannot close account with non-zero balance (%d cents) — withdraw first"
                                    .formatted(state.balanceCents()));
                }
                yield List.of(new AccountClosed(c.accountId()));
            }
        };
    }

    /** Apply one fact to the state. Total: an event is never rejected, it already happened. */
    public static AccountState evolve(AccountState state, AccountEvent event) {
        return switch (event) {
            case AccountOpened e -> new AccountState(e.accountId(), e.owner(), 0L, Status.OPEN);
            case MoneyDeposited e -> state.withBalance(state.balanceCents() + e.amountCents());
            case MoneyWithdrawn e -> state.withBalance(state.balanceCents() - e.amountCents());
            case AccountClosed e -> new AccountState(state.accountId(), state.owner(), state.balanceCents(),
                    Status.CLOSED);
        };
    }

    /** Replay: current state is nothing but the left-fold of history. */
    public static AccountState fold(AccountState from, List<AccountEvent> history) {
        var state = from;
        for (var event : history) {
            state = evolve(state, event);
        }
        return state;
    }

    private static void requireOpen(AccountState state, String operation) {
        switch (state.status()) {
            case NOT_OPENED -> throw new CommandRejectedException(
                    "Cannot %s: account does not exist".formatted(operation));
            case CLOSED -> throw new CommandRejectedException(
                    "Cannot %s: account is closed".formatted(operation));
            case OPEN -> { /* fine */ }
        }
    }

    private static void requirePositive(long amountCents) {
        if (amountCents <= 0) {
            throw new CommandRejectedException("Amount must be positive, was " + amountCents);
        }
    }
}
