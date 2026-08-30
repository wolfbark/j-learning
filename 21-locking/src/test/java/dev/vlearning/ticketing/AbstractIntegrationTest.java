package dev.vlearning.ticketing;

import javax.sql.DataSource;

import dev.vlearning.ticketing.support.DbSession;
import dev.vlearning.ticketing.support.Interleaving;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres, one Spring context, and the same conference before every test:
 * ten conference tickets, ten workshop tickets, and twenty free seats.
 *
 * <p>Hand-driven {@link DbSession}s bypass the connection pool so the test
 * instrument never competes with the code under test for connections.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final long CONFERENCE = 1L;
    protected static final long WORKSHOP = 2L;
    protected static final int CAPACITY = 10;
    protected static final int SEATS = 20;

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final DataSource UNPOOLED = unpooled();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static DataSource unpooled() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected Interleaving interleaving;

    @BeforeEach
    void cleanSlate() {
        interleaving.reset();
        jdbc.sql("DELETE FROM booking").update();
        jdbc.sql("DELETE FROM seat").update();
        jdbc.sql("DELETE FROM ticket_type").update();
        jdbc.sql("""
                INSERT INTO ticket_type (id, name, price, available, version) VALUES
                    (1, 'conference', 50000, 10, 0),
                    (2, 'workshop',   20000, 10, 0)
                """).update();
        jdbc.sql("""
                INSERT INTO seat (id, section, label, status)
                SELECT n, 'STALLS', 'A' || n, 'FREE' FROM generate_series(1, 20) AS n
                """).update();
    }

    protected DbSession session(String name) {
        return new DbSession(UNPOOLED, name);
    }

    protected int available(long ticketTypeId) {
        return jdbc.sql("SELECT available FROM ticket_type WHERE id = :id")
                .param("id", ticketTypeId).query(Integer.class).single();
    }

    protected long versionOf(long ticketTypeId) {
        return jdbc.sql("SELECT version FROM ticket_type WHERE id = :id")
                .param("id", ticketTypeId).query(Long.class).single();
    }

    protected int ticketsSold(long ticketTypeId) {
        return jdbc.sql("SELECT coalesce(sum(quantity), 0) FROM booking WHERE ticket_type_id = :id")
                .param("id", ticketTypeId).query(Integer.class).single();
    }

    protected long heldSeatCount() {
        return jdbc.sql("SELECT count(*) FROM seat WHERE status = 'HELD'").query(Long.class).single();
    }
}
