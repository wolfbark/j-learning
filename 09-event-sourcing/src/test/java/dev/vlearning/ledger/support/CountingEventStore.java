package dev.vlearning.ledger.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.eventstore.EventStore;
import dev.vlearning.ledger.eventstore.StoredEvent;

/**
 * Test decorator: counts how many events pass through the read side. Checkpoint 5 uses it
 * to PROVE snapshots work — "rehydration is faster" is a claim, "rehydration read 5 events
 * instead of 25" is a measurement.
 */
public class CountingEventStore implements EventStore {

    private final EventStore delegate;
    private final AtomicLong eventsRead = new AtomicLong();

    public CountingEventStore(EventStore delegate) {
        this.delegate = delegate;
    }

    public long eventsRead() {
        return eventsRead.get();
    }

    public void resetCounter() {
        eventsRead.set(0);
    }

    @Override
    public void append(String streamId, long expectedVersion, List<AccountEvent> events) {
        delegate.append(streamId, expectedVersion, events);
    }

    @Override
    public List<StoredEvent> readStream(String streamId) {
        return counted(delegate.readStream(streamId));
    }

    @Override
    public List<StoredEvent> readStream(String streamId, long afterVersion) {
        return counted(delegate.readStream(streamId, afterVersion));
    }

    @Override
    public List<StoredEvent> readAll(long afterGlobalSequence, int limit) {
        return counted(delegate.readAll(afterGlobalSequence, limit));
    }

    private List<StoredEvent> counted(List<StoredEvent> result) {
        eventsRead.addAndGet(result.size());
        return result;
    }
}
