# 22 — Distributed Locking: Leases, Fencing, and the Zombie Holder

> One-line promise: you can build a lock that survives a process disappearing, explain precisely
> why it still is not mutual exclusion, and design the thing that finally makes duplicate
> execution harmless.

## Why this matters (2026)

Projects 20 and 21 solved contention inside one database transaction. This one starts where that
stops working: two processes, one job, and no shared memory between them.

- **Every service is multi-instance now, whether or not you designed for it.** A rolling deploy
  runs two versions at once. A HorizontalPodAutoscaler decides at 02:00 that you need three
  replicas. `@Scheduled` fires on all of them. The single-instance assumption baked into a
  nightly job written in 2019 is not documented anywhere, and it fails as money.
- **The available answers are mostly libraries you should understand before adopting.**
  ShedLock, JobRunr, Quartz clustering, Kubernetes `Lease` objects, Redis-based locks. All of
  them are the forty lines in this project's `LeaseService`, plus operational polish. Knowing
  which of their guarantees is real is the difference between using one correctly and being
  surprised by it.
- **The famous critique is still the live one.** Martin Kleppmann's 2016 "How to do distributed
  locking" — written against Redlock — makes an argument about pauses and fencing tokens that no
  amount of newer tooling has retired, because it is about the impossibility, not the
  implementation. Step 4 reproduces the failure and step 5 implements his fix.
- **It closes the loop with the event-driven track.** `07-events-and-outbox` concluded that
  exactly-once delivery is not available and idempotency is the answer. This project reaches the
  same conclusion from the other direction, which is a good sign that the conclusion is right.

## Core concepts

### A lock you cannot release is worse than no lock

Everything about distributed locking follows from one asymmetry: acquiring is easy, releasing is
not. The holder can vanish — crash, network partition, `kill -9`, a node that stops existing —
and release code that assumed it would run is code that does not run. So a distributed lock is
not held until released. It is held **until it expires**: a lease.

That choice buys crash recovery and immediately introduces its own problem, which is step 4.

### Three mechanisms, in increasing order of what they survive

| Mechanism | Released by | Survives a crash | Costs | Good for |
|---|---|---|---|---|
| `synchronized` / `ReentrantLock` | the JVM | n/a — it never left the JVM | nothing | one process, and nothing else |
| `pg_try_advisory_xact_lock(key)` | the commit or rollback | yes, immediately | a connection held for the transaction | work that fits inside one transaction |
| a lease row with `locked_until` | expiry, or an early release | yes, after the lease runs out | a table and a clock question | work measured in minutes |

Advisory locks are underrated: no table, no migration, no cleanup, and the transaction-scoped
variant cannot leak. Their limit is exactly the limit of the transaction — and you should not be
holding a transaction open for a five-minute batch job (project 20, step 8).

The session-scoped `pg_advisory_lock` is the trap. It is held by the *connection*, so with a pool
the connection returns to the pool still holding it and the next borrower inherits a lock nobody
knows about. Use the `_xact_` variants.

### Whose clock?

A lease is a timestamp comparison, so somebody's clock decides who holds it. If that is the
worker's clock, then a fleet has as many opinions about the time as it has machines, and NTP
correcting a node by 30 seconds is a correctness event. Every timestamp in this project comes
from the *database's* `now()` — one clock, consulted by everybody. It is not that the database's
clock is accurate; it is that everyone is wrong in the same direction.

### The pause that makes all of it approximate

A worker checks that it holds the lease, and then it is paused. A stop-the-world GC. A hypervisor
migrating the VM. A container throttled to zero CPU. A laptop lid. Ten seconds is unremarkable.

While it is paused, the lease expires. Another worker takes it — legitimately, correctly, by the
rules. Then the first one resumes and continues, because from the inside no time passed at all.
Now two workers are in the critical section, and no component involved has malfunctioned.

You cannot fix this by making leases longer (you have only traded a correctness bug for a
liveness bug: a real crash now costs you a long outage). You cannot fix it by checking the lease
again just before acting, because the pause can happen after the check. **There is no schedule of
checks that closes the window.** That is step 4.

### Fencing tokens: stop trying to stop it, make it refusable

Every successful acquisition returns a number that only ever increases. The worker presents that
number with every action against the protected resource, and the resource remembers the highest
number it has seen and refuses anything older.

The zombie is still running and still convinced. Its token is stale, so its writes are rejected
at the door. Note what this requires: **the resource has to participate**. If the protected
resource is somebody else's API with no such concept, fencing is not available to you, and you
are back to making the work idempotent — which you should have done anyway.

### The honest hierarchy

1. **Idempotent work** — the only property that survives everything. Costs a key and a
   uniqueness check.
2. **Fencing tokens** — real safety, when you control the resource.
3. **Leases / advisory locks** — reduce how often overlap happens. This is a *performance and
   tidiness* mechanism far more than a correctness one, and it is routinely sold as the latter.
4. **`synchronized`** — a comment about your intentions.

## The project

A nightly billing run. Three customers owing 6 000 between them, a `PaymentGateway` standing in
for somebody else's system, and a fleet of workers who each believe they are the one running the
job.

The gateway is the point of the whole project. Everything in projects 20 and 21 could be undone
by rolling back a transaction; a charge cannot. It offers three doors — one that trusts you, one
that checks a fencing token, one that de-duplicates on an idempotency key — and steps 5 and 7 are
about earning the right to use the second and third.

What is given:

- `billing/Billing` — the work: invoice each customer, charge each customer. A Spring bean.
- `billing/BillingWorker` — **one pod**. Deliberately *not* a bean: the tests construct two of
  them, which is a weak simulation of two JVMs and still strong enough to break a `synchronized`
  block. It also carries `pauseAfterTakingTheLock`, the test seam that turns "the worker was
  paused at the worst possible moment" into something that happens on every run.
- `locking/AdvisoryLock` — step 2.
- `locking/LeaseService` + `Lease` — steps 3 and 5. Forty lines that are, structurally, ShedLock.
- `worker/JobQueue` — step 6.
- `gateway/PaymentGateway` — the resource you cannot roll back.

```bash
mvn test
```

Docker required. `HarnessTest` is green on checkout.

## Guided steps

### Step 1 — The lock that only works in your tests

**Goal:** see why single-instance testing cannot find this class of bug.

Enable `Checkpoint1JvmLockTest`. Read the first test first: with one instance, `synchronized`
works perfectly. That test would pass in CI forever.

The second constructs two instances — two pods — and every customer is charged twice. The monitor
is per object; in production it is per JVM. Nothing here is a race in the usual sense: both
workers do exactly what they were told, both transactions commit, and both are correct.

**Done when** all three are green and you can name the deployment change that turns the first
test's conclusion into the second's.

### Step 2 — An advisory lock, for work that fits in a transaction

**Goal:** get real mutual exclusion for the cost of one function call.

Enable `Checkpoint2AdvisoryLockTest` and implement `AdvisoryLock.tryLockForThisTransaction`.

<details><summary>Hint</summary>

`SELECT pg_try_advisory_xact_lock(:key)` returns a boolean: true if you now hold it, false
immediately if somebody else does. The `try_` prefix is what makes it non-blocking — the
un-prefixed version waits, which in a scheduled job means every replica queues up to run the
job you only wanted run once.

</details>

**Done when** a worker whose key is held does nothing at all (no wait, no exception, just
`false`), and the lock is released by the commit with no unlock call anywhere in your code.

Then read the class javadoc on the session-scoped variant. That one is a genuine footgun with a
connection pool, and it is the version most blog posts show.

### Step 3 — A lease, for work that does not

**Goal:** build the lock that survives its holder dying.

Enable `Checkpoint3LeaseTest` and implement `LeaseService.tryAcquire`.

<details><summary>Hint</summary>

It is project 21's conditional `UPDATE` again:

```sql
UPDATE job_lock
   SET locked_until = now() + …, locked_by = :owner, fencing_token = fencing_token + 1
 WHERE name = :name AND locked_until < now()
```

Update count 1 means you won; 0 means somebody else holds it. Return the row you won. Every
timestamp is `now()` — the database's clock, never the worker's. (You will need the
`fencing_token` increment in step 5; putting it in now costs nothing.)

</details>

**Done when** all five are green — in particular, that an expired lease is claimable again *with
nobody having released it*. That is the whole reason for the expiry: a dead process cannot call
`release()`, and this design does not ask it to.

Note the fourth test: only the holder may release. A worker that wrongly believes it holds the
lease must not be able to free somebody else's — which is a hint about what is coming.

### Step 4 — The zombie holder

**Goal:** understand, concretely, that none of this is mutual exclusion.

Enable `Checkpoint4ZombieHolderTest`. It passes as delivered. There is no fix in this step.

`pod-a` takes a 300 ms lease and is then paused — a GC, a migration, a throttle. Its lease
expires. `pod-b` acquires it perfectly legitimately and does the billing. `pod-a` wakes up and
finishes its run, because from the inside nothing happened.

Every customer is charged twice, and nothing malfunctioned. The lease service is *correct*: it
granted 300 ms and 300 ms passed. The mistake is in the sentence "I acquired the lease",
which was true, being used as if it meant "I hold the lease now", which is a claim about the
present that no past acquisition can make.

**Done when** both tests are green and you can explain why a longer lease is not the fix, and why
re-checking the lease immediately before the charge is not either.

### Step 5 — Fencing tokens

**Goal:** make the zombie's work refusable, since it cannot be prevented.

Enable `Checkpoint5FencingTest`. If you added the `fencing_token` increment in step 3 you may
already be green; if not, that is the change — the acquisition must hand out a number that only
goes up, and only when a lease is actually taken.

**Done when** the zombie's charge is rejected with `StaleTokenException`, `pod-b`'s work goes
through, and every customer is charged exactly once.

Look at what made that work: `PaymentGateway.chargeFenced` remembers the highest token it has
seen. The safety property is implemented *in the resource*, not in the lock service. This is the
core of Kleppmann's argument and the reason "we use a distributed lock" is not, by itself, a
safety claim. It is also why fencing is rare in practice — the resource is usually somebody
else's, and they have never heard of your tokens.

### Step 6 — A fleet sharing one table of work

**Goal:** hand out work to N workers, exactly once each, and survive one of them dying.

Enable `Checkpoint6CompetingWorkersTest` and implement `JobQueue.claim`.

<details><summary>Hint</summary>

Two mechanisms, two windows of time. `FOR UPDATE SKIP LOCKED` (project 21, step 6) covers the
instant of claiming so simultaneous workers take different rows; a `claimed_until` lease covers
the minutes of processing, because the row lock dies with the claiming transaction and the work
outlives it. Claimable means `status = 'PENDING' OR (status = 'CLAIMED' AND claimed_until < now())`.

</details>

**Done when** four workers claim four disjoint sets, a claim stepped over rather than waited for,
and an abandoned claim comes back on its own — with nothing in the system having noticed the
crash. That last property is what you are actually buying.

This is a perfectly good queue for a great many workloads. Before adding a broker to a job table
that sees 200 rows a day, make somebody explain what it buys.

### Step 7 — The only thing that actually holds

**Goal:** make duplicate execution a non-event.

Enable `Checkpoint7IdempotencyTest` and implement `Billing.billIdempotent`.

<details><summary>Hint</summary>

A key that identifies the *work* — period plus customer — not the attempt. `chargeIdempotent`
takes it; the invoice insert needs the same treatment (`INSERT … SELECT … WHERE NOT EXISTS`, or
`ON CONFLICT DO NOTHING` if you add a unique index — see the stretch goals for why the index is
the version that actually holds).

</details>

**Done when** running the entire billing twice bills everybody once, from any worker, with no
lock, no lease and no token involved anywhere in the test.

Then re-read the list in "The honest hierarchy" above. The locks in steps 2–6 stop being your
safety mechanism and become what they always were: a way to avoid doing the work three times.
That is worth having. It is just not the thing keeping you correct.

## An honest section

**What you should actually use.** Do not ship `LeaseService`. Use ShedLock (for `@Scheduled`
methods, which is the common case), JobRunr or Quartz clustering if you want a job framework, or
the Kubernetes `Lease` API if you are already electing a leader for other reasons. They are this
design plus the operational details — metrics, configurable clocks, backoff — that you would
otherwise write badly. Build it once, by hand, to know what they promise; then use theirs.

**On Redis-based locks.** Redlock is a real argument with real people on both sides. The short
version: a single-instance Redis lock is fine for efficiency (don't do the work twice) and not
for correctness (never do the work twice), and the multi-instance algorithm's safety claim
depends on timing assumptions that a paused process violates. If you find yourself needing
correctness from a lock, you need fencing tokens or idempotency, whatever the lock is made of.

**Where this project is dishonest.** Two objects in one JVM are not two machines: they share a
clock, a GC, a network position and a failure domain. That is enough to demonstrate every
mechanism here, and not enough to experience a partition. Real partitions are where the
interesting parts live, and reproducing them needs Jepsen-class tooling, which is beyond a
three-hour lesson but worth knowing exists.

**One thing not covered:** leader election proper (one worker holds a renewed lease indefinitely
while the others stand by), which is a small extension of step 3 and the first stretch goal.

## Self-check

1. Why is a distributed lock a lease rather than a lock, and what does that choice cost you?
2. What breaks if the lease expiry is compared against the worker's clock instead of the
   database's?
3. Explain the zombie-holder failure to a colleague in four sentences. Then explain why a longer
   lease does not fix it.
4. What does a fencing token require from the system you are protecting, and what do you do when
   that system is not yours?
5. When is `pg_advisory_xact_lock` the right tool, and when does it become the wrong one?
6. A queue table with 12 workers: which mechanism covers the claim, which covers the processing,
   and why can't one do both?
7. Your `@Scheduled` nightly job runs on all three replicas. Give the three-line fix, and say what
   it does and does not guarantee.
8. "We took a distributed lock, so this cannot run twice." What is wrong with that sentence?

## Stretch goals

1. **Leader election with renewal.** Extend `LeaseService` with a heartbeat: the holder renews
   every `lease/3`, and stops working the moment a renewal fails. Then write the test that proves
   the obvious flaw — the holder can be paused between the failed renewal and noticing.
2. **Make the invoice insert genuinely idempotent.** `INSERT … WHERE NOT EXISTS` is idempotent
   against *retries* and not against *concurrency*: two simultaneous inserts can both pass the
   check. Add `UNIQUE (customer, period)` and handle the conflict. Then note which of your
   idempotency mechanisms is enforced by the database and which is enforced by hope.
3. **Break the fence.** Make `chargeFenced` compare with `<=` instead of `<` and watch the honest
   worker reject its own second charge. Off-by-one in a fencing check is a subtle outage, and
   writing the test that catches it is the exercise.
4. **Measure the lease's real cost.** Give the nightly job a 5-minute lease and kill the worker
   one minute in. How long is the system idle? Now do it with a 30-second lease and renewal. The
   trade between "how long a crash costs" and "how likely a zombie is" is the only tuning knob
   here, and it is worth having felt it.

## Resources

- **Martin Kleppmann — ["How to do distributed locking"](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)**
  — the fencing-token argument, written as a critique of Redlock. Read it with step 4 fresh; the
  diagram of the paused client is exactly the test you just ran. Then read Salvatore Sanfilippo's
  reply for the other side of the argument.
- **Martin Kleppmann — *Designing Data-Intensive Applications*, 2nd ed. (2026)**, chapter 8
  ("The Trouble with Distributed Systems") — unreliable clocks, process pauses, and why
  "the node checked and it was fine" is not a statement about the present. Chapter 9 for
  consensus, which is where this ends up if you keep pulling.
- **[PostgreSQL — Advisory Locks](https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS)**
  — §13.3.5. Short, and the session-vs-transaction distinction is stated plainly enough that the
  pooling footgun is obvious once you have read it.
- **[ShedLock](https://github.com/lukas-krecan/ShedLock)** — read the source (it is small) and
  especially the README's own warning that it is not a distributed lock in the safety sense. It is
  the same table you built in step 3, and it says so.
- **[Kubernetes leader election](https://kubernetes.io/docs/concepts/architecture/leases/)** and
  the `client-go` leaderelection package's documented caveats — a production lease implementation
  that is honest about the same gap.
- **Chris Richardson — [Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html)**,
  and this training's own `07-events-and-outbox` — the same conclusion, reached from the
  message-delivery side.
- **[Jepsen](https://jepsen.io/analyses)** — Kyle Kingsbury's analyses. Not required reading for
  this lesson, but the correct answer to "how would we actually know?" and a good antidote to
  vendor safety claims.

---

**Build notes (verified August 2026, this machine).** Spring Boot 4.1.1 / Java 25,
`spring-boot-starter-jdbc`, Testcontainers 2.0.5 (`postgres:16-alpine`). Pristine `mvn -B test`:
**26 tests, 2 running and green, 24 checkpoints `@Disabled`**. With every checkpoint enabled and
solved: **26 green** — unlike project 21, nothing here flips, because the vulnerable code paths
(`runUnprotected`, `runWithLease`) stay in place as exhibits alongside the repaired ones.

Findings verified here rather than assumed:

1. **The zombie-holder failure reproduces deterministically** with latches and a 300 ms lease —
   no timing luck, no sleeps in the assertions, and it runs in under a second. Worth knowing,
   because "you can't really test that" is the usual reason this failure is discussed as theory.
2. **`pg_try_advisory_xact_lock` releases on commit with no unlock call**, confirmed by asserting
   `pg_locks` drops to zero advisory locks afterwards — including when the holding transaction
   rolls back.
3. **`make_interval(secs => :seconds)` is the clean way to parameterise a Postgres interval** from
   a JDBC parameter; string-concatenating an interval literal works and invites injection, and
   `:param * interval '1 second'` needs a cast that varies by driver.
4. **`WHERE id = ANY (:ids)`** with a `Long[]` parameter works through `JdbcClient` and avoids
   building an `IN (…)` list by hand.
5. A billing run that throws mid-way rolls back its invoices but **not** its charges — the
   gateway is deliberately outside the transaction, because that is the entire premise of the
   project and the thing a transaction cannot help with.
