package dev.vlearning.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.application.AccountService;
import dev.vlearning.ledger.domain.AccountCommand.CloseAccount;
import dev.vlearning.ledger.domain.AccountCommand.Deposit;
import dev.vlearning.ledger.domain.AccountCommand.OpenAccount;
import dev.vlearning.ledger.domain.AccountCommand.Withdraw;
import dev.vlearning.ledger.projection.BalancesProjector;
import dev.vlearning.ledger.projection.BalancesReadModel;

/**
 * CHECKPOINT 4 — the balances read model. The projector consumes the global feed in
 * batches; tests drive it deterministically with {@code drain()} instead of waiting on the
 * poll loop. The core assertion is an equivalence: for every account, the projected row
 * must equal what folding the stream says.
 */
@SpringBootTest(properties = "ledger.event-store=postgres")
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4BalancesProjectionTest extends PostgresTestBase {

    @Autowired
    AccountService service;

    @Autowired
    BalancesProjector projector;

    @Autowired
    BalancesReadModel readModel;

    @Autowired
    JdbcClient jdbc;

    private void drain() {
        while (projector.runOnce() > 0) {
            // until caught up
        }
    }

    @Test
    void projectedBalancesMatchTheFold() {
        var ada = "cp4-" + UUID.randomUUID();
        var bob = "cp4-" + UUID.randomUUID();

        service.handle(new OpenAccount(ada, "Ada"));
        service.handle(new Deposit(ada, 300_00, "salary"));
        service.handle(new Withdraw(ada, 45_00, "books"));
        service.handle(new OpenAccount(bob, "Bob"));
        service.handle(new Deposit(bob, 20_00, "allowance"));
        service.handle(new Withdraw(bob, 20_00, "sweets"));
        service.handle(new CloseAccount(bob));

        drain();

        for (var accountId : new String[] { ada, bob }) {
            var folded = service.get(accountId);
            var projected = readModel.find(accountId).orElseThrow();
            assertThat(projected.balanceCents())
                    .as("read model must agree with the fold for %s", accountId)
                    .isEqualTo(folded.balanceCents());
            assertThat(projected.status()).isEqualTo(folded.status());
            assertThat(projected.owner()).isEqualTo(folded.owner());
        }
    }

    @Test
    void catchingUpTwiceChangesNothing() {
        var id = "cp4-" + UUID.randomUUID();
        service.handle(new OpenAccount(id, "Grace"));
        service.handle(new Deposit(id, 77_00, "prize"));

        drain();
        var afterFirst = readModel.find(id).orElseThrow();

        assertThat(projector.runOnce()).as("caught-up projector has nothing to do").isZero();
        assertThat(readModel.find(id).orElseThrow()).isEqualTo(afterFirst);
    }

    @Test
    void aCorruptedReadModelIsHealedByRebuildingFromHistory() {
        var id = "cp4-" + UUID.randomUUID();
        service.handle(new OpenAccount(id, "Barbara"));
        service.handle(new Deposit(id, 150_00, "grant"));
        drain();

        // Disaster strikes: someone UPDATEs a read model by hand.
        jdbc.sql("UPDATE account_balances SET balance_cents = 999999 WHERE account_id = :id")
                .param("id", id)
                .update();
        assertThat(readModel.find(id).orElseThrow().balanceCents()).isEqualTo(999999);

        // The events table is the truth; the table is just a cache. Throw it away.
        projector.rebuild();

        assertThat(readModel.find(id).orElseThrow().balanceCents()).isEqualTo(150_00);
    }
}
