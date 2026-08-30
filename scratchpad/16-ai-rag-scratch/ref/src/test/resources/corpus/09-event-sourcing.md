# Event Sourcing lesson (excerpt)

> Frozen excerpt of 09-event-sourcing/README.md, copied into src/test/resources so the checkpoint tests
> have a corpus that never changes. The running application reads the real files.

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
