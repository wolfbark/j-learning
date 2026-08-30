package dev.vlearning.lending;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 4 — feel the staleness. With the projector paused (simulating a
 * crashed/lagging projector, or simply an asynchronous one mid-flight), a
 * borrow succeeds and is fully visible in the system of record — but the
 * dashboard still shows the old world. This is exactly the read-your-writes
 * gap every split read model buys you; the README discusses what products do
 * about it. Requires the projector to consult ProjectionToggle (step 4).
 *
 * Uses member 9 (Sofia), whom no other test asserts on.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@SpringBootTest
@AutoConfigureMockMvc
class Checkpoint4EventualConsistencyTest extends AbstractIntegrationTest {

    @Autowired
    ProjectionToggle toggle;

    @Test
    void dashboardGoesStaleWhileTheProjectorIsDown() throws Exception {
        int openOnDashboardBefore = (int) activity(9).get("openLoans");
        long openInRecordBefore = openLoansInSystemOfRecord(9);

        long loanId;
        try {
            toggle.pause();

            loanId = borrow(9, 7);

            // The write model is correct and fully committed...
            assertThat(openLoansInSystemOfRecord(9)).isEqualTo(openInRecordBefore + 1);
            // ...and the legacy query over the system of record proves it:
            assertThat((int) activityLegacy(9).get("openLoans"))
                    .isEqualTo(openOnDashboardBefore + 1);

            // ...but the dashboard the user is looking at has not caught up.
            Map<String, Object> stale = activity(9);
            assertThat((int) stale.get("openLoans"))
                    .as("view-backed dashboard while the projector is down")
                    .isEqualTo(openOnDashboardBefore);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> currentLoans = (List<Map<String, Object>>) stale.get("currentLoans");
            assertThat(currentLoans)
                    .noneMatch(loan -> ((Number) loan.get("loanId")).longValue() == loanId);
        } finally {
            toggle.resume();
        }

        // Note what did NOT happen: resuming does not replay the missed event.
        // The view for member 9 stays stale until somebody rebuilds it (step 5).
    }

    private long openLoansInSystemOfRecord(long memberId) {
        return jdbc.sql("SELECT count(*) FROM loans WHERE member_id = :id AND returned_on IS NULL")
                .param("id", memberId).query(Long.class).single();
    }
}
