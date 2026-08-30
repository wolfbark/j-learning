package dev.vlearning.parcels.support;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A hand-driven classic consumer group: N members in one JVM, polled round-robin from the test
 * thread. Nothing here is production code — it exists so a test can watch assignment and
 * rebalancing happen instead of reading about them.
 */
public final class ConsumerGroupProbe implements AutoCloseable {

    private final List<KafkaConsumer<String, String>> members = new ArrayList<>();
    private final Map<Integer, AtomicInteger> recordCounts = new TreeMap<>();
    private final AtomicInteger assignmentEvents = new AtomicInteger();

    public ConsumerGroupProbe(String topic, String group, int memberCount, Map<String, Object> extraProps) {
        for (int i = 0; i < memberCount; i++) {
            var props = KafkaSupport.consumerProps(group);
            props.putAll(extraProps);
            var consumer = new KafkaConsumer<String, String>(props);
            consumer.subscribe(List.of(topic), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    if (!partitions.isEmpty()) {
                        assignmentEvents.incrementAndGet();
                    }
                }
            });
            members.add(consumer);
            recordCounts.put(i, new AtomicInteger());
        }
    }

    /**
     * Poll every member until the group settles: every partition owned, as many busy members as
     * the partition count allows, and the same assignment observed three rounds in a row. All
     * three conditions are needed — a member whose JoinGroup is still in flight looks exactly
     * like a member that will stay idle forever. Rebalancing is asynchronous — a single poll round
     * tells you nothing, which is exactly the trap a naive assignment test falls into.
     *
     * <p>Records that arrive during stabilisation are counted but <em>discarded</em>. Either
     * stabilise before producing, or read the totals rather than a handler's side effects.
     */
    public Map<Integer, List<TopicPartition>> awaitStableAssignment(int partitionCount, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Map<Integer, List<TopicPartition>> previous = Map.of();
        int stableRounds = 0;
        while (System.currentTimeMillis() < deadline) {
            pollOnce(Duration.ofMillis(300), record -> {
            });
            var current = assignments();
            int owned = current.values().stream().mapToInt(List::size).sum();
            long active = current.values().stream().filter(list -> !list.isEmpty()).count();
            long expectedActive = Math.min(members.size(), partitionCount);
            if (owned == partitionCount && active == expectedActive && current.equals(previous)) {
                if (++stableRounds >= 3) {
                    break;
                }
            } else {
                stableRounds = 0;
            }
            previous = current;
        }
        return assignments();
    }

    public Map<Integer, List<TopicPartition>> assignments() {
        var result = new TreeMap<Integer, List<TopicPartition>>();
        for (int i = 0; i < members.size(); i++) {
            result.put(i, members.get(i).assignment().stream()
                    .sorted((a, b) -> Integer.compare(a.partition(), b.partition()))
                    .toList());
        }
        return result;
    }

    public void pollOnce(Duration perMember, Consumer<ConsumerRecord<String, String>> handler) {
        for (int i = 0; i < members.size(); i++) {
            var records = members.get(i).poll(perMember);
            recordCounts.get(i).addAndGet(records.count());
            records.forEach(handler);
        }
    }

    /** Poll until {@code target} records have been handled in total, or the timeout expires. */
    public int pollUntil(int target, Duration timeout, Consumer<ConsumerRecord<String, String>> handler) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline && totalRecords() < target) {
            pollOnce(Duration.ofMillis(300), handler);
        }
        return totalRecords();
    }

    public Map<Integer, Integer> recordsPerMember() {
        var result = new TreeMap<Integer, Integer>();
        recordCounts.forEach((member, count) -> result.put(member, count.get()));
        return result;
    }

    public int totalRecords() {
        return recordCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public long idleMembers() {
        return members.stream().filter(c -> c.assignment().isEmpty()).count();
    }

    public int assignmentEvents() {
        return assignmentEvents.get();
    }

    public List<KafkaConsumer<String, String>> members() {
        return List.copyOf(members);
    }

    @Override
    public void close() {
        members.forEach(consumer -> {
            try {
                consumer.close(Duration.ofSeconds(5));
            } catch (RuntimeException ignored) {
                // a member that was already fenced out cannot leave politely
            }
        });
    }
}
