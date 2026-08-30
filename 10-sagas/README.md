# 10 — Sagas: Trip Booking That Fails on Purpose

> After this lesson you can implement a business transaction that spans four services with no
> distributed lock and no two-phase commit — first as choreographed events, then as a persisted
> orchestrator — and you can say, from experience rather than from a blog post, which one you
> want and why "where is booking #42 stuck?" is the question that decides it.

## Why this matters (2026)

Projects 07 and 08 gave you reliable delivery and eventually-consistent reads. This one asks the
harder question: **what happens when step three of a five-step business operation fails, and
steps one and two already committed in databases you cannot roll back?**

There is no `@Transactional` that spans services. The saga pattern — a sequence of local
transactions, each with a compensating action that semantically undoes it — is the answer the
industry settled on, and it comes in two shapes:

- **Choreography.** Each participant reacts to events and emits its own. No coordinator. Minimal
  infrastructure, maximum decoupling — and the flow's logic exists nowhere in particular.
- **Orchestration.** One component owns the flow: it sends commands, tracks state, and runs
  compensations in reverse. One more moving part, and one place that can answer "where is it?"

The 2026 consensus (see [`../docs/research/event-driven.md`](../docs/research/event-driven.md) §6)
is a rule of thumb worth memorizing: **choreography for three or four linear steps; orchestration
beyond that — for observability alone.** And the market has consolidated around *durable
execution* as how orchestrators get written: Temporal, Camunda 8, Axon sagas. You write ordinary
Java; the engine persists every step so the program survives crashes and can wait for days.

Step 5 of this lesson has you hand-roll crash-resume. Step 6 shows you what you just reinvented.
That order is deliberate — durable execution is unremarkable magic until you have built the
un-magical version.

## Core concepts

**Events vs commands, and why the distinction carries the design.** An event is a past-tense
fact anyone may react to (`FlightReserved`); a command is an imperative addressed to exactly one
service (`CancelFlight`). Read [`TripMessage`](src/main/java/dev/vlearning/trips/messages/TripMessage.java):
events travel on one shared `trips.events` topic, commands on the target service's own inbox
topic. Choreography is what you get when only events exist. Orchestration is what appears the
moment something is allowed to *tell* a service what to do.

**Compensation is not rollback.** A rollback erases history; a compensation adds to it. Cancelling
a flight is a new business fact, possibly with a fee, possibly visible to the customer. The
compensating action for "charge €400" is "refund €400", not "pretend the charge never happened".
Design your compensations as first-class operations, because they are.

**Semantic locks and the countermeasures.** Sagas expose intermediate state: between
`FlightReserved` and `PaymentCaptured` the trip is neither booked nor not-booked. The trip's
`PENDING` status is the countermeasure here — a semantic lock that tells every reader "this is
in flight". Richardson's countermeasure catalogue (semantic lock, commutative updates,
pessimistic view, reread value, version file, by-value) is the reading for when `PENDING` is not
enough.

**The stuck-saga problem.** This is the real cost of choreography, and step 3 makes you feel it.
When the flow lives in five listeners across four tables, "where is booking #42?" requires
joining logs to state in your head. The orchestrator's answer is one row in `saga_instance`.

## The project

One Spring Boot app that **simulates four services** — `booking`, `flight`, `hotel`, `payment` —
as packages that may communicate *only* through Kafka. That constraint is not a suggestion:
[`ArchitectureTest`](src/test/java/dev/vlearning/trips/ArchitectureTest.java) fails the build if
one service package references another's classes. You get distribution's coordination problems
without four terminals and four JVMs; everything else about the design is honest.

Four tables, one per service, connected by no foreign keys — because they are standing in for
four databases (see [`schema.sql`](src/main/resources/schema.sql)).

**What is given, complete and working:**

- **The three participants.** `flight`, `hotel`, and `payment` each listen on their own command
  topic, do their local transaction, and publish a result event — including the compensating
  commands (`CancelFlight`, `CancelHotel`, `RefundPayment`). *You are not writing participants.
  You are writing coordination.*
- **Failure injection.** [`ChaosToggles`](src/main/java/dev/vlearning/trips/chaos/ChaosToggles.java)
  via `POST /chaos/{service}/fail-next` (participant replies with a rejection event) and
  `POST /chaos/{service}/drop-next` (participant swallows the command and never replies — the
  timeout case that separates real orchestrators from happy-path demos).
- **The booking API.** `POST /trips` inserts the trip as `PENDING` and publishes `TripRequested`.
  `GET /trips/{id}` reports status. Nothing listens to `TripRequested` yet: every trip stays
  `PENDING` forever. That void is your work.
- **`OrchestratorSwitch`** — pauses and resumes the orchestrator's Kafka listener container, so
  step 5 can "crash" it mid-saga without killing the JVM.
- **The Temporal round**, compilable and ready: `TripBookingWorkflow(Impl)` with Temporal's `Saga`
  compensation helper, a worker runner, and `docker-compose.temporal.yml`.

**Run it:**

```bash
docker compose up -d
```

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Tests use Testcontainers (Postgres + Kafka) and need no compose stack:

```bash
mvn test
```

## Guided steps

### Step 1 — Choreography: make the happy path work

**Goal.** Wire the saga with events only. `booking` publishes `TripRequested`; something must turn
that into `ReserveFlight`, then `FlightReserved` into `ReserveHotel`, then `HotelReserved` into
`CapturePayment`, then `PaymentCaptured` into a `CONFIRMED` trip.

The design decision: *who* sends the commands? In pure choreography each participant reacts to
the previous participant's event on its own. Put a listener in each service package that consumes
`trips.events` and reacts to the one event that concerns it.

<details><summary>Hint</summary>

Add a `@KafkaListener` in each participant package on `${trips.topics.events}` with a distinct
`groupId`, decode with `TripMessageCodec`, and `switch` over the sealed `TripMessage`. Reacting
to an event means publishing a *command* to your own service's command topic — the existing
handler does the work. Note how `FlightReserved` carries `destination` and `price`: hotel and
payment need data that was never theirs. That is the choreography smell called out in
`TripMessage`'s javadoc; live with it for now and name it in your notes.
</details>

**Done when** `Checkpoint1ChoreographyHappyPathTest` passes — `happyPathEndsConfirmedWithAllThree
BookingsInPlace`.

### Step 2 — Choreography: compensation

**Goal.** Handle the three failure paths. `HotelRejected` must cancel the flight and reject the
trip. `PaymentFailed` must cancel hotel *and* flight. `FlightRejected` rejects the trip with
nothing to compensate.

**Done when** `Checkpoint2ChoreographyCompensationTest` passes (three tests). Drive the failures
with `POST /chaos/hotel/fail-next` — the test does this for you; do it by hand too, and watch the
tables.

<details><summary>Hint</summary>

Who issues `CancelFlight` when hotel rejects? In choreography, whoever knows: flight itself can
listen for `HotelRejected`. Notice what you are accumulating — every participant now knows a
little about the trip's *flow*, not just its own job. Count the places that knowledge lives; you
will need that number in step 3.
</details>

### Step 3 — Feel the pain: "where is booking #42 stuck?"

**Goal.** `Checkpoint3StuckSagaDiagnosisTest` seeds a half-finished saga using
`POST /chaos/hotel/drop-next` — hotel receives `ReserveHotel` and never answers. Enable it, run
it, read the table dumps it prints, and answer, using only logs and tables:

1. Which step is this trip waiting on?
2. Which participant owes a reply?
3. What is the compensation set if you give up now?
4. How long has it been waiting, and what would time it out?

Then write down every file that contains a piece of the flow's logic.

**Done when** you have written the four answers and the file list, and can state why question 4
has no good answer in this design. (There is no assertion to satisfy — the test is an
investigation harness. The lesson is the difficulty, not the test result.)

<details><summary>What you should find</summary>

The flow is distributed across every participant's event listener; no single place knows the
saga's shape, so nothing can time it out or report it. The trip row says `PENDING` — true, and
useless. This is the argument for orchestration, and you just made it yourself.
</details>

### Step 4 — Orchestration: one component owns the flow

**Goal.** Implement a `TripSagaOrchestrator` in the `orchestration` package: a persisted state
machine over `saga_instance` (`trip_id`, `current_step`, `status`, using the `SagaStep` /
`SagaStatus` enum names). It consumes `trips.events`, sends commands, advances the step, and on
failure walks compensations in reverse (`COMPENSATING_HOTEL` → `COMPENSATING_FLIGHT`).

Delete the choreography listeners as you go: the participants go back to being dumb command
handlers, which is the point.

<details><summary>Hint</summary>

One `@KafkaListener` on the events topic, one `switch` over `(currentStep, event)`. Persist the
new step **in the same transaction** as sending the next command if you can, or send after commit
and accept at-least-once — project 07's outbox is the honest fix, and saying so in a comment is a
legitimate answer here. `COMPLETED` and `COMPENSATED` are both successful ends; `RUNNING` and
`COMPENSATING` are the states that can get stuck.
</details>

**Done when** `Checkpoint4OrchestrationTest` passes — including `whereIsItStuckIsNowOneSelect`,
which asserts the intermediate state is queryable from `saga_instance`. Compare that test to your
step 3 investigation.

### Step 5 — Crash-resume: durable execution, by hand

**Goal.** `Checkpoint5CrashResumeTest` uses `OrchestratorSwitch.crash()` to pause the orchestrator
mid-saga, then `restart()`. Your orchestrator must resume from persisted state and finish the
trip — no in-memory saga state, no lost bookings.

<details><summary>Hint</summary>

If your orchestrator keeps anything about a saga in a field, a map, or a `CompletableFuture`, this
test kills it. Everything the flow needs must be reconstructable from `saga_instance` plus the
event that just arrived. That constraint *is* durable execution.
</details>

**Done when** `sagaResumesFromPersistedStateAfterTheOrchestratorCrashes` passes.

### Step 6 — What you just reinvented: Temporal

**Goal.** Run the same saga on a durable-execution engine and compare.

```bash
docker compose -f docker-compose.temporal.yml up -d
```

```bash
TEMPORAL_ADDRESS=127.0.0.1:7233 mvn test -Dtest=TemporalTripSagaTest
```

(The test is gated on `TEMPORAL_ADDRESS`, so the normal build never needs the server.) Read
[`TripBookingWorkflowImpl`](src/main/java/dev/vlearning/trips/temporal/TripBookingWorkflowImpl.java):
the whole saga is straight-line Java with a `Saga` helper collecting compensations. No state
machine, no `saga_instance` table, no Kafka listener — because the engine persists the program's
execution. Open the Web UI at <http://localhost:8080> and look at a workflow's event history:
that is your step-4 table, generated for free, plus timers, retries, and queryable state.

**Done when** you have filled in the decision matrix below from experience — this is the lesson's
deliverable:

| | Choreography | Hand-rolled orchestrator | Durable execution |
|---|---|---|---|
| Lines of coordination code | | | |
| Where the flow's logic lives | | | |
| Answers "where is #42 stuck?" | | | |
| Survives a crash mid-saga | | | |
| Handles a participant that never replies | | | |
| New infrastructure to operate | | | |
| Right choice when… | | | |

## Self-check

1. Why is a compensation not a rollback, and what does that imply for a `RefundPayment` that
   arrives twice?
2. In step 2, which service issued `CancelFlight`, and what did that decision cost you in
   coupling?
3. What is a semantic lock, and which field plays that role in this project?
4. Your orchestrator sends `ReserveHotel` and the process dies before the DB commit records the
   step. What happens on restart, and which pattern from project 07 fixes it properly?
5. Hotel never replies. Where does that timeout belong in each of the three designs?
6. Why do `COMPLETED` and `COMPENSATED` both count as success, and what does that say about
   monitoring a saga?
7. When would you pick choreography *knowing* everything this lesson taught?
8. What does Temporal persist that your `saga_instance` table does not?

## Stretch goals

1. **Timeouts.** Add a scheduled sweeper that finds `saga_instance` rows stuck in one step past a
   deadline and compensates them. Then note that Temporal gives you this as one line of workflow
   code.
2. **Idempotent participants.** Fire `CancelFlight` twice (`drop-next` plus a manual resend) and
   make the participants tolerate it — reusing project 07's idempotency table pattern.
3. **Parallel steps.** Reserve flight and hotel *concurrently*, then pay. Notice what this does to
   a choreographed design versus an orchestrated one, and what it does to compensation ordering.
4. **Camunda 8 / BPMN.** Model the same saga with compensation boundary events. When stakeholders
   need to *see* the process, the visual model is the feature — see Ruecker.

## Resources

- **Hector Garcia-Molina & Kenneth Salem — "Sagas" (1987)** — the original paper; short and
  readable, and it predates every framework in this lesson.
- **Chris Richardson — *Microservices Patterns*, 2nd ed. (Manning, MEAP)** and the free
  [saga pattern page](https://microservices.io/patterns/data/saga.html) — the standard treatment,
  including the countermeasure catalogue.
- **Bernd Ruecker — *Practical Process Automation* (O'Reilly)** and his conference talks — the
  orchestration-advocate canon; read for the "choreography scales badly in comprehension"
  argument.
- **Temporal — ["Temporal 101 / 102 with Java"](https://learn.temporal.io)** and
  ["To Choreograph or Orchestrate Your Saga"](https://temporal.io/blog/to-choreograph-or-orchestrate-your-saga-that-is-the-question)
  — free courses plus the decision framing.
- **ByteByteGo — "Saga Pattern Demystified"** — a concise modern comparison, good pre-reading.
- Project context: [`../docs/research/event-driven.md`](../docs/research/event-driven.md) §6 (sagas)
  and §1 (choreography vs orchestration).

---

**Build notes (verified August 2026).** Spring Boot 4.1.1 on Java 25; Kafka auto-configuration in
Boot 4 comes from the modularized `spring-boot-starter-kafka` — a bare `spring-kafka` dependency
compiles but auto-configures nothing. Testcontainers 2.0.5 with the renamed artifacts
(`testcontainers-postgresql`, `testcontainers-kafka`) and relocated classes
(`org.testcontainers.kafka.KafkaContainer`). Images: `postgres:16-alpine`, `apache/kafka:4.1.0`.
Temporal Java SDK 1.38.0. Pristine `mvn test`: green, with the five checkpoint classes `@Disabled`.
