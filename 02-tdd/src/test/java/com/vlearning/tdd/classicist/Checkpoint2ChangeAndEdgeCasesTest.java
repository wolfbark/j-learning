package com.vlearning.tdd.classicist;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.vlearning.tdd.classicist.Coin.DIME;
import static com.vlearning.tdd.classicist.Coin.NICKEL;
import static com.vlearning.tdd.classicist.Coin.QUARTER;
import static com.vlearning.tdd.classicist.Product.CANDY;
import static com.vlearning.tdd.classicist.Product.CHIPS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance checkpoint for step 2 — change-making and the awkward edges.
 * Keep working from TESTLIST.md in {@link VendingMachineTest}; this class only
 * confirms you have arrived.
 *
 * <p>House rule for exact change (also in TESTLIST.md): the machine can make
 * change when its float can break a quarter — pay out 25¢ using nickels and
 * dimes only. If anything is stocked and the float can't do that, the idle
 * display must read EXACT CHANGE ONLY.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2ChangeAndEdgeCasesTest {

    @Test
    void overpaymentComesBackAsChange() {
        var machine = serviceableMachine();
        machine.insert(QUARTER);
        machine.insert(QUARTER);
        machine.insert(QUARTER); // $0.75 in, candy is $0.50

        machine.selectProduct(CANDY);

        assertThat(machine.takeDispenseBin()).containsExactly(CANDY);
        assertThat(totalCents(machine.takeCoinReturn())).isEqualTo(25);
    }

    @Test
    void coinReturnButtonRefundsTheBalance() {
        var machine = serviceableMachine();
        machine.insert(DIME);
        machine.insert(NICKEL);

        machine.pressCoinReturn();

        assertThat(totalCents(machine.takeCoinReturn())).isEqualTo(15);
        assertThat(machine.display()).isEqualTo("INSERT COIN");
    }

    @Test
    void unstockedSelectionReadsSoldOut() {
        var machine = new VendingMachine();
        machine.stock(CANDY, 1);          // chips were never loaded
        loadFullFloat(machine);

        machine.selectProduct(CHIPS);

        assertThat(machine.takeDispenseBin()).isEmpty();
        assertThat(machine.display()).isEqualTo("SOLD OUT");
        assertThat(machine.display()).isEqualTo("INSERT COIN");
    }

    @Test
    void buyingTheLastUnitEmptiesTheSlot() {
        var machine = new VendingMachine();
        machine.stock(CANDY, 1);
        loadFullFloat(machine);

        machine.insert(QUARTER);
        machine.insert(QUARTER);
        machine.selectProduct(CANDY);
        assertThat(machine.takeDispenseBin()).containsExactly(CANDY);

        machine.insert(QUARTER);
        machine.insert(QUARTER);
        machine.selectProduct(CANDY);

        assertThat(machine.takeDispenseBin()).isEmpty();
        assertThat(machine.display()).isEqualTo("SOLD OUT");
        assertThat(machine.display()).isEqualTo("$0.50");
    }

    @Test
    void aMachineThatCannotBreakAQuarterDemandsExactChange() {
        var machine = new VendingMachine();
        machine.stock(CANDY, 1);          // stocked, but the float is empty
        assertThat(machine.display()).isEqualTo("EXACT CHANGE ONLY");

        machine.loadChange(DIME, 2);
        machine.loadChange(NICKEL, 1);    // 25¢ in small coins — a quarter can be broken
        assertThat(machine.display()).isEqualTo("INSERT COIN");
    }

    private static VendingMachine serviceableMachine() {
        var machine = new VendingMachine();
        for (Product product : Product.values()) {
            machine.stock(product, 5);
        }
        loadFullFloat(machine);
        return machine;
    }

    private static void loadFullFloat(VendingMachine machine) {
        machine.loadChange(NICKEL, 10);
        machine.loadChange(DIME, 10);
        machine.loadChange(QUARTER, 10);
    }

    private static int totalCents(List<Coin> coins) {
        return coins.stream().mapToInt(Coin::valueInCents).sum();
    }
}
