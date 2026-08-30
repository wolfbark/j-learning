# 06 — Modular Monolith: The Split-Ready Library

> After this lesson you can take a tangled single-deployable Spring Boot app, carve it into
> Spring Modulith modules with test-enforced boundaries, replace cross-module calls with
> domain events, test each module in isolation, and generate architecture docs from the code.

## Why this matters (2026)

The modular monolith is arguably the headline architecture story of 2025–2026. A widely cited
CNCF figure says **~42% of organizations that adopted microservices have consolidated at least
some services back** into larger deployable units; Gartner found ~60% of teams regretted
microservices for small/medium apps. The industry's corrected consensus — "monolith-first"
(Fowler), amplified by Shopify's and Stack Overflow's monoliths and the Amazon Prime Video
case study — has hardened into **"modulith first, extract when proven."** In the Java world,
Spring Modulith graduated to a flagship Spring project (2.0 GA on Boot 4 in Nov 2025, 2.1 GA
in Jun 2026), and JetBrains shipped dedicated IntelliJ IDEA support in Feb 2026.

The pitch is precise: a modulith gives you the *organizational* benefits people actually want
from microservices — bounded contexts, team ownership, independent evolution — without the
distributed-systems tax. And because modules communicate through events, the internal
contracts are exactly the seams along which a module can later be extracted into a service.
Hence this lesson's title: you are not building a monolith that hopes to stay one; you are
building one that is *cheap to split if the day ever comes* — and cheap to keep whole if it
doesn't, which is the more likely and the more underrated outcome.

Be honest about the costs, too: enforced boundaries are only as good as the APIs you design
(a lazy facade degenerates into a god-gateway), and in-process events buy decoupling at the
price of eventual consistency *inside your own process* — you will feel that price first-hand
in step 4.

One scope note: **event externalization to Kafka (`@Externalized`, the full transactional
outbox arc) is deliberately not in this lesson** — project 07 covers it. Here everything
stays in-process; you'll see at the end how little is missing.

Source material: [architecture-styles.md, section 2](../docs/research/architecture-styles.md)
and [event-driven.md, section 7](../docs/research/event-driven.md).

## Core concepts

**Modules from package conventions.** Spring Modulith derives the module structure from your
packages: every *direct subpackage* of the main application package is an application module.
Types sitting next to the main class belong to no module and are shared infrastructure.

```
dev.vlearning.library            ← application root (shared, unrestricted)
├── catalog                      ← module "catalog"
├── lending                      ← module "lending"
└── notifications                ← module "notifications"
```

**Exposure: base package = API, subpackages = internal.** Types in a module's base package
(`catalog.CatalogService`) are the module's published API — other modules may use them. Types
in any subpackage (`catalog.domain.Book`, `catalog.repo.BookCopyRepository`) are internal —
other modules may not, even though Java's `public` says they could. Java visibility is too
coarse for architecture; Modulith layers the missing rule on top. (Finer control exists via
`@NamedInterface` — see stretch goals.)

**`verify()` — the boundary rules as one JUnit test.**

```java
ApplicationModules.of(LibraryApplication.class).verify();
```

This runs ArchUnit under the hood and fails on: cyclic dependencies between modules, any
access to another module's non-exposed types, and (once you opt in via `allowedDependencies`)
any dependency you didn't declare. It is a fitness function: the architecture cannot silently
rot once this test is in CI.

**Events instead of bean calls.** A direct call `lending → notifications` makes lending
*know about and depend on* notifications forever — and it inverts the domain truth, because
lending doesn't need notifications; notifications needs to know what lending did. The fix is
a domain event:

```java
// lending publishes a fact…
events.publishEvent(new LoanCreated(loanId, memberEmail, bookTitle, copyBarcode, dueDate));

// …notifications reacts to it
@ApplicationModuleListener
void on(LoanCreated event) { ... }
```

`@ApplicationModuleListener` is shorthand for `@Async` + `@TransactionalEventListener`
(after commit) + `@Transactional(REQUIRES_NEW)`. Three consequences you must internalize:
the listener runs *after* the publisher's transaction commits; it runs on *another thread*;
and its failure does *not* roll back the publisher. That is eventual consistency — inside one
JVM. Martin Fowler's distinction matters here: an event used purely as a *notification* makes
the consumer call back for data, while an event that *carries the state* the consumer needs
(event-carried state transfer) removes that dependency entirely. You will build the second
kind.

**Event Publication Registry — the outbox, minus the broker.** An in-memory event is lost if
the process dies between commit and listener completion. With a registry store on the
classpath (this project uses the JPA one), Modulith writes an entry to an
`EVENT_PUBLICATION` table *in the same transaction as the publishing business change*, and
marks it completed when the listener succeeds. Crash in between → an incomplete publication
survives in the database, resubmittable on restart
(`spring.modulith.events.republish-outstanding-events-on-restart=true`). At-least-once
delivery, so listeners must tolerate redelivery. This is the transactional-outbox pattern
with zero infrastructure — project 07 connects it to a real broker.

**Module-scoped integration tests.** `@ApplicationModuleTest` bootstraps *one* module's slice
of the application context — with `BootstrapMode.STANDALONE` (default), or including direct
dependencies (`DIRECT_DEPENDENCIES`), or all of them. The `Scenario` DSL drives event-based
interactions: stimulate the module, await an outgoing event or a state change.

**Generated docs.** `Documenter` produces C4/PlantUML component diagrams and a per-module
"Application Module Canvas" from the *verified* structure — architecture documentation that
cannot lie, because it is derived from the code on every build.

## The project

A small library system: the **catalog** owns books and physical copies, **lending** owns
loans and due dates, **notifications** sends (well, logs and collects) loan confirmations and
overdue notices. In-memory H2 — the database is not the topic.

Everything works. And everything touches everything — the packages *suggest* modules, but no
boundary actually holds. The smells are deliberate:

- `lending.LendingService` imports catalog's **JPA entity and repository** to check
  availability and flip copy status — including the deep reach `copy.getBook().getTitle()`.
- `lending.LendingService` directly calls `notifications.NotificationSender` to send the
  loan confirmation — lending knows about notifications, which is backwards.
- `notifications.NotificationSender` and `notifications.OverdueNotifier` reach back into
  **lending's repository and entity** to compose message texts. Together with the direct call
  above, that is a dependency *cycle*.

```
src/main/java/dev/vlearning/library/
├── LibraryApplication.java, ApiExceptionHandler.java   ← shared root
├── catalog/
│   ├── CatalogController.java          ← add book (+copies), list books
│   ├── domain/Book.java, BookCopy.java
│   └── repo/BookRepository.java, BookCopyRepository.java
├── lending/
│   ├── LendingController.java, LendingService.java
│   ├── LoanCreated.java, LoanReturned.java   ← given event records, unused for now:
│   │                                            the checkpoint tests compile against them
│   └── domain/Loan.java, repo/LoanRepository.java
└── notifications/
    ├── NotificationSender.java         ← fake gateway: logs + collects messages
    ├── OverdueNotifier.java
    └── NotificationsController.java
```

| Endpoint | Behavior |
|---|---|
| `POST /catalog/books` | add a book with N copies → 201, returns generated barcodes |
| `GET /catalog/books` | books with copies and their statuses |
| `POST /loans` | borrow a copy by barcode → 201; 409 if on loan; 404 if unknown |
| `POST /loans/{id}/return` | return the copy, frees it for the next borrower |
| `GET /notifications` | every message "sent" so far |
| `POST /notifications/overdue-run` | compose overdue notices for late, unreturned loans |

Run it:

```bash
mvn spring-boot:run
curl -s -X POST localhost:8080/catalog/books -H 'Content-Type: application/json' \
     -d '{"isbn":"978-0-13-235088-4","title":"Clean Code","author":"Robert C. Martin","copies":2}'
curl -s -X POST localhost:8080/loans -H 'Content-Type: application/json' \
     -d '{"barcode":"978-0-13-235088-4-1","memberEmail":"you@example.com","dueDate":"2026-09-15"}'
curl -s localhost:8080/notifications
```

Run the tests — **pristine checkout must be green** (7 behavior tests pass, 6 checkpoint
tests skipped):

```bash
mvn -q test
```

`LibraryApiBehaviorTest` pins the HTTP contract and stays enabled the whole time. Its
notification assertions *poll* with Awaitility and the class is deliberately not
`@Transactional` — the tangled code sends synchronously, but from step 3 on, messages go out
asynchronously after commit, and the same tests must hold in both worlds.

## Guided steps

### Step 1 — Turn on the boundary check and read the wreckage

**Goal:** make the absence of boundaries *visible and failing*.

Enable both tests in `ModularityTests` (remove `@Disabled`). `printModuleArrangement()` shows
what Modulith derived from your packages — three modules, their base packages, their beans.
`modulesRespectTheirBoundaries()` **fails**. That is the point. This checkpoint is unusual:
it stays enabled and *red* through steps 2–3 and becomes your progress meter; it goes green
at the end of step 4.

Read the violation report top to bottom and find each violation in the code. There are
exactly three kinds:

1. `Cycle detected: Slice lending -> Slice notifications -> Slice lending` — the direct
   call one way, the repository reachback the other.
2. `Module 'lending' depends on non-exposed type dev.vlearning.library.catalog.domain.BookCopy…`
   — subpackages are internal; lending is groping catalog's insides.
3. `Module 'notifications' depends on non-exposed type dev.vlearning.library.lending.repo.LoanRepository…`
   — same disease, other direction.

Note what is *not* flagged: lending calling `notifications.NotificationSender` is a legal
type access (it sits in the notifications base package — exposed by convention). It is only
reported as half of the cycle. Exposure rules and dependency direction are separate checks.

**Done when:** you can point at the code line behind each of the three violation kinds and
explain why Modulith flags it.

### Step 2 — Give catalog a published API

**Goal:** lending stops touching catalog's entities and repositories; it talks to a facade.

Design the smallest API that covers what lending actually needs — which is not "read copies"
but two *operations*: take an available copy, and put one back. Create in the `catalog` base
package (that placement is what makes it public API):

- `CatalogService` — `checkOut(String barcode)` claims an available copy (404-style
  `NoSuchElementException` if unknown, `IllegalStateException` if already out) and
  `putBack(String barcode)` releases it.
- `CheckedOutCopy(String barcode, String bookTitle)` — a record: the *only* shape of copy
  data the rest of the system gets to see. Lending needs the title for the loan record; it
  does not need the entity.

Then rewire `LendingService`: inject `CatalogService` instead of `BookCopyRepository`, and
delete all `catalog.domain`/`catalog.repo` imports from lending.

<details><summary>Hint — the facade</summary>

```java
@Service
public class CatalogService {

    private final BookCopyRepository copies;

    CatalogService(BookCopyRepository copies) { this.copies = copies; }

    @Transactional
    public CheckedOutCopy checkOut(String barcode) {
        BookCopy copy = copies.findByBarcode(barcode)
                .orElseThrow(() -> new NoSuchElementException("No copy with barcode " + barcode));
        if (copy.getStatus() != BookCopy.Status.AVAILABLE) {
            throw new IllegalStateException("Copy " + barcode + " is already on loan");
        }
        copy.setStatus(BookCopy.Status.ON_LOAN);
        return new CheckedOutCopy(copy.getBarcode(), copy.getBook().getTitle());
    }

    @Transactional
    public void putBack(String barcode) {
        copies.findByBarcode(barcode).orElseThrow().setStatus(BookCopy.Status.AVAILABLE);
    }
}
```

In `LendingService.borrow`, the first three lines collapse into
`CheckedOutCopy copy = catalog.checkOut(barcode);`. The availability check, the status flip,
and the title lookup were never lending's business.
</details>

Optionally, rename `catalog.domain`/`catalog.repo` to live under `catalog.internal` and move
`CatalogController` there too. Verification treats all subpackages as internal either way —
the rename only documents intent (and IntelliJ's Modulith support highlights `internal`
specially). The alternative to a moved package is marking a package with `@NamedInterface`
to *expose* it selectively; here the simple convention is enough.

**Done when:** the `modulesRespectTheirBoundaries()` report no longer mentions `catalog`
anywhere (the cycle and the notifications violations remain), and all behavior tests are
still green.

### Step 3 — Lending announces; it stops instructing

**Goal:** replace the `lending → notifications` bean call with the `LoanCreated` event.

The event record is already there — `lending.LoanCreated`, given because the checkpoint
tests compile against it. It sits in lending's *base package* deliberately: events are the
module's published API too.

1. In `LendingService.borrow(...)`, inject `ApplicationEventPublisher` and publish a fully
   populated `LoanCreated` inside the transaction, after saving the loan.
2. Delete the `NotificationSender` injection and call from lending. Lending now has no idea
   notifications exists.
3. In notifications, add a listener: a method annotated `@ApplicationModuleListener`
   (from `org.springframework.modulith.events`) taking `LoanCreated`. For now it may keep
   composing the message the old way — loading the loan by `event.loanId()` through the
   repository reachback. One smell at a time.

**Done when:** the cycle violation is gone from the report (only
`notifications → lending.repo/domain` internal accesses remain) and the behavior tests are
still green — confirmations now arrive asynchronously, which the polling tests absorb.

### Step 4 — The event carries the state; the reachback dies

**Goal:** notifications composes every message from event data alone; its dependency on
lending's internals is deleted. `verify()` goes green.

Look at `LoanCreated`: member, title, barcode, due date — everything the confirmation *and*
the overdue notice need. That is event-carried state transfer in miniature. So:

1. Compose the confirmation purely from the event. Delete
   `NotificationSender.sendLoanConfirmation(...)` and its `LoanRepository` field.
2. `OverdueNotifier` is the interesting one: it queried lending's repository for open, late
   loans. Give notifications *its own* view instead — a `Map<Long, LoanCreated>` of open
   loans, fed by events: put on `LoanCreated`, remove on `LoanReturned`. Publish
   `LoanReturned` (also given) from `LendingService.returnLoan(...)`.
3. Delete every `dev.vlearning.library.lending.domain`/`lending.repo` import from
   notifications. Only the two event types remain — lending's published API.

<details><summary>Hint — the notifier becomes a projection</summary>

```java
@Component
public class OverdueNotifier {

    private final Map<Long, LoanCreated> openLoans = new ConcurrentHashMap<>();
    private final NotificationSender sender;

    OverdueNotifier(NotificationSender sender) { this.sender = sender; }

    @ApplicationModuleListener
    void on(LoanCreated event) {
        openLoans.put(event.loanId(), event);
        sender.send("To %s: you borrowed '%s' (copy %s), due %s".formatted(
                event.memberEmail(), event.bookTitle(), event.copyBarcode(), event.dueDate()));
    }

    @ApplicationModuleListener
    void on(LoanReturned event) {
        openLoans.remove(event.loanId());
    }

    public int run() {
        var overdue = openLoans.values().stream()
                .filter(loan -> loan.dueDate().isBefore(LocalDate.now())).toList();
        overdue.forEach(loan -> sender.send("OVERDUE: %s should have returned '%s' (copy %s) by %s"
                .formatted(loan.memberEmail(), loan.bookTitle(), loan.copyBarcode(), loan.dueDate())));
        return overdue.size();
    }
}
```
</details>

Two honest costs, felt immediately. First, the projection is **eventually consistent**:
return a copy and hit `/notifications/overdue-run` in the same millisecond and the loan may
still be flagged — the `LoanReturned` listener hasn't run yet. Second, the map is
**in-memory**: restart the app and it's empty, and the registry won't replay *completed*
events (see step 6). Loans, meanwhile, are safely in the database. A real implementation
would give notifications its own persistent table — its own data, not lending's. Both
limitations are the price of decoupling, and naming the price is part of the pattern.

**Done when (checkpoint):** `ModularityTests` is fully green, and `mvn -q test` is green.
The architecture now *cannot* regress without a failing build.

### Step 5 — Test a module without booting the world

**Goal:** module-scoped integration tests with `@ApplicationModuleTest` and `Scenario`.

Enable `Checkpoint5LendingScenarioTest` and `Checkpoint5NotificationsScenarioTest` and read
them before running — the annotations *are* the lesson:

- The **lending** test uses `BootstrapMode.DIRECT_DEPENDENCIES`, because lending calls the
  catalog facade synchronously — the bean must exist, so catalog rides along. The catalog
  rows are seeded with plain `@Sql` inserts: the test must not touch catalog's Java
  internals, but the *schema* is still shared — a modulith is one database. Notice both
  facts. The `Scenario` stimulates `lending.borrow(...)` and intercepts the outgoing
  `LoanCreated` — asserting on lending's *contract*, not on another module's behavior.
- The **notifications** test bootstraps `STANDALONE` (the default): after step 4 the module
  depends on nothing but lending's event *types*. `scenario.publish(event)` injects the
  event exactly as lending would; the test then awaits the observable state change.

The asymmetry is the payoff: a synchronous bean dependency forces the neighbor into your
test; an event dependency doesn't. Prove it to yourself — change the lending test to
`STANDALONE` and watch the context fail to start for lack of a `CatalogService` bean
(alternatively, satisfy it with `@MockitoBean`). Revert.

**Done when:** both checkpoint 5 tests are green, and you have seen the lending test fail in
`STANDALONE` mode and can explain why notifications does not have that problem.

### Step 6 — The registry: an outbox you already have

**Goal:** see that event publications are durable, not fire-and-forget.

It has been running since step 3: `spring-modulith-starter-jpa` put the Event Publication
Registry on the classpath, so every `LoanCreated`/`LoanReturned` publish also writes a row
to the `EVENT_PUBLICATION` table — in the same transaction as the loan itself. When the
listener finishes, the row is marked completed.

Enable `Checkpoint6EventPublicationRegistryTest`. It borrows over HTTP, then uses the
registry's API (`CompletedEventPublications`) to prove the `LoanCreated` publication was
persisted *and* completed. Then look at the raw truth: add
`spring.jpa.show-sql=true` for a run, or query the table in any test —
`SELECT event_type, listener_id, completion_date FROM event_publication` — one row per
event *per listener*.

Now the story this table enables. Suppose the JVM dies after the borrow transaction commits
but before the notifications listener runs. Without the registry the event is simply gone —
the classic dual-write hole. With it, the publication sits there with `completion_date IS
NULL`, and:

- `spring.modulith.events.republish-outstanding-events-on-restart=true` resubmits incomplete
  publications on startup, or you trigger it yourself via the `IncompleteEventPublications`
  bean;
- delivery becomes **at-least-once** — a crash *after* the listener ran but *before*
  completion was recorded means redelivery, so listeners must be idempotent (re-`put`ting
  the same `LoanCreated` into the map is — conveniently — harmless);
- completed rows stay by default (`spring.modulith.events.completion-mode=UPDATE`; `DELETE`
  and `ARCHIVE` exist) — plan cleanup in real systems.

To see a failure with your own eyes: throw a `RuntimeException` at the top of the
`LoanCreated` listener, borrow a book via `curl`, and query the table — incomplete
publication, loan committed, copy on loan. Remove the throw. That row is the difference
between "the notification is late" and "the notification never existed".

This is the transactional outbox pattern minus the message broker. Project 07 completes the
arc: `@Externalized` events flowing from this same registry to Kafka.

**Done when:** checkpoint 6 is green and you have seen an incomplete publication in the
table at least once.

### Step 7 — Docs from code, and the exit interview

**Goal:** generate the architecture documentation and decide what you'd extract first.

Enable `Checkpoint7DocumentationTest` and run it. Open `target/spring-modulith-docs/`:

- `components.puml` — the C4 component diagram. Two arrows, and they are *labeled
  differently*: `lending → catalog` is "uses" (bean dependency), `notifications → lending`
  is **"listens to"** (event dependency). The generator read that out of your bytecode.
  Render it with any PlantUML tool (IntelliJ plugin, plantuml.com) or just read the source.
- `module-*.adoc` / `module-*.puml` — the per-module canvas: published API, spring beans,
  events published and listened to. This is onboarding documentation that regenerates on
  every build and therefore cannot drift.

Then the debrief — write the answers down, they are the actual deliverable of this lesson:

1. **Which module would you extract into a service first, and why?** Weigh the evidence the
   diagram gives you: notifications has no synchronous callers, is coupled only through two
   events, and already maintains its own state from those events — extracting it means
   pointing a consumer at a broker instead of the in-process bus. Catalog, by contrast, sits
   behind a *synchronous* facade that lending blocks on: extracting it means a network call
   in the middle of `borrow`, with all the latency and failure modes that implies.
2. **What does the event contract already buy you?** `LoanCreated` is a stable, versionable
   record — the message schema of the future integration. The registry already persists
   publications transactionally — the outbox skeleton. The `@ApplicationModuleTest` for
   notifications already tests it as if it were a standalone consumer. The split, if it ever
   comes, is a deployment decision — not a rewrite.
3. **And the null option:** what did it cost to get all this while *staying* one deployable,
   one database, one debugger session? That cost — a facade, two records, one listener —
   against the microservices tax is the whole 2026 argument.

**Done when:** the docs exist, and you can argue the extraction order from the generated
diagram rather than from vibes.

## Self-check

1. What makes a package an application module for Spring Modulith, and which of its types
   are exposed to other modules by default?
2. `verify()` reported lending's call to `NotificationSender` only as part of a cycle, not
   as an illegal type access. Why? What are the two separate rules at play?
3. Expand `@ApplicationModuleListener` into its constituent annotations and name three
   behavioral consequences of that combination for the publisher and the listener.
4. Why must the behavior tests avoid `@Transactional` and poll for notifications? What
   would a `@Transactional` test silently break after step 3?
5. Fowler distinguishes *event notification* from *event-carried state transfer*. Which did
   step 3 produce, which did step 4 produce, and what did the second one cost notifications
   (think restart)?
6. What exactly does the Event Publication Registry guarantee, what delivery semantics
   follow, and what must listeners therefore be? What does it *not* protect (hint: where
   does the `openLoans` map live)?
7. Why did the lending module test need `DIRECT_DEPENDENCIES` while notifications ran
   `STANDALONE`? What does that asymmetry tell you about which coupling is heavier?
8. Your team says "let's split the library into three services now, while it's small."
   Using this lesson's evidence, argue for and against — in which concrete circumstances
   would extraction be justified?

## Stretch goals

- **Strict dependency declarations.** Add
  `@ApplicationModule(allowedDependencies = "catalog")` (in a `package-info.java`) to
  lending, and the equivalent for the others. Now an *undeclared* dependency fails
  `verify()` even if it only touches exposed types — allow-listing instead of deny-listing.
  Watch notifications fail until you declare `lending` (its event types) as allowed.
- **Named interfaces.** Move lending's event records into a `lending.events` subpackage
  marked with `@NamedInterface("events")`, and restrict notifications to
  `allowedDependencies = "lending::events"` — modules may then use lending's events but
  not its (exposed) service.
- **Survive the restart.** Replace the `openLoans` map with a small JPA entity owned by
  notifications. Now the projection survives restarts and the module owns real data — the
  full event-carried state transfer pattern, and exactly what it would own as a service.
- **Break delivery, then heal it.** With the throwing listener from step 6 in place, borrow
  a book, stop the app, remove the throw, set
  `spring.modulith.events.republish-outstanding-events-on-restart=true`, and start against a
  *file-based* H2 URL instead of in-memory (with `spring.jpa.hibernate.ddl-auto=update`, or
  create-drop will wipe the evidence) — and watch the confirmation arrive on startup, one
  process lifetime late.

## Resources

- Oliver Drotbohm — [Spring Modulith reference documentation](https://docs.spring.io/spring-modulith/reference/)
  and his talks: [Building Better Monoliths — Implementing Modulithic Applications with Spring](https://2019.springio.net/sessions/building-better-monoliths-implementing-modulithic-applications-with-spring)
  and the ongoing "Spring Modulith — A Deep Dive" deck (Spring I/O) — the author's canon.
- Simon Brown — [Modular Monoliths](https://www.youtube.com/watch?v=5OjqD-ow8GE) — the
  original "if you can't build a modular monolith, what makes you think microservices will
  help?"
- Kamil Grzybek — [Modular Monolith with DDD](https://github.com/kgrzybek/modular-monolith-with-ddd)
  — the most complete reference implementation and article series (C#, concepts port 1:1).
- JetBrains — [Migrating to Modular Monolith using Spring Modulith and IntelliJ IDEA](https://blog.jetbrains.com/idea/2026/02/migrating-to-modular-monolith-using-spring-modulith-and-intellij-idea/)
  (Feb 2026) — current tooling walkthrough of exactly this lesson's refactoring.
- Baeldung — [Introduction to Spring Modulith](https://www.baeldung.com/spring-modulith).
- Martin Fowler — [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
  — the notification vs. event-carried state transfer distinction used in step 4.
- Research notes: [Backend Architecture Styles, section 2](../docs/research/architecture-styles.md)
  and [Event-Driven Architecture, section 7](../docs/research/event-driven.md).

---

*Build note (verified on this machine): Spring Boot parent 4.1.1, Spring Modulith BOM
**2.1.0** (latest 2.1.x on Maven Central at authoring time) with `spring-modulith-starter-core`,
`-starter-jpa`, `-starter-test` and `spring-modulith-docs` — all resolved as pinned, no
deviations. Boot 4 modularization notes: MockMvc support comes from
`spring-boot-starter-webmvc-test` with `AutoConfigureMockMvc` in
`org.springframework.boot.webmvc.test.autoconfigure`; Boot 4 ships Jackson 3
(`tools.jackson`) — the behavior tests parse JSON via JsonPath to stay version-agnostic.
Mockito is loaded as a `-javaagent` through the Surefire `argLine` (JDK 25 dynamic-agent
deprecation). `@ApplicationModuleListener` needs no `@EnableAsync` — Modulith auto-configures
async support. The JPA registry's table is `EVENT_PUBLICATION`, created by Hibernate DDL like
the entity tables.*
