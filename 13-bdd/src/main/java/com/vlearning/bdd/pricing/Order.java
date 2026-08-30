package com.vlearning.bdd.pricing;

import java.math.BigDecimal;

/**
 * What the customer is trying to buy. Line-item maths is out of scope for this lesson:
 * {@code subtotal} is the goods total in euros, shipping excluded.
 */
public record Order(BigDecimal subtotal, String promoCode, int pointsToRedeem) {

    public Order {
        if (subtotal == null || subtotal.signum() < 0) {
            throw new IllegalArgumentException("subtotal must be zero or more");
        }
        if (pointsToRedeem < 0) {
            throw new IllegalArgumentException("points to redeem cannot be negative");
        }
    }

    public static Order of(BigDecimal subtotal) {
        return new Order(subtotal, null, 0);
    }

    public static Order of(String subtotal) {
        return of(new BigDecimal(subtotal));
    }

    public Order withPromoCode(String code) {
        return new Order(subtotal, code, pointsToRedeem);
    }

    public Order payingWithPoints(int points) {
        return new Order(subtotal, promoCode, points);
    }
}
