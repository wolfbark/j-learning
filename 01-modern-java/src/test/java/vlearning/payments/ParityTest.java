package vlearning.payments;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the observable behavior of the given (Java-8-style) module.
 *
 * This suite stays ENABLED for the entire lesson and must be green after every
 * refactoring step. It deliberately never calls a getter or setter on any class
 * you will refactor — only constructors, processor methods, and equals/hashCode —
 * so it keeps compiling as the shapes of those classes change.
 */
class ParityTest {

    private final PaymentProcessor processor = new PaymentProcessor(LocalDate.of(2026, 8, 25));

    private static CardDetails visa() {
        return new CardDetails("4242424242424242", CardBrand.VISA, 12, 2030);
    }

    private static PaymentRequest request(String id, String amount, CardDetails card) {
        return new PaymentRequest(id, "cust-7", new BigDecimal(amount), "EUR", card);
    }

    @Test
    void approvesValidVisaPaymentWithFee() {
        PaymentResult result = processor.process(request("pay-1", "100.00", visa()));

        assertThat(result).isInstanceOf(Approved.class);
        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-1 auth=AUTH-pay-1 fee=1.50");
    }

    @Test
    void mastercardSharesTheVisaFeeRate() {
        CardDetails mastercard = new CardDetails("5555555555554444", CardBrand.MASTERCARD, 12, 2030);
        PaymentResult result = processor.process(request("pay-2", "100.00", mastercard));

        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-2 auth=AUTH-pay-2 fee=1.50");
    }

    @Test
    void amexPaysThePremiumRate() {
        CardDetails amex = new CardDetails("378282246310005555", CardBrand.AMEX, 12, 2030);
        PaymentResult result = processor.process(request("pay-3", "100.00", amex));

        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-3 auth=AUTH-pay-3 fee=2.50");
    }

    @Test
    void discoverFallsBackToTheDefaultRate() {
        CardDetails discover = new CardDetails("6011111111111117", CardBrand.DISCOVER, 12, 2030);
        PaymentResult result = processor.process(request("pay-4", "100.00", discover));

        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-4 auth=AUTH-pay-4 fee=2.00");
    }

    @Test
    void waivesTheFeeOnMicroPayments() {
        PaymentResult result = processor.process(request("pay-5", "0.10", visa()));

        assertThat(processor.summarize(result))
                .isEqualTo("APPROVED pay-5 auth=AUTH-pay-5 (fee waived)");
    }

    @Test
    void declinesAnExpiredCard() {
        CardDetails expired = new CardDetails("4242424242424242", CardBrand.VISA, 7, 2026);
        PaymentResult result = processor.process(request("pay-6", "100.00", expired));

        assertThat(result).isInstanceOf(Declined.class);
        assertThat(processor.summarize(result))
                .isEqualTo("DECLINED pay-6 reason=card expired");
    }

    @Test
    void flagsAmountsOverTenThousandAsFraud() {
        PaymentResult result = processor.process(request("pay-7", "10000.01", visa()));

        assertThat(result).isInstanceOf(Fraud.class);
        assertThat(processor.summarize(result))
                .isEqualTo("FRAUD pay-7 risk=0.95 rule=AMOUNT_THRESHOLD");
    }

    @Test
    void flagsIssuerBlacklistedTestCardAsFraud() {
        CardDetails flagged = new CardDetails("4000000000000019", CardBrand.VISA, 12, 2030);
        PaymentResult result = processor.process(request("pay-8", "100.00", flagged));

        assertThat(processor.summarize(result))
                .isEqualTo("FRAUD pay-8 risk=0.92 rule=ISSUER_FLAGGED");
    }

    @Test
    void reportsGatewayProcessingErrorsAsRetryable() {
        CardDetails glitchy = new CardDetails("4000000000000119", CardBrand.VISA, 12, 2030);
        PaymentResult result = processor.process(request("pay-9", "100.00", glitchy));

        assertThat(result).isInstanceOf(Retryable.class);
        assertThat(processor.summarize(result))
                .isEqualTo("RETRY pay-9 in 30s: gateway processing error");
    }

    @Test
    void declinesInsufficientFunds() {
        CardDetails broke = new CardDetails("4000000000009995", CardBrand.VISA, 12, 2030);
        PaymentResult result = processor.process(request("pay-10", "100.00", broke));

        assertThat(processor.summarize(result))
                .isEqualTo("DECLINED pay-10 reason=insufficient funds");
    }

    @Test
    void enforcesTheRegionalLimitFromTheRequestContext() {
        PaymentRequest sixThousand = request("pay-11", "6000.00", visa());

        PaymentResult inEu = RequestContext.callWith(
                new RequestContext("req-1", "EU"), () -> processor.process(sixThousand));
        assertThat(processor.summarize(inEu))
                .isEqualTo("DECLINED pay-11 reason=over regional limit for EU");

        PaymentResult inUs = RequestContext.callWith(
                new RequestContext("req-2", "US"), () -> processor.process(sixThousand));
        assertThat(inUs).isInstanceOf(Approved.class);
    }

    @Test
    void appliesNoRegionalLimitWithoutAContext() {
        PaymentResult result = processor.process(request("pay-12", "6000.00", visa()));

        assertThat(result).isInstanceOf(Approved.class);
    }

    @Test
    void rejectsInvalidRequestsWithAValidationError() {
        assertThatThrownBy(() -> processor.process(null))
                .isInstanceOf(PaymentException.class)
                .hasMessage("[VALIDATION] request must not be null");

        assertThatThrownBy(() -> processor.process(request(" ", "100.00", visa())))
                .isInstanceOf(PaymentException.class)
                .hasMessage("[VALIDATION] payment id is required");

        assertThatThrownBy(() -> processor.process(request("pay-13", "0.00", visa())))
                .isInstanceOf(PaymentException.class)
                .hasMessage("[VALIDATION] amount must be positive");

        PaymentRequest badCurrency = new PaymentRequest("pay-14", "cust-7",
                new BigDecimal("100.00"), "EURO", visa());
        assertThatThrownBy(() -> processor.process(badCurrency))
                .isInstanceOf(PaymentException.class)
                .hasMessage("[VALIDATION] currency must be a 3-letter code");

        PaymentRequest noCard = new PaymentRequest("pay-15", "cust-7",
                new BigDecimal("100.00"), "EUR", null);
        assertThatThrownBy(() -> processor.process(noCard))
                .isInstanceOf(PaymentException.class)
                .hasMessage("[VALIDATION] card details are required");
    }

    @Test
    void paymentExceptionCarriesItsErrorCode() {
        try {
            processor.process(null);
        } catch (PaymentException e) {
            assertThat(e.getCode()).isEqualTo("VALIDATION");
            return;
        }
        throw new AssertionError("expected a PaymentException");
    }

    @Test
    void dtosHaveValueSemantics() {
        assertThat(request("pay-16", "100.00", visa()))
                .isEqualTo(request("pay-16", "100.00", visa()))
                .hasSameHashCodeAs(request("pay-16", "100.00", visa()));

        assertThat(visa()).isEqualTo(visa()).hasSameHashCodeAs(visa());
    }

    @Test
    void summarizeAllSortsByAmountDescendingThenById() {
        List<PaymentRequest> batch = List.of(
                request("pay-a", "50.00", visa()),
                request("pay-c", "200.00",
                        new CardDetails("378282246310005555", CardBrand.AMEX, 12, 2030)),
                request("pay-b", "200.00", visa()));

        assertThat(processor.summarizeAll(batch)).containsExactly(
                "APPROVED pay-b auth=AUTH-pay-b fee=3.00",
                "APPROVED pay-c auth=AUTH-pay-c fee=5.00",
                "APPROVED pay-a auth=AUTH-pay-a fee=0.75");
    }

    @Test
    void processAllInvokesTheCallbackForEveryRequest() {
        List<String> seen = new ArrayList<>();
        List<PaymentRequest> batch = List.of(
                request("pay-x", "100.00", visa()),
                request("pay-y", "10000.01", visa()));

        processor.processAll(batch, (request, result) ->
                seen.add(result.getClass().getSimpleName()));

        assertThat(seen).containsExactly("Approved", "Fraud");
    }
}
