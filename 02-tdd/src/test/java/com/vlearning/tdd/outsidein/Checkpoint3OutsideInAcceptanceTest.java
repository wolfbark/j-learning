package com.vlearning.tdd.outsidein;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance checkpoint for step 3 — the same features as checkpoints 1 and 2,
 * exercised through the driving port. Note there is no Mockito in this class:
 * an acceptance test cares about outcomes, so the collaborators are hand-rolled
 * fakes that behave, not mocks that verify.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3OutsideInAcceptanceTest {

    static final Product COLA  = new Product("A1", "Cola", 100);
    static final Product CHIPS = new Product("B1", "Chips", 65);
    static final Product CANDY = new Product("C1", "Candy", 50);

    final ReferenceCoinValidator validator = new ReferenceCoinValidator();
    final InMemoryCatalog catalog = new InMemoryCatalog(COLA, CHIPS, CANDY);
    final FakeDispenser dispenser = new FakeDispenser(Map.of(COLA, 5, CHIPS, 5, CANDY, 5));
    final RecordingCoinReturn coinReturn = new RecordingCoinReturn();
    final RecordingDisplay display = new RecordingDisplay();

    final VendingUseCase machine =
            new VendingController(validator, catalog, dispenser, coinReturn, display);

    @Test
    void aFullPricePurchaseEndToEnd() {
        insertQuarters(4);

        machine.selectProduct("A1");

        assertThat(dispenser.dispensed).containsExactly(COLA);
        assertThat(display.messages).contains("$0.25", "$0.50", "$0.75", "$1.00");
        assertThat(display.messages).containsSubsequence("$1.00", "THANK YOU");
        assertThat(coinReturn.releasedCents()).isZero();
    }

    @Test
    void underpaymentShowsThePriceAndKeepsTheEscrow() {
        insertQuarters(1);

        machine.selectProduct("A1");

        assertThat(dispenser.dispensed).isEmpty();
        assertThat(display.messages).containsSubsequence("$0.25", "PRICE $1.00");
        assertThat(coinReturn.releasedCents()).isZero();
    }

    @Test
    void overpaymentComesBackAsChange() {
        insertQuarters(3);

        machine.selectProduct("C1"); // candy, $0.50

        assertThat(dispenser.dispensed).containsExactly(CANDY);
        assertThat(coinReturn.releasedCents()).isEqualTo(25);
    }

    @Test
    void soldOutIsAnnouncedAndTheEscrowKept() {
        dispenser.stock.put(CHIPS, 0);
        insertQuarters(3);

        machine.selectProduct("B1");

        assertThat(dispenser.dispensed).isEmpty();
        assertThat(display.messages).contains("SOLD OUT");
        assertThat(coinReturn.releasedCents()).isZero();
    }

    @Test
    void theReturnButtonRefundsTheEscrow() {
        machine.insertCoin(PhysicalCoin.DIME_SIZED);
        machine.insertCoin(PhysicalCoin.NICKEL_SIZED);

        machine.returnCoins();

        assertThat(coinReturn.releasedCents()).isEqualTo(15);
    }

    @Test
    void rejectedObjectsNeverEnterTheEscrow() {
        machine.insertCoin(new PhysicalCoin(7.50, 30.00)); // a bottle cap
        machine.insertCoin(PhysicalCoin.QUARTER_SIZED);

        machine.returnCoins();

        assertThat(coinReturn.rejected).hasSize(1);
        assertThat(coinReturn.releasedCents()).isEqualTo(25);
    }

    private void insertQuarters(int count) {
        for (int i = 0; i < count; i++) {
            machine.insertCoin(PhysicalCoin.QUARTER_SIZED);
        }
    }

    // ---- hand-rolled fakes ----------------------------------------------

    static final class ReferenceCoinValidator implements CoinValidator {
        @Override
        public Optional<Coin> classify(PhysicalCoin object) {
            if (closeTo(object, PhysicalCoin.NICKEL_SIZED))  return Optional.of(Coin.NICKEL);
            if (closeTo(object, PhysicalCoin.DIME_SIZED))    return Optional.of(Coin.DIME);
            if (closeTo(object, PhysicalCoin.QUARTER_SIZED)) return Optional.of(Coin.QUARTER);
            return Optional.empty();
        }

        private static boolean closeTo(PhysicalCoin a, PhysicalCoin b) {
            return Math.abs(a.weightGrams() - b.weightGrams()) < 0.1
                    && Math.abs(a.diameterMillimetres() - b.diameterMillimetres()) < 0.3;
        }
    }

    static final class InMemoryCatalog implements ProductCatalog {
        private final Map<String, Product> products = new HashMap<>();

        InMemoryCatalog(Product... entries) {
            for (Product product : entries) {
                products.put(product.code(), product);
            }
        }

        @Override
        public Optional<Product> byCode(String code) {
            return Optional.ofNullable(products.get(code));
        }
    }

    static final class FakeDispenser implements Dispenser {
        final Map<Product, Integer> stock;
        final List<Product> dispensed = new ArrayList<>();

        FakeDispenser(Map<Product, Integer> initialStock) {
            this.stock = new HashMap<>(initialStock);
        }

        @Override
        public boolean canDispense(Product product) {
            return stock.getOrDefault(product, 0) > 0;
        }

        @Override
        public void dispense(Product product) {
            stock.merge(product, -1, Integer::sum);
            dispensed.add(product);
        }
    }

    static final class RecordingCoinReturn implements CoinReturn {
        final List<PhysicalCoin> rejected = new ArrayList<>();
        final List<Coin> released = new ArrayList<>();

        @Override
        public void reject(PhysicalCoin object) {
            rejected.add(object);
        }

        @Override
        public void release(List<Coin> coins) {
            released.addAll(coins);
        }

        int releasedCents() {
            return released.stream().mapToInt(Coin::valueInCents).sum();
        }
    }

    static final class RecordingDisplay implements Display {
        final List<String> messages = new ArrayList<>();

        @Override
        public void show(String message) {
            messages.add(message);
        }
    }
}
