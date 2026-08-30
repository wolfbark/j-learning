# 02 — TDD: One Kata, Two Schools, Then Break It

> After this lesson you can run a disciplined Canon TDD loop, choose deliberately between
> classicist and London-school styles, audit a test suite's real strength with mutation
> testing, and use failing tests to steer an AI assistant instead of trusting its output.

## Why this matters (2026)

TDD is having a genuine resurgence — driven by AI coding agents, not despite them. The
industry data circulating through 2025–26 (DORA-style findings) shows a 25% increase in
AI-assist usage correlating with a ~7% *decrease* in delivery stability for teams without
disciplined testing. Tests have become the primary steering and verification mechanism for
AI-generated code, and the documented failure modes of unguided agents read like an
anti-TDD checklist: skipping the red phase, writing tests after the code that merely
confirm whatever it does, and deleting failing tests instead of fixing implementations.
Emerging practice separates the *test author* role from the *implementation* role — human
writes tests, agent implements — which only works if you can write tests that drive
design. That is this lesson.

The second reason is older: the classicist (Detroit/Chicago, state-based) versus mockist
(London, interaction-based) split is still the standard teaching frame, and the modern
consensus — heavily shaped by Khorikov's book — leans **classicist by default**: mock only
at architectural boundaries you don't own, because over-mocked suites couple tests to
implementation details and rot fast. AI-generated tests amplify exactly that rot. To be
fair to London: outside-in with mocks is not wrong, it is a different bet — it buys you
early interface discovery and protocol-level design pressure, at the price of tests that
know how your objects talk. You will feel both trade-offs in your own fingers here,
which beats taking anyone's word for it.

Full research notes: [`../docs/research/methodologies.md`](../docs/research/methodologies.md), section 1.

## Core concepts

### Canon TDD (Beck, 2023)

Kent Beck restated the original definition because so much of what gets criticized as
"TDD" isn't. The canonical loop:

1. Write a **list of test scenarios** (words, not code — see `TESTLIST.md`).
2. Turn **exactly one** into a concrete, failing test. One. Never a second while red.
3. Make it pass **without breaking others**. Faking is legal.
4. **Optionally** refactor — the step exists every cycle, taking it is a judgment call.
5. Repeat until the list is empty (it grows as the code teaches you; that's healthy).

What Canon TDD never prescribed: writing all tests up front, 100% coverage as a goal,
tests for getters, or never designing before coding. Criticism of those is criticism of
a strawman.

### The three green moves (from *TDD By Example*)

- **Fake it** — return the constant the test wants. `return "INSERT COIN";` is a
  legitimate green. The next test forces the generalization.
- **Obvious implementation** — when the real code is a one-liner you're sure of, just
  write it.
- **Triangulate** — only generalize when a second example demands it. If you don't know
  what the general code should be, add the test that tells you.

### Two schools

| | Classicist (Detroit/Chicago) | London (mockist, outside-in) |
|---|---|---|
| Root text | Beck, *TDD By Example* (2002) | Freeman & Pryce, *GOOS* (2009) |
| Direction | Inside-out: start with domain logic | Outside-in: start at the use-case boundary |
| Verifies | End state (`assertThat(x).isEqualTo(...)`) | Conversations (`verify(collaborator).did(...)`) |
| Test doubles | Only where the real thing is awkward (I/O, clock) | Mocks for every not-yet-existing collaborator |
| Design feedback | Hard-to-set-up test → too much coupling | Awkward mock conversation → wrong interface |
| Failure locality | One defect can fail many tests (fine — they're fast) | Failures point at one unit |
| Signature risk | Under-specified interfaces between parts | Tests welded to implementation detail |

### What to mock (Khorikov's synthesis)

A good unit test has four pillars: protection against regressions, **resistance to
refactoring**, fast feedback, maintainability. The second pillar is the one mocks
threaten: a test should fail only when *observable behavior* changes, never because you
reorganized internals. Khorikov's rule of thumb: mock **unmanaged dependencies at the
edges of your system** — things you don't own, whose interactions are part of your
contract with the outside world. Never mock domain logic you own. In this kata the
outside-in collaborators are hardware (coin validator, dispenser, display) — legitimate
boundaries. A `ChangeCalculator` would not be: that's your algorithm, test it by state.

```java
// Contract-level: fails only if the observable outcome changes
verify(coinReturn).reject(slug);

// Implementation-level: fails when you reorder, retry, or add a benign call
InOrder order = inOrder(validator, coinReturn);
order.verify(validator, times(1)).classify(slug);
verifyNoMoreInteractions(validator, catalog, dispenser, coinReturn, display);
```

### Mutation testing

Line coverage proves your tests *execute* code, not that they *check* it — delete every
assertion and coverage stays identical. PIT seeds small defects (mutants) into your
bytecode — negated conditionals, changed returns, removed calls — and reruns your tests
against each. A test that fails **kills** the mutant; a green suite over mutated code
means a **survivor**: a defect your suite would wave through to production. Two numbers
matter: *mutation score* (killed / all mutants) and *test strength* (killed / mutants
that were at least covered). In 2026 PIT doubles as an audit tool for AI-written tests —
assertion-free "confirmation" tests are exactly what it exposes.

## The project

One kata, implemented twice, then attacked. The domain is a **vending machine**: insert
coins, select a product, get it dispensed with correct change, handle sold-out slots and
an exact-change-only mode. Prices in integer cents (COLA $1.00, CHIPS $0.65, CANDY
$0.50); the shared display contract (message strings, one-shot semantics, the
exact-change house rule) lives at the top of [`TESTLIST.md`](TESTLIST.md) — both rounds
implement the same contract, which is what makes the resulting designs comparable.

### What's given

```
TESTLIST.md                          ← the Canon TDD scenario list (yours to extend)
src/main/java/com/vlearning/tdd/
  classicist/                        ← round 1+2: Coin, Product, VendingMachine
  outsidein/                         ← round 3: ports and a controller shell
src/test/java/com/vlearning/tdd/
  classicist/
    VendingMachineTest               ← your working suite; first cycle done (passing)
    Checkpoint1CoreVendingTest       ← @Disabled acceptance gate for step 1
    Checkpoint2ChangeAndEdgeCasesTest← @Disabled acceptance gate for step 2
  outsidein/
    VendingControllerTest            ← your working suite; first interaction done (passing)
    CoinRejectionProtocolTest        ← a second given test (passing — for now)
    Checkpoint3OutsideInAcceptanceTest ← @Disabled acceptance gate for step 3
```

- **`classicist`** is empty apart from the first worked red-green cycle:
  `VendingMachine.display()` returns a faked `"INSERT COIN"` and every other method
  throws `UnsupportedOperationException`. The method *signatures* exist only because the
  acceptance checkpoints must compile — treat the API as the requirement spec and the
  bodies as yours to drive test-first.
- **`outsidein`** gives you the seams the London school needs before any of them exist
  for real: the driving port `VendingUseCase`, collaborator interfaces `CoinValidator`,
  `ProductCatalog`, `Dispenser`, `CoinReturn`, `Display`, and a `VendingController` with
  exactly one collaboration implemented (coin rejection).
- Because this is a TDD lesson, the checkpoints are the **only** pre-written tests for
  your production code. They are acceptance gates, not a unit suite — the tests that
  drive your design are the ones you write.
- PIT is fully configured in the `pom.xml` (pitest + JUnit 5/6 plugin), and Mockito is
  loaded as a Java agent by Surefire — the clean setup for inline mocking on JDK 21+.

### How to run it

```bash
mvn -q -B test                                          # green on checkout: 3 pass, 16 skipped
mvn -q -B test org.pitest:pitest-maven:mutationCoverage # mutation testing
open target/pit-reports/index.html                      # the PIT report
```

Maven on this machine already runs on JDK 25 (`mvn -version`); no `JAVA_HOME` fiddling
needed for the build.

## Guided steps

### Step 1 — the Canon loop: core vending, classicist

The first cycle is already done; replay it mentally before continuing. The scenario was
*"an idle machine displays INSERT COIN"*. The test in `VendingMachineTest` was written
first and failed (red — `display()` didn't exist, then returned `null`). The green was a
**fake**: `return "INSERT COIN";`. No refactor — nothing to clean. That fake is not
cheating; it is a placed bet that a later test (*"inserting a nickel shows $0.05"*) will
force the honest generalization. That's triangulation.

Now it's your turn: work through **Round 1** of `TESTLIST.md`, strictly one item at a
time, in `VendingMachineTest`. Red first, always — watch each test fail before making it
pass, and read *why* it failed (a test that fails for the wrong reason is a broken test).
Check items off and add new ones as you discover them.

<details><summary>Hint — money formatting</summary>

Format from integer cents and pin the locale, or your build breaks on machines where the
decimal separator is a comma (like this one — `en_LV`):
`String.format(Locale.ROOT, "$%d.%02d", cents / 100, cents % 100)`.
</details>

<details><summary>Hint — one-shot messages</summary>

The display contract says THANK YOU / PRICE / SOLD OUT show for exactly one read, then
fall back. A single nullable `pendingMessage` field that `display()` consumes-and-clears
is enough. Don't build a message queue until a test forces one (none will).
</details>

<details><summary>Hint — when the fake must die</summary>

The moment you implement *"inserting a nickel shows $0.05"*, the hardcoded
`return "INSERT COIN"` becomes a conditional on credit. Let the tests push it there;
don't design the final display logic in your head first.
</details>

**Done when:** you remove `@Disabled` from `Checkpoint1CoreVendingTest` and `mvn -q -B
test` is green — checkpoint plus your whole own suite.

### Step 2 — change-making and the awkward edges

Continue the loop through **Round 2** of the list: change on overpayment, the coin-return
button, sold-out slots, consuming stock, and the exact-change house rule (stated
precisely in `TESTLIST.md`). This is where the kata stops being cute: you now have three
interacting pieces of state — credit, product stock, and the coin float. Expect the
refactor step of the loop to earn its keep; if `VendingMachine` grows a private helper
class or two, that's the tests applying design pressure, not scope creep.

<details><summary>Hint — making change</summary>

Greedy from the largest float denomination downward works for this coin system (it is
not true for arbitrary coin systems — worth a TESTLIST parking-lot note). Change comes
from the float; the checkpoint deliberately asserts the *sum*, not which coins.
</details>

<details><summary>Hint — exact change rule</summary>

"Can break a quarter" needs only the float counts: can nickels and dimes compose 25¢?
Check it lazily in `display()` rather than maintaining a flag — less state, fewer bugs.
</details>

**Done when:** `Checkpoint2ChangeAndEdgeCasesTest` is enabled and everything is green.

### Step 3 — the same machine, outside-in

Switch packages and schools. In the `outsidein` package you drive the **same features**
from the boundary: start at `VendingUseCase`, and for each Round 3 scenario write a
Mockito **interaction test** against `VendingController` first, stubbing what
collaborators return and verifying what the controller tells them. The worked example
(`VendingControllerTest`) shows the rhythm. This is GOOS's game: the mocks are
stand-ins for objects that don't exist yet, and awkward mock setups are design feedback
about the interfaces — listen to them.

One deliberate trap to notice: the coin **escrow** (credit accumulated during a
transaction) is state the controller *owns*. The London style does not say "mock
everything" — it says mock the *roles at the boundary*. Keep the escrow as a plain field
or small value object and assert its effects; if you find yourself wanting an
`EscrowService` mock, that's the over-mocking reflex this lesson exists to kill.

Then compare the two designs honestly, in writing (three sentences in your notes):
Where does display logic live in each? Which round produced more types? Which suite
would survive renaming every private method — and which survives swapping the
change-making algorithm?

**Done when:** `Checkpoint3OutsideInAcceptanceTest` is enabled and green — note it uses
hand-rolled *fakes*, not Mockito: at the acceptance level, outcomes are what count. Both
example tests must still pass. (If `CoinRejectionProtocolTest` broke while you worked —
leave it red and carry on. Step 5 is waiting for it.)

### Step 4 — run PIT, then kill the survivors

```bash
mvn -q -B test org.pitest:pitest-maven:mutationCoverage
```

Open `target/pit-reports/index.html`. Record four numbers for each package (add a small
table at the bottom of `TESTLIST.md`): line coverage, generated mutants, mutation score,
test strength. Then work the classicist survivors: for each surviving mutant, decide
whether it reveals a **missing test** (usually), a **weak assertion** (often — the test
executed the line but checked nothing about it), or **dead code** (delete it). Kill them
by strengthening the suite, not by staring at the report until you agree with it.
Compare the two packages: interaction tests tend to score differently than state tests
on the same features — figure out *why* yours did.

**Done when:** the classicist package has 100% test strength (no covered mutant
survives), or every remaining survivor has a one-sentence justification in your notes;
both packages' scores are recorded.

### Step 5 — find the over-mocked test and fix it

One given test in `outsidein` couples to implementation detail: it survives only as long
as the controller talks to its collaborators in one exact sequence, exactly once each,
and never says anything extra. Find it (you have two rejection-path tests to compare —
diff what they verify). Then judge it by Khorikov's pillar of *resistance to
refactoring*: which of its verifications would fail on a change that no user of the
machine could ever observe? `InOrder`, `times(1)` on a stubbed query, and the blanket
`verifyNoMoreInteractions` across all five collaborators are three different sins —
name each one.

Refactor it: keep the one verification that is contract ("the object ends up in the coin
return"), delete the protocol choreography. If it already broke during step 3, you have
lived the lesson — brittle tests tax every future change. Note the deeper Khorikov point:
`reject(...)` is worth verifying at all only because `CoinReturn` is an *unmanaged
boundary* — the same verification style against an owned domain object would be the
disease, not the cure.

**Done when:** the over-specified test is rewritten to outcome-level verification (or
folded into the example test), the suite is green, and a benign internal change to
`insertCoin` — say, extracting a `handleInvalid(object)` method, or additionally showing
the current balance after a rejection — no longer fails any rejection test except where
observable behavior truly changed.

### Step 6 — the AI round: implement from *your* failing tests only

No new checkpoint test — this step is a protocol, per the research framing: the spec
provides the *what*, TDD the *proof*, and you keep the test-author role while the
assistant takes the implementation role.

1. Pick an unimplemented scenario (a Round 3 leftover like *unknown keypad code*, or a
   stretch goal below).
2. **You** write the failing test(s). Run them; confirm red, and that it's red for the
   right reason.
3. Hand the assistant only the production file(s) and your failing test, with an
   instruction of this shape: *"Make this test pass. Do not modify, disable, delete, or
   add any test. Do not touch files outside X. Stop when `mvn -q -B test` is green."*
4. Review the diff against the **agent failure-mode checklist**:
   - **Test tampering** — any change under `src/test/java`: edits, deletions, renames,
     new `@Disabled`, loosened assertions. Instant revert; this is the number-one
     documented failure mode.
   - **Skipping red** — code implementing behavior no test demanded (speculative
     branches, extra features). Delete it; if it should exist, it gets a test first.
   - **Over-fitting** — implementation keyed to your exact test inputs (hardcoded
     returns, input-matching conditionals). Detect it by adding one more example
     yourself, or by running PIT: over-fitted code sprays surviving mutants.
   - **Fabricated green** — "all tests pass" without evidence. Run `mvn -q -B test`
     yourself; never accept the claim.
5. Repair, tighten the prompt, repeat. You own red and refactor; the agent owns green.

**Done when:** the feature is in, your tests pass *unmodified*, the full suite is green,
and the PIT score did not drop from your step 4 recording.

## Self-check

1. Recite Canon TDD's five steps. What exactly is wrong — per the canon, not per taste —
   with writing three failing tests and then implementing all three?
2. Your teammate wants to mock the change-making logic "so the controller test stays a
   unit test". What is the classicist objection, and what is Khorikov's boundary rule
   that settles it?
3. Why did the outside-in round need a `ProductCatalog` seam when the classicist round
   got away with a `Product` enum?
4. A class has 100% line coverage and a 55% mutation score. What, literally, does that
   combination tell you about its tests?
5. Which given test in this project violates *resistance to refactoring*, and through
   which three specific Mockito constructs?
6. Why does "agent writes the code, then the agent writes tests for it" defeat the
   purpose of testing even when the resulting coverage is high?
7. When is the London school the *right* default? Name a system property that flips the
   trade-off in its favor.
8. The classicist display is pull (`display()` returns state), the outside-in display is
   push (`show(message)` is commanded). Which differences in your two designs trace back
   to just that choice?

## Stretch goals

- **Exact change, outside-in.** Add the EXACT CHANGE ONLY behavior to the London design.
  It cannot be done with the current five seams — discover the collaborator it forces
  (a coin float / cash box role), and notice how the seam decision relocates the
  change-making algorithm. Write up which design made the feature cheaper and why.
- **Gate the build.** Add `<mutationThreshold>` (start at 85) and `<coverageThreshold>`
  to the PIT plugin configuration so `mutationCoverage` fails the build; ratchet the
  numbers as your suite strengthens. This is how PIT is actually deployed in CI.
- **TCR round.** Replay Round 1 under *test && commit || revert* (Beck's more radical
  sibling of TDD): a script that commits on green and hard-reverts `src/main` on red.
  Brutal, instructive, and a one-evening exercise. Emily Bache's kata repos are ideal
  material.
- **Adversarial test review.** Have an AI assistant generate a "complete" test suite for
  your finished `VendingMachine`, then run PIT against *its* suite and yours. Compare
  test strength, not test count — generated tests characterize existing behavior; they
  don't drive design.

## Resources

- **Kent Beck — "Canon TDD"** (tidyfirst.substack.com, 2023) — the concise restatement;
  read before step 1: <https://tidyfirst.substack.com/p/canon-tdd>
- **Kent Beck — *Test-Driven Development: By Example*** (Addison-Wesley, 2002) — the
  root text; the money example is this kata's spiritual ancestor.
- **Steve Freeman & Nat Pryce — *Growing Object-Oriented Software, Guided by Tests***
  (Addison-Wesley, 2009) — the London school, argued properly; read the "listening to
  the tests" chapters after step 3.
- **Vladimir Khorikov — *Unit Testing Principles, Practices, and Patterns*** (Manning,
  2020) — the four pillars and the mocking rules used in step 5; the most-cited modern
  synthesis of the two schools.
- **Martin Fowler — "Mocks Aren't Stubs"** (martinfowler.com) — short, free, and the
  origin of the classicist/mockist vocabulary:
  <https://martinfowler.com/articles/mocksArentStubs.html>
- **PIT documentation** — quickstart and mutator reference: <https://pitest.org/>
- **Emily Bache — Samman Technical Coaching katas** (github.com/emilybache) — more kata
  material, including the Gilded Rose, for keeping the practice up.
- Full research notes with 2026 version/trend verification:
  [`../docs/research/methodologies.md`](../docs/research/methodologies.md), section 1.

---

**Version notes.** PIT is pinned to **1.19.6**: it is still the 1.19.x line from
`.authoring/CONVENTIONS.md`, but earlier patches (verified with 1.19.1) fail on Java 25
class files with *"Unsupported class file major version 69"*. The
`pitest-junit5-plugin` **1.2.3** works against JUnit **6.1.3** as configured here
(verified by running `mutationCoverage`), so the JUnit 5.14.x fallback contemplated in
the conventions was not needed. Mockito **5.23.0** is attached as a `-javaagent` via
Surefire — required for warning-free inline mocking on JDK 21+.
