# Development Methodologies for Java Backend Engineers — State of Practice, 2025–2026

Research notes for a hands-on training curriculum. All version numbers and trend claims verified via web search, August 2026.

**Platform context (affects every section):** Spring Boot 4.0 shipped 2025-11-20 on Spring Framework 7 and uses **JUnit 6** as its testing foundation; JUnit 4 support (vintage engine) is removed entirely. Any curriculum built now should target Java 17+ (ideally 21+), JUnit 6/Jupiter, and Spring Boot 4.x — while acknowledging most production codebases are still on Boot 3.x / JUnit 5.x.

---

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

## 2. DDD — Domain-Driven Design

### Definition
DDD (Eric Evans, 2003) is an approach to building software for complex domains by aligning code with a shared business model. **Strategic design** deals with the large scale: decomposing a domain into subdomains (core/supporting/generic), drawing **bounded contexts** (boundaries within which a ubiquitous language and model stay consistent), and mapping relationships between them (**context mapping**: partnership, customer-supplier, conformist, anticorruption layer, open host service, published language). Collaborative discovery techniques — **EventStorming** (Brandolini) and Domain Storytelling — feed strategic design. **Tactical patterns** are the building blocks inside a context: entities, value objects, **aggregates** (consistency boundaries reached via a root), domain events, repositories, domain services, and factories.

### Current relevance (2026)
DDD is arguably at peak practical relevance, for two reasons. First, the **modular-monolith correction**: a CNCF Q1 2026 report found 42% of organizations that adopted microservices have consolidated services back into larger deployable units — and modular monoliths live or die on bounded-context discipline. Spring Modulith has made this a first-class, tool-enforced style in the Spring ecosystem. Second, strategic DDD (context boundaries, coupling analysis) has become the dominant language of architecture modernization, merged with Team Topologies and Wardley Mapping (Tune's book). Vlad Khononov is the most prominent current thought leader (his 2024 coupling book generalizes DDD boundary thinking into a measurable model: integration strength — intrusive/functional/model/contract — × distance × volatility). Tactical-pattern DDD in Java is increasingly *expressed in code* via jMolecules annotations/interfaces and *enforced* via ArchUnit and Spring Modulith rather than left as convention. EventStorming remains the standard discovery workshop and is the natural bridge between DDD and BDD/example mapping.

### Key Java tools (current versions, Aug 2026)
| Tool | Version | Notes |
|---|---|---|
| jMolecules (xMolecules org) | **1.9.0** (Nov 2025); 2.0.0-RC1 out | Annotations/types for `@AggregateRoot`, `@Entity`, `@ValueObject`, `@Repository`, events; plus layered/onion/hexagonal architecture annotations; ByteBuddy integration derives boilerplate; ArchUnit rules verify the DDD metamodel |
| Spring Modulith | **1.4 GA**; 2.0/2.1 line (with Boot 4) | Module boundaries verified by tests, module-scoped integration tests, event publication registry with outbox semantics, auto-generated docs (C4/PlantUML) |
| ArchUnit | **1.4.2** (Apr 2026); 1.5.0 available | Enforce context/layer/aggregate rules as plain JUnit tests |
| Axon Framework / Spring | — | Still the main CQRS/event-sourcing option when the curriculum goes that far (optional) |

### Canonical learning resources
1. **Eric Evans — *Domain-Driven Design: Tackling Complexity in the Heart of Software*** (2003) — the source; assign selectively.
2. **Vlad Khononov — *Learning Domain-Driven Design*** (O'Reilly, 2021) — the best current on-ramp, covers strategic + tactical + heuristics.
3. **Vlad Khononov — *Balancing Coupling in Software Design*** (Addison-Wesley Signature Series, Sept 2024) — the notable *newer* book; modern boundary/coupling theory.
4. **Nick Tune & Jean-Georges Perrin — *Architecture Modernization*** (Manning, Feb 2024) — strategic DDD + EventStorming + Team Topologies applied to real modernization.
5. **Alberto Brandolini — *Introducing EventStorming*** (Leanpub) plus eventstorming.com; and **Hofer & Schwentner — *Domain Storytelling*** (2021) as the companion discovery technique.
(Reference implementations: `xsreality/spring-modulith-with-ddd` on GitHub; Baeldung's jMolecules and Spring Modulith guides.)

### Hands-on training project idea
**"Storm it, bound it, enforce it":** big-picture EventStorming of a familiar domain (e.g., conference/training registration: enrollment, invoicing, waitlists, certificates). Identify subdomains, draw bounded contexts and a context map with an explicit ACL. Then implement *one* core context as a Spring Modulith module: aggregates and value objects annotated with jMolecules, cross-module communication only via domain events, and a test suite where `ApplicationModules.verify()` + jMolecules-ArchUnit rules fail the build when someone reaches across a boundary. The payoff moment: deliberately add an illegal dependency and watch the architecture test catch it.

---

## 3. BDD — Behavior-Driven Development

### Definition
BDD (Dan North, mid-2000s) reframes TDD around *behavior conversations*: before building, business people, developers, and testers collaboratively discover and agree on concrete examples of desired behavior, then encode those examples as executable specifications that drive development and remain living documentation. Its discovery practices are **example mapping** (Matt Wynne's card-based workshop: rules/examples/questions per story) and **specification by example** (Adzic). Its automation layer is usually Gherkin (Given/When/Then) bound to step definitions — but BDD's authors insist the conversation, not the Gherkin, is the point.

### Current relevance (2026)
Honest answer for a curriculum: **plateaued and consolidating, not growing** — but with a plausible second act. Adoption studies put BDD-framework usage around ~27% of projects, and the well-documented failure mode dominates the discourse: automation engineers writing Gherkin alone, product owners never reading it, and the whole thing degrading into an expensive translation layer ("BDD doesn't scale" experiences). Practitioner consensus (e.g., Automation Panda's 2025 "Is BDD Dying?") is that BDD is not dead but must refocus on discovery/collaboration rather than tooling. Cucumber-JVM itself is mature and actively released (volunteer-maintained since the SmartBear-era commercial retreat). Two live currents matter for 2026: (1) **spec-driven development with AI agents** is being explicitly framed as "BDD's second chance" — Gherkin-style executable specs turn out to be an excellent contract format for steering coding agents; (2) in the JVM world much "BDD" energy has shifted to developer-facing tools: Spock's given/when/then blocks and Karate's DSL deliver readable specs without the business-collaboration overhead. Teach BDD as a *collaboration discipline with a niche automation payoff*, not as a default test framework.

### Key Java tools (current versions, Aug 2026)
| Tool | Version | Notes |
|---|---|---|
| Cucumber-JVM | **7.34.x** (7.34.4) | Canonical setup: JUnit Platform Suite (`@Suite`) on JUnit 5/6; community-maintained |
| Spock | **2.4** (Dec 2025 — first stable in ~3 years) | Groovy-based given/when/then specs, powerful data tables; Groovy 4 |
| Karate | **2.1.0** (June 2026) | API-test DSL with Gherkin-like syntax (not true collaborative BDD); moved under `io.karatelabs` |
| Serenity BDD | active (releases July 2026) | Rich living-documentation reports on Cucumber/JUnit; criticized in 2026 for heavy transitive-dependency management ("dependency hell") — worth a caveat in training |
| JBehave | legacy | Mention only as history |

### Canonical learning resources
1. **Gojko Adzic — *Specification by Example*** (Manning, 2011) — the collaboration case, still the best argument for/limits of the practice.
2. **Smart & Molak — *BDD in Action, 2nd ed.*** (Manning, 2023) — the current comprehensive text (John Ferguson Smart is Serenity's creator).
3. **Wynne, Hellesøy, Tooke — *The Cucumber for Java Book*** (Pragmatic) + **Matt Wynne's "Example Mapping" article/talks** (cucumber.io blog) — the discovery workshop itself.
4. **Dan North — "Introducing BDD"** (dannorth.net, 2006) — short, free, origin story.
5. **Andy Knight (Automation Panda) — "Is BDD Dying?"** (Mar 2025) — assign as the honest state-of-the-union discussion piece.

### Hands-on training project idea
**"Example mapping first, Gherkin second":** take a deliberately ambiguous business rule (e.g., tiered discount + loyalty-points policy with conflicting edge cases). Run a timed example-mapping session (rules/examples/questions cards) with someone role-playing the product owner. Only then write the agreed examples as Cucumber-JVM scenarios against a Spring Boot service slice, with step definitions calling the domain API (not the UI). Debrief on what the *conversation* caught that coding straight from the ticket would have missed. Optional AI extension: feed the finished feature file to a coding agent as its spec and evaluate the implementation against the scenarios — connecting BDD to spec-driven agentic development.

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

## Cross-cutting observations for curriculum design

- **Sequencing:** TDD → testing strategy → DDD → BDD works well; BDD's discovery techniques (example mapping) and DDD's (EventStorming) are cousins and can share a "collaborative modeling" day.
- **The AI thread is the differentiator for 2026:** every methodology now has an AI angle worth one exercise — TDD as agent guardrails, executable specs as agent contracts (BDD/SDD), mutation testing to audit AI-written tests, and DDD boundaries as the unit of context you hand an agent.
- **Version watch-outs for lab environments:** JUnit 5→6 and Testcontainers 1.x→2.x are both fresh, breaking-ish migrations — pin versions explicitly in training repos; Spring Boot 4 + JUnit 6 + Testcontainers 2 + Mockito 5.23 + AssertJ 3.27 is a coherent, verified 2026 stack.
- **Honest framing:** TDD and DDD are rising in relevance; BDD tooling is stable but the practice needs its collaboration story told carefully; the pyramid should be taught as one of several shapes, chosen per architecture.

## Sources

[JUnit release notes](https://docs.junit.org/current/release-notes/) · [JUnit 6 deep dive](https://ankurm.com/junit-6-deep-dive-mastering-the-next-generation-of-java-testing/) · [Mockito releases](https://github.com/mockito/mockito/releases) · [AssertJ releases](https://github.com/assertj/assertj/releases) · [PIT](https://pitest.org/) · [JavaPro PIT guide](https://javapro.io/2026/01/21/test-your-tests-mutation-testing-in-java-with-pit/) · [Canon TDD](https://tidyfirst.substack.com/p/canon-tdd) · [Exadel on TDD+AI](https://exadel.com/news/test-driven-development-ai-coding) · [Augment Code Spec+TDD](https://www.augmentcode.com/guides/spec-tdd-shippable-ai-generated-code) · [jMolecules](https://github.com/xmolecules/jmolecules/releases) · [Spring Modulith modular monolith guide](https://www.joptimize.io/blog/spring-modulith-modular-monolith-2026) · [Khononov books](https://vladikk.com/page/books/) · [Architecture Modernization (Manning)](https://www.manning.com/books/architecture-modernization) · [EventStorming](https://www.eventstorming.com/) · [Cucumber-JVM](https://github.com/cucumber/cucumber-jvm/releases) · [Spock 2.4](https://github.com/spockframework/spock/releases/tag/spock-2.4) · [Karate releases](https://github.com/karatelabs/karate/releases) · [Serenity BDD in 2026](https://medium.com/@andrei.oleynik/serenity-bdd-in-2026-a-framework-or-dependency-hell-015e3d16d33e) · [Is BDD Dying? (Automation Panda)](https://automationpanda.com/2025/03/06/is-bdd-dying/) · [Testcontainers releases](https://github.com/testcontainers/testcontainers-java/releases) · [Testcontainers 2 upgrade](https://blog.doubleslash.de/en/software-technologien/coding-and-frameworks/testcontainers-2-an-upgrade-worth-it/) · [Pact JVM](https://github.com/pact-foundation/pact-jvm) · [Pact vs Spring Cloud Contract](https://qaskills.sh/compare/pact-vs-spring-cloud-contract) · [ArchUnit](https://www.archunit.org/) · [Test shapes analysis](https://thetestingarchitect.substack.com/p/test-pyramid-test-honeycomb-test) · [Spring Boot 4.0](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/) · [Testing in Spring Boot 4](https://rieckpil.de/whats-new-for-testing-in-spring-boot-4-0-and-spring-framework-7/) · [Diffblue Cover](https://www.diffblue.com/)
