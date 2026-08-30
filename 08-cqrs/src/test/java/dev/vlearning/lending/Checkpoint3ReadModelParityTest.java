package dev.vlearning.lending;

import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpoint 3 — the strangler-style parallel run. The dashboard endpoint now
 * reads from the denormalized member_activity_view; the monster query stays
 * alive at /activity-legacy. For every untouched seeded member the two must
 * agree field for field — that agreement is the only proof you have that the
 * projection is right. Keep both until you trust the new one; then the legacy
 * path dies.
 *
 * Members 1–6 are never mutated by any test, so the comparison is exact.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
@SpringBootTest
@AutoConfigureMockMvc
class Checkpoint3ReadModelParityTest extends AbstractIntegrationTest {

    @Test
    void viewBackedDashboardMatchesTheLegacyQueryForAllSeededMembers() throws Exception {
        for (long memberId = 1; memberId <= 6; memberId++) {
            Map<String, Object> view = activity(memberId);
            Map<String, Object> legacy = activityLegacy(memberId);

            assertThat(view)
                    .as("dashboard for member %d (view vs legacy monster query)", memberId)
                    .isEqualTo(legacy);
        }
    }

    @Test
    void viewTableHoldsOneRowPerMember() {
        long viewRows = jdbc.sql("SELECT count(*) FROM member_activity_view")
                .query(Long.class).single();
        long members = jdbc.sql("SELECT count(*) FROM members")
                .query(Long.class).single();

        assertThat(viewRows).isEqualTo(members);
    }
}
