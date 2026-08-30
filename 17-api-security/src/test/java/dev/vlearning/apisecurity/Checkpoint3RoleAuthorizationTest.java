package dev.vlearning.apisecurity;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3RoleAuthorizationTest extends AbstractSecurityTest {

    /** Report 2 belongs to alice, is SUBMITTED, and sits on team alpha, which carol manages. */
    private static final String ALICE_SUBMITTED = "/api/expenses/2/approve";

    @Test
    void keycloak_realm_roles_arrive_in_the_token() {
        assertThat(claimsOf(tokenFor(CAROL))).contains("realm_access").contains("MANAGER");
        assertThat(claimsOf(tokenFor(ALICE))).contains("EMPLOYEE").doesNotContain("MANAGER");
    }

    @Test
    void an_employee_cannot_approve() {
        HttpResponse<String> response = postJson(ALICE_SUBMITTED, tokenFor(ALICE), "");

        assertThat(response.statusCode())
                .as("alice is authenticated but has no MANAGER role: this is 403, not 401")
                .isEqualTo(403);
    }

    @Test
    void a_manager_can_approve() {
        HttpResponse<String> response = postJson(ALICE_SUBMITTED, tokenFor(CAROL), "");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"APPROVED\"");
    }

    @Test
    void an_employee_can_still_do_employee_things() {
        assertThat(postJson("/api/expenses/1/submit", tokenFor(ALICE), "").statusCode()).isEqualTo(200);
        assertThat(get("/api/expenses/1", tokenFor(ALICE)).statusCode()).isEqualTo(200);
    }
}
