package dev.vlearning.orders;

import java.time.Clock;

import dev.vlearning.orders.payments.PaymentClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link PaymentClient} as an auto-configured HTTP service client: Boot
 * builds a RestClient-backed proxy for the interface and binds it to the
 * properties under {@code spring.http.serviceclient.payments.*}.
 */
@SpringBootApplication
@ImportHttpServices(group = "payments", types = PaymentClient.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
