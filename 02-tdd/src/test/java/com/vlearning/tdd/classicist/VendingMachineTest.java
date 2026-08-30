package com.vlearning.tdd.classicist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Your working test class for the classicist rounds (steps 1 and 2).
 *
 * <p>The first Canon TDD cycle is already done — this test was red, then
 * {@code display()} was faked green (see the README walk-through). Take the
 * next item from {@code TESTLIST.md} and continue: exactly one failing test
 * at a time.
 */
class VendingMachineTest {

    @Test
    void idleMachineAsksForACoin() {
        var machine = new VendingMachine();

        assertThat(machine.display()).isEqualTo("INSERT COIN");
    }
}
