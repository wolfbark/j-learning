package com.vlearning.tdd.outsidein;

/**
 * The use-case implementation you grow outside-in (README, step 3).
 *
 * <p>One collaboration is already in place — the one the example interaction
 * test in {@code VendingControllerTest} pinned down. Everything else is yours
 * to drive from the boundary, mock-first.
 */
public final class VendingController implements VendingUseCase {

    private final CoinValidator coinValidator;
    private final ProductCatalog catalog;
    private final Dispenser dispenser;
    private final CoinReturn coinReturn;
    private final Display display;

    public VendingController(CoinValidator coinValidator,
                             ProductCatalog catalog,
                             Dispenser dispenser,
                             CoinReturn coinReturn,
                             Display display) {
        this.coinValidator = coinValidator;
        this.catalog = catalog;
        this.dispenser = dispenser;
        this.coinReturn = coinReturn;
        this.display = display;
    }

    @Override
    public void insertCoin(PhysicalCoin object) {
        var coin = coinValidator.classify(object);
        if (coin.isEmpty()) {
            coinReturn.reject(object);
            return;
        }
        throw new UnsupportedOperationException("Drive me with a failing interaction test first");
    }

    @Override
    public void selectProduct(String productCode) {
        throw new UnsupportedOperationException("Drive me with a failing interaction test first");
    }

    @Override
    public void returnCoins() {
        throw new UnsupportedOperationException("Drive me with a failing interaction test first");
    }
}
