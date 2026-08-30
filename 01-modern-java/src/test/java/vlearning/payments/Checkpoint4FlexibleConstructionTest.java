package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 4 — Flexible constructor bodies (JEP 513).
 *
 * The given PaymentException performs no argument validation at all, because on
 * old Java validating BEFORE super(...) required contortions (a static helper
 * wrapped around the super argument). Add the validation as plain statements in
 * the constructor prologue, before super(...) runs.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4FlexibleConstructionTest {

    @Test
    void rejectsANullCode() {
        assertThatThrownBy(() -> new PaymentException(null, "boom"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankCode() {
        assertThatThrownBy(() -> new PaymentException("  ", "boom"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsALowercaseCode() {
        assertThatThrownBy(() -> new PaymentException("validation", "boom"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullMessage() {
        assertThatThrownBy(() -> new PaymentException("LIMIT", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validArgumentsStillProduceTheSameMessageFormat() {
        PaymentException e = new PaymentException("LIMIT", "over the limit");

        assertThat(e.getMessage()).isEqualTo("[LIMIT] over the limit");
        assertThat(e.getCode()).isEqualTo("LIMIT");
    }
}
