package dev.vlearning.quotes.domain;

import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint 2. Look at the imports: no Spring, no web, no database. This
 * class exercises the SAME rules the integration tests pin over HTTP — but
 * it runs in milliseconds, without a context, a port, or a schema.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class QuoteCalculatorTest {

    private final QuoteCalculator calculator = new QuoteCalculator();

    @Test
    void standardAdultPaysTheBaseRate() {
        Money premium = calculator.monthlyPremium(Money.euros("90.00"), new RiskProfile(30, Set.of()));

        assertThat(premium).isEqualTo(Money.euros("90.00"));
    }

    @Test
    void under25AddsThirtyPercent() {
        Money premium = calculator.monthlyPremium(Money.euros("100.00"), new RiskProfile(24, Set.of()));

        assertThat(premium).isEqualTo(Money.euros("130.00"));
    }

    @Test
    void exactly25PaysTheBaseRate() {
        Money premium = calculator.monthlyPremium(Money.euros("100.00"), new RiskProfile(25, Set.of()));

        assertThat(premium).isEqualTo(Money.euros("100.00"));
    }

    @Test
    void seventyAndOverAddsTwentyPercent() {
        Money premium = calculator.monthlyPremium(Money.euros("45.50"), new RiskProfile(70, Set.of()));

        assertThat(premium).isEqualTo(Money.euros("54.60"));
    }

    @Test
    void smokerAddsTwentyPercent() {
        Money premium = calculator.monthlyPremium(Money.euros("120.00"),
                new RiskProfile(40, Set.of(RiskFactor.SMOKER)));

        assertThat(premium).isEqualTo(Money.euros("144.00"));
    }

    @Test
    void hazardousOccupationAddsFifteenPercent() {
        Money premium = calculator.monthlyPremium(Money.euros("100.00"),
                new RiskProfile(40, Set.of(RiskFactor.HAZARDOUS_OCCUPATION)));

        assertThat(premium).isEqualTo(Money.euros("115.00"));
    }

    @Test
    void previousClaimsAddFortyPercent() {
        Money premium = calculator.monthlyPremium(Money.euros("100.00"),
                new RiskProfile(40, Set.of(RiskFactor.PREVIOUS_CLAIMS)));

        assertThat(premium).isEqualTo(Money.euros("140.00"));
    }

    @Test
    void allLoadsStackAdditively() {
        Money premium = calculator.monthlyPremium(Money.euros("90.00"),
                new RiskProfile(23, Set.of(RiskFactor.SMOKER, RiskFactor.HAZARDOUS_OCCUPATION, RiskFactor.PREVIOUS_CLAIMS)));

        // 90.00 * (1 + 0.30 + 0.20 + 0.15 + 0.40) = 184.50
        assertThat(premium).isEqualTo(Money.euros("184.50"));
    }

    @Test
    void premiumsRoundHalfUpToCents() {
        // 33.33 * 1.20 = 39.996 -> 40.00
        Money premium = calculator.monthlyPremium(Money.euros("33.33"),
                new RiskProfile(40, Set.of(RiskFactor.SMOKER)));

        assertThat(premium).isEqualTo(Money.euros("40.00"));
    }

    @Test
    void underageApplicantsCannotEvenBeDescribed() {
        assertThatThrownBy(() -> new RiskProfile(17, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eighteenIsOldEnough() {
        assertThatCode(() -> new RiskProfile(18, Set.of())).doesNotThrowAnyException();
    }
}
