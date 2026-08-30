package dev.vlearning.apisecurity;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server-Side Request Forgery and injection. Both are the same mistake in different clothing: input
 * that is treated as instructions instead of as data.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6InjectionAndSsrfTest extends AbstractSecurityTest {

    private HttpResponse<String> attachReceipt(String url) {
        return postJson("/api/expenses/1/receipt-from-url", tokenFor(ALICE),
                "{\"url\":\"" + url + "\"}");
    }

    @Test
    void the_cloud_metadata_endpoint_is_refused() {
        assertThat(attachReceipt("http://169.254.169.254/latest/meta-data/iam/security-credentials/")
                .statusCode())
                .as("the single most valuable SSRF target there is")
                .isEqualTo(400);
    }

    @Test
    void link_local_and_private_ranges_are_refused() {
        assertThat(attachReceipt("http://169.254.0.1/").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("http://10.0.0.7/receipt.pdf").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("http://192.168.1.1/receipt.pdf").statusCode()).isEqualTo(400);
    }

    @Test
    void the_service_cannot_be_pointed_at_itself() {
        assertThat(attachReceipt("http://127.0.0.1:" + port + "/api/expenses").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("http://localhost:" + port + "/api/expenses").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("http://[::1]:" + port + "/api/expenses").statusCode()).isEqualTo(400);
    }

    @Test
    void only_http_and_https_survive() {
        assertThat(attachReceipt("file:///etc/passwd").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("gopher://receipts.example.com/x").statusCode()).isEqualTo(400);
    }

    @Test
    void a_host_that_is_not_on_the_allowlist_is_refused() {
        assertThat(attachReceipt("https://receipts.example.com.evil.test/r.pdf").statusCode()).isEqualTo(400);
        assertThat(attachReceipt("https://pastebin.example/r.pdf").statusCode()).isEqualTo(400);
    }

    /**
     * The positive case: a host on {@code expense.receipts.allowed-hosts} gets as far as an actual
     * outbound call. There is no such host in a test sandbox, so the interesting part is that the
     * answer is <em>not</em> 400 — the URL passed validation and failed later, at the network.
     */
    @Test
    void an_allowlisted_host_is_actually_attempted() {
        assertThat(attachReceipt("https://receipts.example.com/r.pdf").statusCode())
                .as("validation must not reject a host you explicitly allowed")
                .isNotEqualTo(400);
    }

    /**
     * A value that reaches the database as a bound parameter is data, whatever it contains. Nothing
     * to fix here — the point is to know how you would prove it.
     */
    @Test
    void the_search_filter_is_bound_not_concatenated() {
        HttpResponse<String> response = get("/api/expenses?q=" + encode("' or 1=1 --"), tokenFor(ALICE));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
        assertThat(get("/api/expenses", tokenFor(ALICE)).statusCode())
                .as("the table is still there")
                .isEqualTo(200);
    }

    /**
     * The half that placeholders cannot save you from: you cannot bind an identifier. An ORDER BY
     * built from user input has to be an allowlist.
     */
    @Test
    void the_sort_parameter_is_an_allowlist_not_a_string() {
        HttpResponse<String> hostile = get(
                "/api/expenses?sort=" + encode("amount_cents; drop table expense_report"), tokenFor(ALICE));

        assertThat(hostile.statusCode())
                .as("an unknown sort key is a client error, not a database error")
                .isEqualTo(400);

        assertThat(get("/api/expenses?sort=amount_cents", tokenFor(ALICE)).statusCode())
                .as("a legitimate sort key still works")
                .isEqualTo(200);
        assertThat(get("/api/expenses", tokenFor(ALICE)).statusCode()).isEqualTo(200);
    }
}
