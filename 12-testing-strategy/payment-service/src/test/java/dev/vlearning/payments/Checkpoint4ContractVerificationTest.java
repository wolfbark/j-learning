package dev.vlearning.payments;

import java.math.BigDecimal;
import java.time.Instant;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import dev.vlearning.payments.domain.Payment;
import dev.vlearning.payments.domain.PaymentRepository;
import dev.vlearning.payments.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Checkpoint 4 — provider verification.
 *
 * <p>This test does not know what the consumer expects; it reads it. Every
 * interaction in {@code ../order-service/target/pacts/*.json} is replayed against
 * this application running on a real port with a real Postgres behind it, and the
 * response is matched against the consumer's expectations.
 *
 * <p>Generate the pact first:
 * <pre>mvn -f ../order-service/pom.xml test -Dtest=Checkpoint3PaymentContractTest</pre>
 *
 * <p>Provider states are the seam: the consumer says "given a payment exists with
 * id X", and the {@code @State} method below is this provider's private business
 * about how to make that true. That is why contract tests do not couple the two
 * codebases — only the two of them agree on the <em>name</em> of the state.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@Provider("payment-service")
@PactFolder("../order-service/target/pacts")
class Checkpoint4ContractVerificationTest extends AbstractIntegrationTest {

    /** The id the consumer's pact asks about. Fixed, because a contract cannot match a random. */
    static final String KNOWN_PAYMENT_ID = "pay_9f3c1b7a2d5e4c60";

    @Autowired
    PaymentRepository repository;

    @BeforeEach
    void aimAtTheRunningApplication(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyTheConsumersExpectations(PactVerificationContext context) {
        context.verifyInteraction();
    }

    // --- provider states ---------------------------------------------------

    @State("the acquirer approves the card")
    void acquirerApproves() {
        // Nothing to seed: an unused idempotency key and a non-decline token is enough.
    }

    @State("the acquirer declines the card")
    void acquirerDeclines() {
        // Deterministic by construction: the consumer's pact sends a tok_decline_* card.
    }

    @State("an authorized payment exists")
    void anAuthorizedPaymentExists() {
        repository.insertIfAbsent(new Payment(
                KNOWN_PAYMENT_ID,
                "order-777",
                new BigDecimal("42.50"),
                "USD",
                PaymentStatus.AUTHORIZED,
                null,
                "seeded-by-provider-state",
                Instant.parse("2026-08-25T10:15:30Z")));
    }

    @State("no payment exists with that id")
    void noSuchPayment() {
        // cleanSlate() in AbstractIntegrationTest already emptied the table.
    }
}
