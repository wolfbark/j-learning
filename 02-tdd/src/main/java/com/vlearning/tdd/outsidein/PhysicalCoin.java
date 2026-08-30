package com.vlearning.tdd.outsidein;

/**
 * What the coin slot hardware actually hands us: an unidentified metal disc.
 * Deciding whether it is money is a job — see {@link CoinValidator}.
 */
public record PhysicalCoin(double weightGrams, double diameterMillimetres) {

    // US Mint specifications — handy for tests and fakes.
    public static final PhysicalCoin NICKEL_SIZED  = new PhysicalCoin(5.000, 21.21);
    public static final PhysicalCoin DIME_SIZED    = new PhysicalCoin(2.268, 17.91);
    public static final PhysicalCoin QUARTER_SIZED = new PhysicalCoin(5.670, 24.26);
}
