package com.vlearning.bdd.pricing;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The campaign codes marketing is currently running. Given code -- no need to change it.
 */
@Component
public class PromoCatalog {

    private static final Map<String, Integer> PERCENTAGE_BY_CODE = Map.of(
            "WELCOME15", 15,
            "SPRING5", 5);

    /** @return the percentage off for {@code code}, or 0 when no code was supplied. */
    public int percentageFor(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        Integer percentage = PERCENTAGE_BY_CODE.get(code.toUpperCase(Locale.ROOT));
        if (percentage == null) {
            throw new IllegalArgumentException("unknown promo code: " + code);
        }
        return percentage;
    }
}
