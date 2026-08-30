package com.vlearning.tdd.outsidein;

/**
 * A recognised denomination. Note there is no PENNY here: if the
 * {@link CoinValidator} cannot classify an object, it was never money.
 */
public enum Coin {
    NICKEL(5), DIME(10), QUARTER(25);

    private final int valueInCents;

    Coin(int valueInCents) {
        this.valueInCents = valueInCents;
    }

    public int valueInCents() {
        return valueInCents;
    }
}
