package dev.vlearning.parcels.notify;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What actually happened to each work item — the evidence a retry/DLQ test asserts against. */
@Component
public class NotifyLedger {

    public record DeadLetter(String taskId, String cause, String originalTopic) {
    }

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final List<String> attempted = new CopyOnWriteArrayList<>();
    private final List<DeadLetter> deadLettered = new CopyOnWriteArrayList<>();

    public void attempted(NotifyCustomer task) {
        attempted.add(task.taskId());
    }

    public void sent(NotifyCustomer task) {
        sent.add(task.taskId());
    }

    public void deadLettered(String taskId, String cause, String originalTopic) {
        deadLettered.add(new DeadLetter(taskId, cause, originalTopic));
    }

    public List<String> sentTasks() {
        return List.copyOf(sent);
    }

    public List<String> attempts() {
        return List.copyOf(attempted);
    }

    public long attemptsFor(String taskId) {
        return attempted.stream().filter(taskId::equals).count();
    }

    public List<DeadLetter> deadLetters() {
        return List.copyOf(deadLettered);
    }

    public void clear() {
        sent.clear();
        attempted.clear();
        deadLettered.clear();
    }
}
