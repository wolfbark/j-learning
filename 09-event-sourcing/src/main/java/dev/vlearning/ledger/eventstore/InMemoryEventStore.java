package dev.vlearning.ledger.eventstore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.vlearning.ledger.domain.AccountEvent;

/**
 * The reference implementation of the {@link EventStore} contract — the whole thing fits on
 * one screen. It backs the application until step 3 and it is what the enabled tests run
 * against, so you can see the contract behaving before you rebuild it on Postgres.
 *
 * Coarse-grained synchronization keeps the version check and the append atomic — exactly
 * the atomicity your Postgres implementation gets for free from the primary key.
 */
public class InMemoryEventStore implements EventStore {

    private final Map<String, List<StoredEvent>> streams = new HashMap<>();
    private final List<StoredEvent> all = new ArrayList<>();

    @Override
    public synchronized void append(String streamId, long expectedVersion, List<AccountEvent> events) {
        var stream = streams.computeIfAbsent(streamId, id -> new ArrayList<>());
        if (stream.size() != expectedVersion) {
            throw new ConcurrencyException(streamId, expectedVersion);
        }
        var now = Instant.now();
        for (var event : events) {
            var stored = new StoredEvent(all.size() + 1L, streamId, stream.size() + 1L, event, now);
            stream.add(stored);
            all.add(stored);
        }
    }

    @Override
    public synchronized List<StoredEvent> readStream(String streamId) {
        return List.copyOf(streams.getOrDefault(streamId, List.of()));
    }

    @Override
    public synchronized List<StoredEvent> readStream(String streamId, long afterVersion) {
        return streams.getOrDefault(streamId, List.of()).stream()
                .filter(stored -> stored.version() > afterVersion)
                .toList();
    }

    @Override
    public synchronized List<StoredEvent> readAll(long afterGlobalSequence, int limit) {
        return all.stream()
                .filter(stored -> stored.globalSequence() > afterGlobalSequence)
                .limit(limit)
                .toList();
    }
}
