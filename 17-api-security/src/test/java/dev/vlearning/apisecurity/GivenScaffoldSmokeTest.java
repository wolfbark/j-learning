package dev.vlearning.apisecurity;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test that is enabled in the delivered scaffold. It proves the harness works: Keycloak
 * boots, imports both realms, issues a genuine signed token for a real user, and this service
 * accepts it. If this test fails, nothing else in the lesson will make sense.
 */
class GivenScaffoldSmokeTest extends AbstractSecurityTest {

    @Test
    void keycloak_issues_a_real_token_and_the_api_serves_it() {
        String token = tokenFor(ALICE);

        assertThat(claimsOf(token))
                .contains("\"preferred_username\":\"alice\"")
                .contains("expense-api")
                .contains("EMPLOYEE");

        HttpResponse<String> response = get("/api/expenses/1", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Blue Bottle Coffee").contains("\"ownerUsername\":\"alice\"");
    }

    @Test
    void the_fixture_is_deterministic() {
        HttpResponse<String> response = get("/api/expenses", tokenFor(CAROL));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().split("\"ownerUsername\"", -1)).hasSize(9); // 8 reports
    }

    /**
     * A token that is signed, unexpired and correctly formed — by an authority this service never
     * agreed to trust. Spring rejects it on the issuer claim before it ever looks at the payload.
     */
    @Test
    void a_token_from_an_untrusted_issuer_is_rejected() {
        HttpResponse<String> response = get("/api/expenses/1", foreignIssuerToken());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * The {@code expiring-tests} client is configured with a one-second access token lifespan; step 2
     * needs it. Note what this test does <em>not</em> claim: that the expired token is refused. Out of
     * the box Spring's {@code JwtTimestampValidator} allows 60 seconds of clock skew, so a token that
     * expired two seconds ago still works. That surprise is step 2's job.
     */
    @Test
    void the_short_lived_client_really_does_issue_one_second_tokens() {
        String claims = claimsOf(shortLivedTokenFor(ALICE));

        long issuedAt = longClaim(claims, "iat");
        long expiresAt = longClaim(claims, "exp");

        assertThat(expiresAt - issuedAt).isBetween(1L, 5L);
    }

    private static long longClaim(String claims, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(claims);
        assertThat(matcher.find()).as("claim %s present in %s", name, claims).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
