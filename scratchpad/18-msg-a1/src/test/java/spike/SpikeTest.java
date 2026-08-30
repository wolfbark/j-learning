package spike;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
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
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

class SpikeTest {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.0")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1")
            .withEnv("KAFKA_GROUP_COORDINATOR_REBALANCE_PROTOCOLS", "classic,consumer,share");

    static {
        KAFKA.start();
    }

    static Map<String, Object> base() {
        return Map.of("bootstrap.servers", KAFKA.getBootstrapServers());
    }

    static void createTopic(String name, int partitions) throws Exception {
        try (Admin admin = Admin.create(new java.util.HashMap<>(base()))) {
            admin.createTopics(List.of(new NewTopic(name, partitions, (short) 1))).all().get();
        }
    }

    static void produce(String topic, int n) {
        var props = new java.util.HashMap<String, Object>(base());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var p = new KafkaProducer<String, String>(props)) {
            for (int i = 0; i < n; i++) {
                p.send(new ProducerRecord<>(topic, "parcel-" + (i % 10), "scan-" + i));
            }
            p.flush();
        }
    }

    static Map<String, Object> consumerProps(String group) {
        var props = new java.util.HashMap<String, Object>(base());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    @Test
    void classicGroupLeavesOneConsumerIdle() throws Exception {
        String topic = "spike-classic";
        createTopic(topic, 3);
        produce(topic, 30);

        var consumers = new ArrayList<KafkaConsumer<String, String>>();
        for (int i = 0; i < 4; i++) {
            var c = new KafkaConsumer<String, String>(consumerProps("spike-classic-group"));
            c.subscribe(List.of(topic));
            consumers.add(c);
        }
        var counts = new TreeMap<Integer, Integer>();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            int total = 0;
            for (int i = 0; i < consumers.size(); i++) {
                var recs = consumers.get(i).poll(Duration.ofMillis(500));
                counts.merge(i, recs.count(), Integer::sum);
                total += consumers.get(i).assignment().size();
            }
            if (total == 3 && counts.values().stream().mapToInt(Integer::intValue).sum() >= 30) {
                break;
            }
        }
        System.out.println("=== CLASSIC assignments ===");
        for (int i = 0; i < consumers.size(); i++) {
            System.out.println("  consumer " + i + " assignment=" + consumers.get(i).assignment()
                    + " records=" + counts.get(i));
        }
        consumers.forEach(KafkaConsumer::close);
    }

    @Test
    void shareGroupFeedsEveryConsumer() throws Exception {
        String topic = "spike-share";
        String group = "spike-share-group";
        createTopic(topic, 3);

        // try setting the group-level reset via admin
        try (Admin admin = Admin.create(new java.util.HashMap<>(base()))) {
            var resource = new ConfigResource(ConfigResource.Type.GROUP, group);
            admin.incrementalAlterConfigs(Map.of(resource, List.of(
                    new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"),
                            AlterConfigOp.OpType.SET)))).all().get();
            System.out.println("=== SHARE group config set OK ===");
        } catch (Exception e) {
            System.out.println("=== SHARE group config FAILED: " + e);
        }

        produce(topic, 120);

        var props = new java.util.HashMap<String, Object>(base());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");

        var consumers = new ArrayList<KafkaShareConsumer<String, String>>();
        for (int i = 0; i < 4; i++) {
            var c = new KafkaShareConsumer<String, String>(new java.util.HashMap<>(props));
            c.subscribe(List.of(topic));
            consumers.add(c);
        }
        var counts = new TreeMap<Integer, Integer>();
        var total = new AtomicInteger();
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline && total.get() < 120) {
            for (int i = 0; i < consumers.size(); i++) {
                var c = consumers.get(i);
                var recs = c.poll(Duration.ofMillis(500));
                recs.forEach(c::acknowledge);
                if (recs.count() > 0) {
                    c.commitSync();
                }
                counts.merge(i, recs.count(), Integer::sum);
                total.addAndGet(recs.count());
            }
        }
        System.out.println("=== SHARE counts (total=" + total.get() + ") ===");
        counts.forEach((k, v) -> System.out.println("  share consumer " + k + " records=" + v));
        consumers.forEach(KafkaShareConsumer::close);
    }

    @Test
    void rabbitRoutes() throws Exception {
        try (var rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine")) {
            rabbit.start();
            var factory = new ConnectionFactory();
            factory.setUri(rabbit.getAmqpUrl());
            try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                ch.exchangeDeclare("parcel.events", BuiltinExchangeType.TOPIC, true);
                ch.queueDeclare("notify.customer", true, false, false, null);
                ch.queueBind("notify.customer", "parcel.events", "scan.delivered.*");
                ch.basicPublish("parcel.events", "scan.delivered.fi", null, "p-1".getBytes());
                ch.basicPublish("parcel.events", "scan.in_transit.fi", null, "p-2".getBytes());
                Thread.sleep(500);
                GetResponse first = ch.basicGet("notify.customer", false);
                System.out.println("=== RABBIT got=" + (first == null ? "null" : new String(first.getBody())));
                if (first != null) {
                    ch.basicAck(first.getEnvelope().getDeliveryTag(), false);
                }
                GetResponse second = ch.basicGet("notify.customer", false);
                System.out.println("=== RABBIT second=" + (second == null ? "null (routing filtered it)" : new String(second.getBody())));
                System.out.println("=== RABBIT queue class check: " + ch.queueDeclarePassive("notify.customer").getMessageCount());
            }
        }
    }
}
