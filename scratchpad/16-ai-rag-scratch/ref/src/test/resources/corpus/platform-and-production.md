# Platform and Production (research excerpt)

> Frozen excerpt of docs/research/platform-and-production.md, copied into src/test/resources so the checkpoint tests
> have a corpus that never changes. The running application reads the real files.

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

