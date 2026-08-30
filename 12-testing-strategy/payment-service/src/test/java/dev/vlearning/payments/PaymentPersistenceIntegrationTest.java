package dev.vlearning.payments;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIVEN integration test (enabled): one round trip through the real HTTP stack,
 * the real serializer, and a real Postgres. It is the only given test that could
 * have caught a wrong column type or a missing schema file.
 */
class PaymentPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void authorizingAPaymentStoresItAndReturnsIt() {
        var response = authorize("it-key-1", "order-42", "19.99", "USD", "tok_visa_ok");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody())
                .contains("\"status\":\"AUTHORIZED\"")
                .contains("\"currency\":\"USD\"")
                .contains("\"amount\":19.99");
        assertThat(countPayments()).isEqualTo(1);
    }

    @Test
    void aStoredPaymentCanBeReadBackById() {
        var created = authorize("it-key-2", "order-43", "5.00", "EUR", "tok_visa_ok");
        String paymentId = extractPaymentId(created.getBody());

        var fetched = getPayment(paymentId);

        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(fetched.getBody()).contains(paymentId).contains("\"amount\":5.00");
    }

    @Test
    void jpyIsStoredWithoutDecimals() {
        var response = authorize("it-key-3", "order-44", "5000", "JPY", "tok_visa_ok");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"amount\":5000");
        assertThat(storedAmount(extractPaymentId(response.getBody())))
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    static String extractPaymentId(String body) {
        int start = body.indexOf("\"paymentId\":\"") + 13;
        return body.substring(start, body.indexOf('"', start));
    }
}
