# Event-Driven Architecture (research excerpt)

> Frozen excerpt of docs/research/event-driven.md, copied into src/test/resources so the checkpoint tests
> have a corpus that never changes. The running application reads the real files.

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

