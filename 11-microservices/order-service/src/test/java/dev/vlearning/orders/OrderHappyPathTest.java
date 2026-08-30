package dev.vlearning.orders;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the phase-1 contract while shipping cooperates: a synchronous call to
 * the shipping-service, made while the customer waits. Green through steps
 * 1–3. Step 4 deliberately changes the contract the first test pins — the
 * lesson tells you to rewrite it when you get there; the rest stay green.
 */
class OrderHappyPathTest extends AbstractIntegrationTest {

    @Test
    void placingAnOrderArrangesShippingAndConfirms() {
        var response = placeOrder("ada", "mechanical keyboard", 2);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json("$.status")).isEqualTo("CONFIRMED");
        assertThat(response.json("$.shipmentId")).isEqualTo("SHP-TEST-1");

        var orderId = response.json("$.orderId");
        SHIPPING.verify(postRequestedFor(urlEqualTo("/shipments"))
                .withRequestBody(matchingJsonPath("$.orderId", WireMock.equalTo(orderId)))
                .withRequestBody(matchingJsonPath("$.quantity", WireMock.equalTo("2"))));
    }

    @Test
    void anOrderCanBeFetchedById() {
        var placed = placeOrder("grace", "rubber duck", 1);
        var fetched = getOrder(placed.json("$.orderId"));

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.json("$.customerId")).isEqualTo("grace");
        assertThat(fetched.json("$.status")).isEqualTo(placed.json("$.status"));
    }

    @Test
    void everyResponseCarriesACorrelationId() {
        var withHeader = placeOrder("ada", "keyboard", 1, "corr-42");
        assertThat(withHeader.headers().getFirst("X-Correlation-Id")).isEqualTo("corr-42");

        var withoutHeader = placeOrder("ada", "keyboard", 1);
        assertThat(withoutHeader.headers().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void nonsenseOrdersAreRejectedBeforeAnyRemoteCall() {
        var response = placeOrder("ada", "keyboard", 0);

        assertThat(response.status()).isEqualTo(400);
        SHIPPING.verify(0, postRequestedFor(urlEqualTo("/shipments")));
    }
}
