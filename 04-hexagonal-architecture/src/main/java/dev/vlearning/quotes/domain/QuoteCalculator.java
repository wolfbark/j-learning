package dev.vlearning.quotes.domain;

/**
 * Pure domain service: prices a quote from a product base rate and a risk profile.
 * No Spring, no JPA, no HTTP — that is the whole point. The base rate arrives as a
 * parameter precisely so this class never has to know where rates come from.
 */
public class QuoteCalculator {

    /**
     * The pricing rules are currently buried in QuoteService.createQuote (Round 1).
     * QuoteCalculatorTest spells out the expected numbers.
     */
    public Money monthlyPremium(Money baseRate, RiskProfile riskProfile) {
        // TODO Step 2: extract the pricing rules from QuoteService into this method.
        throw new UnsupportedOperationException("Checkpoint 2: extract the pricing rules from QuoteService");
    }
}
