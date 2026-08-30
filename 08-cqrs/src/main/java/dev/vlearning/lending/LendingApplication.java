package dev.vlearning.lending;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LendingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LendingApplication.class, args);
    }

    /**
     * All date logic (due dates, overdue flags) goes through this clock. The
     * integration tests freeze it at 2026-09-01 via {@code app.fixed-clock} so
     * the seeded data produces deterministic dashboards.
     */
    @Bean
    Clock clock(@Value("${app.fixed-clock:}") String fixedInstant) {
        return fixedInstant.isBlank()
                ? Clock.systemDefaultZone()
                : Clock.fixed(Instant.parse(fixedInstant), ZoneOffset.UTC);
    }
}
