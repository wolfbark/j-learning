package dev.vlearning.production.gateway;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A real HTTP call to a real socket — which is what makes the timeout lesson in
 * step 1 honest. In the tests the far end is WireMock, configured per test to be
 * slow, flaky, or dead.
 *
 * <p>Note what is missing: no timeout, no retry, no breaker. This is the
 * "it worked on my machine" client, and every step of this lesson is a bill
 * that arrives because of it.
 */
@Component
public class HttpPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentGateway.class);

    private final RestClient client;
    private final GatewayMeter meter;

    public HttpPaymentGateway(RestClient.Builder builder,
                              @Value("${gateway.base-url}") String baseUrl,
                              GatewayMeter meter) {
        this.client = builder.baseUrl(baseUrl).build();
        this.meter = meter;
    }

    @Override
    public Authorization authorize(String orderId, long amountCents) {
        meter.started();
        try {
            return client.post()
                    .uri("/authorize")
                    .body(Map.of("orderId", orderId, "amountCents", amountCents))
                    .retrieve()
                    .body(Authorization.class);
        } catch (RuntimeException e) {
            log.debug("gateway call failed: {}", e.getMessage());
            throw new GatewayException("payment gateway call failed", e);
        } finally {
            meter.finished();
        }
    }
}
