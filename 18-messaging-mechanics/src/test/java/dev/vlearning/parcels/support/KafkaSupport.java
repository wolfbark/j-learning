package dev.vlearning.parcels.support;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One Kafka broker for the whole suite (started once, on first touch of this class) plus the
 * admin plumbing the mechanics tests need.
 *
 * <p>Two settings are worth knowing about:
 * <ul>
 *   <li>{@code KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR=1} — the share
 *       coordinator's internal topic defaults to replication factor 3, which a single-broker
 *       test cluster can never satisfy. Without this, every share-group call times out.</li>
 *   <li>Share groups are GA in Kafka 4.2, so no feature flag or "unstable API" switch is
 *       needed — that was the 4.1 preview story.</li>
 * </ul>
 */
public final class KafkaSupport {

    public static final String IMAGE = "apache/kafka:4.2.0";

    private static final KafkaContainer KAFKA = new KafkaContainer(IMAGE)
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");

    static {
        KAFKA.start();
    }

    private KafkaSupport() {
    }

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    public static Admin admin() {
        return Admin.create(new HashMap<>(Map.of("bootstrap.servers", bootstrapServers())));
    }

    /** A topic name nothing else in the suite can collide with, already created. */
    public static String freshTopic(String prefix, int partitions) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        createTopic(name, partitions);
        return name;
    }

    public static void createTopic(String name, int partitions) {
        try (Admin admin = admin()) {
            admin.createTopics(List.of(new NewTopic(name, partitions, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("could not create topic " + name, e);
        }
    }

    /**
     * A share group's starting position is <em>group</em> configuration on the broker, not a
     * client property: {@code auto.offset.reset} does nothing for a share consumer. The default
     * is {@code latest}, so a group created after the records were produced would never see them.
     */
    public static void shareGroupReadsFromEarliest(String group) {
        try (Admin admin = admin()) {
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.GROUP, group),
                    List.of(new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"),
                            AlterConfigOp.OpType.SET)))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("could not configure share group " + group, e);
        }
    }

    public static Map<String, Object> producerProps() {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    public static Map<String, Object> consumerProps(String group) {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    /**
     * Props for a {@code KafkaShareConsumer}. {@code share.acknowledgement.mode=explicit} is
     * mandatory if you intend to call {@code acknowledge(...)} yourself — with the default
     * (implicit) mode, {@code acknowledge} throws {@code IllegalStateException}.
     */
    public static Map<String, Object> shareConsumerProps(String group) {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5);
        return props;
    }

    public static void send(String topic, String key, String value) {
        try (var producer = new KafkaProducer<String, String>(producerProps())) {
            producer.send(new ProducerRecord<>(topic, key, value));
            producer.flush();
        }
    }

    /** Send a batch, optionally trickling it in so that consumers get interleaved turns. */
    public static void send(String topic, List<Map.Entry<String, String>> keyedValues, Duration gap) {
        try (var producer = new KafkaProducer<String, String>(producerProps())) {
            for (var entry : keyedValues) {
                producer.send(new ProducerRecord<>(topic, entry.getKey(), entry.getValue()));
                if (!gap.isZero()) {
                    producer.flush();
                    sleep(gap);
                }
            }
            producer.flush();
        }
    }

    /** Read a whole topic with a throwaway group. Returns records in per-partition order. */
    public static List<ConsumerRecord<String, String>> drain(String topic, int expected, Duration timeout) {
        var collected = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(consumerProps("drain-" + UUID.randomUUID()))) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && collected.size() < expected) {
                consumer.poll(Duration.ofMillis(300)).forEach(collected::add);
            }
        }
        return collected;
    }

    public static Map<Integer, Long> endOffsets(String topic) {
        try (var consumer = new KafkaConsumer<String, String>(consumerProps("offsets-" + UUID.randomUUID()))) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            var result = new TreeMap<Integer, Long>();
            consumer.endOffsets(partitions).forEach((tp, offset) -> result.put(tp.partition(), offset));
            return result;
        }
    }

    /** Lag as the consumer itself sees it: how far behind the end of the log am I right now? */
    public static long lagOf(KafkaConsumer<String, String> consumer) {
        var assigned = consumer.assignment();
        if (assigned.isEmpty()) {
            return 0;
        }
        var ends = consumer.endOffsets(assigned);
        long lag = 0;
        for (var tp : assigned) {
            lag += ends.getOrDefault(tp, 0L) - consumer.position(tp);
        }
        return lag;
    }

    /** Lag as an operator sees it: committed offsets versus the end of the log. */
    public static long committedLag(String group, String topic) {
        try (Admin admin = admin()) {
            var committed = admin.listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata().get();
            var ends = endOffsets(topic);
            long lag = 0;
            for (var entry : ends.entrySet()) {
                var tp = new TopicPartition(topic, entry.getKey());
                long position = committed.containsKey(tp) ? committed.get(tp).offset() : 0L;
                lag += entry.getValue() - position;
            }
            return lag;
        } catch (Exception e) {
            throw new IllegalStateException("could not read committed lag for " + group, e);
        }
    }

    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static List<Map.Entry<String, String>> keyedBatch(List<String> keys, int count, String valuePrefix) {
        var batch = new ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < count; i++) {
            batch.add(Map.entry(keys.get(i % keys.size()), valuePrefix + "-" + i));
        }
        return batch;
    }
}
