package dev.vlearning.ledger.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.domain.AccountEvent.AccountOpened;
import dev.vlearning.ledger.domain.AccountEvent.MoneyDeposited;
import dev.vlearning.ledger.domain.AccountEvent.MoneyWithdrawn;

/**
 * ENABLED: the {@link EventStore} CONTRACT, demonstrated against the given in-memory
 * implementation. Your PostgresEventStore must honor exactly these behaviors — checkpoints
 * 2 and 3 re-assert them against the real database.
 */
class InMemoryEventStoreTest {

    private final InMemoryEventStore store = new InMemoryEventStore();

    @Test
    void appendedEventsComeBackInOrderWithContiguousVersions() {
        store.append("acc-1", 0, List.of(
                new AccountOpened("acc-1", "Ada"),
                new MoneyDeposited("acc-1", 100_00, "salary"),
                new MoneyWithdrawn("acc-1", 40_00, "rent")));

        var stored = store.readStream("acc-1");

        assertThat(stored).extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(stored).extracting(StoredEvent::event).containsExactly(
                new AccountOpened("acc-1", "Ada"),
                new MoneyDeposited("acc-1", 100_00, "salary"),
                new MoneyWithdrawn("acc-1", 40_00, "rent"));
    }

    @Test
    void readingAMissingStreamReturnsEmptyNotError() {
        assertThat(store.readStream("nope")).isEmpty();
    }

    @Test
    void appendAtAStaleVersionIsRejected() {
        store.append("acc-1", 0, List.of(new AccountOpened("acc-1", "Ada")));

        // A second writer that ALSO believes the stream is new must lose:
        assertThatThrownBy(() -> store.append("acc-1", 0, List.of(new AccountOpened("acc-1", "Eve"))))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    void readStreamAfterVersionReturnsOnlyTheTail() {
        store.append("acc-1", 0, List.of(
                new AccountOpened("acc-1", "Ada"),
                new MoneyDeposited("acc-1", 1_00, "a"),
                new MoneyDeposited("acc-1", 2_00, "b"),
                new MoneyDeposited("acc-1", 3_00, "c")));

        var tail = store.readStream("acc-1", 2);

        assertThat(tail).extracting(StoredEvent::version).containsExactly(3L, 4L);
    }

    @Test
    void readAllInterleavesStreamsInGlobalOrder() {
        store.append("acc-1", 0, List.of(new AccountOpened("acc-1", "Ada")));
        store.append("acc-2", 0, List.of(new AccountOpened("acc-2", "Bob")));
        store.append("acc-1", 1, List.of(new MoneyDeposited("acc-1", 5_00, "x")));

        var all = store.readAll(0, 100);

        assertThat(all).extracting(StoredEvent::globalSequence).isSorted().hasSize(3);
        assertThat(all).extracting(StoredEvent::streamId).containsExactly("acc-1", "acc-2", "acc-1");
        // and paging picks up where we left off:
        assertThat(store.readAll(all.get(1).globalSequence(), 100)).hasSize(1);
    }

    @Test
    void twoConcurrentAppendsAtTheSameVersionAdmitExactlyOneWinner() throws Exception {
        store.append("acc-1", 0, List.of(new AccountOpened("acc-1", "Ada")));

        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> racers = List.of(
                    executor.submit(() -> race(start, successes, conflicts,
                            List.of(new MoneyDeposited("acc-1", 10_00, "left")))),
                    executor.submit(() -> race(start, successes, conflicts,
                            List.of(new MoneyDeposited("acc-1", 20_00, "right")))));
            start.countDown();
            for (var racer : racers) {
                racer.get();
            }
        }

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(store.readStream("acc-1")).hasSize(2);
    }

    private void race(CountDownLatch start, AtomicInteger successes, AtomicInteger conflicts,
            List<AccountEvent> events) {
        try {
            start.await();
            store.append("acc-1", 1, events);
            successes.incrementAndGet();
        } catch (ConcurrencyException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
