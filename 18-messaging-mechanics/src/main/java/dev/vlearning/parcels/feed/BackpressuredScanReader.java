package dev.vlearning.parcels.feed;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A consumer that hands records to a worker thread, so polling never blocks on slow work.
 *
 * <p>As given, it is the version everybody writes first: an <b>unbounded</b> hand-off queue and a
 * poll loop that never stops fetching. It keeps the consumer in the group and it never loses a
 * record — and it converts broker lag into heap, which fails later, further away, and in
 * production. Step 5 is about replacing "buffer everything" with real backpressure:
 * {@link KafkaConsumer#pause} the assignment while the queue is full, keep calling
 * {@link KafkaConsumer#poll} so the member stays alive, and
 * {@link KafkaConsumer#resume} once the worker has caught up.
 *
 * <p>Lag is sampled on the polling thread. A {@code KafkaConsumer} is single-threaded by
 * contract; asking it for offsets from your test thread is how you earn a
 * {@code ConcurrentModificationException}.
 */
public class BackpressuredScanReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final int inFlightLimit;
    private final Duration workPerRecord;

    private final BlockingQueue<ConsumerRecord<String, String>> handOff = new LinkedBlockingQueue<>();
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicBoolean everPaused = new AtomicBoolean();
    private final AtomicInteger assignmentEvents = new AtomicInteger();

    private volatile long lag;
    private volatile boolean running = true;
    private Thread pollThread;
    private Thread workerThread;

    public BackpressuredScanReader(Map<String, Object> consumerProps, String topic, int inFlightLimit,
                                   Duration workPerRecord) {
        this.consumer = new KafkaConsumer<>(consumerProps);
        this.topic = topic;
        this.inFlightLimit = inFlightLimit;
        this.workPerRecord = workPerRecord;
    }

    public void start() {
        pollThread = Thread.ofPlatform().name("scan-poller").start(this::pollLoop);
        workerThread = Thread.ofPlatform().name("scan-worker").start(this::workLoop);
    }

    private void pollLoop() {
        consumer.subscribe(List.of(topic));
        while (running) {
            var records = consumer.poll(Duration.ofMillis(200));
            if (!consumer.assignment().isEmpty() && assignmentEvents.get() == 0) {
                assignmentEvents.incrementAndGet();
            }
            records.forEach(handOff::add);
            maxInFlight.updateAndGet(current -> Math.max(current, handOff.size()));
            lag = currentLag();
            if (!records.isEmpty()) {
                // Smell: this commits records that are only *buffered*. A crash here loses them —
                // at-least-once quietly became at-most-once. See step 5's stretch goal.
                consumer.commitSync();
            }
        }
        consumer.close(Duration.ofSeconds(5));
    }

    private long currentLag() {
        var assignment = consumer.assignment();
        if (assignment.isEmpty()) {
            return 0;
        }
        var ends = consumer.endOffsets(assignment);
        long total = 0;
        for (var partition : assignment) {
            total += ends.getOrDefault(partition, 0L) - consumer.position(partition);
        }
        return total + handOff.size();
    }

    private void workLoop() {
        while (running || !handOff.isEmpty()) {
            try {
                var record = handOff.poll(200, TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }
                if (!workPerRecord.isZero()) {
                    Thread.sleep(workPerRecord.toMillis());
                }
                processed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Call this from your poll loop once you implement pausing, so tests can see it happened. */
    protected void recordPaused() {
        everPaused.set(true);
    }

    public boolean awaitProcessed(int expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (processed.get() >= expected) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return processed.get() >= expected;
    }

    public int processed() {
        return processed.get();
    }

    public int inFlightLimit() {
        return inFlightLimit;
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public boolean everPaused() {
        return everPaused.get();
    }

    public int assignmentEvents() {
        return assignmentEvents.get();
    }

    public long lag() {
        return lag;
    }

    @Override
    public void close() {
        running = false;
        joinQuietly(pollThread);
        joinQuietly(workerThread);
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(Duration.ofSeconds(15));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
