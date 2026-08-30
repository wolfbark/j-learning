package dev.vlearning.apisecurity;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.vlearning.apisecurity.expense.ExpenseSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared harness: a real Postgres, a real Keycloak, and real HTTP.
 *
 * <p>Both containers are singletons — started once in a static initializer and reused by every test
 * class in the JVM, which is why there is no {@code @Testcontainers} annotation and no per-class
 * lifecycle. Keycloak imports the {@code expenses} realm (the one this API trusts) and
 * the {@code elsewhere} realm (an issuer it must not trust) from {@code src/test/resources/keycloak}.
 *
 * <p>Tokens are fetched over the direct access grant (OAuth2 "password" grant). That grant is
 * deliberately gone from Spring's <em>client</em> side, but Keycloak will still issue with it, which
 * makes it the least ceremonious way for a test to get a genuine signed token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractSecurityTest {

    protected static final String EXPENSE_REALM = "expenses";
    protected static final String OTHER_REALM = "elsewhere";

    /** Confidential client whose tokens carry {@code aud: expense-api}. */
    protected static final String API_CLIENT = "expense-tests";
    protected static final String API_CLIENT_SECRET = "expense-tests-secret";
    /** Same realm, same users, but {@code aud: partner-api} — a token meant for a different API. */
    protected static final String PARTNER_CLIENT = "partner-tests";
    protected static final String PARTNER_CLIENT_SECRET = "partner-tests-secret";
    /** Same realm, {@code aud: expense-api}, access tokens that live for one second. */
    protected static final String EXPIRING_CLIENT = "expiring-tests";
    protected static final String EXPIRING_CLIENT_SECRET = "expiring-tests-secret";
    /** A client in the realm this API does not trust. */
    protected static final String OTHER_CLIENT = "other-tests";
    protected static final String OTHER_CLIENT_SECRET = "other-tests-secret";

    protected static final String ALICE = "alice";
    protected static final String BOB = "bob";
    protected static final String CAROL = "carol";
    protected static final String DAVE = "dave";
    protected static final String MALLORY = "mallory";

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    // Testcontainers 2.x: PostgreSQLContainer is no longer generic — no type parameter here.
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    protected static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>("quay.io/keycloak/keycloak:26.4")
                    .withExposedPorts(8080)
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("keycloak/expenses-realm.json"),
                            "/opt/keycloak/data/import/expenses-realm.json")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("keycloak/elsewhere-realm.json"),
                            "/opt/keycloak/data/import/elsewhere-realm.json")
                    .withCommand("start-dev", "--import-realm")
                    // Waiting on the realm's own discovery document proves the import finished,
                    // not merely that the process is listening.
                    .waitingFor(Wait.forHttp("/realms/expenses/.well-known/openid-configuration")
                            .forPort(8080)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        POSTGRES.start();
        KEYCLOAK.start();
    }

    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri(EXPENSE_REALM));
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ExpenseSeeder seeder;

    @BeforeEach
    void resetFixture() {
        seeder.reset();
    }

    // --- URLs -------------------------------------------------------------------------------

    protected static String keycloakBaseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    protected static String issuerUri(String realm) {
        return keycloakBaseUrl() + "/realms/" + realm;
    }

    protected String apiBaseUrl() {
        return "http://localhost:" + port;
    }

    // --- tokens -----------------------------------------------------------------------------

    /** A token for one of the four employees, issued to the client this API trusts. */
    protected static String tokenFor(String username) {
        return token(EXPENSE_REALM, API_CLIENT, API_CLIENT_SECRET, username, username + "-pw");
    }

    /** Same user, same realm, but the token names a different audience. */
    protected static String partnerAudienceTokenFor(String username) {
        return token(EXPENSE_REALM, PARTNER_CLIENT, PARTNER_CLIENT_SECRET, username, username + "-pw");
    }

    /** A token that expires one second after it is issued. */
    protected static String shortLivedTokenFor(String username) {
        return token(EXPENSE_REALM, EXPIRING_CLIENT, EXPIRING_CLIENT_SECRET, username, username + "-pw");
    }

    /** A perfectly valid token — signed by an issuer this API never agreed to trust. */
    protected static String foreignIssuerToken() {
        return token(OTHER_REALM, OTHER_CLIENT, OTHER_CLIENT_SECRET, MALLORY, "mallory-pw");
    }

    protected static String token(String realm, String clientId, String clientSecret,
                                  String username, String password) {
        String form = "grant_type=password"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&username=" + encode(username)
                + "&password=" + encode(password)
                + "&scope=openid";
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuerUri(realm) + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Keycloak refused to issue a token (%d): %s"
                    .formatted(response.statusCode(), response.body()));
        }
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("no access_token in Keycloak's answer: " + response.body());
        }
        return matcher.group(1);
    }

    /** The middle segment of a JWT, base64url-decoded. Handy when a test needs to explain itself. */
    protected static String claimsOf(String jwt) {
        String[] parts = jwt.split("\\.");
        return new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    // --- HTTP -------------------------------------------------------------------------------

    protected HttpResponse<String> get(String path, String token) {
        return send(authorize(HttpRequest.newBuilder(uri(path)).GET(), token).build());
    }

    protected HttpResponse<String> postJson(String path, String token, String json) {
        return send(authorize(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), token).build());
    }

    protected HttpResponse<String> putJson(String path, String token, String json) {
        return send(authorize(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)), token).build());
    }

    /** A CORS preflight, exactly as a browser would send it. */
    protected HttpResponse<String> preflight(String path, String origin, String method) {
        return send(HttpRequest.newBuilder(uri(path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build());
    }

    protected URI uri(String path) {
        return URI.create(apiBaseUrl() + path);
    }

    protected static HttpRequest.Builder authorize(HttpRequest.Builder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    protected static HttpResponse<String> send(HttpRequest request) {
        return send(HTTP, request);
    }

    protected static HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (IOException e) {
            throw new IllegalStateException("HTTP call failed: " + request.uri(), e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** A client with its own cookie jar — what you need to hold a session in step 5. */
    protected static HttpClient newSessionClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    protected static String formBody(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Crude but dependency-free: pull a string field out of a JSON body. */
    protected static String jsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
