package dev.vlearning.orders;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2, exhibit A: these tests PASS against the pristine scaffold — and what
 * they prove is the disease. A slow dependency makes *us* slow, then makes us
 * *unavailable*, without a single error being thrown anywhere.
 *
 * After you configure timeouts, both tests MUST fail — the calls come back
 * fast. Re-disable the class at that point; it documents what you fixed.
 */
@Disabled("Checkpoint 2 — enable to witness the cascade, re-disable once timeouts are in place")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.threads.max=2")
class Checkpoint2aCascadeDemoTest extends AbstractIntegrationTest {

    @Test
    void theCallerInheritsTheCalleesLatency() {
        stubShippingSlow(3000);

        var response = placeOrder("ada", "keyboard", 1);

        // We did nothing wrong. We are still slow. The customer waited three
        // seconds because a machine they've never heard of felt like it.
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.elapsedMillis()).isGreaterThan(2500);
    }

    @Test
    void slowShippingStarvesRequestsThatNeverTouchShipping() throws Exception {
        stubShippingSlow(3000);

        // Two orders park themselves on shipping's doorstep — and on this
        // service's only two worker threads (server.tomcat.threads.max=2).
        try (var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> placeOrder("ada", "keyboard", 1));
            executor.submit(() -> placeOrder("grace", "duck", 1));
            Thread.sleep(500); // let both requests occupy the workers

            // A plain GET — no shipping involved — now queues behind them.
            var read = getOrder(randomOrderId());
            assertThat(read.elapsedMillis())
                    .as("a read that needs no remote call should have been instant, "
                            + "but every worker thread is parked inside shipping")
                    .isGreaterThan(1500);

            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
