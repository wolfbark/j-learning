package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Step 2 — Convert the result hierarchy to a sealed interface of records.
 *
 * The compiler now knows the complete set of payment outcomes — that knowledge
 * is what makes step 3's exhaustive switch possible.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2SealedResultsTest {

    @Test
    void paymentResultIsASealedInterface() {
        assertThat(PaymentResult.class.isInterface())
                .as("PaymentResult should be an interface (records cannot extend a class)")
                .isTrue();
        assertThat(PaymentResult.class.isSealed()).isTrue();
    }

    @Test
    void exactlyFourOutcomesArePermitted() {
        assertThat(Arrays.stream(PaymentResult.class.getPermittedSubclasses())
                .map(Class::getSimpleName))
                .containsExactlyInAnyOrder("Approved", "Declined", "Fraud", "Retryable");
    }

    @Test
    void everyOutcomeIsARecord() {
        for (Class<?> outcome : PaymentResult.class.getPermittedSubclasses()) {
            assertThat(outcome.isRecord())
                    .as("%s should be a record", outcome.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    void theInterfaceExposesThePaymentId() {
        // Declare `String paymentId();` on the interface — every record satisfies
        // it automatically through its `paymentId` component accessor.
        assertThatCode(() -> PaymentResult.class.getMethod("paymentId"))
                .doesNotThrowAnyException();
    }

    @Test
    void outcomesGainValueSemantics() {
        Approved one = new Approved("pay-1", "AUTH-pay-1", new BigDecimal("1.50"));
        Approved two = new Approved("pay-1", "AUTH-pay-1", new BigDecimal("1.50"));

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
    }
}
