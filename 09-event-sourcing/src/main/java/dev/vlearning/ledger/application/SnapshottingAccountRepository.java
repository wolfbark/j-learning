package dev.vlearning.ledger.application;

import java.util.List;

import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.eventstore.EventStore;

/**
 * YOUR WORK, step 5. Same contract as {@link EventSourcedAccountRepository}, but rehydration
 * starts from the latest snapshot (if any) and folds only the tail, and append occasionally
 * refreshes the snapshot.
 *
 * Policy to implement (simple and good enough): after an append that lands the stream on
 * version V, if V is a multiple of {@code snapshotEvery} — or has crossed one since the last
 * snapshot — save a snapshot at V. Snapshotting inline after the write is fine here; real
 * systems usually do it out-of-band precisely because it is disposable, best-effort work.
 */
public class SnapshottingAccountRepository implements AccountRepository {

    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
    private final int snapshotEvery;

    public SnapshottingAccountRepository(EventStore eventStore, SnapshotStore snapshotStore, int snapshotEvery) {
        this.eventStore = eventStore;
        this.snapshotStore = snapshotStore;
        this.snapshotEvery = snapshotEvery;
    }

    @Override
    public Loaded load(String accountId) {
        // TODO Step 5:
        //   1. snapshotStore.load(accountId)
        //   2. hit  -> fold from snapshot.state() over eventStore.readStream(accountId, snapshot.version())
        //      miss -> fold from AccountState.EMPTY over the full stream
        //   3. version = last tail event's version, or the snapshot's if the tail is empty
        throw new UnsupportedOperationException("TODO Step 5 — rehydrate from snapshot + tail");
    }

    @Override
    public void append(String accountId, long expectedVersion, List<AccountEvent> newEvents) {
        // TODO Step 5: append, then apply the snapshot policy described in the class javadoc.
        //   (To snapshot you need the state at the new version — load(accountId) after the
        //   append is the straightforward way and is itself snapshot-accelerated.)
        throw new UnsupportedOperationException("TODO Step 5 — append + snapshot policy");
    }
}
