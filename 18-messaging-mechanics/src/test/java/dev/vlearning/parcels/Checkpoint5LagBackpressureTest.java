package dev.vlearning.parcels;

import dev.vlearning.parcels.feed.BackpressuredScanReader;
import dev.vlearning.parcels.support.KafkaSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 5 — lag is a queue you cannot see. Measure it, then bound it: a consumer that
 * buffers without limit has not solved backpressure, it has moved the queue into your heap.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5LagBackpressureTest {

    private static final int RECORDS = 200;
    private static final int IN_FLIGHT_LIMIT = 20;

    private static List<Map.Entry<String, String>> batch() {
        var work = new ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < RECORDS; i++) {
            work.add(Map.entry("P-" + i, "scan-" + i));
        }
        return work;
    }

    @Test
    void lagIsVisibleFromInsideTheConsumer() {
        String topic = KafkaSupport.freshTopic("cp5-lag", 3);
        KafkaSupport.send(topic, batch(), Duration.ZERO);

        try (var reader = new BackpressuredScanReader(KafkaSupport.consumerProps("cp5-lag-group"),
                topic, IN_FLIGHT_LIMIT, Duration.ofMillis(10))) {
            reader.start();

            long peakLag = 0;
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline && reader.processed() < RECORDS) {
                peakLag = Math.max(peakLag, reader.lag());
                KafkaSupport.sleep(Duration.ofMillis(50));
            }

            assertThat(peakLag).as("a slow consumer falls behind, measurably").isGreaterThan(50);
            assertThat(reader.processed()).as("and then catches up without losing anything").isEqualTo(RECORDS);
            assertThat(KafkaSupport.committedLag("cp5-lag-group", topic))
                    .as("lag recovers to zero")
                    .isZero();
        }
    }

    @Test
    void backpressureBoundsTheBufferInsteadOfTheHeap() {
        String topic = KafkaSupport.freshTopic("cp5-backpressure", 3);
        KafkaSupport.send(topic, batch(), Duration.ZERO);

        try (var reader = new BackpressuredScanReader(KafkaSupport.consumerProps("cp5-bp-group"),
                topic, IN_FLIGHT_LIMIT, Duration.ofMillis(10))) {
            reader.start();

            assertThat(reader.awaitProcessed(RECORDS, Duration.ofSeconds(60)))
                    .as("everything still gets processed — pausing is not dropping")
                    .isTrue();
            assertThat(reader.maxInFlight())
                    .as("the hand-off buffer never exceeded its bound")
                    .isLessThanOrEqualTo(IN_FLIGHT_LIMIT);
            assertThat(reader.everPaused())
                    .as("and the way you achieved that was pause/resume, not a bigger queue")
                    .isTrue();
            assertThat(reader.assignmentEvents())
                    .as("a paused consumer keeps polling, so it never leaves the group")
                    .isEqualTo(1);
        }
    }
}
