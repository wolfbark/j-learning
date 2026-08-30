# 15 — Production Readiness: Timeouts, Retries, Breakers, and Seeing It All

> After this lesson you can harden a service against its own dependencies in the order that
> actually works — timeout, retry, bulkhead, breaker — prove each one with a test rather than a
> blog post, explain why a retry can make an outage worse, and read your own traces and breaker
> state instead of guessing.

## Why this matters (2026)

Every project up to here made something *work*. This one is about what happens at 03:00 when the
payment provider does not.

Two things changed recently enough to be worth re-learning:

**Resilience moved into Spring core.** Spring Framework 7 ships `@Retryable` and
`@ConcurrencyLimit` (plus a programmatic `RetryTemplate`) in the framework itself — activated with
`@EnableResilientMethods`. Retries, exponential backoff, and jitter no longer need the separate
spring-retry project. What core deliberately does *not* include is circuit breakers, rate limiters
and bulkhead metrics, so **Resilience4j is still the tool** for those. Knowing where that line falls
is the practical skill.

**Virtual threads removed your accidental bulkhead.** Project 14's punchline was that Loom moves the
bottleneck. It also removes a protection you did not know you had: when threads were scarce, the
thread pool throttled how much of your capacity one sick dependency could consume. Now that a thread
costs a kilobyte, 10 000 requests will all pile into a dying gateway unless you say otherwise. That
is why `@ConcurrencyLimit` is a step in this lesson rather than a footnote.

On observability, the war is over: **OpenTelemetry won the wire format**, and the only remaining
question is how you produce it — Micrometer's Observation API bridged to OTel (what we do here), or
the zero-code Java agent. Either way, the point is that you cannot tune a retry budget or a breaker
threshold you cannot see.

Source material: [`../docs/research/platform-and-production.md`](../docs/research/platform-and-production.md) §5.

## Core concepts

**The order is the lesson.** Applied in the wrong order these patterns hurt:

| # | Pattern | Fixes | Breaks if applied alone |
|---|---------|-------|-------------------------|
| 1 | **Timeout** | A call that never returns | — it is never optional |
| 2 | **Retry** (bounded, backoff, jitter) | Transient faults | Without a timeout, multiplies a hang |
| 3 | **Bulkhead** (`@ConcurrencyLimit`) | One dependency eating all capacity | — |
| 4 | **Circuit breaker** | Hammering something that is down | Without retry, trips on faults that would heal |

**A timeout is a promise about the worst case.** Without one, your latency is defined by someone
else's incident. Note the interaction you will measure in step 1: a timeout bounds *one attempt*, and
a retry multiplies whatever it wraps — three attempts at a one-second timeout is a three-second
request. Both facts are true; you have to design for their product.

**Retries are the dangerous one.** They fix exactly one thing — a fault that has gone away by the
time you ask again — and they make everything else worse. When a dependency is genuinely down, every
client's retries add load to a system already failing, in synchronised waves if you forgot jitter.
This is the mechanism behind a large share of real cascading outages. Bound them, back them off,
jitter them, and never retry an error that says "I am overloaded".

**A circuit breaker converts slow, repeated, expensive failure into fast, local, cheap failure.**
Its observable signature is the one this project measures: the downstream call counter *stops rising*
while requests keep being answered. It also gives the dependency room to recover instead of a
thundering herd.

**Observability is what makes the four numbers above tunable.** A timer for the business operation, a
trace id you can carry from a support ticket into a query, and the breaker's state on a dashboard —
"open" is an incident, and you want to know before the support queue tells you.

## The project

A checkout service with exactly one dependency that can ruin its day: a payment gateway, reached over
real HTTP. In tests the far end is **WireMock**, which can be slow, flaky, or dead on demand — real
sockets, so timeouts mean what they say. No Docker needed for the tests.

**What is given:**

- `CheckoutService` — correct, and completely undefended. Every step adds exactly one mitigation here.
- `HttpPaymentGateway` — a `RestClient` call with no timeout, no retry, no breaker.
- `GatewayMeter` — counts calls that actually left the process, and peak concurrent calls. These two
  numbers are how most checkpoints are verified: a retry that triples your downstream load shows up
  here, and a working breaker makes the counter *stop*.
- `CircuitBreakerConfiguration` — a Resilience4j breaker, pre-tuned small enough for tests to trip it
  (8-call window, 50% threshold, 2 s open) and already bound to Micrometer. Step 4 is about *using*
  it, not tuning it.
- `AbstractProductionTest` — WireMock plus stub helpers (`gatewayIsSlow`, `gatewayIsDown`,
  `gatewayFailsThenRecovers`) and, importantly, a per-test `breaker.reset()`. A breaker is stateful
  and Spring caches contexts, so without that reset one test's tripped breaker silently poisons every
  test after it — a bug this project hit for real.
- `docker-compose.yml` — Grafana's `otel-lgtm` all-in-one plus a WireMock gateway, for step 6's
  look-at-it work.

```bash
mvn test
```

```bash
docker compose up -d && mvn spring-boot:run   # then POST /checkout, browse Grafana at :3000
```

## Guided steps

### Step 1 — A timeout, before anything else

**Goal.** The gateway takes five seconds; your request currently takes five seconds too. Add HTTP
client timeouts.

```properties
spring.http.clients.connect-timeout=1s
spring.http.clients.read-timeout=1s
```

<details><summary>Hint — and a trap worth knowing</summary>

Note the **plural**: `spring.http.clients.*`. Boot 4.1 also accepts `spring.http.client.*`
(singular) and it silently has no effect on the auto-configured `RestClient.Builder` — verified the
hard way while writing this project. This is a good argument for asserting timeouts in a test rather
than trusting a property name.
</details>

**Done when** `Checkpoint1TimeoutTest` passes. Reference run: **1060 ms per attempt** against a
gateway that wanted five seconds. Read that test's assertion carefully — it bounds *per attempt*, on
purpose, because step 2 is about to multiply the total.

### Step 2 — A bounded retry for genuinely transient faults

**Goal.** One HTTP 503 followed by a healthy response should be invisible to the caller. Add Spring
Framework 7's core retry to `CheckoutService.checkout(...)`.

<details><summary>Hint</summary>

```java
@Retryable(includes = GatewayException.class, maxRetries = 2,
           delay = 50, jitter = 20, multiplier = 2.0)
```

`jitter` matters more than it looks: without it every instance that failed together retries together,
and you have built a synchronised stampede. `@EnableResilientMethods` is already on the application
class.
</details>

**Done when** `Checkpoint2RetryTest` passes — success, and exactly **2** gateway calls.

### Step 3 — Now look at what retries cost

**Goal.** Enable `Checkpoint3RetryBudgetTest` and think about amplification. Ten requests against a
dead gateway must not become an unbounded number of calls; three attempts each is a **3× load
multiplier applied to a system that is already failing**.

**Done when** both tests pass: one request makes at most 3 attempts, and the backoff is real (the
attempts cannot complete instantly — reference run: **215 ms** for three). Then write down the
arithmetic for your own service: requests/sec × attempts = the load your dependency sees during its
worst hour.

<details><summary>Hint</summary>

The assertions are deliberately upper bounds only. Step 4 adds a breaker that pushes the call count
*down*, and these tests have to keep passing when it does — a small lesson in writing checkpoints
that survive later refactoring.
</details>

### Step 4 — Stop asking a dead dependency

**Goal.** Wrap the gateway call in the provided `CircuitBreaker` so repeated failure stops generating
traffic. Translate the breaker's rejection into a distinct exception
(`GatewayUnavailableException` → 503) so the API says "unavailable", not "bad gateway".

<details><summary>Hint</summary>

```java
try {
    return breaker.executeCallable(() -> gateway.authorize(orderId, amountCents));
} catch (CallNotPermittedException e) {
    throw new GatewayUnavailableException("payment gateway circuit is open");
}
```

Then reconcile it with step 2: add `excludes = GatewayUnavailableException.class` to `@Retryable`.
Retrying an open breaker is pure latency — the answer will not change for two seconds. Getting this
composition right (breaker inside retry, breaker rejection not retried) is the actual skill.
</details>

**Done when** `Checkpoint4CircuitBreakerTest` passes. Reference run: gateway calls plateau at **4 and
stay at 4** across six more requests, each rejected in **1 ms**.

### Step 5 — A bulkhead, because threads are free now

**Goal.** Cap how many checkouts may be in the gateway at once, so a slow dependency degrades
checkout instead of consuming the whole service.

<details><summary>Hint</summary>

`@ConcurrencyLimit(5)` on the same method. Note what you are re-creating deliberately: the throttle
that a small platform-thread pool used to give you by accident (project 14).
</details>

**Done when** `Checkpoint5ConcurrencyLimitTest` passes. Reference run: **peak 5 concurrent gateway
calls** out of 20 simultaneous checkouts.

### Step 6 — Make it all visible

**Goal.** Wrap the operation in a Micrometer `Observation` named `checkout`, and put the trace id in
the response.

<details><summary>Hint — the Boot 4 assembly trap</summary>

```java
return Observation.createNotStarted("checkout", observations).observe(() -> { … });
```

and `tracer.currentSpan()` for the id (guard for null). If your trace ids come out **empty
strings**, you have the no-op tracer: Boot 4 splits observability across modules and hand-picking
`micrometer-tracing-bridge-otel` is not enough. `spring-boot-starter-opentelemetry` pulls the piece
that actually supplies a real `OtelTracer` (`spring-boot-micrometer-tracing-opentelemetry`). This
project already depends on the starter for exactly that reason.
</details>

**Done when** `Checkpoint6ObservabilityTest` passes — a `checkout` timer exists, the response carries
a real trace id, and the breaker's state is a visible gauge.

Then do the part no test can do for you:

```bash
docker compose up -d
```

```bash
OTLP_ENDPOINT=http://localhost:4318/v1/traces mvn spring-boot:run
```

Drive some traffic (including `gatewayIsSlow`-style delays via WireMock's `/__admin`), open Grafana
at <http://localhost:3000>, and find: one request's trace with the gateway call as a child span; the
`checkout` timer's p99; and the breaker gauge flipping to open. **Done when** you have followed a
single slow request from a trace to its log lines using the trace id alone.

### Step 7 — Startup, the other production number

**Goal.** Measure what your service costs to start, and try the 2026 default answer.

```bash
mvn -q package -DskipTests
```

```bash
java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf -jar target/production-readiness-1.0.0-SNAPSHOT.jar --spring.main.web-application-type=none
```

```bash
java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconf -XX:AOTCache=app.aot -jar target/production-readiness-1.0.0-SNAPSHOT.jar
```

```bash
java -XX:AOTCache=app.aot -jar target/production-readiness-1.0.0-SNAPSHOT.jar
```

**Done when** you have a three-row table — plain JVM, AOT cache, and (optionally) a native image —
with time-to-first-request and RSS for each, plus a sentence on which you would pick and why. The
2026 rule of thumb: **fidelity → Leyden AOT cache; density/scale-to-zero → native image; warm first
request → CRaC.**

## Self-check

1. Why is a timeout the only one of these four patterns that is never optional?
2. Your timeout is 1 s and you retry twice. What is your worst-case latency, and what does that do to
   an upstream caller's own timeout?
3. Name two error classes you should *never* retry, and why.
4. What is the observable signature of a working circuit breaker?
5. Why did virtual threads make bulkheads more important rather than less?
6. Breaker inside retry, or retry inside breaker? What goes wrong the other way?
7. You raise your retry limit to 5 during an incident. Describe the likely outcome.
8. A support ticket says "checkout was slow at 14:32". What is the shortest path from that sentence to
   the responsible span?

## Stretch goals

1. **Fallback, not failure.** When the breaker is open, accept the order as `PAYMENT_PENDING` and
   reconcile later (project 07's outbox is right there). Decide which is better for the business — a
   503 or a promise — and note that this is a product question, not a technical one.
2. **Retry budgets.** Cap retries as a *percentage of traffic* rather than per request, so a full
   outage cannot triple your load at all. Compare with a plain `maxRetries`.
3. **The zero-code path.** Run the same app under the OpenTelemetry Java agent instead of
   Micrometer's bridge and compare the traces you get for free against the ones you instrumented.
4. **Load-test the composition.** Put all four patterns under sustained load with a partially
   degraded gateway (30% errors, 10% slow) and graph p99 and error rate per pattern added. The
   "retries made it worse" moment is much more convincing on a graph you produced.

## Resources

- **[Core Spring Resilience Features](https://spring.io/blog/2025/09/09/core-spring-resilience-features/)**
  (Spring team) and the **[Framework reference: Resilience](https://docs.spring.io/spring-framework/reference/core/resilience.html)**
  — what moved into core and what did not.
- **Michael Nygard — *Release It!*, 2nd ed.** — the canon. Circuit breaker, bulkhead, and the failure
  modes this lesson simulates were named here.
- **[Resilience4j documentation](https://resilience4j.readme.io)** — breaker states, sliding windows,
  and the metrics worth alerting on.
- **Ted Young & Austin Parker — *Learning OpenTelemetry*** (O'Reilly), plus
  **[OpenTelemetry with Spring Boot](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/)**
  and **Nicolas Fränkel's [agent vs Micrometer Tracing comparison](https://blog.frankel.ch/opentelemetry-tracing-spring-boot/)**.
- **Google SRE Book, "Handling Overload" and "Addressing Cascading Failures"** — free online; the
  clearest writing on why retries amplify outages and what a retry budget is.
- **[Speed up Java startup with Spring Boot and Project Leyden](https://piotrminkowski.com/2026/03/19/speed-up-java-startup-with-spring-boot-and-project-leyden/)**
  (Piotr Mińkowski) and **[CRaC vs Leyden vs AOT](https://blog.rasc.ch/2026/04/spring-boot-startup.html)**
  (Ralph Schaer) — step 7's decision tree.

---

**Build notes (verified August 2026).** Spring Boot 4.1.1 / Java 25. Boot 4's modularization bites
three times in this project, all confirmed empirically: `RestClient.Builder` needs
`spring-boot-starter-restclient`; a real `Tracer` needs `spring-boot-starter-opentelemetry` (hand-picking
`micrometer-tracing-bridge-otel` yields the no-op tracer and empty trace ids); and client timeouts are
configured under `spring.http.clients.*` (plural) — the singular form is accepted and ignored.
Resilience4j 2.3.0, WireMock 3.13.1 (`http2PlainDisabled(true)`), Micrometer Tracing 1.7.1, OTel SDK
1.62.0. Pristine `mvn test`: 12 tests green, 10 checkpoints `@Disabled`. The `grafana/otel-lgtm` image
is not cached locally; step 6's stack may need a Docker Desktop restart before it pulls.
