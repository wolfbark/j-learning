package dev.vlearning.ledger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.vlearning.ledger.application.AccountRepository;
import dev.vlearning.ledger.application.EventSourcedAccountRepository;
import dev.vlearning.ledger.application.SnapshotStore;
import dev.vlearning.ledger.application.SnapshottingAccountRepository;
import dev.vlearning.ledger.eventstore.EventSerde;
import dev.vlearning.ledger.eventstore.EventStore;
import dev.vlearning.ledger.eventstore.InMemoryEventStore;
import dev.vlearning.ledger.eventstore.PostgresEventStore;

/**
 * Wiring, driven by two properties in application.properties:
 *
 * <ul>
 *   <li>{@code ledger.event-store} — {@code in-memory} (pristine) or {@code postgres}
 *       (you flip this in step 3, once your PostgresEventStore passes checkpoint 3)</li>
 *   <li>{@code ledger.snapshots.enabled} — {@code false} (pristine) or {@code true}
 *       (step 5, once SnapshottingAccountRepository passes checkpoint 5)</li>
 * </ul>
 */
@Configuration
class LedgerConfig {

    @Bean
    @ConditionalOnProperty(name = "ledger.event-store", havingValue = "in-memory", matchIfMissing = true)
    EventStore inMemoryEventStore() {
        return new InMemoryEventStore();
    }

    @Bean
    @ConditionalOnProperty(name = "ledger.event-store", havingValue = "postgres")
    EventStore postgresEventStore(JdbcClient jdbc, EventSerde serde) {
        return new PostgresEventStore(jdbc, serde);
    }

    @Bean
    @ConditionalOnProperty(name = "ledger.snapshots.enabled", havingValue = "false", matchIfMissing = true)
    AccountRepository eventSourcedAccountRepository(EventStore eventStore) {
        return new EventSourcedAccountRepository(eventStore);
    }

    @Bean
    @ConditionalOnProperty(name = "ledger.snapshots.enabled", havingValue = "true")
    AccountRepository snapshottingAccountRepository(EventStore eventStore, SnapshotStore snapshotStore,
            @Value("${ledger.snapshots.every:10}") int snapshotEvery) {
        return new SnapshottingAccountRepository(eventStore, snapshotStore, snapshotEvery);
    }
}
