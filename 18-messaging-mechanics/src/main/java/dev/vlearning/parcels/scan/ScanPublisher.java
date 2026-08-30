package dev.vlearning.parcels.scan;

import dev.vlearning.parcels.ParcelsProperties;
import dev.vlearning.parcels.wire.JsonCodec;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class ScanPublisher {

    private final KafkaTemplate<String, String> template;
    private final JsonCodec codec;
    private final ParcelsProperties properties;

    ScanPublisher(KafkaTemplate<String, String> template, JsonCodec codec, ParcelsProperties properties) {
        this.template = template;
        this.codec = codec;
        this.properties = properties;
    }

    /** Publish with the default key strategy (one parcel, one ordering domain). */
    public SendResult<String, String> publish(ParcelScan scan) {
        return publish(scan, PartitionKeys::byParcel);
    }

    public SendResult<String, String> publish(ParcelScan scan, Function<ParcelScan, String> key) {
        var record = new ProducerRecord<>(properties.topics().scans(), key.apply(scan), codec.toJson(scan));
        return template.send(record).orTimeout(20, TimeUnit.SECONDS).join();
    }
}
