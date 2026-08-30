package dev.vlearning.parcels;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topic names come from configuration so that every test class can point the same listeners at
 * its own topics. Replayed messages then cannot leak between tests — the single most common
 * source of "it passed on my machine" in messaging suites.
 */
@ConfigurationProperties(prefix = "parcels")
public record ParcelsProperties(int scanPartitions, Topics topics, String feedGroup, String notifyGroup) {

    public record Topics(String scans, String tasks, String dlq) {
    }
}
