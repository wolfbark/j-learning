package dev.vlearning.parcels;

import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.scan.PartitionKeys;
import dev.vlearning.parcels.scan.ScanStatus;
import dev.vlearning.parcels.support.KafkaSupport;
import dev.vlearning.parcels.wire.JsonCodec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 2 — what "ordered" actually means in a partitioned log.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2OrderingTest {

    private static final List<String> PARCELS = List.of("P-1", "P-2", "P-3", "P-4", "P-5", "P-6");
    private static final JsonCodec CODEC = new JsonCodec();

    /** Six parcels, five scans each, interleaved exactly as a real feed would interleave them. */
    private static List<Map.Entry<String, String>> interleavedScans() {
        var batch = new ArrayList<Map.Entry<String, String>>();
        int sequence = 0;
        for (ScanStatus status : List.of(ScanStatus.ACCEPTED, ScanStatus.AT_HUB, ScanStatus.IN_TRANSIT,
                ScanStatus.OUT_FOR_DELIVERY, ScanStatus.DELIVERED)) {
            for (String parcel : PARCELS) {
                var scan = ParcelScan.of(parcel, "C-1", status, sequence++);
                batch.add(Map.entry(PartitionKeys.byParcel(scan), CODEC.toJson(scan)));
            }
        }
        return batch;
    }

    @Test
    void perKeyOrderIsGuaranteed() {
        String topic = KafkaSupport.freshTopic("cp2-order", 3);
        KafkaSupport.send(topic, interleavedScans(), Duration.ZERO);

        var byParcel = KafkaSupport.drain(topic, 30, Duration.ofSeconds(20)).stream()
                .map(record -> CODEC.fromJson(record.value(), ParcelScan.class))
                .collect(Collectors.groupingBy(ParcelScan::parcelId));

        assertThat(byParcel).hasSize(PARCELS.size());
        byParcel.forEach((parcel, scans) -> assertThat(scans)
                .as("scans of %s arrive in the order they were produced", parcel)
                .isSortedAccordingTo((a, b) -> Integer.compare(a.sequence(), b.sequence()))
                .extracting(ParcelScan::status)
                .containsExactly(ScanStatus.ACCEPTED, ScanStatus.AT_HUB, ScanStatus.IN_TRANSIT,
                        ScanStatus.OUT_FOR_DELIVERY, ScanStatus.DELIVERED));
    }

    @Test
    void globalOrderIsNot() {
        String topic = KafkaSupport.freshTopic("cp2-global", 3);
        KafkaSupport.send(topic, interleavedScans(), Duration.ZERO);

        var records = KafkaSupport.drain(topic, 30, Duration.ofSeconds(20));
        var sequencesAsRead = records.stream()
                .map(record -> CODEC.fromJson(record.value(), ParcelScan.class).sequence())
                .toList();

        assertThat(records.stream().map(record -> record.partition()).distinct())
                .as("the keys really did spread over several partitions")
                .hasSizeGreaterThan(1);
        assertThat(sequencesAsRead)
                .as("read partition by partition, the producer's global order is gone")
                .isNotEqualTo(sequencesAsRead.stream().sorted().toList());
    }

    @Test
    void oneOrderingDomainMeansOnePartitionAndOneWorker() {
        String topic = KafkaSupport.freshTopic("cp2-single", 1);
        KafkaSupport.send(topic, interleavedScans(), Duration.ZERO);

        var sequencesAsRead = KafkaSupport.drain(topic, 30, Duration.ofSeconds(20)).stream()
                .map(record -> CODEC.fromJson(record.value(), ParcelScan.class).sequence())
                .toList();

        assertThat(sequencesAsRead)
                .as("total order is available — at a throughput ceiling of one consumer")
                .isSortedAccordingTo(Integer::compare);
    }
}
