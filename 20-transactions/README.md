# 20 — Transactions: What ACID Actually Guarantees

> One-line promise: you can predict — and prove, on a real Postgres — what two concurrent
> transactions will do to your data, and you know which of Spring's `@Transactional` defaults
> will quietly do the opposite of what you meant.

## Why this matters (2026)

Every project from 05 onward in this training sprinkles `@Transactional` around and moves on.
That is exactly how the annotation is used in the wild: as a decoration that means "this is
important, please don't break". It is not what it means.

Three things make this worth a project of its own now:

- **The defaults are weaker than everyone assumes.** Postgres runs at READ COMMITTED, and so do
  MySQL/InnoDB deployments in practice, Aurora, RDS, Cloud SQL, and every managed Postgres you
  will be handed. Nothing you own defaults to SERIALIZABLE. Application code written as if it did
  is the single most common source of "the numbers are wrong and nobody knows why" tickets.
- **Concurrency went up by an order of magnitude and nobody re-checked the assumptions.**
  Project 14 moved this stack to virtual threads: the same service that ran eight concurrent
  requests on a platform-thread pool now runs eight hundred. Races that were theoretically
  possible and practically invisible in 2019 are now reproducible in production before lunch.
  `14-virtual-threads/README.md` step 5 puts it plainly — the bottleneck moves to the database,
  and so does the correctness problem.
- **The event-driven projects (07–11) all assume you already have this.** The outbox pattern is
  "write the event in the *same local transaction*". Sagas exist because you *cannot* have a
  distributed one. Both lessons are hollow if "local transaction" is a black box.

The research reports behind this curriculum (`docs/research/event-driven.md` §5–6) treat the local
ACID transaction as the known quantity that distributed patterns are built on top of. This project
is that known quantity, made explicit. The canonical reference is Kleppmann's *Designing
Data-Intensive Applications*, whose 2nd edition landed in March 2026 — chapter 7 is the map this
lesson follows.

## Core concepts

### Isolation is a dial, not a checkbox

Atomicity, consistency and durability are broadly what the marketing says. **I** is different:
it is a level you choose, per transaction, trading correctness guarantees against throughput. The
guarantees are defined by which *anomalies* they forbid.

| Anomaly | What it looks like | READ COMMITTED | REPEATABLE READ (snapshot) | SERIALIZABLE (SSI) |
|---|---|---|---|---|
| **Dirty read** | You read another transaction's uncommitted write | never on PG | never | never |
| **Lost update** | Two read-modify-writes; one silently overwrites the other | **happens** | aborts the second writer (40001) | aborts |
| **Read skew** | Two queries in one transaction see two different worlds | **happens** | never | never |
| **Phantom** | A row matching your `WHERE` appears mid-transaction | **happens** | never | never |
| **Write skew** | Both check a rule against the same snapshot, both write *different* rows, rule broken | **happens** | **happens** | aborts |

Every "happens" in that table is a test in this project, and you will watch each one.

### Postgres is not the SQL standard, in your favour and against it

- There is **no dirty read**, at any level. MVCC does not have one to offer.
- **REPEATABLE READ is snapshot isolation.** The standard's REPEATABLE READ permits phantoms;
  Postgres's does not. The snapshot is taken at the *first statement of the transaction*, not at
  `BEGIN`.
- At REPEATABLE READ, a write to a row that changed under your snapshot does not silently win —
  it **fails with SQLSTATE 40001**. That is a third fix for the lost update, and the reason
  raising the isolation level is never free: somebody has to catch that and retry.
- **SERIALIZABLE is SSI** — Serializable Snapshot Isolation. It takes no extra locks and blocks
  nothing. It watches for dependency cycles and, when it finds one, aborts a transaction **at
  commit time**, again with 40001. You get true serializability at the cost of a retry loop you
  have to write yourself.
- **Readers never block writers and writers never block readers.** Writers block writers, on the
  rows they touch.

### Two families of fix, and they behave very differently under load

Everything in this lesson is one of:

- **Prevent** — take a lock and make the other transaction wait: `SELECT … FOR UPDATE`, or an
  atomic `UPDATE … SET x = x + 1` which locks the row for the instant it needs. Cost: waiting,
  and the risk of deadlock. Behaviour under contention: throughput degrades smoothly.
- **Detect** — let both run and abort one that turns out to be wrong: REPEATABLE READ write
  conflicts, SERIALIZABLE, and (in project 21) optimistic version columns. Cost: retries, and the
  requirement that the work be *safe to re-run*. Behaviour under contention: throughput collapses
  when conflicts are common, because most work is thrown away.

Neither is the right answer. Which one fits depends on how often two requests actually collide,
which is a measurement, not an opinion.

### Spring's part of the story is a proxy, not a database feature

This training has been leaning on proxies since project 03 without ever saying so. Nine projects
use `@Transactional`, `@Scheduled`, `@Retryable` or `@ApplicationModuleListener`, and
the closest any of them comes to an explanation is one line in a collapsed hint in
`04-hexagonal-architecture`. Here is the missing paragraph; it is worth the two minutes because
it explains all of them at once.

**What a proxy is, concretely.** When Spring finds an annotation it implements by interception,
it does not put your object in the container. It generates a *wrapper* — by default a CGLIB
subclass of your class, or an implementation of your interface if you ask for JDK proxies — and
registers that instead. Every injected reference points at the wrapper, not at your object. The
wrapper overrides your methods, runs the machinery, and delegates:

```java
class TransferService$$SpringCGLIB extends TransferService {   // generated, not written
    @Override
    public void deposit(long id, long amount) {
        var tx = txManager.getTransaction(...);                // BEGIN
        try {
            super.deposit(id, amount);                         // your code
            txManager.commit(tx);                              // COMMIT — after your method returns
        } catch (RuntimeException e) {
            txManager.rollback(tx);
            throw e;
        }
    }
}
```

Three consequences fall straight out of that shape, and they are the same three for every
interception-based annotation in Spring:

1. **Only calls that arrive from outside are intercepted.** Inside your method, `this` is the real
   object, not the wrapper — so `this.otherAnnotatedMethod()` runs with no machinery at all.
2. **The machinery runs outside your method body**, so what it throws cannot be caught inside it.
3. **It only applies to Spring-managed beans.** `new TransferService(...)` in a test is a bare
   object with inert annotations — which is exactly the point
   `04-hexagonal-architecture` was making.

A fourth falls out of the default being a subclass: a `final` class, or a `final` method, cannot
be overridden, so it cannot be advised. Spring Boot will usually tell you; a `final` method on a
non-final class it will not.

Swap `@Transactional` for `@Cacheable` and the wrapper checks a cache; for `@Async` it submits to
an executor; for `@PreAuthorize` it checks an authority. Same shape, same three limitations. Step 6
walks into all three deliberately.

With that in hand, the transaction-specific defaults:

```java
@Transactional                       // isolation: default (= whatever the DB says)
public void deposit(long id, long amount) {   // propagation: REQUIRED
    ...                              // rollback: on RuntimeException and Error only
}                                    // ← BEGIN was here, COMMIT is here
```

- The transaction begins when a call **enters through the proxy** and commits when it leaves. A
  call from a neighbouring method in the same class never touches the proxy.
- The commit happens *after* your method returns, so any exception the commit itself raises — a
  constraint violation on flush, a serialisation failure — is thrown from a line you did not
  write and cannot catch inside the method.
- Rollback is on unchecked exceptions. A checked exception commits.
- Propagation decides what a nested call does: join the caller (`REQUIRED`), open a genuinely
  separate transaction on a second connection (`REQUIRES_NEW`), or take a savepoint inside the
  caller's (`NESTED`).

### A transaction is a connection you are holding

An open transaction owns a pooled connection for its entire life. Pool size is therefore a hard
cap on *concurrent transactions*, and transaction **duration** is what sets your throughput
ceiling. This is where step 8 goes, and where this lesson meets `19-reliability-slo`.

## The project

A small retail bank. Three accounts, seeded before every test:

| id | customer | kind | balance |
|---|---|---|---|
| 1 | ada | CHECKING | 5 000 |
| 2 | ada | SAVINGS | 5 000 |
| 3 | linus | CHECKING | 10 000 |

Ada's two accounts share **linked overdraft protection**: either account may go negative, as long
as her accounts *together* stay at or above zero. That rule spans two rows, which is precisely
the kind of invariant that no isolation level below SERIALIZABLE will keep for you.

What is given:

- `account/` — `Account`, and an `AccountRepository` written in plain SQL. No JPA anywhere in this
  project: a first-level cache would hide half of what you are trying to see. (JPA's locking
  support is project 21's subject.)
- `transfer/TransferService` — deposits and transfers as read-modify-write, the shape of most
  business code and the source of most lost updates.
- `report/ReportingService` — a two-query read that is internally inconsistent.
- `overdraft/OverdraftService` — check-a-rule-then-write-another-row: the write-skew subject.
- `tx/SerializationRetry` — a stub you implement in step 5.
- `traps/`, `audit/`, `pool/` — the subjects of steps 6, 7 and 8.
- `support/Interleaving` — a test-controlled pause point in main code, for the same reason
  `07-events-and-outbox` ships a chaos monkey. A race that only shows up "sometimes, under load"
  teaches nothing; armed from a test, this makes the anomaly happen on every run. It is a no-op in
  production, and it is a teaching device, not a pattern to copy.

The test harness is the other half of the lesson:

- `support/DbSession` — one database session you drive by hand, statement by statement, on its own
  thread. Two of them let you write an interleaving out as a script instead of hoping a thread pool
  produces it. A statement that blocks can be started, *asserted to be blocked*, and collected
  later. Its exceptions carry the SQLSTATE, because in this lesson the five-character code is the
  diagnosis.
- `support/Concurrently` — run the same work from N virtual threads that all start at once, and
  collect the failures, which here are results rather than problems.

Run it:

```bash
mvn test
```

Docker must be running (Testcontainers starts `postgres:16-alpine`). `HarnessTest` is green on
checkout and proves the environment; everything else is a `@Disabled` checkpoint waiting for you.

To watch transaction boundaries as they happen, uncomment the two logging lines in
`src/main/resources/application.properties` — invaluable in steps 6 and 7, unbearable elsewhere.

## Guided steps

### Step 1 — Watch a committed transaction lose money

**Goal:** see the lost update, at the default isolation level, with nothing misconfigured.

Enable `Checkpoint1LostUpdateTest`. All three tests pass against the code as delivered — they pin
the bug rather than fix it. Read them in order:

`twoConcurrentDeposits_oneIsSilentlyLost` runs two deposits of 1 000 into an account holding
5 000, and asserts the result is **6 000**. Nothing throws. Nothing is logged. Both transactions
committed successfully and the bank is 1 000 short.

`theSameThingInSql_theSecondWriterBlocksAndStillLoses` is the same thing with the covers off, and
it is the more important of the two: T2 *does* block on T1's row lock. Blocking is not the
problem. T2 wakes up and writes a number it computed from a read that is now history — the lock
made it wait, it never made it re-read.

**Done when** all three are green and you can say, out loud, why a row lock did not help.

### Step 2 — Three ways to stop losing it

**Goal:** implement the two application-side fixes and meet the third for free.

Enable `Checkpoint2FixingLostUpdateTest`. Implement, in `AccountRepository`:

- `addToBalance(id, delta)` — one statement, the database does the arithmetic
- `balanceForUpdate(id)` — a read that also takes the row lock

and wire them into `TransferService.depositAtomically` and `depositWithRowLock`.

<details><summary>Hint</summary>

`UPDATE account SET balance = balance + :delta WHERE id = :id` and
`SELECT balance FROM account WHERE id = :id FOR UPDATE`. In `depositWithRowLock` the lock must be
taken *before* the value is read into your heap, which is the whole point — that is why it is one
statement and not a `SELECT` followed by a `SELECT … FOR UPDATE`.

</details>

The third test needs no code from you: at REPEATABLE READ, Postgres refuses the second writer with
SQLSTATE 40001 rather than losing its update. Raising isolation *is* a fix — it just hands you a
new obligation, which is step 5.

**Done when** sixteen concurrent depositors all land, by both routes, and you can say what each
fix costs: the atomic `UPDATE` cannot express a decision made in Java, and `FOR UPDATE` serialises
every caller through one row.

### Step 3 — Read skew: being wrong without writing anything

**Goal:** understand that a read-only transaction has an isolation problem too.

Enable `Checkpoint3ReadSkewTest`. The first two tests are raw SQL and are there to be read: one
shows a reporting transaction seeing Ada with 15 000 when she has 10 000 — every row it read was
committed — and the second shows REPEATABLE READ freezing a snapshot at the first statement, so
that neither a balance change nor a brand-new row can appear underneath it.

The third test is red. Fix `ReportingService` so that a transfer between Ada's own accounts
cannot change what Ada appears to own.

<details><summary>Hint</summary>

`@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. Note what `readOnly`
did *not* do for you: it is a hint about writes, not a statement about consistency.

</details>

**Done when** the report totals 10 000 no matter when the transfer commits. Then ask yourself
which of your own multi-query endpoints has been quietly doing this for years.

### Step 4 — Write skew: the anomaly snapshot isolation cannot see

**Goal:** meet the invariant violation that survives REPEATABLE READ, and the level that stops it.

Enable `Checkpoint4WriteSkewTest`. Two withdrawals of 6 000 from a customer holding 10 000
combined. Each checks the linked-overdraft rule against a snapshot in which it holds, and each
writes a *different row*, so there is no write-write conflict for the database to serialise. Both
commit. Ada is 2 000 overdrawn and both transactions were individually correct.

The second test is the same script at SERIALIZABLE: nothing blocks, nothing complains, and then
the second `COMMIT` fails with 40001 — Postgres found the read-write dependency cycle and refused
to be part of it.

The third test is red. Change `OverdraftService` so the invariant survives.

<details><summary>Hint</summary>

`@Transactional(isolation = Isolation.SERIALIZABLE)`. The loser's exception reaches you as
Spring's `CannotAcquireLockException` — that is what SQLSTATE 40001 translates to.

</details>

**Done when** exactly one of two concurrent withdrawals succeeds and the combined balance never
goes below zero. Note where the failure was raised: on **commit**, after `withdraw` had already
returned. A `try/catch` inside that method could never have seen it.

### Step 5 — SERIALIZABLE is half a solution; the retry is the other half

**Goal:** make legitimate work succeed under an isolation level that aborts transactions.

Enable `Checkpoint5SerializationRetryTest`. Eight callers each withdraw 500 from a combined 10 000.
Every one of them is affordable, so every one of them must eventually go through — but at
SERIALIZABLE, some will be aborted along the way.

Implement `SerializationRetry.execute`. Two constraints the tests enforce:

- it must wrap the **whole transactional call**, because the failure is raised by the commit
- retrying must re-run the reads, not just the write — this is the reason a retry is *safe*

<details><summary>Hint</summary>

Catch `org.springframework.dao.ConcurrencyFailureException` (the parent of
`CannotAcquireLockException`), loop up to `MAX_ATTEMPTS`, call `retried()` each time round, and
rethrow the last failure if you run out of attempts. Backoff is a stretch goal — see below for
why jitter matters here.

</details>

**Done when** all eight withdrawals succeed, the combined balance is exactly 6 000 (no withdrawal
applied twice by a retry), and `retryCount()` proves retries actually happened.

### Step 6 — Three ways `@Transactional` does nothing

**Goal:** internalise that the annotation is a proxy, by breaking it three times.

Enable `Checkpoint6ProxyTrapsTest`. Each trap comes as a pair: an `asWritten` test that pins
today's behaviour and stays green forever as the exhibit, and a `repaired` test that is red until
you write the fixed variant next to it.

1. **Self-invocation.** `selfInvoked` calls an annotated method through `this`. No transaction is
   started at all; the failure leaves the deposit behind. Implement
   `selfInvokedButTransactional` so the call reaches the proxy.
2. **Checked exceptions commit.** `depositThenFailChecked` throws a checked exception out of a
   transactional method and the transaction commits on the way past. Implement
   `depositThenFailCheckedWithRollback`.
3. **Rollback-only poisoning.** `depositAndSwallowInnerFailure` catches the inner failure and
   carries on — but the inner call joined this transaction and marked it rollback-only, so the
   commit throws `UnexpectedRollbackException` and the unrelated deposit dies with it. Implement
   `depositAndSurviveInnerFailure` and `InnerService.failsInItsOwnTransaction`.

<details><summary>Hints</summary>

1. Inject `ObjectProvider<TrapsService>` and call `self.getObject().depositThenFail(…)` — or move
   the method to a collaborating bean, which is usually the better design answer.
2. `@Transactional(rollbackFor = PaperworkMissingException.class)`.
3. `@Transactional(propagation = Propagation.REQUIRES_NEW)` on the inner method. A failure can only
   be "handled" by a caller if it did not happen inside the caller's transaction.

</details>

**Done when** all six are green. Trap 3 is the one to remember: the exception comes from the
commit, names no line of your code, and the stack trace points at the framework.

### Step 7 — Propagation, for the two requirements that need it

**Goal:** use `REQUIRES_NEW` and `NESTED` for what they are actually for.

Enable `Checkpoint7PropagationTest`.

- **"We must have a record it was attempted, even if it failed."** As delivered, `AuditLog.record`
  joins the caller's transaction, so a rolled-back transfer takes its own audit trail with it.
  One attribute fixes it.
- **"One bad recipient must not cost us the batch."** `payoutBatch` catches per-item failures, but
  the failing `payout` poisons the shared transaction — trap 3 again, in production clothing. Give
  each payout a savepoint it can be rolled back to.

<details><summary>Hint</summary>

`Propagation.REQUIRES_NEW` for the audit log: a genuinely separate transaction on a second
connection, which is also why it is not free — it doubles connection demand for the duration.
`Propagation.NESTED` for the payout: a JDBC savepoint inside the same transaction, so the debit for
the failed recipient is undone and the two good payouts survive.

</details>

**Done when** all three are green. Then note the asymmetry that trips people up: `REQUIRES_NEW`
commits independently — including when the parent later rolls back — so it is right for audit
trails and wrong for anything that must not exist if the business operation did not happen.

### Step 8 — The boundary is a capacity decision

**Goal:** feel the cost of holding a transaction across something slow.

Enable `Checkpoint8TransactionBoundaryTest`. It shrinks the pool to four connections and sends
thirty-two callers at a deposit that runs a 400 ms fraud check *inside* the transaction.

The first test pins the failure and, more importantly, the *error message* you would be paged with:

```
org.springframework.transaction.CannotCreateTransactionException
  └─ java.sql.SQLTransientConnectionException:
     HikariPool-1 - Connection is not available, request timed out after 2011ms
     (total=4, active=4, idle=0, waiting=5)
```

Nothing in that message mentions the fraud check, and nothing in it is the database's fault. This
is the shape of a whole genre of incident: a slow dependency shows up as connection-pool
exhaustion three layers away.

Implement `EnrichmentService.depositWithFraudCheckOutsideTransaction` — score first, then hand the
result to `DepositWriter.applyDeposit`. (It uses `addToBalance` from step 2, so the arithmetic
stays correct under thirty-two concurrent callers. And it lives in its own bean because of step 6.)

**Done when** all thirty-two callers get through on the same four connections, and you can do the
arithmetic in your head: a pool of *n* connections and transactions lasting *d* gives you at most
*n/d* transactions per second, and *d* is the number you control.

## Self-check

1. Your service reads a row, computes a new value, and writes it back inside `@Transactional`.
   Which isolation levels protect you, and what does each cost?
2. Postgres's REPEATABLE READ forbids phantoms, which the SQL standard permits. Why is that not
   simply "better", and what does it still fail to prevent?
3. What exactly does SERIALIZABLE do that REPEATABLE READ does not, and when is the price paid?
4. A colleague adds `@Transactional(isolation = SERIALIZABLE)` to fix a bug found in production.
   What must they add at the same time, and what happens under load if they don't?
5. Why can a `try/catch` inside a `@Transactional` method never handle a serialisation failure?
6. Name the three ways `@Transactional` can be present and have no effect (or the wrong effect) —
   then name two other Spring annotations that fail in exactly the same three ways.
7. When is `REQUIRES_NEW` right, and when is it a way of committing something that should not exist?
8. A pool of 10 connections, transactions lasting 500 ms. What is your ceiling in requests per
   second — and which of those two numbers is easier to change?

## Stretch goals

1. **Add jittered backoff to `SerializationRetry`** and measure it. Retry immediately and eight
   aborted transactions collide again on the same schedule; that is a small thundering herd.
   Compare throughput and total retries for immediate, fixed-delay, and exponential-with-jitter.
2. **Find the throughput cliff.** Parameterise the withdrawal test over isolation level and
   concurrency, and plot successful withdrawals per second for READ COMMITTED + `FOR UPDATE`
   versus SERIALIZABLE + retry. Somewhere there is a contention level where detect loses to
   prevent. Knowing roughly where it is for your workload is the whole engineering judgement.
3. **Statement timeouts.** A lock wait is unbounded by default: a transaction can hold a row while
   an operator stares at it. Set `lock_timeout` and `statement_timeout` (see
   `19-reliability-slo` step 6 for why `spring.jdbc.template.query-timeout` will not do it) and
   write a test that proves a blocked writer gives up instead of piling up.
4. **Prove it about your own database.** If work runs on MySQL/InnoDB, port `DbSession` and rerun
   steps 1, 3 and 4. InnoDB's default is REPEATABLE READ, its REPEATABLE READ is *not* Postgres's,
   and its lost-update behaviour differs. The point of the harness is that it answers the question
   rather than arguing about it.

## Resources

- **Martin Kleppmann — *Designing Data-Intensive Applications*, 2nd ed. (March 2026)**, chapter 7
  "Transactions". The clearest existing account of the anomaly ladder, and the source of the
  doctors-on-call write-skew example that Ada's linked overdraft is a variant of. If you read one
  thing, read this.
- **[PostgreSQL — Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html)**
  — short, precise, and it tells you outright that applications using SERIALIZABLE "must be
  prepared to retry transactions due to serialization failures". Read §13.2 and §13.3 in full.
- **Ports, D. R. K. & Grittner, K. — ["Serializable Snapshot Isolation in PostgreSQL"](https://drkp.net/papers/ssi-vldb12.pdf)**
  (VLDB 2012) — how SSI actually detects the cycle, by the people who implemented it. Worth it for
  the intuition about what it tracks and what it therefore costs.
- **Peter Bailis et al. — ["Highly Available Transactions: Virtues and Limitations"](http://www.bailis.org/papers/hat-vldb2014.pdf)**
  — and Bailis's blog series on isolation levels, for why the standard's definitions are ambiguous
  enough that vendors disagree in good faith.
- **[Spring Framework — Data Access / Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)**
  — specifically "Understanding the Spring Framework Transaction Abstraction" and the declarative
  section. The self-invocation limitation is documented; it is just never read in time.
- **Vlad Mihalcea — [*High-Performance Java Persistence*](https://vladmihalcea.com/books/high-performance-java-persistence/)**,
  part I. The JPA half belongs to project 21, but the transaction and isolation chapters are the
  best Java-specific treatment in print.
- **Franck Pachot / Markus Winand — [*Modern SQL*](https://modern-sql.com/)** — for when the fix
  is a better statement rather than a bigger lock.

---

**Build notes (verified August 2026, this machine).** Spring Boot 4.1.1 / Java 25,
`spring-boot-starter-jdbc`, Testcontainers 2.0.5 (`org.testcontainers:testcontainers-postgresql`,
`postgres:16-alpine`), no JPA. Pristine `mvn -B test`: **29 tests, 3 running and green, 26
checkpoints `@Disabled`**. With every checkpoint enabled and solved: **29 green**.

Findings verified here rather than assumed:

1. **Spring translates SQLSTATE 40001 to `org.springframework.dao.CannotAcquireLockException`**
   (via `ConcurrencyFailureException`), and it surfaces with the message `JDBC commit; ERROR: could
   not serialize access due to read/write dependencies among transactions` — i.e. thrown *by the
   commit*, after the `@Transactional` method has returned. Any retry must therefore sit outside
   the transactional proxy.
2. **Postgres REPEATABLE READ raises 40001 on a write-write conflict**, so the classic lost update
   becomes a failed transaction rather than silent corruption — while write skew still gets
   through. Both are checkpoint tests here.
3. **Connection-pool exhaustion arrives as `CannotCreateTransactionException`**, not as
   `CannotGetJdbcConnectionException`: the transaction manager fails while *opening* the
   transaction, and the Hikari `SQLTransientConnectionException` is the root cause two levels down.
4. **`Propagation.NESTED` works out of the box on `DataSourceTransactionManager`**, which enables
   savepoints by default — and, checked separately in project 21, on `JpaTransactionManager` under
   Boot 4.1.1 as well. The widely repeated "JPA cannot do NESTED" is not what this stack does;
   verify it on yours rather than inheriting the folklore either way.
5. Container startup on this machine costs ~30 s per Maven run, spent inside Ryuk while
   `docker-credential-desktop` times out. It is environmental, not the tests; the whole suite is
   ~3 s of actual work.
