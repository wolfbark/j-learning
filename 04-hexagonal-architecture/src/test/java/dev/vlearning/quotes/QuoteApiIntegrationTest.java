package dev.vlearning.quotes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the externally observable behavior of the quote API. These tests are
 * ENABLED from day one and must stay green through every refactoring round —
 * they are the safety net that makes the whole lesson honest.
 *
 * The app is started on a port chosen up front so that the embedded stub rate
 * provider (part of this same app) can be reached at a base URL that is known
 * before the Spring context exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class QuoteApiIntegrationTest {

    static final int PORT = TestSocketUtils.findAvailableTcpPort();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("rate-provider.base-url", () -> "http://localhost:" + PORT);
    }

    private final RestClient http = RestClient.builder()
            .baseUrl("http://localhost:" + PORT)
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    record CreateQuoteRequest(String productCode, int age, List<String> riskFactors) {
    }

    record QuoteResponse(UUID id, String productCode, int age, String riskFactors,
                         BigDecimal monthlyPremium, String currency, Instant createdAt) {
    }

    record CreateProductRequest(String code, String name) {
    }

    record ProductResponse(String code, String name) {
    }

    @Test
    void standardAdultPaysTheBaseRate() {
        ResponseEntity<QuoteResponse> response = postQuote(new CreateQuoteRequest("AUTO", 30, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().monthlyPremium()).isEqualByComparingTo("90.00");
        assertThat(response.getBody().currency()).isEqualTo("EUR");
    }

    @Test
    void youngDriverWithClaimsHistoryPays70PercentMore() {
        ResponseEntity<QuoteResponse> response =
                postQuote(new CreateQuoteRequest("AUTO", 22, List.of("PREVIOUS_CLAIMS")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().monthlyPremium()).isEqualByComparingTo("153.00");
    }

    @Test
    void smokerPays20PercentMoreOnLifeInsurance() {
        ResponseEntity<QuoteResponse> response =
                postQuote(new CreateQuoteRequest("LIFE", 45, List.of("SMOKER")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().monthlyPremium()).isEqualByComparingTo("144.00");
    }

    @Test
    void seniorPays20PercentMoreOnHomeInsurance() {
        ResponseEntity<QuoteResponse> response =
                postQuote(new CreateQuoteRequest("HOME", 71, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().monthlyPremium()).isEqualByComparingTo("54.60");
    }

    @Test
    void allSurchargesStackAdditively() {
        ResponseEntity<QuoteResponse> response = postQuote(new CreateQuoteRequest(
                "AUTO", 23, List.of("SMOKER", "HAZARDOUS_OCCUPATION", "PREVIOUS_CLAIMS")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // 90.00 * (1 + 0.30 + 0.20 + 0.15 + 0.40) = 184.50
        assertThat(response.getBody().monthlyPremium()).isEqualByComparingTo("184.50");
    }

    @Test
    void underageApplicantIsRejected() {
        ResponseEntity<Void> response = postQuoteExpectingError(new CreateQuoteRequest("AUTO", 17, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownProductIsNotFound() {
        ResponseEntity<Void> response = postQuoteExpectingError(new CreateQuoteRequest("PET", 30, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void quoteCanBeFetchedAfterCreation() {
        QuoteResponse created = postQuote(new CreateQuoteRequest("LIFE", 52, List.of())).getBody();

        ResponseEntity<QuoteResponse> fetched = http.get()
                .uri("/quotes/{id}", created.id())
                .retrieve()
                .toEntity(QuoteResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().id()).isEqualTo(created.id());
        assertThat(fetched.getBody().monthlyPremium()).isEqualByComparingTo(created.monthlyPremium());
    }

    @Test
    void missingQuoteIsNotFound() {
        ResponseEntity<Void> response = http.get()
                .uri("/quotes/{id}", UUID.randomUUID())
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seededProductsAreListed() {
        ResponseEntity<List<ProductResponse>> response = http.get()
                .uri("/products")
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<List<ProductResponse>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ProductResponse::code)
                .contains("AUTO", "HOME", "LIFE");
    }

    @Test
    void productsCanBeCreated() {
        ResponseEntity<ProductResponse> created = http.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProductRequest("TRAVEL", "Travel insurance"))
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<ProductResponse> all = http.get()
                .uri("/products")
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<ProductResponse>>() { });

        assertThat(all).extracting(ProductResponse::code).contains("TRAVEL");
    }

    private ResponseEntity<QuoteResponse> postQuote(CreateQuoteRequest request) {
        return http.post()
                .uri("/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(QuoteResponse.class);
    }

    private ResponseEntity<Void> postQuoteExpectingError(CreateQuoteRequest request) {
        return http.post()
                .uri("/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
