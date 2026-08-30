package dev.vlearning.parcels;

import dev.vlearning.parcels.support.KafkaSupport;
import dev.vlearning.parcels.support.ShareGroupPool;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enabled by default: the queue side, on the same broker. Kafka 4.2 made share groups
 * (KIP-932) GA, so a topic can now be consumed as a work queue with per-record acknowledgement
 * — no offsets, no partition-per-consumer ceiling.
 */
class ShareGroupSmokeTest {

    @Test
    void moreWorkersThanPartitionsAllDoWork() {
        String topic = KafkaSupport.freshTopic("smoke-share", 1);
        var work = new ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < 30; i++) {
            work.add(Map.entry("P-" + i, "notify-" + i));
        }

        try (var pool = new ShareGroupPool(topic, "smoke-notify-workers", 3, Duration.ofMillis(20))) {
            pool.start();
            KafkaSupport.send(topic, work, Duration.ofMillis(15));

            assertThat(pool.awaitTotal(30, Duration.ofSeconds(45))).isTrue();
            assertThat(pool.distinctValues()).as("each work item was handled once").isEqualTo(30);
            assertThat(pool.membersWithWork())
                    .as("one partition, three competing workers: %s", pool.countsPerMember())
                    .isEqualTo(3);
        }
    }

    @Test
    void aReleasedRecordComesBackWithoutRewindingAnything() {
        String topic = KafkaSupport.freshTopic("smoke-release", 1);
        String group = "smoke-release-group";
        KafkaSupport.shareGroupReadsFromEarliest(group);
        KafkaSupport.send(topic, "P-1", "notify-P-1");

        var deliveryCounts = new ArrayList<Integer>();
        try (var consumer = new KafkaShareConsumer<String, String>(KafkaSupport.shareConsumerProps(group))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline && deliveryCounts.size() < 3) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (var record : records) {
                    deliveryCounts.add(record.deliveryCount().orElseThrow().intValue());
                    consumer.acknowledge(record, deliveryCounts.size() < 3
                            ? AcknowledgeType.RELEASE
                            : AcknowledgeType.ACCEPT);
                }
                if (records.count() > 0) {
                    consumer.commitSync();
                }
            }
        }

        assertThat(deliveryCounts)
                .as("the broker tracks state per record, so a redelivery is not a replay")
                .containsExactly(1, 2, 3);
    }
}
