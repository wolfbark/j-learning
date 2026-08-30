package dev.vlearning.apisecurity;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser-facing surface: who may call this API from a page, what the response tells the browser
 * to do, and the one endpoint here that is genuinely CSRF-prone.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5BrowserSurfaceTest extends AbstractSecurityTest {

    private static final String ALLOWED_ORIGIN = "https://expenses.example.com";
    private static final String HOSTILE_ORIGIN = "https://expenses.example.com.evil.test";

    @Value("${expense.legacy.admin-password}")
    private String legacyAdminPassword;

    @Test
    void a_preflight_from_the_configured_origin_is_allowed() {
        HttpResponse<String> response = preflight("/api/expenses", ALLOWED_ORIGIN, "GET");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ALLOWED_ORIGIN);
    }

    @Test
    void a_preflight_from_any_other_origin_is_refused() {
        HttpResponse<String> response = preflight("/api/expenses", HOSTILE_ORIGIN, "GET");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .as("a suffix of an allowed origin is not an allowed origin")
                .isEmpty();
    }

    @Test
    void credentialed_wildcard_cors_is_gone() {
        HttpResponse<String> response = preflight("/api/expenses", ALLOWED_ORIGIN, "GET");

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isNotEqualTo(java.util.Optional.of("*"));
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse(""))
                .as("list the verbs you actually serve")
                .doesNotContain("*");
    }

    @Test
    void responses_carry_the_baseline_security_headers() {
        HttpResponse<String> response = get("/api/expenses", tokenFor(ALICE));

        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        assertThat(response.headers().firstValue("Referrer-Policy")).isPresent();
        assertThat(response.headers().firstValue("X-Frame-Options").orElse("DENY")).isIn("DENY", "SAMEORIGIN");
        assertThat(response.headers().firstValue("Cache-Control").orElse("no-store")).contains("no-store");
    }

    /**
     * The honest part of the CSRF story: a token-authenticated API is not CSRF-prone, because a
     * cross-site request cannot attach an Authorization header. Enabling CSRF protection on it buys
     * nothing and breaks every non-browser client.
     */
    @Test
    void the_token_api_needs_no_csrf_token() {
        HttpResponse<String> response = postJson("/api/expenses", tokenFor(ALICE),
                """
                {"ownerUsername":"alice","team":"alpha","merchant":"Kiosk","amountCents":500,
                 "currency":"EUR","category":"MEALS","cardNumber":"4111111111111111",
                 "employeeEmail":"alice@example.com"}
                """);

        assertThat(response.statusCode()).isLessThan(300);
    }

    /**
     * ...and the other half: the legacy page authenticates with a cookie, which the browser attaches
     * to cross-site requests all by itself. This one needs a CSRF token.
     */
    @Test
    void the_cookie_session_endpoint_refuses_a_write_without_a_csrf_token() {
        var client = newSessionClient();
        logIn(client);

        HttpResponse<String> response = send(client, HttpRequest.newBuilder(uri("/session/preferences"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"notifyByEmail\":\"false\"}"))
                .build());

        assertThat(response.statusCode())
                .as("a session cookie alone must not be enough to change state")
                .isEqualTo(403);
    }

    @Test
    void the_cookie_session_endpoint_accepts_a_write_with_a_csrf_token() {
        var client = newSessionClient();
        logIn(client);
        // The token is rotated on login, so read it again afterwards.
        String csrf = send(client, HttpRequest.newBuilder(uri("/session/csrf")).GET().build()).body();
        String headerName = jsonField(csrf, "headerName");
        String token = jsonField(csrf, "token");
        assertThat(token).as("GET /session/csrf must hand out a token: %s", csrf).isNotNull();

        HttpResponse<String> response = send(client, HttpRequest.newBuilder(uri("/session/preferences"))
                .header("Content-Type", "application/json")
                .header(headerName, token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"notifyByEmail\":\"false\"}"))
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"notifyByEmail\":\"false\"");
    }

    private void logIn(java.net.http.HttpClient client) {
        String csrf = send(client, HttpRequest.newBuilder(uri("/session/csrf")).GET().build()).body();
        String headerName = jsonField(csrf, "headerName");
        String token = jsonField(csrf, "token");
        assertThat(token).as("GET /session/csrf must hand out a token before login: %s", csrf).isNotNull();

        HttpResponse<String> login = send(client, HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(headerName, token)
                .POST(HttpRequest.BodyPublishers.ofString(
                        formBody(Map.of("username", "legacy-admin", "password", legacyAdminPassword))))
                .build());

        assertThat(login.statusCode()).as("form login should redirect on success").isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse("")).doesNotContain("error");
    }
}
