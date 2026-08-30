package dev.vlearning.parcels;

import dev.vlearning.parcels.feed.ScanFeed;
import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.scan.PartitionKeys;
import dev.vlearning.parcels.scan.ScanPublisher;
import dev.vlearning.parcels.scan.ScanStatus;
import dev.vlearning.parcels.support.KafkaSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour pin for the whole lesson: a scan published by the application is projected by the
 * application's own consumer group, on the partition its key selects. Every refactoring in the
 * guided steps must leave this green.
 */
@SpringBootTest
class HappyPathIntegrationTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaSupport::bootstrapServers);
        registry.add("parcels.topics.scans", () -> "happy.parcels.scans");
        registry.add("parcels.topics.tasks", () -> "happy.parcels.notify");
        registry.add("parcels.topics.dlq", () -> "happy.parcels.notify.DLQ");
        registry.add("parcels.feed-group", () -> "happy-parcel-feed");
        registry.add("parcels.notify-group", () -> "happy-notify-workers");
    }

    @Autowired
    private ScanPublisher publisher;

    @Autowired
    private ScanFeed feed;

    @Test
    void aPublishedScanReachesTheFeedOnItsKeysPartition() {
        var accepted = ParcelScan.of("P-9001", "C-42", ScanStatus.ACCEPTED, 1);
        var delivered = ParcelScan.of("P-9001", "C-42", ScanStatus.DELIVERED, 2);

        publisher.publish(accepted);
        publisher.publish(delivered);

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(feed.history("P-9001")).hasSize(2));

        var history = feed.history("P-9001");
        assertThat(history).extracting(entry -> entry.scan().status())
                .containsExactly(ScanStatus.ACCEPTED, ScanStatus.DELIVERED);
        assertThat(history).extracting(ScanFeed.Entry::partition)
                .containsOnly(PartitionKeys.partitionFor("P-9001", 3));
        assertThat(history.get(1).offset())
                .as("same key, same partition, monotonically increasing offsets")
                .isGreaterThan(history.get(0).offset());
    }
}
