# 09 — Event Sourcing: Bank Account, Replayed

> After this lesson you can hand-roll a production-shaped event store on Postgres — append-only
> events table, optimistic append, fold-based rehydration, snapshots, and rebuildable
> projections — in about 150 lines, and you can argue precisely when doing so is a mistake.

## Why this matters (2026)

Event sourcing persists **what happened** instead of **what is**: the append-only sequence of
domain events is the system of record, and current state is derived by replaying (folding)
them. You get a perfect audit log, temporal queries ("state as of March 3rd"), and — the
genuinely unfair advantage — the ability to build read models **retroactively** that nobody
imagined when the data was written. You pay with event versioning discipline, projection
plumbing, and a mental model many teams underestimate.

Three things define the 2026 landscape:

1. **The field is moving again.** The Dynamic Consistency Boundary (DCB) — Sara Pellegrini's
   "kill the aggregate" work — drops the hard aggregate-per-stream rule in favor of tagged
   events and operation-scoped consistency, and it's the headline feature of Axon Framework 5
   (5.3.0 as of Aug 2026). EventStoreDB rebranded to **KurrentDB** (company: Kurrent, first
   release 25.0). Step 7 covers both.
2. **The criticism track is mature.** The community line (Oskar Dudycz, Jimmy Bogard) is blunt:
   event sourcing fails when applied system-wide, when events are CRUD-with-history
   ("property sourcing"), or without a versioning strategy. It is a per-module decision for
   places where audit and temporality are *business requirements* — not an architecture-wide
   default. This lesson ends on when NOT to use it, on purpose.
3. **In Java, hand-rolling is normal.** There is still no true Marten equivalent on the JVM;
   Postgres-based ES is typically hand-rolled (see Dudycz's EventSourcing.JVM) or done via
   Occurrent. That's less bad than it sounds — the machinery is small, and having built it
   once, frameworks stop being magic. The demystification IS this lesson.

Relationship to CQRS (project's sibling concept): event sourcing practically forces CQRS —
you cannot `SELECT` a fold, so reads come either from replay or from projected read models.
You'll build both and feel why they coexist.

Source material: [event-driven.md, section 4](../docs/research/event-driven.md) (event
sourcing) and [section 3](../docs/research/event-driven.md) (CQRS).

## Core concepts

**Two pure functions are the whole domain.** Everything else is plumbing:

```java
decide : (Command, State) -> List<Event>   // may reject; produces new facts
evolve : (State, Event)   -> State         // total; a fact cannot be refused
fold   = evolve repeated from EMPTY        // replay: state is derived, never stored
```

Commands are imperative and rejectable (`Withdraw`); events are past-tense facts
(`MoneyWithdrawn`). The decider is given, fully tested, with zero I/O — the same
sealed-interface-of-records, exhaustive-switch style as project 01. That purity is
load-bearing: every business rule in this project is tested without a database, and when you
add event types in step 6 the compiler walks you through every switch that must learn them.

**A stream per account, a version per event.** Events for account `acc-1` form stream
`acc-1`, versioned 1, 2, 3… contiguously. Rehydration = read stream, fold. There is no
"create stream" operation — a stream exists once its first event does.

**The primary key IS the optimistic lock.** The entire consistency mechanism of this event
store is one line of DDL:

```sql
PRIMARY KEY (stream_id, version)
```

A writer reads the stream (say, up to version 5), decides, and appends at versions 6, 7…
If another writer got there first, version 6 already exists and the INSERT dies with a
unique-key violation — which you translate to `ConcurrencyException`. No `SELECT … FOR
UPDATE`, no lost-update window, no lock held while thinking. This matters for correctness,
not just performance: the overdraft check was made against a balance that the competing
writer may have just spent.

**Snapshots are a cache, never truth.** A snapshot stores the fold result at version N;
rehydration becomes "load snapshot + fold the tail". Delete the snapshots table and nothing
is lost but speed. If deleting a table would lose data, it isn't a snapshot.

**Projections turn the log into tables.** A projector consumes events across all streams (in
this project: ordered by a `global_seq` column), applies them to a read-model table, and
remembers its position in a checkpoint. Because the log is the truth, a projection can always
be truncated and rebuilt — corrupt read models are an `UPDATE` away from healed. Polling a
global sequence is the simplest subscription model and is what you'll build; be aware of its
production caveats (a long-running transaction can commit a *lower* `global_seq` after a
higher one was already read past — real systems use transaction-id fencing, `LISTEN/NOTIFY`,
CDC, or a store with native catch-up subscriptions like KurrentDB).

**Retroactive read models are the payoff; versioning is the price.** Events written years ago
can feed a report invented yesterday (step 6). In exchange, old events never go away: rename
a record, and history stops deserializing. Production systems name types explicitly
(`account-opened.v2`), keep payloads weakly-schemad, and *upcast* old shapes on read.
Greg Young wrote an entire (free) book about just this problem; skim it before you ship ES
anywhere.

## The project

A bank account ledger. Rules: no overdraft, positive amounts only, close only at zero
balance, no operations on closed accounts. Events: `AccountOpened`, `MoneyDeposited`,
`MoneyWithdrawn`, `AccountClosed` — and in step 6 you add `FeeCharged` / `FeeRefunded`.

**Given** (read, don't write): the pure domain (`domain/` — decider, events, commands, state)
with enabled tests; the `EventStore` contract and a complete `InMemoryEventStore` (the app's
store until step 3, and the executable spec of the contract); serialization (`EventSerde`,
Jackson 3 under `tools.jackson`, with its own mapper — persisted formats don't borrow the web
layer's); Flyway migrations V1–V3; snapshot storage (`PostgresSnapshotStore`); projection
checkpoints; REST controllers and error mapping.

**Yours to write**: `PostgresEventStore` (steps 2–4), `BalancesProjector` (step 4),
`SnapshottingAccountRepository` (step 5), and in step 6 two event types, a migration, and a
whole new read model.

Run it:

```bash
docker compose up -d postgres     # local Postgres for manual runs (tests bring their own)
mvn spring-boot:run
mvn test                          # green on checkout: pure-domain tests + one container smoke test
```

Endpoints: `POST /accounts` `{accountId, owner}` · `POST /accounts/{id}/deposits|withdrawals`
`{amountCents, description}` · `POST /accounts/{id}/close` · `GET /accounts/{id}` (rehydrated)
· `GET /accounts/{id}/balance` (read model) · `POST /admin/projections/{name}/rebuild`.
Business rejections are 422; concurrency conflicts are 409 — the client can retry the latter
against fresh state, never the former.

Checkpoint tests are pre-written and `@Disabled("Checkpoint N — …")`; enable each as you
start the step. The events table is inspectable at every point — `psql` into it and read
your history; an event store you can't `SELECT` from is a debugging nightmare, which is half
the argument for building on Postgres.

## Guided steps

### Step 1 — Read the given domain: decide, evolve, fold

Read `AccountDecider`, the sealed `AccountEvent` / `AccountCommand`, and
`AccountDeciderTest`. Note what is absent: no store, no framework, no mocks, no clock.
Then read `InMemoryEventStore` and `InMemoryEventStoreTest` — the `EventStore` contract in
executable form, including the concurrent-append race you're about to re-win on Postgres.
Finally read `V1__event_store.sql` and its comments until "the PK is the lock" clicks.

**Done when** you can explain: why `evolve` cannot reject an event while `decide` can reject
a command; why the domain tests need no database; and what exactly happens in Postgres when
two writers append at the same expected version.

### Step 2 — Optimistic append on Postgres

Implement `PostgresEventStore.append`. Enable `Checkpoint2PostgresAppendTest` — it includes
the race: two threads, same expected version, exactly one winner, and all-or-nothing batches.

<details><summary>Hint — the shape of it</summary>

One multi-row INSERT is atomic (all rows or none) with zero transaction plumbing:

```java
var sql = new StringBuilder("INSERT INTO events (stream_id, version, type, payload) VALUES ");
var params = new ArrayList<>();
for (int i = 0; i < events.size(); i++) {
    if (i > 0) sql.append(", ");
    sql.append("(?, ?, ?, CAST(? AS jsonb))");
    var event = events.get(i);
    params.add(streamId);
    params.add(expectedVersion + 1 + i);
    params.add(serde.typeName(event));
    params.add(serde.toJson(event));
}
try {
    jdbc.sql(sql.toString()).params(params).update();
} catch (DuplicateKeyException e) {
    throw new ConcurrencyException(streamId, expectedVersion);
}
```

Do not insert `occurred_at` (the column defaults to `now()`) or `global_seq` (identity).
</details>

**Done when** checkpoint 2 is green, including `twoConcurrentAppendsAtTheSameVersionAdmitExactlyOneWinner`.

### Step 3 — Read, rehydrate, switch over

Implement both `readStream` overloads. Enable `Checkpoint3RehydrationTest`: it writes with
one store instance and reads with another, and its `stateSurvivesARestart` test rebuilds a
completely fresh object graph over the same database — the fold *is* the recovery procedure.
Then flip `ledger.event-store=postgres` in `application.properties` and re-run
`LedgerApiSmokeTest`: same behavior tests, now against durable truth.

<details><summary>Hint — mapping rows back</summary>

```java
private static final String SELECT =
    "SELECT global_seq, stream_id, version, type, payload::text AS payload, occurred_at FROM events";

// RowMapper body:
new StoredEvent(
    rs.getLong("global_seq"),
    rs.getString("stream_id"),
    rs.getLong("version"),
    serde.fromJson(rs.getString("type"), rs.getString("payload")),
    rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
```
</details>

**Done when** checkpoint 3 and the smoke test are green with the postgres store as default.

### Step 4 — Project the balances read model

Implement `readAll` (the global feed), then `BalancesProjector.runOnce()` and `rebuild()`.
Enable `Checkpoint4BalancesProjectionTest`. The core assertion is an equivalence — for every
account, the projected row equals the fold — plus the money shot: a hand-corrupted read model
healed by `rebuild()`. For a live demo, set `ledger.projections.polling=true` and watch
`GET /accounts/{id}/balance` trail writes by a poll interval: eventual consistency you can
measure.

<details><summary>Hint — the projector loop</summary>

```java
var from = checkpoints.position(NAME);
var batch = eventStore.readAll(from, BATCH_SIZE);
if (batch.isEmpty()) return 0;
for (var stored : batch) apply(stored.event());
checkpoints.advance(NAME, batch.getLast().globalSequence());
return batch.size();
```

`apply` switches over the event: `AccountOpened` → `INSERT … ON CONFLICT DO NOTHING` (rebuild
replays it), deposits/withdrawals → `UPDATE … SET balance_cents = balance_cents + :delta`,
`AccountClosed` → status flip. `rebuild()` = reset checkpoint, `TRUNCATE account_balances`,
drain `runOnce()` to zero.
</details>

**Done when** checkpoint 4 is green. Notice `@Transactional` on `runOnce`: batch + checkpoint
advance commit together, which is exactly why a crashed projector resumes without
double-applying.

### Step 5 — Snapshots, with proof

Implement `SnapshottingAccountRepository`. Enable `Checkpoint5SnapshotTest` — it wraps the
store in a counting decorator and asserts rehydration of a 25-event stream reads **at most 5**
events after the snapshot at version 20. "Faster" is a claim; "read 5, not 25" is a
measurement. When green, flip `ledger.snapshots.enabled=true` — every enabled test must stay
green, because snapshots are an optimization, never a behavior change. Perspective check:
this stream has 25 events. Snapshots earn their keep at 25 *thousand*; plenty of production
ES systems run happily without them. Measure before you cache — even here.

<details><summary>Hint — load and policy</summary>

Load: snapshot hit → fold from `snapshot.state()` over `readStream(id, snapshot.version())`;
miss → fold from `EMPTY` over the full stream. Version = last tail event's, or the
snapshot's if the tail is empty. Append: after appending, `newVersion = expectedVersion +
newEvents.size()`; if `newVersion % snapshotEvery == 0`, `load()` and save a snapshot at
that version. (Inline snapshotting is fine here; real systems do it out-of-band — it's
disposable, best-effort work.)
</details>

**Done when** checkpoint 5 is green and the property flip changes nothing else.

### Step 6 — Time travel: the read model nobody anticipated

The business ships monthly fees, then asks for a fees report **including history**. Enable
`Checkpoint6TimeTravelTest` and read it first: it seeds raw event rows (imports no new
classes — that history predates your code, morally speaking), then expects
`POST /admin/projections/monthly-fees/rebuild` to produce a correct `monthly_fees_report`
from full history — including streams that contain no fee events at all.

Your work: (a) add the event records — the payload shapes are dictated by the seeded JSON:

```java
record FeeCharged(String accountId, long amountCents, String month) implements AccountEvent {}
record FeeRefunded(String accountId, long amountCents, String month) implements AccountEvent {}
```

(b) follow the compiler — `evolve` (fees hit the balance) and the balances projector stop
compiling until they handle the new facts; add `ChargeFee`/`RefundFee` commands and decide
cases while you're there; (c) write migration `V4__monthly_fees_report.sql`; (d) write a
`MonthlyFeesProjector implements RebuildableProjection` — rebuild-on-demand is enough, no
checkpoint needed. Note `month` is a *field in the event*, not `occurred_at`: business time
belongs in the payload; storage metadata is not your calendar.

<details><summary>Hint — table and projector</summary>

```sql
CREATE TABLE monthly_fees_report (
    account_id          text   NOT NULL,
    month               text   NOT NULL,
    fees_charged_cents  bigint NOT NULL DEFAULT 0,
    fees_refunded_cents bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, month)
);
```

Rebuild: `TRUNCATE`, then page through `eventStore.readAll(position, 512)` from 0, switching
on the event — `FeeCharged`/`FeeRefunded` upsert with
`ON CONFLICT (account_id, month) DO UPDATE SET fees_charged_cents = monthly_fees_report.fees_charged_cents + EXCLUDED.fees_charged_cents, …`,
`default -> {}` passes old history by. Register the name as `monthly-fees` and the admin
endpoint finds it.
</details>

**Done when** checkpoint 6 is green. Sit with what happened: events written in "January" by
code that had never heard of fees just fed a report born today. A CRUD system would be
reconstructing this from backup dumps and guesswork. Then re-read `EventSerde.fromJson`'s
failure branch — the flip side is that every event type you ever shipped is a contract you
maintain forever; that's what upcasting and Young's versioning book are for.

### Step 7 — Debrief: when not to, and what's next (reading only)

**When NOT to event-source.** The classic failure is *system-wide* event sourcing — every
module forced through streams, including plain reference data that wanted to be a table. The
second is *property sourcing*: events like `EmailUpdated {email}` that mechanically record
column changes instead of business facts (`CustomerRelocated`) — you inherit all of ES's
costs and none of its meaning. The third is skipping versioning strategy until v2 ships.
Watch Dudycz's "Let's build the worst Event Sourcing system!" — anti-patterns taught by
inversion, ideal after you've built the real thing. Honest heuristic: default to state
persistence; event-source the modules where an auditor, a regulator, or a product manager
genuinely asks "what happened, when, and what did we believe at the time?" — a ledger, like
this one, being the canonical yes.

**The DCB frontier.** Your store enforces invariants per stream — fine for "no overdraft on
one account". Now try "Ada and Bob's *combined* balance may not go below zero". With
aggregate-per-stream you must either merge both accounts into one clumsy stream or bolt on
sagas/locks. The Dynamic Consistency Boundary approach (Pellegrini/Savić, Axon 5,
dcb.events) tags events (`{account:ada}`, `{account:bob}`) and lets each *operation* declare
its consistency scope: "my decision read all events tagged ada-or-bob up to position N —
reject my append if that set has grown." The optimistic-concurrency idea you built in step 2
survives; what changes is the unit it's scoped to — a query, not a stream.

**The dedicated-store path.** KurrentDB (ex-EventStoreDB) gives you real catch-up
subscriptions (no polling), server-side projections, and stream APIs as the native model —
see the stretch goal; `docker compose --profile stretch up -d` already has it waiting.

**Done when** you can answer the last three self-check questions without notes.

## Self-check

1. Two requests race to withdraw from the same account. Walk through exactly how the schema —
   not any Java code — prevents the double-spend. What does the loser see, and what should a
   client do about a 409 that it must never do about a 422?
2. Why does `decide` return events instead of mutating state? Name two concrete things this
   project could not do if the domain functions did their own I/O.
3. Your snapshots table was dropped in production. What is lost? Your `account_balances`
   table was corrupted by a manual UPDATE. What's the fix, and why is it safe?
4. Why does the fee month live in the event payload rather than in `occurred_at`? Give a
   failure that occurs if you use storage time as business time (hint: backfills, timezones,
   late-arriving events).
5. A colleague proposes `ProfileFieldChanged {field, oldValue, newValue}` as the event model
   for user profiles. What is this anti-pattern called, and what two questions do you ask to
   decide whether profiles should be event-sourced at all?
6. What breaks when you rename the `MoneyDeposited` record to `CashDeposited`, when does it
   break (compile, startup, or read time?), and what's the production-grade defense?
7. Why is polling `global_seq` not a bulletproof subscription mechanism under concurrent
   writers, and name two real-world alternatives.
8. State the two-account invariant that aggregates handle awkwardly and sketch how DCB's
   tagged events + operation-scoped append condition handle it.

## Stretch goals

- **Retry-on-conflict.** A 409 for "two deposits at once" is technically right and
  operationally silly — deposits commute. Add a small retry loop around
  `AccountService.handle` (reload, re-decide, re-append, max 3 attempts), then write a test
  firing 10 parallel deposits and asserting all land. Discuss: why must `Withdraw` re-decide
  rather than blindly re-append at the new version?
- **Time-travel endpoint.** `GET /accounts/{id}?asOfVersion=N` — fold only the first N
  events. Ten lines, and suddenly you can answer "what did we believe on March 3rd?" — add
  `asOf` (timestamp) using `occurred_at` and discuss why version is the sturdier axis.
- **An upcaster for real.** Add `currency` to `MoneyDeposited` (default `"EUR"`). Old
  payloads lack the field; make `EventSerde` upcast on read (transform the JSON before
  binding, keyed by type name), and prove old history still folds. Compare with Young's
  weak-schema guidance.
- **The KurrentDB port.** `docker compose --profile stretch up -d` starts KurrentDB 25.x
  (insecure dev mode, UI at http://localhost:2113). Add `io.kurrent:kurrentdb-client` and
  reimplement `EventStore` on it: `appendToStream` with `expectedRevision` is your step 2,
  a catch-up subscription replaces your polling projector. Notice how much of your design
  survives — the contract was the lesson, the store is a vendor.

## Resources

- **Martin Fowler — "Event Sourcing"** (martinfowler.com) — the canonical pattern write-up;
  short, and honest about complexity.
- **Greg Young — *Versioning in an Event Sourced System*** (free book, leanpub) — the
  hardest practical problem, from the person who named the pattern; pair with his *CQRS
  Documents* PDF.
- **Oskar Dudycz — event-driven.io + EventSourcing.JVM** (github.com/oskardudycz/EventSourcing.JVM)
  — the pragmatic JVM canon: hand-rolled Postgres stores like this one, self-paced kits, and
  the "when not to" essays.
- **Oskar Dudycz — "Let's build the worst Event Sourcing system!"** (NDC London 2024,
  YouTube) — anti-patterns by inversion; the best hour you can spend after this lesson.
- **Sara Pellegrini & Milan Savić — "The Aggregate Is Dead. Long Live the Aggregate!"**
  (talk) + **dcb.events** + AxonIQ's "DCB in Axon Framework 5" — the DCB canon, from
  provocation to shipping implementation.
- **Kurrent Academy** — "Beginner's Guide to Event Sourcing"; vendor slant, solid
  fundamentals, and the natural companion to the stretch goal.

---

*Build notes (verified Aug 2026): Boot 4 modularized Flyway — the autoconfiguration lives in
`spring-boot-starter-flyway`; with bare `flyway-core` on the classpath migrations silently
never run. Testcontainers 2.0.5: the Postgres module is `testcontainers-postgresql`, and the
class moved to `org.testcontainers.postgresql.PostgreSQLContainer` — no longer generic (the
1.x `PostgreSQLContainer<?>` self-type is gone). Jackson 3 ships under `tools.jackson` with
unchecked exceptions; this project deliberately builds its own `JsonMapper` for persisted
payloads instead of injecting the web-facing one.*
