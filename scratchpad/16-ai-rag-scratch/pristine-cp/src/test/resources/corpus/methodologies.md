# Methodologies (research excerpt)

> Frozen excerpt of docs/research/methodologies.md, copied into src/test/resources so the checkpoint tests
> have a corpus that never changes. The running application reads the real files.

## 1. TDD — Test-Driven Development

### Definition
TDD is a design-and-development discipline in which the developer works in short red-green-refactor cycles: write a small failing test that specifies the next behavior, write the minimum production code to make it pass, then refactor with the safety net of a green suite. Kent Beck's 2023 "Canon TDD" essay restates the original definition precisely because so much of what is criticized as "TDD" isn't: the canonical loop is (1) write a list of test scenarios, (2) turn exactly one into a concrete failing test, (3) make it pass without breaking others, (4) optionally refactor, (5) repeat until the list is empty.

### Current relevance (2026)
TDD is having a genuine resurgence, driven by AI coding agents rather than despite them. Industry data cited across 2025–26 (DORA-style findings) shows a 25% increase in AI-assist usage correlating with a ~7% *decrease* in delivery stability for teams *without* disciplined testing — tests have become the primary steering and verification mechanism for AI-generated code. Documented failure modes of unguided agents: skipping the red phase, writing tests after the code that merely confirm what it does, and deleting failing tests instead of fixing implementations. Emerging best practice separates the *test author* role from the *implementation* role (human writes tests / agent implements, or two separated agent contexts), and pairs specs with TDD ("spec provides the what, TDD the proof"). The classicist (Detroit/Chicago, state-based, Kent Beck) vs mockist (London, interaction-based, outside-in, Freeman/Pryce) split is still the standard teaching frame; the modern consensus — heavily shaped by Khorikov's book — leans classicist by default (mock only at architectural boundaries you don't own), because over-mocked suites couple tests to implementation details and rot fast, a problem AI-generated tests amplify.

### Key Java tools (current versions, Aug 2026)
| Tool | Version | Notes |
|---|---|---|
| JUnit | **6.1.x** (6.0.0 GA 2025-09-30); 5.14.3 maintenance line | Unified versioning across Platform/Jupiter/Vintage; Java 17 baseline; JSpecify null-safety annotations; Kotlin `suspend` test support; CancellationToken; Vintage engine deprecated. Migration 5→6 is near drop-in |
| Mockito | **5.23.0** (Mar 2026) | Still the default mocking library; inline mock maker is default; note JDK agent-loading warnings on newer JDKs |
| AssertJ | **3.27.7** stable; 4.0.0-M1 milestone (Java 17 baseline) | De facto standard fluent assertions; 3.27.7 fixed an XXE CVE |
| PIT (pitest) | **1.19.x** (1.19.1, Apr 2025) | Mutation testing standard for the JVM; JUnit 5 via `pitest-junit5-plugin`; incremental analysis + `scmMutationCoverage` goal make it CI-viable |
| Diffblue Cover | 2026.x releases | Autonomous (RL-based, non-LLM) unit test generation for Java — relevant as a *contrast* to TDD: generated tests characterize existing behavior, they don't drive design |

### Canonical learning resources
1. **Kent Beck — *Test-Driven Development: By Example*** (2002) — still the root text.
2. **Kent Beck — "Canon TDD"** (tidyfirst.substack.com, Dec 2023) — the modern, concise restatement; ideal pre-reading.
3. **Steve Freeman & Nat Pryce — *Growing Object-Oriented Software, Guided by Tests*** (2009) — the London-school text; outside-in with mocks.
4. **Vladimir Khorikov — *Unit Testing Principles, Practices, and Patterns*** (Manning, 2020) — the most-cited modern synthesis of the two schools; what/when to mock.
5. **Martin Fowler — "Mocks Aren't Stubs"** (martinfowler.com) — origin of the classicist/mockist vocabulary; short, free.
(Also: Emily Bache's *Samman Technical Coaching* katas and her Gilded Rose material for trainers.)

### Hands-on training project idea
**"One kata, two schools, then break it":** implement a small vending-machine or bank-account kata twice — once inside-out/classicist (state assertions, no mocks), once outside-in/mockist (drive from a use-case boundary, mock collaborators). Compare the resulting designs and test brittleness. Then run PIT on both suites and try to kill surviving mutants. Cap it with an AI round: have participants prompt an AI assistant to implement from *their* failing tests only, and observe/repair the classic agent failure modes (test deletion, over-fitting). This teaches the loop, the schools, mutation testing, and AI-era test stewardship in one arc.

---

## 4. Testing Strategy (Pyramid vs Honeycomb/Trophy, Testcontainers, Contract & Architecture Testing)

### Definition
Test strategy is the deliberate allocation of testing effort across layers: the classic **test pyramid** (Cohn; many fast unit tests, fewer integration, few E2E), versus the **testing honeycomb** (Spotify; for microservices, integration-dominant because complexity lives *between* services), versus the **testing trophy** (Kent C. Dodds; integration-heavy plus a static-analysis base). The modern toolkit adds **container-backed integration testing** (Testcontainers: real databases/brokers as throwaway Docker containers in tests), **contract testing** (Pact's consumer-driven contracts; Spring Cloud Contract's producer-driven contracts — verifying service interactions without spinning up both sides), and **architecture testing** (ArchUnit: executable rules about dependencies and structure, i.e., fitness functions).

### Current relevance (2026)
The dogmatic pyramid is effectively retired as a universal rule; the 2025–26 consensus is **shape follows architecture**. Survey data circulating this year: ~48% of microservice teams describe their strategy as honeycomb-like, while trophy-style suits full-stack/serverless teams; risk-based allocation is the other big bloc. Two forces drove this: Testcontainers made "real dependencies in tests" cheap enough that integration tests lost most of their historical cost penalty, and AI-generated code shifted the bottleneck from writing tests to *trusting* them — boosting interest in mutation testing (PIT) and contract verification as quality gates rather than raw coverage. Testcontainers crossed a major milestone: **2.0** (GA late 2025; 2.0.5 by Apr 2026) dropped JUnit 4, renamed all module artifacts (`testcontainers-junit-jupiter`, `org.testcontainers.mysql` packages), and offers an OpenRewrite migration recipe — plus first-class JUnit 6 compatibility. Contract testing remains a two-tool market: Pact (JVM lib at **4.6.x** stable / 4.7.x in development) for polyglot, consumer-driven flows with a broker and `can-i-deploy`; Spring Cloud Contract for JVM/Spring-only shops preferring producer-owned contracts and stub jars via existing Maven infrastructure. ArchUnit (**1.4.2**/1.5.0) is now routinely wired into CI as fitness functions, frequently alongside Spring Modulith.

### Key Java tools (current versions, Aug 2026)
| Tool | Version | Notes |
|---|---|---|
| Testcontainers Java | **2.0.5** (Apr 2026) | Major-version migration is itself a teachable topic; BOM available; JUnit 4 removed |
| Pact JVM | **4.6.x** stable (4.7.x branch active) | JUnit 5 support via `junit5` modules; broker + can-i-deploy workflow |
| Spring Cloud Contract | current release train with Boot 3.x/4.x | Contracts in Groovy/YAML; generates provider tests + consumer stubs |
| ArchUnit | **1.4.2** / 1.5.0 | Layer/cycle/naming rules as JUnit tests; jMolecules and Spring Modulith integrations |
| PIT | 1.19.x | Doubles as the "test the tests" gate in strategy discussions |
| WireMock / MockWebServer | current | Boundary stubbing where contract tests are overkill |

### Canonical learning resources
1. **Ham Vocke — "The Practical Test Pyramid"** (martinfowler.com, 2018) — still the baseline reading before critiquing it.
2. **Spotify Engineering — "Testing of Microservices"** (the honeycomb article) and **Kent C. Dodds — "The Testing Trophy and Testing Classifications"** — the two counter-models.
3. **Testcontainers guides (testcontainers.com/guides)** — official, current, hands-on.
4. **Pact docs (docs.pact.io) contract-testing university / CDC workshop** and Baeldung's Pact + Spring Cloud Contract tutorials — the practical contract-testing path.
5. **Sam Newman — *Building Microservices*, 2nd ed.** (2021), testing chapters — strategy-level framing of contract vs E2E testing.

### Hands-on training project idea
**"Two services, no E2E allowed":** provide a small Order service (consumer) and Payment service (provider) as separate Spring Boot apps. Reach deploy-confidence *without writing a single end-to-end test*: (1) integration tests per service with Testcontainers 2.x (PostgreSQL + Kafka containers); (2) a Pact consumer test in Order generating a contract, verified in Payment's build (or the same exercise mirrored in Spring Cloud Contract to compare philosophies); (3) ArchUnit rules locking the hexagonal structure; (4) a PIT mutation-score gate (e.g., 70%) proving the suite actually detects faults. Finish by breaking the provider's API and watching the contract test — not a flaky E2E — catch it. This one project exercises the honeycomb argument, Testcontainers, both contract-testing styles, ArchUnit, and mutation testing.

---

