package com.vlearning.tdd.classicist;

/**
 * What the machine sells. Prices in cents — this kata never touches
 * floating-point money.
 */
public enum Product {
    COLA(100), CHIPS(65), CANDY(50);

    private final int priceInCents;

    Product(int priceInCents) {
        this.priceInCents = priceInCents;
    }

    public int priceInCents() {
        return priceInCents;
    }
}
