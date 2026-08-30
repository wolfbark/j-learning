package dev.vlearning.reliability;

import java.time.Duration;

import dev.vlearning.reliability.slo.ErrorBudget;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Step 7. The arithmetic that turns "we care about reliability" into a decision
 * you can make on a Tuesday: ship the risky change, or spend the week on
 * reliability work.
 *
 * <p>Numbers below use a 30-day window, 100 000 valid events and a 99.9%
 * objective, so the budget is exactly 100 bad events.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7ErrorBudgetTest {

    private static final Duration WINDOW = Duration.ofDays(30);
    private final ErrorBudget budget = ErrorBudget.of(0.999);

    @Test
    @DisplayName("a quiet month: three quarters of the budget still on the table")
    void withinBudget() {
        assertThat(budget.allowedBadEvents(100_000)).isCloseTo(100.0, within(1e-9));
        assertThat(budget.compliance(99_975, 25)).isCloseTo(0.99975, within(1e-9));
        assertThat(budget.remainingFraction(99_975, 25)).isCloseTo(0.75, within(1e-9));
        assertThat(budget.burnRate(99_975, 25)).isCloseTo(0.25, within(1e-9));
        assertThat(budget.exhausted(99_975, 25)).isFalse();
        assertThat(budget.timeToExhaustion(99_975, 25, WINDOW).orElseThrow())
                .as("burning a quarter as fast as allowed makes a 30-day budget last 120 days; "
                        + "with 75%% left, 90 more days")
                .isCloseTo(Duration.ofDays(90), Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("a bad month: budget gone, and the SLI still looks like 99.6%")
    void overBudget() {
        assertThat(budget.compliance(99_600, 400)).isCloseTo(0.996, within(1e-9));
        assertThat(budget.burnRate(99_600, 400)).isCloseTo(4.0, within(1e-9));
        assertThat(budget.remainingFraction(99_600, 400))
                .as("four times the permitted error rate is four budgets: three over")
                .isCloseTo(-3.0, within(1e-9));
        assertThat(budget.exhausted(99_600, 400)).isTrue();
        assertThat(budget.timeToExhaustion(99_600, 400, WINDOW)).contains(Duration.ZERO);
    }

    @Test
    @DisplayName("a perfect month burns nothing and never runs out")
    void nothingBurning() {
        assertThat(budget.compliance(100_000, 0)).isEqualTo(1.0);
        assertThat(budget.remainingFraction(100_000, 0)).isCloseTo(1.0, within(1e-9));
        assertThat(budget.burnRate(100_000, 0)).isZero();
        assertThat(budget.timeToExhaustion(100_000, 0, WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("burn rate is what you alert on, and it is window-independent")
    void burnRateIsTheAlertSignal() {
        // One hour, 10 000 requests, 144 of them bad.
        assertThat(budget.burnRate(9_856, 144))
                .as("14.4 is the canonical page-now threshold: at that rate a 30-day budget "
                        + "is gone in about two days, and over one hour it is 2%% of it")
                .isCloseTo(14.4, within(1e-6));

        // One minute, 100 requests, 1 bad. Same objective, tiny sample.
        assertThat(budget.burnRate(99, 1))
                .as("a single failed request is a burn rate of 10 over one minute — which is "
                        + "why you alert on burn rate over a window, never on one request")
                .isCloseTo(10.0, within(1e-6));
    }
}
