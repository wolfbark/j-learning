# 04 — Hexagonal Architecture: Same App, Three Shapes

> After this lesson you can take a smeared controller→service→repository Spring service, reshape it
> into ports & adapters, prove the payoff with millisecond use-case tests, lock the shape in with
> ArchUnit — and argue convincingly when *not* to do any of that.

## Why this matters (2026)

Hexagonal, onion, and clean architecture are now standard industry vocabulary: Spring Modulith
explicitly "formalizes hexagonal architecture and DDD into Spring Boot", and jMolecules ships
annotations for all three styles with automatic verification. At the same time, the loudest
2025–2026 content about these patterns is *anti-ceremony*: criticism of 11-files-per-feature Clean
Architecture implementations, cognitive-load complaints, "your CRUD app doesn't need this" — and
Alistair Cockburn himself pushing back on the ritual that has been piled onto his deliberately
minimal pattern (see also "Ports and Adapters, and the Mess in the Middle", Aug 2026).

The mature 2026 position, and the one this lesson trains
(see [`../docs/research/architecture-styles.md`](../docs/research/architecture-styles.md), section 3):

- apply ports & adapters where the domain logic is genuinely worth isolating;
- use plain transaction-script/CRUD where it isn't — in the same codebase, without shame;
- enforce whichever you chose with ArchUnit tests, not folder dogma and code review vibes.

## Core concepts

**Dependency inversion at architecture scale.** All three styles are one idea: the code that
embodies business decisions must not depend — at the *source* level — on the code that talks to
databases, browsers, and other systems. Data still flows outward at runtime; the `import`
statements point inward. That inversion is achieved with interfaces owned by the inside.

**Hexagonal / Ports & Adapters (Cockburn, 2005).** The application core exposes **ports** —
interfaces named for a purposeful conversation:

- **Driving (primary) ports** — things the world asks the app to do. Here: `CreateQuoteUseCase`,
  `GetQuoteQuery`. Called by **driving adapters**: a REST controller, a scheduler, a CLI, a test.
- **Driven (secondary) ports** — things the app needs from the world. Here: `QuoteRepository`,
  `RateProviderPort`. Implemented by **driven adapters**: a JPA gateway, an HTTP client, an
  in-memory fake.

```java
// the application owns and shapes this interface; JPA is nowhere in sight
public interface QuoteRepository {
    Quote save(Quote quote);
    Optional<Quote> findById(UUID id);
}
```

Two things people forget: the hexagon shape means nothing (it's just room to draw many ports), and
the pattern deliberately prescribes **no internal layering** of the core. Everything beyond
"boundary = ports, outside = adapters" is somebody else's addition.

**Onion (Palermo, 2008)** adds concentric rings inside — domain model → domain services →
application services → infrastructure — with dependencies pointing strictly inward.
**Clean (Martin, 2012)** synthesizes both into entities / use cases / interface adapters /
frameworks plus the Dependency Rule; it is the most prescriptive and the most commonly
over-implemented. Treat all three as one family — DIP applied at the architecture level — with
hexagonal as the teachable core. A vocabulary map is in step 7.

## The project

An insurance-quote service. Pricing works like this:

- the monthly **base rate** per product (AUTO, HOME, LIFE) comes from an *external rate provider*
  over HTTP (played by a stub controller inside this same app so everything runs standalone);
- surcharges are added to the base rate: **under 25** +30%, **70 or older** +20%, **SMOKER** +20%,
  **HAZARDOUS_OCCUPATION** +15%, **PREVIOUS_CLAIMS** +40% — additively, then rounded HALF_UP to
  cents;
- applicants under 18 are rejected;
- quotes are persisted (H2 in-memory — the database is deliberately irrelevant here) and can be
  fetched by id. A trivial `/products` CRUD endpoint also exists; it becomes important in step 7.

**What's given** — Round 1 is complete and working, in classic package-by-layer shape:

```
dev.vlearning.quotes
├── web/           controllers + error handler          (you will dismantle this)
├── service/       QuoteService — the mess              (you will dismantle this)
├── persistence/   JPA entities double as domain objects (you will dismantle this)
├── ratestub/      plays the REMOTE rate provider — treat as another team's server, never touch
├── domain/        Round 2 target skeletons — your step 2
└── application/   ports + application service skeletons — your step 3
```

The `domain/` and `application/` skeletons exist so the checkpoint tests compile from day one.
The signatures are part of the lesson — before implementing anything, read them and notice what
they do *not* mention. `QuoteApiIntegrationTest` is **enabled and green**: it pins the API's
observable behavior (including its warts — the JPA entity leaked into the JSON contract, and now
that shape is law) and must stay green through every step.

**Run it:**

```bash
mvn spring-boot:run

curl -s -X POST localhost:8080/quotes -H 'Content-Type: application/json' \
  -d '{"productCode":"AUTO","age":22,"riskFactors":["PREVIOUS_CLAIMS"]}'
curl -s localhost:8080/products
mvn test        # 11 integration tests green, checkpoint tests skipped
```

## Guided steps

Checkpoint tests live in `src/test/java`, annotated
`@Disabled("Checkpoint N — enable when you start step N")`. Remove the annotation, make it pass.

### Step 1 — Map the mess

**Goal:** know your enemy. Read `QuoteService.createQuote` top to bottom and write down every
reason you could *not* unit-test the pricing rules tonight — "unit" meaning: no Spring context, no
network, no database, milliseconds.

<details><summary>Hint — the list you should roughly converge on</summary>

1. `RestClient` is built inline and called mid-method — every test of the age surcharge makes a
   live HTTP call.
2. The base URL comes from an `Environment` lookup — configuration machinery inside business code.
3. `QuoteEntity` is the input, the output, the database row, and the JSON response — you cannot
   construct "just a quote" without dragging JPA along.
4. The pricing rules are interleaved with plumbing (HTTP, mapping, persistence) — there is no
   seam to test them through.
5. `UUID.randomUUID()` and `Instant.now()` are called inline — nondeterministic output.
6. Risk factors are stringly typed (`"SMOKER"` — typo-friendly, `contains` on a `List<String>`).
7. `@Transactional` only works through a Spring proxy — `new QuoteService(...)` in a test behaves
   differently than in production. (Why that is, and the two other things it implies, is explained
   in `20-transactions` under "Spring's part of the story is a proxy".)
8. Error signaling is tuned for the web layer (exceptions chosen so the advice maps them) — the
   "service" already knows it lives behind HTTP.
</details>

**Done when:** you have at least six items and can say for each one *which* future step fixes it.

### Step 2 — Extract the domain

**Goal:** pure-Java `Money` / `RiskProfile` / `Quote` types plus a `QuoteCalculator` with zero
framework imports. Enable `QuoteCalculatorTest`, then move the pricing rules out of `QuoteService`
into the calculator and add the under-18 guard to `RiskProfile`. `Money` is given complete — it's
the exemplar of what "domain type" means here (including the HALF_UP-to-cents policy).

<details><summary>Hint</summary>

The calculator takes the base rate as a *parameter* — that is what keeps it pure. Fetching the
rate is someone else's job (step 3). Signature and expected numbers are pinned by the test;
`Money.times(BigDecimal.ONE.add(load))` does the rounding for you.
</details>

**Done when:** checkpoint 2 is green. Look at the test class imports (none of them say
`springframework`) and at the runtime — this is what the whole refactor is buying.

### Step 3 — Define ports, wire the application service

**Goal:** an application service that orchestrates *only* through ports. The port interfaces are
pre-defined so tests compile — your job before coding is to answer, from the signatures alone:
why does `QuoteRepository` speak `Quote` and not `QuoteEntity`? Why does it have two methods when
`JpaRepository` gives you twenty for free? Why does `RateProviderPort` not mention HTTP, URLs, or
status codes?

Enable `QuoteApplicationServiceTest` and implement `createQuote` / `quoteById`: build the
`RiskProfile`, fetch the base rate through `RateProviderPort`, price with `QuoteCalculator`,
persist through `QuoteRepository`, return the saved `Quote`.

<details><summary>Hint</summary>

Construct the `RiskProfile` *first* — that way underage applicants are rejected before you pay for
a rate lookup, same as Round 1. Note how the test hands in the driven ports: one is a lambda.
Interfaces this small make mocking frameworks optional.
</details>

**Done when:** checkpoint 3 is green — including the structural guardrail tests that verify the
service's constructor takes nothing but ports and domain.

### Step 4 — Build the adapters

**Goal:** Round 2 complete. Create the adapter packages and dismantle Round 1:

- `adapter/in/web` — `QuoteController` rewritten against `CreateQuoteUseCase` + `GetQuoteQuery`,
  plus the error-handling advice. It owns its request/response DTOs and the string↔enum parsing.
- `adapter/out/persistence` — `QuoteEntity` + `QuoteJpaRepository` move here, plus a
  `QuotePersistenceAdapter implements QuoteRepository` that maps entity↔domain both ways.
- `adapter/out/rates` — a `RestClient`-based `RateProviderPort` implementation; it translates the
  provider's 404 into the application's unknown-product failure.

Then wire it up and delete the old `web`, `service`, and `persistence` packages. There is no new
checkpoint test — the *existing* integration tests are the checkpoint. They will go red while you
work and must be green when you stop.

<details><summary>Hint — the sharp edges</summary>

- **The API contract is pinned, warts included.** Round 1 returned the entity as JSON, so your
  response DTO must reproduce that accidental shape: `id`, `productCode`, `age`, `riskFactors`
  (comma-joined string!), `monthlyPremium`, `currency`, `createdAt`. This is what "the entity
  leaked into the contract" costs — you keep paying after the entity is gone.
- **Wiring:** `QuoteApplicationService` has no Spring annotations. Either give it one (`@Service`
  — the pragmatic Hombergs move) or keep the hexagon annotation-free with one `@Configuration`
  class in `application` exposing a `@Bean` that news up the service with `new QuoteCalculator()`.
  Both pass step 6; know which trade you made.
- The rates adapter can take `@Value("${rate-provider.base-url}")` in its constructor and build
  its `RestClient` once — the integration test fixes the port up front, so eager resolution works.
- `UnknownProductException` no longer belongs in a `service` package that shouldn't exist; the
  application layer is its natural home (the adapter throws it, the web advice maps it).
- Quote-not-found needs no exception at all anymore: `quoteById` returns `Optional`, the
  controller maps empty to 404.
</details>

**Done when:** `mvn test` is green and `web/`, `service/`, `persistence/` are gone.

### Step 5 — The swap-the-adapter payoff

**Goal:** feel why you did all this. Enable `CreateQuoteWithFakesTest`: the full use case runs
against an in-memory repository and a fixed-rate provider — both driven adapters swapped for fakes
in a few lines, no Spring context, no Tomcat, no H2, no 50 ms stub latency.

**Done when:** checkpoint 5 is green — and you have compared the surefire timings of this class
against `QuoteApiIntegrationTest` (expect roughly three orders of magnitude). Round 1 could not
have expressed this test *at any speed*.

### Step 6 — Enforce it

**Goal:** the shape survives the next contributor. Enable `HexagonalArchitectureTest`:

1. the domain must not import Spring, Jakarta, or JDBC;
2. the application must not depend on adapters, Spring Web, Spring Data, or JPA;
3. nothing outside `adapter..` may reference an adapter class — port implementations stay
   interchangeable details;
4. the same intent expressed through ArchUnit's built-in `onionArchitecture()` rule, for
   comparison — note the friction of declaring our flat `domain` package as both the
   domain-model and the domain-service ring.

The rules are written as plain JUnit tests (importing classes with `ClassFileImporter`) rather
than ArchUnit's own `@AnalyzeClasses` engine, so the enable-the-checkpoint convention stays
uniform.

**Done when:** checkpoint 6 is green. Prove to yourself it isn't decorative: drop a
`@Service` on `QuoteCalculator`, watch rule 1 fail, revert.

### Step 7 — Judgment debrief: when is a hexagon the wrong shape?

No checkpoint test — this step is judgment, which is the actual skill.

**The `/products` endpoint.** It lists rows and inserts rows. Hexagonalizing it means: a
`ListProductsQuery` port, a `CreateProductUseCase` port, a `Product` domain type with no behavior,
a persistence port, an adapter, and an entity↔domain mapper that copies two fields — six artifacts
guarding zero business rules. The honest answer: don't. Keep it a self-contained CRUD slice
(controller + entity + repository in one `products` package), sitting *next to* the quote hexagon
in the same app. The ArchUnit rules deliberately ignore it. One codebase, two shapes, each earning
its keep — that is the 2026 position, not a compromise of it.

**The vocabulary, mapped:**

| | Hexagonal (Cockburn 2005) | Onion (Palermo 2008) | Clean (Martin 2012) |
|---|---|---|---|
| The inside | "the application" — no internal layers prescribed | domain model → domain services → application services rings | entities → use cases rings |
| The boundary | ports (driving / driven) | interfaces owned by inner rings | use-case input/output ports, the Dependency Rule |
| The outside | adapters | infrastructure | interface adapters, frameworks & drivers |
| Prescriptiveness | deliberately minimal | medium | highest — and most often over-implemented |
| In this project | `application/port/in`, `/out` + `adapter/*` | the `onionArchitecture()` test declares our packages as rings | `CreateQuoteUseCase` is straight from this vocabulary |

Same inversion, three dialects. Interview answer: hexagonal names the boundary, onion/clean
additionally legislate the inside.

**The mess in the middle.** The sharpest current critique (Fritzsche, Aug 2026): ports & adapters
polices the *edges*, so teams cargo-cult ports, adapters, and mappers everywhere — and the old
`QuoteService` blob quietly reforms *inside* the hexagon as a bloated "application service".
The pattern never promised to structure the middle; Cockburn says so himself. What kept our middle
honest was step 2 — a real domain model with the rules in it — not the ports. A hexagon around an
anemic domain is ceremony with extra steps.

**Done when:** you can defend, in two sentences each: one place in this app where hexagonal earns
its keep, and one where it would be pure ceremony.

## Self-check

1. Driving port vs driven port: who calls whom, and where does the dependency inversion actually
   happen in each case?
2. Why is the `QuoteRepository` port not just `JpaRepository<QuoteEntity, UUID>`? Name two
   concrete costs the application would pay for using the Spring Data interface directly.
3. The integration tests never had to change during the refactor. What property of those tests
   made that possible — and what kind of assertion would have ruined it?
4. `CreateQuoteCommand` and the controller's `CreateQuoteRequest` look almost identical. Why do
   both exist, and what starts to rot the day the adapter passes its request DTO into the port?
5. What breaks — concretely, not philosophically — the first day `Quote` imports
   `jakarta.persistence`?
6. What does `onionArchitecture()` enforce that the three hand-written rules don't (and vice
   versa)? Hint: adapter-to-adapter dependencies; framework package bans.
7. Round 1 had `@Transactional` on the service; Round 2 dropped it. Where did transactionality go,
   when would that answer stop being good enough, and what is the pragmatic counterargument for
   putting `@Transactional` back on the application service?
8. A new feature request arrives. Which two questions do you ask about it before deciding between
   "hexagon" and "products-style slice"?

## Stretch goals

- **Deterministic domain:** `Quote.create` calls `UUID.randomUUID()` and `Instant.now()` — the
  same smell you flagged in step 1, one floor down. Introduce `Clock`/id-generation as explicit
  dependencies (constructor-injected or as tiny driven ports) and pin `id`/`createdAt` in the
  fakes test.
- **Prove the swap for real:** replace the JPA adapter with a `JdbcClient`-based one without
  touching `domain/` or `application/`. Checkpoints 5 and 6 must pass unchanged; the integration
  suite is your referee.
- **jMolecules instead of hand-rolled rules:** annotate the code with jMolecules'
  hexagonal/onion annotations and let `jmolecules-archunit` verify — compare the failure messages
  with your step-6 rules.
- **Harden the edge:** give the rates adapter a connect/read timeout and a fallback rate table.
  Write a test that points `rate-provider.base-url` at a dead port and asserts quoting degrades
  the way you chose. Notice: domain and application don't change.

## Resources

- *Hexagonal Architecture Explained* — Alistair Cockburn & Juan Manuel Garrido de Paz (2024).
  The definitive text, from the pattern's author — including his own pushback on the ceremony.
- *Get Your Hands Dirty on Clean Architecture*, 2nd ed. — Tom Hombergs. *The* Java/Spring Boot
  practical treatment; the package layout in this lesson is close kin to his.
- *Clean Architecture* — Robert C. Martin (2017). The source text for the third dialect —
  assign critically, alongside the counterpoints below.
- "Clean vs Onion vs Hexagonal Architecture" — Milan Jovanovic. Best short comparison.
- "Is Clean Architecture Overengineering?" — Three Dots Labs podcast. Essential counterweight.
- "Ports and Adapters, and the Mess in the Middle" — Rico Fritzsche (Aug 2026). The step-7
  critique, at length.
- "Crafting a Clean, Pragmatic Architecture" — Victor Rentea (talks). Where the pragmatist line
  actually runs in Java shops.
- [`../docs/research/architecture-styles.md`](../docs/research/architecture-styles.md), section 3
  — the current-state research this lesson is built on.

---

*Toolchain notes: Spring Boot parent 4.1.1 (web starter `spring-boot-starter-webmvc`), Java 25,
ArchUnit `archunit-junit5:1.5.0`, H2 (Boot-managed). No version deviations from the curriculum
pins.*
