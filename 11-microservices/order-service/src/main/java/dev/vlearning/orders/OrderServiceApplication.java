package dev.vlearning.orders;

import dev.vlearning.orders.shipping.ShippingClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link ShippingClient} as an auto-configured HTTP service client:
 * Boot builds a RestClient-backed proxy for the interface and binds it to the
 * properties under {@code spring.http.serviceclient.shipping.*}.
 */
@SpringBootApplication
@ImportHttpServices(group = "shipping", types = ShippingClient.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
