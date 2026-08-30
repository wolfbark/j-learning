package dev.vlearning.banking;

import dev.vlearning.banking.support.DbSession.Isolation;
import dev.vlearning.banking.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Green on checkout: the container starts, the schema loads, the seed data is
 * there, and a hand-driven session can see a committed change. If this fails,
 * fix the environment before starting step 1.
 */
class HarnessTest extends AbstractIntegrationTest {

    @Autowired
    TransferService transfers;

    @Test
    void seedDataIsInPlace() {
        assertThat(balance(ADA_CHECKING)).isEqualTo(5000);
        assertThat(combined(ADA)).isEqualTo(10_000);
    }

    @Test
    void aDepositCommitsAndIsVisibleToAnotherSession() {
        transfers.deposit(ADA_CHECKING, 250);

        try (var reader = session("reader")) {
            reader.begin(Isolation.READ_COMMITTED);
            assertThat(reader.queryLong("SELECT balance FROM account WHERE id = ?", ADA_CHECKING))
                    .isEqualTo(5250);
        }
    }

    @Test
    void sessionsRunOnSeparateConnectionsAndCanBlockEachOther() {
        try (var t1 = session("T1"); var t2 = session("T2")) {
            t1.begin(Isolation.READ_COMMITTED);
            t2.begin(Isolation.READ_COMMITTED);

            t1.update("UPDATE account SET balance = balance + 1 WHERE id = ?", ADA_CHECKING);
            var blocked = t2.updateLater("UPDATE account SET balance = balance + 1 WHERE id = ?", ADA_CHECKING);

            assertThat(blocked.isBlocked())
                    .as("a second write to a locked row waits for the first transaction to end")
                    .isTrue();

            t1.commit();
            assertThat(blocked.await()).isEqualTo(1);
            t2.commit();
        }
        assertThat(balance(ADA_CHECKING)).isEqualTo(5002);
    }
}
