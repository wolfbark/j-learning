package dev.vlearning.ledger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres container for the whole test run (singleton pattern: started in a static
 * initializer, shared by every subclass, reaped by Ryuk when the JVM exits). Flyway runs
 * on each Spring context start; migrations are idempotent because Flyway records them.
 *
 * Tests share the database, so they isolate by unique stream ids, not by truncating.
 * That is realistic: an event store in production is never truncated either.
 */
public abstract class PostgresTestBase {

    // Testcontainers 2.x: new package org.testcontainers.postgresql, and the class finally
    // dropped the self-typed generic — no more PostgreSQLContainer<?>.
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
