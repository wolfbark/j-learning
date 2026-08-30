package com.vlearning.tdd.outsidein;

import java.util.Optional;

/** Maps keypad codes to products. */
public interface ProductCatalog {

    Optional<Product> byCode(String code);
}
