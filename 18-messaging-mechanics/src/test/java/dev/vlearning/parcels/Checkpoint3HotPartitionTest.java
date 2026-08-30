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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 3 — one very large customer, six partitions, and the arithmetic that ruins your day:
 * {@code murmur2(key) % partitions} does not care how much traffic a key carries.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3HotPartitionTest {

    private static final int PARTITIONS = 6;
    private static final int SALT_BUCKETS = 4 * PARTITIONS;
    private static final int TOTAL = 300;
    private static final String WHALE = "C-MEGACORP";
    private static final JsonCodec CODEC = new JsonCodec();

    /** 90% of the traffic belongs to one customer — the shape of every real B2B parcel feed. */
    private static List<ParcelScan> skewedTraffic() {
        var scans = new ArrayList<ParcelScan>();
        for (int i = 0; i < TOTAL; i++) {
            String customer = i % 10 == 0 ? "C-" + i : WHALE;
            scans.add(new ParcelScan("S-" + i, "P-" + i, customer, ScanStatus.IN_TRANSIT, "HUB-1", i, i));
        }
        return scans;
    }

    private static Map<Integer, Long> partitionCounts(String topic, Function<ParcelScan, String> keying) {
        var batch = new ArrayList<Map.Entry<String, String>>();
        skewedTraffic().forEach(scan -> batch.add(Map.entry(keying.apply(scan), CODEC.toJson(scan))));
        KafkaSupport.send(topic, batch, Duration.ZERO);

        return KafkaSupport.drain(topic, TOTAL, Duration.ofSeconds(30)).stream()
                .collect(Collectors.groupingBy(record -> record.partition(), Collectors.counting()));
    }

    @Test
    void keyingByCustomerCooksOnePartition() {
        var counts = partitionCounts(KafkaSupport.freshTopic("cp3-hot", PARTITIONS), PartitionKeys::byCustomer);

        long hottest = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(hottest)
                .as("the whale's partition carries the whole whale: %s", counts)
                .isGreaterThan((long) (TOTAL * 0.8));
        assertThat(hottest / (double) (TOTAL / (double) PARTITIONS))
                .as("skew factor against a perfectly even spread")
                .isGreaterThan(4.0);
    }

    @Test
    void aSaltedKeySpreadsTheWhaleWithoutLosingIt() {
        var counts = partitionCounts(KafkaSupport.freshTopic("cp3-salted", PARTITIONS),
                scan -> PartitionKeys.salted(scan, SALT_BUCKETS));

        long hottest = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(counts).as("every partition is doing some of the work").hasSize(PARTITIONS);
        assertThat(hottest)
                .as("the hottest partition is now well under half the traffic: %s", counts)
                .isLessThan((long) (0.4 * TOTAL));
    }

    @Test
    void theSaltNamesItsCustomerAndKeepsOneParcelInOnePlace() {
        var keys = new java.util.HashSet<String>();
        for (int i = 0; i < 50; i++) {
            var scan = new ParcelScan("S-" + i, "P-" + i, WHALE, ScanStatus.IN_TRANSIT, "HUB-1", i, i);
            keys.add(PartitionKeys.salted(scan, SALT_BUCKETS));
        }

        assertThat(keys)
                .as("a salted key must still say which customer it belongs to, or the downstream "
                        + "aggregate cannot be re-built")
                .allSatisfy(key -> assertThat(key).contains(WHALE));
        assertThat(keys).as("and it must actually spread, or nothing was salted").hasSizeGreaterThan(1);

        var accepted = new ParcelScan("S-1", "P-1", WHALE, ScanStatus.ACCEPTED, "HUB-1", 1, 1);
        var delivered = new ParcelScan("S-2", "P-1", WHALE, ScanStatus.DELIVERED, "HUB-9", 2, 2);
        assertThat(PartitionKeys.salted(accepted, SALT_BUCKETS))
                .as("stable per parcel — otherwise you traded a hot partition for lost ordering")
                .isEqualTo(PartitionKeys.salted(delivered, SALT_BUCKETS));
    }
}
