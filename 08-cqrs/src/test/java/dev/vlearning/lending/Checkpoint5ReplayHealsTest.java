package dev.vlearning.lending;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkpoint 5 — replay to heal. Events were missed while the projector was
 * down; resuming does not bring them back (there is no event store here —
 * that is project 09). What we CAN do is rebuild the view from the system of
 * record: truncate and reproject every member. After the rebuild the view and
 * the legacy query must agree again.
 *
 * Uses member 10 (Ana), whom no other test asserts on.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
@SpringBootTest
@AutoConfigureMockMvc
class Checkpoint5ReplayHealsTest extends AbstractIntegrationTest {

    @Autowired
    ProjectionToggle toggle;

    @Test
    void rebuildingFromTheSystemOfRecordConvergesTheView() throws Exception {
        int openBefore = (int) activityLegacy(10).get("openLoans");

        long firstLoanId;
        long secondLoanId;
        try {
            toggle.pause();
            firstLoanId = borrow(10, 15);
            secondLoanId = borrow(10, 5);
        } finally {
            toggle.resume();
        }

        // Two events were dropped on the floor: the dashboard is behind.
        assertThat((int) activity(10).get("openLoans"))
                .as("dashboard after projector downtime, before rebuild")
                .isEqualTo(openBefore);

        mvc.perform(post("/api/admin/member-activity/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedMembers", greaterThanOrEqualTo(10)));

        Map<String, Object> view = activity(10);
        Map<String, Object> legacy = activityLegacy(10);
        assertThat(view)
                .as("dashboard for member 10 after rebuild (view vs legacy)")
                .isEqualTo(legacy);
        assertThat((int) view.get("openLoans")).isEqualTo(openBefore + 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> currentLoans = (List<Map<String, Object>>) view.get("currentLoans");
        assertThat(currentLoans)
                .extracting(loan -> ((Number) loan.get("loanId")).longValue())
                .contains(firstLoanId, secondLoanId);
    }
}
