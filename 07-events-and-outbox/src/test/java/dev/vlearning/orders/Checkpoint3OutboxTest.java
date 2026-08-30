package dev.vlearning.orders;

import java.time.Duration;

import dev.vlearning.orders.chaos.ChaosException;
import dev.vlearning.orders.chaos.ChaosMonkey.CrashPoint;
import dev.vlearning.orders.order.OrderService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Step 3: the transactional outbox. One local transaction owns both the
 * business row and the event publication; the broker send happens after
 * commit and is retried until it succeeds. The same chaos that corrupted
 * state in step 2 now merely delays delivery.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3OutboxTest extends AbstractIntegrationTest {

    @Autowired
    OrderService orderService;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Test
    void crashBeforeCommit_leavesNoTraceAnywhere() {
        try (var probe = newProbe()) {
            chaos.armCrash(CrashPoint.AFTER_SEND_BEFORE_COMMIT);

            assertThatThrownBy(() -> orderService.place("Margaret Hamilton", someItems()))
                    .isInstanceOf(ChaosException.class);

            // order, outbox entry and event all roll back together: NEITHER side
            // has data. Atomicity restored.
            assertThat(orderCount()).isZero();
            assertThat(incompletePublicationCount()).isZero();
            assertThat(probe.recordsWithin(Duration.ofSeconds(4))).isEmpty();
        }
    }

    @Test
    void brokerOutage_eventWaitsInTheOutboxAndIsDeliveredAfterRecovery() {
        try (var probe = newProbe()) {
            chaos.breakBroker();

            // placing the order SUCCEEDS — the broker being down is no longer
            // the order service's problem
            var orderId = orderService.place("Margaret Hamilton", someItems());
            assertThat(orderExists(orderId)).isTrue();

            // the event sits in the outbox, undelivered: SELECT * FROM event_publication
            await().atMost(Duration.ofSeconds(10)).untilAsserted(
                    () -> assertThat(incompletePublicationCount()).isEqualTo(1));
            assertThat(probe.recordsWithin(Duration.ofSeconds(3))).isEmpty();

            // "restart": the broker comes back and the registry re-relays what
            // never completed (in production: republish-outstanding-events-on-restart)
            chaos.healBroker();
            incompletePublications.resubmitIncompletePublications(publication -> true);

            var records = probe.awaitRecords(1, Duration.ofSeconds(15));
            assertThat(records.getFirst()).contains(orderId.toString());
            await().atMost(Duration.ofSeconds(10)).untilAsserted(
                    () -> assertThat(incompletePublicationCount()).isZero());
        }
    }
}
