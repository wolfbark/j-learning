# 08 — CQRS: Library Lending, Split in Two

> After this lesson you can split a service along its command/query seam, sync a denormalized
> read model with domain events, explain exactly what staleness your users will feel — and heal
> a broken projection by replaying from the source.

## Why this matters (2026)

CQRS has completed a full hype cycle. The 2010s version — "every service gets a write database,
a read database, and a message bus between them" — produced enough wreckage that the pendulum
has swung hard toward *pragmatic, single-database CQRS* (see `docs/research/event-driven.md`,
§3). The 2025–2026 literature (Oskar Dudycz's "CQRS facts and myths explained", the current
foojay.io and Java-guide crop) repeats one message: **CQRS is a code-organization pattern
first, not a two-database mandate.** At minimum it is two code paths over one database. Every
level above that — a denormalized view, a separate store, a separate service — must earn its
complexity with a genuinely divergent read shape or scaling profile.

Greg Young, who named the pattern, said as much from the start: *CQRS is not a top-level
architecture* — it is something you apply to a bounded context, or a slice of one, where it
pays. That warning is being actively rediscovered. Meanwhile the framework path got a major
refresh: Axon Framework 5 (Nov 2025, 5.3 as of Aug 2026) is a ground-up redesign of the leading
Java CQRS/ES stack — worth knowing about, and deliberately *not* used here. You will hand-roll
everything, so you know what any framework is doing for you.

This project walks the levels in order, and — just as important — makes you *feel* the cost of
each one before you pay for the next.

## Core concepts

**Command/query separation, scaled up.** Bertrand Meyer's CQS says a method either changes
state or returns data, never both. CQRS applies that at the model level: the code (and
eventually the storage) that handles `borrowBook` is separate from the code that serves the
member-activity dashboard. Different shapes, different consistency needs, different reasons to
change.

**The three levels — each one optional:**

| Level | What splits | Costs | Buys you |
|---|---|---|---|
| 1. Code split | packages/classes, one DB | almost nothing | use-case-named handlers, independent evolution, testability |
| 2. Same-DB read model | a denormalized table, synced by events | projector plumbing, backfill, eventual consistency | reads become `SELECT *`, write model stays normalized |
| 3. Separate read store | database/technology | infra, ops, lag monitoring, rebuild tooling | independent scaling, purpose-built tech (search index, cache) |

This lesson takes you through levels 1 and 2. Level 3 is the same ideas with more ops — if you
can do 2, 3 is a deployment problem, not a concept problem.

**Domain events as the sync mechanism.** The write side publishes facts (`BookBorrowed`,
`BookReturned`) via `ApplicationEventPublisher` *inside* the command transaction. A
`@TransactionalEventListener(phase = AFTER_COMMIT)` consumes them only once the transaction is
real — you must never project data that might roll back. Two subtleties you will hit in step 3:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // fact is durable
@Transactional(propagation = Propagation.REQUIRES_NEW)             // old tx is finished; open a fresh one
public void on(BookBorrowed event) { ... }
```

**Projection styles.** A projector can apply *deltas* from the event payload (increment
`open_loans`), or treat the event as a *trigger* and recompute the affected row from the source
tables. You will build the trigger style: it is trivially correct, idempotent, and makes
rebuild identical to normal operation. The delta style is what you graduate to when recompute
is too expensive — and it is harder than it looks (try maintaining `distinct_authors` from a
`BookBorrowed` payload alone; stretch goal 2).

**Eventual consistency is a product decision.** Once the dashboard is a projection, a user can
borrow a book and — if the projector is async, lagging, or down — not see it on their dashboard.
That gap is not a bug to eliminate; it is a property to design for (step 4).

**Replay heals.** A projection is derived data. If it drifts, breaks, or needs a new column:
throw it away and rebuild. Here, with no event store, you rebuild *from current state* — which
recovers today's truth but not history. Project 09 (event sourcing) is where the event log
itself becomes the source and replay gets superpowers.

## The project

A library lending service. Members borrow and return books; the product team's pride is the
**member activity dashboard**: total/open/returned/late loan counts, distinct books and
authors, favorite author, last activity, and the list of current loans with overdue flags.

**What's given:** a working app with three endpoints and one `LibraryService` doing everything:

| Endpoint | Behavior |
|---|---|
| `POST /api/loans` `{memberId, bookId}` | 201 + `{loanId, dueOn}`; 409 when no copy is free or the member holds 5 open loans; 404 unknown ids |
| `POST /api/loans/{id}/return` | 200 + `{loanId, returnedOn, late}`; 409 when already returned |
| `GET /api/members/{id}/activity` | 200 + the full dashboard |

Flyway migrations create the schema (`members`, `books`, `loans`) and seed ten members with a
deterministic loan history. `LendingApiBehaviorTest` (enabled) pins all behavior and must stay
green through every step. Five checkpoint tests are `@Disabled` — enable each as you reach it.

**Start by reading `LibraryService.getMemberActivity`.** Really read it. One HTTP request
costs: a three-table join returning one row per loan the member ever had, then Java-side
counting, two hash sets, a frequency map, a tie-breaking scan, and a sort. Every dashboard hit
redoes work whose answer only changes when that member borrows or returns. The write path next
to it is four small statements. Those two code paths have nothing in common — they only live in
the same class because CRUD habits put them there. That asymmetry is the entire motivation for
this lesson.

**Run it:**

```bash
mvn test                     # Testcontainers PostgreSQL; Docker must be running

# or run the app against a local PostgreSQL:
docker run --rm -e POSTGRES_USER=lending -e POSTGRES_PASSWORD=lending \
  -e POSTGRES_DB=lending -p 5432:5432 postgres:16-alpine
mvn spring-boot:run
curl localhost:8080/api/members/1/activity
```

The tests freeze the clock at **2026-09-01** (`app.fixed-clock` in `src/test/resources`), so
seeded due dates and overdue flags are deterministic. All date logic goes through the injected
`java.time.Clock` — keep it that way.

## Guided steps

### Step 1 — Split the code paths (this alone is CQRS)

Dissolve `LibraryService` and `LibraryController` along the command/query seam:

- `dev.vlearning.lending.commands` — `BorrowBookHandler`, `ReturnBookHandler`, and a controller
  for the two POST endpoints. One handler per use case, named after the use case.
- `dev.vlearning.lending.queries` — `MemberActivityReader` (the monster query, moved verbatim)
  and a controller for the GET endpoint.

Same database, same SQL, zero behavior change — and yet this is already CQRS in its minimal,
legitimate form. Handlers stop competing for the same class, the read path can now change shape
without touching invariants, and the dependency rule ("commands never call queries, queries
never call commands") becomes mechanically checkable.

**Done when:** `LendingApiBehaviorTest` still green + `Checkpoint1CommandQuerySplitTest`
enabled and green (ArchUnit enforces the no-dependency rule in both directions, and that the
do-everything service is gone).

<details><summary>Hint — where things land</summary>

Shared, staying in the root package: `MemberActivity` (the read DTO), `ApiExceptionHandler`,
`ProjectionToggle`, the `events` package. Both sides may depend on root; neither may depend on
the other. Move the `LoanReceipt`/`ReturnReceipt` records into their handlers. The controllers
move into `commands`/`queries` respectively — a controller is part of the side it serves.
</details>

### Step 2 — The write side announces facts

Inject `ApplicationEventPublisher` into both handlers and publish the given event records —
`events.publishEvent(new BookBorrowed(loanId, memberId, bookId, borrowedOn, dueOn))` after the
insert, `BookReturned(...)` after the update — *inside* the `@Transactional` method.

Nothing consumes these yet. That is the point: the write side's job ends at "this fact is now
true"; it neither knows nor cares that a dashboard exists. Publishing inside the transaction
matters because of what comes next: `@TransactionalEventListener` binds delivery to the
transaction's fate, so a rolled-back borrow never reaches a listener. (`publishEvent` on its
own is synchronous and in-process — no broker, no magic.)

**Done when:** `Checkpoint2DomainEventsTest` enabled and green (Spring's `@RecordApplicationEvents`
captures what you publish).

### Step 3 — Build the read model

Now make the dashboard read *precomputed* data.

1. **Migration `V3__member_activity_view.sql`** — two real tables (not SQL views), shaped like
   the dashboard: `member_activity_view` (one row per member, all the scalar fields) and
   `member_open_loans_view` (one row per open loan: `loan_id`, `member_id`, `title`, `due_on`).
2. **`MemberActivityProjector`** (in `queries`) — listens `AFTER_COMMIT` for both events and
   reprojects the affected member. Cheapest correct implementation: call the legacy
   `MemberActivityReader` for that one member and write the result into the two tables
   (delete + insert). The monster query still runs — but per *write*, not per *read*, and only
   for one member.
3. **Backfill** — on `ApplicationReadyEvent`, if the view is empty, project every member once.
   Any projection added to a live system has history to catch up on before events keep it
   current.
4. **`MemberActivityViewReader`** — two trivial `SELECT`s (order open loans by `due_on, loan_id`;
   compute `overdue` against the clock at read time; empty view row lookup → the same 404 as
   before).
5. **Flip the endpoint** — `/api/members/{id}/activity` now serves the view reader. Keep the
   monster alive at `/api/members/{id}/activity-legacy`.

Keeping both paths is strangler-style parallel running: the parity test comparing them is the
*only* proof your projection is right. In production you would diff the two on live traffic for
days before deleting the old path.

**Done when:** `Checkpoint3ReadModelParityTest` enabled and green (view and legacy agree field
for field on all untouched seeded members), and the behavior tests — including
`borrowingShowsUpInTheDashboardImmediately` — still green. That last one deserves a pause: it
passes only because your `AFTER_COMMIT` listener runs synchronously, on the request thread,
before the HTTP response leaves. You still have read-your-writes — for now.

<details><summary>Hint — the view schema</summary>

```sql
CREATE TABLE member_activity_view (
    member_id        BIGINT PRIMARY KEY,
    name             TEXT NOT NULL,
    email            TEXT NOT NULL,
    total_loans      INT  NOT NULL,
    open_loans       INT  NOT NULL,
    returned_loans   INT  NOT NULL,
    late_returns     INT  NOT NULL,
    distinct_books   INT  NOT NULL,
    distinct_authors INT  NOT NULL,
    favorite_author  TEXT,
    last_activity_on DATE
);

CREATE TABLE member_open_loans_view (
    loan_id   BIGINT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    title     TEXT   NOT NULL,
    due_on    DATE   NOT NULL
);
CREATE INDEX idx_open_loans_view_member ON member_open_loans_view (member_id);
```
</details>

<details><summary>Hint — the projector's ears</summary>

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void on(BookBorrowed event) {
    projectMember(event.memberId());
}
```

Why `REQUIRES_NEW`? The listener runs after the command's transaction has completed — there is
nothing left to join. Default propagation would silently run your writes on the completed
transaction's connection resources; a fresh transaction is the documented pattern for DB work
in `AFTER_COMMIT` listeners. Note the projector only uses `event.memberId()` — the event is a
trigger, not a data carrier, in this style. Nullable insert params (`favorite_author`,
`last_activity_on`): give `JdbcClient` the SQL type, e.g.
`.param("favoriteAuthor", activity.favoriteAuthor(), Types.VARCHAR)`.
</details>

### Step 4 — Feel the staleness

Wire the given `ProjectionToggle` into the projector: when paused, the listener returns without
projecting. The event is simply dropped — no queue, no retry. This simulates the real failure
modes of level 2+: a crashed projector, a lagging async consumer, a broker backlog.

Enable `Checkpoint4EventualConsistencyTest` and watch what it proves: with the projector down, a
borrow returns 201, the `loans` table has the row, the legacy query sees it — and the dashboard
the user is looking at says nothing happened. And when the projector resumes: *still* nothing.
The missed event is gone.

This is the moment CQRS skeptics are talking about, so know the product answers by name:

- **Render from the command result.** Your 201 already returns `{loanId, dueOn}` — the
  confirmation screen needs nothing from the read model. Most "I don't see my write!" bugs are
  UIs that pointlessly round-trip through a projection.
- **Version tokens / read-your-writes.** The write returns a version (e.g. the loan id); the
  client polls or the read side blocks until the projection has caught up to that version.
- **Optimistic UI.** Apply the change client-side immediately; reconcile when the projection
  catches up.
- **Honesty.** "Your dashboard may take a moment to update" is a legitimate answer for
  genuinely async projections — if the product owner signed off on it.

**Done when:** `Checkpoint4EventualConsistencyTest` enabled and green.

### Step 5 — Replay to heal

Expose the projector's rebuild as `POST /api/admin/member-activity/rebuild`: truncate both view
tables, reproject every member from the system of record, respond
`200 {"projectedMembers": n}`. (Your step-3 backfill is the same operation — extract/reuse.)

Be precise about what this is: **replay from state, not from events.** There is no event store
here; the `loans` table is the source of truth and it happens to retain full history, so the
rebuilt view is complete. Two honest caveats: (a) most write models *don't* retain history —
if loans were hard-deleted after return, no rebuild could recover `late_returns`; the view only
looks perfectly rebuildable because this write model is accidentally log-like. (b) A rebuild
computes *today's* truth — with an event log you could also rebuild views that old code never
anticipated, at any point in time. That is project 09's opening argument.

Also worth noticing: rebuild-while-serving has a consistency window (the truncate-to-reprojected
gap). Production systems rebuild into a shadow table and swap, or version their projections.

**Done when:** `Checkpoint5ReplayHealsTest` enabled and green — projector downtime, missed
events, rebuild, convergence with the legacy query.

### Step 6 — Debrief: when does CQRS earn its keep?

No code. Read [Fowler's CQRS page](https://martinfowler.com/bliki/CQRS.html) and Dudycz's
["CQRS facts and myths explained"](https://event-driven.io/en/cqrs_facts_and_myths_explained/),
then the [Axon Framework 5.0 release post](https://www.axoniq.io/blog/release-of-axon-framework-5-0)
as the "what a framework buys you" tour: command gateways and handler routing, event processors
that are your projector with retries/tracking/replay built in, dead-letter queues, and (new in
5) dynamic consistency boundaries. Map each Axon concept to the class you hand-rolled this week.

The honest scorecard for this project:

- **Step 1 was nearly free** — and worth it in almost any service with non-trivial reads.
  If you take one thing from this lesson, it is that "CQRS" can mean *just this*.
- **Steps 2–5 cost real complexity**: events, a projector, backfill, a toggle, a rebuild
  endpoint, and a new failure mode (staleness) that reached your *product* conversations. The
  payoff was one query becoming a `SELECT` — worth it only when that query (or its load, or its
  storage shape) actually hurts.

Greg Young's warning, restated for 2026: apply CQRS to a slice that needs it, not to an
architecture diagram. Fowler's: CQRS done well is a sharp tool for a specific pain; done badly
it is a system-wide tax paid for no benefit.

**Done when:** you can write a one-paragraph ADR either adopting or rejecting a separate read
model for project 06's library — and defend both versions.

## Self-check

1. What does CQRS mean *at minimum*, and what did the pure code split buy you before any read
   model existed?
2. Why must the projector listen `AFTER_COMMIT` rather than with a plain `@EventListener`?
   What corruption does each of the two wrong choices (plain listener, `BEFORE_COMMIT`) allow?
3. Why does the `AFTER_COMMIT` listener need `REQUIRES_NEW`?
4. The behavior test `borrowingShowsUpInTheDashboardImmediately` kept passing after step 3.
   Which single change (annotation) would break it, and what would you tell the product owner?
5. Your projector recomputes a member's row from source tables instead of applying event
   deltas. Name one column of `member_activity_view` that makes the delta style genuinely hard,
   and explain why.
6. In step 5 you replayed from state. What can replay-from-an-event-log do that replay-from-state
   cannot? What does *this* write model coincidentally preserve that most don't?
7. A member registered after the backfill has no view row until their first borrow or the next
   rebuild. What event is missing from the write side, and what does that teach about projection
   completeness?
8. Name two signals that level 1 is no longer enough and a materialized read model (level 2) is
   worth its cost — and two signals that someone is about to apply CQRS as ceremony.

## Stretch goals

1. **Incremental projector.** Rewrite the projector to update `member_activity_view` using only
   event payload data — no reads from the source tables. Counters are easy; `distinct_authors`
   and `favorite_author` force you to add helper state to the read model. Measure what you
   traded for not re-running the recompute.
2. **JSONB variant.** Replace `member_open_loans_view` with a `current_loans JSONB` column on
   the main view row, projected with Jackson and `::jsonb`. One-row reads, but compare the
   projector and reader code honestly with the two-table version.
3. **Read-your-writes token.** Add a monotonic `version` to the view row (e.g. max loan id
   projected). Return the new loan id from `POST /loans`; make `GET .../activity` accept
   `?minVersion=` and return `202 Retry-After` until the view catches up. You have just built
   what Axon's subscription queries automate.
4. **Axon on paper.** Sketch (no dependency, pseudo-code) this exact slice as Axon 5 command
   handlers, an event processor, and a query handler. Count the concepts you'd inherit for free
   — and the ones you'd now be forced to adopt.

## Resources

- **"CQRS" — Martin Fowler** (martinfowler.com, 2011) — short, canonical, appropriately
  skeptical; read it alongside his "ReportingDatabase" note, which is level 2 by another name.
- **CQRS Documents — Greg Young** (free PDF) — the origin text; source of "CQRS is not a
  top-level architecture".
- **"CQRS facts and myths explained" + "CQRS is simpler than you think" — Oskar Dudycz**
  (event-driven.io) — the definitive pragmatic 2020s take; the spine of this lesson.
- **microservices.io/patterns/data/cqrs.html — Chris Richardson** — CQRS in a microservices
  context, with the trade-offs table; pairs with his Command-side/Query-side sagas material.
- **"The Release of Axon Framework 5.0" — AxonIQ blog (Nov 2025)** + AxonIQ Academy — the
  framework path, once you know what it automates.
- Repo research notes: `docs/research/event-driven.md` §3 (CQRS), §1 (events vs commands,
  eventual consistency), §4 (event sourcing — where project 09 picks up).
