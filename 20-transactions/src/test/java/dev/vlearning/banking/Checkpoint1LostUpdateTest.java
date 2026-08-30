package dev.vlearning.banking;

import java.util.concurrent.CyclicBarrier;

import dev.vlearning.banking.support.Concurrently;
import dev.vlearning.banking.support.DbSession.Isolation;
import dev.vlearning.banking.transfer.TransferService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1: the lost update, twice — once through the service, once as raw SQL you
 * can read statement by statement.
 *
 * <p>These tests PASS against the code as delivered. They pin the bug; they do
 * not fix it. Both deposits are committed by a real transaction at the database's
 * default isolation level, and one of them silently disappears anyway.
 */
class Checkpoint1LostUpdateTest extends AbstractIntegrationTest {

    @Autowired
    TransferService transfers;

    @Test
    void twoConcurrentDeposits_oneIsSilentlyLost() {
        var bothHaveRead = new CyclicBarrier(2);
        interleaving.armAfterRead(() -> {
            try {
                bothHaveRead.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        var outcome = Concurrently.run(2, worker -> transfers.deposit(ADA_CHECKING, 1000));

        assertThat(outcome.failures())
                .as("nothing fails — that is what makes this bug expensive")
                .isEmpty();
        assertThat(balance(ADA_CHECKING))
                .as("5000 + 1000 + 1000 should be 7000; one deposit was overwritten")
                .isEqualTo(6000);
    }

    @Test
    void theSameThingInSql_theSecondWriterBlocksAndStillLoses() {
        try (var t1 = session("T1"); var t2 = session("T2")) {
            t1.begin(Isolation.READ_COMMITTED);
            t2.begin(Isolation.READ_COMMITTED);

            long readByT1 = t1.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING);
            long readByT2 = t2.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING);
            assertThat(readByT1).isEqualTo(readByT2).isEqualTo(5000);

            t1.update("UPDATE account SET balance = ? WHERE id = ?", readByT1 + 1000, ADA_CHECKING);

            // T2 is not allowed to write the row while T1 holds it...
            var t2Write = t2.updateLater("UPDATE account SET balance = ? WHERE id = ?",
                    readByT2 + 1000, ADA_CHECKING);
            assertThat(t2Write.isBlocked()).isTrue();

            t1.commit();

            // ...and the moment it is allowed, it writes a number it computed
            // from a read that is now history. Blocking never made T2 re-read.
            t2Write.await();
            t2.commit();
        }

        assertThat(balance(ADA_CHECKING)).isEqualTo(6000);
    }

    @Test
    void readCommittedIsThePostgresDefault() {
        try (var s = session("default")) {
            assertThat(s.queryText("SHOW transaction_isolation")).isEqualTo("read committed");
        }
    }
}
