# 03 — Vertical Slices: Refactor Kata, Layers → Slices

> After this lesson you can take a package-by-layer Spring Boot app, refactor it into
> self-contained feature slices without breaking behavior, and lock the structure in
> with an ArchUnit fitness function.

## Why this matters (2026)

Vertical Slice Architecture (VSA) is one of the most discussed styles of 2025–2026. Coined by
Jimmy Bogard in the .NET world (~2018), it has migrated firmly into Java and is increasingly
presented as *the pragmatic answer to Clean Architecture fatigue*: instead of spreading one
feature across `controllers/`, `services/`, `repositories/` and `dto/`, you organize code by
the thing that actually changes together — the feature. Package-by-feature is by now
near-universal advice in the Java community over package-by-layer, and in larger systems VSA
combines naturally with Spring Modulith (modules as coarse boundaries, slices inside them).

Carry the honest framing, though: slices are **not a silver bullet**. Oskar Dudycz's critique
(see [Resources](#resources)) points out how quickly "vertical slices" degrades into "the same
coupled code, now in feature-named folders" — semantic diffusion. This kata is designed to make
both the benefit *and* the failure modes felt. Source material:
[docs/research/architecture-styles.md](../docs/research/architecture-styles.md), section 4.

## Core concepts

**Package-by-layer** groups code by technical role. One feature — "create task" — is smeared
across every package; one package — `service/` — is touched by every feature. The result over
time is the *fat service*: a class that knows everything and that every change flows through.

**Package-by-feature / vertical slice** groups code by use case. A slice cuts from the HTTP
endpoint down to the database and contains everything that use case needs:

```
features/createtask/
├── CreateTaskController.java    // the endpoint
├── CreateTaskRequest.java       // record
├── CreateTaskResponse.java      // record
└── CreateTaskRepository.java    // narrow: interface … extends Repository<Task, Long> { Task save(Task t); }
```

The design rule (Bogard): **minimize coupling between slices, maximize coupling within a
slice.** Corollaries:

- **No shared service classes.** A slice's logic lives in a plain Spring bean (or right in the
  controller when it is three lines). In .NET this role is played by MediatR handlers; the
  common Java idiom is simply one bean per use case — no mediator library needed.
- **Each slice chooses its own internal sophistication.** One slice is a transaction script,
  another uses a rich domain method, a read-only report slice may skip JPA entirely and run
  SQL through `JdbcClient` into a projection record. Consistency *across* slices is explicitly
  not a goal; fitness for the one use case is.
- **Duplication is tolerated — up to a point.** Two slices each defining a small response
  record is fine (they will evolve independently). Two slices copy-pasting business rules is
  not — that's a signal the rule belongs in a shared domain concept.
- **Something always stays shared.** The JPA entity, cross-cutting web config, exception
  mapping. Keeping that set small and *boring* is the discipline; watching it grow is the
  early-warning signal.
- **Structure is enforced, not hoped for.** ArchUnit's slice rules turn the packaging
  convention into a build failure:

```java
SlicesRuleDefinition.slices()
        .matching("..features.(*)..")
        .should().notDependOnEachOther()
        .check(classes);
```

## The project

A small task-management REST API, deliberately written in classic package-by-layer style.
The smells are intentional — `TaskService` does creation, completion, assignment *and*
reporting; four DTOs live in a `dto/` dumping ground; one controller owns every route.

```
src/main/java/dev/vlearning/tasks/
├── TasksApplication.java
├── model/Task.java                      ← JPA entity (status, assignee, due date)
├── repository/TaskRepository.java       ← Spring Data JPA
├── service/TaskService.java             ← the fat service: all four use cases
├── service/TaskNotFoundException.java
├── dto/                                 ← CreateTaskRequest, AssignTaskRequest,
│                                          TaskResponse, OverdueReportResponse
└── web/TaskController.java              ← all endpoints
    web/ApiExceptionHandler.java         ← IllegalArgumentException → 400
```

| Endpoint | Behavior |
|---|---|
| `POST /tasks` | create a task → 201 |
| `POST /tasks/{id}/complete` | mark DONE, stamp `completedAt` |
| `POST /tasks/{id}/assign` | set assignee |
| `GET /reports/overdue` | open tasks past their due date, with `daysOverdue` |

The database is in-memory H2 — deliberately, because persistence is irrelevant to this
lesson's topic.

Run it:

```bash
mvn spring-boot:run
curl -s -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
     -d '{"title":"Try the API","dueDate":"2026-08-01"}'
curl -s localhost:8080/reports/overdue
```

Run the tests — **pristine checkout must be green** (7 behavior tests pass, 7 checkpoint
tests skipped):

```bash
mvn -q test
```

`TaskApiBehaviorTest` pins the HTTP contract and stays enabled the whole time. It knows
nothing about packages or classes — that is exactly what makes it a refactoring safety net.
If it ever goes red during this kata, you changed behavior, not just structure.

## Guided steps

### Step 1 — Feel the pain

**Goal:** implement "add comment to task" in the *existing layered style*, and count the cost.

Enable `Checkpoint1AddCommentTest` (remove `@Disabled`). Make it green the way the current
codebase wants you to: entity in `model/`, repository in `repository/`, logic in
`TaskService`, records in `dto/`, endpoints in `TaskController`.

While you work, keep a tally of **every package you touch**. Write the number down — you will
need it in step 4.

<details><summary>Hint</summary>

Keep `Comment` simple: `id`, `taskId` (a plain `Long` column, no `@ManyToOne` needed),
`author`, `text`. A derived query `findByTaskIdOrderByIdAsc` gives you insertion order.
Remember to 404 when the task does not exist — `TaskService.load(id)` already does that.
</details>

**Done when:** checkpoint 1 is green, all behavior tests still green, and you have your
package count (expect 5 main packages plus the test — that number *is* the lesson).

### Step 2 — Carve the first slice

**Goal:** move task creation into `features/createtask/`, leaving everything else untouched.

Create `dev.vlearning.tasks.features.createtask` containing:

- `CreateTaskController` — owns `POST /tasks`, delete the method from `TaskController`
- `CreateTaskRequest` / `CreateTaskResponse` — the slice's own records (yes, even though
  `dto/TaskResponse` still exists for the other endpoints — that duplication is deliberate
  and temporary)
- a **narrow repository**: either a slice-local Spring Data interface or direct
  `EntityManager` usage. The point: this slice can persist a `Task`, nothing more.

Then delete `createTask` from `TaskService` and `CreateTaskRequest` from `dto/`.

<details><summary>Hint — the narrow repository</summary>

Spring Data happily creates several repositories for the same entity. A slice-scoped one
exposes only what the slice needs:

```java
interface CreateTaskRepository extends Repository<Task, Long> {
    Task save(Task task);
}
```

Extending `Repository` (not `JpaRepository`) means you inherit *nothing* you didn't ask for.
The alternative — inject `EntityManager`, call `persist` — is equally valid and even more
explicit. Pick one; there is no "layer" telling you which.
</details>

**Done when:** all enabled tests are still green and no class outside `features/createtask`
mentions task creation.

### Step 3 — Slice the rest

**Goal:** `features/completetask/`, `features/assigntask/`, `features/addcomment/`,
`features/overduereport/` — and at the end, **delete `TaskService`, `TaskController`, and the
`dto/` package entirely**. `git rm` (or just delete) — the fat service does not get to
survive as an empty husk.

Two design decisions you must make along the way:

1. **The report slice is different — let it be.** It is read-only and needs no entity.
   Try `JdbcClient` (already on the classpath via `spring-boot-starter-data-jpa`) mapping
   straight into a projection record. One slice doing raw SQL while its neighbor uses JPA is
   not inconsistency — it is the point: each slice chooses its own sophistication.
2. **Something needs a shared home.** `Task` (used by several slices), the exception mapping,
   `TaskNotFoundException`. Give them a small `shared/` package (or keep `model/`). Notice
   how *little* ends up there — and notice that `Comment` is NOT shared: only the addcomment
   slice knows it exists, so it moves inside that slice.

<details><summary>Hint — the report slice with JdbcClient</summary>

```java
@RestController
class OverdueReportController {
    private final JdbcClient jdbc;
    OverdueReportController(JdbcClient jdbc) { this.jdbc = jdbc; }

    record OverdueRow(Long id, String title, String assignee, LocalDate dueDate, long daysOverdue) {}

    @GetMapping("/reports/overdue")
    OverdueReport report() {
        List<OverdueRow> rows = jdbc.sql("""
                SELECT id, title, assignee, due_date,
                       DATEDIFF(DAY, due_date, CURRENT_DATE) AS days_overdue
                FROM task WHERE status = 'OPEN' AND due_date < CURRENT_DATE
                ORDER BY due_date""")
                .query((rs, i) -> new OverdueRow(rs.getLong("id"), rs.getString("title"),
                        rs.getString("assignee"), rs.getDate("due_date").toLocalDate(),
                        rs.getLong("days_overdue")))
                .list();
        return new OverdueReport(rows.size(), rows);
    }
    record OverdueReport(int totalOverdue, List<OverdueRow> tasks) {}
}
```

(`DATEDIFF` is H2 syntax — a reminder that dropping below JPA costs you portability. Every
sophistication level has a price tag.)
</details>

**Done when:** `service/`, `web/`, `dto/`, and `repository/` no longer exist; every feature
lives under `features/`; `mvn -q test` is green.

### Step 4 — The payoff: next feature, one directory

**Goal:** implement "reopen task" touching **exactly one new directory**.

Enable `Checkpoint4ReopenTaskTest`. Build `features/reopentask/` — controller, handler logic,
narrow data access, response record — and nothing anywhere else.

Now compare: the package count from step 1 versus this. That difference is the entire
maintainability argument of this lesson, measured on your own code.

**Done when:** checkpoint 4 is green and the only new source directory is
`features/reopentask/`.

### Step 5 — Enforce it

**Goal:** turn the convention into a build failure.

Enable `Checkpoint5SliceIndependenceTest`. It should pass immediately. (If you enable it
before `features/` exists, ArchUnit refuses to vacuously pass — `failOnEmptyShould` — which
is itself a lesson: a fitness function that can silently check nothing is worthless.)

Then **break it on purpose**: import a class from one slice into another — e.g. reuse
createtask's response record inside reopentask — and run the test. Read the failure message
carefully: `Slice reopentask depends on Slice createtask`. That message, in CI, is what keeps
this architecture alive after you stop paying attention. Revert the import.

**Done when:** you have seen the rule fail with a cross-slice dependency, reverted it, and
everything is green.

### Step 6 — Debrief: what stayed shared, and when NOT to do this

No code. Answer these in writing (a few sentences each) — this is where the lesson earns the
"not a silver bullet" framing:

1. List everything in your shared package(s). For each item: is it shared because it is
   genuinely cross-cutting (`ApiExceptionHandler`), or because two slices need the same
   *data* (`Task`)? The second kind is coupling wearing a trench coat — every slice touching
   the `Task` entity is still coupled through the schema.
2. Dudycz's critique: if a teammate now adds `features/common/` and starts moving "useful
   helpers" there, what have you got? (Layers again — rotated 90 degrees.) What rule or
   review habit prevents it?
3. When would you *not* do this? Consider: a 6-endpoint CRUD app where every slice would be
   an identical copy-paste; a domain with heavy invariants spanning many use cases (a rich
   domain core with ports & adapters may serve better — lesson 04 territory); a team that
   already navigates by layer fluently.
4. Where does this scale to next? Slices inside Spring Modulith modules: modules draw the
   coarse business boundaries, slices organize inside them (lesson on moduliths).

**Done when:** you can argue *for* layers in one scenario and *for* slices in another,
concretely.

## Self-check

1. What belongs inside one vertical slice, and what is the coupling rule that defines the
   style in one sentence?
2. Why is duplicating small request/response records between slices acceptable, while
   duplicating a validation rule is a smell? What is the difference in kind?
3. Your `shared/` package after step 3 is tiny. What forces will grow it over the next year,
   and which of them should you resist?
4. The report slice uses raw SQL while createtask uses JPA. Defend this inconsistency to a
   reviewer who calls it "messy".
5. What exactly does `slices().matching("..features.(*)..").should().notDependOnEachOther()`
   check, and what does it *not* check (hint: the shared entity)?
6. Why did the "reopen task" feature touch one directory while "add comment" touched five?
   What property of the codebase changed between the two?
7. How do vertical slices relate to Spring Modulith modules — competitors or complements?
8. Name one concrete situation where package-by-layer remains the reasonable choice.

## Stretch goals

- **CQRS-lite the report.** Give `overduereport` its own denormalized read table, updated by
  listening to Spring application events published by the other slices. Now the report slice
  no longer shares even the schema — measure what that buys and costs.
- **Single-file slices.** Collapse one slice into a single `@RestController` file with nested
  records and the handler logic inline. Where is the line between "gloriously cohesive" and
  "a god file"?
- **Slices inside a module.** Add Spring Modulith, make `features` verify as application
  modules (`ApplicationModules.verify()`), and compare its violations report with your
  ArchUnit rule.
- **Measure change coupling.** If you commit per step, script `git log --name-only` to count
  directories touched per commit before and after the refactoring — the kata's argument as a
  number.

## Resources

- Jimmy Bogard — [Vertical Slice Architecture](https://www.jimmybogard.com/vertical-slice-architecture/)
  and his [NDC talk](https://www.youtube.com/watch?v=oAoaMlS1PWo) — the origin of the style.
- Oskar Dudycz — [My thoughts on Vertical Slices, CQRS, Semantic Diffusion and other fancy words](https://www.architecture-weekly.com/p/my-thoughts-on-vertical-slices-cqrs)
  — the best critical take; read after step 6.
- javathinking.com — [Vertical Slice Architecture: A Modern Approach to Feature-Centric Software Design](https://www.javathinking.com/blog/vertical-slice-architecture/)
  — Java-specific treatment.
- Milan Jovanovic — [Vertical Slice Architecture](https://www.milanjovanovic.tech/blog/vertical-slice-architecture)
  — excellent structure; .NET examples that port directly to Spring.
- Simon Brown — "Package by component" (chapter 34 of *Clean Architecture*) and his
  [Modular Monoliths talk](https://www.youtube.com/watch?v=5OjqD-ow8GE) — the packaging
  spectrum from layers to features to components.
- Research notes: [Backend Architecture Styles, section 4](../docs/research/architecture-styles.md).

---

*Build note (Boot 4): MockMvc test support was modularized — this project adds
`spring-boot-starter-webmvc-test` and imports `AutoConfigureMockMvc` from
`org.springframework.boot.webmvc.test.autoconfigure` (the Boot 3 package
`…boot.test.autoconfigure.web.servlet` no longer exists). All version pins from the
conventions resolved as-is; no deviations.*
