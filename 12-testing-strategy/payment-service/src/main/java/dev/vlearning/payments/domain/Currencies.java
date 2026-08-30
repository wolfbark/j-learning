package dev.vlearning.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Minor-unit table. Money is not always two decimal places, and a payment API
 * that assumes it is will happily authorize 100x the intended amount in JPY.
 */
public final class Currencies {

    private static final Map<String, Integer> MINOR_UNITS = Map.of(
            "USD", 2,
            "EUR", 2,
            "JPY", 0);

    private Currencies() {
    }

    public static boolean isSupported(String currency) {
        return currency != null && MINOR_UNITS.containsKey(currency);
    }

    public static int minorUnits(String currency) {
        Integer units = MINOR_UNITS.get(currency);
        if (units == null) {
            throw new IllegalArgumentException("unsupported currency: " + currency);
        }
        return units;
    }

    /** Amounts are always exposed at the currency's natural scale, in and out of the database. */
    public static BigDecimal normalize(BigDecimal amount, String currency) {
        return amount.setScale(minorUnits(currency), RoundingMode.UNNECESSARY);
    }
}
