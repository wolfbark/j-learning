package dev.vlearning.parcels;

import dev.vlearning.parcels.notify.FlakyChannel;
import dev.vlearning.parcels.notify.NotifyCustomer;
import dev.vlearning.parcels.notify.NotifyDispatcher;
import dev.vlearning.parcels.notify.NotifyLedger;
import dev.vlearning.parcels.support.KafkaSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 6 — bounded retries and a dead-letter topic that carries evidence.
 *
 * <p>Three kinds of failure, three correct answers: a transient channel outage should be retried
 * a bounded number of times; a work item that can never succeed should go straight to the DLQ;
 * and healthy items must keep flowing while both of those happen.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
@SpringBootTest
class Checkpoint6RetryDlqTest {

    private static final String TASKS = "cp6.notify.tasks";
    private static final String DLQ = "cp6.notify.tasks.DLQ";

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaSupport::bootstrapServers);
        registry.add("parcels.topics.scans", () -> "cp6.parcels.scans");
        registry.add("parcels.topics.tasks", () -> TASKS);
        registry.add("parcels.topics.dlq", () -> DLQ);
        registry.add("parcels.feed-group", () -> "cp6-feed");
        registry.add("parcels.notify-group", () -> "cp6-notify-workers");
    }

    @Autowired
    private NotifyDispatcher dispatcher;

    @Autowired
    private NotifyLedger ledger;

    @Autowired
    private FlakyChannel channel;

    @BeforeEach
    void reset() {
        ledger.clear();
        channel.reset();
    }

    @Test
    void aPoisonTaskIsParkedWithItsCauseWhileGoodTasksKeepFlowing() {
        var poison = new NotifyCustomer("task-poison", "P-1", "C-1", "carrier-pigeon", "delivered");
        dispatcher.dispatch(poison);
        dispatcher.dispatch(NotifyCustomer.sms("P-2", "C-2", "delivered"));
        dispatcher.dispatch(NotifyCustomer.sms("P-3", "C-3", "delivered"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(ledger.sentTasks()).contains("P-2-notify", "P-3-notify"));

        assertThat(ledger.attemptsFor("task-poison"))
                .as("a non-retryable failure is retried zero times")
                .isEqualTo(1);

        var parked = KafkaSupport.drain(DLQ, 1, Duration.ofSeconds(20)).stream()
                .filter(record -> record.value().contains("task-poison"))
                .toList();
        assertThat(parked).as("the poison task landed in the DLQ").hasSize(1);

        var record = parked.getFirst();
        assertThat(record.value()).contains("carrier-pigeon");
        assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                .as("with the cause attached, so the on-call engineer does not have to guess")
                .contains("Unknown notification channel");
        assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TASKS);
    }

    @Test
    void aTransientFailureIsRetriedAndThenSucceeds() {
        channel.armTransientFailures(2);
        var task = NotifyCustomer.sms("P-9", "C-9", "delivered");

        dispatcher.dispatch(task);

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(ledger.sentTasks()).contains(task.taskId()));
        assertThat(ledger.attemptsFor(task.taskId()))
                .as("bounded: three attempts, not three hundred")
                .isEqualTo(3);
        assertThat(KafkaSupport.drain(DLQ, 5, Duration.ofSeconds(5)))
                .as("a task that eventually succeeded must not be dead-lettered")
                .noneMatch(record -> record.value().contains(task.taskId()));
    }

    private static String header(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
                                 String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
