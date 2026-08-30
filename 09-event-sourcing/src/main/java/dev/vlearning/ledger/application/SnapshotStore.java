package dev.vlearning.ledger.application;

import java.util.Optional;

import dev.vlearning.ledger.domain.AccountState;

/**
 * Storage for snapshots: a cached fold result at a known version. A snapshot is ALWAYS
 * disposable — delete the table and nothing is lost, rehydration just gets slower until
 * new snapshots are taken. If deleting a table would lose data, it is not a snapshot.
 */
public interface SnapshotStore {

    Optional<Snapshot> load(String streamId);

    void save(Snapshot snapshot);

    record Snapshot(String streamId, long version, AccountState state) {
    }
}
