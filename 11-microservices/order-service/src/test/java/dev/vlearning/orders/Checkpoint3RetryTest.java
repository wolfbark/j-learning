package dev.vlearning.orders;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: a bounded retry with backoff and jitter absorbs transient failures
 * without the customer noticing — and gives up on permanent ones while the
 * SLA still holds. The second test pins the retry budget: THREE attempts
 * total, then degrade. Not four. Not forever.
 *
 * Red until you add the retry. NOTE: step 4 removes the synchronous call this
 * class exercises — re-disable it then; it stays as the exhibit of the
 * sync-era resilience you built.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3RetryTest extends AbstractIntegrationTest {

    @Test
    void aTransientShippingFailureIsInvisibleToTheCustomer() {
        // first call fails with a 503, the second succeeds — a deploy blip
        SHIPPING.stubFor(post(urlEqualTo("/shipments")).inScenario("blip")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        SHIPPING.stubFor(post(urlEqualTo("/shipments")).inScenario("blip")
                .whenScenarioStateIs("recovered")
                .willReturn(shipmentCreated(0)));

        var response = placeOrder("ada", "keyboard", 1);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json("$.status")).isEqualTo("CONFIRMED");
        assertThat(response.json("$.shipmentId")).isEqualTo("SHP-TEST-1");
        assertThat(response.elapsedMillis()).isLessThan(2000);
        SHIPPING.verify(exactly(2), postRequestedFor(urlEqualTo("/shipments")));
    }

    @Test
    void aPermanentFailureExhaustsTheBudgetThenDegrades() {
        stubShippingDown();

        var response = placeOrder("grace", "duck", 1);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json("$.status")).isEqualTo("SHIPPING_PENDING");
        assertThat(response.elapsedMillis())
                .as("retries must fit inside the SLA — do the arithmetic before picking the numbers")
                .isLessThan(2000);
        SHIPPING.verify(exactly(3), postRequestedFor(urlEqualTo("/shipments")));
    }
}
