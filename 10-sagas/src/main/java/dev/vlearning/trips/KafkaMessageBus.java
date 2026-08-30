package dev.vlearning.trips;

import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage;
import dev.vlearning.trips.messages.TripMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed {@link MessageBus}. Every outgoing message is logged — in step 3
 * these log lines and the database tables are ALL you get to answer "where is
 * booking #42 stuck?". Notice there is no transaction spanning the database
 * write and this send: the dual-write problem is real here and deliberately
 * ignored — lesson 07 (transactional outbox) is the fix, and production sagas
 * assume it is in place.
 */
@Component
class KafkaMessageBus implements MessageBus {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageBus.class);

    // Boot 4 declares its auto-configured template as KafkaTemplate<Object, Object>;
    // with the default String serializers it carries our String envelopes just fine.
    private final KafkaTemplate<Object, Object> kafka;
    private final TripMessageCodec codec;

    KafkaMessageBus(KafkaTemplate<Object, Object> kafka, TripMessageCodec codec) {
        this.kafka = kafka;
        this.codec = codec;
    }

    @Override
    public void publish(String topic, TripMessage message) {
        String json = codec.encode(message);
        log.info("→ {} {}", topic, json);
        kafka.send(topic, message.tripId().toString(), json);
    }
}
