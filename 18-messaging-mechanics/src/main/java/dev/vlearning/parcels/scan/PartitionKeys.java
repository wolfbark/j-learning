package dev.vlearning.parcels.scan;

import org.apache.kafka.clients.producer.internals.BuiltInPartitioner;

import java.nio.charset.StandardCharsets;

/**
 * Where a record lands is a pure function of its key: {@code murmur2(key) % partitions}.
 * That single line is the whole ordering and scaling story of a log — the key chooses the
 * partition, the partition chooses the ordering domain and the consumer.
 */
public final class PartitionKeys {

    private PartitionKeys() {
    }

    /** The obvious key: everything about one parcel is ordered against everything else about it. */
    public static String byParcel(ParcelScan scan) {
        return scan.parcelId();
    }

    /** The key a naive "route by customer" design picks — and the one that creates hot partitions. */
    public static String byCustomer(ParcelScan scan) {
        return scan.customerId();
    }

    /**
     * Step 3: spread one very large key over {@code buckets} partitions while keeping enough
     * structure to re-aggregate downstream. Deleting this exception is your job.
     */
    public static String salted(ParcelScan scan, int buckets) {
        throw new UnsupportedOperationException("Checkpoint 3 — implement a salted key");
    }

    /**
     * The default partitioner's arithmetic for a record that has a key. Useful in tests: you can
     * predict placement without asking the broker.
     */
    public static int partitionFor(String key, int partitionCount) {
        return BuiltInPartitioner.partitionForKey(key.getBytes(StandardCharsets.UTF_8), partitionCount);
    }
}
