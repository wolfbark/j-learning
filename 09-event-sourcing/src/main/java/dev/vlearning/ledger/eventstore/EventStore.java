package dev.vlearning.ledger.eventstore;

import java.util.List;

import dev.vlearning.ledger.domain.AccountEvent;

/**
 * The whole contract of an event store. Four methods. Everything else in event sourcing —
 * rehydration, snapshots, projections, time travel — is built on top of these.
 *
 * <p>Contract, precisely:
 * <ul>
 *   <li>{@link #append}: atomically appends {@code events} to {@code streamId}, assigning
 *       versions {@code expectedVersion + 1, expectedVersion + 2, …}. {@code expectedVersion}
 *       is the version of the last event the CALLER has seen (0 for a stream it believes is
 *       new). If the stream has moved past that, throws {@link ConcurrencyException} and
 *       appends nothing — all events or none.</li>
 *   <li>{@link #readStream(String)}: every event of one stream, in version order.
 *       Empty list if the stream does not exist (there is no "create stream" operation —
 *       a stream exists once its first event does).</li>
 *   <li>{@link #readStream(String, long)}: only events with {@code version > afterVersion} —
 *       the tail you fold on top of a snapshot (step 5).</li>
 *   <li>{@link #readAll}: up to {@code limit} events across ALL streams with
 *       {@code globalSequence > afterGlobalSequence}, in global order — the feed that
 *       projections consume (step 4).</li>
 * </ul>
 */
public interface EventStore {

    void append(String streamId, long expectedVersion, List<AccountEvent> events);

    List<StoredEvent> readStream(String streamId);

    List<StoredEvent> readStream(String streamId, long afterVersion);

    List<StoredEvent> readAll(long afterGlobalSequence, int limit);
}
