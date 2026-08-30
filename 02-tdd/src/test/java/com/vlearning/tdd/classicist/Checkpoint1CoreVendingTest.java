package com.vlearning.tdd.classicist;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.vlearning.tdd.classicist.Coin.DIME;
import static com.vlearning.tdd.classicist.Coin.NICKEL;
import static com.vlearning.tdd.classicist.Coin.PENNY;
import static com.vlearning.tdd.classicist.Coin.QUARTER;
import static com.vlearning.tdd.classicist.Product.CANDY;
import static com.vlearning.tdd.classicist.Product.COLA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance checkpoint for step 1 — core vending. This is NOT your unit test
 * suite: it only tells you when the step is done. The tests that drive your
 * design are the ones you write yourself in {@link VendingMachineTest}.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1CoreVendingTest {

    private final VendingMachine machine = serviceableMachine();

    /** A machine a technician has prepared: everything stocked, plenty of change. */
    private static VendingMachine serviceableMachine() {
        var machine = new VendingMachine();
        for (Product product : Product.values()) {
            machine.stock(product, 5);
        }
        machine.loadChange(NICKEL, 10);
        machine.loadChange(DIME, 10);
        machine.loadChange(QUARTER, 10);
        return machine;
    }

    @Test
    void displaysTheRunningBalanceAsCoinsDrop() {
        machine.insert(NICKEL);
        assertThat(machine.display()).isEqualTo("$0.05");

        machine.insert(DIME);
        assertThat(machine.display()).isEqualTo("$0.15");

        machine.insert(QUARTER);
        assertThat(machine.display()).isEqualTo("$0.40");
    }

    @Test
    void sendsPenniesToTheCoinReturn() {
        machine.insert(PENNY);

        assertThat(machine.display()).isEqualTo("INSERT COIN");
        assertThat(machine.takeCoinReturn()).containsExactly(PENNY);
    }

    @Test
    void dispensesWhenEnoughMoneyIsIn() {
        machine.insert(QUARTER);
        machine.insert(QUARTER);
        machine.insert(QUARTER);
        machine.insert(QUARTER);

        machine.selectProduct(COLA);

        assertThat(machine.takeDispenseBin()).containsExactly(COLA);
        assertThat(machine.display()).isEqualTo("THANK YOU");   // one-shot message…
        assertThat(machine.display()).isEqualTo("INSERT COIN"); // …then back to idle
    }

    @Test
    void showsThePriceThenTheBalanceWhenShortOfMoney() {
        machine.insert(QUARTER);

        machine.selectProduct(COLA);

        assertThat(machine.takeDispenseBin()).isEmpty();
        assertThat(machine.display()).isEqualTo("PRICE $1.00");
        assertThat(machine.display()).isEqualTo("$0.25");
    }

    @Test
    void exactPaymentLeavesTheCoinReturnEmpty() {
        machine.insert(QUARTER);
        machine.insert(QUARTER);

        machine.selectProduct(CANDY);

        assertThat(machine.takeDispenseBin()).containsExactly(CANDY);
        assertThat(machine.takeCoinReturn()).isEmpty();
    }
}
