package dev.vlearning.quotes.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The domain's own quote — compare it to QuoteEntity, which serves the database.
 * (Purists would inject a clock and an id generator for determinism; see the
 * stretch goals in the README.)
 */
public record Quote(UUID id, String productCode, RiskProfile riskProfile, Money monthlyPremium, Instant createdAt) {

    public static Quote create(String productCode, RiskProfile riskProfile, Money monthlyPremium) {
        return new Quote(UUID.randomUUID(), productCode, riskProfile, monthlyPremium, Instant.now());
    }
}
