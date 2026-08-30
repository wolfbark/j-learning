package dev.vlearning.quotes.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A framework-free value object — the exemplar for everything in this package.
 * Note what is NOT here: no annotations, no getters/setters, no Spring, no JPA.
 * Amounts are normalized to two decimals (HALF_UP), so equals() behaves:
 * Money.euros("90") equals Money.euros("90.00").
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money euros(String amount) {
        return new Money(new BigDecimal(amount), "EUR");
    }

    public Money times(BigDecimal multiplier) {
        return new Money(amount.multiply(multiplier), currency);
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add " + other.currency + " to " + currency);
        }
        return new Money(amount.add(other.amount), currency);
    }
}
