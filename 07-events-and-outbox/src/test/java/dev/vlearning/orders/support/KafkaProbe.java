package dev.vlearning.orders.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * A raw Kafka consumer that observes a topic from the moment it is created:
 * it seeks to the current end of every partition, so anything it returns was
 * produced afterwards. Independent of the application's listener containers —
 * this is the test's own pair of eyes on the broker.
 */
public final class KafkaProbe implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaProbe(String bootstrapServers, String topic) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        this.consumer = new KafkaConsumer<>(props);

        var partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position); // resolve the seek now, not lazily on first poll
    }

    /** Collects everything produced to the topic during the given window. */
    public List<String> recordsWithin(Duration window) {
        var records = new ArrayList<String>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> records.add(record.value()));
        }
        return records;
    }

    /** Waits until {@code expected} records arrived, or fails after the timeout. */
    public List<String> awaitRecords(int expected, Duration timeout) {
        var records = new ArrayList<String>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && records.size() < expected) {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> records.add(record.value()));
        }
        if (records.size() < expected) {
            throw new AssertionError(
                    "expected " + expected + " record(s) on the topic within " + timeout + ", saw " + records.size());
        }
        return records;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
