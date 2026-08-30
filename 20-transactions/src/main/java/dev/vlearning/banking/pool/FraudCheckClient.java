package dev.vlearning.banking.pool;

import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * Stands in for the HTTP call to a scoring service. The latency is the point;
 * the score is not.
 */
@Component
public class FraudCheckClient {

    public static final Duration LATENCY = Duration.ofMillis(400);

    /** A score above this means "do not credit the deposit". */
    public static final int REJECT_ABOVE = 50;

    public int score(long accountId, long amount) {
        try {
            Thread.sleep(LATENCY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return (int) ((accountId * 31 + amount) % 100);
    }
}
