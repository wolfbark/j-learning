package dev.vlearning.production;

import dev.vlearning.production.support.AbstractProductionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour, pinned. Timeouts, retries, limits and breakers must not change what
 * a successful checkout looks like — if one of them does, it is a bug, not a
 * hardening measure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutContractTest extends AbstractProductionTest {

    @Test
    @DisplayName("a healthy gateway produces a confirmed checkout")
    void happyPath() throws Exception {
        gatewayRespondsOk();

        var response = checkout("order-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"CONFIRMED\"")
                .contains("\"authorizationCode\":\"AUTH-1\"");
        assertThat(meter.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("a gateway failure surfaces as 502, never as a fake confirmation")
    void gatewayFailureIsNotSwallowed() throws Exception {
        gatewayIsDown();

        var response = checkout("order-2");

        assertThat(response.statusCode()).isIn(502, 503);
        assertThat(response.body()).doesNotContain("CONFIRMED");
    }
}
