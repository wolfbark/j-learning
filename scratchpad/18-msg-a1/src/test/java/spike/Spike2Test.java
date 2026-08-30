package spike;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
import java.util.concurrent.atomic.AtomicInteger;

class Spike2Test {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.0")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");

    static {
        long t = System.currentTimeMillis();
        KAFKA.start();
        System.out.println("=== KAFKA start ms=" + (System.currentTimeMillis() - t));
    }

    static Map<String, Object> base() {
        return new HashMap<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
    }

    static void createTopic(String name, int partitions) throws Exception {
        try (Admin admin = Admin.create(base())) {
            admin.createTopics(List.of(new NewTopic(name, partitions, (short) 1))).all().get();
        }
    }

    static void produce(String topic, int n) {
        var props = base();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var p = new KafkaProducer<String, String>(props)) {
            for (int i = 0; i < n; i++) {
                p.send(new ProducerRecord<>(topic, "parcel-" + (i % 10), "scan-" + i));
            }
            p.flush();
        }
    }

    @Test
    void classicGroupStableAssignment() throws Exception {
        String topic = "spike2-classic";
        String group = "spike2-classic-group";
        createTopic(topic, 3);
        produce(topic, 30);

        long t0 = System.currentTimeMillis();
        var consumers = new ArrayList<KafkaConsumer<String, String>>();
        for (int i = 0; i < 4; i++) {
            var props = base();
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            var c = new KafkaConsumer<String, String>(props);
            c.subscribe(List.of(topic));
            consumers.add(c);
        }
        int members = 0;
        try (Admin admin = Admin.create(base())) {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                for (var c : consumers) {
                    c.poll(Duration.ofMillis(300));
                }
                var desc = admin.describeConsumerGroups(List.of(group)).all().get().get(group);
                members = desc.members().size();
                int assigned = consumers.stream().mapToInt(c -> c.assignment().size()).sum();
                if (members == 4 && assigned == 3) {
                    break;
                }
            }
        }
        System.out.println("=== CLASSIC members=" + members + " settle_ms=" + (System.currentTimeMillis() - t0));
        for (int i = 0; i < consumers.size(); i++) {
            System.out.println("  consumer " + i + " assignment=" + consumers.get(i).assignment());
        }
        long idle = consumers.stream().filter(c -> c.assignment().isEmpty()).count();
        System.out.println("=== CLASSIC idle=" + idle);
        consumers.forEach(KafkaConsumer::close);
    }

    @Test
    void shareGroupConcurrentFairness() throws Exception {
        String topic = "spike2-share";
        String group = "spike2-share-group";
        createTopic(topic, 3);
        try (Admin admin = Admin.create(base())) {
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.GROUP, group),
                    List.of(new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"),
                            AlterConfigOp.OpType.SET)))).all().get();
        }
        int n = 200;
        produce(topic, n);

        long t0 = System.currentTimeMillis();
        var counts = new ConcurrentHashMap<Integer, AtomicInteger>();
        var seen = new CopyOnWriteArrayList<String>();
        var total = new AtomicInteger();
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < 4; i++) {
            final int id = i;
            counts.put(id, new AtomicInteger());
            threads.add(Thread.ofPlatform().start(() -> {
                var props = base();
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
                props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
                props.put("max.poll.records", 10);
                try (var c = new KafkaShareConsumer<String, String>(props)) {
                    c.subscribe(List.of(topic));
                    long deadline = System.currentTimeMillis() + 45_000;
                    while (System.currentTimeMillis() < deadline && total.get() < n) {
                        var recs = c.poll(Duration.ofMillis(500));
                        for (var r : recs) {
                            try {
                                Thread.sleep(5);
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
        for (var t : threads) {
            t.join();
        }
        System.out.println("=== SHARE total=" + total.get() + " distinct=" + seen.stream().distinct().count()
                + " ms=" + (System.currentTimeMillis() - t0));
        new TreeMap<>(counts).forEach((k, v) -> System.out.println("  share consumer " + k + " records=" + v.get()));
    }
}
