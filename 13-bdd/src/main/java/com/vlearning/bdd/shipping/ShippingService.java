package com.vlearning.bdd.shipping;

import com.vlearning.bdd.pricing.MemberTier;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * The worked example for this lesson: a small rule that was already discovered,
 * agreed and specified. See {@code src/test/resources/features/free-shipping.feature}.
 */
@Service
public class ShippingService {

    static final BigDecimal FREE = new BigDecimal("0.00");
    static final BigDecimal STANDARD_RATE = new BigDecimal("4.95");
    static final BigDecimal FREE_SHIPPING_FROM = new BigDecimal("50.00");

    /**
     * @param amountCharged the cash the customer actually pays for the goods
     *                      (i.e. after discounts and point redemption)
     */
    public ShippingQuote quoteFor(BigDecimal amountCharged, MemberTier tier) {
        if (tier == MemberTier.GOLD) {
            return new ShippingQuote(FREE, ShippingQuote.Reason.GOLD_MEMBER);
        }
        if (amountCharged.compareTo(FREE_SHIPPING_FROM) >= 0) {
            return new ShippingQuote(FREE, ShippingQuote.Reason.ORDER_OVER_THRESHOLD);
        }
        return new ShippingQuote(STANDARD_RATE, ShippingQuote.Reason.STANDARD_RATE);
    }
}
