package com.vlearning.tdd.outsidein;

import java.util.List;

/** The chute and tray at the bottom of the machine. */
public interface CoinReturn {

    /** Pass an unrecognised object straight through, untouched. */
    void reject(PhysicalCoin object);

    /** Pay out coins from the machine: change, or a refunded escrow. */
    void release(List<Coin> coins);
}
