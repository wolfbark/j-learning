package dev.vlearning.trips.orchestration;

import java.time.Duration;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * The kill switch for checkpoint 5. {@link #crash()} pauses the orchestrator's
 * Kafka consumer — from the saga's point of view the coordinator is dead:
 * replies pile up in the topic, nothing advances. {@link #restart()} brings it
 * back. If the saga resumes correctly, you have proven the orchestrator keeps
 * NO state worth losing in memory — everything lives in {@code saga_instance}
 * plus the topic offsets. That property is durable execution, hand-rolled.
 *
 * <p>Requires your orchestrator's listener to be identifiable:
 * {@code @KafkaListener(id = "orchestrator", ...)}.
 */
@Component
public class OrchestratorSwitch {

    private static final Duration PAUSE_TIMEOUT = Duration.ofSeconds(15);

    private final KafkaListenerEndpointRegistry registry;

    public OrchestratorSwitch(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    /** Pause the orchestrator's consumer and wait until the pause has taken effect. */
    public void crash() {
        var container = container();
        container.pause();
        long deadline = System.nanoTime() + PAUSE_TIMEOUT.toNanos();
        while (!container.isContainerPaused()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Orchestrator container did not pause within " + PAUSE_TIMEOUT);
            }
            sleep();
        }
    }

    public void restart() {
        container().resume();
    }

    /** Test-hygiene helper: bring the orchestrator back if a failed test left it crashed. */
    public void restartIfCrashed() {
        var container = registry.getListenerContainer("orchestrator");
        if (container != null && (container.isPauseRequested() || container.isContainerPaused())) {
            container.resume();
        }
    }

    private MessageListenerContainer container() {
        var container = registry.getListenerContainer("orchestrator");
        if (container == null) {
            throw new IllegalStateException("""
                    No Kafka listener with id "orchestrator" found. \
                    Give your TripSagaOrchestrator's listener that id: @KafkaListener(id = "orchestrator", ...)""");
        }
        return container;
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the orchestrator to pause", e);
        }
    }
}
