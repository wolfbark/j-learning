package dev.vlearning.parcels;

import dev.vlearning.parcels.scan.PartitionKeys;
import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.scan.ScanStatus;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionKeysTest {

    @Test
    void theKeyDecidesThePartitionAndNothingElseDoes() {
        int partitions = 3;
        int first = PartitionKeys.partitionFor("P-4711", partitions);

        assertThat(IntStream.range(0, 50).map(i -> PartitionKeys.partitionFor("P-4711", partitions)).distinct())
                .containsExactly(first);
    }

    @Test
    void everyScanOfOneParcelGetsTheSameKey() {
        var accepted = ParcelScan.of("P-1", "C-1", ScanStatus.ACCEPTED, 1);
        var delivered = ParcelScan.of("P-1", "C-1", ScanStatus.DELIVERED, 2);

        assertThat(PartitionKeys.byParcel(accepted)).isEqualTo(PartitionKeys.byParcel(delivered));
    }

    @Test
    void addingPartitionsMovesExistingKeys() {
        long moved = IntStream.range(0, 200)
                .filter(i -> PartitionKeys.partitionFor("P-" + i, 3) != PartitionKeys.partitionFor("P-" + i, 6))
                .count();

        assertThat(moved)
                .as("re-partitioning re-homes a large share of keys, which is why it also breaks "
                        + "per-key ordering for anything already in flight")
                .isBetween(60L, 199L);
    }
}
