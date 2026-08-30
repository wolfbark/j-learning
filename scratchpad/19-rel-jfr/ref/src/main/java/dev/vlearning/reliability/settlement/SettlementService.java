package dev.vlearning.reliability.settlement;

import java.time.Duration;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import dev.vlearning.reliability.web.CorrelationIdFilter;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {

    private final SettlementAuditLog audit;
    private final RequestMetrics metrics;
    private final LatencyProfile latency;
    private final ChaosSwitch chaos;

    public SettlementService(SettlementAuditLog audit, RequestMetrics metrics,
                             LatencyProfile latency, ChaosSwitch chaos) {
        this.audit = audit;
        this.metrics = metrics;
        this.latency = latency;
        this.chaos = chaos;
    }

    public SettlementResult settle(SettlementCommand command) {
        audit.enteringSettle(command.orderId());
        long startedAt = System.nanoTime();

        sleepQuietly(latency.next(chaos.pathologicalShare()));
        boolean success = !chaos.shouldFail(command.orderId());

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        latency.record(took);
        metrics.recordRequest(command.userId(), command.orderId(), success ? 200 : 502);
        metrics.recordOutcome(success);
        audit.settled(command, took, success);

        if (!success) {
            throw new SettlementFailedException("settlement declined for " + command.orderId());
        }
        return new SettlementResult(command.orderId(), "SETTLED", took.toMillis(),
                CorrelationIdFilter.current());
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SettlementResult(String orderId, String status, long durationMillis,
                                   String correlationId) {
    }
}
