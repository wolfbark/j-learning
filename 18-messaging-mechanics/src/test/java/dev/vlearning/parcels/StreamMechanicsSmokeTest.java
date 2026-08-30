package dev.vlearning.parcels;

import dev.vlearning.parcels.scan.PartitionKeys;
import dev.vlearning.parcels.support.ConsumerGroupProbe;
import dev.vlearning.parcels.support.KafkaSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enabled by default: the stream side of the lesson, proven once against a real broker. If this
 * test fails, your Docker environment is the suspect, not your code.
 */
class StreamMechanicsSmokeTest {

    private static final List<String> PARCELS = List.of("P-1", "P-2", "P-3", "P-4");

    @Test
    void aKeyPinsARecordToOnePartitionForever() {
        String topic = KafkaSupport.freshTopic("smoke-keys", 3);
        KafkaSupport.send(topic, KafkaSupport.keyedBatch(PARCELS, 20, "scan"), Duration.ZERO);

        var partitionsPerKey = KafkaSupport.drain(topic, 20, Duration.ofSeconds(20)).stream()
                .collect(Collectors.groupingBy(record -> record.key(),
                        Collectors.mapping(record -> record.partition(), Collectors.toSet())));

        assertThat(partitionsPerKey).hasSize(PARCELS.size());
        partitionsPerKey.forEach((key, partitions) -> {
            assertThat(partitions).as("all records for %s land together", key).hasSize(1);
            assertThat(partitions).containsExactly(PartitionKeys.partitionFor(key, 3));
        });
    }

    @Test
    void aConsumerGroupCannotHaveMoreWorkersThanPartitions() {
        String topic = KafkaSupport.freshTopic("smoke-group", 3);
        KafkaSupport.send(topic, KafkaSupport.keyedBatch(PARCELS, 12, "scan"), Duration.ZERO);

        try (var group = new ConsumerGroupProbe(topic, "smoke-feed", 4, Map.of())) {
            var assignments = group.awaitStableAssignment(3, Duration.ofSeconds(60));

            assertThat(assignments.values().stream().mapToInt(List::size).sum()).isEqualTo(3);
            assertThat(group.idleMembers())
                    .as("3 partitions, 4 members: one member is paid to watch\n%s", assignments)
                    .isEqualTo(1);

            assertThat(group.pollUntil(12, Duration.ofSeconds(30), record -> {
            })).isEqualTo(12);
        }
    }
}
