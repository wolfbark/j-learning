package dev.vlearning.reliability;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import dev.vlearning.reliability.database.ReportQueryRepository;
import dev.vlearning.reliability.support.MeterSampler;
import dev.vlearning.reliability.support.PostgresTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6, first half: diagnosis. Twelve concurrent report aggregates, a pool of
 * four, a query that holds a connection for a second. Nothing fails. The error
 * rate is zero. Users are furious.
 *
 * <p>This test asserts only what the metrics say, because that is all you get in
 * production, and it passes before you change anything — the diagnosis is the
 * deliverable here.
 *
 * <p>The pinned properties keep it a truthful exhibit: whatever you configure in
 * step 6's second half, this class still demonstrates the unmitigated pool. It
 * also gives the class its own application context, so its rolling maximums
 * cannot leak into the other checkpoint's assertions.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
@SpringBootTest(properties = {
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.datasource.hikari.connection-init-sql=SET statement_timeout = '30s'",
        "spring.jdbc.template.query-timeout=30s"
})
class Checkpoint6PoolDiagnosisTest extends PostgresTestBase {

    @Autowired
    ReportQueryRepository reports;

    @Autowired
    MeterRegistry meters;

    @Autowired
    ChaosSwitch chaos;

    @BeforeEach
    void everyReportTakesTheSlowPath() {
        chaos.set(ChaosSwitch.Mode.POOL_HOG);
    }

    @AfterEach
    void reset() {
        chaos.reset();
    }

    @Test
    @DisplayName("pool exhaustion is diagnosable from hikaricp metrics alone")
    void exhaustionIsVisibleInMetrics() {
        try (var pending = new MeterSampler(meters, "hikaricp.connections.pending")) {
            var outcomes = driveConcurrently(12, reports::aggregate);
            System.out.println(outcomes + " peakPending=" + pending.peak());

            assertThat(pending.peak())
                    .as("threads waiting for a connection: the single most under-watched "
                            + "number in a Spring service")
                    .isGreaterThan(0.0);
            assertThat(outcomes.failures())
                    .as("and nothing failed — the outage is entirely in the latency")
                    .isZero();
        }

        double acquireMaxMillis = meters.get("hikaricp.connections.acquire").timer()
                .max(TimeUnit.MILLISECONDS);
        double usageMaxMillis = meters.get("hikaricp.connections.usage").timer()
                .max(TimeUnit.MILLISECONDS);
        double timeouts = meters.get("hikaricp.connections.timeout").counter().count();
        System.out.printf("acquire max=%.0fms usage max=%.0fms timeouts=%.0f%n",
                acquireMaxMillis, usageMaxMillis, timeouts);

        assertThat(acquireMaxMillis)
                .as("acquire time is queueing for a connection; it is not the database's fault")
                .isGreaterThan(500.0);
        assertThat(usageMaxMillis)
                .as("usage time is how long one query kept a connection — the numerator of "
                        + "your capacity: pool size ÷ hold time = requests per second")
                .isGreaterThan(1_500.0);
        assertThat(timeouts)
                .as("nobody timed out, because Hikari's default patience is 30 seconds. "
                        + "A connection timeout is not a fix, but 30s of hope is not a policy.")
                .isZero();
    }

    @Test
    @DisplayName("the pool's capacity is arithmetic, not an opinion")
    void littlesLaw() {
        int poolSize = (int) meters.get("jdbc.connections.max").gauge().value();
        Duration holdTime = Duration.ofMillis(
                (long) (ReportQueryRepository.PATHOLOGICAL_SECONDS * 1_000));

        double sustainableRps = poolSize / (holdTime.toMillis() / 1000.0);

        assertThat(poolSize).isEqualTo(4);
        assertThat(sustainableRps)
                .as("four connections, two seconds each: two requests per second. No amount "
                        + "of CPU, and no number of virtual threads, changes this number. "
                        + "Note the meter: pool SIZE is jdbc.connections.max, not "
                        + "hikaricp.connections.max — and it is readable before the pool has "
                        + "ever opened a connection.")
                .isEqualTo(2.0);
    }
}
