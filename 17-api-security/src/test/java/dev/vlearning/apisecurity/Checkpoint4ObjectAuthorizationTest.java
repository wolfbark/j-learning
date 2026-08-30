package dev.vlearning.apisecurity;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OWASP API Security Top 10 — API1:2023 Broken Object Level Authorization, plus the property-level
 * variant (API3:2023). The most-exploited class of API bug there is, and the cheapest to test for.
 *
 * <p>Fixture (see {@code ExpenseSeeder}): alice owns 1, 2 and 6; bob owns 3, 4 and 7; carol owns 5;
 * dave owns 8. alice and carol are on team alpha, bob and dave on team beta. carol manages alpha,
 * dave manages beta.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
class Checkpoint4ObjectAuthorizationTest extends AbstractSecurityTest {

    private static final Set<Long> ALICES_REPORTS = Set.of(1L, 2L, 6L);
    private static final long LAST_ID = 8L;

    @Test
    void alice_cannot_read_bobs_report() {
        HttpResponse<String> response = get("/api/expenses/3", tokenFor(ALICE));

        assertThat(response.statusCode())
                .as("prefer 404: a 403 confirms that report 3 exists")
                .isEqualTo(404);
        assertThat(response.body()).doesNotContain("Hotel Adlon");
    }

    @Test
    void alice_cannot_update_bobs_report() {
        HttpResponse<String> attempt = putJson("/api/expenses/4", tokenFor(ALICE),
                """
                {"merchant":"Definitely Legitimate GmbH","amountCents":900000,"category":"TRAVEL"}
                """);

        assertThat(attempt.statusCode()).isEqualTo(404);

        HttpResponse<String> asOwner = get("/api/expenses/4", tokenFor(BOB));
        assertThat(asOwner.statusCode()).isEqualTo(200);
        assertThat(asOwner.body())
                .as("the write must not have landed")
                .contains("Deutsche Bahn")
                .doesNotContain("Definitely Legitimate");
    }

    @Test
    void the_userId_query_parameter_cannot_be_used_to_read_someone_else() {
        HttpResponse<String> response = get("/api/expenses?userId=bob", tokenFor(ALICE));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("the caller's identity comes from the token, never from a query parameter")
                .doesNotContain("\"ownerUsername\":\"bob\"");
    }

    @Test
    void the_unfiltered_list_only_shows_the_callers_own_reports() {
        HttpResponse<String> response = get("/api/expenses", tokenFor(ALICE));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .doesNotContain("\"ownerUsername\":\"bob\"")
                .doesNotContain("\"ownerUsername\":\"carol\"")
                .doesNotContain("\"ownerUsername\":\"dave\"");
        assertThat(response.body().split("\"ownerUsername\"", -1)).hasSize(ALICES_REPORTS.size() + 1);
    }

    /**
     * The attack, written out: walk the id space and see what falls out. Zero foreign records may
     * leak, and every foreign id must answer identically to an id that does not exist at all.
     */
    @Test
    void enumerating_the_id_space_leaks_nothing() {
        String alice = tokenFor(ALICE);
        List<Long> readable = new ArrayList<>();

        for (long id = 1; id <= LAST_ID; id++) {
            HttpResponse<String> response = get("/api/expenses/" + id, alice);
            if (response.statusCode() == 200) {
                readable.add(id);
                assertThat(response.body())
                        .as("report %d was served to alice and she does not own it", id)
                        .contains("\"ownerUsername\":\"alice\"");
            }
            else {
                assertThat(response.statusCode())
                        .as("id %d must be indistinguishable from a non-existent report", id)
                        .isEqualTo(404);
            }
        }

        assertThat(readable).containsExactlyElementsOf(ALICES_REPORTS.stream().sorted().toList());

        HttpResponse<String> nonExistent = get("/api/expenses/9999", alice);
        assertThat(nonExistent.statusCode()).isEqualTo(404);
    }

    /**
     * Property-level authorization (API3:2023): the client is allowed to send a create request, but
     * not to choose who owns the result.
     */
    @Test
    void alice_cannot_create_a_report_owned_by_bob() {
        HttpResponse<String> response = postJson("/api/expenses", tokenFor(ALICE),
                """
                {"ownerUsername":"bob","team":"beta","merchant":"Ghost Expense",
                 "amountCents":100000,"currency":"EUR","category":"MEALS",
                 "cardNumber":"4111111111111111","employeeEmail":"alice@example.com"}
                """);

        assertThat(response.statusCode()).isIn(200, 201, 400);
        if (response.statusCode() < 300) {
            assertThat(jsonField(response.body(), "ownerUsername"))
                    .as("ownership is decided by the token, not by the request body")
                    .isEqualTo("alice");
            assertThat(jsonField(response.body(), "team")).isEqualTo("alpha");
        }
    }

    @Test
    void a_manager_can_only_approve_their_own_teams_reports() {
        // report 8 belongs to dave, on team beta; carol manages alpha
        HttpResponse<String> crossTeam = postJson("/api/expenses/8/approve", tokenFor(CAROL), "");
        assertThat(crossTeam.statusCode())
                .as("MANAGER is not a licence to approve the whole company")
                .isEqualTo(404);

        HttpResponse<String> ownTeam = postJson("/api/expenses/2/approve", tokenFor(CAROL), "");
        assertThat(ownTeam.statusCode()).isEqualTo(200);
        assertThat(ownTeam.body()).contains("\"status\":\"APPROVED\"");
    }
}
