package com.vlearning.tdd.outsidein;

/**
 * The driving port: everything the front panel hardware (or a test) can do
 * to the machine. The London-school round starts here and works inward.
 */
public interface VendingUseCase {

    void insertCoin(PhysicalCoin object);

    void selectProduct(String productCode);

    void returnCoins();
}
