package dev.vlearning.apisecurity;

import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2AuthenticationTest extends AbstractSecurityTest {

    @Test
    void a_request_without_a_token_is_refused() {
        HttpResponse<String> response = get("/api/expenses/1", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .as("a 401 has to tell the client how to authenticate")
                .isPresent();
    }

    @Test
    void a_request_with_a_valid_token_is_served() {
        HttpResponse<String> response = get("/api/expenses/1", tokenFor(ALICE));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void a_garbage_token_is_refused() {
        HttpResponse<String> response = get("/api/expenses/1", "not.a.jwt");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * Same realm, same user, same signing key — but the token was minted for a different API. Without
     * an audience check, any service in your estate can replay tokens issued to any other service.
     */
    @Test
    void a_token_minted_for_another_api_is_refused() {
        String token = partnerAudienceTokenFor(ALICE);
        assertThat(claimsOf(token)).contains("partner-api").doesNotContain("expense-api");

        HttpResponse<String> response = get("/api/expenses/1", token);

        assertThat(response.statusCode())
                .as("the aud claim of this token does not name this API")
                .isEqualTo(401);
    }

    @Test
    void a_token_from_an_untrusted_issuer_is_refused() {
        HttpResponse<String> response = get("/api/expenses/1", foreignIssuerToken());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * The trap: {@code JwtTimestampValidator} defaults to <b>60 seconds</b> of clock skew, so a token
     * that expired eight seconds ago is accepted by a stock resource server. Tighten the skew (a few
     * seconds is plenty for machines that run NTP) and this passes.
     */
    @Test
    void an_expired_token_is_refused() throws InterruptedException {
        String token = shortLivedTokenFor(ALICE);
        TimeUnit.SECONDS.sleep(8);

        HttpResponse<String> response = get("/api/expenses/1", token);

        assertThat(response.statusCode())
                .as("this token expired ~7 s ago; the default 60 s clock skew is why it still works")
                .isEqualTo(401);
    }
}
