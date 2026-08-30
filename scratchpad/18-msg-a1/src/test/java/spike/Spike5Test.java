package spike;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaShareConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

class Spike5Test {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.0")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");

    static {
        KAFKA.start();
    }

    static Map<String, Object> base() {
        return new HashMap<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
    }

    static void setup(String topic, int partitions, String group) throws Exception {
        try (Admin admin = Admin.create(base())) {
            admin.createTopics(List.of(new NewTopic(topic, partitions, (short) 1))).all().get();
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.GROUP, group),
                    List.of(new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"),
                            AlterConfigOp.OpType.SET)))).all().get();
        }
    }

    static Map<Integer, AtomicInteger> runShareConsumers(String topic, String group, int members, int expected,
                                                         int fetchBytes, Runnable produceAction) throws Exception {
        var counts = new ConcurrentHashMap<Integer, AtomicInteger>();
        var seen = new CopyOnWriteArrayList<String>();
        var total = new AtomicInteger();
        var joined = new CountDownLatch(members);
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < members; i++) {
            final int id = i;
            counts.put(id, new AtomicInteger());
            threads.add(Thread.ofPlatform().start(() -> {
                var props = base();
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
                props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
                props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 2);
                if (fetchBytes > 0) {
                    props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, fetchBytes);
                    props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchBytes);
                }
                try (var c = new KafkaShareConsumer<String, String>(props)) {
                    c.subscribe(List.of(topic));
                    c.poll(Duration.ofMillis(1000));
                    joined.countDown();
                    long deadline = System.currentTimeMillis() + 45_000;
                    while (System.currentTimeMillis() < deadline && total.get() < expected) {
                        var recs = c.poll(Duration.ofMillis(300));
                        for (var r : recs) {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ignored) {
                            }
                            c.acknowledge(r);
                            seen.add(r.value());
                            counts.get(id).incrementAndGet();
                            total.incrementAndGet();
                        }
                        if (recs.count() > 0) {
                            c.commitSync();
                        }
                    }
                }
            }));
        }
        joined.await();
        produceAction.run();
        for (var t : threads) {
            t.join();
        }
        System.out.println("   total=" + total.get() + " distinct=" + seen.stream().distinct().count());
        return new TreeMap<>(counts);
    }

    static void produce(String topic, int n, long gapMs) {
        var props = base();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var p = new KafkaProducer<String, String>(props)) {
            for (int i = 0; i < n; i++) {
                p.send(new ProducerRecord<>(topic, "parcel-" + (i % 10), "scan-" + i));
                if (gapMs > 0) {
                    p.flush();
                    try {
                        Thread.sleep(gapMs);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            p.flush();
        }
    }

    @Test
    void trickleProduction() throws Exception {
        String topic = "spike5-trickle";
        String group = "spike5-trickle-group";
        setup(topic, 1, group);
        System.out.println("=== A: trickle, 1 partition, 3 members, 40 records @15ms gaps");
        var counts = runShareConsumers(topic, group, 3, 40, 0, () -> produce(topic, 40, 15));
        counts.forEach((k, v) -> System.out.println("   member " + k + " = " + v.get()));
        System.out.println("   with_work=" + counts.values().stream().filter(v -> v.get() > 0).count());
    }

    @Test
    void tinyFetchBudget() throws Exception {
        String topic = "spike5-tiny";
        String group = "spike5-tiny-group";
        setup(topic, 1, group);
        System.out.println("=== B: burst, 1 partition, 3 members, 40 records, fetch budget 300 bytes");
        var counts = runShareConsumers(topic, group, 3, 40, 300, () -> produce(topic, 40, 0));
        counts.forEach((k, v) -> System.out.println("   member " + k + " = " + v.get()));
        System.out.println("   with_work=" + counts.values().stream().filter(v -> v.get() > 0).count());
    }
}
