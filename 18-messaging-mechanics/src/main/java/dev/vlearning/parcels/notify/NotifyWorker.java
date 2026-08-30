package dev.vlearning.parcels.notify;

import dev.vlearning.parcels.wire.JsonCodec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The work-queue side, deliberately naive: no error classification, no bounded retry, no dead
 * letter. Whatever the framework's default error handling does is what this worker does — which
 * is precisely the state most services ship in. Step 6 is where you fix that.
 */
@Component
public class NotifyWorker {

    private final FlakyChannel channel;
    private final NotifyLedger ledger;
    private final JsonCodec codec;

    NotifyWorker(FlakyChannel channel, NotifyLedger ledger, JsonCodec codec) {
        this.channel = channel;
        this.ledger = ledger;
        this.codec = codec;
    }

    @KafkaListener(topics = "${parcels.topics.tasks}", groupId = "${parcels.notify-group}")
    void onTask(ConsumerRecord<String, String> record) {
        var task = codec.fromJson(record.value(), NotifyCustomer.class);
        ledger.attempted(task);
        channel.send(task);
        ledger.sent(task);
    }
}
