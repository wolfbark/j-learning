# 05 — DDD: Storm It, Bound It, Enforce It

> After this lesson you can read an event-storming wall, cut a domain into bounded contexts,
> turn an anemic Spring service into a real aggregate with value objects and domain events —
> and make ArchUnit fail the build when anyone quietly undoes it.

## Why this matters (2026)

DDD is arguably at peak practical relevance, twenty-plus years after Evans — for two
reasons. First, the **modular-monolith correction**: a CNCF Q1 2026 report found 42% of
organizations that adopted microservices have consolidated services back into larger
deployable units, and a modular monolith lives or dies on bounded-context discipline. That
is what made strategic DDD the working vocabulary of architecture modernization (Tune's
book merges it with Team Topologies; Khononov's coupling model generalizes it). Second, the
tooling caught up: tactical DDD in Java is now *expressed in code* with jMolecules
annotations and *enforced* with ArchUnit and Spring Modulith, instead of living in a wiki
nobody reads.

Carry the honest framing, though: **the tactical patterns are the visible part, but
strategic design is the value.** An aggregate with a beautiful `confirm()` method in the
wrong bounded context is still the wrong model — and a plain CRUD service in a correctly
drawn context is often exactly right. Khononov is blunt about this: most DDD failures are
strategic failures (boundaries, language, relationships between teams), not a missing
`@ValueObject` annotation. This lesson therefore starts and ends with strategy — event
storming in, context-map debrief out — and puts the tactical refactoring in the middle,
where it belongs. Source material:
[docs/research/methodologies.md](../docs/research/methodologies.md), section 2, and
[docs/research/architecture-styles.md](../docs/research/architecture-styles.md), section 2.

## Core concepts

**Strategic design** answers "what are we building, in how many models, owned by whom":

- **Subdomains** come in three kinds. *Core* — where the business differentiates and
  complexity pays for itself; build it carefully, in-house. *Supporting* — necessary but
  not differentiating; keep it simple. *Generic* — solved industry-wide; buy or copy.
  Khononov's heuristic: core subdomains change often and hurt when done badly; generic ones
  you could outsource tomorrow and nobody would notice.
- A **bounded context** is a boundary inside which one model and one **ubiquitous
  language** hold. "Course" means something different to the person scheduling trainers
  than to the person booking a seat — that is two contexts, not one entity with forty
  fields. Contexts are a *solution-space* decision (you draw them); subdomains are
  *problem-space* (you discover them).
- **Context mapping** names the relationships between contexts: partnership,
  customer–supplier, conformist, anticorruption layer (ACL), open host service (OHS),
  published language. These are as much about team politics as about code — a conformist
  relationship is a decision to *not* negotiate.
- **EventStorming** (Brandolini) is the discovery workshop that feeds all of the above:
  everyone in a room, orange stickies for domain events on a timeline, and the boundaries
  reveal themselves where the language changes — the *pivotal events*.

**Tactical design** answers "how does the code inside one context stay honest":

- **Value objects** are immutable, compared by value, and impossible to construct invalid.
  Java records with a validating compact constructor are the perfect fit:

  ```java
  @ValueObject
  public record Email(String value) {
      public Email {
          value = value.trim().toLowerCase();
          if (!FORMAT.matcher(value).matches())
              throw new IllegalArgumentException("not a valid email address: " + value);
      }
  }
  ```

  The payoff is *type-level trust*: a method receiving an `Email` never re-validates it.
- **Entities** have identity and a lifecycle. An **aggregate** is a cluster of entities and
  value objects with one **root**, and it is the *consistency boundary*: every business
  invariant that must hold transactionally lives inside one aggregate, and each transaction
  touches one aggregate. Corollaries (Vaughn Vernon's classic rules): keep aggregates
  small, and **reference other aggregates by identifier** — holding a live object reference
  into another aggregate (or worse, another context) welds their transactions and their
  teams together.
- **Domain events** are facts, past tense, published by the aggregate when something worth
  telling the world happened. Downstream contexts react without the upstream knowing them.
- **Repositories** mimic a collection of aggregates — `save`, `findById` — not a query
  toolbox with forty derived finders.
- **jMolecules + ArchUnit** turn all of the above from convention into build failure:
  `@AggregateRoot`, `@ValueObject`, `@Identity`, `@DomainEvent` express the metamodel, and
  `JMoleculesDddRules.all()` verifies it — aggregates need identity, value objects may not
  reference entities, aggregates reference each other by id only:

  ```java
  JMoleculesDddRules.all().check(classes);        // the whole DDD metamodel
  noClasses().that().resideInAPackage("..enrollment..")
      .should().dependOnClassesThat().resideInAPackage("..catalog.internal..");
  ```

When NOT to do this: a context whose logic is "load it, edit it, save it" gains nothing
from aggregates and events — transaction script + records is the honest design there.
Tactical DDD earns its keep where invariants and state transitions are the complexity.

## The project

A training-course registration system — yes, the very kind of platform this curriculum
runs on. An event-storming session has already happened; the wall is transcribed in
**[docs/eventstorming.md](docs/eventstorming.md)** (read it in step 1). It identified four
subdomains — Enrollment, Course Catalog, Billing, Notifications — and this codebase
implements the enrollment slice of that map, badly, on purpose.

What's given:

```
src/main/java/dev/vlearning/registration/
├── RegistrationApplication.java
├── shared/CourseId.java                  ← shared-kernel id — a SHELL, no invariants yet
├── catalog/                              ← the neighboring context, done RIGHT (given)
│   ├── CourseCatalog.java                ← published interface (open host service)
│   ├── CourseInfo.java                   ← published language (@ValueObject record)
│   └── internal/                         ← Course aggregate, repository, seeder — private
├── enrollment/                           ← the anemic mess you will fix
│   ├── Enrollment.java                   ← public setters, primitives, a foreign @ManyToOne
│   │                                       into catalog.internal.Course; rich API stubbed
│   ├── EnrollmentService.java            ← ALL the rules live here (the fat service)
│   ├── EnrollmentRepository.java, EnrollmentController.java, EnrollmentResponse.java
│   ├── Email.java, SeatCount.java        ← value-object shells (checkpoint 2)
│   ├── EnrollmentConfirmed.java          ← event record, never published (checkpoint 4)
│   └── *Converter.java                   ← JPA plumbing, given — VOs map to plain columns
└── notifications/EnrollmentNotifier.java ← conformist listener, given; never fires yet
```

The catalog context is deliberately finished: it shows the target style (annotated
aggregate, no setters, published interface hiding `internal/`) — and it gives the anemic
enrollment code something real to violate. Seeded courses: `DDD-101` (12 seats),
`TDD-201` (8), `MOD-401` (3 — lesson 06, book early).

| Endpoint | Behavior |
|---|---|
| `POST /enrollments` | request enrollment `{courseCode, attendeeEmail, seats}` → 201 REQUESTED |
| `POST /enrollments/{id}/reserve-seat` | REQUESTED → SEAT_RESERVED (else 409) |
| `POST /enrollments/{id}/confirm` | SEAT_RESERVED → CONFIRMED (else 409) |
| `POST /enrollments/{id}/cancel` | → CANCELLED; confirmed ones are 409 (refunds = Billing) |
| `GET /enrollments/{id}` | fetch |

Run it:

```bash
mvn spring-boot:run
curl -s -X POST localhost:8080/enrollments -H 'Content-Type: application/json' \
     -d '{"courseCode":"DDD-101","attendeeEmail":"kriss@vaadin.com","seats":2}'
curl -s -X POST localhost:8080/enrollments/1/reserve-seat
curl -s -X POST localhost:8080/enrollments/1/confirm
```

Run the tests — **pristine checkout must be green** (12 behavior tests pass, 27 checkpoint
tests skipped):

```bash
mvn -q test
```

`EnrollmentApiBehaviorTest` pins the HTTP contract and stays enabled throughout. It knows
nothing about aggregates or events — if it goes red during the refactoring, you changed
behavior, not just the model.

## Guided steps

### Step 1 — Read the wall

**Goal:** work through [docs/eventstorming.md](docs/eventstorming.md) and classify the four
subdomains (core / supporting / generic) in the handout's empty table, with one sentence of
reasoning each. Then decide: which classification would change if this company's actual
product were *selling* courses built by others?

No code, no test. This is the step most real projects skip, and it is the one that decides
whether the rest is worth doing.

<details><summary>Answer — the classification and the why</summary>

- **Enrollment — core.** Seat reservation, waitlist policy, confirmation rules are where
  this business wins or loses customers; the rules are volatile (marketing invents a new
  cancellation policy every quarter) and domain-specific. That volatility × complexity is
  Khononov's core-subdomain signature — and it is why this lesson spends five checkpoints
  on exactly this context.
- **Course Catalog — supporting.** Necessary, owned in-house, but essentially structured
  data with lookups. No competitor is beaten by a fancier catalog. Keep it simple and
  stable — which is why its given implementation is small and boring.
- **Billing — supporting** (arguable, and the argument is the point). Invoicing rules are
  ours, but nothing differentiates us; if the handout's pricing were commodity, buy it and
  it drifts generic. Reasonable people land on either side — what matters is that the
  *reasoning* is about differentiation and volatility, not about technical interest.
- **Notifications — generic.** Sending email is a solved problem. Any effort beyond a thin
  conformist adapter over a provider is wasted — which is exactly how the code treats it.

If the product were reselling third-party courses, catalog ingestion/curation would become
core and enrollment would drift toward supporting: subdomain types are a property of the
*business model*, not of the code.
</details>

**Done when:** your table is filled in and your answers survive contact with the collapsible.

### Step 2 — Value objects: make illegal states unrepresentable

**Goal:** turn the three shells — `Email`, `SeatCount` (in `enrollment/`), `CourseId` (in
`shared/`) — into real value objects, and switch the `Enrollment` entity's email and seats
fields over to them.

Enable `Checkpoint2ValueObjectsTest` (remove `@Disabled`). It specifies the policies:
emails validate against a sane pattern and normalize (trim + lowercase — equality must mean
*same mailbox*); seat counts are 1–20; course codes look like `DDD-101` and normalize to
uppercase. Enforce everything in **compact constructors**, annotate all three with
jMolecules `@ValueObject`, then change the field types on `Enrollment` (setters stay for
now — one smell at a time) and let `EnrollmentService` construct the VOs. Delete the
service's inline email/seats `if`-checks as you go: that scattered validation is exactly
what the value objects replace. The JPA side is already handled — the given
`AttributeConverter`s map each VO to the same plain column as before.

<details><summary>Hint — compact constructors that normalize AND validate</summary>

```java
public CourseId {
    if (value == null) throw new IllegalArgumentException("a course id needs a value");
    value = value.trim().toUpperCase();               // normalize first…
    if (!Pattern.matches("^[A-Z]{2,5}-\\d{3}$", value))
        throw new IllegalArgumentException("not a course code: " + value);  // …then validate
}
```

Reassigning the parameter inside a compact constructor rewrites what the record stores.
Normalizing *inside* the VO is the difference between "a string that was checked once" and
"a type that cannot hold garbage".
</details>

**Done when:** checkpoint 2 and all behavior tests are green. Notice which behavior test
just got a second guardian: `attendeeEmailIsNormalizedOnTheWayIn` used to depend on a
`trim().toLowerCase()` someone remembered to call in the service.

### Step 3 — The rich aggregate: move the rules home

**Goal:** move the lifecycle rules from `EnrollmentService` into `Enrollment`, kill the
setters, and swap the foreign `Course` reference for a `CourseId`.

Enable `Checkpoint3RichAggregateTest` — plain JUnit, no Spring, which is itself the lesson:
a domain model you can only test with a container is not a domain model. The target API is
already stubbed on the entity: implement `request(...)` (static factory → `REQUESTED`),
`reserveSeat()`, `confirm()` (the invariant: **no confirmation without a reserved seat**),
and `cancel()` (confirmed ⇒ refuse — refunds are Billing's job). Then:

- replace `@ManyToOne Course course` with `private CourseId courseId` (the given converter
  maps it onto the same `course_code` column),
- annotate the class `@AggregateRoot` and the id field `@Identity`,
- delete every setter and the public use of the no-arg constructor,
- shrink each service method to *load → call the domain → save*, and fix
  `EnrollmentResponse.from(...)`.

The capacity check (`seats > course.capacity()`) stays in the service — it compares data
from two different contexts, so it is orchestration, not an aggregate invariant. Worth a
pause: not every rule belongs inside the aggregate, only the ones the aggregate can
guarantee with its own state.

<details><summary>Hint — what the service methods shrink to</summary>

```java
public Enrollment confirm(long enrollmentId) {
    Enrollment enrollment = load(enrollmentId);
    enrollment.confirm();                     // the rule lives in the aggregate now
    return enrollments.save(enrollment);
}
```

And the aggregate method it calls:

```java
public void confirm() {
    if (status != EnrollmentStatus.SEAT_RESERVED)
        throw new IllegalStateException(
            "cannot confirm without a reserved seat, this enrollment is " + status);
    status = EnrollmentStatus.CONFIRMED;
}
```

Keep the `protected Enrollment()` constructor — JPA needs it. That wart is the price of
mapping the aggregate directly; jMolecules' ByteBuddy integration can generate it if the
wart offends you (stretch goal).
</details>

**Done when:** checkpoints 2–3 and all behavior tests are green, and `EnrollmentService`
contains no `if` about enrollment state.

### Step 4 — Domain events: tell the world, know nobody

**Goal:** `confirm()` registers an `EnrollmentConfirmed` domain event; saving the aggregate
publishes it; the notifications context finally reacts.

Enable `Checkpoint4DomainEventsTest`. Annotate the `EnrollmentConfirmed` record with
jMolecules `@DomainEvent`, have `Enrollment` extend Spring Data's
`AbstractAggregateRoot<Enrollment>`, and call `registerEvent(...)` inside `confirm()` —
the aggregate *decides* the fact, the infrastructure *transports* it. On
`repository.save(...)`, Spring Data publishes registered events as application events; the
given `EnrollmentNotifier` (a `@Component` in the notifications context that has been
listening to silence since the project started) picks it up.

<details><summary>Hint — registration and the save gotcha</summary>

```java
public void confirm() {
    ...
    status = EnrollmentStatus.CONFIRMED;
    registerEvent(new EnrollmentConfirmed(id, courseId, attendeeEmail));
}
```

Events are published when the aggregate passes through `save(...)` — JPA dirty-checking
alone flushes the UPDATE but publishes nothing. The service's `load → call → save` shape
from step 3 already does the right thing.

Production honesty: a plain `@EventListener` runs synchronously in the same transaction —
if the mail server explodes, the confirmation rolls back. `@TransactionalEventListener`,
and Spring Modulith's event publication registry (with outbox semantics), are the grown-up
answers — that is lesson 06's territory.
</details>

**Done when:** checkpoint 4 is green and you can see the notifier's log line when you run
the curl sequence above.

### Step 5 — Reach the neighbor through the front door

**Goal:** the enrollment context stops touching `catalog.internal` entirely.

Enable `Checkpoint5IdReferencesTest`. One of its two rules fails immediately:
`EnrollmentService` still injects the catalog's *internal* `CourseRepository` — a leftover
from when the entity needed a `Course` object to wire the `@ManyToOne`. Read the ArchUnit
failure list: every line is a place where one context has its hand inside another's model.
Replace the repository with the published `CourseCatalog` interface: look up
`Optional<CourseInfo>` by `CourseId`, take the capacity from the `CourseInfo` snapshot.

The other rule — jMolecules' `aggregateReferencesShouldBeViaIdOrAssociation()` — passes
already, because step 3 swapped the object reference for `CourseId`. It is not decoration:
it is what fails on the day someone reintroduces a direct reference (you will prove that in
step 6).

**Done when:** checkpoint 5 is green and `EnrollmentService`'s imports contain nothing from
`catalog.internal`.

### Step 6 — Enforce it, then break it on purpose

**Goal:** turn the whole model into a build failure waiting for an offender.

Enable `Checkpoint6ArchitectureRulesTest`. If steps 2–5 are done, everything passes on
enable: `JMoleculesDddRules.all()` (aggregates have identity, VOs don't hold entities,
references by id) plus the context boundaries (nobody but catalog touches
`catalog.internal`; upstream catalog knows no consumers; enrollment doesn't know who
listens; the notifications conformist consumes only published facts — `@DomainEvent`s and
their `@ValueObject`s, never services).

Now the important part — **violate it deliberately**, one at a time, and read each failure:

1. Put `private Course course;` back into `Enrollment` → three different tests object at
   once (the metamodel, the internals rule, and the catalog-privacy rule), each naming the
   field.
2. Make `EnrollmentNotifier` inject `EnrollmentService` for a "quick status check" → the
   conformist just grew hands; `theConformistConsumesPublishedFactsNotServices` names the
   dependency and its `because(...)` explains what the relationship silently became.
3. Remove `@Identity` from the id field → the metamodel rule demands an identity:
   *"Type Enrollment must declare a field or a method annotated with @Identity!"*

Revert everything. The point of the exercise is the failure *messages*: in CI they are the
difference between architecture-as-diagram and architecture-as-fact — the model outlives
the code review where everyone still remembered why.

**Done when:** you have seen all three failures, reverted them, and `mvn -q test` is fully
green with every checkpoint enabled.

### Step 7 — Context-map debrief: strategy is the value

**Goal:** connect each relationship on the context map to a concrete artifact in this
codebase, and articulate what was strategic vs tactical about this lesson. In writing, a
few sentences each:

1. **Open host service / published language** — where in the code? What would break for
   whom if `CourseInfo` gained a field vs. if `Course` did?
2. **Customer–supplier** — Billing doesn't exist in this codebase. What, concretely, is the
   contract Enrollment already committed to on its behalf? What team conversation happens
   before that contract changes?
3. **Conformist** — where does Notifications conform, and why is a translation layer
   (ACL) *not* worth building there? When would you flip that decision?
4. **Anticorruption layer** — the LegacyHR sketch in the handout. Which side of the ACL
   speaks `TRAINING_ENTITLEMENT_FLAG='Y'`, which side speaks `Email` — and inside which
   context does the adapter live?
5. Per Khononov: name one thing in this lesson that was *strategic* design and one that was
   *tactical* — and which of the two would have saved a project that modeled enrollment and
   billing as one context with perfect aggregates.

<details><summary>Model answers</summary>

1. `CourseCatalog` + `CourseInfo` are the OHS and published language. A new field on
   `CourseInfo` is an *additive published-language change* — coordinate with consumers
   (enrollment, tomorrow others). A new field on `Course` is nobody's business: that is
   the entire value of the `internal` package plus the rule guarding it.
2. The `EnrollmentConfirmed` record *is* the contract: id, `CourseId`, `Email`. Changing
   or removing a field is a supplier breaking a negotiated agreement — the customer
   (Billing team) is consulted first; adding a field is usually safe. That the contract is
   a Java record today and might be a Kafka schema in lesson 07 changes the transport, not
   the relationship.
3. `EnrollmentNotifier` consumes the event type directly — enrollment's model, take it or
   leave it. Right-sized because notifications is generic: translation effort would buy
   nothing. Flip to an ACL the day notifications grows real rules of its own (digest
   batching, preference centers) that enrollment's event shape starts to distort.
4. The adapter speaks SOAP-flag on the outside and something like
   `employerPaysFor(Email): boolean` on the inside; it lives in the *enrollment* context
   (the downstream protects itself — an ACL is the customer's shield, not the supplier's
   favor).
5. Strategic: the subdomain classification, the pivotal-event boundaries, deciding
   notifications deserves conformism. Tactical: records, aggregate, events, ArchUnit.
   The strategic half saves the merged-context project; perfect aggregates inside a wrong
   boundary just make the wrong model harder to change. Tactics are how a good boundary
   *stays* good; strategy is what makes it good.
</details>

**Done when:** your five answers hold up against the collapsible — and you can say what
this lesson did NOT enforce (module boundaries as a first-class concept, event delivery
guarantees) — which is precisely where project 06, the Spring Modulith modular monolith,
picks up.

## Self-check

1. Subdomain vs bounded context — problem space or solution space? Why can one subdomain
   end up served by two contexts, or one context span two subdomains (and which of those
   two is usually the mistake)?
2. What made `EnrollmentConfirmed` a *pivotal* event on the storming wall, and what is the
   relationship between pivotal events and context boundaries?
3. Recite the aggregate rules this lesson enforced mechanically: what does
   `aggregateReferencesShouldBeViaIdOrAssociation` protect you from, in transactional
   terms?
4. Why did the capacity check stay in `EnrollmentService` when every other rule moved into
   the aggregate? What would it take to make "no overbooking across all enrollments" a
   true invariant, and what would it cost?
5. The `Email` record normalizes in its constructor. Argue why this beats a
   `EmailValidator.isValid(String)` utility — in terms of where invalid data can *exist*.
6. Customer–supplier vs conformist: both are upstream/downstream. What exactly is
   different, and who decided which one Notifications got?
7. Your teammate annotates a `@Service` with `@AggregateRoot` "because it's important".
   Which checkpoint-6 rule fails, and what is the one-sentence explanation of why the
   annotation is not a badge of honor?
8. When is this whole apparatus — aggregates, VOs, events, enforcement — the wrong tool?
   Name the signal you'd look for in a context's code before deciding.

## Stretch goals

- **Waitlist as a second aggregate.** Add `WaitlistJoined` / `SeatReleased` from the
  storming wall: `cancel()` registers `SeatReleased`, a policy listener offers the seat to
  a `Waitlist` aggregate — by id reference only, with checkpoint 6 kept green. Feel how the
  one-aggregate-per-transaction rule forces the policy to be eventually consistent.
- **Build the LegacyHR ACL.** A fake `LegacyHrClient` returning
  `EMP_REC{TRAINING_ENTITLEMENT_FLAG:'Y'/'N'/'P'/' '}` and an adapter exposing
  `employerPaysFor(Email)` to enrollment. Write the ArchUnit rule that keeps
  `LegacyHr*` types out of the domain packages.
- **Types instead of annotations.** jMolecules has a type-based model —
  `implements AggregateRoot<Enrollment, EnrollmentId>`, `Association<Course, CourseId>`.
  Convert `Enrollment` and compare: what does the compiler now catch that ArchUnit caught
  before, and what did it cost in signature noise?
- **Kill the JPA wart.** Add the jMolecules ByteBuddy plugin so the protected no-arg
  constructor and JPA annotations are derived, keeping the aggregate source clean. Measure
  the build-complexity price and decide whether you'd pay it on a team.

## Resources

- Eric Evans — *Domain-Driven Design: Tackling Complexity in the Heart of Software*
  (2003) — the source; read part IV (strategic design) even if you skim the patterns.
- Vlad Khononov — *Learning Domain-Driven Design* (O'Reilly, 2021) — the best current
  on-ramp; chapters 1–4 and 8–10 map directly onto this lesson.
- Vlad Khononov — *Balancing Coupling in Software Design* (Addison-Wesley, 2024) — the
  modern generalization: integration strength × distance × volatility.
- Nick Tune & Jean-Georges Perrin — *Architecture Modernization* (Manning, 2024) —
  strategic DDD + EventStorming + Team Topologies at portfolio scale.
- Alberto Brandolini — *Introducing EventStorming* (Leanpub) and
  [eventstorming.com](https://www.eventstorming.com/) — from the workshop's inventor.
- Stefan Hofer & Henning Schwentner — *Domain Storytelling* (2021) — the companion
  discovery technique when stickies aren't enough.
- [xsreality/spring-modulith-with-ddd](https://github.com/xsreality/spring-modulith-with-ddd)
  — reference implementation of exactly this stack, one step ahead (Modulith — lesson 06).
- [jMolecules](https://github.com/xmolecules/jmolecules) — the annotations, the type-based
  model, and the ArchUnit/ByteBuddy integrations used here.
- Research notes: [methodologies.md, section 2](../docs/research/methodologies.md) and
  [architecture-styles.md, section 2](../docs/research/architecture-styles.md).

---

*Build note (verified on this machine): jMolecules resolves via the current GA BOM
`org.jmolecules:jmolecules-bom:2025.0.2`, which pins `jmolecules-ddd`/`jmolecules-events`
**2.0.1** and `jmolecules-archunit` **0.33.0** — the research notes' "1.9.0 era" has moved
on to the 2.0.x line, with unchanged annotation packages (`org.jmolecules.ddd.annotation`,
`org.jmolecules.event.annotation`), so no code differences. ArchUnit is pinned to 1.5.0 and
the jMolecules integration converges on it. Boot 4 modularized test support applies as in
lesson 03: MockMvc comes from `spring-boot-starter-webmvc-test` with `AutoConfigureMockMvc`
in `org.springframework.boot.webmvc.test.autoconfigure`. No other deviations from the
conventions' pins.*
