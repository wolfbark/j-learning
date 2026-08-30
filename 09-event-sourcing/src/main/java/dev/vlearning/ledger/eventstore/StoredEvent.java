package dev.vlearning.ledger.eventstore;

import java.time.Instant;

import dev.vlearning.ledger.domain.AccountEvent;

/**
 * A domain event plus its storage metadata:
 *
 * <ul>
 *   <li>{@code globalSequence} — position in the store-wide log; projections track this</li>
 *   <li>{@code version} — position within the stream, contiguous from 1; the optimistic lock</li>
 *   <li>{@code occurredAt} — when the store recorded it. Deliberately metadata: business
 *       time (like which month a fee belongs to) goes IN the event payload, not here</li>
 * </ul>
 */
public record StoredEvent(long globalSequence, String streamId, long version, AccountEvent event,
                          Instant occurredAt) {
}
