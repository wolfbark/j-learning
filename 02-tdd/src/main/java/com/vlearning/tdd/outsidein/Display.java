package com.vlearning.tdd.outsidein;

/**
 * Push-model display: the controller tells it what to show, when.
 * (Contrast with the classicist round, where tests <em>pull</em> the display.)
 */
public interface Display {

    void show(String message);
}
