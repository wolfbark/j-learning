package com.vlearning.bdd.pricing;

import org.springframework.stereotype.Service;

/**
 * Checkout pricing: tier discounts, promo codes and loyalty points.
 *
 * <p>Do not implement this from {@code docs/the-ticket.md}. The ticket is ambiguous on at
 * least eight points, and guessing at any of them produces code that is confidently wrong.
 * Run the example-mapping session first (step 2), write the agreed examples as Gherkin
 * (step 3), and only then come back here.
 *
 * <p>The signature is given so your step definitions compile; the body is yours.
 */
@Service
public class PricingService {

    private final PromoCatalog promoCatalog;

    public PricingService(PromoCatalog promoCatalog) {
        this.promoCatalog = promoCatalog;
    }

    /**
     * Prices one order for one customer.
     *
     * @throws IllegalArgumentException when the requested point redemption is not allowed
     *                                  (the session decided which cases those are)
     */
    public PricingResult price(Order order, Member member) {
        throw new UnsupportedOperationException(
                "Step 5: implement this from the examples you agreed, one scenario at a time");
    }
}
