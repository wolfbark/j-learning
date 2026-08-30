package dev.vlearning.parcels.feed;

import dev.vlearning.parcels.scan.ParcelScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The stream side's read model: an in-memory projection of the scan log. It keeps the partition
 * and offset of every record so tests can talk about placement and ordering, not just content.
 */
@Component
public class ScanFeed {

    public record Entry(int partition, long offset, ParcelScan scan) {
    }

    private final List<Entry> arrivals = new CopyOnWriteArrayList<>();
    private final Map<String, List<Entry>> byParcel = new ConcurrentHashMap<>();

    public void record(int partition, long offset, ParcelScan scan) {
        var entry = new Entry(partition, offset, scan);
        arrivals.add(entry);
        byParcel.computeIfAbsent(scan.parcelId(), k -> new CopyOnWriteArrayList<>()).add(entry);
    }

    /** Everything this consumer group saw, in the order it saw it (not the order it was produced). */
    public List<Entry> arrivals() {
        return List.copyOf(arrivals);
    }

    public List<Entry> history(String parcelId) {
        return List.copyOf(byParcel.getOrDefault(parcelId, List.of()));
    }

    public Map<Integer, Integer> countsPerPartition() {
        var counts = new java.util.TreeMap<Integer, Integer>();
        arrivals.forEach(entry -> counts.merge(entry.partition(), 1, Integer::sum));
        return counts;
    }

    public int size() {
        return arrivals.size();
    }

    public void clear() {
        arrivals.clear();
        byParcel.clear();
    }

    public List<ParcelScan> scans() {
        return new ArrayList<>(arrivals.stream().map(Entry::scan).toList());
    }
}
