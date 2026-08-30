package dev.vlearning.parcels.support;

import org.apache.kafka.clients.consumer.KafkaShareConsumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of competing workers on a Kafka <em>share group</em> — the queue side of the lesson.
 * Each member runs on its own thread, acknowledges every record individually, and commits.
 *
 * <p>{@link #start()} returns only once every member has joined the group, so a test can produce
 * work <em>after</em> the workers exist. That is not test decoration: a share group whose backlog
 * already sits in the log lets one member acquire a large batch in a single fetch, and the others
 * find nothing left to do.
 */
public final class ShareGroupPool implements AutoCloseable {

    private final List<Thread> threads = new ArrayList<>();
    private final Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final List<String> values = new CopyOnWriteArrayList<>();
    private final AtomicInteger total = new AtomicInteger();
    private volatile boolean running = true;

    private final String topic;
    private final String group;
    private final int members;
    private final Duration perRecordWork;

    public ShareGroupPool(String topic, String group, int members, Duration perRecordWork) {
        this.topic = topic;
        this.group = group;
        this.members = members;
        this.perRecordWork = perRecordWork;
        KafkaSupport.shareGroupReadsFromEarliest(group);
    }

    public void start() {
        var joined = new CountDownLatch(members);
        for (int i = 0; i < members; i++) {
            final int id = i;
            counts.put(id, new AtomicInteger());
            threads.add(Thread.ofPlatform().name("share-worker-" + id).start(() -> {
                try (var consumer = new KafkaShareConsumer<String, String>(
                        KafkaSupport.shareConsumerProps(group))) {
                    consumer.subscribe(List.of(topic));
                    consumer.poll(Duration.ofSeconds(1));
                    joined.countDown();
                    while (running) {
                        var records = consumer.poll(Duration.ofMillis(300));
                        for (var record : records) {
                            if (!perRecordWork.isZero()) {
                                KafkaSupport.sleep(perRecordWork);
                            }
                            consumer.acknowledge(record);
                            values.add(record.value());
                            counts.get(id).incrementAndGet();
                            total.incrementAndGet();
                        }
                        if (records.count() > 0) {
                            consumer.commitSync();
                        }
                    }
                }
            }));
        }
        try {
            joined.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public boolean awaitTotal(int expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (total.get() >= expected) {
                return true;
            }
            KafkaSupport.sleep(Duration.ofMillis(100));
        }
        return total.get() >= expected;
    }

    public Map<Integer, Integer> countsPerMember() {
        var result = new TreeMap<Integer, Integer>();
        counts.forEach((member, count) -> result.put(member, count.get()));
        return result;
    }

    public long membersWithWork() {
        return counts.values().stream().filter(count -> count.get() > 0).count();
    }

    public int total() {
        return total.get();
    }

    public List<String> valuesSeen() {
        return List.copyOf(values);
    }

    public long distinctValues() {
        return values.stream().distinct().count();
    }

    @Override
    public void close() {
        running = false;
        threads.forEach(thread -> {
            try {
                thread.join(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
