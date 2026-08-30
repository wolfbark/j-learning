package dev.vlearning.parcels;

import dev.vlearning.parcels.support.ConsumerGroupProbe;
import dev.vlearning.parcels.support.KafkaSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 4 — the classic footgun: processing a poll batch takes longer than
 * {@code max.poll.interval.ms}, the coordinator concludes the member is dead, the group
 * rebalances, the survivor inherits the same uncommitted batch, and the loop repeats.
 *
 * <p>Both tests use the same slow handler and the same short poll interval. They differ in
 * {@code max.poll.records}. That is the lesson.
 *
 * <p>Both also let the group settle <em>before</em> producing anything: a rebalance while the
 * topic is empty is instant and free, and it keeps the measurement about processing rather than
 * about start-up.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4RebalanceTest {

    private static final Duration WORK_PER_RECORD = Duration.ofMillis(300);

    private static final Map<String, Object> SHORT_LEASH = Map.of(
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 5_000,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 6_000,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 2_000);

    private static Map<String, Object> withMaxPollRecords(int maxPollRecords) {
        var props = new java.util.HashMap<String, Object>(SHORT_LEASH);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        return props;
    }

    private static List<Map.Entry<String, String>> batch(int count) {
        var work = new ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < count; i++) {
            work.add(Map.entry("P-" + i, "scan-" + i));
        }
        return work;
    }

    @Test
    void aPollBatchBiggerThanThePollIntervalNeverFinishes() {
        int records = 120;
        String topic = KafkaSupport.freshTopic("cp4-storm", 3);

        Set<String> handled = new HashSet<>();
        try (var group = new ConsumerGroupProbe(topic, "cp4-storm-group", 2, withMaxPollRecords(25))) {
            group.awaitStableAssignment(3, Duration.ofSeconds(30));
            int settledEvents = group.assignmentEvents();
            KafkaSupport.send(topic, batch(records), Duration.ZERO);

            long deadline = System.currentTimeMillis() + 45_000;
            while (System.currentTimeMillis() < deadline && handled.size() < records) {
                group.pollOnce(Duration.ofMillis(300), record -> {
                    KafkaSupport.sleep(WORK_PER_RECORD);
                    handled.add(record.value());
                });
            }

            assertThat(group.assignmentEvents() - settledEvents)
                    .as("every eviction costs the whole group another assignment round")
                    .isGreaterThan(1);
            assertThat(handled)
                    .as("meanwhile the work does not get done: %d of %d", handled.size(), records)
                    .hasSizeLessThan(records);
            assertThat(group.totalRecords())
                    .as("and much of what was delivered was delivered more than once")
                    .isGreaterThan(handled.size());
        }
    }

    @Test
    void boundingThePollBatchFixesIt() {
        int records = 40;
        String topic = KafkaSupport.freshTopic("cp4-bounded", 3);

        Set<String> handled = new HashSet<>();
        try (var group = new ConsumerGroupProbe(topic, "cp4-bounded-group", 2, withMaxPollRecords(1))) {
            var assignments = group.awaitStableAssignment(3, Duration.ofSeconds(30));
            int settledEvents = group.assignmentEvents();
            KafkaSupport.send(topic, batch(records), Duration.ZERO);

            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline && handled.size() < records) {
                group.pollOnce(Duration.ofMillis(300), record -> {
                    KafkaSupport.sleep(WORK_PER_RECORD);
                    handled.add(record.value());
                });
            }

            assertThat(handled).as("all the work, with the same slow handler").hasSize(records);
            assertThat(group.assignmentEvents())
                    .as("and not one rebalance after the group settled on %s", assignments)
                    .isEqualTo(settledEvents);
        }
    }
}
