# Modern Java Platform & Production-Readiness — Research Findings (verified August 2026)

All version numbers and release dates verified via web search in August 2026. Structured for curriculum design: each section gives context, current state, canonical resources, and a hands-on exercise idea.

---

## 1. The Java Language & Platform Now

### 1a. Java 25 LTS (the current teaching baseline)

**Context.** Java 25 is the current LTS, released September 16, 2025, succeeding Java 21 LTS. It's the version production teams are converging on in 2026 and should be the curriculum baseline. Java 26 (non-LTS) shipped March 17, 2026; Java 27 lands September 2026.

**Current state.** Java 25 shipped 18 JEPs. The ones that matter for a backend curriculum:
- **Language:** Flexible constructor bodies (JEP 513), compact source files & instance `main` methods (JEP 512 — great for teaching), module import declarations (JEP 511), primitive types in patterns (JEP 507, preview).
- **Concurrency:** Scoped Values finalized (JEP 506) — the modern replacement for ThreadLocal in virtual-thread code; Structured Concurrency 5th preview.
- **Runtime/performance:** AOT method profiling (JEP 515, Project Leyden), compact object headers (JEP 519 — real memory wins), Stable Values (JEP 502, preview), generational ZGC as default.
- Java 26 added: HTTP/3 in the HTTP Client API (final), AOT object caching with any GC (JEP 516), G1 throughput improvements (~15% in some workloads, JEP 522), 4th preview of primitive patterns.

**Resources.**
- [OpenJDK JDK 25 project page](https://openjdk.org/projects/jdk/25/) — authoritative JEP list
- [HappyCoders Java features guides](https://www.happycoders.eu/java/) — Sven Woltmann's per-release deep dives
- [Inside.java](https://inside.java) — Oracle Java team (Nicolai Parlog, José Paumard, Billy Korando); the JEP Café video series is excellent for concurrency topics
- *The Well-Grounded Java Developer, 2nd ed.* — Ben Evans, Jason Clark, Martijn Verburg (Manning)
- [Keyhole Software: What's New in Java 25](https://keyholesoftware.com/java-25-whats-new/)

**Exercise idea.** "Time-travel refactoring": take a small service written in Java 8/11 idioms (anonymous classes, getters/setters DTOs, instanceof-cast chains, ThreadLocal context) and modernize it on JDK 25 — records, sealed interfaces + exhaustive switch, scoped values — then measure heap with/without compact object headers (`-XX:+UseCompactObjectHeaders`).

### 1b. Virtual threads (Loom) in practice

**Context.** Virtual threads (final since Java 21, JEP 444) give thread-per-request scalability without reactive rewrites.

**Current state (2026).** Adoption is mainstream and "boring in the best way." The big blocker — `synchronized` pinning carriers — was fixed by **JEP 491 in Java 24**, so Java 25 LTS is the first LTS where virtual threads work without the classic footguns. Spring Boot enables them with one property (`spring.threads.virtual.enabled=true`); on JDK 24+, defaulting new Spring Boot 4 services to virtual threads is considered reasonable. Remaining caveats worth teaching: JNI frames and class initializers can still pin; `ThreadLocal` misuse (use Scoped Values); pool-based assumptions (e.g., sizing DB connection pools becomes the real bottleneck); MDC/trace-ID propagation quirks.

**Resources.**
- [JEP 444](https://openjdk.org/jeps/444) and [JEP 491](https://openjdk.org/jeps/491) — the primary specs
- [InfoQ: Virtual Threads after JDK 24 — What Changed for Production Java](https://www.infoq.com/articles/virtual-threads-after-jdk24/)
- [Dan Vega: JDK 24's Major Improvement — Virtual Threads Without Pinning](https://www.danvega.dev/blog/jdk-24-virtual-threads-without-pinning)
- [Mike Kowalski: Java 24 — thread pinning revisited](https://mikemybytes.com/2025/04/09/java24-thread-pinning-revisited/)

**Exercise idea.** Build a "slow aggregator" endpoint calling three fake downstream services (each sleeping 300 ms). Run it with a fixed platform-thread pool vs. virtual threads under load (`hey`/`wrk` or Gatling), observe throughput collapse vs. scale; then artificially shrink the DB/HTTP connection pool and watch the bottleneck move — teaching that Loom removes thread scarcity, not resource scarcity.

### 1c. Structured concurrency & what's coming

**Current state.** Structured concurrency is **still preview**: 6th preview in Java 26 (JEP 525), 7th preview targeted at JDK 27 (JEP 533, refining exception handling and Joiner API). Finalization expected in JDK 27 or shortly after — teach it as "the near-future standard pattern," usable today with `--enable-preview`. Scoped Values are already final (Java 25).

**Valhalla:** JEP 401 (Value Classes and Objects) is integrated as a **preview in JDK 28** (due March 2027). It introduces identity-free classes, changes `==` semantics for value objects, and enables heap flattening. For a 2026 curriculum: one awareness slide, not hands-on.

**Records/sealed/pattern matching** are settled idiom now — the "data-oriented programming" style (records as data, sealed interfaces as closed sums, exhaustive switch as operations) is the canonical modern Java design approach; Brian Goetz's "Data-Oriented Programming in Java" (InfoQ) and Nicolai Parlog's follow-ups on inside.java are the reference articles.

**Exercise idea.** Model a payment-processing domain as a sealed hierarchy of result types (`Approved`/`Declined`/`Fraud`/`Retryable`) with records and exhaustive switch, then fan out validation + fraud-check + limit-check with `StructuredTaskScope` (preview enabled), demonstrating automatic cancellation when one subtask fails.

---

## 2. Spring Boot 4 / Spring Framework 7

**Current state.** Spring Framework 7.0 and Spring Boot 4.0 went **GA November 2025**. **Spring Boot 4.1.0 shipped June 10, 2026** (4.1.1 on Aug 20, 2026) and is the recommended target for new projects; 4.2 is expected November 2026. Key changes:
- **Null safety via JSpecify**: JSpecify annotations across the whole portfolio; Spring's own `@Nullable`/`@NonNullApi` deprecated. Works with NullAway/IntelliJ for compile-time null checking — a genuinely teachable engineering practice.
- **First-class API versioning**: version attribute directly in `@RequestMapping`, negotiated via header/param/path.
- **Built-in resilience**: `@Retryable`, `@ConcurrencyLimit`, `RetryTemplate` in core (see §5).
- **HTTP interface clients**: declarative `@HttpExchange` interfaces (RestClient-backed) are the standard way to consume APIs — `RestTemplate` is legacy; Boot 4 adds auto-configured client registration.
- **Baselines**: Java 17 minimum (Java 25 fully supported), Jakarta EE 11, Jackson 3, modularized starter jars (e.g., separate `spring-boot-starter-webmvc`).
- Boot 4.1: gRPC auto-configuration, SSRF mitigation in HTTP clients, Kotlin 2.3.
- **Migration from 3.x matters commercially**: OSS support for Boot 3.5 runs out mid-2026-ish, so 3.x→4.x migration (OpenRewrite-assisted) is a real-world skill worth an exercise.

**Resources.**
- [InfoQ: The Spring Team on Spring Framework 7 and Spring Boot 4](https://www.infoq.com/articles/spring-team-spring-7-boot-4/)
- [Dan Vega: Spring Boot 4 is here](https://www.danvega.dev/blog/spring-boot-4-is-here)
- [Loiane Groner: Spring Boot 4 & Spring Framework 7 key features](https://loiane.com/2025/08/spring-boot-4-spring-framework-7-key-features/)
- [Spring Boot 4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- Josh Long's "Bootiful Spring Boot" series (spring.io / YouTube)

**Exercise idea.** Build a small "product catalog" service on Boot 4.1: expose `/api/products` with two API versions using native versioning; consume an external mock service through an `@HttpExchange` interface; turn on JSpecify + NullAway in the build and fix the resulting null-safety errors. Optional second lab: migrate a provided Boot 3.5 app to 4.1 with OpenRewrite recipes.

---

## 3. Alternative Stacks & GraalVM Native Image

**Current state.**
- **Quarkus**: current release **3.37.x** (July 2026); LTS releases every 6 months — current recommended LTS is **3.33** (March 2026). Mature, fast-moving, strong dev-mode ("dev services" spin up Testcontainers-backed dependencies automatically).
- **Micronaut**: **4.10.x** / 5.0.x (5.0 GA May 2026). Best-in-class cold start and Spring-like ergonomics; popular for serverless.
- **Helidon**: **4.x** (Java 21+ required). Notable as the only server built ground-up on virtual threads (Níma) — pedagogically valuable for showing "blocking code that scales."
- **GraalVM**: **GraalVM 25** (aligned with JDK 25). Native image adoption is real but selective: chosen for scale-to-zero/serverless/CLI, while Project Leyden's AOT cache now covers much of the "fast JVM startup" middle ground without native image's closed-world constraints.

**Resources.**
- [Quarkus guides](https://quarkus.io/guides/) — official, exercise-shaped; *Quarkus in Action* — Martin Štefanko & Jan Martiška (Manning)
- [Micronaut guides](https://guides.micronaut.io/)
- [Helidon documentation](https://helidon.io/docs)
- [GraalVM Native Image docs](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Java Code Geeks: Helidon 4 vs Quarkus 3 vs Micronaut 4 with virtual threads](https://www.javacodegeeks.com/2026/03/helidon-4-vs-quarkus-3-vs-micronaut-4-which-framework-actually-winswith-virtual-threads.html)

**Exercise idea.** "Same service, three ways": implement one tiny REST+DB endpoint in Spring Boot and Quarkus (Micronaut optional); compile the Quarkus one to a native image (Mandrel container build) and compare startup time, RSS memory, and image size in a table.

---

## 4. Persistence: Modern Practice

**Current state.**
- **Hibernate ORM 7.0** (GA May 2025; 7.x line current): implements Jakarta Persistence 3.2, plus a complete implementation of **Jakarta Data 1.0** — the new standard, compile-time-checked repository spec. Hibernate Data Repositories is the production-ready implementation.
- **Spring Data 2025.0/2025.1**: 2025.1 GA (Nov 2025, ships with Boot 4) — baselines to Jakarta EE 11, Hibernate ORM 7, Hibernate Validator 9.
- **jOOQ**: 3.20/3.21 current — type-safe SQL-first alternative; Lukas Eder's blog remains the best "think in SQL" education.
- **Migrations**: **Flyway 12.x** (12.7, May 2026) and **Liquibase 5.0.x** (May 2026). Schema-migration-as-code is now assumed baseline practice, paired with **Testcontainers** for real-database integration tests.

**Resources.**
- *High-Performance Java Persistence* — Vlad Mihalcea (book + blog vladmihalcea.com); the canonical performance reference
- [Thorben Janssen's blog](https://thorben-janssen.com) — approachable Hibernate/JPA tutorials, covers Hibernate 7 and Jakarta Data
- [Hibernate 7 announcement — In Relation To](https://in.relation.to/2025/05/20/hibernate-orm-seven/)
- [jOOQ blog](https://blog.jooq.org) — Lukas Eder
- [Bytebase: Flyway vs Liquibase 2026 comparison](https://www.bytebase.com/blog/flyway-vs-liquibase/)

**Exercise idea.** "Repository three ways + N+1 hunt": same `Order`/`OrderLine` schema managed by Flyway migrations, accessed via (a) Spring Data JPA repository, (b) Jakarta Data repository on Hibernate 7, (c) a jOOQ query — all tested against real PostgreSQL via Testcontainers. Then enable SQL logging, find and fix a planted N+1 problem with a fetch join/entity graph.

---

## 5. Production Concerns: Observability, Resilience, Security

### Observability

**Current state.** OpenTelemetry has won the wire-format/standards war; the Java question is only *how* you produce OTel data. Two mainstream paths, both current:
1. **Micrometer-native** (Spring's default): Micrometer Observation API in Framework 7 core, **Micrometer 1.16** + Micrometer Tracing, exporting OTLP. Spring Boot 4 documents OTel as a first-class story.
2. **OTel Java agent / Spring Boot starter** (zero-code): opentelemetry-java-instrumentation **2.x** line, now with declarative YAML configuration; the agent picks up Micrometer metrics automatically.
Logs + traces + metrics correlated through trace IDs (with the known virtual-threads MDC-propagation gotcha worth teaching).

**Resources.**
- [spring.io: OpenTelemetry with Spring Boot (Nov 2025)](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)
- *Learning OpenTelemetry* — Ted Young & Austin Parker (O'Reilly)
- [OpenTelemetry Java docs](https://opentelemetry.io/docs/languages/java/)
- [Nicolas Fränkel: OpenTelemetry tracing on Spring Boot — agent vs Micrometer Tracing](https://blog.frankel.ch/opentelemetry-tracing-spring-boot/)

**Exercise idea.** Wire two small services (A calls B calls Postgres) into a docker-compose stack with an OTel Collector + Grafana LGTM image (`grafana/otel-lgtm`); first via Micrometer/OTLP, then swap to the zero-code Java agent — follow one request's trace across services, find the slow span, and correlate it to a log line by trace ID.

### Resilience

**Current state.** Big 2026 shift: **Spring Framework 7 has resilience in core** — `@Retryable`, `@ConcurrencyLimit`, `RetryTemplate` (superseding the separate spring-retry project for basics). **Resilience4j 2.x remains the tool for circuit breakers, rate limiters, and bulkheads** — Spring's built-ins are deliberately a subset, not a replacement. Teach: timeouts first, then retry (with jitter/budgets), then circuit breaker, and the interplay with virtual threads.

**Resources.**
- [spring.io: Core Spring Resilience Features](https://spring.io/blog/2025/09/09/core-spring-resilience-features/)
- [Spring Framework reference: Resilience Features](https://docs.spring.io/spring-framework/reference/core/resilience.html)
- [Resilience4j official docs](https://resilience4j.readme.io)
- *Release It!, 2nd ed.* — Michael Nygard (the conceptual canon for stability patterns)

**Exercise idea.** Provide a flaky downstream stub (10% errors, occasional 5 s hangs). Add, in order: client timeout, `@Retryable` with backoff, `@ConcurrencyLimit`, then a Resilience4j circuit breaker — running a load test after each step and graphing p99 latency and error rate to see each pattern's distinct effect (including the classic "retries made it worse" moment).

### API security

**Current state.** **Spring Security 7** (with Boot 4): lambda DSL only (chained `.and()` removed), removed OAuth2 password grant (OAuth 2.1 alignment), `PathPatternRequestMatcher` replaces Ant/Mvc matchers; headline features are **first-class multi-factor authentication** and mature **passkeys/WebAuthn** and one-time-token login. Standard practice for APIs: resource server with JWT validation against an external IdP (Keycloak/Auth0/Entra), authorization code + PKCE for user flows, client credentials for service-to-service.

**Resources.**
- [Spring Security reference docs](https://docs.spring.io/spring-security/reference/)
- *Spring Security in Action, 2nd ed.* — Laurențiu Spilcă (Manning)
- [spring.io: Multi-Factor Authentication in Spring Security 7](https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7/)
- [Dan Vega: Spring Security 7 MFA tutorial](https://www.danvega.dev/blog/spring-security-7-multi-factor-authentication)
- *OAuth 2.0 Simplified* / oauth.net materials — Aaron Parecki

**Exercise idea.** Stand up Keycloak in Docker, protect the product-catalog API as an OAuth2 resource server (JWT with roles→authorities mapping), call it service-to-service with client credentials via an HTTP interface client, and write security tests. Stretch: add passkey login to a small MVC frontend.

---

## 6. Cloud-Native Packaging & Startup Optimization

**Current state.**
- **Buildpacks**: Spring Boot's `bootBuildImage` with **Paketo buildpacks** remains the no-Dockerfile default. Layered jars, SBOMs, and non-root images come free. Jib and plain multi-stage Dockerfiles are the alternatives.
- **Startup optimization is now a decision tree**:
  - **Project Leyden AOT cache** — JEP 483 (Java 24), ergonomics in JEP 514/515 (Java 25), any-GC object caching in JEP 516 (Java 26). Training-run → AOT cache → ~2-4x faster startup with full JVM fidelity. Spring Boot supports it out of the box; this is the new default recommendation.
  - **GraalVM Native Image** — best memory density and cold start (tens of ms), at the cost of closed-world constraints and build times.
  - **CRaC** (checkpoint/restore) — millisecond restore *with warmed-up JIT*; supported by Boot, Azul/BellSoft JDKs.
  - Rule of thumb: fidelity → AOT cache; density/serverless → native image; warm first request → CRaC. (AppCDS effectively subsumed by the AOT cache.)
- **Kubernetes practices**: Actuator liveness/readiness probes, graceful shutdown, container-aware JVM defaults, resource-limit-aligned heap sizing.

**Resources.**
- [BellSoft: A guide to using buildpacks with Spring Boot apps in 2026](https://bell-sw.com/blog/how-to-use-buildpacks-with-spring-boot/)
- [Piotr Mińkowski: Speed up Java startup with Spring Boot and Project Leyden](https://piotrminkowski.com/2026/03/19/speed-up-java-startup-with-spring-boot-and-project-leyden/)
- [Ralph Schaer: Faster Spring Boot startup with CRaC, Leyden, and Spring AOT](https://blog.rasc.ch/2026/04/spring-boot-startup.html) — direct three-way comparison
- *Cloud Native Spring in Action* — Thomas Vitale (Manning)

**Exercise idea.** "Startup shootout": containerize the same Boot app with `bootBuildImage`, then produce (a) plain JVM, (b) Leyden AOT-cache, and (c) optional native image variants; deploy to a local kind/k3d cluster with readiness probes and measure time-to-ready and RSS for each.

---

## 7. AI in Java Backends

**Current state — yes, this is now a mainstream backend skill**, roughly where "REST client integration" was a decade ago: not every service needs it, but every backend team is expected to have it.
- **Spring AI**: 1.0 GA May 2025; **1.1** (Nov 2025, maintained for Boot 3.x); **2.0.0 GA June 12, 2026** — built on Boot 4/Framework 7, JSpecify, Jackson 3; unified/composable tool calling via `ToolCallingAdvisor`, progressive tool discovery, self-correcting structured output, full MCP (Model Context Protocol) support, 20+ model backends, vector-store abstraction.
- **LangChain4j**: 1.0 GA May 2025, monthly releases; **1.17/1.18 by mid-2026** — agentic patterns, human-in-the-loop suspend/resume, MCP support. Framework-agnostic (works with Quarkus via its excellent extension, Micronaut, plain Java).
- Both support MCP, which is itself becoming a teachable primitive (Java services as MCP servers/clients).

**Resources.**
- [Spring AI reference documentation](https://docs.spring.io/spring-ai/reference/)
- *Spring AI in Action* — Craig Walls (Manning)
- [LangChain4j documentation](https://docs.langchain4j.dev)
- [spring.io: Spring AI 2.0.0 GA announcement](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/)
- Dan Vega's and Thomas Vitale's Spring AI talks/tutorials
- [Java Code Geeks: Choosing a Java LLM integration strategy in 2026](https://www.javacodegeeks.com/2026/03/choosing-a-java-llm-integration-strategy-in-2026-spring-ai-1-1-vs-langchain4j-vs-direct-api-calls.html)

**Exercise idea.** "RAG over your own docs": Spring AI 2.0 service that ingests the training material itself (markdown → embeddings → pgvector via Testcontainers), exposes a `/ask` endpoint with a `ChatClient` + retrieval advisor, and adds one `@Tool`-annotated method so you see tool calling end-to-end. Stretch: expose the same tool as an MCP server and connect it to a desktop MCP client.

---

## Cross-cutting curriculum observations

- **Version anchor for 2026 training**: Java 25 LTS + Spring Boot 4.1 + Spring Framework 7 + Hibernate 7 + Testcontainers is the coherent "current stack." Quarkus 3.33 LTS for the alternative-stack module.
- **Highest-leverage "new since most devs last looked" items**: JEP 491 unpinning virtual threads, Spring's core resilience annotations, JSpecify null safety, native API versioning, Leyden AOT cache, Jakarta Data, and Spring AI 2.0/MCP.
- **Still-preview, teach-as-outlook**: structured concurrency (final ~JDK 27), Valhalla value classes (preview JDK 28, March 2027).
- Migration skills (Boot 3→4, Security 6→7 DSL removal, OpenRewrite) are commercially valuable curriculum content since 3.x OSS support windows are closing.

## Sources

[OpenJDK JDK 25](https://openjdk.org/projects/jdk/25/) · [OpenJDK JDK 26](https://openjdk.org/projects/jdk/26/) · [Keyhole: Java 25](https://keyholesoftware.com/java-25-whats-new/) · [InfoQ: Virtual Threads after JDK 24](https://www.infoq.com/articles/virtual-threads-after-jdk24/) · [JEP 491](https://openjdk.org/jeps/491) · [Dan Vega: virtual threads without pinning](https://www.danvega.dev/blog/jdk-24-virtual-threads-without-pinning) · [Inside.java: JEP 533 → JDK 27](https://inside.java/2026/05/11/jep533-target-jdk27/) · [InfoQ: JEP 401 preview](https://www.infoq.com/news/2026/08/jep401-value-objects-preview/) · [InfoQ: Spring team on Framework 7/Boot 4](https://www.infoq.com/articles/spring-team-spring-7-boot-4/) · [Dan Vega: Spring Boot 4](https://www.danvega.dev/blog/spring-boot-4-is-here) · [spring.io: Boot 4.1.0](https://spring.io/blog/2026/06/10/spring-boot-4/) · [spring.io: Boot 4.1.1](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/) · [HeroDevs: Quarkus versions July 2026](https://www.herodevs.com/blog-posts/quarkus-versions-eol-dates-and-latest-releases-july-2026) · [Micronaut 5.0.0 release](https://micronaut.io/2026/05/20/micronaut-framework-5-0-0-released/) · [Helidon releases](https://github.com/helidon-io/helidon/releases) · [InfoWorld: GraalVM 25](https://www.infoworld.com/article/4061937/graalvm-25-arrives-backed-by-jdk-25.html) · [In Relation To: Hibernate 7](https://in.relation.to/2025/05/20/hibernate-orm-seven/) · [spring.io: Spring Data 2025.1](https://spring.io/blog/2025/01/24/spring-data-2025/) · [Bytebase: Flyway vs Liquibase 2026](https://www.bytebase.com/blog/flyway-vs-liquibase/) · [spring.io: OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/) · [Frankel: OTel agent vs Micrometer Tracing](https://blog.frankel.ch/opentelemetry-tracing-spring-boot/) · [spring.io: core resilience features](https://spring.io/blog/2025/09/09/core-spring-resilience-features/) · [Spring Framework resilience docs](https://docs.spring.io/spring-framework/reference/core/resilience.html) · [spring.io: MFA in Spring Security 7](https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7/) · [BellSoft: buildpacks 2026](https://bell-sw.com/blog/how-to-use-buildpacks-with-spring-boot/) · [Piotr Mińkowski: Leyden + Spring Boot](https://piotrminkowski.com/2026/03/19/speed-up-java-startup-with-spring-boot-and-project-leyden/) · [rasc.ch: CRaC/Leyden/AOT comparison](https://blog.rasc.ch/2026/04/spring-boot-startup.html) · [spring.io: Spring AI 2.0.0 GA](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/) · [LangChain4j releases](https://github.com/langchain4j/langchain4j/releases) · [JCG: Java LLM strategy 2026](https://www.javacodegeeks.com/2026/03/choosing-a-java-llm-integration-strategy-in-2026-spring-ai-1-1-vs-langchain4j-vs-direct-api-calls.html)
