package dev.vlearning.ledger.eventstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.PostgresTestBase;
import dev.vlearning.ledger.application.AccountService;
import dev.vlearning.ledger.application.EventSourcedAccountRepository;
import dev.vlearning.ledger.domain.AccountCommand.Deposit;
import dev.vlearning.ledger.domain.AccountCommand.OpenAccount;
import dev.vlearning.ledger.domain.AccountCommand.Withdraw;
import dev.vlearning.ledger.domain.AccountEvent.AccountOpened;
import dev.vlearning.ledger.domain.AccountEvent.MoneyDeposited;
import dev.vlearning.ledger.domain.AccountState.Status;

/**
 * CHECKPOINT 3 — read + rehydration, and the punchline of the first half of the lesson:
 * state lives in the database as EVENTS, so a brand-new process folds its way back to
 * exactly where the old one stopped. "Restart" here is simulated the honest way — a
 * completely separate object graph over the same database.
 *
 * When this is green, flip {@code ledger.event-store=postgres} in application.properties
 * (this test forces it via properties, so it is green either way) and re-run
 * {@code LedgerApiSmokeTest}: same behavior, real durability.
 */
@SpringBootTest(properties = "ledger.event-store=postgres")
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3RehydrationTest extends PostgresTestBase {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventSerde serde;

    @Autowired
    AccountService service;

    @Test
    void whatOneStoreInstanceWritesAnotherReadsBack() {
        var id = "cp3-" + UUID.randomUUID();
        var writer = new PostgresEventStore(jdbc, serde);
        var reader = new PostgresEventStore(jdbc, serde);

        writer.append(id, 0, List.of(
                new AccountOpened(id, "Ada"),
                new MoneyDeposited(id, 100_00, "salary")));

        var stored = reader.readStream(id);

        assertThat(stored).extracting(StoredEvent::version).containsExactly(1L, 2L);
        assertThat(stored).extracting(StoredEvent::event).containsExactly(
                new AccountOpened(id, "Ada"),
                new MoneyDeposited(id, 100_00, "salary"));
        assertThat(stored.getFirst().occurredAt()).isNotNull();
    }

    @Test
    void readStreamAfterVersionReturnsOnlyTheTail() {
        var id = "cp3-" + UUID.randomUUID();
        var store = new PostgresEventStore(jdbc, serde);
        store.append(id, 0, List.of(
                new AccountOpened(id, "Ada"),
                new MoneyDeposited(id, 1_00, "a"),
                new MoneyDeposited(id, 2_00, "b"),
                new MoneyDeposited(id, 3_00, "c")));

        assertThat(store.readStream(id, 2)).extracting(StoredEvent::version).containsExactly(3L, 4L);
        assertThat(store.readStream(id, 99)).isEmpty();
    }

    @Test
    void stateSurvivesARestart() {
        var id = "cp3-" + UUID.randomUUID();

        // "Before the restart": the Spring-wired service handles commands...
        service.handle(new OpenAccount(id, "Margaret"));
        service.handle(new Deposit(id, 500_00, "paycheck"));
        service.handle(new Withdraw(id, 120_00, "groceries"));

        // ...and "after the restart": a freshly built object graph, sharing nothing with the
        // service above except the database. No cache to warm, no state to migrate — the
        // fold IS the recovery procedure.
        var rebooted = new AccountService(
                new EventSourcedAccountRepository(new PostgresEventStore(jdbc, serde)));

        var view = rebooted.get(id);
        assertThat(view.owner()).isEqualTo("Margaret");
        assertThat(view.balanceCents()).isEqualTo(380_00);
        assertThat(view.status()).isEqualTo(Status.OPEN.name());
        assertThat(view.version()).isEqualTo(3);
    }
}
