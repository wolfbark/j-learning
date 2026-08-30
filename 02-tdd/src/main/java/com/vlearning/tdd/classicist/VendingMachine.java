package com.vlearning.tdd.classicist;

import java.util.List;

/**
 * The classicist kata subject, frozen exactly one red-green cycle in.
 *
 * <p>The public API below is fixed because the acceptance checkpoints in
 * {@code src/test/java} must compile against something — but only
 * {@link #display()} is implemented (the first cycle, demonstrated in the
 * README). Everything else is yours: work from {@code TESTLIST.md}, one
 * failing test at a time, in {@code VendingMachineTest}.
 */
public class VendingMachine {

    /**
     * What the front panel currently reads. Reading the display can advance it:
     * one-shot messages (THANK YOU, PRICE …, SOLD OUT) show once, then the
     * display reverts to the running balance or the idle message.
     */
    public String display() {
        return "INSERT COIN";
    }

    /** A coin drops into the slot. */
    public void insert(Coin coin) {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** A product button is pressed. */
    public void selectProduct(Product product) {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** The coin-return button is pressed. */
    public void pressCoinReturn() {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** Empties the coin return tray and hands you what was in it. */
    public List<Coin> takeCoinReturn() {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** Empties the dispense bin and hands you what was in it. */
    public List<Product> takeDispenseBin() {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** Service hatch: load {@code quantity} units of a product. */
    public void stock(Product product, int quantity) {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }

    /** Service hatch: load {@code quantity} coins into the change float. */
    public void loadChange(Coin coin, int quantity) {
        throw new UnsupportedOperationException("Drive me with a failing test first");
    }
}
