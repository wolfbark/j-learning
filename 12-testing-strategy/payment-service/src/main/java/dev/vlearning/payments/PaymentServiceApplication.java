package dev.vlearning.payments;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    /** Injected rather than called statically, so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
