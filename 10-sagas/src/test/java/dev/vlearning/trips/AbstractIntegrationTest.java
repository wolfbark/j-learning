package dev.vlearning.trips;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripTopics;
import dev.vlearning.trips.orchestration.OrchestratorSwitch;
import dev.vlearning.trips.support.KafkaProbe;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared plumbing for every container-backed test: one Postgres, one Kafka for
 * the whole test run (singleton-container pattern). Each PROFILE combination
 * (none / choreography / orchestration) gets its own Spring context — and its
 * own uniquely suffixed topics, so events replayed from an earlier round can
 * never leak into a later one. Tables are shared and wiped before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Fresh topics per Spring context: this method runs once per context build.
        var run = UUID.randomUUID().toString().substring(0, 8);
        registry.add("trips.topics.events", () -> "trips.events." + run);
        registry.add("trips.topics.flight-commands", () -> "trips.flight.commands." + run);
        registry.add("trips.topics.hotel-commands", () -> "trips.hotel.commands." + run);
        registry.add("trips.topics.payment-commands", () -> "trips.payment.commands." + run);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected ChaosToggles chaos;

    @Autowired
    protected MessageBus bus;

    @Autowired
    protected TripTopics topics;

    @Autowired
    protected OrchestratorSwitch orchestratorSwitch;

    @BeforeEach
    void cleanSlate() {
        chaos.reset();
        orchestratorSwitch.restartIfCrashed();
        jdbc.sql("DELETE FROM saga_instance").update();
        jdbc.sql("DELETE FROM payments").update();
        jdbc.sql("DELETE FROM hotel_reservations").update();
        jdbc.sql("DELETE FROM flight_reservations").update();
        jdbc.sql("DELETE FROM trips").update();
    }

    /** Books Ada's Lisbon trip (499.50) via the HTTP API and returns the trip id. */
    protected UUID postTrip() throws Exception {
        String body = mvc.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"traveller": "Ada Lovelace", "destination": "Lisbon", "price": 499.50}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.tripId"));
    }

    protected String tripStatus(UUID tripId) {
        return jdbc.sql("SELECT status FROM trips WHERE trip_id = :id")
                .param("id", tripId).query(String.class).single();
    }

    protected Optional<String> flightStatus(UUID tripId) {
        return jdbc.sql("SELECT status FROM flight_reservations WHERE trip_id = :id")
                .param("id", tripId).query(String.class).optional();
    }

    protected Optional<String> hotelStatus(UUID tripId) {
        return jdbc.sql("SELECT status FROM hotel_reservations WHERE trip_id = :id")
                .param("id", tripId).query(String.class).optional();
    }

    protected Optional<String> paymentStatus(UUID tripId) {
        return jdbc.sql("SELECT status FROM payments WHERE trip_id = :id")
                .param("id", tripId).query(String.class).optional();
    }

    /** The one SELECT that round 2 is about: (current_step, status) of the saga. */
    protected Optional<String> sagaState(UUID tripId) {
        return jdbc.sql("SELECT current_step || '/' || status FROM saga_instance WHERE trip_id = :id")
                .param("id", tripId).query(String.class).optional();
    }

    protected void awaitTripStatus(UUID tripId, String expected) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(tripStatus(tripId)).isEqualTo(expected));
    }

    /** A test-owned consumer on this context's events topic (new messages only). */
    protected KafkaProbe eventsProbe() {
        return new KafkaProbe(KAFKA.getBootstrapServers(), topics.events());
    }
}
