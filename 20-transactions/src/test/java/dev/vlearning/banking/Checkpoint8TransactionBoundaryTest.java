package dev.vlearning.banking;

import java.sql.SQLTransientConnectionException;

import dev.vlearning.banking.pool.EnrichmentService;
import dev.vlearning.banking.support.Concurrently;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 8: the transaction boundary is a capacity decision.
 *
 * <p>Four connections, thirty-two callers, and a 400 ms fraud check. Whether
 * that fraud check happens inside or outside the transaction is the difference
 * between an outage and a shrug — and the error you get when it goes wrong
 * accuses the database, which is innocent.
 */
@Disabled("Checkpoint 8 — enable when you start step 8")
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=2000"
})
class Checkpoint8TransactionBoundaryTest extends AbstractIntegrationTest {

    private static final int CALLERS = 32;
    private static final long EACH = 100;

    @Autowired
    EnrichmentService enrichment;

    @Test
    void slowWorkInsideTheTransaction_starvesThePool() {
        var outcome = Concurrently.run(CALLERS, worker -> enrichment.depositWithFraudCheck(ADA_CHECKING, EACH));

        assertThat(outcome.failuresOfType(CannotCreateTransactionException.class))
                .as("callers time out waiting for a connection that a sleeping transaction is holding")
                .isPositive();

        // Read the message you would be paged with. It names Hikari and a
        // timeout; it says nothing about the fraud check that is the actual
        // cause, and nothing here is the database's fault.
        assertThat(outcome.failures().getFirst())
                .rootCause()
                .isInstanceOf(SQLTransientConnectionException.class)
                .hasMessageContaining("Connection is not available, request timed out");
    }

    @Test
    void slowWorkOutsideTheTransaction_everyoneGetsThrough() {
        var outcome = Concurrently.run(CALLERS, worker ->
                enrichment.depositWithFraudCheckOutsideTransaction(ADA_CHECKING, EACH));

        assertThat(outcome.failures()).isEmpty();
        assertThat(balance(ADA_CHECKING))
                .as("same four connections, same fraud checks, no queue")
                .isEqualTo(5000 + CALLERS * EACH);
    }
}
