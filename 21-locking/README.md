# 21 — Optimistic and Pessimistic Locking

> One-line promise: you can choose — and defend — a concurrency-control strategy for a
> contended row, implement it in JPA, and explain to a colleague why the fourth option is
> usually to take no lock at all.

## Why this matters (2026)

Project 20 established what a transaction does and does not guarantee. This one is about the
thing you reach for once you know: making two requests that want the same row take turns.

- **It is the most common correctness bug that ships.** Oversold inventory, double-charged
  refunds, two people assigned the same ticket — almost always the same read-modify-write over an
  entity, inside a perfectly good transaction, at the perfectly normal default isolation level.
- **JPA hands you two mechanisms and no judgement.** `@Version` and
  `@Lock(PESSIMISTIC_WRITE)` are each a one-line change with wildly different behaviour under
  load, and the framework will not tell you which one you want. Choosing wrongly is invisible in
  a code review and obvious at peak traffic.
- **Concurrency arrived without an invitation.** Project 14 put this stack on virtual threads;
  the request concurrency your service can reach is now bounded by the database, not the thread
  pool. Contention that used to be theoretical is now the thing on fire.
- **The distributed-systems lessons upstream depend on it.** Every idempotent consumer in
  `07-events-and-outbox` and every compensating step in `10-sagas` is, underneath, a contended
  row being claimed exactly once. Project 22 then asks the harder question: what do you do when
  the contenders are not even in the same database transaction?

## Core concepts

### Optimistic: assume it will be fine, verify at the end

A version column travels with the row. Every write carries the version it was based on:

```sql
UPDATE ticket_type SET available = ?, version = 4 WHERE id = ? AND version = 3
```

If that updates zero rows, somebody moved first. JPA counts the rows, sees zero, and throws.
Nothing was locked, nobody waited, and the conflict was detected rather than prevented.

- **Cost:** the loser's work is thrown away. Under heavy contention almost everybody loses,
  and throughput collapses precisely when you need it.
- **Right when:** conflicts are rare, work is cheap to redo, or the "transaction" spans human
  think-time and no database lock could have covered it anyway (step 8).

### Pessimistic: claim it first, decide afterwards

`SELECT … FOR UPDATE` takes an exclusive row lock that lives until the transaction ends. A second
transaction wanting the same row waits.

- **Cost:** waiting — and a waiting request is a request thread *and* a pooled connection you
  cannot use for anything else. An unbounded lock wait is an outage with extra steps.
- **Right when:** conflicts are common, the work between lock and commit is short and
  predictable, and you would rather everybody be a little slower than most people fail.

JPA's lock modes are worth knowing precisely:

| Mode | SQL on Postgres | Meaning |
|---|---|---|
| `OPTIMISTIC` | none | check the version at commit even for a read |
| `OPTIMISTIC_FORCE_INCREMENT` | none | bump the version even though this row did not change — the way you version an *aggregate* through its root |
| `PESSIMISTIC_READ` | `FOR SHARE` | others may read, nobody may write |
| `PESSIMISTIC_WRITE` | `FOR UPDATE` | nobody else may have it |
| `PESSIMISTIC_FORCE_INCREMENT` | `FOR UPDATE` + version bump | lock now, and make optimistic readers elsewhere fail too |

### Neither: put the rule in the statement

```sql
UPDATE ticket_type SET available = available - 1 WHERE id = ? AND available >= 1
```

One statement. The database evaluates the rule and applies the change atomically; the update
count tells you whether it happened. No read, no version, no lock held across your code, no
retry, and nothing to get wrong at 3am. This is the right answer far more often than the two
famous ones — and step 7 is where you find out why it is not the answer to *everything*.

### Deadlock is an ordering bug, not a database problem

Two transactions, two rows, opposite order: each holds what the other needs. Postgres waits
`deadlock_timeout` (1 s), detects the cycle, and kills one — SQLSTATE `40P01`. There is no
setting that fixes this and no clever retry that makes it right. The fix is to always acquire
locks in a deterministic order, usually by sorting on the primary key.

### The lock lives as long as the transaction

You cannot release a row lock early, and you cannot hold one across a request boundary. Both
facts point the same way: keep the transaction short, and never park a lock behind something slow
(project 20, step 8). It is also why `spring.jpa.open-in-view` is set to `false` in this project —
leaving the persistence context open for the whole HTTP response, view rendering included, is the
opposite of the discipline this lesson is teaching.

## The project

A conference ticket shop, seeded before every test:

| id | ticket type | price | available |
|---|---|---|---|
| 1 | conference | 50 000 | 10 |
| 2 | workshop | 20 000 | 10 |

plus twenty free seats in one section.

`schema.sql` already carries two things you will need opinions about: a `version` column that
nothing is using yet, and `CHECK (available >= 0)`. Step 1 oversells the conference **without
ever violating that constraint** — the last writer stores `9`, which is a perfectly legal number.
A constraint can only reject a value it can see, and the wrong value looks fine.

What is given:

- `catalog/` — the `TicketType` entity and its Spring Data repository, with the locking
  annotations left off and two query methods waiting for you.
- `booking/BookingService` — booking as load-entity, mutate, let the flush write it back. The
  ordinary shape, and the vulnerable one.
- `booking/OptimisticRetry` — a stub for step 3.
- `seating/` — seat allocation, in plain SQL, because `FOR UPDATE SKIP LOCKED` is a
  statement-level decision and reads better as one.
- `api/TicketTypeController` — the HTTP edge, for step 8.
- `support/Interleaving` — the same test-controlled pause point as project 20. No-op in
  production, and a teaching device rather than a pattern.

The test harness (`support/DbSession`, `support/Concurrently`) is carried over from project 20; if
you skipped that project, read its README section on the harness first.

```bash
mvn test
```

Docker required. `HarnessTest` is green on checkout — it also proves Hibernate validates its
mappings against `schema.sql`, so a mapping mistake fails at startup rather than in step 4.

To watch the SQL — the `FOR UPDATE` clauses, the `AND version = ?` predicates, the flush ordering —
uncomment `spring.jpa.show-sql` in `application.properties`. Do it at least once during step 2.

## Guided steps

### Step 1 — Sell the conference twice

**Goal:** watch JPA lose an update, with transactions, without an error.

Enable `Checkpoint1OversellTest`. Ten buyers, ten tickets, one seat's worth of arithmetic done ten
times from the same starting value. Everybody is told they have a ticket, ten `booking` rows
exist, and `available` says **nine**.

Note what did *not* save you: the transaction (both were valid), the entity (it checked
`available` correctly, against a stale value), and the `CHECK` constraint (never violated).

**Done when** all three tests are green. They pin the bug; step 2 breaks them on purpose, at
which point re-disable the class and leave it in the repo as the exhibit — the same convention
`07-events-and-outbox` uses for its dual-write tests.

### Step 2 — `@Version`

**Goal:** turn silent corruption into a loud, correct failure — with one annotation.

Enable `Checkpoint2OptimisticLockingTest` and add `@Version` to `TicketType.version`.

<details><summary>Hint</summary>

`jakarta.persistence.Version`. Nothing else changes: the column already exists, and Hibernate
takes over maintaining it. Turn on `show-sql` and look at the `UPDATE` — the `WHERE` clause grew.

</details>

**Done when** the loser of a two-buyer race gets `ObjectOptimisticLockingFailureException`, the
counter matches the bookings, and ten simultaneous buyers can no longer oversell.

Two details worth stopping on. First, the exception is raised at **flush**, which Spring performs
as part of the commit — after your service method has returned. Same lesson as project 20, step 4:
you cannot catch it where the mistake looks like it happened. Second, look at the message:
`Unexpected row count (expected row count 1 but was 0)`. That is the whole mechanism, stated
plainly.

### Step 3 — A conflict is not an answer to give a customer

**Goal:** retry the loser, and understand what makes retrying safe.

Enable `Checkpoint3RetryTest` and implement `OptimisticRetry.execute`.

<details><summary>Hint</summary>

Catch `org.springframework.dao.OptimisticLockingFailureException` (the framework-neutral parent of
the JPA-specific one), loop to `MAX_ATTEMPTS`, call `retried()` each time, rethrow the last
failure if you run out. It must wrap the whole transactional call — for the reason you just met in
step 2.

</details>

**Done when** all ten entitled buyers get a ticket, none of them sees an error, and
`retryCount()` proves retries happened.

Then look at the second test, which needs nothing from you: the eleventh buyer must still be told
"sold out". A version conflict is worth retrying; a business rule is not. A retry loop that cannot
tell those apart will hammer the database forever over a ticket that does not exist.

### Step 4 — Pessimistic locking, and what waiting costs

**Goal:** make callers queue instead of fail, and see the bill.

Enable `Checkpoint4PessimisticLockingTest`. Add the locking annotations to
`TicketTypeRepository.findByIdForUpdate` and `findByIdForUpdateNoWait`.

<details><summary>Hint</summary>

`@Lock(LockModeType.PESSIMISTIC_WRITE)` on both. For the second, add
`@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))` — on Postgres,
Hibernate turns a lock timeout of zero into `FOR UPDATE NOWAIT`. (Postgres has no
`FOR UPDATE WAIT n` syntax, so an arbitrary timeout value cannot be expressed this way; the
session-level `lock_timeout` is the tool for that.)

</details>

**Done when** all three are green. The middle test is the pleasant one — ten buyers, ten tickets,
zero failures, everyone served. The first and third are the invoice: the second buyer is
*parked*, holding a request thread and a pooled connection, and the only reason that test can
assert anything is that the wait is unbounded. `NOWAIT` converts an unbounded wait into an
immediate, bounded failure (SQLSTATE `55P03`), which is very often the trade you want.

### Step 5 — Deadlock, and the fix that is not a retry

**Goal:** cause a deadlock deliberately, then make it structurally impossible.

Enable `Checkpoint5DeadlockTest`. Two customers buy the same two ticket types as a bundle, in
opposite orders. Each takes one lock and asks for the other. After a second, Postgres detects the
cycle and kills one of them: `CannotAcquireLockException`, root cause `deadlock detected`.

Implement `bookBundleSafely`.

<details><summary>Hint</summary>

Sort the two ids and lock in that order. Any total order will do as long as *everybody* uses the
same one — which is why sorting on the primary key is the convention: it needs no coordination
between the people writing the two code paths.

</details>

**Done when** the reordered version has both customers succeed. Note that the second customer
still *waits* — waiting is not deadlocking, and eliminating the wait was never the goal.

### Step 6 — `SKIP LOCKED`: the row nobody else has

**Goal:** claim work from a queue without queueing behind other claimants.

Enable `Checkpoint6SkipLockedTest` and implement `SeatRepository.nextFreeSeatSkippingLocked`.

<details><summary>Hint</summary>

`FOR UPDATE SKIP LOCKED`. One clause.

</details>

The first test is the diagnosis. With plain `FOR UPDATE`, a worker that finds row 1 locked
*waits for it*, even though nineteen seats are free. It does eventually get the next one —
Postgres re-evaluates the row after the wait and moves on — so this is not a correctness bug. It
is worse than that in practice: a pool of workers degenerates into one worker and a queue of
spectators, and nothing in your metrics says "lock wait".

**Done when** the skip-locked version returns seat 2 *while the holder is still holding* seat 1,
and four workers claim four different seats simultaneously.

This is the general pattern behind every database-backed work queue: the row lock is the lease,
the commit releases it, and a crashed worker's claim is released by its dying transaction. Worth
remembering when someone proposes adding a queue broker for a job table that has 200 rows a day.

### Step 7 — The version, the lock and the retry you did not need

**Goal:** solve the whole problem with one statement, and know exactly when you can't.

Enable `Checkpoint7NoLockAtAllTest` and implement `TicketTypeRepository.reserveIfAvailable` —
replace the `default` method with a real query method.

<details><summary>Hint</summary>

`@Modifying` plus a native `@Query`:
`UPDATE ticket_type SET available = available - :quantity WHERE id = :id AND available >= :quantity`.
Return the update count. `clearAutomatically`/`flushAutomatically` keep the persistence context
honest about a row you changed behind its back.

</details>

**Done when** twenty buyers chase ten tickets, exactly ten succeed, ten get a truthful "sold
out", the version column is never touched, and `retryCount()` is **zero** — no conflict, no
wasted work.

Now the honest part, which is the reason the previous six steps exist. This works because the
rule fits in a `WHERE` clause over the row being updated. It does not survive a rule that needs a
call to another service, a decision a human makes, or an invariant spanning rows — like project
20's linked overdraft, where the rule is a `SUM` across two accounts and no single-row `UPDATE`
can express it.

The third test is the floor under everything: the `CHECK` constraint rejects a negative balance
outright. It could not prevent step 1's bug, and it is still the only guarantee no application
bug, no migration script and no careless `psql` session can bypass. Put both in.

### Step 8 — Optimistic locking at the HTTP edge

**Goal:** protect a record from two humans, where no database lock can reach.

Enable `Checkpoint8HttpConcurrencyTest`. Two administrators open the price form at 09:00 and save
at 09:05 and 09:06. Their transactions never overlap; the thing being raced is think-time. As
delivered, the second save silently overwrites the first — the step-1 bug, minutes wide instead of
milliseconds.

`GET` already returns the version as an `ETag`. Make `PUT` honour `If-Match`.

<details><summary>Hint</summary>

Compare the header against `etagFor(type)` and throw
`ResponseStatusException(HttpStatus.PRECONDITION_FAILED)` on a mismatch. Spring also has
`ServletWebRequest.checkNotModified(etag)`, which implements the RFC 9110 rules for you — worth
reading either way, since it handles `If-None-Match` on reads with the same version value.

</details>

**Done when** a stale `If-Match` gets `412`, a current one gets `204` with the next ETag, and the
rejected administrator can re-fetch and retry.

This is the same idea as `@Version`, carried to the only place that can act on it: the client. It
is also the only correct answer for edits that span think-time, and it has been in HTTP since 1999.

## Self-check

1. Two requests read the same row and both write. Describe what happens with (a) nothing, (b)
   `@Version`, (c) `PESSIMISTIC_WRITE`, (d) a conditional `UPDATE`.
2. Why does throughput *collapse* under optimistic locking at high contention, when it merely
   degrades under pessimistic locking?
3. Where is `ObjectOptimisticLockingFailureException` actually thrown from, and why does that make
   a `try/catch` inside the service method useless?
4. What is `PESSIMISTIC_FORCE_INCREMENT` for, and how does it relate to an aggregate root
   (`05-ddd`)?
5. Your retry loop starts hammering the database in production. Name two causes and one thing you
   should have logged.
6. Why is a deadlock an application design bug rather than a database tuning problem?
7. A colleague proposes `FOR UPDATE` on a job-queue table polled by twelve workers. What do you
   ask them, and what do you suggest?
8. Give a rule that a conditional `UPDATE` cannot enforce, and say which mechanism you would use
   instead.

## Stretch goals

1. **Measure the crossover.** Parameterise the booking test over contention (2, 5, 10, 50
   simultaneous buyers) and strategy (version + retry, `FOR UPDATE`, conditional `UPDATE`), and
   plot successful bookings per second. Somewhere between two buyers and fifty, optimistic loses
   to pessimistic. Find it, and note how far the conditional `UPDATE` is from both.
2. **Add jittered backoff and a retry budget.** Then break it deliberately: make the retry loop
   catch every exception, and watch a `SoldOutException` become twenty pointless round trips.
3. **`OPTIMISTIC_FORCE_INCREMENT` for a real aggregate.** Make booking a seat bump the *event's*
   version, so that two people editing different seats of the same event still conflict. This is
   the aggregate-as-consistency-boundary idea from `05-ddd` expressed as a lock mode — and a good
   argument to have with yourself about whether you want it.
4. **Turn the seat table into a proper worker pool.** N workers, `SKIP LOCKED`, a `held_until`
   lease, and a test that kills a worker mid-claim and proves the seat comes back. That is the
   bridge into project 22 — where the workers are in different JVMs and the lock has to survive
   one of them disappearing.

## Resources

- **Vlad Mihalcea — [*High-Performance Java Persistence*](https://vladmihalcea.com/books/high-performance-java-persistence/)**
  and his [blog](https://vladmihalcea.com/) — the reference for JPA locking specifically. His
  articles on optimistic locking, `@Version` semantics and `PESSIMISTIC_FORCE_INCREMENT` are the
  clearest treatment anywhere, with the generated SQL shown throughout.
- **[PostgreSQL — Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)**
  — §13.3, including row-level lock modes, `NOWAIT`/`SKIP LOCKED`, and the deadlock section that
  says outright that the fix is a consistent ordering.
- **Martin Kleppmann — *Designing Data-Intensive Applications*, 2nd ed. (2026)**, chapter 7 — the
  same chapter behind project 20; the "preventing lost updates" section is exactly this project's
  argument, told from the database's side.
- **[Jakarta Persistence 3.2 specification](https://jakarta.ee/specifications/persistence/)**,
  §3.4 "Locking and Concurrency" — short, and the definitive word on what each `LockModeType`
  promises (as opposed to what a given provider happens to do).
- **[RFC 9110 §13](https://www.rfc-editor.org/rfc/rfc9110#section-13)** — conditional requests.
  `If-Match`, `412`, and `428 Precondition Required` ([RFC 6585](https://www.rfc-editor.org/rfc/rfc6585))
  for the API that refuses to accept a blind write at all.
- **[2ndQuadrant / EDB — "What is SKIP LOCKED for?"](https://www.enterprisedb.com/blog/what-skip-locked-postgresql-95)**
  — the canonical write-up of the queue pattern, by the people who added the feature.
- **Jim Gray & Andreas Reuter — *Transaction Processing: Concepts and Techniques*** — if you ever
  want the bottom of this particular well. Chapter 7 on isolation and locking has not been
  superseded, only re-explained.

---

**Build notes (verified August 2026, this machine).** Spring Boot 4.1.1 / Java 25,
`spring-boot-starter-data-jpa` + `-webmvc` (+ `-webmvc-test`), Testcontainers 2.0.5
(`postgres:16-alpine`), Hibernate as shipped by Boot 4.1.1. Pristine `mvn -B test`: **25 tests, 2
running and green, 23 checkpoints `@Disabled`**. With steps 2–8 solved: **25 tests, 23 green and
Checkpoint 1's three deliberately red** — that class pins the bug step 2 removes, and is meant to
be re-disabled once you have moved past it.

Findings verified here rather than assumed:

1. **`@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))` combined
   with `@Lock(PESSIMISTIC_WRITE)` really does produce `FOR UPDATE NOWAIT`** on Postgres with this
   Hibernate version: the caller fails immediately with `CannotAcquireLockException`, root cause
   `PSQLException: ERROR: could not obtain lock on row in relation "ticket_type"` (SQLSTATE
   `55P03`). Non-zero timeouts have no Postgres syntax to map onto — use session `lock_timeout`.
2. **`SELECT … WHERE … ORDER BY id LIMIT 1 FOR UPDATE` does not return an empty result** after
   waiting for a row that stopped qualifying — Postgres re-evaluates and moves on to the next
   matching row, so the caller gets seat 2. The plain-`FOR UPDATE` problem here is latency and
   convoying, not lost work. (This is worth knowing precisely because the opposite is widely
   repeated.)
3. **`Propagation.NESTED` works on `JpaTransactionManager`** under Boot 4.1.1 — savepoints are
   available, contrary to the common "JPA cannot do NESTED" advice. Project 20's build notes carry
   the same correction.
4. **A deadlock reproduces deterministically** if one participant simply holds its first lock for
   ~500 ms; no rendezvous between the two threads is needed. `Checkpoint5DeadlockTest` uses that,
   and it costs the suite about a second.
5. **`spring.jpa.hibernate.ddl-auto=validate` against a hand-written `schema.sql`** works cleanly
   with `spring.sql.init.mode=always` and `defer-datasource-initialization=false` — the script runs
   before the EntityManagerFactory is built, so a mapping error fails the context, which is where
   you want it.
