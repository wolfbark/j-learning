package com.vlearning.bdd.pricing;

import java.math.BigDecimal;

/**
 * The priced order. Every field here exists because some example in the mapping session
 * asked about it -- if you find yourself wanting another one, you found another example.
 *
 * @param subtotal             goods total, as submitted
 * @param discount             money taken off by the winning percentage discount
 * @param pointsCredit         money paid for by redeemed loyalty points
 * @param amountDue            cash the customer actually pays
 * @param pointsRedeemed       points actually consumed (may be less than requested)
 * @param pointsEarned         points awarded for this order
 * @param remainingPointBalance balance after redeeming and earning
 */
public record PricingResult(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal pointsCredit,
        BigDecimal amountDue,
        int pointsRedeemed,
        int pointsEarned,
        int remainingPointBalance) {
}
