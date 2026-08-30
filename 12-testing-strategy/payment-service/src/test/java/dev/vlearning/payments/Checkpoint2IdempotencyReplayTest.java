package dev.vlearning.payments;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static dev.vlearning.payments.PaymentPersistenceIntegrationTest.extractPaymentId;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 2 — the integration suite the given tests were missing.
 *
 * <p>Every assertion here is about behaviour that only exists <em>because</em> a
 * real database is underneath: a unique index, a transaction boundary, a race
 * between two connections. {@link PaymentDecisionTest} cannot fail on any of it.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2IdempotencyReplayTest extends AbstractIntegrationTest {

    @Test
    void replayingAnIdenticalRequestReturns200AndTheSamePayment() {
        var first = authorize("replay-1", "order-100", "25.00", "USD", "tok_visa_ok");
        var second = authorize("replay-1", "order-100", "25.00", "USD", "tok_visa_ok");

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(extractPaymentId(second.getBody())).isEqualTo(extractPaymentId(first.getBody()));
        assertThat(countPayments()).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentAmountIs409AndChangesNothing() {
        var first = authorize("replay-2", "order-101", "25.00", "USD", "tok_visa_ok");
        var conflicting = authorize("replay-2", "order-101", "99.00", "USD", "tok_visa_ok");

        assertThat(conflicting.getStatusCode().value()).isEqualTo(409);
        assertThat(conflicting.getBody()).contains("\"error\":\"idempotency_conflict\"");
        assertThat(countPayments()).isEqualTo(1);
        assertThat(storedAmount(extractPaymentId(first.getBody()))).isEqualByComparingTo("25.00");
    }

    @Test
    void aDeclineIsAlsoIdempotent() {
        var first = authorize("replay-3", "order-102", "10.00", "USD", "tok_decline_stolen");
        var replay = authorize("replay-3", "order-102", "10.00", "USD", "tok_decline_stolen");

        assertThat(first.getStatusCode().value()).isEqualTo(402);
        assertThat(replay.getStatusCode().value()).isEqualTo(402);
        assertThat(replay.getBody()).contains("\"reason\":\"CARD_DECLINED\"");
        // A decline is a decision, and a decision is stored: retrying must not
        // eventually produce an approval.
        assertThat(countPayments()).isEqualTo(1);
    }

    @Test
    void twoConcurrentRequestsWithTheSameKeyCreateExactlyOneRow() throws Exception {
        Callable<ResponseEntity<String>> call =
                () -> authorize("replay-4", "order-103", "77.00", "EUR", "tok_visa_ok");

        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(call, call));
            var ids = results.stream().map(f -> {
                try {
                    return extractPaymentId(f.get().getBody());
                }
                catch (Exception e) {
                    throw new AssertionError(e);
                }
            }).distinct().toList();

            // The unique index decides the winner; the loser reads the winner's row.
            assertThat(ids).hasSize(1);
        }
        assertThat(countPayments()).isEqualTo(1);
    }

    @Test
    void aMissingIdempotencyKeyIs400() {
        var response = authorize(null, "order-104", "10.00", "USD", "tok_visa_ok");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("\"error\":\"invalid_request\"");
    }

    @Test
    void anUnknownPaymentIdIs404WithTheSharedErrorShape() {
        var response = getPayment("pay_does_not_exist");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("\"error\":\"not_found\"");
    }
}
