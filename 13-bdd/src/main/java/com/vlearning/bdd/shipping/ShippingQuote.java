package com.vlearning.bdd.shipping;

import java.math.BigDecimal;

/** What shipping costs for one order, and why. */
public record ShippingQuote(BigDecimal cost, Reason reason) {

    public enum Reason {
        GOLD_MEMBER,
        ORDER_OVER_THRESHOLD,
        STANDARD_RATE
    }

    public boolean isFree() {
        return cost.signum() == 0;
    }
}
