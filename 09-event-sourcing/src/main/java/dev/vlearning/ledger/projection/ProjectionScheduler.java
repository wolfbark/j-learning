package dev.vlearning.ledger.projection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Given: drives the balances projector when polling is switched on
 * ({@code ledger.projections.polling=true} — off by default so checkpoint tests can drive
 * {@code runOnce()} deterministically). Polling a global sequence is the simplest
 * subscription model; the lesson text covers what real systems use instead.
 */
@Component
@ConditionalOnProperty(name = "ledger.projections.polling", havingValue = "true")
public class ProjectionScheduler {

    private final BalancesProjector balancesProjector;

    public ProjectionScheduler(BalancesProjector balancesProjector) {
        this.balancesProjector = balancesProjector;
    }

    @Scheduled(fixedDelayString = "${ledger.projections.poll-interval-ms:200}")
    void poll() {
        while (balancesProjector.runOnce() > 0) {
            // keep draining until caught up
        }
    }
}
