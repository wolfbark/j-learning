package com.vlearning.tdd.outsidein;

import java.util.Optional;

/** Classifies a physical object as a denomination — or refuses to. */
public interface CoinValidator {

    Optional<Coin> classify(PhysicalCoin object);
}
