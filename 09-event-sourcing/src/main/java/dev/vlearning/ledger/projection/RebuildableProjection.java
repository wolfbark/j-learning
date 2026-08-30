package dev.vlearning.ledger.projection;

/**
 * A projection that can be thrown away and rebuilt from the event log. This is the
 * defining superpower of event sourcing: read models are caches, history is truth.
 * Exposed over POST /admin/projections/{name}/rebuild.
 */
public interface RebuildableProjection {

    String name();

    /** Discard the read model and replay it from global sequence 0. */
    void rebuild();
}
