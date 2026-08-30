package dev.vlearning.reliability;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import dev.vlearning.reliability.database.ReportQueryRepository;
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
 * Step 6, second half: the fix, and the honest trade it makes. A statement
 * timeout bounds how long any one query may hold a connection, which converts an
 * invisible latency outage into a visible, bounded error rate.
 *
 * <p>That is a product decision disguised as a property: you have chosen to fail
 * some report requests quickly rather than make all of them slow. Step 7's error
 * budget is where that choice gets accounted for.
 *
 * <p>This test reads your configuration, not overrides of its own — it is the
 * one place in step 6 where application.properties has to be right.
 */
@Disabled("Checkpoint 6 — enable when you finish step 6")
@SpringBootTest
class Checkpoint6StatementTimeoutTest extends PostgresTestBase {

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
    @DisplayName("a statement timeout bounds connection hold time — and makes failure explicit")
    void statementTimeoutBoundsHoldTime() {
        var outcomes = driveConcurrently(12, reports::aggregate);
        double usageMaxMillis = meters.get("hikaricp.connections.usage").timer()
                .max(TimeUnit.MILLISECONDS);
        System.out.println(outcomes + " usageMax=" + usageMaxMillis + "ms");

        assertThat(usageMaxMillis)
                .as("no query may hold a connection for a second any more: cap it well below "
                        + "the query's natural runtime (%s)", ReportQueryRepository.PATHOLOGICAL_SECONDS)
                .isLessThan(1_300.0);

        assertThat(outcomes.slowest())
                .as("and the whole workload is bounded, because the pool turns over predictably")
                .isLessThan(Duration.ofSeconds(6));

        assertThat(outcomes.failures())
                .as("the pathological query now fails instead of hogging: that is the trade, "
                        + "and it is the right one — but it is a trade")
                .isGreaterThan(0);
        assertThat(outcomes.anyErrorMentions("cancel", "timeout", "statement"))
                .as("the error names the cause, which is what makes it actionable")
                .isTrue();
    }
}
