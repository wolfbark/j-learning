package dev.vlearning.loom;

import dev.vlearning.loom.support.AbstractLoomTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour, pinned. Every refactoring in this lesson — concurrent fan-out,
 * structured concurrency, a narrower critical section, a new context primitive
 * — must leave this test untouched and green. If a "performance improvement"
 * breaks it, it was not an improvement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerViewContractTest extends AbstractLoomTest {

    @Test
    @DisplayName("the aggregate view contains all three downstream sections")
    void aggregateViewIsComplete() throws Exception {
        var response = get("/customers/C-1", "contract-test");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"customerId\":\"C-1\"")
                .contains("\"name\":\"Customer C-1\"")
                .contains("\"tier\":\"GOLD\"")
                .contains("\"availableItems\":42")
                .contains("\"currency\":\"EUR\"")
                .contains("\"correlationId\":\"contract-test\"");
        assertThat(downstream.meter().total()).isEqualTo(3);
    }

    @Test
    @DisplayName("the database-backed view is complete too, and borrows a connection")
    void databaseViewIsComplete() throws Exception {
        var response = get("/customers/C-2/with-database", "contract-test-db");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"customerId\":\"C-2\"");
        assertThat(pool.peakConcurrentUse()).isEqualTo(1);
    }
}
