package dev.vlearning.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.application.AccountService;
import dev.vlearning.ledger.application.PostgresSnapshotStore;
import dev.vlearning.ledger.application.SnapshottingAccountRepository;
import dev.vlearning.ledger.domain.AccountCommand.Deposit;
import dev.vlearning.ledger.domain.AccountCommand.OpenAccount;
import dev.vlearning.ledger.eventstore.EventSerde;
import dev.vlearning.ledger.eventstore.PostgresEventStore;
import dev.vlearning.ledger.support.CountingEventStore;

/**
 * CHECKPOINT 5 — snapshots, with the proof in numbers. The repository under test is built
 * by hand around a {@link CountingEventStore}, so the test can assert not just that
 * rehydration is CORRECT but that it read only the tail after the last snapshot.
 *
 * When green, flip {@code ledger.snapshots.enabled=true} — the enabled behavior tests must
 * stay green, because snapshots are an optimization, never a behavior change.
 */
@SpringBootTest(properties = "ledger.event-store=postgres")
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5SnapshotTest extends PostgresTestBase {

    private static final int SNAPSHOT_EVERY = 10;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventSerde serde;

    @Autowired
    PostgresSnapshotStore snapshotStore;

    CountingEventStore countingStore;
    AccountService service;

    @BeforeEach
    void wireByHand() {
        countingStore = new CountingEventStore(new PostgresEventStore(jdbc, serde));
        service = new AccountService(
                new SnapshottingAccountRepository(countingStore, snapshotStore, SNAPSHOT_EVERY));
    }

    @Test
    void rehydrationAfterASnapshotReadsOnlyTheTail() {
        var id = "cp5-" + UUID.randomUUID();

        // 1 open + 24 deposits = version 25; with N=10, the latest snapshot sits at v20.
        service.handle(new OpenAccount(id, "Katherine"));
        for (int deposit = 1; deposit <= 24; deposit++) {
            service.handle(new Deposit(id, 1_00, "deposit #" + deposit));
        }

        var snapshotVersion = jdbc.sql("SELECT version FROM snapshots WHERE stream_id = :id")
                .param("id", id)
                .query(Long.class)
                .optional();
        assertThat(snapshotVersion).as("a snapshot must exist by version 25").hasValue(20L);

        countingStore.resetCounter();
        var view = service.get(id);

        assertThat(view.balanceCents()).isEqualTo(24_00);
        assertThat(view.version()).isEqualTo(25);
        assertThat(countingStore.eventsRead())
                .as("rehydration must fold snapshot + tail (5 events), not all 25")
                .isLessThanOrEqualTo(25 - 20)
                .isGreaterThan(0);
    }

    @Test
    void snapshotsAreDisposable() {
        var id = "cp5-" + UUID.randomUUID();
        service.handle(new OpenAccount(id, "Dorothy"));
        for (int deposit = 1; deposit <= 12; deposit++) {
            service.handle(new Deposit(id, 2_00, "deposit #" + deposit));
        }
        var before = service.get(id);

        // Snapshots are a cache: destroying them may cost speed but never correctness.
        jdbc.sql("DELETE FROM snapshots WHERE stream_id = :id").param("id", id).update();

        var after = service.get(id);
        assertThat(after).isEqualTo(before);
    }
}
