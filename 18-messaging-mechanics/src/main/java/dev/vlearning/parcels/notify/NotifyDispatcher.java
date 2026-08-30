package dev.vlearning.parcels.notify;

import dev.vlearning.parcels.ParcelsProperties;
import dev.vlearning.parcels.wire.JsonCodec;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class NotifyDispatcher {

    private final KafkaTemplate<String, String> template;
    private final JsonCodec codec;
    private final ParcelsProperties properties;

    NotifyDispatcher(KafkaTemplate<String, String> template, JsonCodec codec, ParcelsProperties properties) {
        this.template = template;
        this.codec = codec;
        this.properties = properties;
    }

    public void dispatch(NotifyCustomer task) {
        var record = new ProducerRecord<>(properties.topics().tasks(), task.taskId(), codec.toJson(task));
        template.send(record).orTimeout(20, TimeUnit.SECONDS).join();
    }
}
