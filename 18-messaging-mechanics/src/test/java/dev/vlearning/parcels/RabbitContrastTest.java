package dev.vlearning.parcels;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import dev.vlearning.parcels.support.KafkaSupport;
import dev.vlearning.parcels.support.RabbitSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enabled by default: the other broker model. RabbitMQ is a <em>smart broker</em> — routing,
 * per-message state and redelivery live in the broker, and the consumer is deliberately dumb.
 * Kafka is a dumb broker with smart consumers: it appends bytes and remembers offsets.
 */
class RabbitContrastTest {

    @Test
    void theBrokerRoutesAndTheConsumerAcksOneMessageAtATime() throws Exception {
        try (var connection = RabbitSupport.connect(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare("parcel.scans", BuiltinExchangeType.TOPIC, true);
            channel.exchangeDeclare("parcel.scans.dlx", BuiltinExchangeType.FANOUT, true);
            channel.queueDeclare("notify.parked", true, false, false, Map.of());
            channel.queueBind("notify.parked", "parcel.scans.dlx", "");

            // A quorum queue: replicated via Raft, the 4.x default for anything that matters.
            channel.queueDeclare("notify.delivered", true, false, false, Map.of(
                    "x-queue-type", "quorum",
                    "x-dead-letter-exchange", "parcel.scans.dlx"));
            channel.queueBind("notify.delivered", "parcel.scans", "scan.delivered.#");

            publish(channel, "scan.delivered.fi", "P-1");
            publish(channel, "scan.in_transit.fi", "P-2");
            KafkaSupport.sleep(Duration.ofMillis(500));

            var delivered = channel.basicGet("notify.delivered", false);
            assertThat(delivered).as("only messages whose routing key matched were queued").isNotNull();
            assertThat(new String(delivered.getBody(), StandardCharsets.UTF_8)).isEqualTo("P-1");
            channel.basicAck(delivered.getEnvelope().getDeliveryTag(), false);

            assertThat(channel.basicGet("notify.delivered", false))
                    .as("the non-matching message was dropped by the exchange, not filtered by us")
                    .isNull();
        }
    }

    @Test
    void rejectingAMessageDeadLettersItWithoutAnyCodeOfYours() throws Exception {
        try (var connection = RabbitSupport.connect(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare("notify.work", BuiltinExchangeType.DIRECT, true);
            channel.exchangeDeclare("notify.work.dlx", BuiltinExchangeType.FANOUT, true);
            channel.queueDeclare("notify.dead", true, false, false, Map.of());
            channel.queueBind("notify.dead", "notify.work.dlx", "");
            channel.queueDeclare("notify.tasks", true, false, false, Map.of(
                    "x-dead-letter-exchange", "notify.work.dlx"));
            channel.queueBind("notify.tasks", "notify.work", "sms");

            channel.basicPublish("notify.work", "sms", null, "poison".getBytes(StandardCharsets.UTF_8));
            KafkaSupport.sleep(Duration.ofMillis(300));

            var task = channel.basicGet("notify.tasks", false);
            assertThat(task).isNotNull();
            channel.basicNack(task.getEnvelope().getDeliveryTag(), false, false);
            KafkaSupport.sleep(Duration.ofMillis(500));

            var parked = channel.basicGet("notify.dead", true);
            assertThat(parked).as("nack(requeue=false) is the whole dead-letter implementation").isNotNull();
            assertThat(new String(parked.getBody(), StandardCharsets.UTF_8)).isEqualTo("poison");
        }
    }

    private static void publish(Channel channel, String routingKey, String body) throws Exception {
        channel.basicPublish("parcel.scans", routingKey, new AMQP.BasicProperties.Builder().build(),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
