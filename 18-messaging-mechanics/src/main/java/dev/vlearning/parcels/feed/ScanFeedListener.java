package dev.vlearning.parcels.feed;

import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.wire.JsonCodec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The stream side: a classic consumer group over a durable log. Every member owns whole
 * partitions, offsets advance in order, and replay is a matter of moving an offset.
 */
@Component
public class ScanFeedListener {

    private final ScanFeed feed;
    private final JsonCodec codec;

    ScanFeedListener(ScanFeed feed, JsonCodec codec) {
        this.feed = feed;
        this.codec = codec;
    }

    @KafkaListener(topics = "${parcels.topics.scans}", groupId = "${parcels.feed-group}")
    void onScan(ConsumerRecord<String, String> record) {
        feed.record(record.partition(), record.offset(), codec.fromJson(record.value(), ParcelScan.class));
    }
}
