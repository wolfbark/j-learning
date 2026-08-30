package dev.vlearning.production.resilience;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A circuit breaker, given as configuration so the lesson can be about *using*
 * it rather than tuning it. Step 4 is where you wrap the gateway call.
 *
 * <p>The numbers are deliberately small so a test can trip the breaker in a
 * second; production values are usually a 20–100 call window over 10–60 s. The
 * important part is the shape: count the last N calls, open above a failure
 * rate, wait, then let a few probes through.
 */
@Configuration
public class CircuitBreakerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfiguration.class);

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        var config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(8)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        var registry = CircuitBreakerRegistry.of(config);
        // Breaker state belongs on your dashboard: "open" is an incident, and the
        // metric is how you find out before the support queue tells you.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    CircuitBreaker paymentGatewayCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker("payment-gateway");
        breaker.getEventPublisher()
                .onStateTransition(event -> log.warn("circuit breaker {}: {}",
                        event.getCircuitBreakerName(), event.getStateTransition()));
        return breaker;
    }
}
