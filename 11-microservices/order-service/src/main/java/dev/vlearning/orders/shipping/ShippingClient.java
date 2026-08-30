package dev.vlearning.orders.shipping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the shipping-service. The implementation is a
 * proxy generated at startup; base URL and (eventually) timeouts come from
 * {@code spring.http.serviceclient.shipping.*}.
 */
@HttpExchange("/shipments")
public interface ShippingClient {

    @PostExchange
    ShipmentResponse arrange(@RequestBody ShipmentRequest request);
}
