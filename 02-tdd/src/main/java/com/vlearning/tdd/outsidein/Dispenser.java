package com.vlearning.tdd.outsidein;

/** The spiral-motor rack that holds and drops products. */
public interface Dispenser {

    boolean canDispense(Product product);

    void dispense(Product product);
}
