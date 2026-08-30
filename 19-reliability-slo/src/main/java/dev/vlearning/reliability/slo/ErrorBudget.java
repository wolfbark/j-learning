package dev.vlearning.reliability.slo;

import java.time.Duration;
import java.util.Optional;

/**
 * Step 7's exercise. An SLO is a number plus a window plus a consequence; an
 * error budget is what turns it from a slogan into a decision procedure.
 *
 * <p>Definitions, so the arithmetic is not ambiguous:
 * <ul>
 *   <li><b>SLI</b> — good events ÷ valid events.</li>
 *   <li><b>Budget</b> — the bad events the objective permits: (1 − objective) × valid.</li>
 *   <li><b>Remaining fraction</b> — 1.0 when untouched, 0.0 when exactly spent,
 *       negative when you are over. Report it as a fraction of the budget, not
 *       of traffic: "we have used 40% of the budget" is the sentence that makes
 *       people act.</li>
 *   <li><b>Burn rate</b> — observed error ratio ÷ (1 − objective). 1.0 means you
 *       will spend exactly the whole budget in exactly the window. 14.4 means
 *       you will spend a 30-day budget in about 50 minutes, which is why that
 *       number appears in every burn-rate alerting example.</li>
 * </ul>
 *
 * <p>Implement the methods; keep the signatures, the checkpoint test uses them.
 *
 * @param objective e.g. {@code 0.999} for three nines
 */
public record ErrorBudget(double objective) {

    public static ErrorBudget of(double objective) {
        if (objective <= 0 || objective >= 1) {
            throw new IllegalArgumentException("objective must be between 0 and 1: " + objective);
        }
        return new ErrorBudget(objective);
    }

    /** Bad events the objective permits over {@code valid} events. */
    public double allowedBadEvents(long valid) {
        throw new UnsupportedOperationException("Step 7");
    }

    /** The SLI: good ÷ valid. */
    public double compliance(long good, long bad) {
        throw new UnsupportedOperationException("Step 7");
    }

    /** 1.0 = untouched, 0.0 = exactly spent, negative = over budget. */
    public double remainingFraction(long good, long bad) {
        throw new UnsupportedOperationException("Step 7");
    }

    /** Observed error ratio ÷ (1 − objective). */
    public double burnRate(long good, long bad) {
        throw new UnsupportedOperationException("Step 7");
    }

    public boolean exhausted(long good, long bad) {
        throw new UnsupportedOperationException("Step 7");
    }

    /**
     * How long the remaining budget lasts at the observed burn rate, given the
     * SLO window: {@code (remainingFraction / burnRate) × window}. Empty when
     * nothing is burning; {@link Duration#ZERO} once the budget is gone.
     */
    public Optional<Duration> timeToExhaustion(long good, long bad, Duration window) {
        throw new UnsupportedOperationException("Step 7");
    }
}
