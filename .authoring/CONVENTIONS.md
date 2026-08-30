# Authoring conventions for J-Learning mini-projects

These rules keep all 22 projects consistent. Follow them when generating or updating a lesson.

## Project layout

- Each project is a **standalone Maven project** at the repo root: `NN-slug/`. No parent
  aggregator POM, no cross-project dependencies. A learner must be able to open one directory
  in isolation.
- Directory contents:
  - `README.md` — the lesson (structure below)
  - `pom.xml` + `src/main/java`, `src/test/java`, `src/main/resources`
  - `docker-compose.yml` only when infra cannot be Testcontainers-managed (e.g. an
    observability stack the learner browses)
  - Optional `docs/` for lesson-specific diagrams or handouts (e.g. event-storming output)

## Toolchain & version pins (verified August 2026)

- **Java 25**: `<maven.compiler.release>25</maven.compiler.release>`. Preview features
  (`--enable-preview`) only where the lesson explicitly teaches them (structured concurrency).
- **Spring Boot**: parent `org.springframework.boot:spring-boot-starter-parent:4.1.1`.
  Boot 4 has modularized starters (web MVC starter is `spring-boot-starter-webmvc`) — verify
  starter artifact IDs against what actually resolves.
- Pins for non-Boot-managed libraries:
  - Testcontainers **2.0.5** — verified: Boot 4.1.1's imported `testcontainers-bom` manages it
    natively; Postgres module artifact is `org.testcontainers:testcontainers-postgresql` with class
    `org.testcontainers.postgresql.PostgreSQLContainer` (2.x renamed artifacts/packages; no longer
    generic). The singleton-container pattern (static start + `@DynamicPropertySource`) needs no
    `testcontainers-junit-jupiter`. Prefer image `postgres:16-alpine` (cached on this machine).
    Boot 4 splits Flyway support into `spring-boot-starter-flyway` (+ `flyway-database-postgresql`).
  - ArchUnit **1.5.0** (`archunit-junit5`)
  - jMolecules **1.9.0** / current jMolecules BOM + `jmolecules-archunit`
  - Spring Modulith **2.1.x** (BOM `spring-modulith-bom`)
  - Cucumber-JVM **7.34.x** (JUnit Platform Suite engine)
  - PIT **1.19.6+** (verified: 1.19.1 fails on Java 25 class files with "Unsupported class file
    major version 69") + `pitest-junit5-plugin` 1.2.3 (verified compatible with JUnit 6)
  - CloudEvents SDK **4.0.2**, Springwolf **1.x**, Temporal SDK **1.38.0**, Debezium **3.6**
- JUnit, AssertJ, Mockito, Spring Kafka: use Boot-managed versions in Spring projects.
  Plain-Java projects: JUnit BOM (6.1.x line), AssertJ 3.27.x.
- **Boot 4 modularized starters (verified):** Kafka auto-configuration comes from
  `spring-boot-starter-kafka`; a bare `spring-kafka` dependency compiles but auto-configures
  nothing. Same pattern for Flyway (`spring-boot-starter-flyway`) — with only `flyway-core`,
  migrations silently never run. Assume any Boot 3 auto-config may now need its own starter.
- **WireMock 3.13.1 (verified):** the config methods are `http2PlainDisabled(boolean)` /
  `http2TlsDisabled(boolean)`. There is no `http2PlaintextEnabled`. Disable h2c with
  `options().dynamicPort().http2PlainDisabled(true)` — the JDK HttpClient's h2c upgrade otherwise
  trips over WireMock's Jetty.
- **Pact JVM on JUnit 6 (partially verified):** the JUnit 5 extension does load under JUnit 6, but
  it requires either V4 method signatures or an explicitly declared V3 pact version, and the
  test methods must be `public`. Confirm end-to-end before relying on it.
- **Boot 4 modularization — the full list confirmed empirically.** Auto-configuration you took for
  granted in Boot 3 now lives in its own module, and omitting it fails *silently* rather than loudly:
  - `RestClient.Builder` → `spring-boot-starter-restclient`
  - a real `Tracer` (not the no-op) → `spring-boot-starter-opentelemetry`. Hand-picking
    `micrometer-tracing-bridge-otel` + `spring-boot-micrometer-tracing` + `spring-boot-opentelemetry`
    is NOT enough: the OTel bridge autoconfig lives in `spring-boot-micrometer-tracing-opentelemetry`,
    which only the starter pulls. Symptom: empty-string trace ids.
  - Kafka auto-config → `spring-boot-starter-kafka`; Flyway → `spring-boot-starter-flyway`
  - MockMvc test support → `spring-boot-starter-webmvc-test`
  Rule of thumb: if a bean is missing or a feature silently no-ops, look for a module before
  debugging your own code.
- **HTTP client timeouts are `spring.http.clients.*` — PLURAL (verified).** Boot 4.1 also accepts
  `spring.http.client.*` (singular) and it has NO effect on the auto-configured `RestClient.Builder`.
  Always assert a timeout in a test rather than trusting the property name.
- **Pact JVM 4.6.21 on JUnit 6 / JDK 25 (verified working).** Recipe: `V4Pact` return types +
  `toPact(V4Pact.class)`, `@PactTestFor(pactVersion = PactSpecVersion.V4)`, `public` `@Pact` and
  `@Test` methods. Declare request headers explicitly BEFORE `.body(...)` — Pact's DSL defaults to
  `application/json; charset=UTF-8` while Spring's `RestClient` sends bare `application/json`,
  producing a confusing `PartialRequestMatch`.
- **Stateful test doubles need explicit reset.** Spring caches contexts across test classes, so a
  singleton `CircuitBreaker` (or any stateful resilience bean) tripped by one test poisons every
  later test with zero downstream calls. Reset such beans in `@BeforeEach` in the shared test base.
- **Spring AI 2.0.x (verified).** BOM `org.springframework.ai:spring-ai-bom:2.0.1`. Artifacts renamed
  from 1.x: the vector-store advisor is `spring-ai-vector-store-advisor` (NOT
  `spring-ai-advisors-vector-store`). Model options are read through `getOptions()` —
  `getDefaultOptions()` is deprecated and never consulted, and overriding the wrong one makes
  `ToolCallingAdvisor` silently skip the whole tool loop while `hasToolCalls()` still returns true.
  `ChatClient` auto-registers `ToolCallingAdvisor` in 2.0, so a one-method fake `ChatModel` that
  returns tool calls exercises tool calling end-to-end.
- **pgvector in Testcontainers (verified):** image `pgvector/pgvector:pg17` (cached locally) needs
  `.asCompatibleSubstituteFor("postgres")` to be accepted by the Postgres module.
- **JFR on JDK 25, programmatic (verified working).** `new Recording()` in try-with-resources;
  `enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace()` and
  `enable("jdk.ObjectAllocationSample").with("throttle", "500/s").withStackTrace()`; `start()` →
  drive load → `stop()` → `dump(path)`; parse with `jdk.jfr.consumer.RecordingFile` and walk
  `event.getStackTrace().getFrames()`. Guard with `event.hasField("duration")`.
  `jdk.ExecutionSample` / `jdk.ThreadPark` exist but yield ~1 event in sub-second runs — too sparse
  to assert on.
- **Micrometer percentiles (verified):** `publishPercentileHistogram()` materialises no bucket ladder
  on `SimpleMeterRegistry` (only Prometheus-style registries), so tests must assert against explicit
  `serviceLevelObjectives` boundaries instead.
- **Hikari metric names (verified):** the pool-size gauge is **`jdbc.connections.max`** (Boot's
  metadata binder, present before the pool opens), NOT `hikaricp.connections.max`. The
  `hikaricp.connections.{pending,acquire,usage,timeout}` names are right but only appear once the
  lazy pool actually starts.
- **`spring.jdbc.template.query-timeout` is truncated to whole seconds** (it maps to
  `Statement.setQueryTimeout(int)`), so `300ms` silently means "no timeout". For sub-second statement
  timeouts on Postgres use `spring.datasource.hikari.connection-init-sql=SET statement_timeout = '300ms'`.
- **Keycloak in Testcontainers (verified, image `quay.io/keycloak/keycloak:26.4`).** Use
  `GenericContainer` with `start-dev --import-realm` and realm JSONs copied to
  `/opt/keycloak/data/import`. Three traps, each of which only surfaces as "Wait strategy failed":
  - The file MUST be named `<realm>-realm.json` and the inner realm name must match, or the
    container exits 1 with "File name / realm name mismatch".
  - A user without `firstName`/`lastName` cannot use the direct access grant: Keycloak's
    `VERIFY_PROFILE` action returns `invalid_grant / "Account is not fully set up"`.
  - Wait on `/realms/<realm>/.well-known/openid-configuration`, which proves the import finished
    rather than merely that the port is open.
- **Spring Security `JwtTimestampValidator` allows 60 s of clock skew by default (verified).** A
  token that expired seconds ago is therefore still accepted by a stock resource server. Tighten the
  skew explicitly if you want short-lived tokens to mean anything.
- **Kafka share groups on `apache/kafka:4.2.0` (verified working; three non-obvious requirements).**
  1. Broker env `KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR=1` (+ `MIN_ISR=1`), or every
     share call times out on a single-broker container. No feature flag — share groups are GA in 4.2.
  2. Client `share.acknowledgement.mode=explicit`, or `acknowledge()` throws
     `IllegalStateException: Implicit acknowledgement of delivery is being used`.
  3. Start position is a GROUP config, not a client one: `Admin.incrementalAlterConfigs` on
     `ConfigResource(Type.GROUP, id)` with `share.auto.offset.reset=earliest`. The client's
     `auto.offset.reset` is ignored.
  Measured behaviour worth knowing: share-group fairness is per-fetch, so with a large pre-existing
  backlog one member can acquire most of it and leave others idle (36/72/12/0 across four members).
  Fairness shows up when work *arrives* rather than when it is already queued.
- **Testcontainers 2.0.5 messaging modules (verified):** `testcontainers-kafka` →
  `org.testcontainers.kafka.KafkaContainer`; `testcontainers-rabbitmq` →
  `org.testcontainers.rabbitmq.RabbitMQContainer` (use `getAmqpUrl()`).
- **Jackson 3 is NOT transitive everywhere in Boot 4.** With only `spring-boot-starter-kafka` on the
  classpath you must declare `tools.jackson.core:jackson-databind` explicitly.
- **Jackson 3 in Boot 4 (verified):** Boot 4 ships Jackson 3 under the `tools.jackson` package
  namespace (not `com.fasterxml.jackson`); in tests prefer JsonPath or records over raw
  ObjectMapper usage, and check imports carefully.
- **Spring Modulith (verified):** BOM 2.1.0 resolves; the event republish property is
  `spring.modulith.events.republish-outstanding-events-on-restart`; the registry table is
  `EVENT_PUBLICATION`; `@ApplicationModuleListener` needs no `@EnableAsync`.
- **Mockito on JDK 25 (verified):** load `mockito-core` as a `-javaagent` through the Surefire
  `argLine` to avoid the dynamic-agent-loading warning/deprecation.
- **Boot 4 modularized test support (verified on this machine):** MockMvc support lives in the
  separate starter `spring-boot-starter-webmvc-test`, and `AutoConfigureMockMvc` moved to
  `org.springframework.boot.webmvc.test.autoconfigure` (the Boot 3 package
  `org.springframework.boot.test.autoconfigure.web.servlet` no longer exists). Expect the same
  modularization pattern for other test slices (data-jpa test etc.) — check packages, don't assume.
- **Spring's exception translation for concurrency SQLSTATEs (verified).** `40001`
  (serialization_failure), `40P01` (deadlock_detected) and `55P03` (lock_not_available) all arrive
  as `org.springframework.dao.CannotAcquireLockException`; the SQLSTATE is only recoverable from
  the root `PSQLException`, so tests should assert on the root-cause message
  (`could not serialize access`, `deadlock detected`, `could not obtain lock on row`). Catch
  `ConcurrencyFailureException` (or `OptimisticLockingFailureException` for `@Version`) in retry
  code rather than the leaf types.
- **A serialisation failure is thrown by the COMMIT, not by your method (verified).** At
  SERIALIZABLE the message is literally `JDBC commit; ERROR: could not serialize access due to
  read/write dependencies among transactions`. Same for JPA's `@Version` check, which fires at
  flush. Any retry must therefore wrap the whole transactional call from *outside* the proxy — a
  `try/catch` inside the `@Transactional` method can never see it.
- **Connection-pool exhaustion surfaces as `CannotCreateTransactionException` (verified)**, not
  `CannotGetJdbcConnectionException`: the transaction manager fails while opening the transaction.
  The useful text is two levels down in `java.sql.SQLTransientConnectionException`:
  `HikariPool-1 - Connection is not available, request timed out after 2011ms (total=4, active=4,
  idle=0, waiting=5)`.
- **`Propagation.NESTED` works on both `DataSourceTransactionManager` and `JpaTransactionManager`
  under Boot 4.1.1 (verified).** Savepoints are available in both; the widely repeated "JPA cannot
  do NESTED" does not describe this stack.
- **JPA pessimistic locking on Postgres (verified).** `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a
  Spring Data `@Query` method emits `FOR UPDATE`. Adding
  `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))` emits
  `FOR UPDATE NOWAIT` and fails immediately with SQLSTATE `55P03`. Non-zero timeouts have no
  Postgres syntax to map onto — use a session-level `lock_timeout` for those.
- **`SELECT … ORDER BY id LIMIT 1 FOR UPDATE` does NOT return an empty result** after waiting on a
  row that stopped qualifying (verified): Postgres re-evaluates and moves on to the next matching
  row. The plain-`FOR UPDATE` problem in a queue table is convoying/latency, not lost work — the
  opposite is widely repeated, so check before teaching it.
- **`ddl-auto=validate` over a hand-written `schema.sql` (verified)** works with
  `spring.sql.init.mode=always` and `spring.jpa.defer-datasource-initialization=false`: the script
  runs before the EntityManagerFactory is built, so a mapping error fails the context at startup.
- **Postgres interval and array parameters through `JdbcClient` (verified):** use
  `make_interval(secs => :seconds)` rather than concatenating an interval literal, and
  `WHERE id = ANY (:ids)` with a `Long[]` rather than building an `IN (…)` list.
- **AssertJ `singleElement()` returns an `ObjectAssert`**, which has no `hasMessageContaining` /
  `rootCause`. For assertions on a collected exception use
  `assertThat(list).hasSize(1); assertThat(list.getFirst())…` instead.
- **Deterministic concurrency in lessons.** Projects 20–22 use a main-code `Interleaving` hook (a
  no-op `Runnable` a test can arm), the same idea as `07-events-and-outbox`'s chaos monkey, plus a
  hand-driven two-connection `DbSession` test harness. Prefer these over sleeps and thread pools:
  every anomaly in those lessons happens on every run. Two constraints learned the hard way — a
  barrier armed with N parties needs N connections available in the pool, and a barrier is
  unusable where the code under test *blocks on a lock* (the second party never arrives). For
  those, pause only the first arrival.
- **If a pinned artifact does not resolve**, use the nearest available version, make the build
  green, and record the deviation in a footnote at the bottom of the lesson README and in your
  completion report.

## Lesson README structure

```markdown
# NN — Title
> One-line promise: what you can do after this lesson.

## Why this matters (2026)      ← current-state context, cite docs/research/*.md
## Core concepts                 ← the theory, concise, with small code fragments
## The project                   ← domain intro; what's given; how to run it
## Guided steps                  ← 5–8 checkpoints; each: goal, hints, "done when"
## Self-check                    ← 5–8 questions you should be able to answer
## Stretch goals                 ← 2–4 harder extensions
## Resources                     ← curated links (books, articles, talks) with authors
```

- Guided steps are driven by **checkpoint tests**: pre-written tests in `src/test/java`
  annotated `@Disabled("Checkpoint N — enable when you start step N")`. The learner removes
  the annotation and makes them pass. Steps that can't be test-verified (e.g. run a load test,
  read a trace) state an observable "done when" instead.
- Hints go in `<details><summary>Hint</summary>…</details>` blocks so they don't spoil.

## Scaffold quality bar

- **Pristine state must be green**: `mvn -q test` passes on checkout (checkpoint tests disabled).
  Verify by actually running it before reporting completion.
- Given-code is realistic and compact. Deliberate smells (the refactoring subject) are allowed
  and encouraged where the lesson calls for them — flag them in the lesson text, not with
  code comments.
- Keep scaffolds small: the learner writes the interesting code. Given-code supplies the boring
  context, the test harness, and the refactoring subject.
- Prefer Testcontainers over docker-compose; prefer H2 only in lessons where the database is
  irrelevant to the topic.
- No Lombok. Records and modern Java remove most of the need, and the training targets plain
  modern idiom.

## Environment note (August 2026, this machine)

Registry pulls WORK again (the earlier hang cleared without a restart — re-test before assuming).
Images loaded locally: `postgres:16-alpine`, `postgres:17-alpine`, `apache/kafka:4.1.0`,
`apache/kafka:4.2.0` (share groups GA — needed for Kafka queue semantics),
`quay.io/keycloak/keycloak:26.4`, `rabbitmq:3-management-alpine`, `pgvector/pgvector:pg17`,
`testcontainers/ryuk:0.14.0`. Prefer these. If a pull stalls again, fetch via the registry API
host-side and `docker load`, or make that step a documented manual exercise.

**Testcontainers 2.x generic containers (verified):** `org.testcontainers:testcontainers:2.0.5`
(NOT `testcontainers-core`) provides `org.testcontainers.containers.GenericContainer` — that package
did not move in 2.x, unlike the per-technology modules. Use `GenericContainer` for Keycloak rather
than the community `dasniko/testcontainers-keycloak` module, which targets Testcontainers 1.x.

## Tone

Lessons are written for an experienced Java developer refreshing on modern practice — direct,
technical, no filler. Explain *why* a pattern exists and when NOT to use it; the research
reports contain honest criticism sections — carry that balance into lessons.
