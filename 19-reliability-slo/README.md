# 19 — Reliability Engineering: Measuring, Profiling, and Promising

> After this lesson you can turn "the service feels slow" into a number, defend that number
> against the average that hides it, find the frame responsible with a profiler that was already
> installed, diagnose a pool from its metrics alone, and write down a promise — an SLO with an
> error budget — that a product manager and an on-call engineer would both accept.

## Why this matters (2026)

Projects 14 and 15 made this service *fast* and *hard to knock over*. Neither of them made it
**measurable**, and that is the gap where most real teams live: full OTel pipeline, forty
dashboards, and no one able to say whether last Tuesday was acceptable.

Three things make this a 2026 lesson rather than a 2016 one.

**The wire format question is settled and the discipline question is not.** OpenTelemetry won;
Micrometer bridges to it; Boot 4 documents it as first-class (see
[`../docs/research/platform-and-production.md`](../docs/research/platform-and-production.md) §5).
Producing telemetry is now a dependency and two properties. Choosing *which* numbers constitute a
promise is still a judgement call, and tooling has not made it one bit easier.

**Cardinality is now your bill.** Every managed metrics backend prices by active time series. A
`user_id` tag is no longer a performance mistake in a Prometheus you run; it is an invoice, and
occasionally an outage in someone else's cluster. Step 2 makes you cause it.

**Profiling stopped being special.** JFR ships in the JDK, costs a couple of percent, starts from
inside a running process, and writes a file you can parse with `jdk.jfr.consumer` in twenty lines.
There is no longer any excuse for the sentence "we think it's GC".

And one thing that has not changed since 2016, which is why the SRE book is still the reading:
**an SLO is a product decision.** The interesting number is not your p99, it is how much
unreliability your users will tolerate before they leave — and that number is never 100%.

## Core concepts

**You cannot promise what you cannot measure, and you cannot measure with an average.** A mean is
the one summary statistic guaranteed to describe nobody. Take this service's real distribution:
90% of settlements at ~20 ms, 10% at ~900 ms. The mean is ~110 ms, comfortably inside a 250 ms
SLO, and it is a number almost no request ever experiences. The p99 is ~900 ms. Both come from the
same 2 000 requests.

**Percentiles do not average and do not compose.** The mean of two instances' p99s is not the p99
of the fleet. This is why histograms exist: ship *buckets*, let the backend add them up, and
compute the quantile once over the merged distribution. It is also why an SLO bucket — a boundary
placed exactly at your promise — is better than any percentile estimate: the SLI becomes a
division, not an approximation.

**Coordinated omission, honestly.** Most latency numbers you have ever read are wrong in the same
direction. A load generator that waits for a response before sending the next request stops
measuring during exactly the period the service is worst, and then reports the average of the
requests that got through. Gil Tene's talk is the canonical treatment; step 4 reproduces the
mechanism structurally, and the checkpoint's own tests are honest about where they cheat.

**Cardinality multiplies.** Series count is the *product* of the distinct values of every tag on
the meter, not the sum. `user_id` (100 k) × `path`-with-ids (50 k) × `status` (5) is not "three
tags", it is 25 billion possible series. High-cardinality data belongs in logs and traces, where
it is stored once per event; metrics are pre-aggregated on purpose.

**Structured logs are an API.** `log.info("Settled order {} for {}", …)` is a string that a human
can read and a query cannot. The same event as key/value pairs — stable event name, stable field
names, a correlation id, no PII — is queryable, aggregatable, and safe to retain. Boot 4 will emit
JSON for you with one property; the discipline is in the field names, not the encoder.

**SLI, SLO, error budget.**

| Term | Definition | Shape |
|------|------------|-------|
| **SLI** | a measurement of one dimension of service health | good events ÷ valid events |
| **SLO** | the target for an SLI, over a window | "99.9% of settlements succeed, per 30 days" |
| **Error budget** | the unreliability the SLO permits | (1 − objective) × valid events |
| **Burn rate** | how fast you are spending it | observed error ratio ÷ (1 − objective) |

Burn rate is the operational number: 1.0 means you will spend exactly the budget in exactly the
window; 14.4 means a 30-day budget is gone in about two days and someone should be woken up.

**Profiling answers a different question from monitoring.** Metrics tell you *that* report
rendering is slow. Only a profiler tells you it is slow because every caller queues on one monitor
and each call allocates 25 MB it immediately discards. JFR event types worth knowing:
`jdk.JavaMonitorEnter` (lock contention, with the blocked stack), `jdk.ObjectAllocationSample`
(sampled allocation, with the allocating stack), `jdk.ExecutionSample` (CPU, i.e. a flame graph),
`jdk.ThreadPark`, plus the GC and safepoint families.

**What this project does not re-teach.** Virtual threads and structured concurrency are
[project 14](../14-virtual-threads/README.md); timeouts, retries, breakers, bulkheads, tracing
setup and the Leyden startup work are [project 15](../15-production-readiness/README.md). If you
want a Grafana stack to look at, project 15 already has one — point this service's OTLP endpoint
at it rather than starting a second copy.

## The project

A **settlement service**. `POST /settlements` settles an order; `GET /reports/{id}` renders a
report; `GET /reports/{id}/aggregate` runs a report query against Postgres; `GET /slo` reports the
service's own SLIs. The interesting parts are all deliberately wrong.

**What is given:**

- `SettlementAuditLog` — prose logging, with an email address and a card number interpolated into
  it, plus a "we got here" line on every request. Step 1's subject.
- `RequestMetrics` — a counter tagged with a user id and a path containing an order id. Step 2's
  subject. Its sibling `recordOutcome(...)` shows what a bounded tag looks like, and is the
  availability SLI's numerator and denominator.
- `LatencyProfile` — the bimodal latency distribution described above, plus a timer registered
  with no percentiles, no histogram and no SLO buckets. Step 3's subject.
- `ThrottledWorkload` — four at a time, 25 ms each: a hard 160/s ceiling for step 4 to measure.
- `LoadHarness` (test support) — closed-model and open-model load generators, about forty lines
  each, both waiting for stragglers before computing percentiles.
- `ReportRenderer` — one planted lock and one planted allocation storm. The lesson deliberately
  does not tell you which methods; step 5 is to find out.
- `JfrProfiler` — an empty two-method API for you to implement in step 5.
- `ReportQueryRepository` — a query that holds a pooled connection for two seconds, against a pool
  of four. Step 6's subject.
- `ErrorBudget` — an empty calculator for you to implement in step 7.
- `ChaosSwitch` + `POST /chaos?mode=…` — the toggle for step 7's drill.
- `docs/postmortem-template.md` — to be filled in, once, for real.

Four always-on tests guard the work: the settlement contract, the shape of the latency
distribution, "every request is still counted exactly once" (step 2 must not lose data), and
deterministic rendering (step 5 must not change output).

```bash
mvn test
```

```bash
mvn spring-boot:run   # then: curl -s -XPOST localhost:8080/settlements \
                      #   -H 'Content-Type: application/json' \
                      #   -d '{"orderId":"ORD-1","userId":"U-1","customerEmail":"ada@example.com",
                      #        "cardNumber":"4111111111111111","amountCents":19900}' | jq
```

`GET /slo`, `GET /diagnostics`, `GET /actuator/metrics/{name}`, `POST /diagnostics/reset`.
**Docker is needed only for step 6** (Testcontainers, `postgres:16-alpine`).

## Guided steps

### Step 1 — Structured logging that can be queried

**Goal.** Turn `SettlementAuditLog` into machine-readable events: a stable event name, stable
field names, a correlation id, and no personal data. Then delete the theatre.

Look at the two given log calls and ask, for each: *which incident does this line shorten?*
`settled(...)` is genuinely the line you want at 03:00 — it has the outcome, the duration and the
id. `enteringSettle(...)` is theatre: one line per request, forever, that has never once explained
anything. It is also not free — it is bytes, index entries, and retention cost.

<details><summary>Hint</summary>

SLF4J 2's fluent API attaches key/value pairs to an event, and the appender decides how to render
them:

```java
log.atInfo().setMessage("settlement.completed")
   .addKeyValue("order_id", command.orderId())
   .addKeyValue("duration_ms", took.toMillis())
   .addKeyValue("outcome", success ? "success" : "failure")
   .log();
```

The correlation id may travel as a key/value pair or in MDC — the checkpoint accepts either, and
`CorrelationIdFilter.current()` gives you the current one. Then turn on JSON and look at the
result:

```properties
logging.structured.format.console=ecs
```

For the theatre line: demote it to DEBUG rather than deleting it, if it helps you locally. The
checkpoint only insists it never reaches INFO.
</details>

**Done when** `Checkpoint1StructuredLoggingTest` passes: the event has `order_id`, `user_id`,
`outcome`, `duration_ms` and `correlation_id`; no field and no rendered message contains the email
or the card number; and nothing reaches INFO on the way in.

### Step 2 — Bounded cardinality: the outage you cause yourself

**Goal.** Do the arithmetic on `RequestMetrics.recordRequest(...)`, then bound it.

With 100 000 users and 50 000 order ids in a retention period, the given code's three tags produce
up to 100 000 × 50 000 × 2 series. You will not get there — you will get an alert from your
metrics vendor, or a Prometheus that OOMs, somewhere in the low millions. Note that this is a
*product* of cardinalities: adding one unbounded tag to an existing unbounded tag does not double
your problem, it squares it.

<details><summary>Hint</summary>

Two moves. Template the path (`/settlements/{orderId}`) so the tag describes the *route*, not the
request. Replace the user id with something bounded that you would actually alert on — a status
class, a tenant tier, authenticated vs anonymous. The id itself belongs in the log event you built
in step 1 and in the trace, where it is stored once per event instead of forever per series.

The registry-wide backstop is worth knowing but is not a fix:

```java
@Bean
MeterFilter boundedRequestTags() {
    return MeterFilter.maximumAllowableTags("settlement.requests", "user", 20,
            MeterFilter.deny());
}
```

It protects your bill after the design mistake, and it protects it by *losing data*.
</details>

**Done when** `Checkpoint2CardinalityTest` passes: 500 distinct users produce at most 20 series,
the total count is still exactly 500, and no tag value is a raw identifier.

### Step 3 — Averages lie: histograms and percentiles

**Goal.** Configure `LatencyProfile.timer(...)` so the distribution survives the trip to your
metrics backend, then read the SLI off it.

<details><summary>Hint</summary>

```java
return Timer.builder("settlement.latency")
        .publishPercentiles(0.5, 0.95, 0.99)      // client-side quantiles, for this process
        .publishPercentileHistogram()             // bucket ladder, for backend aggregation
        .serviceLevelObjectives(Duration.ofMillis(100), LATENCY_SLO, Duration.ofSeconds(1))
        .register(registry);
```

The three lines do different jobs, and it is worth knowing which is which. `publishPercentiles`
computes quantiles *in this JVM* — cheap, useful in a test, and not aggregatable across instances.
`publishPercentileHistogram` ships a bucket ladder so the backend can compute a fleet-wide
quantile; note that Micrometer only materialises that ladder for registries that support
aggregable percentiles (Prometheus does; `SimpleMeterRegistry` does not).
`serviceLevelObjectives` adds explicit boundaries — including one exactly at your promise, which
is what turns the SLI into a division instead of an estimate.
</details>

**Done when** `Checkpoint3PercentilesTest` passes. Reference run on this machine: **mean 108.6 ms,
p50 22.5 ms, p95 905 ms, p99 973 ms, and 1 804 of 2 000 settlements (90.2%) inside the 250 ms
SLO** — a service that fails a 99% latency objective by a mile while its mean sits 57% under the
limit. Worth noticing that the reported p99 (973 ms) is slightly *above* the highest value ever
observed (950 ms): Micrometer's client-side percentiles default to one digit of precision, so they
are estimates with roughly 10% error. That is fine for "is the tail terrible", useless for
"exactly how terrible" — another argument for counting an SLO bucket instead. Then look at
`GET /slo`: `latencySli` was `null` before this step, because an SLI you cannot compute is not an
SLI.

One honest caveat, because it invalidates a lot of published numbers: this checkpoint *replays* a
recorded distribution into the timer rather than living through it. That makes it deterministic
and instant, and it means the test cannot exhibit coordinated omission — the subject of step 4.

### Step 4 — Realistic load modelling

**Goal.** Offer `ThrottledWorkload` the same nominal 200 requests/second two ways, and explain the
difference.

- **Closed model** — a fixed population of N users, each waiting for its own response before
  thinking and asking again. This is what "50 concurrent users" means in every commercial load
  tool. Offered load is a *function of your response time*: when you slow down, the harness slows
  down with you, and the queue can never exceed N.
- **Open model** — arrivals at a fixed rate whatever the service is doing. This is what real
  traffic does. When arrival rate exceeds service rate, the backlog grows at the difference, and
  latency grows with the backlog.

**Done when** `Checkpoint4LoadModelTest` passes. Reference run, 2 seconds each, both at a nominal
200/s against a 160/s service:

| model | throughput | p50 | p99 | peak in flight |
|-------|-----------|-----|-----|----------------|
| closed, 10 users, 25 ms think time | 139/s | 30 ms | 112 ms | **10** |
| open, 200 arrivals/s | 141/s | 430 ms | **835 ms** | **120** |

Identical throughput — the capacity is the capacity — and a 7× difference in the tail. The closed
harness reported a healthy service because *it* was the admission control. Write down which model
your own load tests use.

<details><summary>Hint — and where the harness could have lied to you</summary>

Both harnesses wait for outstanding requests before computing percentiles. Stop measuring at the
end of the run instead, and you silently delete the slowest requests — the ones that had not
finished yet. That is coordinated omission, and it is why a harness that reports "p99 = 112 ms"
under saturation deserves suspicion rather than a screenshot.
</details>

### Step 5 — Profiling with JFR

**Goal.** `GET /reports/{id}` is slow under concurrency and no metric will tell you why.
Implement `JfrProfiler`: record while load runs, then parse the recording and attribute events to
a class.

<details><summary>Hint</summary>

Recording, from inside the process — no agent, no restart:

```java
try (var recording = new Recording()) {
    recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
    recording.enable("jdk.ObjectAllocationSample").with("throttle", "500/s").withStackTrace();
    recording.start();
    load.run();
    recording.stop();
    recording.dump(destination);
}
```

Parsing, with the JDK's own consumer API:

```java
try (var file = new RecordingFile(recording)) {
    while (file.hasMoreEvents()) {
        RecordedEvent event = file.readEvent();
        event.getStackTrace().getFrames().stream()
             .map(frame -> frame.getMethod().getType().getName())   // fully-qualified class
             .anyMatch(name -> name.contains(fragment));
        Duration blocked = event.hasField("duration") ? event.getDuration() : Duration.ZERO;
    }
}
```

Two traps. Enable *specific* events with thresholds — enabling everything turns a 2% profiler into
a 20% one. And `jdk.ObjectAllocationSample` is throttled by design (`throttle` setting): it is a
sample, so it tells you *where* allocation happens, not exactly how much.

The other way in, when the process is not yours to change:
`java -XX:StartFlightRecording=duration=30s,filename=app.jfr,settings=profile -jar app.jar`, then
open the file in JDK Mission Control and read the flame graph.
</details>

**Done when** `Checkpoint5JfrProfilingTest` passes and you can name both planted problems and the
methods they live in. Reference run on this machine (JDK 25, ~0.4 s of load): **47
`jdk.JavaMonitorEnter` events totalling 1.85 s of blocked wall-clock time, and 198
`jdk.ObjectAllocationSample` events — every one of them attributable to `ReportRenderer` by stack
trace.** `jdk.ExecutionSample` is available and carries the same stacks, but a sub-second
recording produces almost none; it needs a longer run to be useful. Now decide what you would
actually change, and note that you could not have guessed either problem from a dashboard.

### Step 6 — Connection-pool and database diagnosis

**Goal.** Diagnose a pool from its metrics, then bound the damage. Needs Docker.

Enable `Checkpoint6PoolDiagnosisTest`. It passes immediately, which is the point: the diagnosis is
the deliverable. Twelve concurrent report aggregates, a pool of four, a query that holds a
connection for two seconds.

Reference run: **peak `hikaricp.connections.pending` = 10, `hikaricp.connections.acquire` max =
4 108 ms, `hikaricp.connections.usage` max = 2 014 ms, `hikaricp.connections.timeout` = 0, slowest
caller 6 131 ms.** Read that as a sentence: ten threads were queueing for a connection, the
queueing cost four seconds, each query held its connection for two, and *not a single request
failed*. Your error rate is zero and your users are furious. This is what a saturated pool looks
like, and it is invisible unless someone graphs `pending`.

Then the arithmetic that decides everything: **pool size ÷ hold time = sustainable requests per
second.** Four connections at two seconds each is two requests per second. No amount of CPU and no
number of virtual threads changes that number — which is a different lesson from project 14's,
where the fix was to stop holding a connection across remote calls. Here the hold time *is* the
query.

Now the second half. `Checkpoint6StatementTimeoutTest` asks for a bound on how long any single
query may hold a connection.

<details><summary>Hint — two timeouts that are not the same timeout</summary>

`spring.datasource.hikari.connection-timeout` bounds how long a caller waits **for a connection**.
It does nothing about how long a query holds one; lowering it converts slow into failing, which is
sometimes exactly right and is never the whole answer.

A **statement timeout** bounds the query itself:

```properties
spring.datasource.hikari.connection-init-sql=SET statement_timeout = '300ms'
```

There is a JDBC-side equivalent with a trap in it. `spring.jdbc.template.query-timeout=1s` works,
but `Statement.setQueryTimeout` takes **whole seconds**, and the Duration is truncated — so
`300ms` becomes `0`, which means "no timeout at all". Verified the hard way while writing this
project. Sub-second bounds have to come from the server.
</details>

**Done when** both checkpoints pass. Reference run after `statement_timeout = '300ms'`:
**`usage` max 308 ms, slowest caller 1 132 ms, and 12 of 12 requests failing fast** with
`canceling statement due to statement timeout`. Notice what you did: you converted an invisible
latency outage into a visible, bounded error rate. That is a real improvement and it is also a
*choice* — you decided that failing some report requests quickly beats making all of them slow.
Step 7 is where that choice gets accounted for.

### Step 7 — SLIs, SLOs, error budgets — and an incident drill

**Goal, part one.** Implement `ErrorBudget` and make `Checkpoint7ErrorBudgetTest` pass. Availability
SLI = `settlement.outcomes{outcome=success}` ÷ all outcomes; latency SLI = the SLO bucket ÷ count,
which step 3 gave you. `GET /slo` reports both.

The arithmetic is easy and the framing is not, so read the test's numbers as sentences:

- 25 bad events out of 100 000 against a 99.9% objective: **75% of the budget still unspent, burn
  rate 0.25, 90 more days of it.** Ship the risky change.
- 400 bad: **SLI 99.6%, burn rate 4.0, three budgets over.** The SLI still reads like a good grade.
- 144 bad in an hour out of 10 000: **burn rate 14.4** — the canonical page-now threshold.
- 1 bad request in a minute out of 100: **burn rate 10.** Which is exactly why you alert on burn
  rate over a window and never on a single failed request.

**Goal, part two — the drill.** Have someone else (or a coin flip) pick a mode and run
`POST /chaos?mode=…` against a running instance, then drive traffic and diagnose it **without
reading the source**. Only `GET /slo`, `/actuator/metrics/*`, and your step 1 log events.

Ask, in order: is the availability SLI or the latency SLI burning? What is the burn rate, and how
long until the budget is gone? Which meter changed first? Which of the modes is consistent with
*all* the evidence — and which did you rule out, on what basis?

**Done when** (a) you name the mode correctly and can point at the two signals that identify it,
(b) you can state the burn rate and the time to exhaustion, and (c) `docs/postmortem-template.md`
is filled in — blameless, with a real timeline sourced from log lines and metrics, contributing
factors that are not people, and action items with owners. The "where we got lucky" section is not
optional; it is the most predictive part of the document.

## An honest section

**An SLO is a product conversation, not a dashboard feature.** The right objective is the level of
unreliability your users will tolerate before they change their behaviour, minus a margin. Nobody
in engineering knows that number. If your SLO was chosen by whoever configured the alert, you have
a threshold, not an objective — and the tell is that nothing happens when you miss it. An SLO with
no consequence attached is decoration.

**Four nines of a dependency caps your own availability below four nines.** Availability composes
multiplicatively across a serial request path: three dependencies at 99.9% each put your ceiling
at 99.7% before you write a line of code. Two consequences worth internalising: your SLO cannot
meaningfully exceed your dependencies' (unless you degrade gracefully — which is a design
decision, not a config), and a hard dependency's outage is *your* outage as far as your users are
concerned. Promising more than your suppliers do is how teams end up burning a quarter's budget in
an afternoon for reasons entirely outside their control.

**Alert on burn rate, not on single-request failures.** A page should mean "the budget is going,
and a human must intervene". One failed request means nothing; a burn rate of 14.4 sustained over
an hour means a 30-day budget is gone in two days. Multi-window, multi-burn-rate alerts (fast burn
over a short window pages, slow burn over a long window opens a ticket) are the SRE Workbook's
recommendation and the single highest-leverage change most teams can make to their alerting.

**And the limits of all this.** 100% is the wrong target for everything except the things where it
is the only target — data loss, security, safety. Error budgets are meaningless for a service with
ten requests a day (your denominator is noise). And percentiles say nothing about *who* is slow: a
p99 that is entirely one large customer is a different problem from a p99 spread evenly, and only
your high-cardinality data — logs and traces, exactly where step 2 sent the ids — can tell those
apart.

## Self-check

1. Your mean latency is 110 ms and your SLO is 250 ms. Why might that be a failing service, and
   what one configuration change would let you find out?
2. Why is the average of two instances' p99 values meaningless, and what do you ship instead?
3. Explain coordinated omission to someone who owns a load-testing licence. What in their setup
   causes it?
4. You add `user_id` and a raw path to one counter. Estimate the series count for 50 000 users
   over a retention period, and say where that data should have gone instead.
5. A saturated connection pool with a 30-second connection timeout: what does your error rate look
   like, and which meter gives it away?
6. Pool of 4, 200 ms per query. What is your sustainable request rate, and name two independent
   ways to raise it.
7. Given a 99.9% objective and 400 bad events out of 100 000: budget remaining, burn rate, and
   what should happen to this week's plan?
8. Three serial dependencies at 99.95% each. What is your availability ceiling, and what would you
   have to build to beat it?
9. Metrics say report rendering is slow. Name the JFR event type you would enable first for each
   of: a suspected lock, a suspected allocation problem, a suspected hot loop.

## Stretch goals

1. **Multi-window burn-rate alerting.** Implement the SRE Workbook's recommended pair (2% of
   budget in 1 hour → page; 5% in 6 hours → ticket) over a synthetic series of good/bad minutes,
   and test that a five-minute blip does *not* page while a sustained degradation does.
2. **Exemplars.** Link a histogram bucket to a trace id, so clicking the slow bucket in Grafana
   lands on an actual slow request. Point this service's OTLP exporter at project 15's LGTM stack
   rather than starting another one.
3. **Continuous profiling.** Run JFR permanently with a rotating recording
   (`maxAge`/`maxSize`), dump on a latency-SLO breach, and keep the file — profiling *after* the
   incident is how you find things you cannot reproduce. Measure the overhead you actually pay.
4. **Fix what step 5 found.** Give the renderer a lock per report id (or none), reuse the page
   buffer, and prove the improvement with a before/after recording rather than a feeling. Keep
   `ReportRendererTest` green.
5. **Make the drill harder.** Add a chaos mode that degrades only one tenant, and see whether your
   step 2 tagging scheme can still find them. This is the honest cost of bounded cardinality.

## Resources

- **[Google SRE Book](https://sre.google/books/)** — *Site Reliability Engineering*, chapters
  "Service Level Objectives", "Handling Overload", "Addressing Cascading Failures"; and **[The
  Site Reliability Workbook](https://sre.google/workbook/table-of-contents/)**, "Implementing
  SLOs" and "Alerting on SLOs" (the burn-rate arithmetic). Both free online, both still the
  reference.
- **Gil Tene — ["How NOT to Measure Latency"](https://www.infoq.com/presentations/latency-response-time/)**
  — coordinated omission, why your p99 is probably a p50, and what a load generator owes you.
  Watch this before you quote another latency number.
- **Brendan Gregg — *Systems Performance*, 2nd ed.**, and his
  **[flame graph material](https://www.brendangregg.com/flamegraphs.html)** — the method (USE,
  latency heat maps) behind step 5, independent of any one tool.
- **[JDK Flight Recorder docs](https://docs.oracle.com/en/java/javase/25/troubleshoot/diagnostic-tools.html)**
  and **[JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)**
  — plus the `jdk.jfr` / `jdk.jfr.consumer` Javadoc, which is short and worth reading in full.
- **[Micrometer concepts](https://docs.micrometer.io/micrometer/reference/concepts.html)** —
  especially "Histograms and percentiles" and the naming/tagging guidance; the cardinality warning
  is in there and everybody skips it.
- **Michael Nygard — *Release It!*, 2nd ed.** — connection pool sizing, "the bottleneck moved",
  and why capacity is arithmetic.
- **Charity Majors, Liz Fong-Jones & George Miranda — *Observability Engineering*** — the case for
  high-cardinality events over pre-aggregated metrics; read it as the counterweight to step 2.
- **Alex Hidalgo — *Implementing Service Level Objectives*** — the book-length version of step 7,
  including how to have the product conversation.

---

**Build notes (verified August 2026, this machine).** Spring Boot 4.1.1 / Java 25,
`spring-boot-starter-webmvc` + `-actuator` + `-jdbc`, Testcontainers 2.0.5
(`org.testcontainers:testcontainers-postgresql`, `postgres:16-alpine`). Pristine `mvn -B test`:
**24 tests, 6 running and green, 18 checkpoints `@Disabled`, ~5 s wall clock, no Docker**.

Four findings worth recording, all verified here rather than assumed:

1. **JFR programmatic recording and parsing work exactly as advertised on JDK 25.**
   `jdk.JavaMonitorEnter` (threshold 1 ms) and `jdk.ObjectAllocationSample` (`throttle` setting)
   both carry stack traces that name the offending class; `jdk.ExecutionSample` and
   `jdk.ThreadPark` are available but yield very few events in a sub-second recording. Events
   without a `duration` field need an `event.hasField("duration")` guard.
2. **`publishPercentileHistogram()` materialises its bucket ladder only on registries that support
   aggregable percentiles.** On `SimpleMeterRegistry` you get exactly the `serviceLevelObjectives`
   boundaries you asked for — which is why step 3's checkpoint asserts on those.
3. **Pool size is `jdbc.connections.max`, not `hikaricp.connections.max`.** Boot's
   `DataSourcePoolMetadata` binder owns `jdbc.connections.{max,min,active,idle}` and registers them
   without opening a connection; Hikari's own tracker owns `hikaricp.connections.{pending,acquire,
   usage,timeout,…}` and only appears once the pool has started (it is lazy).
4. **`spring.jdbc.template.query-timeout` is truncated to whole seconds**, because
   `Statement.setQueryTimeout` takes an `int` of seconds — so any sub-second value silently means
   "no timeout". Sub-second statement bounds must come from the server
   (`connection-init-sql=SET statement_timeout = …`), which is what step 6 uses.

Reference numbers throughout were measured on this machine (Apple Silicon, JDK 25 via Homebrew);
absolute values will differ, the ratios should not.
