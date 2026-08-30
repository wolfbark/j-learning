# 11 — Microservices: The Real Cost of Distribution

> After this lesson you can make a synchronous two-service system fail the way distributed
> systems actually fail — cascading latency, thread starvation, ambiguous outcomes — and then
> pay down each cost deliberately: timeouts and degraded states, bounded retries, asynchronous
> events, and a correlation id that survives the process boundary. And you can argue, with
> numbers, when *not* to do any of this.

## Why this matters (2026)

Every previous project in this repo ran in one JVM. Project 06 gave you modules with enforced
boundaries; project 07 taught events and the outbox — but a method call was still a method
call: it either happened or it threw, in nanoseconds, atomically with your transaction.

This project splits the system in two, and everything you relied on quietly disappears. A call
can now *partially* happen. It can succeed but take five seconds. It can succeed on the other
side while you receive an error. That is not an edge case of microservices — it **is**
microservices; the rest is mitigation.

The industry's 2026 position is worth stating plainly, because this lesson is built on it
(source: [docs/research/architecture-styles.md](../docs/research/architecture-styles.md),
sections 1 and 5):

- **"Default no, deliberate yes."** A widely cited CNCF figure says ~42% of organizations that
  adopted microservices have consolidated at least some services back into larger units;
  Gartner found ~60% of teams regretted microservices for small/medium apps. Amazon Prime
  Video's Video Quality Analysis team moving back to a monolith — cutting infrastructure cost
  by 90% — remains the canonical cautionary tale.
- **The justification is organizational, not technical.** The 2026 heuristics: roughly 50+
  engineers needing genuinely independent deploys, or truly divergent scaling/compliance needs.
  "We might need to scale later" is not on the list. Fowler's **MonolithFirst** and the
  strangler fig are the accepted playbook; "modulith-first, extract when proven" (project 06)
  is the mainstream recommendation for new systems.
- **When you do distribute, the 2026 pattern is hybrid:** REST/OpenAPI at the edge, gRPC for
  internal synchronous calls (first-class via the official Spring gRPC project), and
  **async-first between services** to cut temporal coupling — which is exactly the arc of this
  lesson's guided steps.

So why practice building one at all? Because the moment you *do* need distribution — one
noisy integration, one compliance boundary, one team that must deploy alone — you need to know
the tax schedule before you sign. This lesson makes you pay each tax in miniature, on your
laptop, where it's cheap.

What this lesson deliberately keeps introductory: circuit breakers, bulkheads, rate limiting
and the full observability stack (metrics, traces, SLOs) are **project 15's** job. Here you
meet the first rung of each ladder — the timeout, the bounded retry, the correlation id — and
learn why the ladder exists.

## Core concepts

### A method call became a network call

The eight fallacies of distributed computing (Peter Deutsch and colleagues at Sun, 1994) are
old enough to drink, and teams still rediscover them one incident at a time: *the network is
reliable; latency is zero; bandwidth is infinite; the network is secure; topology doesn't
change; there is one administrator; transport cost is zero; the network is homogeneous.*

The first two do the most damage, and they do it *silently*. A remote call that fails loudly
is easy; the killer is the call that **succeeds slowly**. No exception is thrown, no error is
logged — your service is simply held hostage, one servlet thread at a time.

### Synchronous coupling: the math is against you

Chain services synchronously and two multiplications happen, both against you:

- **Availability multiplies down.** Two services at 99.9% availability, called in series, give
  you 99.8%. Five give you 99.5% — you lost half a "nine" without any component getting worse.
  A synchronous chain is *at most* as available as its least available link, and always worse.
- **Latency adds up — and tails compound.** Your p99 is built from your callee's p99. If order
  placement calls shipping while the customer waits, shipping's bad day *is* your bad day.
  Worse: while a thread waits on a slow callee it serves nobody else, so slowness converts
  into unavailability once the pool drains. That conversion — latency into outage — is the
  cascade you will produce on demand in step 2.

The phase-1 scaffold commits both classic sins on purpose: **no timeout configured** (the
default of nearly every HTTP client, including the JDK's) and **no degraded state** — shipping's
failure becomes the customer's HTTP 500, with the order left in limbo.

### Timeouts, retries, and budgets

The timeout is the first resilience tool because it converts *unbounded* waiting into a
*bounded*, handleable failure. Two rules:

1. **Every remote call has a deadline.** No exceptions. An unset timeout is a bet that a
   machine you don't control will never misbehave.
2. **A timeout without a fallback is just a faster error.** Decide *what the caller gets
   instead*: here, an order accepted as `SHIPPING_PENDING` — a business answer, not a stack
   trace.

Retries are the second tool and the first foot-gun. A retry is a bet that the failure was
transient; against an overloaded service it is a self-inflicted DDoS — each layer that retries
multiplies traffic (three attempts at three layers = 27× load, precisely when capacity is
lowest). Hence **retry budgets**: bounded attempts, exponential backoff, jitter to avoid
thundering herds, and a worst-case total that must fit inside your own SLA — you'll do that
arithmetic in step 3. The grown-up version (circuit breakers that stop betting entirely) is
project 15.

### Temporal decoupling: the async escape hatch

Every mitigation so far accepts the premise that placing an order *requires shipping to be up
right now*. Step 4 rejects the premise. Publish `OrderPlaced` to a topic; let shipping consume
it when it can; let a `ShipmentArranged` event flow back. Now:

- Shipping can be **down for an hour** and order-taking doesn't blink — the queue absorbs the
  outage and shipping catches up on restart (you will watch this happen).
- The price is honesty about time: the customer gets "accepted, shipping pending" instead of
  "confirmed" — the degraded state you built in step 2 becomes the *normal* state, promoted
  from fallback to design.
- The other price is delivery semantics: Kafka gives you **at-least-once**, so consumers must
  tolerate duplicates. And publishing an event after a DB commit without an outbox is the
  **dual write** you dissected in project 07 — this scaffold's step-4 shape has exactly that
  flaw, deliberately; recognizing it is part of the step. One paragraph of recap lives there;
  the full treatment is project 07's.

### Correlation across the gap

In one process, a stack trace tells the story of a request. Across two processes and a broker,
*nothing* does — unless you thread an identifier through every hop yourself: HTTP header in,
MDC for logging, Kafka record header out, MDC again on the far side. That id is the difference
between "grep two log files, see one story" and archaeology.

Be honest about what it is: a hand-rolled correlation id is **one tenth of observability**. It
gives you *which* log lines belong together — not timing, not causality, not sampling, not a
waterfall. Micrometer Tracing with an OpenTelemetry bridge does all of that with W3C
`traceparent` propagation, and project 15 sets it up properly. Step 5 hand-rolls the id
precisely so you know what the tracing libraries automate.

## The project

Two Spring Boot 4 services. An order requires a shipment; how the order-service gets one is
the whole lesson.

```
        POST /orders                      POST /shipments
customer ──────────► order-service ─────────────────────► shipping-service
                     (Postgres)      synchronous HTTP      (in-memory store)
                                     no timeout, phase 1    + chaos switch
                          │                                      │
                          └––––– step 4 replaces the arrow ––––––┘
                                 with Kafka: orders.placed →
                                 ← shipments.arranged
```

**Structure note:** unlike every other lesson, this directory holds **two standalone Maven
projects** — `order-service/` and `shipping-service/` — because separate deployables *are the
subject*. Each builds and tests on its own; there is no shared code, no parent POM. The only
contract between them is HTTP (phase 1) and two Kafka topics (phase 4) — which is the point.

**What's given:**

- `order-service` — `POST /orders`, `GET /orders/{id}`; Postgres via JDBC; a declarative
  `@HttpExchange` client for shipping (`ShippingClient`), registered with
  `@ImportHttpServices` and configured only with a base URL — **deliberately no timeouts**;
  order statuses `PLACED → CONFIRMED` (plus `SHIPPING_PENDING`, unused so far). Shipping
  failure = customer-facing 500 and an order stuck in `PLACED`. That ambiguity is phase 1's
  honest face.
- `shipping-service` — `POST /shipments`, plus the chaos switch:
  `POST /chaos {"mode":"OK"|"SLOW_5S"|"DOWN"}` changes how every subsequent `/shipments` call
  behaves. Failure on demand, no waiting for production.
- Both services: a `CorrelationIdFilter` (accepts/mints `X-Correlation-Id`, puts it in the
  MDC, echoes it back) and a log pattern that prints it.
- `docker-compose.yml` — Kafka (KRaft, single node), Postgres, both services.
- `demo.sh` — curls the system through happy path and both chaos modes, printing timings.
- Enabled tests: order-service happy path against a WireMock shipping stub (real HTTP on real
  sockets — latency included); shipping-service API and chaos behavior.
- `@Disabled` checkpoint tests for steps 2–5 in both services.

**Run it:**

```bash
# build the jars, then bring up the system
mvn -q -f order-service/pom.xml package -DskipTests
mvn -q -f shipping-service/pom.xml package -DskipTests
docker compose up --build -d

./demo.sh                     # the guided tour
docker compose logs -f order-service shipping-service   # watch both stories

# tests, per service (Docker needed for Testcontainers)
mvn -f order-service/pom.xml test
mvn -f shipping-service/pom.xml test
```

Ports: order-service `:8080`, shipping-service `:8081`, Kafka `localhost:9092` (host) /
`kafka:19092` (inside compose), Postgres `localhost:5433`.

## Guided steps

Checkpoint tests live in each service's `src/test/java`, annotated
`@Disabled("Checkpoint N — enable when you start step N")`. Remove the annotation when you
reach the step. Two classes are **exhibits** that pass against the *broken* code and must be
re-disabled after their step (`Checkpoint2aCascadeDemoTest`, and `Checkpoint3RetryTest` once
step 4 removes the code it exercises) — the same rhythm as project 07: demonstrate the
disease, then cure it, and keep the demonstration around as history.

### Step 1 — Feel the latency you inherited

**Goal:** experience the fallacies with a stopwatch, not a slide.

Bring the system up and run `./demo.sh`. Watch part 2: after `chaos SLOW_5S`, placing an order
takes just over five seconds — the order-service did nothing wrong, called nothing extra,
threw nothing. Then part 3: shipping DOWN turns into *your* HTTP 500.

While it runs, check the order-service logs: during the slow call there is no error, no
warning — nothing. The service is degrading invisibly. Then re-read the "Why this matters"
numbers with your own measurement next to them: one hop cost you 5 seconds; Amazon's Prime
Video team was paying for hops like this at scale, and stopped.

**Done when:** you can name which two fallacies you just measured, and can say what the
customer saw in each chaos mode versus what actually happened to their order (hint: in DOWN
mode, check Postgres — the order row exists, in `PLACED`, forever).

### Step 2 — The cascade, then the containment

**Goal:** watch slowness become unavailability; then put a deadline and a degraded state on
the call so the orders API answers within a 2-second SLA no matter what shipping does.

First the exhibit. In `order-service`, enable `Checkpoint2aCascadeDemoTest` and run it:

```bash
mvn -f order-service/pom.xml test -Dtest=Checkpoint2aCascadeDemoTest
```

Both tests **pass**, and that is the bad news:

- `theCallerInheritsTheCalleesLatency` — a 3-second shipping stub makes `POST /orders` take
  3 seconds. Nobody chose that latency; it was inherited.
- `slowShippingStarvesRequestsThatNeverTouchShipping` — with Tomcat pinned to two worker
  threads, two slow orders park *both* of them inside shipping, and an innocent `GET` that
  needs no remote call queues behind them. Latency has become unavailability. Production pools
  are bigger; production traffic is too — the arithmetic is identical, only slower to detonate.

Now the cure. Enable `Checkpoint2TimeoutsAndFallbackTest` (red on the pristine code) and make
it green:

1. Give the shipping client a deadline. The client is auto-configured, so this is
   configuration, not code — in `order-service/src/main/resources/application.yaml`:

   <details><summary>Hint — the exact properties</summary>

   ```yaml
   spring:
     http:
       serviceclient:
         shipping:
           base-url: ${SHIPPING_BASE_URL:http://localhost:8081}
           connect-timeout: 500ms
           read-timeout: 500ms
   ```

   These bind per-group under `spring.http.serviceclient.<group>.*` — the group name is the
   one in `@ImportHttpServices(group = "shipping", ...)` on `OrderServiceApplication`.

   </details>

2. Decide what the customer gets when the deadline fires. Catch the failure in
   `OrderService.place(...)` and accept the order as `SHIPPING_PENDING` instead of failing it.

   <details><summary>Hint — what to catch, and the shape</summary>

   Timeouts surface as `ResourceAccessException`, shipping's 503 as
   `HttpServerErrorException` — both are `RestClientException`:

   ```java
   var order = Order.placed(customerId, item, quantity);
   repository.insert(order);
   try {
       var shipment = shipping.arrange(new ShipmentRequest(order.id(), item, quantity));
       repository.updateStatus(order.id(), OrderStatus.CONFIRMED, shipment.shipmentId());
   } catch (RestClientException e) {
       log.warn("shipping unavailable for order {} — accepting as SHIPPING_PENDING", order.id(), e);
       repository.updateStatus(order.id(), OrderStatus.SHIPPING_PENDING, null);
   }
   return repository.findById(order.id()).orElseThrow();
   ```

   Note what you just did: you turned an infrastructure failure into a *business state* that
   someone (a re-driver job, an ops dashboard — step 4 has a better answer) must own. A
   fallback that nobody watches is a silent order-loss machine.

   </details>

3. Re-run the exhibit: `Checkpoint2aCascadeDemoTest` must now **fail** — calls come back in
   ~500ms, nothing starves. Re-disable it with a note; it documents what you fixed. Then run
   `./demo.sh` against the composed system with chaos DOWN: the customer now gets a 201.

**Done when:** `Checkpoint2TimeoutsAndFallbackTest` is green, the exhibit fails and is
re-disabled, and you can explain why the read-timeout you chose (500ms) plus the SLA (2s)
leaves room for step 3.

### Step 3 — Retry, but with a budget

**Goal:** absorb transient blips without human-visible failure — while proving the retry is
*bounded* and still fits the SLA.

Enable `Checkpoint3RetryTest`. The first test simulates a deploy blip (one 503, then success)
and demands the customer never notices; the second pins the budget: against a dead shipping
service, **exactly three attempts**, then degrade — within the 2-second SLA.

Spring Framework 7 has retry in the core, no extra dependency:

<details><summary>Hint — @Retryable on a gateway seam</summary>

Don't annotate `OrderService.place(...)` — that would retry the DB insert too. Give the remote
call its own seam (the same seam a circuit breaker will want in project 15):

```java
@Component
public class ShippingGateway {

    private final ShippingClient client;

    public ShippingGateway(ShippingClient client) {
        this.client = client;
    }

    @Retryable(includes = { HttpServerErrorException.class, ResourceAccessException.class },
               maxRetries = 2, delay = 50, jitter = 25, multiplier = 2.0)
    public ShipmentResponse arrange(ShipmentRequest request) {
        return client.arrange(request);
    }
}
```

Plus `@EnableResilientMethods` on `OrderServiceApplication`, and `OrderService` now calls the
gateway. Imports: `org.springframework.resilience.annotation.Retryable` /
`EnableResilientMethods` — Spring Framework 7 core, not the old spring-retry add-on.

`includes` matters: a 4xx means *you* sent garbage — retrying it is spam. Retry only what can
plausibly heal: 5xx and I/O failures.

</details>

Do the budget arithmetic before you run: worst case = 3 attempts × 500ms read-timeout
+ backoff delays (~50ms + ~100ms, jitter ±25ms) ≈ **1.75s** < 2s SLA. If you'd kept a 1s
read-timeout, the same three attempts would blow the SLA — the numbers are a system, not
individual knobs. And the reason `maxRetries = 2` and not "until it works": each retry
multiplies load on a service that is *already failing* — naive retries are how one slow
dependency takes down a fleet. The tool that stops betting on a dead horse entirely is the
circuit breaker (project 15).

**Done when:** both checkpoint tests are green and you can recite your worst-case latency from
the numbers in your annotation.

### Step 4 — Cut the wire: events instead of calls

**Goal:** remove the synchronous dependency altogether. Order placement publishes
`OrderPlaced` to Kafka; shipping consumes it and announces `ShipmentArranged`; the
order-service confirms the order when the reply arrives. Shipping being down stops mattering
at order time.

The contract is two topics with JSON payloads — defined by the checkpoint tests on *both*
sides, so the services agree without sharing a single class:

| topic | key | value |
|---|---|---|
| `orders.placed` | orderId | `{"orderId":"…","item":"…","quantity":n}` |
| `shipments.arranged` | orderId | `{"orderId":"…","shipmentId":"SHP-…","status":"ARRANGED"}` |

Enable `Checkpoint4AsyncOrderTest` (order-service) and `Checkpoint4ShippingConsumerTest`
(shipping-service). On the order side: `place(...)` now inserts the order as
`SHIPPING_PENDING`, publishes, returns immediately — note the checkpoint runs with the
shipping stub *down* and verifies **zero** HTTP calls. The degraded state you built in step 2
just became the normal initial state; that is not an accident, it's the async design being
honest that confirmation takes time. On the shipping side: a `@KafkaListener` consumes,
arranges, produces the reply.

<details><summary>Hint — the moving parts (order-service side)</summary>

```java
public record OrderPlaced(UUID orderId, String item, int quantity) {}

// in OrderService (KafkaTemplate<String,String> and ObjectMapper are auto-configured;
// ObjectMapper is Jackson 3: tools.jackson.databind.ObjectMapper)
repository.insert(order.withStatus(OrderStatus.SHIPPING_PENDING));   // or build it PENDING
kafka.send("orders.placed", order.id().toString(),
        mapper.writeValueAsString(new OrderPlaced(order.id(), item, quantity)));

@Component
class ShipmentArrangedListener {
    @KafkaListener(topics = "shipments.arranged")
    void on(String payload) {
        // parse, then repository.updateStatus(orderId, CONFIRMED, shipmentId)
    }
}
```

The shipping side mirrors it: listener on `orders.placed`, `store.save(Shipment.arranged(...))`,
send on `shipments.arranged`. Serializers are already configured to String in both
`application.yaml`s; the group ids too. `ShippingClient`, the gateway and the retry become
dead code — delete them or leave them for the gRPC stretch goal, but *nothing* may call them.

</details>

Two honesty checks, then the fun part:

- **You just wrote a dual write.** `INSERT` into Postgres, then `send()` to Kafka — no shared
  transaction. Crash between the two and the order sits in `SHIPPING_PENDING` forever, event
  lost. You proved this failure mode exists in project 07, and fixed it there with the
  transactional outbox (Spring Modulith's event publication registry — one transaction covers
  the row and the intent to publish; a relay delivers with retry). This lesson doesn't rebuild
  it; know that production code here would need it, and where to copy it from.
- **At-least-once means duplicates.** If shipping crashes after arranging but before
  committing its consumer offset, redelivery arranges a *second* shipment for the same order.
  Project 07's idempotent-consumer pattern is the fix; the stretch goal lets you provoke it.
- Two enabled happy-path tests pin the synchronous contract you are deleting —
  `placingAnOrderArrangesShippingAndConfirms` asserts `CONFIRMED` and a shipping HTTP call.
  Rewrite it for the new contract (or replace it with the checkpoint's assertions): the POST
  now returns `SHIPPING_PENDING` and nobody calls `/shipments`. Contracts changed; tests that
  pin contracts change with them.

The system exercise — this is the payoff, do not skip it:

```bash
docker compose up --build -d          # rebuild with the new code
docker compose stop shipping-service  # shipping is now OFF
./demo.sh                             # orders: 201, SHIPPING_PENDING — no errors, no 5s waits
docker compose start shipping-service
docker compose logs -f shipping-service order-service
```

**Done when:** all four checkpoint tests (two per service) are green, and you have watched
orders placed *during* the shipping outage get confirmed after the restart — the queue
absorbed an outage that phase 1 would have served to customers as errors.

### Step 5 — One id across the gap

**Goal:** make a single customer action traceable through both services and the broker:
same correlation id in the order-service log line, the Kafka record header, and the
shipping-service log line.

The filter already puts `X-Correlation-Id` into the MDC and both log patterns already print
it (`[order-service,a1b2c3d4]`). What's missing is propagation: the id dies at the process
boundary. Enable `Checkpoint5CorrelationTest` (order-service) and
`Checkpoint5ShippingCorrelationTest` (shipping-service).

<details><summary>Hint — headers out, MDC in, headers out again</summary>

Producing (order-service) — put the MDC value on the record:

```java
var record = new ProducerRecord<>("orders.placed", order.id().toString(), payload);
record.headers().add(CorrelationIdFilter.HEADER,
        MDC.get(CorrelationIdFilter.MDC_KEY).getBytes(StandardCharsets.UTF_8));
kafka.send(record);
```

Consuming (shipping-service) — take `ConsumerRecord<String, String>` instead of `String`,
restore the MDC, and copy the header onto the reply:

```java
@KafkaListener(topics = "orders.placed")
void on(ConsumerRecord<String, String> record) {
    var header = record.headers().lastHeader(CorrelationIdFilter.HEADER);
    MDC.put(CorrelationIdFilter.MDC_KEY,
            header == null ? "no-corr" : new String(header.value(), StandardCharsets.UTF_8));
    try {
        // ... arrange, and add the same header to the outgoing ProducerRecord
    } finally {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
}
```

The reply path back into order-service deserves the same treatment.

</details>

Then prove it end to end, the way you would during an incident:

```bash
curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: incident-4711' -d '{"customerId":"ada","item":"duck","quantity":1}'
docker compose logs | grep incident-4711     # one story, two services, in order
```

Now the honest framing: you hand-rolled ~1/10th of observability. The id tells you *which*
lines belong together; it does not time spans, record causality, sample intelligently, or
draw waterfalls. Micrometer Tracing (with the OpenTelemetry bridge) propagates W3C
`traceparent` over HTTP *and* Kafka automatically, including the MDC plumbing you just wrote
by hand. Project 15 wires it up; today you know exactly what it saves you.

**Done when:** both checkpoint tests are green and one grep across both services' logs tells
one ordered story.

### Step 6 — Debrief: when to distribute (no code)

**Goal:** turn the pain into judgment. Answer these in writing, one paragraph each — commit
the file next to this README if you like.

1. **Should Order and Shipping be two services at all?** Use the research heuristics: How many
   teams? Do they need independent deploys *today*? Divergent scaling or compliance? For this
   toy: almost certainly no — one modulith with an `orders` and a `shipping` module (project
   06) plus an outbox (project 07) delivers the same decoupling without the network in the
   middle. Write down what would have to become true to flip the answer.
2. **The middle path you skipped:** Self-Contained Systems (scs-architecture.org) — a handful
   of coarse verticals, each owning **UI + logic + data**, integrating asynchronously or via
   links, each a small monolith with a face. Relevant to Vaadin developers specifically:
   SCS is UI-inclusive, so a server-side UI stack is a natural fit — see Simon Martinelli's
   "Goodbye Microservices, Hello Self-contained Systems" (2025). Where would the UI live in
   *this* system's SCS version?
3. **Cell-based architecture** — awareness only: partition the *whole system* into
   self-contained cells (all services + data per cell), route each customer to exactly one,
   and cap the blast radius of any failure. DoorDash, Slack and AWS run this at serious scale;
   below multi-region scale it's not for you — but notice it composes with (rather than
   replaces) everything you built today.
4. **Service mesh in 2026, honestly:** classic sidecar meshes declined sharply (one analysis:
   18% adoption in 2023 → 8% in 2025) because the per-pod tax rarely paid for itself;
   Istio's sidecar-less **ambient mode** went production-grade (~70% resource savings) and is
   driving a partial comeback. The 2026 guidance: most Java teams don't need a mesh at all —
   mTLS, retries and telemetry can live in the app (you just built two of the three); teams
   that do need one start with ambient mode or Gateway API + Cilium. What in this lesson would
   a mesh have given you "for free", and what would it still not solve? (Hint: nothing in
   step 4 or 6 — a mesh does not do business fallbacks or event design.)
5. **gRPC note:** had you kept the synchronous phase, the 2026 idiom for *internal* calls is
   gRPC via the official Spring gRPC project (contract-first `.proto`, HTTP/2, deadlines as a
   first-class request property — note: deadlines built in, where HTTP made you go find the
   timeout knob). It's the stretch goal below.

**Done when:** you can argue *both* directions for this system — the case for two services and
the case for one — and say which you'd ship, and why, in front of people who disagree.

## Self-check

1. Two services at 99.9% availability called synchronously in series: what's the ceiling on
   the chain's availability, and why does going async change the *question* rather than the
   number?
2. Why is "no timeout" the default in most HTTP clients, and what exactly happened to Tomcat's
   worker threads in `Checkpoint2aCascadeDemoTest` while shipping was slow?
3. Your SLA is 2s and your read-timeout is 500ms. Show the arithmetic that says whether 3
   retry attempts with 50ms/multiplier-2 backoff fit. What single config change silently
   breaks it?
4. Why must retries be restricted to 5xx/I-O failures — what goes wrong (twice) when you retry
   a 400? Why jitter?
5. After step 4, where did the "shipping is down" problem *go*? It didn't disappear — name the
   new place it lives and who has to watch it (two answers: a queue metric, and a
   `SHIPPING_PENDING`-age alert).
6. The step-4 code inserts the order and then publishes to Kafka. Which crash loses the event,
   what's the fix called, and in which project of this repo did you build it?
7. A correlation id and a distributed trace both connect log lines across services. Name three
   things the trace gives you that the id cannot.
8. Your team is 12 engineers, one product, one deploy cadence — a colleague proposes
   extracting three more services "for scalability". Which two findings from this lesson do
   you put on the table first, and what alternative architecture do you offer?

## Stretch goals

- **gRPC for the internal call (reading + code).** Resurrect the synchronous phase on a
  branch and convert `ShippingClient` to gRPC with the official **Spring gRPC** project:
  contract-first `shipping.proto`, generated stubs, and a client-side *deadline* instead of a
  read-timeout. Notice what HTTP made optional, gRPC makes explicit.
- **Provoke the duplicate.** In shipping-service, make the listener throw *after*
  `store.save(...)` but before the reply is sent (a chaos hook exists — wire `ChaosMode.DOWN`
  into the listener). Watch redelivery create a second shipment for the same order, then fix
  it with project 07's idempotent-consumer pattern (track processed orderIds, skip repeats).
- **Retry budget under pressure.** Load the phase-3 system with concurrent orders
  while shipping is `SLOW_5S` (a shell loop with `curl ... &` or `hey`/`oha` if installed) and
  watch what bounded retries + timeouts do to throughput versus the pristine scaffold —
  measure, don't vibe.
- **Two cells, one toy.** Duplicate the compose stack (different ports/project name), put a
  10-line reverse proxy in front that routes `customerId` hashes to cell A or B, kill cell A,
  and verify cell B customers never notice. Cell-based architecture at 1:1000 scale.

## Resources

- **Chris Richardson — *Microservices Patterns, 2nd Edition*** (Manning MEAP) and the
  microservices.io pattern catalog — the pattern vocabulary this lesson leans on (timeouts and
  retries live under "Circuit Breaker"'s family; the events flow is "Saga"-adjacent).
- **Sam Newman — *Building Microservices, 2nd Edition*** (O'Reilly) and *Monolith to
  Microservices* — the decision frameworks behind step 6, including "don't start here".
- **Martin Fowler — "Microservices" and "MonolithFirst"** (martinfowler.com) — still the
  framing texts; MonolithFirst is the one-page version of this lesson's conclusion.
- **Amazon Prime Video — "Scaling up the audio/video monitoring service and reducing costs by
  90%"** — the consolidation case study; read it for *why the architecture fit the workload*,
  not as a gotcha.
- **"Microservices vs Monoliths in 2026: When Each Architecture Wins"** (Java Code Geeks,
  Dec 2025) — the current-state decision framework used in steps 1 and 6.
- **Arnon Rotem-Gal-Oz — "Fallacies of Distributed Computing Explained"** — the canonical
  short paper on the eight fallacies you measured in step 1.
- **scs-architecture.org** and **Simon Martinelli — "Goodbye Microservices, Hello
  Self-contained Systems"** (2025) — the SCS middle path from step 6, UI included.
- **AWS Well-Architected — "Reducing the Scope of Impact with Cell-Based Architecture"** —
  the cell-based awareness reading.
- **Spring Framework 7 reference — "Resilience Features"** (`@Retryable`,
  `@ConcurrencyLimit`, `RetryTemplate`) and **Spring Boot reference — "Calling REST
  Services"** (HTTP service clients, `spring.http.serviceclient.*`) — the exact APIs used in
  steps 2–3.
- **Spring gRPC project** (spring.io/projects/spring-grpc) — for the stretch goal.
