package dev.vlearning.ledger.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.PostgresTestBase;
import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.domain.AccountEvent.AccountOpened;
import dev.vlearning.ledger.domain.AccountEvent.MoneyDeposited;

/**
 * CHECKPOINT 2 — the optimistic append. The Spring context is only here to provide a
 * migrated database and a JdbcClient; the store under test is constructed by hand, because
 * that is all it is: a class talking SQL. Assertions go straight to the table — read()
 * doesn't exist until step 3.
 */
@SpringBootTest
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2PostgresAppendTest extends PostgresTestBase {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventSerde serde;

    PostgresEventStore store;

    @BeforeEach
    void freshStore() {
        store = new PostgresEventStore(jdbc, serde);
    }

    @Test
    void appendWritesRowsWithContiguousVersionsAndReadablePayloads() {
        var id = "cp2-" + UUID.randomUUID();

        store.append(id, 0, List.of(
                new AccountOpened(id, "Ada"),
                new MoneyDeposited(id, 100_00, "salary"),
                new MoneyDeposited(id, 2_50, "found on the street")));

        var rows = jdbc.sql("SELECT version, type, payload::text AS payload FROM events WHERE stream_id = :id ORDER BY version")
                .param("id", id)
                .query()
                .listOfRows();

        assertThat(rows).extracting(row -> row.get("version")).containsExactly(1L, 2L, 3L);
        assertThat(rows).extracting(row -> row.get("type"))
                .containsExactly("AccountOpened", "MoneyDeposited", "MoneyDeposited");
        assertThat((String) rows.get(0).get("payload")).contains("\"owner\"").contains("Ada");
    }

    @Test
    void appendAtAStaleVersionThrowsConcurrencyExceptionAndWritesNothing() {
        var id = "cp2-" + UUID.randomUUID();
        store.append(id, 0, List.of(new AccountOpened(id, "Ada")));

        assertThatThrownBy(() -> store.append(id, 0, List.of(new MoneyDeposited(id, 1_00, "stale"))))
                .isInstanceOf(ConcurrencyException.class);

        assertThat(countRows(id)).isEqualTo(1);
    }

    @Test
    void multiEventAppendIsAllOrNothing() {
        var id = "cp2-" + UUID.randomUUID();
        store.append(id, 0, List.of(new AccountOpened(id, "Ada"), new MoneyDeposited(id, 1_00, "a")));

        // This batch believes the stream is at version 1, so it targets versions 2 and 3.
        // Version 2 is already taken: the whole batch must be rejected — no partial writes.
        // (A loop of single INSERTs would leave a half-written batch here; one multi-row
        // INSERT statement cannot.)
        assertThatThrownBy(() -> store.append(id, 1,
                List.of(new MoneyDeposited(id, 2_00, "b"), new MoneyDeposited(id, 3_00, "c"))))
                .isInstanceOf(ConcurrencyException.class);

        assertThat(countRows(id)).isEqualTo(2);
    }

    @Test
    void twoConcurrentAppendsAtTheSameVersionAdmitExactlyOneWinner() throws Exception {
        var id = "cp2-" + UUID.randomUUID();
        store.append(id, 0, List.of(new AccountOpened(id, "Ada")));

        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> racers = List.of(
                    executor.submit(() -> race(id, start, successes, conflicts,
                            new MoneyDeposited(id, 10_00, "left racer"))),
                    executor.submit(() -> race(id, start, successes, conflicts,
                            new MoneyDeposited(id, 20_00, "right racer"))));
            start.countDown();
            for (var racer : racers) {
                racer.get();
            }
        }

        assertThat(successes).as("exactly one append may win").hasValue(1);
        assertThat(conflicts).as("the loser must see ConcurrencyException").hasValue(1);
        assertThat(countRows(id)).isEqualTo(2);
    }

    private void race(String id, CountDownLatch start, AtomicInteger successes, AtomicInteger conflicts,
            AccountEvent event) {
        try {
            start.await();
            store.append(id, 1, List.of(event));
            successes.incrementAndGet();
        } catch (ConcurrencyException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long countRows(String streamId) {
        return jdbc.sql("SELECT count(*) FROM events WHERE stream_id = :id")
                .param("id", streamId)
                .query(Long.class)
                .single();
    }
}
