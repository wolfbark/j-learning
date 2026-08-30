package dev.vlearning.shipping.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * The test's own pair of eyes (and hands) on the broker, independent of the
 * application's Kafka wiring: a raw consumer that watches a topic from the
 * moment the probe is created, plus a static helper to produce records.
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
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "true");
        this.consumer = new KafkaConsumer<>(props);

        var partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position); // resolve the seek now, not lazily on first poll
    }

    /** Waits until {@code expected} records arrived, or fails after the timeout. */
    public List<ConsumerRecord<String, String>> awaitRecords(int expected, Duration timeout) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && records.size() < expected) {
            consumer.poll(Duration.ofMillis(200)).forEach(records::add);
        }
        if (records.size() < expected) {
            throw new AssertionError(
                    "expected " + expected + " record(s) on the topic within " + timeout + ", saw " + records.size());
        }
        return records;
    }

    /** Collects everything produced to the topic during the given window. */
    public List<ConsumerRecord<String, String>> recordsWithin(Duration window) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(records::add);
        }
        return records;
    }

    /** Reads a header from a record as a UTF-8 string, or null if absent. */
    public static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Produces one record, optionally with string headers. */
    public static void produce(String bootstrapServers, String topic, String key, String value,
                               Map<String, String> headers) {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (var producer = new KafkaProducer<String, String>(props)) {
            var record = new ProducerRecord<>(topic, key, value);
            headers.forEach((name, headerValue) ->
                    record.headers().add(name, headerValue.getBytes(StandardCharsets.UTF_8)));
            producer.send(record);
            producer.flush();
        }
    }

    @Override
    public void close() {
        consumer.close();
    }
}
