package dev.vlearning.parcels.notify;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A notification channel that fails the first {@code transientFailures} attempts per task and
 * then succeeds. Tests arm it; the worker just calls it.
 */
@Component
public class FlakyChannel {

    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private volatile int transientFailures;
    private volatile long processingDelayMillis;

    public void armTransientFailures(int failuresPerTask) {
        this.transientFailures = failuresPerTask;
    }

    public void slowDownBy(long millis) {
        this.processingDelayMillis = millis;
    }

    public void reset() {
        attempts.clear();
        transientFailures = 0;
        processingDelayMillis = 0;
    }

    public int attemptsFor(String taskId) {
        return attempts.getOrDefault(taskId, new AtomicInteger()).get();
    }

    public void send(NotifyCustomer task) {
        int attempt = attempts.computeIfAbsent(task.taskId(), k -> new AtomicInteger()).incrementAndGet();
        if (processingDelayMillis > 0) {
            try {
                Thread.sleep(processingDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChannelUnavailableException("interrupted while sending " + task.taskId());
            }
        }
        if (!"sms".equals(task.channel()) && !"email".equals(task.channel())) {
            throw new UnknownChannelException(task.channel());
        }
        if (attempt <= transientFailures) {
            throw new ChannelUnavailableException(
                    "channel " + task.channel() + " unavailable (attempt " + attempt + ")");
        }
    }
}
