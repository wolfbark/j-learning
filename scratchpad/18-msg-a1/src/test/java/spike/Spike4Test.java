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

class Spike4Test {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.0")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");

    static {
        KAFKA.start();
    }

    static Map<String, Object> base() {
        return new HashMap<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
    }

    @Test
    void threeShareConsumersOnOnePartition() throws Exception {
        String topic = "spike4-one-partition";
        String group = "spike4-share-group";
        try (Admin admin = Admin.create(base())) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.GROUP, group),
                    List.of(new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"),
                            AlterConfigOp.OpType.SET)))).all().get();
        }
        int n = 60;
        var counts = new ConcurrentHashMap<Integer, AtomicInteger>();
        var seen = new CopyOnWriteArrayList<String>();
        var total = new AtomicInteger();
        var joined = new CountDownLatch(3);
        var threads = new ArrayList<Thread>();
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            final int id = i;
            counts.put(id, new AtomicInteger());
            threads.add(Thread.ofPlatform().start(() -> {
                var props = base();
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
                props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
                props.put("max.poll.records", 5);
                try (var c = new KafkaShareConsumer<String, String>(props)) {
                    c.subscribe(List.of(topic));
                    c.poll(Duration.ofMillis(1000));
                    joined.countDown();
                    long deadline = System.currentTimeMillis() + 60_000;
                    while (System.currentTimeMillis() < deadline && total.get() < n) {
                        var recs = c.poll(Duration.ofMillis(500));
                        for (var r : recs) {
                            try {
                                Thread.sleep(25);
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
        var props = base();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var p = new KafkaProducer<String, String>(props)) {
            for (int i = 0; i < n; i++) {
                p.send(new ProducerRecord<>(topic, "parcel-" + (i % 10), "scan-" + i));
            }
            p.flush();
        }
        try (Admin admin = Admin.create(base())) {
            Thread.sleep(1500);
            var desc = admin.describeShareGroups(List.of(group)).all().get().get(group);
            System.out.println("=== SHARE1P state=" + desc.groupState() + " members=" + desc.members().size());
            desc.members().forEach(m ->
                    System.out.println("  member " + m.clientId() + " assignment=" + m.assignment().topicPartitions()));
        }
        for (var t : threads) {
            t.join();
        }
        System.out.println("=== SHARE1P total=" + total.get() + " distinct=" + seen.stream().distinct().count()
                + " ms=" + (System.currentTimeMillis() - t0));
        new TreeMap<>(counts).forEach((k, v) -> System.out.println("  share consumer " + k + " records=" + v.get()));
        System.out.println("=== SHARE1P consumers_with_work="
                + counts.values().stream().filter(v -> v.get() > 0).count());
    }
}
