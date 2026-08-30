package com.vlearning.bdd.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Acceptance gate for step 5. These are the four rulings that are easiest to get subtly
 * wrong in code even after the session agreed them -- they are not a substitute for the
 * unit tests you write yourself while implementing.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5PricingMathTest {

    private final PricingService pricing = new PricingService(new PromoCatalog());

    @Test
    void tierDiscountAppliesOnlyStrictlyAboveOneHundredEuros() {
        assertThat(pricing.price(Order.of("100.00"), Member.member(0)).discount())
                .isEqualByComparingTo("0.00");
        assertThat(pricing.price(Order.of("100.01"), Member.member(0)).discount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void percentageDiscountsRoundHalfUpToTheNearestCent() {
        assertThat(pricing.price(Order.of("107.55"), Member.member(0)).discount())
                .isEqualByComparingTo("10.76");
        assertThat(pricing.price(Order.of("149.99"), Member.gold(0)).discount())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void pointsAreEarnedPerWholeTenEurosOfCashPaid() {
        PricingResult result = pricing.price(Order.of("39.99"), Member.member(0));

        assertThat(result.amountDue()).isEqualByComparingTo("39.99");
        assertThat(result.pointsEarned()).isEqualTo(3);
    }

    @Test
    void redemptionNeverPushesTheAmountDueBelowZero() {
        PricingResult result = pricing.price(
                Order.of("120.00").payingWithPoints(2000), Member.member(2000));

        assertThat(result.pointsCredit()).isEqualByComparingTo("100.00");
        assertThat(result.amountDue()).isEqualByComparingTo("8.00");
        assertThat(result.pointsRedeemed()).isEqualTo(1000);
        assertThat(result.remainingPointBalance()).isEqualTo(1000);
    }

    @Test
    void theCustomerGetsTheBetterOfTierDiscountAndPromoCodeButNeverBoth() {
        assertThat(pricing.price(Order.of("200.00").withPromoCode("WELCOME15"), Member.gold(0)).discount())
                .isEqualByComparingTo("40.00");
        assertThat(pricing.price(Order.of("200.00").withPromoCode("WELCOME15"), Member.member(0)).discount())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void guestsCannotRedeemPoints() {
        Throwable thrown = catchThrowable(
                () -> pricing.price(Order.of("120.00").payingWithPoints(100), Member.guest()));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only members can redeem points");
    }
}
