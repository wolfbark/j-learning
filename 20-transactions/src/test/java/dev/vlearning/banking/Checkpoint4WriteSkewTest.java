package dev.vlearning.banking;

import java.util.concurrent.CyclicBarrier;

import dev.vlearning.banking.overdraft.OverdraftService;
import dev.vlearning.banking.support.Concurrently;
import dev.vlearning.banking.support.DbSession.Isolation;
import dev.vlearning.banking.support.DbSession.SessionException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 4: write skew — the anomaly that snapshot isolation cannot see.
 *
 * <p>Two withdrawals, each checking the same rule against the same snapshot,
 * each writing a <em>different</em> row. No write-write conflict exists for the
 * database to serialise, so REPEATABLE READ lets both through and the invariant
 * they were both checking ends up violated.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4WriteSkewTest extends AbstractIntegrationTest {

    @Autowired
    OverdraftService overdraft;

    @Test
    void repeatableRead_letsBothWithdrawalsThroughAndBreaksTheInvariant() {
        try (var t1 = session("T1"); var t2 = session("T2")) {
            t1.begin(Isolation.REPEATABLE_READ);
            t2.begin(Isolation.REPEATABLE_READ);

            // Both check the rule: 10 000 combined, withdrawing 6 000 leaves 4 000. Fine.
            assertThat(t1.queryLong("SELECT sum(balance) FROM account WHERE customer = ?", ADA)).isEqualTo(10_000);
            assertThat(t2.queryLong("SELECT sum(balance) FROM account WHERE customer = ?", ADA)).isEqualTo(10_000);

            t1.update("UPDATE account SET balance = balance - 6000 WHERE id = ?", ADA_CHECKING);
            t2.update("UPDATE account SET balance = balance - 6000 WHERE id = ?", ADA_SAVINGS);

            t1.commit();
            t2.commit();
        }

        assertThat(combined(ADA))
                .as("both transactions were individually correct; together they overdrew by 2 000")
                .isEqualTo(-2000);
    }

    @Test
    void serializable_refusesTheSecondCommit() {
        try (var t1 = session("T1"); var t2 = session("T2")) {
            t1.begin(Isolation.SERIALIZABLE);
            t2.begin(Isolation.SERIALIZABLE);

            t1.queryLong("SELECT sum(balance) FROM account WHERE customer = ?", ADA);
            t2.queryLong("SELECT sum(balance) FROM account WHERE customer = ?", ADA);

            t1.update("UPDATE account SET balance = balance - 6000 WHERE id = ?", ADA_CHECKING);
            t2.update("UPDATE account SET balance = balance - 6000 WHERE id = ?", ADA_SAVINGS);

            t1.commit();

            // Nothing blocked, nothing complained — until the second commit, where
            // Postgres finds the dependency cycle and refuses to be part of it.
            assertThatThrownBy(t2::commit)
                    .isInstanceOf(SessionException.class)
                    .extracting(e -> ((SessionException) e).sqlState())
                    .as("40001 — serialization_failure. Retry it; do not report it as a bug.")
                    .isEqualTo("40001");
        }

        assertThat(combined(ADA)).isEqualTo(4000);
    }

    @Test
    void theServiceKeepsTheInvariantUnderConcurrentWithdrawals() {
        var bothHaveChecked = new CyclicBarrier(2);
        interleaving.armAfterRead(() -> {
            try {
                bothHaveChecked.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var outcome = Concurrently.run(2, worker ->
                overdraft.withdraw(ADA, worker == 0 ? ADA_CHECKING : ADA_SAVINGS, 6000));

        assertThat(combined(ADA))
                .as("the linked-overdraft rule holds no matter how the two requests interleave")
                .isGreaterThanOrEqualTo(0);
        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.failures()).singleElement()
                .as("the loser is told to try again — SQLSTATE 40001, translated by Spring")
                .isInstanceOf(CannotAcquireLockException.class);
    }
}
