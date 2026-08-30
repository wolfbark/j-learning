# 12 — Testing Strategy: Two Services, No E2E Allowed

> After this lesson you can defend a deploy decision for a two-service system with
> zero end-to-end tests: a container-backed integration suite for each service's own
> risk, a consumer-driven contract for the risk *between* them, architecture fitness
> functions for the risk that accumulates silently, and a mutation gate that tells you
> whether any of those tests would actually notice a bug.

## Why this matters (2026)

Project 11 left you with two services and a working system. This project asks the
question that follows immediately: **how do you know it still works before you press
deploy?**

The reflex answer — "spin both services up and run a suite of user-journey tests" —
is the one the industry has been walking away from, and the reasons are arithmetic
rather than fashion:

- **The dogmatic pyramid is retired as a universal rule.** The 2025–26 consensus
  (source: [docs/research/methodologies.md](../docs/research/methodologies.md),
  section 4) is **shape follows architecture**: survey data circulating this year has
  ~48% of microservice teams describing their strategy as honeycomb-like, while
  trophy-style suits full-stack and serverless teams, and risk-based allocation is the
  other large bloc. Nobody serious argues for one shape for all systems any more.
- **Testcontainers removed integration testing's cost penalty.** Real Postgres in a
  unit-test-shaped test used to mean a shared CI database and a 40-minute suite. Now
  it means one static container and 30 seconds. That single change is what moved the
  centre of mass of the pyramid upward — and Testcontainers **2.0** (GA late 2025,
  2.0.5 by April 2026) dropped JUnit 4, renamed every module artifact, and shipped
  first-class JUnit 6 support.
- **AI-generated code moved the bottleneck from writing tests to trusting them.** When
  a plausible-looking test suite can be produced in seconds, "we have tests" and
  "we have coverage" stop being evidence. What is left as evidence is whether the
  suite *fails when the code is wrong* — which is what mutation testing measures and
  what contract verification enforces at a boundary.
- **E2E tests fail for reasons that have nothing to do with your change.** They are
  slow, they are flaky, nobody owns them, and the feedback arrives after the
  interesting moment has passed. Worse, an E2E suite over N services needs *both*
  deploy pipelines to agree on a moment in time — which is precisely the independent
  deployability you split the services to get.

So the challenge framing of this project is a real engineering position, not an
exercise gimmick: **reach deploy-confidence with zero end-to-end tests.** By the end
you will have deliberately broken the provider's API and watched a 30-second test in
the *provider's own build* catch it, before deploy, with a message naming the exact
field.

What this lesson is not: an argument that E2E tests are always wrong. A handful of
post-deploy smoke tests against real infrastructure ("can I place one order in
production?") earns its place. The claim is narrower and stronger — *an E2E suite is
not how you get confidence in a change*, and treating it as such is how teams end up
with a six-hour pipeline they mute.

## Core concepts

### Three shapes, and why the shape is not the point

| Shape | Where the weight sits | Fits | Attributed to |
|---|---|---|---|
| **Pyramid** | many unit, fewer integration, few E2E | a monolith with rich domain logic | Mike Cohn; popularized by Ham Vocke |
| **Honeycomb** | integration-dominant; thin unit layer; almost no E2E | microservices, where complexity lives *between* services | Spotify Engineering |
| **Trophy** | static analysis base, integration-heavy, some unit, few E2E | full-stack / serverless, where most code is glue | Kent C. Dodds |

The useful reading of these three is not "pick your favourite". It is that each one
matches a *place where bugs actually come from*:

- Rich, branchy domain logic in one process → unit tests, and the pyramid is right.
- Glue code across boundaries you don't own → integration tests, and the honeycomb is
  right.
- Types and lint catching most of your class of mistake → the trophy's static base.

This repo's two services contain **both** kinds of code, which is why this project
uses both shapes at once and is explicit about which is which:

- `order-service` has `OrderPricer` — pure, branchy, boundary-rich pricing arithmetic.
  Unit tests, run in microseconds, mutation-gated. Pyramid.
- `payment-service` has validation, an idempotency key and a unique index. Its risk is
  in the interaction between HTTP, JDBC, transactions and constraints — none of which
  a mocked repository can be wrong about. Integration tests over a real Postgres.
  Honeycomb.

**Shape follows architecture.** If someone hands you a target ratio without asking
what the code looks like, they are selling a poster.

### The gap E2E tests were covering, and what replaces it

Between two services there are exactly three things you can get wrong:

1. **The wire format** — field names, types, status codes, headers.
2. **The semantics** — what a 402 *means*, whether a decline is an error, what
   "idempotent" covers.
3. **The environment** — DNS, TLS, quotas, config.

Contract testing addresses (1) and pins the consumer's understanding of (2). Nothing
except production addresses (3) — which is an argument for good deploy practice
(canaries, health checks, feature flags), not for an E2E suite that runs against a
staging environment nobody else uses.

### Consumer-driven contract testing

A contract test splits one integration test into two independently-runnable halves:

```
consumer test                              provider test
──────────────                             ─────────────
real client code                           real application + real DB
       │                                          ▲
       ▼                                          │
  Pact mock server ──► pact JSON file ────────────┘
   (records what the                   (replays it, with provider
    consumer expects)                   states seeding the DB)
```

Three properties make this worth the ceremony:

- **Neither half needs the other running.** The pact file is the only shared artifact.
- **The consumer states only what it needs.** The provider may add fields, endpoints,
  and statuses freely — nothing in the contract mentions them, so nothing breaks. That
  asymmetry is what makes independent deploys safe rather than merely permitted.
- **Provider states are a seam, not a coupling.** The consumer says *"given an
  authorized payment exists"*; the provider decides what SQL makes that true. The two
  codebases agree only on a string.

**Provider-driven** is the other half of the market: Spring Cloud Contract, where the
provider publishes contracts (Groovy/YAML) and generates both its own verification
tests and consumer stub jars.

| | Pact | Spring Cloud Contract |
|---|---|---|
| Contract owner | consumer | provider |
| Contract format | generated JSON (a recording) | hand-written Groovy/YAML (a spec) |
| Polyglot | yes — that is the point | JVM-centric |
| Distribution | Pact Broker (HTTP) or files | Maven stub jars, existing infra |
| Killer feature | `can-i-deploy` against real deployed versions | consumer stubs for free, from your artifact repo |
| Reach for it when | many consumers, several languages, consumers know what they need | one JVM shop, provider owns the API design, Maven is already the backbone |

This lesson uses **Pact with file-based exchange and no broker**, because the broker is
an operational component and the pedagogy is in the round trip. What the broker adds is
the part that matters in production and is a stretch goal below: `can-i-deploy` answers
*"is there a verified contract between the version I am about to deploy and the versions
currently running?"* — a question a file on disk cannot answer.

### Fitness functions

An ArchUnit rule is an executable architectural decision. Not documentation about a
decision — the decision itself, failing a build. They matter here because the
*testability* of code is architectural: `OrderPricer` can be mutation-tested in
microseconds only as long as nothing in `pricing` is allowed to know about HTTP. One
"quick fix" that injects the payment client into the pricer and that property is gone
forever. A three-line rule prevents it.

Fitness functions rot in one specific way: someone relaxes the rule instead of fixing
the code. Step 6 puts you in exactly that situation.

### Mutation testing: trust in tests, not coverage theatre

Line coverage measures which lines were *executed*. It cannot distinguish a test that
asserts an outcome from a test that calls the method and asserts nothing. Both give
100%.

PIT changes your bytecode — flips `>=` to `>`, replaces a return value, removes a
call — and re-runs the tests that cover that line. If they still pass, the **mutant
survived**: there is a version of your code your suite considers acceptable. The
mutation score is the percentage killed, and it is the closest thing to a measurement
of *how much a green build is worth*.

Two honest caveats, both of which step 7 makes you confront:

- **Equivalent mutants exist.** Some changes are genuinely unobservable, and no
  reasonable test can kill them. 100% is not the target; a *justified* number is.
- **It is slow.** PIT re-runs tests once per mutant. That is why the gate lives in a
  Maven profile here and typically in a nightly job or a changed-files-only run in
  real projects — never in the inner loop.

## The project

Two standalone Maven projects (the same structure exception as project 11 — a
lesson about two services needs two services):

```
12-testing-strategy/
├── order-service/     (consumer)  port 8080, no database, no Docker needed
└── payment-service/   (provider)  port 8081, Postgres via Testcontainers
```

**Both services are given complete and working.** This lesson is not about building
them. Every checkpoint is about tests — the given production code changes exactly
twice: once in step 5 (a change you make, watch fail, and revert) and once in step 6
(an architecture violation you fix).

**order-service** places orders: prices them with `OrderPricer` (tiered discounts, a
loyalty bonus with a cap, a coupon that loses to a better tier, currency-aware
rounding, free shipping above a threshold), then authorizes payment through a
declarative `@HttpExchange` client. `OrderPricer` is the mutation-testing target and
it is exactly as branchy as that description sounds.

**payment-service** authorizes payments against Postgres: manual validation
(currency support, minor units — money is not always two decimal places), an
`Idempotency-Key` header backed by a unique index, deterministic decline rules, and a
single error body shape for every non-2xx.

Run the tests:

```bash
mvn -f order-service/pom.xml   test     # ~8s, no Docker
mvn -f payment-service/pom.xml test     # ~45s, needs Docker
```

Both are green on checkout. The given tests:

| Service | Test | Kind | Notes |
|---|---|---|---|
| order | `OrderPricerTest` (5) | unit | **deliberately incomplete** — passes, misses every boundary |
| order | `PlaceOrderTest` (3) | unit, hand-written stub | no mocking framework |
| payment | `PaymentDecisionTest` (7) | unit, in-memory fake repo | decision + validation + idempotency logic |
| payment | `PaymentPersistenceIntegrationTest` (3) | integration, Testcontainers | real HTTP, real serializer, real Postgres |

Checkpoint tests are pre-written and annotated
`@Disabled("Checkpoint N — enable when you start step N")`. Remove the annotation when
you reach the step. To run one *without* editing the file (useful while reading):

```bash
mvn test -Dtest=Checkpoint3PaymentContractTest -Djunit.jupiter.conditions.deactivate='*'
```

## Guided steps

### Step 1 — Audit the given tests: what do you actually know?

**Goal:** before writing a line, decide what confidence the existing suite provides.
This is the whole strategy discussion in concrete form.

Read the four given test classes. Then fill this in — one row at a time, honestly. For
each question, name the test that would fail if the answer were wrong.

| Question about the system | Which given test fails if this breaks? | Confidence |
|---|---|---|
| Does the 5% tier apply at *exactly* 100.00? | | |
| Is the loyalty bonus capped at 15%? | | |
| Is free shipping compared against the discounted subtotal? | | |
| Does the `payments` table actually have a unique index on the idempotency key? | | |
| Do two concurrent identical authorizations create one row or two? | | |
| Does `POST /payments` answer 402 (not 500) for a declined card? | | |
| Does the declined body carry a `reason` field named `reason`? | | |
| Would order-service notice if `paymentId` were renamed to `id`? | | |
| Is JPY stored and returned without decimals? | | |
| Can `OrderPricer` still be unit-tested after someone injects an HTTP client into it? | | |

**Done when:** you have a filled table and can say, in one sentence each, which
*shape* each service's risk calls for and why they differ.

<details><summary>Answers — read after you have tried</summary>

Rows 1–3: **nothing**. `OrderPricerTest` tests one comfortable example per branch;
every boundary is unverified. Step 7 quantifies this: PIT kills 17 of 26 mutants —
**65%**.

Rows 4–5: **nothing**. `PaymentDecisionTest` uses an in-memory fake whose
`insertIfAbsent` is a `ConcurrentHashMap.putIfAbsent`. It would pass identically if
`schema.sql` had no unique index at all. This is the honeycomb argument in one
sentence: the fake cannot be wrong in the way production is wrong. Step 2.

Rows 6–7: partially. `PaymentDecisionTest` asserts the *domain* decision;
`PaymentPersistenceIntegrationTest` asserts a 201 body over real HTTP. Neither asserts
the 402 status or the declined body shape — the two things the consumer parses. Step 3.

Row 8: **nothing, on either side.** The provider's tests assert its own fields; the
consumer's stub returns whatever the consumer's own record declares. Both suites stay
green while the system breaks. This is the specific hole contract testing exists to
fill, and step 5 is the demonstration.

Row 9: yes — `jpyIsStoredWithoutDecimals` and `yenIsPricedWithoutDecimals`. Note that
this required *someone to think of it*; a mutation report would not have suggested it.
Mutation testing improves the tests you have; it does not invent requirements.

Row 10: **nothing.** No rule prevents it. Step 6.

Shapes: `order-service`'s risk is arithmetic in one process → pyramid, mutation-gated.
`payment-service`'s risk is the interaction of HTTP, JDBC, constraints and
serialization → honeycomb, container-backed. Same repo, same week, two shapes,
because the code is different.

</details>

### Step 2 — An integration suite that only a real database can pass

**Goal:** cover the idempotency contract against real Postgres, including the race the
in-memory fake cannot express.

Enable `Checkpoint2IdempotencyReplayTest` in payment-service and run it. Read it before
you run it, and for each test ask: *could the in-memory fake have failed this?*

```bash
mvn -f payment-service/pom.xml test -Dtest=Checkpoint2IdempotencyReplayTest
```

Then extend it. Two additions worth making:

1. A replay of the *same* key with a different **currency** (not amount) — is that a
   conflict? Should it be?
2. An amount with too many decimals for its currency, over real HTTP: 400, and no row
   written. (`PaymentDecisionTest` asserts the exception; assert the *observable*
   outcome.)

<details><summary>Hint — why the concurrency test is the important one</summary>

`twoConcurrentRequestsWithTheSameKeyCreateExactlyOneRow` is the only test in either
service that would fail if you deleted `payments_idempotency_key_uq` from
`schema.sql`. Try it: comment the index out and watch the unit tests stay green while
this one produces two payment ids for one order.

That is the honeycomb argument, executable. Note also what makes it cheap — one static
container for the whole run (`AbstractIntegrationTest`), started once, reaped by Ryuk
on JVM exit. Ten years ago this test needed a shared CI database and a cleanup script.

</details>

**Done when:** the suite is green, and commenting out the unique index in `schema.sql`
makes exactly one test fail (then put it back).

### Step 3 — Write the contract, from the consumer's side

**Goal:** state what order-service needs from payment-service, as an executable
artifact.

Enable `Checkpoint3PaymentContractTest` in order-service and run it. Four interactions:
an approved authorization (201), a declined one (402 with the error shape), a lookup
(200), an unknown id (404).

```bash
mvn -f order-service/pom.xml test -Dtest=Checkpoint3PaymentContractTest
ls order-service/target/pacts/
```

Read the test as a specification. Notice three deliberate choices:

- The client under test is the **real** `PaymentClient` proxy over a real `RestClient`
  — same annotations, same serializer, same headers as production. Only the socket on
  the far end is Pact's mock server. A contract recorded through a hand-written HTTP
  call would be a contract about the test, not about the consumer.
- Most fields use **type matchers** (`stringType`, `decimalType`) — the consumer cares
  that `amount` is a number, not that it is 42.50. Two fields use exact values
  (`stringValue("status", "AUTHORIZED")`, `stringValue("reason", "CARD_DECLINED")`)
  because the consumer branches on those literals. Over-specifying a contract makes
  the provider's build fail for changes nobody depends on; under-specifying makes it
  pass for changes that break you.
- The consumer's `PaymentResponse` record does **not** mirror the provider's. It
  declares six fields; the contract mentions six fields; the provider may have sixty.

<details><summary>Hint — the header gotcha, and why the test says so out loud</summary>

Pact's DSL defaults a JSON body's `Content-Type` to `application/json; charset=UTF-8`.
Spring's `RestClient` sends a bare `application/json`. Left alone, the mock server
rejects your own consumer's request with a `PartialRequestMatch`, which is a confusing
way to learn that headers are part of a contract too.

The fix is in the test: request and response headers are declared explicitly, **before**
`.body(...)`, so the DSL default never applies.

</details>

**Done when:** `order-service/target/pacts/order-service-payment-service.json` exists
and contains four interactions, each with a `providerStates` entry.

### Step 4 — Verify the contract against the real provider

**Goal:** replay the consumer's expectations against the running provider, with its
real database, in the *provider's* build.

The pact file crosses the boundary as a file — no broker
(`@PactFolder("../order-service/target/pacts")`). Generate it first, then verify:

```bash
mvn -f order-service/pom.xml   test -Dtest=Checkpoint3PaymentContractTest
mvn -f payment-service/pom.xml test -Dtest=Checkpoint4ContractVerificationTest
```

Expected output — one block per interaction:

```
Verifying a pact between order-service and payment-service
    returns a response which
      has a matching body (OK)
```

The `@State` methods are the interesting part. `"an authorized payment exists"` inserts
a row with a fixed id; `"the acquirer declines the card"` does nothing at all, because
the provider's decline rule is deterministic by design. Randomness in a provider is a
contract-testing tax — an acquirer simulator that declines 5% of cards would force you
to build a seam anyway.

<details><summary>Hint — if verification cannot find the pact</summary>

`@PactFolder` is relative to the Maven working directory, i.e. the `payment-service`
directory. If you ran the consumer test with a different `pact.rootDir`, or ran
`mvn clean` in order-service afterwards, the folder is empty and Pact fails with
"No pacts found". Regenerate; the pact file is a build output, not a source file.

</details>

**Done when:** all four interactions verify green, and you can explain why this test
needed a Postgres container but did **not** need order-service running.

### Step 5 — Break the provider on purpose

**Goal:** experience the moment the whole lesson exists for.

You are a payment-service developer. `paymentId` is an ugly name — the resource is a
payment, so `id` is cleaner. In
`payment-service/src/main/java/dev/vlearning/payments/web/PaymentResponse.java`, rename
the record component `paymentId` to `id`.

Do **not** touch order-service. You do not have access to it; in a real organization
you might not know it exists.

Now run the provider's own test suite:

```bash
mvn -f payment-service/pom.xml test -Dtest=Checkpoint4ContractVerificationTest \
    -Djunit.jupiter.conditions.deactivate='*'
```

```
1) Verifying a pact between order-service and payment-service - a lookup of that payment has a matching body
    1.1) body: $ Actual map is missing the following keys: paymentId
```

Read that carefully. The failure is **in your build**, **before your deploy**, naming
**the exact field**, in 30 seconds, and it did not require order-service to be running
or even to exist as a checkout.

Now revert the rename and write the comparison down — in a scratch file, a commit
message, wherever. Answer these:

- With E2E tests instead: who would have caught this, in which pipeline, how long
  after your commit, and what would the failure message have said? (Realistically:
  a journey test failing on a null pointer three services away, hours later, in a
  suite that was already red for two unrelated reasons.)
- What would it have cost to catch it in production instead?
- What does the provider now know that it could not have known before? (That someone
  depends on that field name — a fact that lived only in another team's codebase.)
- What does this test *not* protect you from? (Renaming a field the contract never
  mentioned — correctly, that is allowed. And any semantic change that keeps the shape:
  returning `AUTHORIZED` for a payment you did not actually authorize.)

**Done when:** verification is green again, and your written note answers all four.

### Step 6 — Fitness functions, and the temptation to relax one

**Goal:** make the structural properties the rest of this lesson depends on
executable.

Enable `Checkpoint6ArchitectureFitnessTest` in **both** services and run them.

order-service passes: five rules, including the one that keeps `pricing` free of
`api`, `order` and `payments` — the property that makes step 7's mutation run take
seconds instead of minutes.

payment-service **fails one rule**: `webMustNotReachIntoPersistenceDirectly`.
`PaymentController` injects `JdbcPaymentRepository` for its `GET` — someone needed a
quick read and skipped the service.

Fix the **code**, not the rule. The rule is correct.

<details><summary>Hint</summary>

`PaymentService.get(String id)` already exists and already throws
`PaymentNotFoundException`. Replace the controller's `JdbcPaymentRepository` dependency
with the service call and delete the import. Both the integration suite and the contract
verification must still be green afterwards — that is what tells you the refactor was
behaviour-preserving.

Note *which* rules are worth writing. "No `web` → `persistence`" protects a real
property: the domain owns storage decisions. A rule like "every class must have a
Javadoc comment" protects nothing and trains the team to ignore failures.

</details>

**Done when:** all rules in both services are green, no rule was weakened, and
`mvn test` in payment-service is still fully green.

### Step 7 — Make the tests worth trusting

**Goal:** raise `OrderPricer`'s mutation score past the gate by testing behaviour the
given tests never touched.

Run the gate. It lives in a Maven profile so a pristine `mvn test` stays fast and
green:

```bash
mvn -f order-service/pom.xml -Pmutation verify
open order-service/target/pit-reports/index.html
```

Baseline on the given tests:

```
>> Generated 26 mutations Killed 17 (65%)
[ERROR] Mutation score of 65 is below threshold of 75
```

Sixty-five percent — from a suite that is green, readable, and covers every branch at
least once. That number is the honest answer to step 1's first three rows.

Open the HTML report. Surviving mutants are highlighted per line; hover for what was
changed. Then enable `Checkpoint7PricingMutationTest` and work the TODO list in it.
Two boundary tests are given as a demonstration of the shape; the rest are yours.

<details><summary>Hint — how to read a survivor</summary>

The most common survivor in this code is `changed conditional boundary` — PIT flipped a
`>=` to `>` and every test still passed, because no test uses a subtotal of exactly
100.00, 500.00 or 1000.00. Each of those is one two-line test.

The interesting survivor is the loyalty cap: `Math.min(percent + 2, 15)` mutated to
drop the `min`. Killing it requires an order in the top tier *plus* loyalty — 15% + 2%
capped at 15% — a combination the given tests never make.

Two rules while you work:

- **Do not chase 100%.** For each survivor ask whether it represents behaviour anyone
  should rely on. If the answer is no, it is an equivalent mutant and it stays alive;
  write down which ones and why. A justified 82% beats a gamed 100%.
- **Never write a test whose only purpose is to kill a mutant.** Write the test that
  documents the *behaviour* — "the loyalty bonus never pushes a discount past 15%" —
  and the mutant dies as a side effect. If you cannot phrase the behaviour, you have
  found a design question, not a test gap. (`COUPON_WELCOME10` being silently dropped
  when a better tier exists is exactly such a question.)

</details>

**Done when:** `mvn -Pmutation verify` passes the 75% gate, and you can name at least
one mutant you deliberately left alive and say why.

## Self-check

1. Why does a microservice system's test suite tend toward the honeycomb rather than
   the pyramid — and what property of *code*, not of architecture, actually decides it?
2. `PaymentDecisionTest` and `Checkpoint2IdempotencyReplayTest` both test idempotency.
   Name a production bug only the second one can catch, and one the first catches
   faster.
3. In consumer-driven contract testing, who owns the contract, who runs the
   verification, and why is that split the thing that makes independent deploys safe?
4. Your provider adds a new optional field to a response. Which builds break? What if
   it adds a required field to a *request*?
5. What can a contract test not tell you that an E2E test could? Is that gap worth an
   E2E suite?
6. What does `can-i-deploy` answer that a pact file on disk cannot?
7. A colleague proposes deleting `webMustNotReachIntoPersistenceDirectly` because "it
   fails on a controller that works fine". What is your argument, in one sentence?
8. Line coverage is 83% and the mutation score is 65%. What do those two numbers
   together tell you that neither tells you alone?
9. When is a surviving mutant *not* a problem, and how do you avoid using that as an
   excuse?

## Stretch goals

1. **Add a Pact Broker and `can-i-deploy`.** Run the broker in Docker, publish the pact
   with a consumer version, publish verification results from the provider, then wire
   `can-i-deploy` into a pretend pipeline. Two things become possible that files cannot
   do: the provider discovers consumers it did not know about, and a deploy can be
   blocked on *deployed* versions rather than on the tip of a branch.
2. **Mirror step 3 in Spring Cloud Contract.** Write the same four interactions as
   provider-owned Groovy contracts, generate the provider tests and the consumer stub
   jar, and consume the stub from order-service. Then write down which flow you would
   choose for a five-team JVM shop and which for a polyglot platform, and why the
   answer is not the same.
3. **Mutation-gate the provider too.** Point PIT at
   `dev.vlearning.payments.domain.*` and find out what the seven given unit tests are
   worth. Then try `<mutators><param>STRONGER</param></mutators>` and watch the score
   drop — mutator selection is part of the gate's definition, and a threshold is
   meaningless without it.
4. **A message contract.** Have payment-service publish a `PaymentAuthorized` event and
   write a Pact **message** contract for it (no HTTP involved). Async contracts are
   where contract testing earns the most, because there is no request/response to
   observe in staging at all.
5. **Delete a test.** Find one given test that provides no confidence any other test
   does not already provide, delete it, and defend the deletion. Suites grow by
   accretion; someone has to prune.

## Resources

- **Ham Vocke — "The Practical Test Pyramid"** (martinfowler.com, 2018) — the baseline
  you should be able to state fairly before critiquing it. Also Martin Fowler's
  **"TestPyramid"**, **"IntegrationTest"** and **"ContractTest"** entries.
- **Spotify Engineering — "Testing of Microservices"** (the honeycomb article) and
  **Kent C. Dodds — "The Testing Trophy and Testing Classifications"** — the two
  counter-models, both worth reading for *what architecture they assume*.
- **Sam Newman — *Building Microservices*, 2nd ed. (2021)**, the testing chapters —
  the strategy-level framing of contract testing versus E2E, including the
  "who owns the end-to-end suite" problem that kills most of them.
- **Testcontainers guides** (testcontainers.com/guides) and the **Testcontainers 2.0
  release notes** — the singleton-container pattern and the 1.x→2.x artifact/package
  renames used here.
- **Pact documentation** (docs.pact.io) — the Contract Testing University track and the
  **CDC workshop** (pact-foundation/pact-workshop-jvm-spring); the provider-states and
  `can-i-deploy` pages are the two to read closely.
- **Spring Cloud Contract reference documentation** — for the comparison in stretch
  goal 2; Baeldung's Pact and Spring Cloud Contract tutorials are the fastest practical
  path into both.
- **PIT documentation** (pitest.org) — especially "Mutators" and the notes on
  equivalent mutants and incremental analysis.
- **ArchUnit user guide** (archunit.org) — and Neal Ford, Rebecca Parsons & Patrick
  Kua, *Building Evolutionary Architectures*, for where the term "fitness function"
  comes from.

---

### Footnotes — deviations and verified details (August 2026, this machine)

1. **Pact JVM 4.6.21** (consumer `au.com.dius.pact.consumer:junit5`, provider
   `au.com.dius.pact.provider:junit5`) runs correctly under **Spring Boot 4.1.1 /
   JUnit 6 / JDK 25**, with the conventions' precautions applied: `V4Pact` method
   signatures plus `@PactTestFor(pactVersion = PactSpecVersion.V4)`, and `public`
   `@Pact` and `@Test` methods. Verified end to end here — consumer test generates the
   pact, provider test verifies all four interactions green, and a field rename fails
   verification with `Actual map is missing the following keys: paymentId`. No
   Spring Cloud Contract fallback was needed; the two-tool comparison stays as lesson
   text.
2. **The `Content-Type` mismatch is real and non-obvious.** Pact's DSL defaults a JSON
   body to `application/json; charset=UTF-8`; Spring's `RestClient` sends
   `application/json`. Both request and response headers are therefore declared
   explicitly *before* `.body(...)` in the consumer test.
3. **Plain `com.tngtech.archunit:archunit:1.5.0` instead of `archunit-junit5`.** The
   `archunit-junit5` module registers its own JUnit Platform engine, whose handling of
   `@Disabled` on an `@AnalyzeClasses` class is not something this scaffold should
   depend on. Writing the rules as ordinary `@Test` methods that call `.check(...)`
   keeps `@Disabled` behaving exactly as it does everywhere else in the repo, at the
   cost of a little boilerplate.
4. **PIT 1.19.6 + `pitest-junit5-plugin` 1.2.3** works on JDK 25 class files, as the
   conventions state (1.19.1 does not). The gate is `mutationThreshold` only — no
   `coverageThreshold` — because the lesson's claim is that the mutation score is the
   number worth gating on.
5. **`-Djunit.jupiter.conditions.deactivate='*'`** runs a checkpoint test without
   editing its `@Disabled` annotation. Handy while reading, and how the pristine-state
   verification for this lesson was done.
6. Neither service ships a `docker-compose.yml`: every container this lesson needs is
   Testcontainers-managed, and running the two services together is deliberately not
   part of any checkpoint.
