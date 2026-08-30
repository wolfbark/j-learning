# J-Learning — Modern Java Backend Training

A personal training portfolio of **22 self-contained mini-projects**, each teaching one modern
Java backend architecture style, methodology, or platform capability. The topic list and all
version pins were researched against the ecosystem as of **August 2026**.

**Baseline stack:** Java 25 LTS · Spring Boot 4.1.x / Spring Framework 7 · JUnit 6 ·
Testcontainers 2.x · Docker

## How to work through this

- Every numbered directory is a **standalone Maven project + lesson**. Open one at a time.
  (Two exceptions, by design: `11-microservices/` and `12-testing-strategy/` each contain **two**
  Maven projects, because the lesson is about what happens *between* services.)
- Start with the project's `README.md` — it is the lesson: why the topic matters in 2026,
  the core concepts, guided exercise steps, self-check questions, stretch goals, and curated reading.
- Each scaffold **compiles and tests green as delivered**. Progress is driven by *checkpoint
  tests*: pre-written tests marked `@Disabled("Checkpoint N — …")`. Enable them step by step
  and make them pass.
- The numbering is a suggested order (each track builds on the previous), but tracks are
  reasonably independent — jump around if a topic is hot for you at work.
- Budget roughly **3–6 focused hours per project**.

## Curriculum

### Track A — Language foundations
| # | Project | You will learn |
|---|---------|----------------|
| 01 | `01-modern-java` | Data-oriented programming: records, sealed interfaces, exhaustive `switch` pattern matching, scoped values — by time-travel-refactoring a Java-8-style codebase to Java 25 idiom |
| 02 | `02-tdd` | Canon TDD (red-green-refactor), classicist vs London schools, mutation testing with PIT, and steering AI assistants with tests |

### Track B — Architecture styles
| # | Project | You will learn |
|---|---------|----------------|
| 03 | `03-vertical-slices` | Package-by-feature / vertical slice architecture; refactor a layered app into slices and enforce slice independence with ArchUnit |
| 04 | `04-hexagonal-architecture` | Ports & adapters (with onion/clean compared); swap infrastructure without touching the domain; architecture rules as tests |
| 05 | `05-ddd` | Strategic DDD (bounded contexts, context mapping, event storming) and tactical patterns (aggregates, value objects, domain events) with jMolecules |
| 06 | `06-modular-monolith` | Spring Modulith: enforced module boundaries, module-scoped tests, in-process domain events, generated architecture docs — the 2026 default recommendation |

### Track C — Event-driven systems
| # | Project | You will learn |
|---|---------|----------------|
| 07 | `07-events-and-outbox` | The dual-write problem, transactional outbox, event externalization to Kafka, idempotent consumers |
| 08 | `08-cqrs` | Pragmatic CQRS: command/query split, denormalized read models, projections, and when the pattern earns its complexity |
| 09 | `09-event-sourcing` | Hand-rolled event sourcing (~150 lines demystify it): append-only event store, folds, snapshots, replay, retroactive read models |
| 10 | `10-sagas` | Distributed transactions via sagas: choreography vs orchestration, compensation, durable execution |
| 11 | `11-microservices` | Extracting services, the real cost of distribution: sync REST failures, resilience, async events, distributed tracing; plus self-contained systems as the middle path |
| 18 | `18-messaging-mechanics` | The broker machinery 07–11 skipped: partitions and per-key ordering, hot partitions, consumer-group rebalancing, lag and real backpressure, bounded retries with a DLQ, schema-compatibility gates, Kafka share groups vs a classic group, and a RabbitMQ routing contrast |

### Track D — Quality & collaboration
| # | Project | You will learn |
|---|---------|----------------|
| 12 | `12-testing-strategy` | "Two services, no E2E allowed": Testcontainers 2, consumer-driven contracts (Pact), ArchUnit fitness functions, mutation-score gates |
| 13 | `13-bdd` | BDD as collaboration: example mapping first, Cucumber-JVM second; executable specs as living documentation (and as AI-agent contracts) |

### Track E — Platform & production
| # | Project | You will learn |
|---|---------|----------------|
| 14 | `14-virtual-threads` | Virtual threads after the JDK 24 pinning fix, structured concurrency, scoped values; measuring where the bottleneck actually moves |
| 15 | `15-production-readiness` | Observability (OpenTelemetry + Grafana), resilience patterns in order (timeout → retry → circuit breaker), startup optimization decision tree |
| 16 | `16-ai-backend` *(elective)* | LLM integration as a backend skill: Spring AI 2.0, RAG over these very training materials, tool calling, offline evaluation. Runs with **no API key** — deterministic fake models keep every test hermetic; using a real model is an optional exercise |
| 19 | `19-reliability-slo` | The measurement discipline: metric cardinality as a self-inflicted outage, histograms vs lying averages, closed- vs open-model load, JFR profiling parsed programmatically, connection-pool diagnosis from metrics alone, SLIs/SLOs/error budgets, and a blameless postmortem drill |

### Track F — Security & identity
| # | Project | You will learn |
|---|---------|----------------|
| 17 | `17-api-security` | Threat modelling, Spring Security 7 resource server against real Keycloak, **object-level authorization** (the API's most common real failure — you fix a planted IDOR), CORS/CSRF/SSRF/injection, business-flow limits, secret handling, and audit logs that do not leak PII |

### Track G — Data & concurrency
| # | Project | You will learn |
|---|---------|----------------|
| 20 | `20-transactions` | What ACID actually guarantees: the four anomalies reproduced on real Postgres, snapshot isolation vs SSI, serialisation failures and the retry they oblige you to write, and the three ways `@Transactional` silently does nothing — plus why a transaction boundary is a capacity decision |
| 21 | `21-locking` | Optimistic (`@Version`) vs pessimistic (`FOR UPDATE`) locking and what each costs under contention; deadlocks and the ordering that prevents them; `SKIP LOCKED`; the conditional `UPDATE` that beats both; and `ETag`/`If-Match` for the edits that span human think-time |
| 22 | `22-distributed-locking` | Locks that must survive a process disappearing: advisory locks, lease tables, the zombie-holder failure no lease can prevent, fencing tokens, competing consumers with crash recovery — and why idempotency is the only property that actually holds |

Projects 17–19 were added after the first sixteen to close specific gaps, and 20–22 after those
(see the coverage map below). Track numbers are thematic; the digits are simply the order things
were built, so Track C runs 07–11 then 18, and Track E runs 14–16 then 19.

Track C onward needs Docker (Testcontainers spins up Postgres and Kafka per test run), as does
Track G. Project 14
compiles with `--enable-preview` because structured concurrency is still a preview API in Java 25;
that flag is scoped to that one project.

## Prerequisites

- **JDK 25** — already installed via Homebrew; Maven on this machine already runs on it
  (`mvn -version` reports Java 25). Your shell default `java` is still 17; for IDE work select
  the Homebrew JDK (`/opt/homebrew/opt/openjdk`), and for CLI runs of `java` directly:

  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk
  ```

- **Docker** — required from Track C onward (Testcontainers, Kafka, observability stack).
- **Maven 3.9+** — installed.

> **Docker caveat (August 2026):** this machine's Docker Desktop was refusing to pull new images
> while the training was built (a restart should fix it). The images the lessons actually need are
> already loaded locally: `postgres:16-alpine`, `postgres:17-alpine`, `apache/kafka:4.1.0`,
> `pgvector/pgvector:pg17` (project 16), `testcontainers/ryuk:0.14.0`. Two optional extras are *not* cached —
> `grafana/otel-lgtm` (project 15, step 6) and the Temporal server images (project 10, step 6) — so
> restart Docker Desktop before those two exercises.

## IDE setup

The repo root has a thin aggregator `pom.xml` (`packaging=pom`, no parent inheritance) whose sole
job is to list every project as a `<module>`. Open the repo root as a Maven project in your IDE and
all 22 lessons (24 modules, counting the two-project tracks) are imported in one step — no need to
add each project as a module by hand. Each project still keeps its own versions, dependencies and
parent, and is built independently by `verify-all.sh`; the aggregator is IDE/tooling convenience
only.

## Checking the repo still builds

```bash
./verify-all.sh
```

Runs `mvn test` in every project and prints one line each. `./verify-all.sh 07 14` limits it to
specific projects. A high `Skipped` count is expected in a fresh project — those are the checkpoint
tests waiting for you.

## Coverage map

What this training does and does not teach, against a standard backend-engineering syllabus:

| Area | Coverage | Projects |
|---|---|---|
| Modern Java language & idiom | Full | 01, 14 |
| Testing & methodology (TDD, BDD, strategy) | Full | 02, 12, 13 |
| Architecture & domain design | Full | 03, 04, 05, 06, 11 |
| Event-driven patterns | Full | 07, 08, 09, 10 |
| Transactions, isolation & locking | Full | 20, 21 |
| Distributed coordination | Full | 22 (with 10) |
| Messaging & broker mechanics | Full | 18 |
| Security & identity | Full | 17 |
| Observability, performance, reliability | Full | 15, 19 (setup in 11, 14) |
| AI integration | Elective | 16 |
| **Not covered by design** | — | Kubernetes operations, IaC, frontend, mobile, data engineering/analytics, mTLS and key rotation at scale, WAFs |

Three topics are taught as *practices with templates* rather than code, because that is how they
exist in real work: threat modelling (`17-api-security/docs/threat-model.md`), event storming
(`05-ddd/docs/eventstorming.md`), and blameless postmortems
(`19-reliability-slo/docs/postmortem-template.md`).

## Reference material

- `docs/research/` — the four research reports (August 2026) this curriculum was built from:
  architecture styles, methodologies, event-driven patterns, and platform/production trends.
  Each lesson's reading list links back to these.
- `.authoring/CONVENTIONS.md` — how lessons are structured and versions pinned, if you want
  to extend the training later.

## Progress

- [ ] 01 Modern Java
- [ ] 02 TDD
- [ ] 03 Vertical slices
- [ ] 04 Hexagonal architecture
- [ ] 05 DDD
- [ ] 06 Modular monolith
- [ ] 07 Events & outbox
- [ ] 08 CQRS
- [ ] 09 Event sourcing
- [ ] 10 Sagas
- [ ] 11 Microservices
- [ ] 12 Testing strategy
- [ ] 13 BDD
- [ ] 14 Virtual threads
- [ ] 15 Production readiness
- [ ] 16 AI backend (elective)
- [ ] 17 API security
- [ ] 18 Messaging mechanics
- [ ] 19 Reliability & SLOs
- [ ] 20 Transactions
- [ ] 21 Locking
- [ ] 22 Distributed locking
