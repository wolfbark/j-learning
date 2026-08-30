package dev.vlearning.trips;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ONE runnable app that simulates FOUR separately deployed processes: the booking
 * API plus three downstream services (flight, hotel, payment). Each lives in its
 * own package, owns its own tables, and talks to the others ONLY via Kafka topics
 * — {@code ArchitectureTest} fails the build on any direct bean call between them.
 * One JVM keeps the lab debuggable; the topics keep the pain realistic.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TripsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripsApplication.class, args);
    }
}
