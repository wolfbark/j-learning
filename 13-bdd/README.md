# 13 — BDD: Example Mapping First, Gherkin Second

> After this lesson you can run an example-mapping session that turns an ambiguous ticket
> into agreed examples, encode the ones worth automating as Cucumber scenarios bound to a
> domain API, judge honestly when Gherkin earns its keep — and reuse the same artefact as a
> contract for a coding agent.

## Why this matters (2026)

Let's start with the honest version, because BDD's marketing has done it no favours.

**BDD tooling has plateaued.** Adoption studies put BDD-framework usage at roughly 27% of
projects and flat. Cucumber-JVM is mature, actively released, and volunteer-maintained since
SmartBear's commercial retreat. The dominant story in the discourse is not success, it is a
well-documented failure mode: **automation engineers writing Gherkin alone while product
owners never read it.** When that happens, every scenario is an expensive translation layer —
a second, less precise, more brittle copy of tests you could have written in JUnit — and
teams conclude "BDD doesn't scale". They are right about what they built. Andy Knight's
"Is BDD Dying?" (2025) is the fair state-of-the-union: not dead, but it has to refocus on
discovery and collaboration rather than tooling.

So why teach it? Because the part that never plateaued is the part that isn't a tool. Dan
North invented BDD to fix a *conversation* problem, and Adzic's *Specification by Example*
argues the case on those grounds alone: the value is the shared understanding that a team
reaches while inventing concrete examples together, and Gherkin is a way to keep that
understanding executable so it does not rot. Example mapping — Matt Wynne's four-colour,
25-minute card workshop — is the highest-yield 25 minutes in agile practice, and it costs a
whiteboard. That skill transfers to every project you touch, including ones that will never
run Cucumber. This lesson gives that half most of the weight, deliberately.

**The second act is AI.** Spec-driven development with coding agents has revived interest in
executable specifications, and Gherkin turns out to be a very good contract format for
steering an agent: business-language, example-based, unambiguous about outcomes, and
*runnable*, so the agent's claim of success can be checked rather than believed. This is the
same insight as project 02's use of failing tests as agent guardrails, one level up: the spec
provides the *what*, the executable examples provide the *proof*. Step 6 makes you try it and
find out where your spec was actually incomplete — because tacit knowledge leaks, and an
agent will implement your gaps confidently and wrongly.

Meanwhile, most JVM "BDD" energy has moved to developer-facing tools that deliver the
readability without the collaboration overhead: Spock's `given/when/then` blocks (2.4, first
stable release in three years) and Karate's Gherkin-flavoured API DSL. Neither is
collaborative BDD, and both are frequently the better engineering choice. Knowing *why* is
part of the lesson.

Full research notes: [`../docs/research/methodologies.md`](../docs/research/methodologies.md), section 3.

## Core concepts

### The three practices, in order

BDD is usually drawn as a loop of **Discovery → Formulation → Automation**.

1. **Discovery** — *example mapping*: given a story, enumerate its rules, illustrate each
   rule with concrete examples, and write down every question nobody can answer. Output:
   agreement, and a list of decisions.
2. **Formulation** — turn the examples worth keeping into declarative Given/When/Then in
   business language. Output: a document a product owner can read without help.
3. **Automation** — bind the steps to code. Output: living documentation that fails when the
   behaviour drifts.

Skipping straight to 3 is the failure mode. Doing 1 alone is still worth the meeting; teams
that only ever do example mapping still get most of the benefit. That ordering is why this
lesson's first two steps have no code in them at all.

### Gherkin style that survives contact

```gherkin
# Imperative, UI-coupled, brittle: this is a test script in Gherkin costume
Given I open the checkout page
When I type "100.01" into the "subtotal" field
And I click "Apply discount"
Then I should see "10.00" in the ".discount-amount" element

# Declarative, business language, outcome-oriented: this is a specification
Given a member with 0 points
When they check out an order of €100.01
Then the discount is €10.00
```

The first breaks when a CSS class is renamed and tells a reader nothing about the rule. The
rules of thumb:

- **One scenario, one rule.** If you need "and also", you have two scenarios.
- **No UI, no HTTP, no SQL, no field names.** Nouns and verbs from the domain only.
- **Concrete data, no logic.** No `if`, no loops, no arithmetic in the step text; if the
  scenario is tabular, that's what `Scenario Outline` + `Examples` is for.
- **Third person, present tense.** "A member checks out" reads better than "I check out" and
  stops the author sliding into a click script.
- **`Rule:` blocks** (Gherkin 6+) group the examples that illustrate one business rule —
  they map one-to-one onto the blue cards from your session. Use them; they make the file
  readable as documentation rather than as a test list.
- **Step text is global.** Two step definitions matching the same sentence is a build error,
  so phrasing is a shared vocabulary decision, not a local one. That friction is a feature:
  it is your ubiquitous language, enforced by the compiler.

### Why step definitions call the domain, not the UI

Bind Gherkin at the **domain API** — here, `PricingService.price(order, member)`. The
scenario then costs milliseconds, fails for exactly one reason, and stays true across UI
rewrites. Drive it through HTTP and every scenario also tests JSON mapping, routing,
serialization and the servlet container; the suite gets slow, flaky, and vague about what
broke.

That leaves the HTTP layer genuinely untested — so test it, once, narrowly, in plain JUnit.
[`CheckoutControllerTest`](src/test/java/com/vlearning/bdd/web/CheckoutControllerTest.java)
is that test: two cases proving JSON maps onto the domain call and back. This is the same
argument as project 12's test-shape discussion — put the many cases where they are cheap,
and cover the wiring once where it is expensive.

### The plumbing (Cucumber on JUnit 6)

Three artefacts and one runner class:

```java
@Suite                                    // junit-platform-suite runs it as a test class
@IncludeEngines("cucumber")               // ...delegating to the Cucumber engine
@SelectPackages("features")               // src/test/resources/features/**.feature
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.vlearning.bdd.steps")
public class RunCucumberTest {}
```

`cucumber-java` (annotations), `cucumber-junit-platform-engine` (a `TestEngine`, so features
are first-class JUnit Platform tests) and `cucumber-spring` (glue classes become Spring
beans, so a step definition can constructor-inject `PricingService`). Surefire finds
`RunCucumberTest` by its name, and `mvn test` runs Gherkin and JUnit in one pass, one report.

Spring wiring is one class in the glue package:

```java
@CucumberContextConfiguration
@SpringBootTest(classes = BddApplication.class)
public class CucumberSpringConfiguration {}
```

## The project

A Spring Boot 4.1.1 / Java 25 service for an online training platform's checkout. No
database, no Docker: the domain is arithmetic and policy, which is where the interesting
ambiguity lives.

Your subject is [`docs/the-ticket.md`](docs/the-ticket.md) — a real-shaped PM one-liner:

> Members get 10% off orders over €100, gold members 20%, plus 1 loyalty point per €10
> spent; points can pay for orders (100 points = €10); discounts don't stack with point
> payments... probably?

Two sentences, three sprint points, **at least eight** decisions nobody has made. That count
is the target for step 1: we found eight, plus two worth deferring. Beat six before you look
at anything else in `docs/`.

### What's given

```
docs/the-ticket.md                    ← the ambiguous request (your input)
docs/example-mapping.md               ← facilitation guide, blank template, AI PO persona
                                        prompt, and (at the bottom) the answer key
src/main/java/com/vlearning/bdd/
  pricing/
    PricingService                    ← SKELETON: signature only, throws. Yours to implement.
    Order, Member, MemberTier,
    PricingResult, PromoCatalog       ← given: the domain vocabulary and the promo codes
  shipping/ShippingService, ShippingQuote  ← the worked example, implemented and passing
  web/CheckoutController              ← thin HTTP edge over both services
src/test/java/com/vlearning/bdd/
  RunCucumberTest                     ← the @Suite runner
  steps/CucumberSpringConfiguration   ← cucumber-spring context
  steps/ParameterTypes                ← custom {euros} and {tier} parameter types
  steps/FreeShippingSteps             ← worked example glue (feature → domain API)
  pricing/Checkpoint5PricingMathTest   ← @Disabled acceptance gate for step 5
  web/CheckoutControllerTest          ← the one thin HTTP test
src/test/resources/features/
  free-shipping.feature               ← the complete worked example, passing
  pricing.feature.EXPECTED            ← reference scenarios; does NOT execute (step 5)
```

`features/pricing.feature` does not exist. You write it in step 3.

The worked example is `free-shipping.feature`: a deliberately tiny rule (€4.95 flat, free
from €50, always free for gold) that was already discovered, agreed and specified. Read it
first — it is the shape you are aiming for, including `Rule:` blocks, a `Scenario Outline`
for the threshold, and steps that read like sentences about a business.

Money is `BigDecimal` in euros throughout, and the `{euros}` parameter type parses `€12.34`
with a locale-independent `new BigDecimal(String)`. That is not incidental: the built-in
`{bigdecimal}` type is locale-sensitive, and this machine's locale uses a comma decimal
separator.

### How to run it

```bash
mvn -B test         # pristine: Tests run: 15, Failures: 0, Skipped: 6
                    #   → 7 free-shipping scenarios + 2 HTTP tests pass,
                    #     6 skipped are the disabled step-5 checkpoint
mvn -B test -Dcucumber.filter.tags="@wip"    # once you start tagging your own scenarios
```

The Cucumber console output (`pretty, summary` plugins) prints every scenario with each step
resolved to the method that ran it — the fastest way to debug an unmatched or ambiguous step.

## Guided steps

### Step 1 — read the ticket, list the ambiguities. No code.

Read [`docs/the-ticket.md`](docs/the-ticket.md) and write down — in a scratch file, in your
own words — every question you would need answered before you could implement it correctly.
Not "the ticket is vague": specific, answerable questions, each phrased so that the answer is
a number, a yes, or a no.

Do this before you open `docs/example-mapping.md`, and before you ask any AI assistant
anything. The whole point of the count is that it is *your* count.

<details><summary>Hint — where ambiguity hides</summary>

Six places, reliably: **boundaries** (is "over €100" inclusive?), **order of operations**
(which of two rules applies first?), **basis** (a percentage of *what* exactly?),
**rounding** (up, down, nearest, and in whose favour?), **partiality** (can you do half of
it?), and **interaction** (what happens when two rules both apply?). Then ask the question
everyone forgets: what does the system do when the customer asks for something illegal —
silently ignore, clamp, or reject?
</details>

**Done when:** you have a written list of at least six ambiguities. Ten is a good score; the
answer key documents eight in-scope plus two deferrals.

### Step 2 — run the session

Open [`docs/example-mapping.md`](docs/example-mapping.md), read everything above the answer
key, and run a real 25-minute session. Two ways:

- **With a colleague** — best. One of you plays Pia the PO (the persona brief in the guide is
  enough to play her), the others hunt edge cases. Actual cards or sticky notes beat a doc.
- **Solo, against an AI PO** — paste the persona prompt from the guide into a fresh chat and
  interview it. One question at a time, business language, and push every answer to a
  concrete number. It is a genuinely good substitute for this exercise, and it is also a
  preview of step 6: you are already treating a model as a source of specification.

Fill in the blank template as you go. Resist writing Gherkin — cards are faster, and
formulation is a different job with a different hat.

**Done when:** every blue rule card has ≥2 green examples, every red question card has a
ruling or an explicit deferral with an owner, and your `Out of scope` list is non-empty. Then
— and only then — read the answer key at the bottom of the guide and diff it against your
notes. Where you differ, keep your own rulings and note the difference; where the key found
something you missed, note *how* you would have found it.

### Step 3 — formulate: write `features/pricing.feature`

Create `src/test/resources/features/pricing.feature` and write your agreed examples as
Gherkin. Structure it with one `Rule:` block per blue card and the examples underneath.
Where a rule is naturally tabular — several inputs, one shape of outcome — use
`Scenario Outline` with an `Examples:` table; where an example makes a specific point, give
it a `Example:` name that states the point ("An order of exactly €100 misses the discount")
rather than the mechanics ("test boundary case 1").

Re-read the style rules above before you start, then apply the two tests that matter: could
Pia read this file and tell you it's wrong? And does any line mention a class, a field, a
JSON key, or a button?

<details><summary>Hint — how many scenarios?</summary>

Roughly one per green card, plus the rejection paths. The reference file has 16 scenarios
across 6 rules, including three that assert a rejection message. If you have 40, you are
probably specifying combinations that no rule needs — those belong in unit tests, not in
living documentation. If you have 5, you dropped the boundaries.
</details>

<details><summary>Hint — specifying a rejection in business language</summary>

`When they try to check out …` / `Then checkout is rejected with "…"`. The "try to" wording
is the convention that lets a reader see the difference between a happy path and an error
path at a glance — and it keeps the step definition's exception handling out of the spec.
</details>

**Done when:** the file covers every ruling from your session, and `mvn -B test` fails with
*undefined* steps (Cucumber prints snippets for them). Undefined, note, not failed: you have
a specification and no automation yet.

### Step 4 — automate: step definitions, all red

Write `src/test/java/com/vlearning/bdd/steps/PricingSteps.java`. Copy the shape of
`FreeShippingSteps`: constructor-inject `PricingService` (cucumber-spring makes the glue
class a bean), keep the scenario's state in fields, and reuse the `{euros}` and `{tier}`
parameter types from `ParameterTypes`.

Keep the glue **thin**. A step definition translates one sentence into one call and one
assertion; any logic in there is untested code sitting inside your test suite, and it is
where Gherkin projects go to die.

<details><summary>Hint — one scenario, one instance</summary>

cucumber-spring gives each glue class scenario scope, so plain fields (`private Member
member;`) are safe to carry state from `Given` to `Then`. Do not add `static`.
</details>

<details><summary>Hint — asserting on money</summary>

`assertThat(result.discount()).isEqualByComparingTo("10.00")` — AssertJ's
`isEqualByComparingTo` ignores `BigDecimal` scale, so `10.0` and `10.00` both pass.
`isEqualTo` compares scale too and will bite you.
</details>

<details><summary>Hint — the rejection steps</summary>

`Throwable thrown = catchThrowable(() -> pricingService.price(order, member));` then assert
the type in the `When` step and stash `thrown.getMessage()` for the `Then`. The exact
messages are in the answer key.
</details>

**Done when:** every step resolves to a method and every pricing scenario fails with
`UnsupportedOperationException` from `PricingService`. That is the real red bar: the
specification is executable and the behaviour is missing.

### Step 5 — implement until green, then reconcile

Enable `Checkpoint5PricingMathTest` (remove the `@Disabled`) and implement
`PricingService.price`. Work one scenario at a time — the same Canon TDD loop as project 02,
just with the scenario list handed to you by the business instead of written by you. BDD and
TDD compose exactly here: the feature file is the outer loop (behaviour agreed with the PO),
your own unit tests are the inner loop (the arithmetic nobody wants in Gherkin).

Write those unit tests. Rounding direction, the €10-block floor for earned points, the
clamping of an over-large redemption — these are properties of a calculation, and specifying
them in Gherkin as a dozen more scenarios would bloat the living documentation with detail
Pia does not care about. The checkpoint pins the four rulings that are easiest to get subtly
wrong; yours should cover the arithmetic more densely than that.

Then reconcile with the reference:

```bash
diff -u src/test/resources/features/pricing.feature \
        src/test/resources/features/pricing.feature.EXPECTED
```

Read the diff as a review of your *specification*, not of your code: which examples did the
reference cover that you did not, and were they real gaps or noise? Which of yours are better
than the reference's? Where your rulings differ from the answer key, your feature file wins —
adjust the checkpoint test to match your rulings and say so in a comment.

**Done when:** `mvn -B test` is green with the checkpoint enabled, your pricing feature
passing, your own unit tests in place, and the diff against `.EXPECTED` explained
line-by-line in your notes.

### Step 6 — the second act: your feature file as an agent contract

Now use the artefact for what 2026 actually wants it for. Give a coding agent a fresh start:
a scratch copy of this project with `PricingService` back to a skeleton, `PricingSteps`
deleted, and **only your `pricing.feature`** as the specification.

Prompt shape: *"`features/pricing.feature` is the complete specification for
`PricingService`. Implement the service and the step definitions so that `mvn -B test`
passes. Do not modify the feature file. If the specification is ambiguous or silent on
something you need, stop and list the questions instead of guessing."*

Score the result against this checklist:

| # | Check | What a failure tells you |
|---|---|---|
| 1 | Does `mvn -B test` pass, run by *you*, not claimed by the agent? | Never trust a reported green (project 02, step 6). |
| 2 | Was the feature file modified — at all? | The number-one agent failure mode is editing the spec to fit the code. |
| 3 | Did it ask questions, and were they the *right* gaps? | Good sign. An agent that never asks is guessing silently. |
| 4 | Rounding: does its implementation round the way you agreed, or the way `BigDecimal` defaults? | Your spec did not pin the rule — it only pinned two examples of it. |
| 5 | The clamping rule: does it clamp, reject, or go negative on an over-large redemption? | Same gap, higher stakes: you specified one instance of a policy, not the policy. |
| 6 | Threshold: `>` or `>=`? | If it got this right, your boundary examples earned their place. |
| 7 | Behaviour your spec never mentioned — extra validation, extra fields, a promo code that stacks? | Tacit knowledge you assumed. Nobody told it; it invented a plausible answer. |
| 8 | Is the glue thin, or did business logic migrate into the step definitions? | Executable specs constrain outcomes, not structure. |

Then write the debrief — three short paragraphs, and this is the actual deliverable of the
lesson:

1. **Where the spec was sufficient.** Which categories of behaviour did examples alone pin
   down completely?
2. **Where tacit knowledge leaked.** For each gap the agent filled by guessing: could you
   have specified it in Gherkin without wrecking readability, or does it belong in a unit
   test, a type, or a comment? (Notice how often the honest answer is "not in Gherkin".)
3. **The trade honestly.** You now have both artefacts for the same behaviour: a feature file
   plus glue, and a plain JUnit suite. Which would you keep on a team where the PO reads the
   feature file monthly? On a team where nobody but you ever opens it? Cucumber's cost is
   real — an indirection layer, a step-text vocabulary to maintain, and a build plugin — and
   it is only repaid by readers. Name the conditions under which you would pay it.

Finish with a paragraph you can defend in a code review: **when is plain JUnit strictly
better, and where do Spock and Karate fit?** Spock gives you `given/when/then` labels,
`where:` data tables and superb failure messages inside a normal JVM test — the readability
of BDD with none of the collaboration machinery, at the price of Groovy on the build.
Karate's Gherkin-flavoured DSL is an API-testing tool, not collaborative BDD: nobody
role-plays a product owner over `match response.discount == 10.00`. Both are frequently the
right answer, which is exactly why Cucumber should be a decision rather than a default. And
add the caveat about Serenity BDD, if you are tempted by its living-documentation reports:
the 2026 criticism is transitive-dependency weight, which is a real tax on a build.

**Done when:** the debrief exists in writing, with the checklist scored and at least two
concrete gaps identified in your own specification.

## Self-check

1. Name the three BDD practices in order. Which one delivers most of the value, and which
   one do teams start with?
2. Your team has a 400-scenario Cucumber suite that only the automation engineers ever open.
   Diagnose it, and say what you would actually do on Monday.
3. Why do the step definitions here call `PricingService` rather than the HTTP endpoint —
   and what would you lose if you deleted `CheckoutControllerTest` on the grounds that "the
   scenarios cover checkout"?
4. Which of your rounding and clamping rules deserve to be scenarios, and which belong in
   unit tests? What is the criterion?
5. In the ticket, the phrase "discounts don't stack with point payments… probably?" was
   pointing at the wrong risk. What was the real one, and which card colour catches that?
6. `mvn test` runs both Gherkin and JUnit tests in one pass. Which artefact makes that
   happen, and what would break if you removed `@IncludeEngines("cucumber")`?
7. Two step definitions in different classes match the sentence "the customer is a member".
   What happens, and why is that arguably the right design?
8. An agent implemented your feature file and everything passed, but it invented a rounding
   rule you never wrote down. Whose defect is that, and what is the cheapest fix?

## Stretch goals

- **Living documentation.** Add `@wip` and `@revenue-critical` tags and run subsets with
  `-Dcucumber.filter.tags`. Then generate an HTML report (`html:target/cucumber.html`
  plugin) and ask the honest question: would Pia open it? If not, what would have to change
  about the file — not about the report?
- **Same spec, Spock.** Port three pricing scenarios to a Spock specification with a `where:`
  data table (Spock 2.4, Groovy 4). Compare line count, failure messages, and who could read
  each version. This is the comparison that makes the tooling choice concrete.
- **A second bounded context.** Specify refunds — ambiguity A9 in the answer key, deliberately
  deferred. Run a second, shorter mapping session on it (points clawed back? partial
  refunds? does a refund below the €100 threshold retroactively remove the discount?) and
  notice that the deferral was the right call: it is a bigger story than the original ticket.
- **Break the spec on purpose.** Change one ruling in `PricingService` (make the threshold
  `>=`) and watch which scenarios fail. Then do the same to a rule the scenarios only imply
  rather than assert, and find the hole. This is the mutation-testing instinct from project
  02, applied to a specification.

## Resources

- **Gojko Adzic — *Specification by Example*** (Manning, 2011) — the collaboration case,
  argued from real teams; still the best statement of what BDD is for and where it stops.
- **John Ferguson Smart & Jan Molak — *BDD in Action*, 2nd ed.** (Manning, 2023) — the
  current comprehensive text; discovery, formulation and automation as one pipeline.
- **Matt Wynne — "Introducing Example Mapping"** (cucumber.io blog, 2015) — the workshop
  itself, in ten minutes of reading:
  <https://cucumber.io/blog/bdd/example-mapping-introduction/>
- **Wynne, Hellesøy & Tooke — *The Cucumber for Java Book*** (Pragmatic Bookshelf) — the
  reference for step definitions, parameter types and glue design.
- **Dan North — "Introducing BDD"** (dannorth.net, 2006) — the origin story, and short:
  <https://dannorth.net/introducing-bdd/>
- **Andy Knight (Automation Panda) — "Is BDD Dying?"** (Mar 2025) — the honest
  state-of-the-union; read it before you recommend Cucumber to a team:
  <https://automationpanda.com/2025/03/06/is-bdd-dying/>
- **Cucumber-JVM docs** — JUnit Platform Suite setup and configuration options:
  <https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-junit-platform-engine>
- Full research notes with 2026 version/trend verification:
  [`../docs/research/methodologies.md`](../docs/research/methodologies.md), section 3.

---

**Version notes.** Cucumber-JVM **7.34.4** (`cucumber-java`, `cucumber-spring`,
`cucumber-junit-platform-engine`) runs cleanly on Spring Boot 4.1.1's **JUnit 6.1.x**
platform with no pinning: the Cucumber artifacts' transitive `junit-platform-*` 1.x
dependencies are overridden by Boot's imported `junit-bom`, and the `TestEngine` SPI is
compatible across that jump. `junit-platform-suite` is declared without a version (Boot
manages it) — do not import `cucumber-bom` alongside it, because a child `dependencyManagement`
entry outranks the parent's and would silently downgrade the whole platform to 1.x while
Jupiter stays on 6.x. Feature files are selected with `@SelectPackages("features")`;
`@SelectClasspathResource` also works but the JUnit 6 platform emits a discovery-issue
warning for it. Custom `{euros}`/`{tier}` parameter types are used instead of the built-in
`{bigdecimal}`, which parses according to locale (this machine is `en_LV`, comma decimal
separator). No database and no Docker: the domain is pure computation.
