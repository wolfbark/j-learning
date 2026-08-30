package dev.vlearning.ledger.application;

import java.util.List;

import dev.vlearning.ledger.domain.AccountDecider;
import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.domain.AccountState;
import dev.vlearning.ledger.eventstore.EventStore;
import dev.vlearning.ledger.eventstore.StoredEvent;

/**
 * Rehydration, the plain way: read the whole stream, fold from EMPTY. This is all an
 * event-sourced repository is. Step 5 adds the snapshot-aware variant next to it.
 */
public class EventSourcedAccountRepository implements AccountRepository {

    private final EventStore eventStore;

    public EventSourcedAccountRepository(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public Loaded load(String accountId) {
        var stored = eventStore.readStream(accountId);
        var state = AccountDecider.fold(AccountState.EMPTY, stored.stream().map(StoredEvent::event).toList());
        var version = stored.isEmpty() ? 0L : stored.getLast().version();
        return new Loaded(state, version);
    }

    @Override
    public void append(String accountId, long expectedVersion, List<AccountEvent> newEvents) {
        eventStore.append(accountId, expectedVersion, newEvents);
    }
}
