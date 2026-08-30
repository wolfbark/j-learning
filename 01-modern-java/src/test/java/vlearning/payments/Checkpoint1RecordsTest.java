package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1 — Replace the mutable DTOs with records.
 *
 * Uses reflection for everything the pristine code does not have yet, so this
 * file compiles before AND after your refactoring.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1RecordsTest {

    @Test
    void paymentRequestIsARecord() {
        assertThat(PaymentRequest.class.isRecord()).isTrue();
        assertThat(Arrays.stream(PaymentRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("id", "customerId", "amount", "currency", "card");
    }

    @Test
    void cardDetailsIsARecord() {
        assertThat(CardDetails.class.isRecord()).isTrue();
        assertThat(Arrays.stream(CardDetails.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("number", "brand", "expiryMonth", "expiryYear");
    }

    @Test
    void beanBoilerplateIsGone() {
        for (Class<?> dto : new Class<?>[] {PaymentRequest.class, CardDetails.class}) {
            assertThat(Arrays.stream(dto.getDeclaredMethods()).map(Method::getName))
                    .as("%s should expose record accessors, not getters/setters", dto.getSimpleName())
                    .noneMatch(name -> name.startsWith("get") || name.startsWith("set"));
        }
    }

    @Test
    void valueSemanticsNowComeForFree() {
        CardDetails card = new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
        PaymentRequest one = new PaymentRequest("pay-1", "cust-7", new BigDecimal("100.00"), "EUR", card);
        PaymentRequest two = new PaymentRequest("pay-1", "cust-7", new BigDecimal("100.00"), "EUR",
                new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030));

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
    }
}
