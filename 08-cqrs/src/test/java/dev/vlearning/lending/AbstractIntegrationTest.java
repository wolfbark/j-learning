package dev.vlearning.lending;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One PostgreSQL container for the whole test run (classic singleton-container
 * pattern: started once in a static initializer, never stopped — Ryuk reaps
 * it). All integration tests share one Spring context and therefore one
 * Flyway-migrated database; tests are written against disjoint seeded members
 * so they cannot step on each other.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    protected long borrow(long memberId, long bookId) throws Exception {
        String body = mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": %d, "bookId": %d}
                                """.formatted(memberId, bookId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.loanId")).longValue();
    }

    protected Map<String, Object> activity(long memberId) throws Exception {
        return getJson("/api/members/%d/activity".formatted(memberId));
    }

    protected Map<String, Object> activityLegacy(long memberId) throws Exception {
        return getJson("/api/members/%d/activity-legacy".formatted(memberId));
    }

    private Map<String, Object> getJson(String url) throws Exception {
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }
}
