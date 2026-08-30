package dev.vlearning.orders.pricing;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 7 — kill the mutants.
 *
 * <p>Run {@code mvn -Pmutation verify} first and read
 * {@code target/pit-reports/index.html}. Every surviving mutant is a change
 * someone could make to {@link OrderPricer} that your suite would wave through.
 *
 * <p>Two are done for you, as a demonstration of the shape: a boundary test pins
 * a {@code >=} so that PIT's "change conditional boundary" mutant dies. The
 * remaining survivors are yours. The report is the to-do list; the threshold
 * (75%) is the gate.
 *
 * <p>Do not chase 100%. Read each survivor and decide whether it represents a
 * behaviour anyone should rely on — an equivalent mutant that no reasonable test
 * can kill is a fact about the code, not a hole in the suite.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7PricingMutationTest {

    private final OrderPricer pricer = new OrderPricer();

    private static List<OrderLine> line(int quantity, String unitPrice) {
        return List.of(new OrderLine("SKU-1", quantity, new BigDecimal(unitPrice)));
    }

    @Test
    void exactlyOneHundredIsAlreadyTheFirstTier() {
        // >= 100.00, not > 100.00: this kills the boundary mutant on the first tier.
        var quote = pricer.quote(line(1, "100.00"), "USD", null, false);

        assertThat(quote.appliedRule()).isEqualTo("TIER_5");
        assertThat(quote.discount()).isEqualByComparingTo("5.00");
    }

    @Test
    void oneCentBelowTheFirstTierGetsNothing() {
        var quote = pricer.quote(line(1, "99.99"), "USD", null, false);

        assertThat(quote.appliedRule()).isEqualTo("NO_DISCOUNT");
        assertThat(quote.discount()).isEqualByComparingTo("0.00");
    }

    // TODO the second and third tier boundaries (500.00 and 1000.00), from both sides.

    // TODO the loyalty bonus: it adds 2 percentage points...

    // TODO ...and it is capped at 15% — an order in the top tier plus loyalty must
    //      still be 15%, not 17%.

    // TODO the welcome coupon applies below the first tier...

    // TODO ...and is silently dropped when a real tier discount exists. Is that the
    //      behaviour you want? Write the test that documents your answer.

    // TODO the free-shipping threshold is compared against the *discounted* subtotal,
    //      and the comparison is >=. Two tests.

    // TODO rounding: find a subtotal and percentage whose discount lands on a half
    //      cent, and pin HALF_UP. (Hint: 5% of a subtotal ending in .10)

    // TODO the rejected inputs: zero quantity, negative quantity, negative unit
    //      price, unsupported currency.
}
