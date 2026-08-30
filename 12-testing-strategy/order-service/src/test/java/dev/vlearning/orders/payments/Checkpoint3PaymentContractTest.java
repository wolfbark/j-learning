package dev.vlearning.orders.payments;

import java.math.BigDecimal;
import java.util.Map;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint 3 — the consumer-driven contract.
 *
 * <p>Read this as a specification written by the consumer, for the provider: these
 * four interactions are everything order-service needs from payment-service. Not
 * everything payment-service <em>does</em> — the provider is free to add fields,
 * endpoints and statuses this file never mentions, and that freedom is the whole
 * economic argument for contract testing over end-to-end testing.
 *
 * <p>The client under test is the real {@link PaymentClient} proxy over a real
 * {@link RestClient} — same annotations, same serializer, same header handling as
 * production. Only the socket on the other end is Pact's mock server.
 *
 * <p>Running this writes {@code target/pacts/order-service-payment-service.json}.
 * That file is the deliverable; step 4 replays it against the provider.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service", pactVersion = PactSpecVersion.V4)
public class Checkpoint3PaymentContractTest {

    private static final String KNOWN_PAYMENT_ID = "pay_9f3c1b7a2d5e4c60";
    private static final String PAYMENT_ID_PATTERN = "pay_[0-9a-f]{16}";
    private static final String ISO_INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    /**
     * Declared explicitly, and <em>before</em> the body: Pact's DSL otherwise
     * defaults to {@code application/json; charset=UTF-8}, while Spring's RestClient
     * sends a bare {@code application/json} — and a contract test that disagrees with
     * its own consumer about a header is worse than no contract test.
     */
    private static final Map<String, String> JSON_RESPONSE_HEADERS = Map.of("Content-Type", "application/json");
    private static final Map<String, String> JSON_REQUEST_HEADERS_WITH_KEY_777 =
            Map.of("Content-Type", "application/json", "Idempotency-Key", "order-777");
    private static final Map<String, String> JSON_REQUEST_HEADERS_WITH_KEY_778 =
            Map.of("Content-Type", "application/json", "Idempotency-Key", "order-778");

    // --- the contract ------------------------------------------------------

    @Pact(consumer = "order-service")
    public V4Pact anApprovedAuthorization(PactDslWithProvider builder) {
        return builder
                .given("the acquirer approves the card")
                .uponReceiving("an authorization for 42.50 USD")
                    .path("/payments")
                    .method("POST")
                    .headers(JSON_REQUEST_HEADERS_WITH_KEY_777)
                    .body(new PactDslJsonBody()
                            .stringType("orderId", "order-777")
                            .decimalType("amount", 42.50)
                            .stringType("currency", "USD")
                            .stringType("cardToken", "tok_visa_ok"))
                .willRespondWith()
                    .status(201)
                    .headers(JSON_RESPONSE_HEADERS)
                    .body(new PactDslJsonBody()
                            .stringMatcher("paymentId", PAYMENT_ID_PATTERN, KNOWN_PAYMENT_ID)
                            .stringType("orderId", "order-777")
                            .decimalType("amount", 42.50)
                            .stringType("currency", "USD")
                            .stringValue("status", "AUTHORIZED")
                            .stringMatcher("createdAt", ISO_INSTANT_PATTERN, "2026-08-25T10:15:30Z"))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "order-service")
    public V4Pact aDeclinedAuthorization(PactDslWithProvider builder) {
        return builder
                .given("the acquirer declines the card")
                .uponReceiving("an authorization with a card the acquirer refuses")
                    .path("/payments")
                    .method("POST")
                    .headers(JSON_REQUEST_HEADERS_WITH_KEY_778)
                    .body(new PactDslJsonBody()
                            .stringType("orderId", "order-778")
                            .decimalType("amount", 10.00)
                            .stringType("currency", "USD")
                            .stringValue("cardToken", "tok_decline_stolen"))
                .willRespondWith()
                    .status(402)
                    .headers(JSON_RESPONSE_HEADERS)
                    .body(new PactDslJsonBody()
                            .stringValue("error", "payment_declined")
                            .stringValue("reason", "CARD_DECLINED")
                            .stringMatcher("paymentId", PAYMENT_ID_PATTERN, KNOWN_PAYMENT_ID))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "order-service")
    public V4Pact aLookupOfAnExistingPayment(PactDslWithProvider builder) {
        return builder
                .given("an authorized payment exists")
                .uponReceiving("a lookup of that payment")
                    .path("/payments/" + KNOWN_PAYMENT_ID)
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(JSON_RESPONSE_HEADERS)
                    .body(new PactDslJsonBody()
                            .stringValue("paymentId", KNOWN_PAYMENT_ID)
                            .stringType("orderId", "order-777")
                            .decimalType("amount", 42.50)
                            .stringType("currency", "USD")
                            .stringValue("status", "AUTHORIZED")
                            .stringMatcher("createdAt", ISO_INSTANT_PATTERN, "2026-08-25T10:15:30Z"))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "order-service")
    public V4Pact aLookupOfAnUnknownPayment(PactDslWithProvider builder) {
        return builder
                .given("no payment exists with that id")
                .uponReceiving("a lookup of a payment that does not exist")
                    .path("/payments/pay_00000000000000ff")
                    .method("GET")
                .willRespondWith()
                    .status(404)
                    .headers(JSON_RESPONSE_HEADERS)
                    .body(new PactDslJsonBody()
                            .stringValue("error", "not_found")
                            .stringType("message", "no payment with id pay_00000000000000ff"))
                .toPact(V4Pact.class);
    }

    // --- the consumer, exercised against the mock provider -----------------

    @Test
    @PactTestFor(pactMethod = "anApprovedAuthorization")
    public void anApprovedAuthorizationBecomesAnApprovedOutcome(MockServer mockServer) {
        var outcome = gatewayAt(mockServer)
                .authorize("order-777", "order-777", new BigDecimal("42.50"), "USD", "tok_visa_ok");

        assertThat(outcome.approved()).isTrue();
        assertThat(outcome.status()).isEqualTo("AUTHORIZED");
        assertThat(outcome.paymentId()).matches(PAYMENT_ID_PATTERN);
        assertThat(outcome.declineReason()).isNull();
    }

    @Test
    @PactTestFor(pactMethod = "aDeclinedAuthorization")
    public void a402IsADeclineNotAFailure(MockServer mockServer) {
        var outcome = gatewayAt(mockServer)
                .authorize("order-778", "order-778", new BigDecimal("10.00"), "USD", "tok_decline_stolen");

        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.declineReason()).isEqualTo("CARD_DECLINED");
        assertThat(outcome.paymentId()).isNotNull();
    }

    @Test
    @PactTestFor(pactMethod = "aLookupOfAnExistingPayment")
    public void aLookupDeserializesIntoThePaymentResponse(MockServer mockServer) {
        var payment = gatewayAt(mockServer).lookup(KNOWN_PAYMENT_ID);

        assertThat(payment.paymentId()).isEqualTo(KNOWN_PAYMENT_ID);
        assertThat(payment.amount()).isEqualByComparingTo("42.50");
        assertThat(payment.currency()).isEqualTo("USD");
        assertThat(payment.status()).isEqualTo("AUTHORIZED");
        assertThat(payment.createdAt()).isNotBlank();
    }

    @Test
    @PactTestFor(pactMethod = "aLookupOfAnUnknownPayment")
    public void anUnknownPaymentIsA404(MockServer mockServer) {
        var gateway = gatewayAt(mockServer);

        assertThatThrownBy(() -> gateway.lookup("pay_00000000000000ff"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    /**
     * The production client, pointed at Pact's mock server. Nothing about
     * {@link HttpPaymentGateway} or {@link PaymentClient} is test-specific.
     */
    private static HttpPaymentGateway gatewayAt(MockServer mockServer) {
        var restClient = RestClient.builder().baseUrl(mockServer.getUrl()).build();
        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return new HttpPaymentGateway(factory.createClient(PaymentClient.class));
    }
}
