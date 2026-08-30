# Backend Architecture Styles in the Java Ecosystem — State of the Art, August 2026

Research notes for a hands-on training curriculum. All version numbers and trend claims verified via web search in August 2026.

**Ecosystem baseline (context for everything below):** Spring Boot 4.1.1 (Aug 20, 2026) on Spring Framework 7, Java 17 minimum / Java 25 LTS as the common production target; Quarkus 3.37.x (LTS 3.33, supported to March 2027); Micronaut 5.0.6 (5.0 GA May 2026, Java 25 baseline, GraalVM 25); Spring Cloud 2025.1.x / 2026.0 "Oakwood" for Boot 4. Virtual threads (Project Loom) are now production-mainstream and have significantly reduced the need for reactive programming in I/O-bound services — an important cross-cutting theme for any 2026 curriculum.

---

## 1. Microservices

**Definition.** An application is decomposed into independently deployable services, each owning its own data store and communicating over the network (synchronous HTTP/gRPC or asynchronous messaging). Services align with business capabilities/bounded contexts and are owned end-to-end by small teams, enabling independent scaling, deployment, and technology choices — at the cost of distributed-systems complexity (network failures, eventual consistency, distributed tracing, operational overhead).

**Relevance in 2026: stable but chastened — "default no, deliberate yes."** Microservices remain the standard for genuinely large organizations, but the industry consensus has hardened against using them as a default. A widely cited CNCF figure says ~42% of organizations that adopted microservices have consolidated at least some services back into larger deployable units; Gartner found ~60% of teams regretted microservices for small/medium apps. Amazon Prime Video's Video Quality Analysis team's 2023 move back to a monolith (90% infra cost reduction) is still the canonical cautionary tale. The 2026 decision heuristics: microservices are justified by *organizational* scale (roughly 50+ engineers needing independent deploys) and genuinely divergent scaling/compliance needs — not by anticipated technical scale. "Monolith-first" (Fowler) and the strangler-fig migration pattern are the accepted playbook.

**Key frameworks (current versions, Aug 2026):**
- **Spring Boot 4.1.1 + Spring Cloud 2026.0 "Oakwood"** — still the dominant stack (Gateway, config, circuit breakers via Resilience4j, service discovery).
- **Quarkus 3.37.x** (LTS 3.33) — container-first, fast startup, best-in-class GraalVM native-image story; strong in Kubernetes/serverless shops.
- **Micronaut 5.0.6** — compile-time DI, lean footprint, Java 25 baseline.
- **Helidon 4.x** — notable as the first framework rebuilt entirely on virtual threads.
- **Communication:** the 2026 pattern is explicitly hybrid — REST (OpenAPI) for public/edge APIs, **gRPC for internal service-to-service** (now first-class via the official **Spring gRPC** project, 1.0.x supporting Boot 4.1 — replacing the community `net.devh` starter), and **Kafka/Pulsar for async event flows**. Async-first is increasingly the recommended default between services to reduce temporal coupling.
- **Service mesh:** a genuinely mixed picture worth teaching honestly. Classic sidecar-mesh adoption declined sharply (one analysis: 18% in 2023 → 8% in 2025) as teams found the overhead unjustified; simultaneously **Istio ambient (sidecar-less) mode** went production-grade (OpenShift Service Mesh 3.2, ambient multicluster beta at KubeCon EU 2026, ~70% resource savings) and is driving a partial comeback, especially for AI-traffic management. 2026 guidance: most Java teams don't need a mesh; those that do should start with ambient mode or Gateway API + Cilium.

**Canonical learning resources:**
1. *Microservices Patterns, 2nd Edition* — Chris Richardson (Manning, MEAP since mid-2025; examples in Java) + microservices.io pattern catalog.
2. *Building Microservices, 2nd Edition* — Sam Newman (O'Reilly, 2021) and its companion *Monolith to Microservices*.
3. Martin Fowler — "Microservices" and "MonolithFirst" (martinfowler.com) — still the framing texts.
4. Amazon Prime Video's "Scaling up the audio/video monitoring service and reducing costs by 90%" case study + the commentary wave around it.
5. "Microservices vs Monoliths in 2026: When Each Architecture Wins" (Java Code Geeks, Dec 2025) — a good current-state decision framework.

**Training project idea.** *"Order & Shipping":* two Spring Boot services (Order, Shipping) plus an API gateway. Phase 1: synchronous REST between them; inject a failure and watch cascading breakage. Phase 2: swap the internal call to gRPC with Spring gRPC (contract-first .proto). Phase 3: replace with Kafka events and an outbox; demonstrate resilience during Shipping downtime. Finish with distributed tracing (OpenTelemetry/Micrometer) across all three phases. Teaches the *cost* of distribution, not just the mechanics.

---

## 2. Modular Monolith ("Modulith")

**Definition.** A single deployable unit whose internals are divided into strictly bounded, domain-aligned modules with explicit, enforced dependencies — typically communicating internally via application events rather than direct calls. It delivers most of the organizational benefits of microservices (team ownership, bounded contexts, independent evolution) without the distributed-systems tax, and keeps the option open to extract a module into a service later.

**Relevance in 2026: strongly rising — arguably the headline architecture story of 2025-2026.** This is the "industry swing back": the CNCF 42%-consolidation statistic, Shopify handling 30TB/min during BFCM 2025 on a Rails modular monolith, Stack Overflow's long-standing monolith, and DHH's vocal advocacy ("by the time you've earned the scale to justify microservices, your speed, your clarity, and your product instincts will already be gone"). In the Java world Spring Modulith graduated to a flagship project, JetBrains shipped dedicated IntelliJ IDEA support (Feb 2026), and "modulith-first, extract when proven" is now the mainstream recommendation for new systems.

**Key frameworks/libraries (current versions):**
- **Spring Modulith 2.1 GA** (June 2026; 2.0 GA Nov 21, 2025 on Boot 4/Framework 7; 1.4.x maintained for Boot 3). Features: module boundary verification from package conventions (`ApplicationModules.verify()`), `@ApplicationModuleTest` for module-scoped integration tests, event publication registry with multiple stores (JDBC/JPA/MongoDB/Neo4j), **event externalization** to Kafka/AMQP/SQS via `@Externalized`, per-module Flyway migrations (2.0), transactional-outbox externalization via Namastack Outbox and JobRunr support (2.1), auto-generated C4/PlantUML documentation, and actuator/observability of module interactions.
- **ArchUnit 1.5.0** — architecture-as-tests; the enforcement backbone even outside Spring.
- **jMolecules** (with jmolecules-archunit) — express DDD/architecture concepts as annotations, verified automatically.
- Java Platform Module System and Maven/Gradle multi-module builds as coarser complements.

**Key advocates & canonical resources:**
1. **Oliver Drotbohm** — "Building Better Monoliths – Modulithic Applications with Spring" (Spring I/O talks, ongoing "Spring Modulith – A Deep Dive" deck) — the defining voice in the Java space.
2. **Simon Brown** — "Modular Monoliths" talk (the original "if you can't build a modular monolith, what makes you think microservices will help?").
3. **Kamil Grzybek** — "Modular Monolith with DDD" (GitHub repo + article series) — the most complete reference implementation write-up.
4. Baeldung's "Introduction to Spring Modulith" + the official Spring Modulith reference docs.
5. JetBrains blog: "Migrating to Modular Monolith using Spring Modulith and IntelliJ IDEA" (Feb 2026) — current tooling walkthrough.

**Training project idea.** *"Library system as a modulith":* one Spring Boot 4 app with `catalog`, `lending`, and `notifications` modules. Students first write a naive version with cross-package imports; then add `ApplicationModules.verify()` and watch the build fail; refactor to named interfaces and `@ApplicationModuleListener` events; write `@ApplicationModuleTest`s; generate the C4 docs. Capstone: flip one annotation to `@Externalized` and watch a domain event flow to Kafka unchanged — demonstrating the modulith→microservice evolution path in a single afternoon.

---

## 3. Hexagonal (Ports & Adapters), Onion, and Clean Architecture

**Definition & differences.** All three are dependency-inversion architectures that isolate domain logic from infrastructure; they differ mainly in vocabulary and prescriptiveness. **Hexagonal** (Alistair Cockburn, 2005): the core exposes *ports* (interfaces); *adapters* translate between ports and the outside world (driving adapters: REST controllers, schedulers; driven adapters: repositories, message publishers). Deliberately minimal — it prescribes *no* internal layering. **Onion** (Jeffrey Palermo, 2008): adds concentric rings (domain model → domain services → application services → infrastructure) with dependencies pointing inward. **Clean** (Robert C. Martin, 2012): synthesizes both with entities/use-cases/interface-adapters/frameworks rings and the Dependency Rule; the most prescriptive and the most commonly over-implemented. Practical takeaway for training: teach them as one family — "DIP applied at the architecture level" — with hexagonal as the teachable core.

**Relevance in 2026: stable as the default *internal* structure, with a strong pragmatist correction.** These patterns are now standard vocabulary — Spring Modulith explicitly "formalizes hexagonal architecture and DDD into Spring Boot", and jMolecules ships annotations for all three. But the loudest 2025-2026 content is *anti-ceremony*: criticism of 11-files-per-feature Clean Architecture implementations, cognitive-load complaints, "your CRUD app doesn't need this," and Cockburn himself pushing back on the ceremony piled onto his pattern (see also "Ports and Adapters, and the Mess in the Middle," Aug 2026). The mature 2026 position: apply ports & adapters where the domain is genuinely complex; use plain transaction-script/CRUD where it isn't; enforce whatever you choose with ArchUnit rather than folder dogma.

**Key frameworks/libraries:** intentionally framework-light — plain Java packages + Spring. Enforcement/expression tooling: **ArchUnit 1.5.0** (layer/onion rules built in: `Architectures.layeredArchitecture()`, `onionArchitecture()`), **jMolecules** `@DomainRing`/hexagonal/layered annotation modules with ArchUnit and bytecode integrations, **Spring Modulith** for module-level boundaries. Works identically in Quarkus/Micronaut.

**Canonical learning resources:**
1. *Hexagonal Architecture Explained* — Alistair Cockburn & Juan Manuel Garrido de Paz (2024) — the definitive book, from the pattern's author.
2. *Get Your Hands Dirty on Clean Architecture* (2nd ed.) — Tom Hombergs — *the* Java/Spring Boot practical treatment.
3. *Clean Architecture* — Robert C. Martin (2017) — the source text; assign critically.
4. "Clean vs Onion vs Hexagonal Architecture" — Milan Jovanovic — best short comparison.
5. Counterpoint material: "Is Clean Architecture Overengineering?" (Three Dots Labs podcast) and Victor Rentea's "Crafting a Clean, Pragmatic Architecture" talks — essential for balance.

**Training project idea.** *"Same app, three shapes":* a small insurance-quote service. Round 1: students build it as classic controller→service→repository layers. Round 2: refactor to hexagonal — extract `QuoteRepository` and `RateProviderPort` ports, add a REST driving adapter and JPA + external-API driven adapters; swap the rate provider for a stub in tests without Spring context. Round 3: add ArchUnit tests that fail when the domain imports Spring or JPA. Debrief exercise: a deliberately trivial CRUD endpoint where the class discusses whether hexagonal earns its keep — teaching judgment, not just pattern.

---

## 4. Vertical Slice Architecture & Package-by-Feature

**Definition.** Code is organized by *feature/use-case* (a vertical slice cutting through UI-to-database) rather than by technical layer. Each slice is a self-contained folder holding its endpoint/handler, request/response models, validation, data access, and tests; coupling is minimized *between* slices and maximized *within* one. Each slice may choose its own internal sophistication — one slice is a transaction script, another uses a rich domain model. Package-by-feature is the long-standing Java packaging expression of the same idea, versus package-by-layer (`controllers/`, `services/`, `repositories/`).

**Relevance in 2026: rising, migrating from .NET into Java.** Coined/popularized by Jimmy Bogard (~2018, .NET/MediatR world), VSA has become one of the most discussed styles of 2025-2026 and is increasingly presented as the pragmatic answer to Clean Architecture fatigue — "avoid premature abstraction, organize around change." In Java it's gaining real traction, usually combined with Spring Modulith (modules = coarse boundaries, slices = internal organization) and CQRS-lite. Package-by-feature is now near-universal advice in the Java community over package-by-layer. Notable current debate: whether slices and Clean Architecture compose or conflict (Oskar Dudycz's and Rico Fritzsche's essays).

**Key frameworks/libraries:** primarily a packaging discipline — Spring Boot/Quarkus with plain packages; **Spring Modulith** (module per feature area), **ArchUnit** slice rules (`slices().matching("..feature.(*)..")` to forbid inter-slice dependencies); optional in-process command/query dispatch via Spring application events or lightweight mediator libraries (Java ports of MediatR exist — e.g. Pipelinr — but plain Spring beans per use-case are the more common Java idiom).

**Canonical learning resources:**
1. "Vertical Slice Architecture" — Jimmy Bogard (jimmybogard.com) + his NDC conference talk (YouTube) — the origin.
2. "My thoughts on Vertical Slices, CQRS, Semantic Diffusion and other fancy words" — Oskar Dudycz (Architecture Weekly) — best critical/nuanced take.
3. "Vertical Slice Architecture: A Modern Approach to Feature-Centric Software Design" (javathinking.com) — Java-specific treatment.
4. Milan Jovanovic — "Vertical Slice Architecture" articles/videos (excellent structure, .NET examples that port directly).
5. The classic "package by feature, not layer" argument — Simon Brown's "Package by component" chapter material in *Clean Architecture* and his "Modular Monoliths" talk cover the packaging spectrum well.

**Training project idea.** *"Refactor kata: layers → slices":* give students a small task-management app packaged by layer (one `service` class doing everything). Exercise 1: implement a new feature and count how many packages they touch. Exercise 2: repackage into `features/createtask/`, `features/completetask/`, `features/reports/` — each with its own handler and data access — and implement the next feature touching exactly one directory. Exercise 3: add the ArchUnit slice-independence test. This makes the maintainability argument *felt* within two hours.

---

## 5. Emerging / Adjacent Styles with Real Traction

### Self-Contained Systems (SCS)

**Definition.** An SCS (scs-architecture.org, from INNOQ/Eberhard Wolff) is a vertical slice at *system* granularity: each system owns its UI, business logic, and database, is independently deployable and operable by one team, and integrates with other SCSs asynchronously (or via UI composition/links) wherever possible. Think "a handful of macro-services, each a small monolith with a face" — deliberately coarser than microservices.

**Relevance:** stable niche with renewed 2025-2026 interest as a middle path — recent advocacy positions it explicitly as the microservices alternative for enterprise modernization ("Goodbye Microservices, Hello Self-contained Systems" — Simon Martinelli, 2025). Production usage at Otto, Galeria Kaufhof, Kühne+Nagel. **Tooling:** any Java web stack; the style is UI-inclusive, so server-side UI frameworks (Vaadin, Thymeleaf, JSF successors) and transclusion/web-components composition are natural fits. **Resources:** scs-architecture.org; INNOQ's "Self-contained Systems: A Different Approach to Microservices"; Eberhard Wolff's *Microservices — A Practical Guide* (covers SCS); Martinelli's 2025 article. **Training project:** split a shop into two SCSs — "Catalog" and "Checkout," each a full Spring Boot app with its own UI and database; integrate only via links and one async event stream; kill Checkout and show Catalog is fully unaffected.

### Cell-Based Architecture

**Definition.** The system is partitioned into *cells* — self-contained replicas or shards, each containing a full set of services plus their own data stores, caches, and infrastructure — with a thin routing layer directing each customer/tenant to exactly one cell. The goal is fault isolation and blast-radius reduction ("bulkheads at infrastructure scale") rather than code organization.

**Relevance:** rising in the large-scale/platform-engineering niche, with real production stories (DoorDash, Slack, AWS internally) and steady 2025-2026 content growth; it's an *operational* pattern that composes with microservices or moduliths rather than replacing them. Rarely relevant below serious multi-region scale — teach it as awareness, not practice. **Tooling:** Kubernetes + routing layer (Gateway API/Istio), IaC for cell stamping; Akka has positioned itself for cell-based designs. **Resources:** AWS Well-Architected "Reducing scope of impact with cell-based architecture" whitepaper; InfoQ's cell-based architecture eMag/articles; TechTarget's "Exploring cell-based architecture vs. microservices"; the DoorDash and Slack engineering-blog write-ups. **Training project:** simulation-scale only — run two "cells" of the same two-service app in separate Docker Compose stacks with a tiny router that hashes user IDs to cells; kill cell A and demonstrate cell B users are unaffected.

### Cross-cutting 2026 trends worth one curriculum module

- **Virtual threads replacing reactive:** blocking-style code on Loom is now the default recommendation for I/O-bound Java services (JavaLand speakers project ~70% adoption by 2027); WebFlux retreats to genuine streaming edge cases. Helidon 4 is built on it; Spring Boot enables it with one property.
- **Event-driven backbone everywhere:** the transactional outbox pattern has gone mainstream (now built into Spring Modulith 2.1); Kafka remains the lingua franca across every style above.
- **Architecture-as-code enforcement:** ArchUnit/jMolecules/Spring Modulith verification tests as "fitness functions" are now expected practice, and make an excellent recurring thread through *all* curriculum modules.

---

## Suggested Curriculum Arc

The research suggests a natural teaching order that mirrors the industry's own learning curve: **package-by-feature/vertical slices** (day 1, low ceremony) → **hexagonal/ports & adapters** (where complexity earns it) → **modular monolith with Spring Modulith** (the 2026 default recommendation, with enforcement and events) → **extraction to microservices** (gRPC, Kafka, outbox — showing the modulith→microservices path) → **awareness sessions** on SCS, cell-based, and service-mesh-in-2026. Each stage can literally evolve the same codebase, which is itself the single most current architectural message: *start modular, distribute only what proves it needs distribution.*

## Sources

[Java Code Geeks — Microservices vs Monoliths in 2026](https://www.javacodegeeks.com/2025/12/microservices-vs-monoliths-in-2026-when-each-architecture-wins.html) · [Hakia — Microservices vs Monoliths trade-offs](https://hakia.com/compare/microservices-vs-monoliths/) · [Metamindz — The Modular Monolith Renaissance](https://www.metamindz.co.uk/post/modular-monolith-renaissance-shopify-amazon-stack-overflow-2026) · [byteiota — Microservices Rollback 2026](https://byteiota.com/microservices-rollback-2026-42-return-to-monoliths/) · [Spring blog — Spring Boot 4.0.6](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/) · [HeroDevs — Spring Boot versions July 2026](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026) · [Quarkus releases](https://quarkus.io/releases/) · [Micronaut 5.0.0 release](https://micronaut.io/2026/05/20/micronaut-framework-5-0-0-released/) · [Micronaut 5.0.6 release](https://micronaut.io/2026/07/23/micronaut-framework-5-0-6-released/) · [Spring Cloud supported versions](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions) · [Spring gRPC project](https://spring.io/projects/spring-grpc/) · [Spring Modulith 2.0 GA announcement](https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/) · [Spring Modulith 2.1 releases](https://github.com/spring-projects/spring-modulith/releases) · [JetBrains — Migrating to Modular Monolith with Spring Modulith](https://blog.jetbrains.com/idea/2026/02/migrating-to-modular-monolith-using-spring-modulith-and-intellij-idea/) · [Baeldung — Spring Modulith](https://www.baeldung.com/spring-modulith) · [Drotbohm — Building Better Monoliths (Spring I/O)](https://2019.springio.net/sessions/building-better-monoliths-implementing-modulithic-applications-with-spring) · [Cockburn & Garrido de Paz — Hexagonal Architecture Explained](https://www.amazon.com/Hexagonal-Architecture-Explained-Alistair-Cockburn/dp/173751978X) · [Milan Jovanovic — Clean vs Onion vs Hexagonal](https://milanjovanovic.tech/blog/clean-architecture-vs-onion-vs-hexagonal) · [Rico Fritzsche — Ports and Adapters and the Mess in the Middle](https://levelup.gitconnected.com/ports-and-adapters-and-the-mess-in-the-middle-dbe1d98c7172) · [Three Dots Labs — Is Clean Architecture Overengineering?](https://threedots.tech/episode/is-clean-architecture-overengineering/) · [AlgoCademy — Why Your Clean Architecture Is Making Things More Complicated](https://algocademy.com/blog/why-your-clean-architecture-is-making-things-more-complicated/) · [Jimmy Bogard — Vertical Slice Architecture talk](https://www.youtube.com/watch?v=oAoaMlS1PWo) · [Oskar Dudycz — My thoughts on Vertical Slices](https://www.architecture-weekly.com/p/my-thoughts-on-vertical-slices-cqrs) · [javathinking — Vertical Slice Architecture](https://www.javathinking.com/blog/vertical-slice-architecture/) · [SCS architecture site](https://scs-architecture.org/) · [INNOQ — Self-contained Systems](https://www.innoq.com/en/articles/2016/11/self-contained-systems-different-microservices/) · [Martinelli — Goodbye Microservices, Hello Self-contained Systems](https://martinelli.ch/goodbye-microservices-hello-self-contained-systems/) · [TechTarget — Cell-based architecture vs microservices](https://www.techtarget.com/searchapparchitecture/tip/Exploring-cell-based-architecture-vs-microservices) · [Mad Devs — Cell-Based Architecture](https://maddevs.io/blog/cell-based-architecture-vs-microservices/) · [Cloud Native Now — Service Mesh Comeback 2026](https://cloudnativenow.com/contributed-content/why-service-mesh-is-poised-for-a-dramatic-comeback-in-2026/) · [Red Hat — OpenShift Service Mesh 3.2 with ambient mode](https://www.redhat.com/en/blog/introducing-openshift-service-mesh-32-istios-ambient-mode) · [Istio dataplane modes](https://istio.io/latest/docs/overview/dataplane-modes/) · [Manning — Microservices Patterns 2nd Edition](https://www.manning.com/books/microservices-patterns-second-edition) · [microservices.io — MEAP announcement](https://microservices.io/post/architecture/2025/06/26/announcing-meap-microservices-patterns-2nd-edition.html) · [ArchUnit (TNG)](https://github.com/TNG/archUnit) · [jMolecules](https://github.com/xmolecules/jmolecules) · [plus8soft — Virtual Threads vs WebFlux 2026](https://plus8soft.com/blog/virtual-threads-vs-webflux/) · [Java Code Geeks — Virtual Threads: Blocking Code Is Cool Again](https://www.javacodegeeks.com/2026/02/project-looms-virtual-threads-why-blocking-code-is-cool-again.html)
