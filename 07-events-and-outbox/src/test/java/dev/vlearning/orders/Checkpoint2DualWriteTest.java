package dev.vlearning.orders;

import java.time.Duration;

import dev.vlearning.orders.chaos.ChaosException;
import dev.vlearning.orders.chaos.ChaosMonkey.CrashPoint;
import dev.vlearning.orders.order.OrderService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 2: prove the dual write is broken — in both directions.
 *
 * These tests PASS against the naive OrderService. They pin the bug, they do
 * not fix it. After step 3 removes the dual write, both tests MUST fail —
 * that is the outbox doing its job. Re-disable the class at that point; it
 * stays in the repo as the exhibit of what you repaired.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2DualWriteTest extends AbstractIntegrationTest {

    @Autowired
    OrderService orderService;

    @Test
    void crashAfterCommitBeforeSend_theEventIsLost() {
        try (var probe = newProbe()) {
            chaos.armCrash(CrashPoint.AFTER_COMMIT_BEFORE_SEND);

            assertThatThrownBy(() -> orderService.place("Grace Hopper", someItems()))
                    .isInstanceOf(ChaosException.class);

            // the database is certain the order happened...
            assertThat(orderCount()).isEqualTo(1);

            // ...but the broker never hears about it. Fulfillment will wait forever.
            assertThat(probe.recordsWithin(Duration.ofSeconds(4))).isEmpty();
        }
    }

    @Test
    void crashAfterSendBeforeCommit_aGhostEventEscapes() {
        try (var probe = newProbe()) {
            chaos.armCrash(CrashPoint.AFTER_SEND_BEFORE_COMMIT);

            assertThatThrownBy(() -> orderService.place("Grace Hopper", someItems()))
                    .isInstanceOf(ChaosException.class);

            // the transaction rolled back — as far as the database is concerned,
            // this order never existed...
            assertThat(orderCount()).isZero();

            // ...yet the event is out there, announcing an order that isn't.
            assertThat(probe.awaitRecords(1, Duration.ofSeconds(10))).hasSize(1);
        }
    }
}
