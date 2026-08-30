# Event-Driven & Distributed-Systems Patterns in Java — State of the Ecosystem, August 2026

Research notes for a hands-on training curriculum. All versions and dates verified via web search, August 2026.

---

## 1. Event-Driven Architecture Fundamentals (Events vs Commands, Choreography vs Orchestration, Eventual Consistency)

**Definition.** Event-driven architecture (EDA) structures systems around the production and consumption of *events* — immutable facts about something that already happened ("OrderPlaced") — as opposed to *commands*, which are requests directed at a specific recipient to do something that may be rejected ("PlaceOrder"). Coordination between services follows two styles: *choreography*, where each service reacts independently to events with no central coordinator, and *orchestration*, where a dedicated component drives the flow by issuing commands and awaiting results. Because state changes propagate asynchronously, EDA systems trade strong consistency for *eventual consistency*: all replicas/read models converge to the same state, but readers may observe stale data in the interim.

**Relevance in 2026.** Still the foundational vocabulary — and the area where teams make the most expensive mistakes. The current consensus (visible in Temporal's, ByteByteGo's and Conduktor's 2025–2026 writing) has hardened into a pragmatic rule of thumb: choreography for simple, linear, 2–4-step flows and cross-team decoupling; orchestration for anything with branching, compensation, or more than ~4 participants, because a central state record makes failures diagnosable. Martin Fowler's warning that "event-driven" means four different patterns (notification, event-carried state transfer, event sourcing, CQRS) remains the standard framing. The 2025–2026 twist: the modular-monolith movement (Spring Modulith) legitimized *in-process* events as the starting point, with distributed EDA as a later step, not a default.

**Key Java tools (current versions).**
- Spring Framework 7.0 / Spring Boot 4.1.x — `ApplicationEventPublisher`, `@TransactionalEventListener`
- Spring Modulith 2.1.x — module-scoped events (see §7)
- Any broker from §2; plus lightweight in-JVM buses (Guava EventBus, MBassador) for teaching contrast
- Testcontainers (2.x line) for realistic broker-backed labs

**Canonical learning resources.**
1. *Designing Data-Intensive Applications, 2nd ed.* — Martin Kleppmann & Chris Riccomini (O'Reilly, 2025/2026) — the newly-released update is the definitive distributed-systems grounding.
2. "What do you mean by 'Event-Driven'?" — Martin Fowler (martinfowler.com, 2017) — the four-pattern taxonomy; still the best 20-minute read.
3. *Enterprise Integration Patterns* — Gregor Hohpe & Bobby Woolf — the messaging pattern vocabulary (channels, correlation, competing consumers) everything else builds on.
4. *Building Event-Driven Microservices* — Adam Bellemare (O'Reilly, 2020).
5. "To Choreograph or Orchestrate Your Saga, That Is the Question" — Temporal blog; plus Bernd Ruecker's *Practical Process Automation* (O'Reilly, 2021) for the orchestration counter-position.

**Hands-on project idea.** "Coffee shop, three ways": build a small order flow (order → payment → barista → notification) first as synchronous REST calls, then as choreographed events over a broker, then orchestrated by one coordinator class. Inject a failure in the payment step in each variant and compare: where does the failure knowledge live, what's the blast radius, and what does the customer see while state is eventually consistent? One codebase, three branches — the comparison *is* the lesson.

---

## 2. Messaging Technology in Java, 2026: Kafka, Pulsar, RabbitMQ, NATS + Spring Integration

**Definition.** The message broker layer: durable logs (Kafka, Pulsar) that retain ordered event streams for replay, versus smart-broker queues (RabbitMQ) that route and delete on ack, versus lightweight multi-paradigm messaging (NATS with JetStream persistence). Spring Kafka provides idiomatic listener-container programming for Kafka; Spring Cloud Stream abstracts over brokers via a binder SPI.

**Relevance in 2026.** The log-vs-queue dichotomy just collapsed: **Kafka 4.2 (Feb 2026) shipped share groups (KIP-932, "Queues for Kafka") as GA**, giving Kafka per-record acknowledgment, delivery counts, and queue semantics natively — a headline topic any 2026 curriculum must cover. Kafka is now **ZooKeeper-free**: 4.0 (Mar 2025) removed ZooKeeper entirely; KRaft is the only mode (3.9.x remains the "bridge" release for migrations). RabbitMQ 4.x completed its own modernization: native AMQP 1.0 (4.0), Khepri (Raft-based) as default metadata store (4.2), with 4.3 (Apr 2026) as current. Pulsar remains the multi-tenant/geo-replication alternative, with 5.0 milestones previewing "Scalable Topics" and the Oxia metadata store. NATS is the rising lightweight option (edge, IoT, K8s-native), adding atomic batch publish, message scheduling and counters in 2.12–2.14.

**Key Java tools (current versions).**
- **Apache Kafka 4.3.1** (Jun 2026; 4.2.0 Feb 2026 = share groups GA); `kafka-clients` same versioning
- **Spring for Apache Kafka 4.1.0** (Jun 2026; 4.0 GA Nov 2025 — added Jackson 3 and `@KafkaListener` share-consumer support; 3.3.x maintenance line for Boot 3)
- **Spring Cloud Stream 5.0.2** (part of Spring Cloud 2025.1.x, latest 2025.1.3 Aug 2026) — binders for Kafka, Rabbit, Pulsar, Kinesis, Pub/Sub, Solace, etc.
- **RabbitMQ 4.3.5** broker; Java: `amqp-client` (AMQP 0-9-1) and the newer `rabbitmq-amqp-client` for AMQP 1.0; Spring AMQP 4.x
- **Apache Pulsar 4.2.2** (LTS line 4.0.x; 5.0.0-M1 out); Java client `pulsar-client`
- **NATS Server 2.14** (Apr 2026); Java client `io.nats:jnats` 2.26.x

**Canonical learning resources.**
1. *Kafka: The Definitive Guide, 2nd ed.* — Shapira, Palino, Sivaram, Petty (O'Reilly) — still the standard; supplement with 4.x release notes for KRaft/share groups.
2. Confluent Developer (developer.confluent.io) — free courses ("Apache Kafka 101", "Spring Framework and Kafka"), kept current.
3. "Let's Take a Look at… KIP-932: Queues for Kafka!" — Gunnar Morling (morling.dev) — the best independent explainer of share groups.
4. "Introducing Share Consumer Support (Kafka Queues) in Spring for Apache Kafka" — Soby Chacko (spring.io blog, Oct 2025).
5. RabbitMQ official blog release posts (4.2, 4.3) and NATS by Example (natsbyexample.com) for the non-Kafka side.

**Hands-on project idea.** "One workload, three brokers": a parcel-tracking feed produced at random rates, consumed by (a) Kafka classic consumer group, (b) Kafka *share group* via Spring Kafka 4.1, (c) RabbitMQ quorum queue — all via Testcontainers. Measure redelivery behavior, ordering guarantees, and scaling-out (add a 4th consumer to a 3-partition topic: classic group leaves it idle; share group doesn't). Finish by porting one consumer to Spring Cloud Stream to experience the binder abstraction and its leaky edges.

---

## 3. CQRS (Command Query Responsibility Segregation)

**Definition.** CQRS splits the model that handles state changes (commands) from the model that serves reads (queries). At minimum this is two code paths over one database; at maximum it is separate write and read stores kept in sync by events, allowing each side to scale and be shaped independently (e.g., normalized writes, denormalized search-index reads).

**Relevance in 2026.** Alive, but the pendulum has swung decisively toward *pragmatic, single-database CQRS*. The 2025–2026 literature (Oskar Dudycz, foojay.io, current Java guides) repeats the same message: CQRS is a code-organization pattern first, not a two-database mandate; it "earns its complexity" only when read and write shapes or scaling profiles genuinely diverge, or when event sourcing already provides the event stream. Greg Young's original guidance ("CQRS is not a top-level architecture") is being rediscovered. In the Java world, the big 2025 event was **Axon Framework 5.0 (Nov 2025)** — a ground-up redesign of the leading CQRS/ES framework. Hand-rolled CQRS (Spring + jOOQ/JPA read projections, or Spring Modulith events feeding a view table) is the mainstream take; Axon is the framework path for teams wanting the full message-driven model.

**Key Java tools (current versions).**
- **Axon Framework 5.3.0** (Aug 2026; 5.0 GA Nov 2025; 4.x line still supported) + Axon Server 2025.x
- Hand-rolled: Spring Boot 4.1 + Spring Data / jOOQ 3.20.x for read models; Spring's `ApplicationEventPublisher` or Modulith events to sync projections
- Occurrent (Johan Haleby) and Eventuate for alternative framings
- No dedicated "MediatR-for-Java" has won; simple command-handler interfaces or Axon's gateways fill that role

**Canonical learning resources.**
1. "CQRS" — Martin Fowler (martinfowler.com, 2011) — short, canonical, appropriately skeptical.
2. *CQRS Documents* — Greg Young (free PDF) — the origin text; pair with his "CQRS and Event Sourcing" talks.
3. event-driven.io — Oskar Dudycz — esp. "CQRS facts and myths explained" and "CQRS is simpler than you think" — the definitive pragmatic 2020s take.
4. AxonIQ Academy (academy.axoniq.io) + "The Release of Axon Framework 5.0" (axoniq.io blog, Nov 2025) for the framework path.
5. microservices.io/patterns/data/cqrs.html — Chris Richardson — CQRS in the microservices context, trade-offs table.

**Hands-on project idea.** "Library lending, split in two": start from a working CRUD Spring Boot app for book lending. Step 1: split command and query services over the *same* database (pure code CQRS) and add a denormalized `member_activity_view` table updated by domain events in-process. Step 2: move the view to a separate schema updated asynchronously and *break* it (kill the projector, observe stale reads, replay to heal). Step 3 (optional stretch): re-implement the same slice in Axon 5 with command handlers and projections, and compare line counts and concepts. Teaches when each level of CQRS pays off.

---

## 4. Event Sourcing

**Definition.** Event sourcing persists state as the append-only sequence of domain events that produced it, rather than as current-state rows; current state is derived by replaying (folding) events, usually with snapshots as an optimization. It gives a perfect audit log, temporal queries ("state as of March 3rd"), and the ability to build new read models retroactively — at the cost of event versioning discipline, projection plumbing, and a mental model many teams underestimate.

**Relevance in 2026.** Active and genuinely evolving — 2025–2026 delivered the biggest conceptual shift in a decade: the **Dynamic Consistency Boundary (DCB)**, which drops the hard aggregate-per-stream rule in favor of tagged events and on-demand, operation-scoped consistency boundaries. DCB (originating in Sara Pellegrini's "kill the aggregate" work) is the headline feature of **Axon Framework 5 / Axon Server 2025.1** and is being explored by Occurrent; dcb.events collects the emerging theory. Meanwhile **EventStoreDB rebranded to KurrentDB** (company: Kurrent, first release 25.0; Docker `kurrentplatform/kurrentdb`). The criticism track is equally mature: the community line (Dudycz, Jimmy Bogard, "Let's build the worst Event Sourcing system!") is that event sourcing fails when applied system-wide, when events are designed as CRUD-with-history ("property sourcing"), or without versioning strategy — use it per-module where audit/temporality is a business requirement, not as an architecture-wide default.

**Key Java tools (current versions).**
- **Axon Framework 5.3.0** + Axon Server — the dominant Java ES stack, now with DCB
- **KurrentDB 25.x** (ex-EventStoreDB) with Java client `io.kurrent:kurrentdb-client` 1.1.x
- **Occurrent** (Johan Haleby) — unintrusive JVM ES library storing events as CloudEvents in MongoDB/Postgres; the closest philosophical cousin to .NET's Marten (there is still **no true Marten equivalent in Java** — Postgres-based ES is typically hand-rolled or done via Occurrent/Eventuate; Oskar Dudycz's EventSourcing.JVM repo shows the hand-rolled approach)
- Eventuate Local, Akka/Pekko Persistence (Apache Pekko 1.1.x) for actor-flavored ES

**Canonical learning resources.**
1. "Event Sourcing" — Martin Fowler (martinfowler.com) + Greg Young's "Versioning in an Event Sourced System" (free book) — foundations and the hardest practical problem.
2. event-driven.io + EventSourcing.JVM (github.com/oskardudycz/EventSourcing.JVM) — Oskar Dudycz — Java/Kotlin samples, self-paced kits, and the pragmatic "when not to" essays.
3. "Let's build the worst Event Sourcing system!" — Oskar Dudycz (NDC London 2024, YouTube) — anti-patterns taught by inversion; ideal training material.
4. "The Aggregate Is Dead. Long Live the Aggregate!" — Sara Pellegrini & Milan Savić (talk) + dcb.events + AxonIQ's "DCB in Axon Framework 5" blog — the DCB canon.
5. Kurrent Academy — vendor-neutral-ish ES fundamentals, "Beginner's Guide to Event Sourcing".

**Hands-on project idea.** "Bank account, replayed": implement an account ledger three ways in one session. (1) Hand-rolled: Postgres `events` table + optimistic append with a version check + a fold function — no framework, ~150 lines, demystifies everything. (2) Same domain on KurrentDB with its Java client, adding a catch-up subscription that projects balances into Postgres. (3) Time-travel exercise: add a retroactive "fee refund" event type and rebuild a new read model from history that old code never anticipated. Stretch: show the same invariant ("no overdraft across *two* accounts") is awkward with aggregates and natural with Axon 5's DCB.

---

## 5. Transactional Outbox, Idempotent Consumers, and Exactly-Once Realities

**Definition.** The *dual-write problem*: a service cannot atomically update its database and publish to a broker — one can succeed while the other fails. The **transactional outbox** solves it by writing the event into an `outbox` table inside the same local DB transaction, then relaying it to the broker (by polling or CDC), guaranteeing at-least-once publication. Since at-least-once implies duplicates, the **idempotent consumer** pattern makes redelivery harmless — by natural idempotency, processed-message-ID tracking in the consumer's DB, or dedup keys. "Exactly-once" is achievable only as *exactly-once processing within a closed system* (e.g., Kafka read-process-write with transactions), never as generic exactly-once *delivery* across arbitrary systems.

**Relevance in 2026.** This is now considered table-stakes engineering, and the 2025–2026 consensus is refreshingly blunt: stop chasing exactly-once, embrace at-least-once + idempotency; Kafka transactions cover only the Kafka-to-Kafka path — a consumer's DB write is outside their scope, so outbox + idempotent writes remain necessary. Current Kafka improvements matter for the story: KIP-890 ("transactions v2", in Kafka 4.x) hardened the transaction protocol against hanging transactions, and KIP-939 (2PC participation, for external coordinators like Flink) is the frontier. Two notable 2025–2026 conveniences: **Spring Modulith 2.0's Event Publication Registry gives Spring apps "outbox for free"** (persisted event publications, redelivery on restart), and Debezium's outbox event router remains the canonical CDC relay.

**Key Java tools (current versions).**
- **Debezium 3.6** (stable; 3.7 in alphas; Debezium Quarkus outbox extension 3.5.2.Final) — CDC-based outbox relay to Kafka
- **Spring Modulith 2.1.x** Event Publication Registry (JDBC/JPA/MongoDB/Neo4j) — polling-flavored outbox; new integration with **Namastack Outbox** (ordered, multi-instance outbox library) in Modulith 2.1
- Eventuate Tram 0.26.0 (Apr 2026) — Richardson's outbox + messaging framework, still maintained
- Kafka 4.x idempotent producer (default) + `read_committed` consumers + `kafka-streams` `processing.guarantee=exactly_once_v2`
- Plain Spring: `@TransactionalEventListener(AFTER_COMMIT)` as the teaching stepping stone

**Canonical learning resources.**
1. "Pattern: Transactional Outbox" — Chris Richardson (microservices.io) — the canonical pattern page.
2. "Reliable Microservices Data Exchange With the Outbox Pattern" — Gunnar Morling (debezium.io blog) — the CDC-outbox reference implementation article.
3. "Idempotence Is Not a Medical Condition" — Pat Helland (ACM Queue) — the timeless paper on why duplicates are inevitable.
4. "Idempotent Processing with Kafka" — Nejc Korasa; plus Conduktor's "Build Idempotent Kafka Consumers: Patterns That Actually Work" (2025/2026) — practical consumer-side recipes.
5. Confluent's "Exactly-Once Semantics Are Possible: Here's How" — Neha Narkhede — read *critically* against #3 to teach what EOS does and does not claim.

**Hands-on project idea.** "Break the dual write, then fix it twice": an order service naively saves to Postgres then publishes to Kafka; a chaos toggle kills the process between the two operations, and a reconciliation script proves events were lost. Fix 1: outbox table + Debezium (Testcontainers: Postgres + Kafka + Kafka Connect) with the outbox event router. Fix 2: same app on Spring Modulith's externalized events, comparing operational trade-offs (CDC infra vs polling latency). Then flip to the consumer: run it at concurrency 3 with redeliveries and make it idempotent via a `processed_messages` table — asserting the invoice total is correct despite duplicates. Every guarantee is *demonstrated by a failing test first*.

---

## 6. Sagas / Process Managers for Distributed Transactions

**Definition.** A saga replaces an impossible cross-service ACID transaction with a sequence of local transactions, each paired with a *compensating action* to semantically undo it if a later step fails (cancel reservation, refund payment). *Choreographed* sagas coordinate via events; *orchestrated* sagas use a central process manager that tracks state, issues commands, and runs compensations in reverse on failure. "Process manager" is the general stateful-coordinator pattern; a saga is the transactional special case.

**Relevance in 2026.** Very high, and the market has consolidated around **durable execution** as the modern way to write orchestrators: you write the saga as ordinary Java code, and the engine (Temporal, and similar systems) persists every step so the "program" survives crashes and can wait days. Temporal is now the most-cited saga tool (its 2026 additions include Nexus for cross-team calls and standalone Activities in the Java SDK); Camunda 8 remains the choice where BPMN visual models are a stakeholder requirement — note Camunda 8.8 replaced the Zeebe Java client with the unified **Camunda Java client** (Zeebe client removed in 8.10). Axon Sagas serve teams already on Axon; Eventuate Tram Sagas remains the lightweight Richardson-style option. The 2026 heuristic: choreography for ≤3–4 linear steps; orchestration beyond that, for observability alone.

**Key Java tools (current versions).**
- **Temporal Java SDK 1.38.0** (Aug 2026), `io.temporal:temporal-sdk`; self-hostable server or Temporal Cloud; `Saga` helper class built in
- **Camunda 8.9.x** (8.9.16 Aug 2026) with the new Camunda Java client + Spring Boot SDK; BPMN compensation events model sagas visually
- **Axon Framework 5.x** sagas/process managers
- **Eventuate Tram Sagas 0.26.0** — orchestration DSL on JDBC/JPA + outbox
- Honorable mentions: Spring Statemachine, MicroProfile LRA (Narayana implementation) — niche but curriculum-relevant as contrast

**Canonical learning resources.**
1. "Sagas" — Hector Garcia-Molina & Kenneth Salem (1987 paper) — short, readable, the origin.
2. *Microservices Patterns, 2nd ed.* — Chris Richardson (Manning, MEAP since Jun 2025) — chapters on sagas/countermeasures; microservices.io/patterns/data/saga.html for the free version.
3. *Practical Process Automation* — Bernd Ruecker (O'Reilly, 2021) + his conference talks — the orchestration-advocate canon.
4. Temporal's free courses "Temporal 101 / 102 with Java" (learn.temporal.io) + "Saga Orchestration vs. Choreography" (temporal.io blog).
5. "Saga Pattern Demystified" — ByteByteGo (Alex Xu's newsletter, 2025) — concise modern comparison for pre-reading.

**Hands-on project idea.** "Trip booking that fails on purpose": flight + hotel + payment services (each a tiny Spring Boot app with a failure-injection endpoint). Round 1: choreographed saga over Kafka — implement compensation events, then discover how hard it is to answer "where is booking #42 stuck?". Round 2: same flow as a Temporal workflow in Java using the `Saga` compensation helper; kill the worker mid-saga and watch it resume; use the Web UI to inspect history. Round 3 (demo or stretch): the same flow in Camunda 8 BPMN with compensation boundary events, to show the visual-model alternative. Deliverable: a one-page decision matrix filled in from experience.

---

## 7. Spring Modulith: Application Events and Externalization as a Stepping Stone

**Definition.** Spring Modulith structures a Spring Boot application into verifiable modules (enforced by tests over package conventions) that communicate via Spring application events instead of direct bean calls. Its *Event Publication Registry* persists event publications transactionally and redelivers incomplete ones after crashes; *event externalization* (`@Externalized`) forwards selected domain events to Kafka/AMQP/JMS automatically. The result is a modular monolith whose internal event contracts are exactly the seams along which it can later be split into services.

**Relevance in 2026.** One of the strongest currents in the Java ecosystem — "modulith first, microservices when forced" is now mainstream advice, and Spring Modulith is its reference implementation. **2.0 GA (Nov 21, 2025)** landed on Boot 4/Framework 7 with a revamped registry (JDBC, JPA, MongoDB, Neo4j), module-specific Flyway migrations, and startup-time module verification; **2.1 GA (Jun 2026)** added outbox-based externalization via Namastack Outbox (ordered, multi-instance). For a curriculum it is the ideal bridge topic: events, eventual consistency, at-least-once delivery, and the outbox — all inside one deployable, before any broker ops.

**Key Java tools (current versions).**
- **Spring Modulith 2.1.x** (2.1 GA Jun 2026; 2.0.x for Boot 4.0; 1.4.x maintenance for Boot 3.5) — `spring-modulith-events-kafka`, `-amqp`, `-jms`, `-aws-sns/sqs` externalizers
- Spring Boot 4.1.x, `@ApplicationModuleListener` (= async + transactional + new transaction)
- ArchUnit (transitively) for module verification; Modulith's `Documenter` for generated C4/PlantUML docs
- Namastack Outbox (new in the 2.1 integration) for ordered externalization

**Canonical learning resources.**
1. Spring Modulith reference documentation + Oliver Drotbohm's talks ("Spring Modulith — A Deep Dive", Spring I/O) — the author's canon.
2. "Event Externalization with Spring Modulith" — Baeldung — the standard walk-through.
3. "Spring Modulith Externalized Events: Publishing Events to Kafka" — Dan Vega (danvega.dev + YouTube) — accessible video/code treatment.
4. *Modular Monolith* essays — Kamil Grzybek — the architectural case, framework-independent.
5. "Externalize Spring-Modulith Events with Spring Cloud Stream" — ZenWave360 — the Modulith-to-broker bridge in practice.

**Hands-on project idea.** "Split-ready shop": take a deliberately tangled single-package e-commerce monolith and (1) carve it into Modulith modules until `ApplicationModules.verify()` passes, (2) replace the direct `inventoryService.reserve()` call with a domain event handled via `@ApplicationModuleListener`, (3) kill the JVM between publish and handle, restart, and watch the registry redeliver (outbox behavior with zero infrastructure), (4) add `@Externalized("orders.OrderCompleted")` plus the Kafka externalizer and see the same event appear on a Testcontainers broker, and (5) generate the module documentation. Endgame discussion: which module would you extract first, and what does its event contract already give you?

---

## 8. Async APIs: AsyncAPI and CloudEvents

**Definition.** Two complementary standards for making event-driven interfaces first-class contracts. **AsyncAPI** is "OpenAPI for EDA": a specification document describing an application's channels, operations (send/receive), messages, and broker bindings, enabling docs, code generation, and governance. **CloudEvents** is a CNCF-graduated specification for the *envelope* of an individual event — standard attributes (`id`, `source`, `type`, `time`, …) with bindings for HTTP, Kafka, AMQP, MQTT — so events are portable and middleware/tooling can route them without understanding payloads.

**Relevance in 2026.** Steadily rising, driven by API-governance programs extending to event streams. **AsyncAPI 3.1.0 (Jan 2026)** is current — 3.0 (Nov 2023) fixed the notorious publish/subscribe ambiguity, and 3.x adoption in tooling is now solid. In Java, code-first generation via **Springwolf** (annotations like `@KafkaListener` scanned into AsyncAPI 3 docs + UI) has become the pragmatic default over spec-first. CloudEvents graduated CNCF in 2024 and is quietly ubiquitous: Knative, Dapr, Azure Event Grid, and Java-side support in Spring (`cloudevents-spring`), Quarkus/Funqy, and Occurrent (which stores events *as* CloudEvents). Teaching angle: schema/contract discipline (with a registry) is what separates workshops from production EDA.

**Key Java tools (current versions).**
- **Springwolf 1.x** (springwolf-core; AsyncAPI 3.0 output since v1.0; plugins for Kafka, AMQP, SQS/SNS, JMS, STOMP) + springwolf-ui
- **CloudEvents Java SDK 4.0.2** (`io.cloudevents:cloudevents-core`, `-kafka`, `-spring`, `-http-basic`)
- jasyncapi (code-first AsyncAPI POJOs), AsyncAPI Generator/CLI (Node-based, generates Java Spring templates)
- Schema management: Confluent Schema Registry / Apicurio Registry 3.x with Avro/Protobuf/JSON Schema — the de-facto contract layer for Kafka itself

**Canonical learning resources.**
1. AsyncAPI official docs + "AsyncAPI 3.0.0 / 3.1.0 Release Notes" (asyncapi.com) — spec and rationale.
2. "Documenting Spring Event-Driven API Using AsyncAPI and Springwolf" — Baeldung — the standard Java tutorial.
3. Springwolf docs + GitHub (springwolf.github.io) — quickstarts per protocol.
4. CloudEvents primer + spec (cloudevents.io) — short and readable; pair with the Java SDK docs.
5. "Understanding AsyncAPI's Role in Event-Driven Architectures" — AsyncAPI Conf talks (YouTube) for ecosystem context.

**Hands-on project idea.** "Contract or it didn't happen": take the coffee-shop app from §1 and (1) add Springwolf so the existing `@KafkaListener`s self-document; explore the generated AsyncAPI 3.1 doc in springwolf-ui and publish a test message from it; (2) wrap outgoing events as CloudEvents with the Kafka binding and show a generic auditing consumer that routes on `type` without deserializing payloads; (3) introduce a breaking payload change and catch it with a schema-registry compatibility check in CI; (4) generate a working consumer from another team's AsyncAPI file alone. Teaches that in EDA, the *message* is the API.

---

## Cross-Cutting Curriculum Notes

- **Version-stable lab stack (Aug 2026):** Java 21/25 LTS, Spring Boot 4.1.x, Spring Kafka 4.1, Spring Modulith 2.1, Kafka 4.2+ (KRaft, single-node via Testcontainers), Debezium 3.6, Temporal SDK 1.38, Axon 5.3. All labs runnable via Docker/Testcontainers — no ZooKeeper anywhere, which simplifies teaching materially versus pre-2025 courses.
- **Narrative arc that matches 2026 consensus:** in-process events (Modulith) → outbox/externalization → broker consumers + idempotency → CQRS read models → sagas (choreography, then durable orchestration) → event sourcing as the advanced elective, with DCB as the "what's next" capstone.
- **Recurring authors worth anchoring the reading list on:** Kleppmann & Riccomini, Chris Richardson, Oskar Dudycz, Gunnar Morling, Bernd Ruecker, Oliver Drotbohm, Greg Young, Pat Helland.

## Sources

[Apache Kafka release announcements](https://kafka.apache.org/blog/releases/) · [Kafka 4.2.0 announcement](https://kafka.apache.org/blog/2026/02/17/apache-kafka-4.2.0-release-announcement/) · [Gravitee: Kafka in 2026](https://www.gravitee.io/blog/apache-kafka-news-2026_whats-next) · [Confluent: Kafka queue semantics GA](https://www.confluent.io/blog/kafka-queue-semantics-share-consumer-ga/) · [Morling on KIP-932](https://www.morling.dev/blog/kip-932-queues-for-kafka/) · [KIP-939](https://cwiki.apache.org/confluence/display/KAFKA/KIP-939:+Support+Participation+in+2PC) · [RabbitMQ release information](https://www.rabbitmq.com/release-information) · [RabbitMQ 4.3 highlights](https://www.rabbitmq.com/blog/2026/04/23/rabbitmq-4.3-release) · [Pulsar versions](https://pulsar.apache.org/versions/) · [NATS 2.14 release](https://nats.io/blog/nats-server-2.14-release/) · [Spring Kafka 4.0 GA](https://spring.io/blog/2025/11/18/spring-kafka-4/) · [Spring Kafka 4.1.0](https://spring.io/blog/2026/06/09/spring-kafka-4/) · [Spring Kafka share consumer](https://spring.io/blog/2025/10/14/introducing-spring-kafka-share-consumer/) · [Spring Cloud 2025.1.3](https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/) · [Axon Framework 5.0 release](https://www.axoniq.io/blog/release-of-axon-framework-5-0) · [Axon 5.3.0 announcement](https://discuss.axoniq.io/t/axon-and-axoniq-framework-release-5-3-0/6771) · [DCB in Axon 5](https://www.axoniq.io/blog/dcb-in-af-5) · [Kurrent rebrand FAQ](https://www.kurrent.io/blog/kurrent-re-brand-faq) · [KurrentDB Java client](https://github.com/kurrent-io/KurrentDB-Client-Java) · [Occurrent](https://github.com/johanhaleby/occurrent) · [EventSourcing.JVM](https://github.com/oskardudycz/EventSourcing.JVM) · [event-driven.io](https://event-driven.io/) · [Temporal Java SDK releases](https://github.com/temporalio/sdk-java/releases) · [Camunda 8.9 announcements](https://docs.camunda.io/docs/reference/announcements-release-notes/890/890-announcements/) · [Eventuate Tram Sagas](https://github.com/eventuate-tram/eventuate-tram-sagas) · [Temporal saga blog](https://temporal.io/blog/to-choreograph-or-orchestrate-your-saga-that-is-the-question) · [Spring Modulith 2.0 GA](https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/) · [Spring Modulith 2.1 GA](https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/) · [Baeldung event externalization](https://www.baeldung.com/spring-modulith-event-externalization) · [Dan Vega on externalized events](https://www.danvega.dev/blog/spring-modulith-externalized-events) · [Debezium releases](https://debezium.io/tag/releases/) · [microservices.io outbox](https://microservices.io/patterns/data/transactional-outbox.html) · [Idempotent processing with Kafka — Korasa](https://nejckorasa.github.io/posts/idempotent-kafka-procesing/) · [Conduktor idempotent consumers](https://www.conduktor.io/blog/building-idempotent-consumers) · [AsyncAPI 3.1.0 release notes](https://www.asyncapi.com/blog/release-notes-3.1.0) · [Springwolf](https://github.com/springwolf/springwolf-core) · [Baeldung Springwolf](https://www.baeldung.com/java-spring-doc-asyncapi-springwolf) · [CloudEvents Java SDK](https://github.com/cloudevents/sdk-java) · [DDIA 2nd edition — Kleppmann](https://martin.kleppmann.com/2026/03/24/designing-data-intensive-applications-2e.html) · [Microservices Patterns 2e MEAP](https://microservices.io/post/architecture/2025/06/26/announcing-meap-microservices-patterns-2nd-edition.html) · [microservices.io CQRS](https://microservices.io/patterns/data/cqrs.html)
