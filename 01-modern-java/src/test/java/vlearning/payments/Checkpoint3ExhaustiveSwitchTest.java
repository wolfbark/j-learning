package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 3 — Replace the instanceof-and-cast chain with an exhaustive pattern switch.
 *
 * The discriminating assertion is the null case: the old chain fell through all
 * instanceof checks and threw IllegalStateException; a pattern switch without a
 * `case null` throws NullPointerException on a null selector. If that test goes
 * green, you really are running a switch.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3ExhaustiveSwitchTest {

    private final PaymentProcessor processor = new PaymentProcessor(LocalDate.of(2026, 8, 25));

    @Test
    void nullSelectorNowThrowsNullPointerException() {
        assertThatThrownBy(() -> processor.summarize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void guardedPatternKeepsTheFeeWaiverBehavior() {
        CardDetails visa = new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
        PaymentResult micro = processor.process(
                new PaymentRequest("pay-1", "cust-7", new BigDecimal("0.10"), "EUR", visa));

        assertThat(processor.summarize(micro))
                .isEqualTo("APPROVED pay-1 auth=AUTH-pay-1 (fee waived)");
    }

    @Test
    void allFourOutcomeFormatsAreUnchanged() {
        CardDetails visa = new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
        CardDetails glitchy = new CardDetails("4000000000000119", CardBrand.VISA, 12, 2030);
        CardDetails broke = new CardDetails("4000000000009995", CardBrand.VISA, 12, 2030);

        assertThat(processor.summarize(processor.process(
                new PaymentRequest("pay-1", "cust-7", new BigDecimal("100.00"), "EUR", visa))))
                .isEqualTo("APPROVED pay-1 auth=AUTH-pay-1 fee=1.50");
        assertThat(processor.summarize(processor.process(
                new PaymentRequest("pay-2", "cust-7", new BigDecimal("10000.01"), "EUR", visa))))
                .isEqualTo("FRAUD pay-2 risk=0.95 rule=AMOUNT_THRESHOLD");
        assertThat(processor.summarize(processor.process(
                new PaymentRequest("pay-3", "cust-7", new BigDecimal("100.00"), "EUR", glitchy))))
                .isEqualTo("RETRY pay-3 in 30s: gateway processing error");
        assertThat(processor.summarize(processor.process(
                new PaymentRequest("pay-4", "cust-7", new BigDecimal("100.00"), "EUR", broke))))
                .isEqualTo("DECLINED pay-4 reason=insufficient funds");
    }
}
