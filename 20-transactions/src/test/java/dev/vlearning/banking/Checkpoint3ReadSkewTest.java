package dev.vlearning.banking;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.vlearning.banking.report.ReportingService;
import dev.vlearning.banking.support.DbSession.Isolation;
import dev.vlearning.banking.transfer.TransferService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: read skew and phantoms — anomalies that need no write of your own.
 *
 * <p>The first two tests are raw SQL and pass whatever you do to the services:
 * they are there to be read. The last one is red until {@link ReportingService}
 * asks for a snapshot that lasts the whole transaction.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3ReadSkewTest extends AbstractIntegrationTest {

    @Autowired
    ReportingService reports;

    @Autowired
    TransferService transfers;

    @Test
    void readCommitted_twoQueriesSeeTwoDifferentWorlds() {
        try (var reader = session("reader"); var writer = session("writer")) {
            reader.begin(Isolation.READ_COMMITTED);
            writer.begin(Isolation.READ_COMMITTED);

            long checking = reader.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING);

            writer.update("UPDATE account SET balance = balance - 5000 WHERE id = ?", ADA_CHECKING);
            writer.update("UPDATE account SET balance = balance + 5000 WHERE id = ?", ADA_SAVINGS);
            writer.commit();

            long savings = reader.queryLong("SELECT balance FROM account WHERE id = ?", ADA_SAVINGS);

            assertThat(checking + savings)
                    .as("Ada has 10 000; the report says she has 15 000, and every row it read was committed")
                    .isEqualTo(15_000);
            reader.commit();
        }
    }

    @Test
    void repeatableRead_theSnapshotIsTakenOnceAndPhantomsCannotAppear() {
        try (var reader = session("reader"); var writer = session("writer")) {
            reader.begin(Isolation.REPEATABLE_READ);
            // The snapshot is taken at the first statement, not at BEGIN.
            long before = reader.queryLong("SELECT count(*) FROM account WHERE customer = ?", ADA);

            writer.begin(Isolation.READ_COMMITTED);
            writer.update("INSERT INTO account (id, customer, kind, balance) VALUES (?, ?, 'SAVINGS', 700)",
                    42L, ADA);
            writer.update("UPDATE account SET balance = balance - 5000 WHERE id = ?", ADA_CHECKING);
            writer.commit();

            assertThat(reader.queryLong("SELECT count(*) FROM account WHERE customer = ?", ADA))
                    .as("no phantom row appears mid-transaction")
                    .isEqualTo(before);
            assertThat(reader.queryLong("SELECT sum(balance) FROM account WHERE customer = ?", ADA))
                    .as("and the balances are the ones the snapshot froze")
                    .isEqualTo(10_000);
            reader.commit();

            // Outside the transaction, of course, the writer's work is there.
            assertThat(combined(ADA)).isEqualTo(5700);
        }
    }

    @Test
    void theStatementIsInternallyConsistent() throws Exception {
        var checkingWasRead = new CountDownLatch(1);
        var transferCommitted = new CountDownLatch(1);
        interleaving.armAfterRead(() -> {
            checkingWasRead.countDown();
            try {
                if (!transferCommitted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the transfer never committed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.ofVirtual().start(() -> {
            try {
                checkingWasRead.await();
                interleaving.reset();
                transfers.transfer(ADA_CHECKING, ADA_SAVINGS, 5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                transferCommitted.countDown();
            }
        });

        var statement = reports.statementFor(ADA);

        assertThat(statement.total())
                .as("a transfer between her own accounts cannot change what Ada owns")
                .isEqualTo(10_000);
    }
}
