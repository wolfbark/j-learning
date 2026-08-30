package dev.vlearning.orders.chaos;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

/**
 * The application's one and only {@link KafkaTemplate}, with a kill switch.
 * When the chaos monkey has taken the broker "down", every send fails the way
 * a real send fails when the broker is unreachable or the process dies during
 * the relay: the future completes exceptionally and no record is written.
 */
public class ChaosKafkaTemplate extends KafkaTemplate<String, byte[]> {

    private final ChaosMonkey chaos;

    public ChaosKafkaTemplate(ProducerFactory<String, byte[]> producerFactory, ChaosMonkey chaos) {
        super(producerFactory);
        this.chaos = chaos;
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(String topic, byte[] data) {
        return brokenOr(() -> super.send(topic, data));
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(String topic, String key, byte[] data) {
        return brokenOr(() -> super.send(topic, key, data));
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(String topic, Integer partition, String key, byte[] data) {
        return brokenOr(() -> super.send(topic, partition, key, data));
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(ProducerRecord<String, byte[]> record) {
        return brokenOr(() -> super.send(record));
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(Message<?> message) {
        return brokenOr(() -> super.send(message));
    }

    private CompletableFuture<SendResult<String, byte[]>> brokenOr(
            java.util.function.Supplier<CompletableFuture<SendResult<String, byte[]>>> send) {
        if (chaos.brokerDown()) {
            return CompletableFuture.failedFuture(new ChaosException("broker unreachable (chaos monkey)"));
        }
        return send.get();
    }
}
