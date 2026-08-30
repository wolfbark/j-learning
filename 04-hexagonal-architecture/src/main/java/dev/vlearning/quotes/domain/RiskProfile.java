package dev.vlearning.quotes.domain;

import java.util.Set;

/**
 * Who we are insuring: age plus declared risk factors.
 */
public record RiskProfile(int age, Set<RiskFactor> riskFactors) {

    public RiskProfile {
        // TODO Step 2: applicants younger than 18 cannot be quoted —
        //  reject them here with an IllegalArgumentException.
        riskFactors = Set.copyOf(riskFactors);
    }

    public boolean has(RiskFactor factor) {
        return riskFactors.contains(factor);
    }
}
