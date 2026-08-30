package dev.vlearning.orders;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2, the cure: the orders API answers within its 2-second SLA no matter
 * what shipping is doing. Slow or dead shipping degrades the order to
 * SHIPPING_PENDING — accepted, shipping owed — instead of degrading *us*.
 *
 * Red on the pristine scaffold. Green once you (a) configure connect/read
 * timeouts on the shipping client and (b) catch the failure and accept the
 * order anyway. Stays green for the rest of the lesson.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2TimeoutsAndFallbackTest extends AbstractIntegrationTest {

    @Test
    void ordersRespondWithinTheSlaEvenWhenShippingIsSlow() {
        stubShippingSlow(3000);

        var response = placeOrder("ada", "keyboard", 1);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.elapsedMillis())
                .as("the SLA is 2 seconds — shipping's latency must not become ours")
                .isLessThan(2000);
        assertThat(response.json("$.status")).isEqualTo("SHIPPING_PENDING");
        assertThat(response.json("$.shipmentId")).isNull();
    }

    @Test
    void ordersAreAcceptedWhenShippingIsDown() {
        stubShippingDown();

        var response = placeOrder("grace", "duck", 3);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json("$.status")).isEqualTo("SHIPPING_PENDING");

        // and the degraded state is durable, not a white lie in the response
        var fetched = getOrder(response.json("$.orderId"));
        assertThat(fetched.json("$.status")).isEqualTo("SHIPPING_PENDING");
    }
}
