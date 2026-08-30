package dev.vlearning.payments;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.vlearning.payments.domain.IdempotencyConflictException;
import dev.vlearning.payments.domain.PaymentService;
import dev.vlearning.payments.domain.PaymentStatus;
import dev.vlearning.payments.domain.PaymentValidationException;
import dev.vlearning.payments.support.InMemoryPaymentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIVEN unit tests: the authorization decision and validation, in memory, in
 * milliseconds. Part of step 1's audit is deciding what these tests do
 * <em>not</em> tell you.
 */
class PaymentDecisionTest {

    private final InMemoryPaymentRepository repository = new InMemoryPaymentRepository();
    private final PaymentService service = new PaymentService(repository,
            Clock.fixed(Instant.parse("2026-08-25T10:15:30Z"), ZoneOffset.UTC));

    @Test
    void authorizesAnAmountUnderTheLimit() {
        var result = service.authorize("key-1", "order-1", new BigDecimal("42.50"), "USD", "tok_visa_ok");

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.payment().declineReason()).isNull();
        assertThat(result.payment().id()).startsWith("pay_");
    }

    @Test
    void declinesAboveTheAcquirerLimit() {
        var result = service.authorize("key-2", "order-2", new BigDecimal("1000.01"), "USD", "tok_visa_ok");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(result.payment().declineReason()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void declinesAMagicDeclineToken() {
        var result = service.authorize("key-3", "order-3", new BigDecimal("10.00"), "EUR", "tok_decline_expired");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(result.payment().declineReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void rejectsAnUnsupportedCurrency() {
        assertThatThrownBy(() -> service.authorize("key-4", "order-4", new BigDecimal("10.00"), "XYZ", "tok_visa_ok"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyHas() {
        assertThatThrownBy(() -> service.authorize("key-5", "order-5", new BigDecimal("500.5"), "JPY", "tok_visa_ok"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("JPY");
    }

    @Test
    void replayingTheSameRequestReturnsTheSamePaymentWithoutCreatingIt() {
        var first = service.authorize("key-6", "order-6", new BigDecimal("20.00"), "USD", "tok_visa_ok");
        var replay = service.authorize("key-6", "order-6", new BigDecimal("20.00"), "USD", "tok_visa_ok");

        assertThat(replay.created()).isFalse();
        assertThat(replay.payment().id()).isEqualTo(first.payment().id());
    }

    @Test
    void reusingAKeyForADifferentAmountIsAConflict() {
        service.authorize("key-7", "order-7", new BigDecimal("20.00"), "USD", "tok_visa_ok");

        assertThatThrownBy(() -> service.authorize("key-7", "order-7", new BigDecimal("21.00"), "USD", "tok_visa_ok"))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
