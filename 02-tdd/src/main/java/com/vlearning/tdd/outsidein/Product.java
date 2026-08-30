package com.vlearning.tdd.outsidein;

/** A catalog entry: keypad code, human name, price in cents. */
public record Product(String code, String name, int priceInCents) {
}
