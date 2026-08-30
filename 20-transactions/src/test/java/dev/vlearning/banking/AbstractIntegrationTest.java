package dev.vlearning.banking;

import javax.sql.DataSource;

import dev.vlearning.banking.support.DbSession;
import dev.vlearning.banking.support.Interleaving;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres, one Spring context, and the same three accounts before every
 * test: Ada holds 5 000 in checking and 5 000 in savings, Linus holds 10 000.
 *
 * <p>Hand-driven {@link DbSession}s deliberately bypass the connection pool —
 * step 8 makes the pool small on purpose, and the test instrument should not be
 * competing with the code under test for connections.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final String ADA = "ada";
    protected static final String LINUS = "linus";
    protected static final long ADA_CHECKING = 1L;
    protected static final long ADA_SAVINGS = 2L;
    protected static final long LINUS_CHECKING = 3L;
    protected static final long NO_SUCH_ACCOUNT = 999L;

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
        jdbc.sql("DELETE FROM audit_log").update();
        jdbc.sql("DELETE FROM account").update();
        jdbc.sql("""
                INSERT INTO account (id, customer, kind, balance) VALUES
                    (1, 'ada',   'CHECKING',  5000),
                    (2, 'ada',   'SAVINGS',   5000),
                    (3, 'linus', 'CHECKING', 10000)
                """).update();
    }

    /** A hand-driven session on its own connection. Always close it. */
    protected DbSession session(String name) {
        return new DbSession(UNPOOLED, name);
    }

    protected long balance(long accountId) {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id")
                .param("id", accountId).query(Long.class).single();
    }

    protected long combined(String customer) {
        return jdbc.sql("SELECT coalesce(sum(balance), 0) FROM account WHERE customer = :customer")
                .param("customer", customer).query(Long.class).single();
    }

    protected long auditRowCount() {
        return jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
    }
}
