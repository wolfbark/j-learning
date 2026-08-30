package dev.vlearning.banking;

import java.util.concurrent.CyclicBarrier;

import dev.vlearning.banking.support.Concurrently;
import dev.vlearning.banking.support.DbSession.Isolation;
import dev.vlearning.banking.support.DbSession.SessionException;
import dev.vlearning.banking.transfer.TransferService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 2: two repairs for the same bug, with different costs.
 *
 * <p>Both keep the money right under sixteen concurrent depositors. The
 * difference is what they cost and what they can express: the atomic UPDATE is
 * free but can only do arithmetic the database can do, while the row lock
 * serialises everyone but lets you decide anything you like in Java in between.
 */
class Checkpoint2FixingLostUpdateTest extends AbstractIntegrationTest {

    private static final int DEPOSITORS = 16;
    private static final long EACH = 100;

    @Autowired
    TransferService transfers;

    @Test
    void atomicUpdate_keepsEveryDeposit() {
        var outcome = Concurrently.run(DEPOSITORS, worker -> transfers.depositAtomically(ADA_CHECKING, EACH));

        assertThat(outcome.failures()).isEmpty();
        assertThat(balance(ADA_CHECKING)).isEqualTo(5000 + DEPOSITORS * EACH);
    }

    @Test
    void selectForUpdate_keepsEveryDeposit() {
        var outcome = Concurrently.run(DEPOSITORS, worker -> transfers.depositWithRowLock(ADA_CHECKING, EACH));

        assertThat(outcome.failures()).isEmpty();
        assertThat(balance(ADA_CHECKING)).isEqualTo(5000 + DEPOSITORS * EACH);
    }

    @Test
    void repeatableRead_refusesTheSecondWriterRatherThanLosingItsUpdate() {
        try (var t1 = session("T1"); var t2 = session("T2")) {
            t1.begin(Isolation.REPEATABLE_READ);
            t2.begin(Isolation.REPEATABLE_READ);

            long readByT1 = t1.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING);
            long readByT2 = t2.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING);

            t1.update("UPDATE account SET balance = ? WHERE id = ?", readByT1 + EACH, ADA_CHECKING);
            var t2Write = t2.updateLater("UPDATE account SET balance = ? WHERE id = ?",
                    readByT2 + EACH, ADA_CHECKING);
            assertThat(t2Write.isBlocked()).isTrue();

            t1.commit();

            // The third fix: at REPEATABLE READ, Postgres will not let T2 write
            // over a row that changed under its snapshot. Nothing is lost — but
            // somebody has to catch this and run the whole thing again.
            assertThatThrownBy(t2Write::await)
                    .isInstanceOf(SessionException.class)
                    .extracting(e -> ((SessionException) e).sqlState())
                    .isEqualTo("40001");
        }

        assertThat(balance(ADA_CHECKING)).isEqualTo(5000 + EACH);
    }

    @Test
    void theUnfixedVersionKeepsExactlyOneOfEightDeposits() {
        var allHaveRead = new CyclicBarrier(8);
        interleaving.armAfterRead(() -> {
            try {
                allHaveRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        Concurrently.run(8, worker -> transfers.deposit(ADA_CHECKING, EACH));

        assertThat(balance(ADA_CHECKING))
                .as("eight readers, eight writers, one surviving deposit")
                .isEqualTo(5000 + EACH);
    }
}
