package dev.vlearning.ledger.projection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.vlearning.ledger.eventstore.EventStore;

/**
 * YOUR WORK, step 4. Maintains the {@code account_balances} table — a read model that
 * answers "what's the balance?" without folding a stream, by consuming the global event
 * feed in batches and remembering how far it got.
 *
 * The processing loop every polling projector shares:
 * <pre>
 *   from = checkpoints.position(NAME)
 *   batch = eventStore.readAll(from, BATCH_SIZE)
 *   for each stored event: apply it to the table
 *   checkpoints.advance(NAME, last global sequence in batch)
 * </pre>
 * All inside one transaction — {@code @Transactional} on {@link #runOnce()} makes
 * "apply batch + advance checkpoint" atomic, which is what makes a crash harmless.
 */
@Component
public class BalancesProjector implements RebuildableProjection {

    public static final String NAME = "balances";
    static final int BATCH_SIZE = 512;

    private final EventStore eventStore;
    private final ProjectionCheckpoints checkpoints;
    private final JdbcClient jdbc;

    public BalancesProjector(EventStore eventStore, ProjectionCheckpoints checkpoints, JdbcClient jdbc) {
        this.eventStore = eventStore;
        this.checkpoints = checkpoints;
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Process one batch; return how many events were handled (0 = caught up).
     * Tests drain with {@code while (projector.runOnce() > 0)}.
     */
    @Transactional
    public int runOnce() {
        // TODO Step 4:
        //   - read the checkpoint, fetch a batch from eventStore.readAll(...)
        //   - switch over each event: AccountOpened inserts a row (balance 0), deposits and
        //     withdrawals adjust balance_cents, AccountClosed flips status. INSERT … ON
        //     CONFLICT (account_id) DO UPDATE is your friend.
        //   - advance the checkpoint to the batch's last globalSequence, return batch size
        throw new UnsupportedOperationException("TODO Step 4 — project the balances read model");
    }

    @Override
    public void rebuild() {
        // TODO Step 4: reset checkpoint, TRUNCATE account_balances, drain runOnce() to 0.
        //   This is the "projections are disposable" demo: corrupt the table, rebuild, healed.
        throw new UnsupportedOperationException("TODO Step 4 — rebuild from history");
    }
}
