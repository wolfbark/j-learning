# 14 — Virtual Threads: Loom Removes Thread Scarcity, Not Resource Scarcity

> After this lesson you can explain — with numbers you measured yourself — what virtual threads
> actually fix, rewrite a sequential fan-out as structured concurrency that cancels properly,
> find the bottleneck that appears once threads stop being scarce, and keep request context alive
> across a fan-out now that `ThreadLocal` no longer suffices.

## Why this matters (2026)

Virtual threads have been final since Java 21, but 2026 is the first year the advice is
unqualified — and that changes what "good backend code" looks like.

The reason is [JEP 491](https://openjdk.org/jeps/491) in **Java 24**: until then, a virtual thread
that blocked inside a `synchronized` block *pinned* its carrier thread, so one badly-placed lock in
a driver or library could quietly reintroduce the thread ceiling you adopted Loom to remove. That
class of footgun is gone. **Java 25 LTS is the first LTS where virtual threads simply work**, which
is why benchmark posts and cautionary tales from 2023 are worth re-running before you trust them.

The consequence the industry has settled on: for I/O-bound services, **blocking code on virtual
threads is the default again**, and reactive programming retreats to genuine streaming use cases.
Spring Boot turns it on with one property. WebFlux is no longer the price of scalability.

But the more useful half of this lesson is the part the marketing skips. Virtual threads make
*threads* nearly free. They do nothing about connection pools, rate limits, downstream capacity, or
locks. In practice, adopting Loom **moves your bottleneck** — usually to a pool whose limit was
never really tested, because the thread pool was throttling traffic before it. Step 5 makes that
happen to you deliberately.

See [`../docs/research/platform-and-production.md`](../docs/research/platform-and-production.md)
§1b/§1c for the source material.

## Core concepts

**A virtual thread is not a faster thread — it is a cheaper one.** Same `Thread` API, same blocking
calls, but the JVM schedules many of them onto a small pool of carrier (platform) threads and
unmounts one whenever it blocks. Cost drops from ~1 MB of stack and a kernel thread to roughly a
kilobyte of heap. That single price change makes "one thread per request" and "just fork a thread
per remote call" reasonable engineering again.

**Thread-per-request stops being a budget decision.** With platform threads you size a pool, and
that number becomes a hard concurrency ceiling: request N+1 waits for one of N to finish, even
though the machine is idle and every waiting thread is parked on a socket. That is not a physics
limit; it is an accounting limit. You will measure exactly this in step 1.

**Structured concurrency gives subtask lifetimes a scope.** An `ExecutorService` fan-out leaks:
when one branch fails, the siblings keep running — consuming downstream capacity for a response
nobody will read, potentially outliving the request. `StructuredTaskScope` binds subtasks to a
block: leaving the block means everything forked inside it has finished or been cancelled, and a
failing subtask cancels its siblings.

```java
try (var scope = StructuredTaskScope.open()) {          // still preview in Java 25 (JEP 505)
    var profile   = scope.fork(() -> downstream.fetchProfile(id));
    var inventory = scope.fork(() -> downstream.fetchInventory(id));
    scope.join();                                        // both done, or one failed and the
    return combine(profile.get(), inventory.get());      // other was cancelled for you
}
```

Preview status is worth knowing: 5th preview in Java 25, 6th in 26, targeted to finalise around
JDK 27. This project already passes `--enable-preview`. `ScopedValue`, by contrast, is **final**
in Java 25 — no flag.

**`ThreadLocal` breaks precisely when you start fanning out.** Request context set on the request
thread is invisible to threads you fork, so your correlation id vanishes from the log lines you most
want during an incident. `ScopedValue` is the replacement: bound for the duration of a scope,
immutable, automatically inherited by threads forked inside that scope, and cheap enough that a
million virtual threads do not care.

**When Loom does *not* help.** CPU-bound work (you have the cores you have), anything gated by a
scarce resource (step 5), and code holding locks across blocking calls. Virtual threads raise the
ceiling on *waiting*, nothing else.

## The project

A customer-view aggregator: `GET /customers/{id}` needs three independent things — profile,
inventory, pricing — each a simulated remote call that takes **150 ms** and burns no CPU. Written
the way everyone writes it the first time: one after another, 450 ms per request.

`Thread.sleep` is an honest simulation here — post-JEP-491 it parks the virtual thread and releases
the carrier exactly as a socket read would.

**What is given:**

- `AggregatorService` — the sequential fan-out (your refactoring subject) and
  `loadWithDatabase(...)`, which borrows a pooled connection **for the whole request**, including
  the three remote calls that do not need one. That is the shape of an over-broad
  `@Transactional`, and it is step 5's subject.
- `ScarcePool` — a `Semaphore`-based stand-in for a connection pool. Ten permits, like HikariCP's
  default, with hold-time and peak-usage instrumentation.
- `DownstreamService` — the three simulated calls, a `ConcurrencyMeter`, an interrupt counter (so
  cancellation is *provable*), a failure toggle, and a record of the request context each call
  actually observed.
- `RequestConcurrencyFilter` — counts requests in flight, measured on the request thread. Kept
  separate from the downstream counter on purpose: after step 3 one request has three calls in
  flight, so only this number measures *request* concurrency.
- `LoadHarness` (test support) — a small load generator so nobody needs to install `wrk`. Every
  request runs on its own virtual thread, so the client is never the bottleneck — the mistake that
  has invalidated more virtual-thread benchmarks than any other.
- `application.properties` — Tomcat capped at **8 threads**, so the ceiling is visible without a
  load-testing rig. Production's default of 200 only moves the cliff.

Two always-on tests guard the work: `CustomerViewContractTest` (behaviour must survive every
refactor) and `PlatformThreadCeilingTest`, which pins `spring.threads.virtual.enabled=false` in its
own context so it remains a truthful exhibit of platform-thread behaviour even after you switch
the application over.

```bash
mvn test
```

```bash
mvn spring-boot:run   # then: curl -s localhost:8080/customers/C-1 | jq
```

`GET /diagnostics` reports every meter; `POST /diagnostics/reset` zeroes them;
`POST /chaos/pricing?fail=true` breaks the pricing call.

## Guided steps

### Step 1 — Measure the ceiling you already have

**Goal.** Run `PlatformThreadCeilingTest` and read its output. Forty simultaneous users, eight
Tomcat threads, three sequential 150 ms calls each.

On this machine, pristine: **16.3 req/s, p99 2452 ms, peak 8 requests in flight.** The queue is five
deep and the CPU is asleep.

**Done when** you can state the arithmetic: why 40 users × 450 ms ÷ 8 threads produces that p99, and
why no amount of CPU would improve it.

### Step 2 — One property

**Goal.** Add to `application.properties`:

```properties
spring.threads.virtual.enabled=true
```

Then enable `Checkpoint2VirtualThreadsTest`. Note it does *not* set the property itself — it reads
your application's real configuration.

<details><summary>Hint</summary>

Once virtual threads are on, `server.tomcat.threads.max` stops meaning anything: there is no pool
to size. Keep the line in the file as a fossil, and notice in `/diagnostics` that
`requestPeakConcurrency` is now bounded by the number of users, not by your configuration.
</details>

**Done when** both tests in that class pass — the handler reports `servedByVirtualThread: true`, and
in-flight requests exceed 20. Reference run: **peak 40 requests in flight**, and even before any
code change the throughput roughly triples.

### Step 3 — Now that threads are cheap, stop waiting in sequence

**Goal.** Rewrite `AggregatorService.load(...)` so the three independent calls happen concurrently.
Latency should become the *slowest* call, not the sum.

<details><summary>Hint</summary>

`Executors.newVirtualThreadPerTaskExecutor()` in a try-with-resources works and is worth writing
first — precisely so step 4 has something to criticise. Watch `downstreamPeakConcurrency` per single
request: it should be 3.
</details>

**Done when** `Checkpoint3ConcurrentFanoutTest` passes. Reference run: **157 ms for one request**,
against ~450 ms sequential.

### Step 4 — Make failure behave: structured concurrency

**Goal.** With the executor version, ask what happens when pricing fails while profile and inventory
are still in flight. Then rewrite the fan-out with `StructuredTaskScope` so a failure cancels the
siblings and nothing outlives the block.

Test the failure path: `Checkpoint4StructuredConcurrencyTest` sets the pricing toggle, then asserts
the request fails *fast* and that siblings were actually interrupted.

<details><summary>Hint</summary>

`StructuredTaskScope.open()` (the default `Joiner`) is all-or-nothing: the first subtask failure
cancels the rest and `join()` throws `FailedException`. Cancellation arrives in siblings as an
`InterruptedException` — `DownstreamService` counts those, which is how the test proves cancellation
happened rather than assuming it. For the "first good answer wins" shape, use
`Joiner.anySuccessfulResultOrThrow()`.
</details>

**Done when** the checkpoint passes. Reference run: **failed in 43 ms with 2 cancellations** — versus
waiting out the full 150 ms for results that get discarded.

### Step 5 — Find the new bottleneck

**Goal.** Enable `Checkpoint5ResourceScarcityTest` and hammer
`GET /customers/{id}/with-database` — the endpoint that holds a pooled connection across the remote
calls.

The first test asserts the pool saturates (10/10 permits busy) and will pass immediately: that is
the diagnosis, and it is the important half of the lesson. Threads are free now; connections are not,
and **your throughput ceiling is pool size ÷ hold time**.

The second test is the work: narrow the critical section so a connection is held only for the actual
queries, never across a remote call.

<details><summary>Hint</summary>

Two short `withConnection(...)` calls around the fan-out, instead of one long one wrapping it. In a
real application this is the difference between a service-level `@Transactional` and a repository-level
one — and with virtual threads it is the difference between 10 concurrent users and hundreds.
</details>

**Done when** both tests pass. Reference run: average hold time drops to **~53 ms** (two queries)
from ~550 ms (queries plus three remote calls), with the pool still fully utilised.

### Step 6 — Pay the fan-out's context bill

**Goal.** Enable `Checkpoint6ContextPropagationTest`. It fails now, because `CorrelationId` is a
`ThreadLocal` and step 3 moved the work to forked threads: the downstream calls observe `null`.

Convert `CorrelationId` to a `ScopedValue` and bind it for the request.

<details><summary>Hint</summary>

```java
private static final ScopedValue<String> CURRENT = ScopedValue.newInstance();

public static <T> T callWhere(String id, Callable<T> work) throws Exception {
    return ScopedValue.where(CURRENT, id).call(work::call);
}
```

Read it with `CURRENT.isBound() ? CURRENT.get() : null` — an unbound `get()` throws. Bind it in the
controller around the call to the service; subtasks forked inside the scope inherit it
automatically, including through nested scopes. Do not "fix" this by threading the id through every
method signature: that works and teaches nothing about why Loom needed a new context primitive.
</details>

**Done when** all three downstream calls report the request's correlation id. Then put
`%X{correlationId}` back to work: MDC is still thread-bound, so a `ScopedValue` → MDC bridge (or
Micrometer's context propagation) is what production needs — see project 15.

## Self-check

1. Why does a fixed platform-thread pool cap throughput even when the CPU is idle?
2. What did JEP 491 change, and why does it make pre-2024 virtual-thread advice unreliable?
3. Why is `Thread.sleep` a fair simulation of a remote call on Java 25, but was not on Java 21?
4. What exactly does `StructuredTaskScope` guarantee that an `ExecutorService` fan-out does not?
5. Your service moves to virtual threads and throughput barely improves. Name three things to check.
6. Why does `ThreadLocal` fail after a fan-out, and what makes `ScopedValue` cheap enough for a
   million threads?
7. Pool of 10, connections held 500 ms. What is your maximum sustainable request rate — and which
   number is easier to change?
8. When would you still reach for reactive/WebFlux in 2026?

## Stretch goals

1. **Find a pinned carrier.** Wrap a downstream call in a `synchronized` block and run with
   `-Djdk.tracePinnedThreads=full` on a JDK 21 toolchain versus 25. On 25 there is nothing to see —
   that is the lesson.
2. **Size the pool honestly.** Raise `loom.pool-size` and re-measure. Plot throughput against pool
   size and find where the downstream (not the pool) becomes the limit.
3. **Timeouts in a scope.** Add a deadline to the fan-out with `Joiner`/`scope.join(...)` so a
   pathologically slow downstream cannot hold a request open forever. Compare with project 15's
   client-timeout approach.
4. **MDC bridge.** Make the correlation id appear in every log line again, from inside the subtasks,
   and note how much machinery that takes compared with the `ScopedValue` itself.

## Resources

- **[JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)** and
  **[JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)** — the
  primary sources; 491 is the one that changed the advice.
- **[JEP 506 — Scoped Values](https://openjdk.org/jeps/506)** (final in 25) and
  **[JEP 505 — Structured Concurrency](https://openjdk.org/jeps/505)** (preview) — read the
  motivation sections, they are unusually good design writing.
- **InfoQ — ["Virtual Threads after JDK 24: What Changed for Production Java"](https://www.infoq.com/articles/virtual-threads-after-jdk24/)**
  — the state-of-play article for exactly this lesson.
- **Dan Vega — ["Virtual Threads Without Pinning"](https://www.danvega.dev/blog/jdk-24-virtual-threads-without-pinning)**
  and **Mike Kowalski — ["Java 24: thread pinning revisited"](https://mikemybytes.com/2025/04/09/java24-thread-pinning-revisited/)**
  — hands-on treatments with reproducible examples.
- **José Paumard — JEP Café** (inside.java) — the clearest video explanations of Loom and structured
  concurrency; pair with Brian Goetz's writing on the design rationale.
- **Michael Nygard — *Release It!*, 2nd ed.** — pool sizing, bulkheads, and why "the bottleneck
  moved" is the normal outcome of every optimisation.

---

**Build notes (verified August 2026).** Spring Boot 4.1.1 on Java 25 (`spring-boot-starter-webmvc`).
`--enable-preview` is set for compile, Surefire, and `spring-boot:run` because structured
concurrency is still a preview API in 25; `ScopedValue` needs no flag. The Java 25 API used here is
`StructuredTaskScope.open()` / `open(Joiner.…)` with `scope.fork(...)` returning `Subtask<T>` —
older `ShutdownOnFailure` examples predate it. Reference figures in this lesson were measured on
this machine (Apple Silicon, JDK 25 via Homebrew); absolute numbers will differ, the ratios should
not. No Docker required.
