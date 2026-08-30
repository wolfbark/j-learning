package com.vlearning.tdd.classicist;

/**
 * The denominations the coin slot can physically accept. The machine only
 * <em>credits</em> nickels, dimes and quarters — a {@link #PENNY} exists so
 * there is something to reject.
 */
public enum Coin {
    PENNY(1), NICKEL(5), DIME(10), QUARTER(25);

    private final int valueInCents;

    Coin(int valueInCents) {
        this.valueInCents = valueInCents;
    }

    public int valueInCents() {
        return valueInCents;
    }
}
