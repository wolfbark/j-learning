package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6 — Data-oriented programming wrap-up.
 *
 * Anonymous classes compile to nested class files (PaymentProcessor$1, ...);
 * lambdas and method references do not. The class-file scan below fails while
 * the anonymous Comparator and ResultCallback from 2014 are still around.
 * (Synthetic $SwitchMap holders that javac generates for enum switches are
 * tolerated — only Comparator/ResultCallback implementations count.)
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6DataOrientedTest {

    private final PaymentProcessor processor = new PaymentProcessor(LocalDate.of(2026, 8, 25));

    @Test
    void noAnonymousClassesRemainInTheProcessor() {
        for (int i = 1; i <= 5; i++) {
            Class<?> nested;
            try {
                nested = Class.forName("vlearning.payments.PaymentProcessor$" + i);
            } catch (ClassNotFoundException gone) {
                continue;
            }
            assertThat(Comparator.class.isAssignableFrom(nested))
                    .as("%s is an anonymous Comparator — use Comparator.comparing", nested.getName())
                    .isFalse();
            assertThat(ResultCallback.class.isAssignableFrom(nested))
                    .as("%s is an anonymous ResultCallback — use a lambda", nested.getName())
                    .isFalse();
        }
    }

    @Test
    void resultCallbackIsAFunctionalInterface() {
        assertThat(ResultCallback.class.isAnnotationPresent(FunctionalInterface.class))
                .as("annotate ResultCallback with @FunctionalInterface")
                .isTrue();
    }

    @Test
    void batchOrderingStillHolds() {
        CardDetails visa = new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
        CardDetails amex = new CardDetails("378282246310005555", CardBrand.AMEX, 12, 2030);
        List<PaymentRequest> batch = List.of(
                new PaymentRequest("pay-a", "cust-7", new BigDecimal("50.00"), "EUR", visa),
                new PaymentRequest("pay-c", "cust-7", new BigDecimal("200.00"), "EUR", amex),
                new PaymentRequest("pay-b", "cust-7", new BigDecimal("200.00"), "EUR", visa));

        assertThat(processor.summarizeAll(batch)).containsExactly(
                "APPROVED pay-b auth=AUTH-pay-b fee=3.00",
                "APPROVED pay-c auth=AUTH-pay-c fee=5.00",
                "APPROVED pay-a auth=AUTH-pay-a fee=0.75");
    }

    @Test
    void deconstructionKeepsBehaviorIdentical() {
        CardDetails visa = new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
        PaymentResult result = processor.process(
                new PaymentRequest("pay-1", "cust-7", new BigDecimal("100.00"), "EUR", visa));

        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-1 auth=AUTH-pay-1 fee=1.50");
    }
}
