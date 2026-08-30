package dev.vlearning.coordination;

import javax.sql.DataSource;

import dev.vlearning.coordination.billing.Billing;
import dev.vlearning.coordination.billing.BillingWorker;
import dev.vlearning.coordination.gateway.PaymentGateway;
import dev.vlearning.coordination.locking.LeaseService;
import dev.vlearning.coordination.support.DbSession;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres, one Spring context, three customers to bill, ten jobs to do, and
 * a fresh {@link PaymentGateway} before every test.
 *
 * <p>{@link #worker(String)} builds a {@link BillingWorker} — one pod. Tests
 * build two of them and let them argue.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final String PERIOD = "2026-08";
    protected static final int CUSTOMERS = 3;
    protected static final int JOBS = 10;
    protected static final long TOTAL_OWED = 6000;

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
    protected PaymentGateway gateway;

    @Autowired
    protected Billing billing;

    @Autowired
    protected LeaseService leases;

    @BeforeEach
    void cleanSlate() {
        gateway.reset();
        jdbc.sql("DELETE FROM invoice").update();
        jdbc.sql("DELETE FROM job").update();
        jdbc.sql("DELETE FROM job_lock").update();
        jdbc.sql("DELETE FROM customer").update();
        jdbc.sql("""
                INSERT INTO customer (id, name, amount) VALUES
                    (1, 'ada', 1000), (2, 'linus', 2000), (3, 'grace', 3000)
                """).update();
        // The lease row exists and is long expired: nobody holds it.
        jdbc.sql("""
                INSERT INTO job_lock (name, locked_until, locked_by, fencing_token)
                VALUES ('nightly-billing', now() - interval '1 day', NULL, 0)
                """).update();
        jdbc.sql("""
                INSERT INTO job (id, payload, status)
                SELECT n, 'job-' || n, 'PENDING' FROM generate_series(1, 10) AS n
                """).update();
    }

    /** One pod, with its own identity and its own idea of what is going on. */
    protected BillingWorker worker(String id) {
        return new BillingWorker(billing, leases, id);
    }

    protected DbSession session(String name) {
        return new DbSession(UNPOOLED, name);
    }

    protected long invoiceCount() {
        return jdbc.sql("SELECT count(*) FROM invoice").query(Long.class).single();
    }

    protected long invoiceCountFor(String customer) {
        return jdbc.sql("SELECT count(*) FROM invoice WHERE customer = :c")
                .param("c", customer).query(Long.class).single();
    }

    protected static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
