package dev.vlearning.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * ENABLED and green from the start: every business rule, tested with values in and values
 * out. No database, no container, no Spring, no mocks — because decide and evolve are pure
 * functions. This is the file to read for step 1; the rest of the project never adds
 * another business rule, only machinery to persist what these functions produce.
 */
class AccountDeciderTest {

    private static final String ID = "acc-1";

    private static AccountState stateAfter(AccountEvent... history) {
        return AccountDecider.fold(AccountState.EMPTY, List.of(history));
    }

    @Nested
    class Opening {

        @Test
        void openingANewAccountEmitsAccountOpened() {
            var events = AccountDecider.decide(new OpenAccount(ID, "Ada"), AccountState.EMPTY);

            assertThat(events).containsExactly(new AccountOpened(ID, "Ada"));
        }

        @Test
        void openingTwiceIsRejected() {
            var state = stateAfter(new AccountOpened(ID, "Ada"));

            assertThatThrownBy(() -> AccountDecider.decide(new OpenAccount(ID, "Ada"), state))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    class DepositsAndWithdrawals {

        private final AccountState open = stateAfter(
                new AccountOpened(ID, "Ada"),
                new MoneyDeposited(ID, 100_00, "salary"));

        @Test
        void depositEmitsMoneyDeposited() {
            var events = AccountDecider.decide(new Deposit(ID, 25_00, "gift"), open);

            assertThat(events).containsExactly(new MoneyDeposited(ID, 25_00, "gift"));
        }

        @Test
        void withdrawalWithinBalanceEmitsMoneyWithdrawn() {
            var events = AccountDecider.decide(new Withdraw(ID, 40_00, "rent"), open);

            assertThat(events).containsExactly(new MoneyWithdrawn(ID, 40_00, "rent"));
        }

        @Test
        void withdrawingTheExactBalanceIsAllowed() {
            var events = AccountDecider.decide(new Withdraw(ID, 100_00, "all of it"), open);

            assertThat(events).containsExactly(new MoneyWithdrawn(ID, 100_00, "all of it"));
        }

        @Test
        void overdraftIsRejected() {
            assertThatThrownBy(() -> AccountDecider.decide(new Withdraw(ID, 100_01, "too much"), open))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("Overdraft");
        }

        @Test
        void nonPositiveAmountsAreRejected() {
            assertThatThrownBy(() -> AccountDecider.decide(new Deposit(ID, 0, "nothing"), open))
                    .isInstanceOf(CommandRejectedException.class);
            assertThatThrownBy(() -> AccountDecider.decide(new Withdraw(ID, -5, "negative"), open))
                    .isInstanceOf(CommandRejectedException.class);
        }

        @Test
        void operationsOnANonExistentAccountAreRejected() {
            assertThatThrownBy(() -> AccountDecider.decide(new Deposit(ID, 10_00, "x"), AccountState.EMPTY))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("does not exist");
        }
    }

    @Nested
    class Closing {

        @Test
        void closingWithZeroBalanceEmitsAccountClosed() {
            var state = stateAfter(
                    new AccountOpened(ID, "Ada"),
                    new MoneyDeposited(ID, 50_00, "in"),
                    new MoneyWithdrawn(ID, 50_00, "out"));

            var events = AccountDecider.decide(new CloseAccount(ID), state);

            assertThat(events).containsExactly(new AccountClosed(ID));
        }

        @Test
        void closingWithNonZeroBalanceIsRejected() {
            var state = stateAfter(new AccountOpened(ID, "Ada"), new MoneyDeposited(ID, 1, "penny"));

            assertThatThrownBy(() -> AccountDecider.decide(new CloseAccount(ID), state))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("non-zero balance");
        }

        @Test
        void anyOperationOnAClosedAccountIsRejected() {
            var closed = stateAfter(new AccountOpened(ID, "Ada"), new AccountClosed(ID));

            assertThatThrownBy(() -> AccountDecider.decide(new Deposit(ID, 10_00, "late"), closed))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("closed");
            assertThatThrownBy(() -> AccountDecider.decide(new Withdraw(ID, 10_00, "late"), closed))
                    .isInstanceOf(CommandRejectedException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    class Folding {

        @Test
        void stateIsTheLeftFoldOfHistory() {
            var state = stateAfter(
                    new AccountOpened(ID, "Ada"),
                    new MoneyDeposited(ID, 100_00, "salary"),
                    new MoneyWithdrawn(ID, 30_00, "rent"),
                    new MoneyDeposited(ID, 5_00, "refund"));

            assertThat(state).isEqualTo(new AccountState(ID, "Ada", 75_00, Status.OPEN));
        }

        @Test
        void foldingNoEventsLeavesTheEmptyState() {
            assertThat(stateAfter()).isEqualTo(AccountState.EMPTY);
        }

        @Test
        void decidingIsDeterministic() {
            // Same command, same state -> same events. Purity is what lets the event store
            // retry a decision after a ConcurrencyException without fear.
            var first = AccountDecider.decide(new OpenAccount(ID, "Ada"), AccountState.EMPTY);
            var second = AccountDecider.decide(new OpenAccount(ID, "Ada"), AccountState.EMPTY);

            assertThat(first).isEqualTo(second);
        }
    }
}
