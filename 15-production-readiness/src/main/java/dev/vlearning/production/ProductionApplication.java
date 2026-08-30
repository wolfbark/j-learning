package dev.vlearning.production;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * {@code @EnableResilientMethods} switches on Spring Framework 7's built-in
 * resilience interceptors — the ones behind {@code @Retryable} and
 * {@code @ConcurrencyLimit}. Before Framework 7 both of those needed the
 * separate spring-retry project; now retry, backoff, jitter and concurrency
 * throttling are core, and Resilience4j is reserved for what core deliberately
 * left out (circuit breakers, rate limiters, bulkhead metrics).
 */
@SpringBootApplication
@EnableResilientMethods
public class ProductionApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductionApplication.class, args);
    }
}
