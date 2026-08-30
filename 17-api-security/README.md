# 17 — API Security: Authentication, Authorization, and the Bug That Actually Happens

> After this lesson you can stand up a Spring Security 7 resource server against a real Keycloak,
> explain precisely what a JWT does and does not prove, and — the part that matters — enforce
> *object-level* authorization in a way you can prove with a test. You will also have written the
> exploit for the most common API vulnerability in the world, against your own code, and watched it
> stop working.

## Why this matters (2026)

Authentication is a solved problem you can buy. Authorization is a problem you have to write, every
time, in every endpoint, and it is where real breaches come from.

The industry's own data says so. OWASP's **API Security Top 10 (2023)** puts **Broken Object Level
Authorization** at number one and **Broken Object Property Level Authorization** at number three.
These are not exotic: they are `where id = ?` with no owner check, and a request body that is allowed
to set fields the caller has no business setting. No memory corruption, no crypto, no tooling — a
logged-in user changes a number in a URL. Every bug-bounty report you have ever read that begins "I
noticed the report id was sequential" is this bug.

Three things are genuinely different in 2026 and shape how this project is built:

**Spring Security 7 finished its API cleanup.** Lambda DSL only — the chained `.and()` is gone.
`PathPatternRequestMatcher` replaces the Ant and MVC matchers. The OAuth2 *password* grant is gone
from the client side in line with OAuth 2.1 (Keycloak will still issue with it, which is why the test
harness can use it to mint tokens). The headline additions are first-class multi-factor
authentication and mature passkeys/WebAuthn — neither of which helps you at all with the bug above.

**Identity is external and that is now unremarkable.** The standard shape of a Java API is: an IdP you
did not write (Keycloak, Entra, Auth0), authorization code + PKCE for humans, client credentials for
services, and your service as a pure resource server that validates JWTs and holds no passwords. This
project uses a **real Keycloak in Testcontainers** rather than self-issued test tokens, because
roughly half of the things that go wrong in this area go wrong at the seam between your service and
the IdP: audience claims, nested role claims, clock skew, discovery, key rotation. A hand-rolled test
token hides every one of them.

**"Shift left" became mandatory rather than aspirational.** Dependency scanning, SBOMs and image
scanning are compliance line items now (see the closing section). They are also the easiest part.

Source material: [`../docs/research/platform-and-production.md`](../docs/research/platform-and-production.md) §5, "API security".

## Core concepts

**Three different questions, three different mechanisms.** Most security bugs are a confusion between
them:

| Question | Mechanism | Failure looks like |
|----------|-----------|--------------------|
| Who is calling? | Token validation (signature, issuer, **audience**, expiry) | 401 |
| May this *kind* of user call this endpoint at all? | Roles / scopes / authorities, `@PreAuthorize`, request matchers | 403 |
| May this *specific* user touch this *specific* object? | A predicate in your query | **200, and you never find out** |

The third row is the one with no framework support worth the name, no default, and no error message.
It is the subject of step 4 and it is why this lesson exists.

**What a JWT is.** A signed statement by an issuer, at a point in time, about a subject — plus
whatever claims the issuer felt like including. Validating it means checking, at minimum: the
signature against the issuer's published keys, the `iss` claim, the `exp` claim, and the **`aud`
claim** — is this token *for me*? Skipping the audience check means any service in your estate can
replay a token issued to any other service. Keycloak needs an explicit audience mapper to put a
useful `aud` in the token at all, which is exactly why step 2 tests it.

**What a JWT cannot do: be un-issued.** A self-contained token is valid until it expires, full stop.
There is no revocation list, and asking the IdP on every request (introspection) throws away the only
advantage the format has. Which gives you the standard, unglamorous design:

- **short access-token lifetimes** (minutes), and a refresh token held somewhere safer;
- **"logout" is client-side fiction.** Deleting the token from the browser ends the session in the UI
  and does nothing at all to a copy an attacker already has. Keycloak's back-channel logout can end
  the *SSO session* — the outstanding access token keeps working until `exp`;
- if you truly need instant kill, you need a revocation check on the hot path, and you should decide
  that on purpose rather than discover it during an incident;
- and one trap worth knowing before it bites: Spring's `JwtTimestampValidator` allows **60 seconds of
  clock skew by default**, so a token that expired half a minute ago is still accepted.

**Roles vs scopes vs authorities.** *Roles* say what the user is (`MANAGER`). *Scopes* say what the
client was delegated (`expenses:read`) — a user with the MANAGER role using an app that was only
granted read scope should not be able to approve. Spring flattens both into `GrantedAuthority`
strings, and by default maps only the `scope`/`scp` claim, prefixing nothing, while `hasRole("X")`
looks for the authority `ROLE_X`. Keycloak puts realm roles in a *nested* claim,
`realm_access.roles`, which no default converter reads. So realm roles arriving as authorities is
something you configure, and step 3 is where you notice.

**Prefer 404 to 403 for objects you must not admit exist.** A 403 on `/api/expenses/4711` confirms
that report 4711 exists — free reconnaissance. Answer as if the record is not there, because from
that caller's point of view it is not. (Not a universal rule: inside a team workspace, a 403 that
says "ask the owner for access" is better product design. Decide per resource, deliberately.)

**Enforce ownership in the query, not after it.** These two are not equivalent:

```java
// post-hoc: the row is already in your process, in your logs, in your metrics
var report = repository.findById(id).orElseThrow();
if (!report.ownerUsername().equals(me)) throw new AccessDeniedException("no");

// in the query: the row was never yours to leak
var report = repository.findByIdAndOwner(id, me).orElseThrow(NotFound::new);
```

The second survives the future. The first breaks the day someone adds a second call site, a bulk
endpoint, a GraphQL resolver, or an `if` with an inverted condition — and it breaks silently.

## The project

An **expense-report API**. Employees create, read and update their own reports; managers approve
reports belonging to the teams they manage. The domain is deliberately dull: it exists so that
"object-level authorization" is a sentence about *someone else's dinner receipt* rather than an
abstraction.

Identity is a **real Keycloak 26.4** (Testcontainers), realm `expenses`:

| User | Password | Realm roles | Team |
|------|----------|-------------|------|
| `alice` | `alice-pw` | EMPLOYEE | alpha |
| `bob` | `bob-pw` | EMPLOYEE | beta |
| `carol` | `carol-pw` | EMPLOYEE, MANAGER | alpha (manages alpha) |
| `dave` | `dave-pw` | EMPLOYEE, MANAGER | beta (manages beta) |

Three confidential clients issue tokens by direct access grant: `expense-tests`
(`aud: expense-api` — the one this API should accept), `partner-tests` (`aud: partner-api` — a token
for a different service), and `expiring-tests` (one-second access tokens). A second realm,
`elsewhere`, exists purely to produce a perfectly valid token from an issuer this API must refuse.

**What is given.** A working, authenticated-ish, and deliberately vulnerable service:

- `ExpenseController` — the whole API surface: list/search, create, read, update, submit, approve,
  and "attach a receipt from a URL".
- `ExpenseRepository` — `JdbcClient` over Postgres. Ownership columns exist; nothing consults them.
- `SecurityConfiguration` — two filter chains (a stateless `/api` chain and a legacy cookie-session
  `/session` chain), a `JwtDecoder` wired to Keycloak's issuer, and a set of choices you will be
  undoing.
- `ReceiptFetcher` — a JDK `HttpClient` that fetches whatever URL it is handed.
- `AuditLogger` — "compliance asked for a full audit trail" as interpreted by someone in a hurry.
- `ExpenseSeeder` — the deterministic fixture: reports 1–8, owners as listed in its javadoc. Tests
  reset it before every method, so ids are stable and enumeration tests can be exact.
- `AbstractSecurityTest` — singleton Keycloak + Postgres, `@DynamicPropertySource` wiring, and
  helpers to mint real tokens (`tokenFor("alice")`, `partnerAudienceTokenFor`, `shortLivedTokenFor`,
  `foreignIssuerToken`) and to make raw HTTP calls, including CORS preflights and a cookie-jar client.
- `docs/threat-model.md` — half-finished on purpose; that is step 1.

**Six flaws are planted.** They are listed here and nowhere in the code — no `// TODO: insecure`
comments, because production code does not have those either:

| # | Flaw | Where | Step |
|---|------|-------|------|
| 1 | **BOLA/IDOR**: `GET`/`PUT /api/expenses/{id}` load by id alone; the list endpoint filters by a `?userId=` parameter it takes on faith; `POST` lets the body choose the owner | `ExpenseController`, `ExpenseRepository` | 4 |
| 2 | CORS allows every origin *with credentials*; all security headers are switched off | `SecurityConfiguration` | 5 |
| 3 | SSRF: `POST /api/expenses/{id}/receipt-from-url` fetches any URL, any scheme, following redirects | `ReceiptFetcher` | 6 |
| 4 | Audit log prints the whole request body — card number, email — plus the bearer token and the signing key, at INFO | `AuditLogger` | 7 |
| 5 | No limit on approvals or submissions | `ExpenseController` | 7 |
| 6 | Secrets committed in `application.properties` | `src/main/resources` | 7 |

Plus one that is not on the list because it is a starting point rather than a bug to plant: the `/api`
chain is `permitAll()`, so an unauthenticated request is served happily. That is step 2.

```bash
mvn test          # pristine: the smoke test runs, the 35 checkpoint tests are @Disabled
```

The first run pulls nothing (both images are cached) and starts Keycloak and Postgres once for the
whole JVM. To poke at it by hand:

```bash
docker compose up -d
mvn spring-boot:run
# a token, the honest way:
TOKEN=$(curl -s -d grant_type=password -d client_id=expense-tests \
  -d client_secret=expense-tests-secret -d username=alice -d password=alice-pw \
  http://localhost:8081/realms/expenses/protocol/openid-connect/token | jq -r .access_token)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/expenses/3   # bob's. Enjoy.
```

## Guided steps

### Step 1 — Threat model first, code second

**Goal.** Decide what you are protecting before you decide how. Open
[`docs/threat-model.md`](docs/threat-model.md) and finish it: assets, data classification, trust
boundaries, and a STRIDE-lite table. The first row of each is worked through as a model of the level
of detail wanted — concrete, arguable, and short.

Do this **before reading the source**. Then read the "Six flaws" table above and count how many you
predicted from the outside. Four is a good score; the two you missed are the interesting ones.

<details><summary>Hint — how to keep it from becoming a document nobody reads</summary>

Two rules. First, every threat row ends in a decision: a step of this lesson, a ticket, or "accepted,
because…". A row with no decision is a feeling. Second, timebox it. A threat model that took two days
is never updated, and an out-of-date threat model is worse than none because people trust it.

The highest-yield question in the whole exercise is not a STRIDE letter, it is: *"which of these
fields does the client control?"* Answer for path variables, query parameters, headers, and every
body field. The answer is always "all of them", and yet code keeps being written as if body fields
were somehow more trustworthy than URLs.
</details>

**Done when** the checklist at the bottom of `docs/threat-model.md` is satisfied. No test — a threat
model you can pass with a green bar is not a threat model.

### Step 2 — Authentication: validate the token properly

**Goal.** Make the `/api` chain require a valid token, and make "valid" mean what it should: correct
signature, correct issuer, **correct audience**, and not expired.

Four things to do, in `SecurityConfiguration`:

1. `authorizeHttpRequests` → `anyRequest().authenticated()` (leave a documented exception if you want
   a health endpoint).
2. `sessionManagement` → `STATELESS` for this chain. A token API that mints sessions is a token API
   with a second, undefended authentication mechanism.
3. Add an audience validator to the `JwtDecoder`, reading `expense.api.audience`.
4. Tighten the timestamp validator's clock skew.

<details><summary>Hint — the decoder</summary>

`JwtValidators.createDefaultWithIssuer(issuer)` gives you issuer + timestamp. Compose your own:

```java
@Bean
JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties,
                      @Value("${expense.api.audience}") String audience) {
    String issuer = properties.getJwt().getIssuerUri();
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(issuer),
            new JwtTimestampValidator(Duration.ofSeconds(5)),
            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                    aud -> aud != null && aud.contains(audience))));
    return decoder;
}
```

`JwtDecoders.fromIssuerLocation` performs discovery at startup — which means the app will not start if
the IdP is unreachable. That is usually what you want; know that it is a startup dependency.
</details>

<details><summary>Hint — why the expired-token test needs eight seconds</summary>

Because `JwtTimestampValidator`'s default `clockSkew` is **60 seconds**. The `expiring-tests` client
issues one-second tokens, the test waits eight, and a stock resource server still says 200. The
default exists for machines with sloppy clocks; on anything running NTP, a few seconds is plenty. This
is a good example of a security default that is tuned for compatibility rather than for you.
</details>

**Done when** `Checkpoint2AuthenticationTest` passes. In the delivered scaffold three of its six tests
fail: no token → 200, a `partner-api` token → 200, and an expired token → 200. The other three
already pass, which tells you something useful: Spring gives you signature, issuer and format for
free. It is the *audience* and the *skew* that are your problem.

Write down, in one sentence each, before moving on: what does your API do when the token is expired
mid-session, and what does "log out" mean for a client of this API?

### Step 3 — Role authorization: only managers approve

**Goal.** `POST /api/expenses/{id}/approve` requires the `MANAGER` realm role. Everything else stays
open to any authenticated employee.

Two halves. First, get Keycloak's realm roles into Spring as authorities — they arrive in the nested
`realm_access.roles` claim and nothing reads it by default. Second, enforce the requirement, either as
a request matcher rule or with `@PreAuthorize("hasRole('MANAGER')")` (which needs
`@EnableMethodSecurity`).

<details><summary>Hint — the converter</summary>

```java
JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
converter.setJwtGrantedAuthoritiesConverter(jwt -> {
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    List<String> roles = realmAccess == null ? List.of() : (List<String>) realmAccess.get("roles");
    return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
});
converter.setPrincipalClaimName("preferred_username");
```

Note the `ROLE_` prefix: `hasRole("MANAGER")` looks for the authority `ROLE_MANAGER`, while
`hasAuthority("MANAGER")` does not add anything. Half the "my @PreAuthorize does nothing" questions on
the internet are this. Also note what you lose by *replacing* the default converter: the `scope`
claim's authorities. If your clients are also scope-restricted, map both — a MANAGER using a
read-only client should still not be able to approve.

Register it with `oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))`.
</details>

<details><summary>Hint — 401 vs 403</summary>

An employee calling `approve` must get **403**, not 401. 401 means "I do not know who you are, try
authenticating"; a client that retries authentication in response to a 403 will loop forever. If you
see 401 here, your rule is running before authentication, or your entry point is misconfigured.
</details>

**Done when** `Checkpoint3RoleAuthorizationTest` passes: alice gets 403 on approve, carol gets 200,
and alice can still submit her own report. Be able to explain what would change if this were a scope
(`expenses:approve`) rather than a role, and which one you would want a third-party integration to
carry.

### Step 4 — Object-level authorization (the centrepiece)

**Goal.** Fix the IDOR. This is the step to slow down on.

Right now `carol` has a MANAGER role, so step 3 is satisfied — and `alice` can still read, edit, and
enumerate everybody's expense reports, because nothing anywhere compares the record's owner to the
caller. Four things to fix:

1. `GET /api/expenses/{id}` and `PUT /api/expenses/{id}`: ownership must be part of the **query**.
2. The list endpoint: the caller's identity comes from the token. `?userId=` should be ignored (or
   rejected outright — argue for one).
3. `POST /api/expenses`: the owner and team come from the principal, not from the request body.
4. `approve`: a manager may only approve reports belonging to a team they manage. `MANAGER` is not a
   licence to approve the whole company.

<details><summary>Hint — where the check goes</summary>

Add repository methods that cannot be called wrongly:

```java
Optional<ExpenseReport> findByIdAndOwner(long id, String owner);
List<ExpenseReport> findAllByOwner(String owner, String query, String sortKey);
Optional<ExpenseReport> findByIdForApprover(long id, String manager);  // joins team_manager
```

`ExpenseRepository` already gives you `teamOf(username)` and `teamsManagedBy(username)` so that the
plumbing is not the exercise. The design rule: make the *unsafe* call impossible to express. If
`findById(long)` still exists and is public, someone will use it — delete it, or make it
package-private and used only where a genuine cross-tenant read is intended.

Spring Security's `@PostAuthorize("returnObject.ownerUsername == authentication.name")` and ACL
support exist, and both are post-hoc: the row is loaded, logged, and counted before the check runs.
They are a reasonable second layer, never the only one.
</details>

<details><summary>Hint — 404 or 403?</summary>

Return **404** for a record the caller may not know exists — that is what the checkpoint asserts, and
it means a foreign id is indistinguishable from an id that was never issued. Two consequences worth
noticing: your audit log must record the *denial* (a 404 that is really a denial is invisible
otherwise), and your own operators lose a diagnostic. Both are worth the trade here.
</details>

**Done when** `Checkpoint4ObjectAuthorizationTest` passes — all seven tests. In the delivered scaffold
all seven fail, including `enumerating_the_id_space_leaks_nothing`, which is the exploit written as a
test: walk ids 1..8 as alice and assert that not one foreign record comes back. Keep that test. It is
the cheapest regression net you will ever write, and this bug comes back every time someone adds an
endpoint.

**Why this is number one.** OWASP API Security Top 10 2023 lists **API1:2023 Broken Object Level
Authorization** first and **API3:2023 Broken Object Property Level Authorization** third. Both are in
this step: the id you are allowed to name, and the field you are allowed to set.
<https://owasp.org/API-Security/editions/2023/en/0x11-t10/>

### Step 5 — The browser-facing surface: CORS, headers, and CSRF honestly

**Goal.** Stop advertising this API to every origin on the internet, put the response headers back,
and get CSRF right — which means understanding why it matters for exactly one endpoint here.

1. CORS: replace the `*` origin pattern with the explicit list in `expense.cors.allowed-origins`, name
   the methods and headers you actually serve, and think hard about `allowCredentials`.
2. Headers: the `.headers(disable)` calls are doing real damage. Restore the defaults and add a
   `Content-Security-Policy` and a `Referrer-Policy`.
3. CSRF: enable it on the `/session` chain, leave it off on the `/api` chain, and be able to defend
   both halves of that sentence.

<details><summary>Hint — CORS is not a permission system</summary>

CORS relaxes the browser's same-origin policy; it stops a *page* from reading your response. It stops
nobody's `curl`, and it is not an authorization control. Two specifics that bite:

- `allowedOriginPatterns("*")` together with `allowCredentials(true)` is the combination that makes
  any page in the world able to make credentialed calls on a logged-in user's behalf. (Plain
  `allowedOrigins("*")` + credentials is rejected by the spec; the *patterns* variant reflects the
  request's origin back, which is the same thing with extra steps.)
- Matching origins by prefix or suffix is a classic bypass: `https://expenses.example.com.evil.test`
  passes a naive `endsWith`/`startsWith`, which is why the checkpoint tests exactly that string.
</details>

<details><summary>Hint — the honest CSRF story</summary>

CSRF exists because browsers attach **cookies** to cross-site requests automatically. A bearer token
is not attached automatically — an attacker's page cannot add an `Authorization` header to a
cross-origin request without your CORS configuration letting it. So a purely token-authenticated API
is not CSRF-prone, and turning CSRF protection on for it buys nothing while breaking every non-browser
client.

The moment you have *one* cookie-authenticated endpoint, that reasoning stops applying to it. This
project has one on purpose: `/session/preferences`, behind form login, which the old internal admin
page still calls. `evil.test` can make a logged-in admin's browser POST to it, and the browser will
attach the session cookie. So: CSRF on for that chain, off for `/api`. In a hybrid app that is exactly
what you do — per-chain, not per-application. `SameSite=Lax` cookies raise the bar a lot but are
defence in depth, not a replacement, and they do nothing for same-site subdomain takeovers.

`GET /session/csrf` is given as the standard SPA handshake; make it work. `CookieCsrfTokenRepository`
is the usual choice. Note that the token is rotated on login, so the value you read before logging in
is not the one you need afterwards — the checkpoint test reads it twice, which is a hint rather than
an accident.
</details>

**Done when** `Checkpoint5BrowserSurfaceTest` passes: a preflight from the allowed origin is answered
and one from `https://expenses.example.com.evil.test` is not, the baseline headers are present, a
session write without a CSRF token is refused, one with a token succeeds, and the token API still
needs no CSRF token at all.

### Step 6 — SSRF and injection

**Goal.** `receipt-from-url` currently makes the server fetch anything, from any scheme, following
redirects, from inside your network. Turn it into an allowlist. And prove that the search endpoint's
two inputs are handled correctly — one of them is not.

**SSRF** is the interesting half. The rule is not "block bad URLs", it is **"allow known-good hosts,
then verify what you actually resolved"**:

1. Scheme must be `http` or `https`. Nothing else — `file:`, `gopher:`, `jar:` all have their party
   tricks.
2. Host must be on `expense.receipts.allowed-hosts`, compared as a whole label sequence, not a suffix.
3. Resolve the host and reject loopback, link-local (`169.254.0.0/16` — this is
   `169.254.169.254`, the cloud metadata endpoint, and it is the single most valuable SSRF target in
   existence), private ranges, and IPv6 equivalents (`::1`, `fc00::/7`, `fe80::/10`,
   `::ffff:169.254.169.254`).
4. **Do not follow redirects** (or re-run every check on each hop). An allowlisted host answering
   `302 Location: http://169.254.169.254/` defeats a naive allowlist, and this is the mistake that
   made the 2019 Capital One breach possible.
5. Bound it: connect and read timeouts, a response size cap, and no reflection of the response body
   back to the caller.

Return **400** for a URL you refuse — the client asked for something invalid. Reserve 502 for "I tried
and the far end failed", which is how the checkpoint distinguishes "validated and rejected" from
"attempted".

<details><summary>Hint — the TOCTOU hole in DNS-based checks</summary>

Resolving the host, approving the address, and then handing the *hostname* to an HTTP client means the
client resolves again — and a DNS entry you do not control can answer differently the second time
(DNS rebinding). Doing this properly means connecting to the address you validated while still sending
the right `Host` header and SNI. For this lesson, validate-then-fetch is acceptable and you should
know why it is not sufficient at a bank. The robust answer in production is an egress proxy with an
allowlist, so that a bug in your validation is not the only thing between an attacker and your
metadata service.
</details>

<details><summary>Hint — the injection half is a trap with two sides</summary>

`?q=` is bound as a parameter, so `' or 1=1 --` is a string that matches no merchant. Nothing to fix;
the checkpoint proves it, and knowing how to prove it is the skill.

`?sort=` is concatenated into `ORDER BY`, because **you cannot bind an identifier**. Placeholders
protect values, not column names, table names, or SQL keywords — so a "just use prepared statements"
reflex is not enough here. The only correct fix is an allowlist: a `Map<String, String>` from a
client-facing sort key to a column name you wrote yourself, and a 400 for anything else. This
generalises to `LIMIT`, `ORDER BY … ASC|DESC`, dynamic table names, and every "flexible filter" API
you will ever be asked to build.
</details>

**Done when** `Checkpoint6InjectionAndSsrfTest` passes: the metadata address, private ranges, this
service's own port, non-HTTP schemes and non-allowlisted hosts are all 400; an allowlisted host gets as
far as a real network call; `?q=` is provably bound; and an unknown `?sort=` key is a 400 rather than a
database error.

### Step 7 — Rate limits, secrets, and an audit log you can actually keep

**Goal.** Three small things that fail in production together.

**Rate and business-flow limits (API6:2023).** Every approval in the checkpoint is a syntactically
valid action by a real manager; only the *rate* is wrong. Limit approvals per manager per minute
(`expense.approvals.per-minute`). Resilience4j's `RateLimiter` is already on the classpath; a small
token bucket keyed by username is equally acceptable. Return **429** with a `Retry-After` header.

<details><summary>Hint — put the limiter in front of the handler</summary>

Failed attempts have to count. A limiter that only counts *successful* approvals gives an attacker
unlimited free guesses, which is exactly the pattern behind credential-stuffing and coupon-brute-force
abuse. The checkpoint approves report 2 once (200) and then hammers the same id — those calls are 409s
on the business rule, and they must still consume budget.

Also know what this is not: an in-process limiter is per instance. Three replicas means three times
the limit, and a restart forgets everything. Real limits live at the gateway or in Redis; the
in-process one is a useful backstop and a bad only line of defence.
</details>

**Secrets.** `application.properties` contains an HMAC key and a password. Move them to environment
variables — `${EXPENSE_AUDIT_HMAC_KEY}` — and remember that removing a secret from a file does not
remove it from git history; the real-world response to a committed secret is *rotate it*, then clean
the file. The checkpoint scans the file for any key whose name looks like a secret and insists the
value is a placeholder.

<details><summary>Hint — keeping the app runnable</summary>

A placeholder with an obviously-fake default (`${EXPENSE_AUDIT_HMAC_KEY:dev-only-not-a-secret}`)
passes the checkpoint, boots without ceremony, and cannot be mistaken for a real key. The alternative
is a hard `${VAR}` and a documented `.env` — fail-fast, more friction. For a training scaffold the
first is fine; for a service that signs anything, prefer fail-fast, so a missing secret is a startup
error rather than a silently weak signature.
</details>

**Audit logging.** The current line dumps the request body (card number, email address), the bearer
token, and the signing key. An audit event has to survive being read by people who are not allowed to
see the data it describes — which is everyone with access to your log aggregator. Keep **subject,
action, object id, decision, timestamp**; drop everything else. Log the *denials* too, especially the
404s you introduced in step 4, or your only record of an enumeration attack will be a wall of
innocent-looking 404s in an access log.

<details><summary>Hint — redaction is a design decision, not a regex</summary>

A regex over a formatted string is a losing game — the next field someone adds is not covered, and
`toString()` on a record prints every component. Build the event as a small type with only the fields
you are willing to keep, and never pass a domain object or a request body into the logger at all. If
you must keep a correlation to card data, log a keyed HMAC of it (that is what the key is *for*), not
a prefix and not the last four with everything else.
</details>

**Done when** `Checkpoint7LimitsSecretsAuditTest` passes: a 429 appears within twelve approval
attempts, the config file holds no literal secrets, and the audit output still names who did what to
which object with what outcome while containing no card number, no email address, no `eyJ…`, and no
key.

## Supply chain: dependencies and images

Your code is a minority of what you ship. This is the cheapest security work available and it is not
wired into this build on purpose — an NVD download in `mvn test` is a slow, flaky, offline-hostile
build. Run these deliberately, in CI, on a schedule:

```bash
# Known-vulnerable dependencies. First run downloads the NVD data (slow); needs a free API key.
mvn org.owasp:dependency-check-maven:12.1.0:check -DnvdApiKey=$NVD_API_KEY

# Same job, no local database, much faster — OSS Index / OSV based:
mvn org.sonatype.ossindex.maven:ossindex-maven-plugin:audit

# The image, not the jar: OS packages, the JRE, and your layers.
trivy image expense-api:latest --severity HIGH,CRITICAL
grype expense-api:latest

# An SBOM, because you will be asked for one.
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
syft expense-api:latest -o cyclonedx-json
```

Two habits matter more than the tool you pick: **fail the build on new HIGH/CRITICAL findings only**
(a blanket failure on all findings gets switched off within a week), and **keep the base image
moving** — most image CVEs are fixed by rebuilding on a current base, not by changing your code.

## What this lesson does not cover

Named explicitly, because a checklist that looks complete is its own hazard:

- **mTLS and service-to-service identity.** Workload identity (SPIFFE/SVID), certificate rotation, and
  the service mesh that usually owns it. Bearer tokens between services are common and weaker.
- **Key management at scale.** Rotating signing keys without downtime, `kid` handling, JWKS cache
  invalidation, HSMs, and envelope encryption for data at rest.
- **WAFs, bot management, and DDoS.** Useful, sold aggressively, and no substitute for step 4.
- **Multi-factor authentication and passkeys** — Spring Security 7's headline features, and squarely
  an IdP concern here.
- **Encryption at rest and field-level encryption**, tokenization, and the PCI scope reduction that
  makes "we never store card numbers" the real answer to flaw 4.
- **Authorization as a service** — OPA/Rego, Cedar, OpenFGA/Zanzibar-style relationship models. Once
  "who may see this object" stops fitting in a `WHERE` clause, this is where you go next.
- **Penetration testing and fuzzing.** Every test here is one you wrote knowing the answer. Schemathesis
  or RESTler against your OpenAPI document finds the ones you did not think of.

## Self-check

1. A user's account is disabled in Keycloak at 14:00. Their access token was issued at 13:58 with a
   five-minute lifetime. What can they do at 14:01, and what would it take to stop them?
2. Why is skipping the `aud` check dangerous even when every service in your estate trusts the same
   issuer?
3. An employee gets 403 from `approve` and 404 from another employee's report. Explain both choices.
4. Where exactly does the ownership check belong, and what specifically breaks about `if
   (!report.owner().equals(me)) throw …` six months later?
5. Your API is bearer-token only. Should you enable CSRF protection? Now one endpoint starts using a
   session cookie. What changes, and for which filter chain?
6. Prepared statements everywhere, and the search endpoint is still injectable. How?
7. What does an SSRF allowlist have to do beyond checking the hostname against a list?
8. A colleague suggests logging the full request body "for auditability, we can always redact later".
   Give the two strongest arguments against, one technical and one legal.

## Stretch goals

1. **Multi-tenancy makes it worse.** Add an `organisation` column and a second tenant. Now every query
   needs *two* predicates, and one missing tenant filter is a cross-company data leak. Enforce it
   structurally — Postgres row-level security with a session variable, or a repository layer that
   cannot be called without a tenant — and write the enumeration test that proves it.
2. **Prove it with an ArchUnit rule.** A test that fails if any method in the repository package takes
   an id and no principal, or if a controller calls a `findById` that has no owner parameter. Turning
   a code-review habit into a build failure is the highest-leverage security work in the project.
3. **Token exchange for the service-to-service hop.** Have the API call a second service using
   Keycloak's client-credentials grant, then switch to RFC 8693 token exchange so the downstream
   service sees the *end user's* identity rather than the API's. Compare what each option means for
   step 4's ownership check in the downstream service.
4. **Break your own fix with a redirect.** Stand up a WireMock that answers `302 Location:
   http://169.254.169.254/…` on an allowlisted host, and confirm your step 6 code refuses it. If it
   does not, you have learned the actual lesson of Capital One.
5. **Turn the audit log into something usable.** Emit structured JSON events to a separate appender,
   include the decision (ALLOW/DENY) and a request id, and write a query that would surface an
   enumeration attack: one subject, many distinct object ids, mostly denials, inside a minute.

## Resources

- **[OWASP API Security Top 10 — 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)** —
  short, concrete, and the single most useful security document for someone who writes APIs. API1
  (BOLA), API3 (BOPLA) and API6 (unrestricted access to sensitive business flows) are steps 4 and 7 of
  this lesson.
- **[OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/)** — a
  requirements catalogue rather than a top-10 list; use it when someone asks "how secure is it?" and
  you need something better than an opinion. Chapters 4 (access control), 7 (errors and logging) and
  13 (APIs) map directly onto this project.
- **Laurențiu Spilcă — *Spring Security in Action*, 2nd ed. (Manning)** — the best single book on the
  framework. Chapters on OAuth2 resource servers, method security and testing are the ones for this
  lesson.
- **[Spring Security 7 reference documentation](https://docs.spring.io/spring-security/reference/)** —
  in particular the OAuth2 Resource Server, CORS, CSRF and Method Security sections, plus the
  [Boot 3→4 / Security 6→7 migration guide](https://docs.spring.io/spring-security/reference/migration/index.html)
  for the removed `.and()` DSL and `PathPatternRequestMatcher`.
- **Aaron Parecki — [*OAuth 2.0 Simplified*](https://www.oauth.com) and
  [oauth.net](https://oauth.net/2/)** — the clearest explanation of the grants, and of why the password
  grant is deprecated even though this project's tests use it. See also
  [OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700) (RFC 9700).
- **[Keycloak documentation](https://www.keycloak.org/documentation)** — the
  [server administration guide](https://www.keycloak.org/docs/latest/server_admin/) for audience
  mappers, realm roles, token lifespans and the direct access grant; the
  [securing applications guide](https://www.keycloak.org/docs/latest/securing_apps/) for what the
  client side should be doing.
- **[PortSwigger Web Security Academy](https://portswigger.net/web-security)** — free, hands-on labs.
  The access-control and SSRF sections are the attacker's-eye view of steps 4 and 6.
- **Adam Shostack — *Threat Modeling: Designing for Security*** — the four-question framing used in
  `docs/threat-model.md`.

---

**Build notes (verified August 2026).** Spring Boot 4.1.1 / Spring Security 7.1.1 / Java 25,
Keycloak 26.4, Postgres 16, Testcontainers 2.0.5. Pristine `mvn -B clean test`: **39 tests, 4 green,
35 `@Disabled`, ~17 s** including container startup (both images cached locally).

Four things cost real time while building this and are worth knowing:

- **Keycloak's directory import requires `<realm-name>-realm.json` filenames.** A file named
  `expense-realm.json` containing realm `expense-realm` makes the container exit with code 1 and
  `ERROR: File name / realm name mismatch` — which Testcontainers reports only as "Wait strategy
  failed. Container exited with code 1". Hence realms `expenses` and `elsewhere` in
  `expenses-realm.json` / `elsewhere-realm.json`.
- **Testcontainers 2.x: `PostgreSQLContainer` is no longer generic** (`new PostgreSQLContainer("…")`,
  no diamond), while `GenericContainer<>` still is. `org.testcontainers.containers.GenericContainer`
  and `…containers.wait.strategy.Wait` did not move in 2.x; the per-technology modules did
  (`testcontainers-postgresql` → `org.testcontainers.postgresql`).
- **A Keycloak user without `firstName`/`lastName` cannot use the direct access grant.** The
  declarative user profile triggers a `VERIFY_PROFILE` required action and the token endpoint answers
  `400 {"error":"invalid_grant","error_description":"Account is not fully set up"}`.
- **`JwtTimestampValidator` allows 60 seconds of clock skew by default** — the reason step 2's expired
  token test needs an explicit skew setting rather than just a short-lived token.

Java note: this project needs JDK 25 or newer on `JAVA_HOME` (`maven.compiler.release=25`); it was
verified on Temurin 26.
