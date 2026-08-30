# 07 — Events & Outbox: Break the Dual Write, Then Fix It

> After this lesson you can demonstrate the dual-write problem with a test that proves data
> loss, fix it with a transactional outbox (Spring Modulith's event publication registry,
> externalized to Kafka), and make the consumer idempotent — so at-least-once delivery is a
> property you engineer for, not an incident you explain afterwards.

## Why this matters (2026)

Project 06 gave you a modular monolith whose modules talk through in-process application
events. That bought decoupling *inside* one JVM and one database — every event and its
side effects could still commit or roll back together. This lesson is about the moment that
stops being true: the event has to leave the process, and suddenly you have **two systems of
record** — your database and your broker — **and no transaction that spans both**.

Teams rediscover this the hard way, constantly. The service saves the order and publishes
`OrderPlaced`; a deploy, an OOM-kill or a network blip lands between the two operations; and
now either fulfillment ships nothing (lost event) or ships an order that doesn't exist (ghost
event). The 2025–2026 consensus on this is refreshingly blunt: **stop chasing exactly-once
delivery — it does not exist across arbitrary systems. At-least-once plus idempotency is the
job.** Kafka's own transactions cover only the Kafka-to-Kafka path; the write to *your*
database is outside their scope. That is why the transactional outbox and the idempotent
consumer are considered table-stakes engineering now, not advanced patterns.

Two 2025–2026 conveniences make the pattern cheap in Spring: **Spring Modulith 2.x's Event
Publication Registry gives Spring apps an outbox "for free"** (event publications persisted
in the same transaction, redelivered after crashes, `@Externalized` straight to Kafka), and
Debezium's outbox event router remains the canonical CDC relay when polling doesn't scale.
Source material: [docs/research/event-driven.md](../docs/research/event-driven.md),
sections 1, 5 and 7.

## Core concepts

### Events vs commands

- A **command** is a request directed at a specific recipient, which may refuse it:
  `PlaceOrder`. Imperative mood, addressed, expects an outcome.
- An **event** is an immutable fact about something that already happened: `OrderPlaced`.
  Past tense, addressed to nobody in particular, cannot be rejected — only reacted to.

The grammar is a design tool: if your "event" is named `SendInvoice`, it's a command wearing
an event costume, and you've coupled the publisher to one specific consumer's job.

Martin Fowler's warning still frames every EDA conversation: "event-driven" means at least
four different patterns — **event notification** (thin ping, consumer calls back for data),
**event-carried state transfer** (the event carries everything the consumer needs),
**event sourcing** (events *are* the persistence), and **CQRS**. Teams that don't say which
one they mean end up arguing about four architectures at once. Step 1 makes you classify
this project's usage.

### The dual-write problem

```
            ┌─────────────┐        ┌─────────────┐
   place()  │  Postgres    │        │   Kafka      │
  ────────► │  INSERT      │  then  │   send()     │
            │  COMMIT      │ ─────► │              │
            └─────────────┘        └─────────────┘
                 write #1               write #2
```

Two independent writes, no shared transaction. Whatever order you choose:

- **commit, then send** — crash between them and the event is **lost**. The database says
  the order exists; downstream never hears about it.
- **send, then commit** — crash between them and you published a **ghost**: downstream
  reacts to an order the database rolled back.

Moving the `send()` around doesn't fix anything; it only chooses which lie you tell.
XA/two-phase commit is not the answer either: Kafka doesn't participate as an XA resource,
and even where 2PC is available, its blocking failure modes and operational cost are why
the industry abandoned it for this problem.

### The transactional outbox

The fix is to stop doing two writes. Do **one** local transaction that covers both the
business row *and* the intent to publish — by writing the event into an outbox table in the
same database. A relay then moves outbox entries to the broker *after* commit, retrying until
each one is acknowledged:

```
  ┌──────────────────────────────┐
  │ ONE local transaction         │        ┌─────────┐
  │   INSERT INTO orders          │  relay │  Kafka   │
  │   INSERT INTO outbox          │ ─────► │          │
  │ COMMIT (atomic)               │ retry  └─────────┘
  └──────────────────────────────┘
```

Crash before commit: neither row exists, nothing was sent. Crash after commit but before the
relay finishes: the event sits in the outbox and is delivered when the process (or broker)
recovers. The guarantee becomes **at-least-once publication** — never zero-or-ghost.

Two relay flavors dominate:

- **Polling / in-process relay** — the application itself re-delivers unfinished entries.
  This is what **Spring Modulith's Event Publication Registry** does: `publishEvent(...)`
  inside a transaction writes a row to `event_publication`; listeners (including the Kafka
  externalizer for `@Externalized` events) mark it complete on success; incomplete rows can
  be re-submitted on restart (`IncompleteEventPublications`, or
  `spring.modulith.events.republish-outstanding-events-on-restart=true`).
- **CDC (change data capture)** — Debezium tails the database WAL, turns outbox inserts into
  broker records, and its *outbox event router* routes them to topics. No polling, lower
  latency, works across many app instances — at the price of running Kafka Connect. Step 6
  weighs the two; the stretch goal builds the CDC variant.

### At-least-once means duplicates — the idempotent consumer

At-least-once is not a defect of the outbox; it is the *only* honest contract, and its price
is duplicates: relay retries after timeouts, consumer crashes after processing but before
committing offsets, rebalances, registry resubmission — all deliver the same event again.
Pat Helland's line is the whole chapter in one sentence: over a network, *"guaranteed
delivery"* can only mean *guaranteed at-least-once*, so **receivers must treat duplicates as
normal traffic**. The standard recipe is the **idempotent consumer**: track processed event
IDs in the consumer's own database, in the *same local transaction* as the consumer's write,
and treat a known ID as a no-op. (The other recipes — naturally idempotent operations like
`UPSERT`, or dedup windows in the broker — are variations on the same idea: make redelivery
harmless.)

### Exactly-once, honestly

Kafka's "exactly-once semantics" (idempotent producer + transactions + `read_committed`)
are real — **within a closed Kafka read-process-write loop** (Kafka Streams being the
flagship). The moment a consumer writes to Postgres, calls an HTTP API or sends an email,
that write is outside the transaction's scope. So the working architecture in 2026 is
unchanged: outbox on the producer side, at-least-once in the middle, idempotency on the
consumer side. Exactly-once *processing* is something you build at the edges; exactly-once
*delivery* is something vendors print on brochures.

## The project

An order service: placing an order must (a) store it in Postgres and (b) notify fulfillment
via the Kafka topic `orders.OrderPlaced`. Fulfillment (same JVM, separate module — in
production it would be another service) records a task per received event.

```
src/main/java/dev/vlearning/orders/
├── OrdersApplication.java
├── KafkaConfig.java                     ← topic + the chaos-aware KafkaTemplate bean
├── order/
│   ├── Order.java, OrderItem.java       ← minimal domain (records)
│   ├── OrderPlaced.java                 ← the event; carries its own eventId
│   ├── OrderService.java                ← THE NAIVE DUAL WRITE (your step-3 subject)
│   ├── OrderRepository.java             ← JdbcClient
│   └── OrderController.java             ← POST /orders
├── fulfillment/
│   ├── FulfillmentListener.java         ← @KafkaListener (NOT idempotent — step 5 subject)
│   └── FulfillmentRepository.java
└── chaos/
    ├── ChaosMonkey.java                 ← armable crash points + broker kill switch
    ├── ChaosKafkaTemplate.java          ← every send fails while the "broker is down"
    └── ChaosException.java
```

The given `OrderService.place()` is deliberately wrong. Its `switch` over
`chaos.crashPoint()` is not three designs — it is the same naive design with the `send()`
moved around, which is exactly what teams do when they first meet this bug ("just publish
inside the transaction!"). The chaos monkey decides where the process "dies"; step 2 proves
every arm loses something.

On the wire, events travel as raw JSON bytes (`ByteArraySerializer`). Object-to-JSON
conversion happens in the messaging layer via the `RecordMessageConverter` that
spring-modulith-events-kafka auto-configures (Jackson 3) — the same converter serves the
naive sends, the externalized events of step 3, and the `@KafkaListener` argument
conversion.

**Requirements:** Docker running. Tests use Testcontainers (`postgres:16-alpine`,
`apache/kafka:4.1.0` — first run pulls them) with **one shared container pair and one Spring
context for the whole suite**, so the suite stays in test-minutes, not coffee-break-minutes.
There is deliberately no H2 anywhere: the outbox story is about real transactions in a real
database you can inspect.

```bash
mvn test                 # pristine checkout: green (unit tests + happy path; checkpoints skipped)
```

`HappyPathIntegrationTest` is this lesson's behavior pin: HTTP request → Postgres row →
Kafka record → fulfillment task. It stays enabled and green from the naive dual write all
the way through the outbox refactoring. If it goes red, you changed behavior, not plumbing.

Optionally, run the app for manual poking (uses the same infra as the stretch goal):

```bash
docker compose -f docker-compose.debezium.yml up -d postgres kafka
mvn spring-boot:run -Dspring-boot.run.profiles=local
curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' \
     -d '{"customer":"Ada","items":[{"sku":"KB-42","quantity":1,"unitPrice":59.50}]}'
```

## Guided steps

Checkpoint tests live in `src/test/java` annotated
`@Disabled("Checkpoint N — enable when you start step N")`. Remove the annotation when you
reach the step. **Two of them (2 and 4) pin bugs**: they pass against the broken code and
*must fail* after you fix it — at which point you re-disable them as exhibits. That rhythm
is the lesson: every guarantee is demonstrated by a test before you're allowed to rely on it.

### Step 1 — Name the pattern you're using

**Goal:** place this project on Fowler's map before touching the plumbing.

Read `OrderPlaced` and `FulfillmentListener`. Which of the four "event-driven" meanings is
this — event notification, event-carried state transfer, event sourcing, or CQRS? What
would the *notification* version of `OrderPlaced` look like, and what new coupling would it
introduce?

<details><summary>Answer</summary>

**Event-carried state transfer**, mostly: the event carries `orderId`, `customer` and
`total`, so fulfillment never calls back into the order module — at the price of a payload
that is now a published contract (add a field: fine; rename one: you break consumers).
The *notification* variant would carry only `orderId`, and fulfillment would query the
order module for details — thinner contract, but it reintroduces a synchronous dependency
and a read-your-writes race (the query may hit a replica that hasn't seen the order yet).
Neither is event sourcing: Postgres stores current state, events are an announcement, not
the system of record.

</details>

**Done when:** you can defend the classification and name the trade-off you'd accept by
switching to the other one.

### Step 2 — Prove it's broken (both directions)

**Goal:** turn "I heard dual writes are bad" into a red-bar-shaped fact.

Enable `Checkpoint2DualWriteTest` and run it:

```bash
mvn test -Dtest=Checkpoint2DualWriteTest
```

Both tests **pass** — and that is terrible news, because what they assert is inconsistency:

- `crashAfterCommitBeforeSend_theEventIsLost` — the order is in Postgres, Kafka never
  hears about it. Reconciliation is now somebody's on-call job.
- `crashAfterSendBeforeCommit_aGhostEventEscapes` — the transaction rolls back, yet the
  event is out. Fulfillment ships an order that does not exist.

Read `OrderService.place()` alongside the tests. Note that the "safe-looking" arm — sending
*inside* the transaction — is the one that produces the ghost: `KafkaTemplate.send()` is not
a transactional resource, it fires whether or not the surrounding transaction commits.

<details><summary>Hint — where is the commit?</summary>

`OrderService` uses a `TransactionTemplate` instead of `@Transactional` precisely so you can
*see* the commit: it happens when `executeWithoutResult` returns. With `@Transactional` the
commit hides at the method boundary — which is exactly how this bug hides in real codebases.
Trace each chaos arm and mark which side (DB, broker) has already made its write permanent
when `crashNow()` throws.

</details>

**Done when:** both checkpoint tests pass and you can explain, per arm, which write was
durable at the moment of the crash.

### Step 3 — Fix it with the outbox

**Goal:** replace the direct `KafkaTemplate` call with one local transaction that covers the
order *and* the publication, letting Spring Modulith's registry relay the event.

1. Annotate the event so Modulith externalizes it to Kafka:

   ```java
   @Externalized(OrderPlaced.TOPIC)   // org.springframework.modulith.events.Externalized
   public record OrderPlaced(...) { ... }
   ```

2. Rewrite `OrderService.place(...)`: delete the whole `switch`, publish an application
   event inside the transaction instead of sending to Kafka. Keep **one** chaos hook inside
   the transaction — same crash, new outcome:

   <details><summary>Hint — the target shape</summary>

   ```java
   private final OrderRepository orders;
   private final ApplicationEventPublisher events;
   private final TransactionTemplate transactions;
   private final ChaosMonkey chaos;

   public UUID place(String customer, List<OrderItem> items) {
       var order = Order.place(customer, items);
       transactions.executeWithoutResult(tx -> {
           orders.insert(order);
           events.publishEvent(OrderPlaced.from(order));
           chaos.maybeCrash(CrashPoint.AFTER_SEND_BEFORE_COMMIT);
       });
       return order.id();
   }
   ```

   `KafkaTemplate` and `JsonMapper` leave the constructor;
   `ApplicationEventPublisher` arrives. `OrderService` no longer knows Kafka exists —
   that knowledge moved to one annotation on the event.

   </details>

3. Enable `Checkpoint3OutboxTest`:
   - `crashBeforeCommit_leavesNoTraceAnywhere` — the same chaos that produced the ghost in
     step 2 now rolls back *everything*: no order, no outbox row, no Kafka record. The
     publication registration participates in your transaction — that is the whole pattern.
   - `brokerOutage_eventWaitsInTheOutboxAndIsDeliveredAfterRecovery` — with the broker
     "down", `place()` **succeeds anyway**. The event sits in `event_publication` with
     `completion_date IS NULL`; after the broker heals, `IncompleteEventPublications
     .resubmitIncompletePublications(...)` re-relays it (the test's stand-in for a restart
     with `spring.modulith.events.republish-outstanding-events-on-restart=true`).

4. Look at your outbox with your own eyes. While a test is paused or against the `local`
   profile:

   ```sql
   SELECT id, event_type, listener_id, publication_date, completion_date
   FROM event_publication;
   ```

5. Run `Checkpoint2DualWriteTest` again. **It must fail now** — the inconsistencies it pins
   are gone. Re-disable it with a new reason:
   `@Disabled("Documented the dual-write bug — superseded by the outbox in step 3")`.

**Done when:** checkpoint 3 is green, checkpoint 2 fails (and is re-disabled as an exhibit),
and the happy-path test never blinked.

### Step 4 — Feel the guarantee shift

**Goal:** understand what you traded for atomicity: delivery is now at-least-once, and your
consumer is not ready for it.

Enable `Checkpoint4DuplicateDeliveryTest`. It delivers the *same* `OrderPlaced` (identical
`eventId`) twice — exactly what a relay retry, an offset-commit crash or a registry
resubmission produces — and asserts the fulfillment table **double-counted**. It passes:
the bug is pinned.

Before moving on, list where duplicates come from in this very codebase: the registry
resubmits publications whose completion was never recorded (send succeeded, ack lost);
a consumer that crashes after `recordTask` but before committing its offset re-reads the
record on restart; a rebalance re-assigns a partition mid-flight. None of these are
exotic — at scale they are Tuesday.

**Done when:** checkpoint 4 passes and you can name three concrete duplicate sources
without looking at the paragraph above.

### Step 5 — Make the consumer idempotent

**Goal:** duplicates stay; their effect goes. Track processed `eventId`s in the consumer's
database, in the same transaction as the consumer's write.

1. Add the table to `schema.sql`:

   ```sql
   CREATE TABLE IF NOT EXISTS processed_messages (
       event_id     uuid PRIMARY KEY,
       processed_at timestamptz NOT NULL DEFAULT now()
   );
   ```

2. Give `FulfillmentRepository` a claim-check method — atomic thanks to the primary key:

   <details><summary>Hint — INSERT … ON CONFLICT</summary>

   ```java
   /** @return true if this event was never seen before (and is now claimed). */
   public boolean markProcessed(UUID eventId) {
       return jdbc.sql("INSERT INTO processed_messages (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
               .param("id", eventId)
               .update() > 0;
   }
   ```

   </details>

3. Guard the listener — the guard and the write must share one transaction, otherwise a
   crash between them either loses the event or reopens the duplicate window:

   <details><summary>Hint — the guarded listener</summary>

   ```java
   @KafkaListener(topics = OrderPlaced.TOPIC)
   @Transactional
   void on(OrderPlaced event) {
       if (!fulfillment.markProcessed(event.eventId())) {
           return;   // a redelivery, not news
       }
       fulfillment.recordTask(event);
   }
   ```

   </details>

4. Enable `Checkpoint5IdempotentConsumerTest` — same double delivery, exactly one
   fulfillment task, exactly one `processed_messages` row. Then re-run checkpoint 4: it
   must fail now. Retire it like checkpoint 2.

Note what you did *not* need: no broker feature, no distributed lock, no exactly-once
add-on. A primary key and a local transaction.

**Done when:** checkpoint 5 is green and checkpoint 4 is red (re-disabled with an updated
reason).

### Step 6 — Debrief: guarantees, and when to reach for CDC

**Goal:** leave with the 2026 defaults and the words to defend them.

No test for this step — read, compare, decide. The pipeline you built is: **atomic intent
capture** (outbox in the business transaction) → **at-least-once relay** (registry +
resubmission) → **idempotent application** (processed_messages). Each stage covers the
failure mode the previous one cannot.

The registry relay you used is in-process and polling-flavored. Its alternative is CDC:

| | Modulith registry (polling relay) | Debezium CDC (log tailing) |
|---|---|---|
| Extra infrastructure | none — a table in your DB | Kafka Connect cluster + connector config |
| Latency | good in-process; resubmission adds lag after crashes | near-real-time, WAL-driven |
| Load on database | polling/completion updates on the publication table | reads the WAL, not your tables |
| Multi-instance apps | care needed (double relay ⇒ more duplicates — still safe if consumers are idempotent; Modulith 2.1 adds ordered outbox integration) | Connect coordinates; single logical relay |
| Event schema on the wire | your serialized event | outbox row columns (id, type, payload) via event router |
| Coupling | Spring-only, zero-ops, one dependency | language-agnostic; ops burden is real |
| Reach for it when | modular monolith or small service count; Spring end-to-end; ops budget minimal | high throughput, many producer instances, polyglot consumers, latency-sensitive relays |

Whichever relay you pick, the two ends do not change: transaction-local capture on the
producer, idempotency on the consumer. That symmetry — not any broker feature — is what
people actually mean when they claim "exactly-once" in production systems. Read Helland's
"Idempotence Is Not a Medical Condition", then read Confluent's "Exactly-Once Semantics Are
Possible" *critically* against it: notice how carefully the latter scopes its claim to the
Kafka-internal loop.

**Done when:** you can answer self-check questions 6–8 out loud, without hedging.

## Self-check

1. `PlaceOrder` vs `OrderPlaced` — why can only one of them be rejected, and what does that
   imply about who owns the resulting behavior?
2. Why doesn't wrapping the DB write and `kafka.send()` in one `@Transactional` method fix
   the dual write? What *does* the transaction roll back, and what does it not?
3. Which chaos point produces a lost event and which a ghost event — and which of the two
   would hurt more in a payment flow?
4. What exactly does a row in `event_publication` represent? When is `completion_date` set,
   and by whom?
5. After the outbox fix, why did `place()` *succeed* while the broker was down — and why is
   that the correct behavior rather than a bug?
6. Why must `markProcessed` and `recordTask` share one transaction? Describe the failure if
   the guard committed separately, in each order.
7. Kafka's EOS: which loop does `processing.guarantee=exactly_once_v2` actually protect,
   and why does the fulfillment table's insert fall outside it?
8. Your team runs 40 services in three languages and relays are saturating the publication
   tables — polling or CDC, and which three rows of the step-6 table decide it?

## Stretch goals

- **The CDC variant.** `docker-compose.debezium.yml` starts Postgres (with
  `wal_level=logical`), Kafka and Debezium Connect 3.6. Create a classic outbox table
  (`id uuid, aggregatetype text, aggregateid text, type text, payload text`), write to it in
  `place()`'s transaction (instead of — or alongside — the registry), then register the
  provided outbox event router config:
  `curl -X POST -H 'Content-Type: application/json' --data @debezium/register-outbox-connector.json localhost:8083/connectors`.
  The router sends rows with `aggregatetype = 'OrderPlaced'` to `orders.OrderPlaced` —
  watch your existing consumer process them unchanged.
- **Ordering by key.** Externalize with a partition key:
  `@Externalized("orders.OrderPlaced::#{#this.orderId().toString()}")`. Explain what is now
  ordered, what still is not, and why the fulfillment table can't tell the difference.
- **Poison messages.** Make the listener throw for one specific order and add a
  `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer` — where do retries end and
  the DLT begin, and how does that interact with `processed_messages`?
- **Outbox hygiene.** The `event_publication` table only grows. Investigate
  `spring.modulith.events.completion-mode=DELETE` and
  `CompletedEventPublications.deletePublicationsOlderThan(...)`; decide on a retention
  policy and defend it (hint: completed rows are also your audit/debug trail).

## Resources

- **Chris Richardson — "Pattern: Transactional Outbox"** (microservices.io) — the canonical
  pattern page, with the polling-vs-log-tailing relay split used in step 6.
- **Gunnar Morling — "Reliable Microservices Data Exchange With the Outbox Pattern"**
  (debezium.io blog) — the reference CDC-outbox implementation article; the stretch goal is
  a scale model of it.
- **Pat Helland — "Idempotence Is Not a Medical Condition"** (ACM Queue) — the timeless
  paper on why duplicates are inevitable and receivers must own the consequences.
- **Martin Fowler — "What do you mean by 'Event-Driven'?"** (martinfowler.com) — the
  four-pattern taxonomy behind step 1.
- **Conduktor — "Build Idempotent Kafka Consumers: Patterns That Actually Work"** — the
  practical consumer-side recipes; step 5 implements their tracking-table pattern.
- **Spring Modulith reference documentation** — chapters "Working with Application Events"
  and "Event Publication Registry" (spring.io/projects/spring-modulith); Oliver Drotbohm's
  talks for the design rationale.
- **Dan Vega — "Spring Modulith Externalized Events: Publishing Events to Kafka"**
  (danvega.dev) — an accessible walk-through of exactly the step-3 wiring.
- **Neha Narkhede — "Exactly-Once Semantics Are Possible: Here's How"** (Confluent) — read
  critically against Helland, as instructed in step 6.
- **Martin Kleppmann & Chris Riccomini — *Designing Data-Intensive Applications*, 2nd ed.**
  (O'Reilly) — chapters on delivery guarantees and derived data for the deep grounding.
