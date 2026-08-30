package dev.vlearning.trips.messages;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topic topology, property-driven so the integration tests can give every
 * Spring context its own isolated set of topics on the shared broker.
 *
 * <ul>
 *   <li>{@code events} — every fact, from every service, one shared topic</li>
 *   <li>{@code *Commands} — one inbox per service; only that service consumes it</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "trips.topics")
public record TripTopics(String events, String flightCommands, String hotelCommands, String paymentCommands) {}
