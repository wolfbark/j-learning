package dev.vlearning.parcels;

import dev.vlearning.parcels.support.ConsumerGroupProbe;
import dev.vlearning.parcels.support.KafkaSupport;
import dev.vlearning.parcels.support.ShareGroupPool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 1 — the same workload, two ways: a durable log read by a consumer group, and a work
 * queue drained by competing workers. Same broker, same topic shape, completely different
 * scaling and delivery semantics.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1LogVsQueueTest {

    private static List<Map.Entry<String, String>> work(int count) {
        var batch = new ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < count; i++) {
            batch.add(Map.entry("P-" + i, "notify-" + i));
        }
        return batch;
    }

    @Test
    void aConsumerGroupCannotOutgrowItsPartitions() {
        String topic = KafkaSupport.freshTopic("cp1-log", 1);
        KafkaSupport.send(topic, work(30), Duration.ZERO);

        try (var group = new ConsumerGroupProbe(topic, "cp1-feed", 3, Map.of())) {
            var assignments = group.awaitStableAssignment(1, Duration.ofSeconds(60));

            assertThat(group.idleMembers())
                    .as("one partition, three members: two of them are decoration\n%s", assignments)
                    .isEqualTo(2);
            assertThat(group.pollUntil(30, Duration.ofSeconds(30), record -> {
            })).isEqualTo(30);
            assertThat(group.recordsPerMember().values().stream().filter(count -> count > 0))
                    .as("all 30 records were processed by a single member")
                    .hasSize(1);
        }
    }

    @Test
    void aShareGroupCan() {
        String topic = KafkaSupport.freshTopic("cp1-queue", 1);

        try (var pool = new ShareGroupPool(topic, "cp1-workers", 3, Duration.ofMillis(20))) {
            pool.start();
            KafkaSupport.send(topic, work(30), Duration.ofMillis(15));

            assertThat(pool.awaitTotal(30, Duration.ofSeconds(45))).isTrue();
            assertThat(pool.membersWithWork())
                    .as("same single partition, three workers, all of them busy: %s", pool.countsPerMember())
                    .isEqualTo(3);
            assertThat(pool.distinctValues()).as("and nobody did the same item twice").isEqualTo(30);
        }
    }

    @Test
    void theLogReplaysAndTheQueueDoesNot() {
        String topic = KafkaSupport.freshTopic("cp1-replay", 1);
        String shareGroup = "cp1-replay-workers";

        try (var pool = new ShareGroupPool(topic, shareGroup, 1, Duration.ZERO)) {
            pool.start();
            KafkaSupport.send(topic, work(10), Duration.ofMillis(10));
            assertThat(pool.awaitTotal(10, Duration.ofSeconds(30))).isTrue();
        }

        // A brand-new consumer group reads the log from the beginning: the records are still there.
        assertThat(KafkaSupport.drain(topic, 10, Duration.ofSeconds(20)))
                .as("the log is the system of record; consuming it did not consume it")
                .hasSize(10);

        // The same share group, restarted, has nothing left: acknowledgement state is group state.
        try (var again = new ShareGroupPool(topic, shareGroup, 1, Duration.ZERO)) {
            again.start();
            assertThat(again.awaitTotal(1, Duration.ofSeconds(10)))
                    .as("an accepted work item is done, not merely 'read'")
                    .isFalse();
        }
    }
}
