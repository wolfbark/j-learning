# 01 — Modern Java: Time-Travel Refactoring

> After this lesson you can take a Java-8-style codebase and rework it into Java 25
> data-oriented idiom — records, sealed interfaces, exhaustive pattern switches, flexible
> constructor bodies, and scoped values — without changing its observable behavior.

## Why this matters (2026)

Java 25 is the LTS that production teams are converging on
([research: platform & production, §1a](../docs/research/platform-and-production.md)). The
language work of the last five releases has settled into a coherent design style that Brian
Goetz calls **data-oriented programming** (DOP): model data as immutable records, close the
set of alternatives with sealed interfaces, and write operations as exhaustive `switch`
expressions over that closed set. This is no longer a preview-flag curiosity — records,
sealed types, pattern matching for `switch`, and record deconstruction patterns are all
final and are the canonical modern idiom (research §1c). Java 25 adds the last two pieces
this lesson uses: **flexible constructor bodies** (JEP 513) and **scoped values** (JEP 506),
both final — no `--enable-preview` anywhere in this project.

Most real work in 2026 is not greenfield: it is exactly what you will do here — meeting a
service written in 2014 idiom and modernizing it *safely*. The refactoring is the skill;
the behavior-parity test suite is the safety net.

## Core concepts

**Records (JEP 395)** — nominal tuples: `record CardDetails(String number, CardBrand brand,
int expiryMonth, int expiryYear) {}` replaces ~80 lines of constructor/getter/setter/
`equals`/`hashCode`/`toString`. Components are `final`; accessors are `number()`, not
`getNumber()`. Validation belongs in the compact constructor. When *not* to use them:
entities with identity and mutable lifecycle (a JPA `@Entity` is not a record), and be
aware component-based equality is only as good as the components' `equals` —
`new BigDecimal("1.5")` and `new BigDecimal("1.50")` are *not* equal.

**Sealed interfaces (JEP 409)** — a closed sum: the type declares every permitted
implementation, and the compiler knows the list.

```java
public sealed interface PaymentResult permits Approved, Declined, Fraud, Retryable {
    String paymentId();
}
```

Sealing is for domain alternatives *you* own. It is deliberately hostile to extension — the
wrong tool for a plugin SPI, exactly the right tool for "a payment attempt has these four
outcomes, period."

**Exhaustive `switch` with patterns (JEP 441) + record patterns (JEP 440)** — the operation
side of DOP. Over a sealed type, a `switch` with no `default` must cover every permitted
subtype or it does not compile. That is the entire point: add a fifth outcome next year and
every operation that forgot about it becomes a *compile error*, not a production incident. A
`default` branch silently buys back the old behavior — resist it. Two semantic changes from
`if`-`instanceof` chains worth knowing: a `null` selector throws `NullPointerException`
(unless you add `case null`), and guards (`when fee.signum() == 0`) hang conditions directly
on a pattern.

**Flexible constructor bodies (JEP 513)** — statements may now run *before* `super(...)`.
The classic victim was validating constructor arguments in a subclass: pre-25 you either
validated after `super()` had already run, or smuggled the check into a static helper
wrapped around the super argument. Now the prologue is plain code that fails before the
object exists.

**Scoped values (JEP 506)** — the modern replacement for `ThreadLocal` request context.
A `ScopedValue` is bound for the dynamic extent of one call — `ScopedValue.where(CTX, v).call(op)`
— and unbound automatically when `op` returns. Compare the `ThreadLocal` failure modes it
removes: forgotten `remove()` leaking across pooled-thread reuse, nested `set()` wiping the
outer value, and any callee being able to mutate the context behind your back. `ThreadLocal`
remains correct for genuinely thread-owned *mutable* state (a per-thread buffer or
`SimpleDateFormat` cache); scoped values are one-way, immutable, share-with-callees context.
Bindings are inherited by child tasks under structured concurrency — still preview, covered
in project 14.

**Data-oriented programming (Goetz)** — the umbrella over all of the above: model the data,
the whole data, and nothing but the data; make illegal states unrepresentable; keep data
immutable and transparent; put behavior in functions that pattern-match over the closed sum.
DOP complements OOP rather than replacing it: virtual dispatch is still right when an *open*
set of types each owns its behavior; DOP wins when the set of shapes is closed and the set
of operations grows.

## The project

You have inherited `vlearning.payments` — a small card-payment authorization module,
plausibly last touched in 2014. It validates a payment request, runs fraud and regional-limit
checks, computes a brand-specific fee, and reports one of four outcomes. It *works*. It is
also a museum: mutable JavaBean DTOs, an inheritance-based result hierarchy walked with
`instanceof`-and-cast chains, an enum `switch` with fall-through, a `ThreadLocal` request
context, and anonymous-class comparators and callbacks.

Given code (`src/main/java/vlearning/payments/`):

| File | Role | 2014 smell you will remove |
|---|---|---|
| `PaymentRequest`, `CardDetails` | DTOs | mutable JavaBeans, ~170 lines of boilerplate |
| `PaymentResult` + `Approved`, `Declined`, `Fraud`, `Retryable` | result hierarchy | abstract base class, getters, no value semantics |
| `PaymentProcessor` | the service | `instanceof` chains, fall-through enum `switch`, anonymous classes |
| `PaymentException` | error type | no argument validation (couldn't run it before `super`) |
| `RequestContext` | request metadata | static `ThreadLocal` with `set`/`clear` |
| `ResultCallback` | batch callback | implemented with anonymous classes |

The test suite has two layers:

- **`ParityTest` — enabled, 17 tests, stays green the whole time.** It pins observable
  behavior and deliberately never calls a getter or setter on anything you will refactor, so
  it keeps compiling as class shapes change. If it goes red, your refactoring changed
  behavior — stop and fix.
- **`Checkpoint1…6` — `@Disabled`, one per step.** Enable each when you start the step. Most
  contain at least one *discriminating* test that fails against the given code and can only
  pass once you have genuinely made the change (they verify sealedness via reflection, the
  pattern-switch null semantics, scoped-value nesting, and so on).

Run it:

```bash
cd 01-modern-java
mvn test        # green as delivered: 17 parity tests pass, 26 checkpoint tests skipped
```

Maven on this machine already runs on JDK 25 (`mvn -version`); the direct `java` CLI may
still default to 17 — for the stretch goals use `export JAVA_HOME=/opt/homebrew/opt/openjdk`.

## Guided steps

Work in order — later steps build on earlier ones. For every step: remove the `@Disabled`
line from its checkpoint test, make it green, and keep `ParityTest` green.

### Step 1 — DTOs become records

**Goal.** Replace `PaymentRequest` and `CardDetails` with records; fix the compile errors
this causes in `PaymentProcessor` (getter call sites become component accessors).

Keep the component names and order identical to the old all-args constructor — the parity
tests construct DTOs positionally. Do **not** add validation to the records yet: the parity
suite pins that invalid input fails in the *processor* with a `PaymentException`, and a
record compact constructor would fail earlier with a different exception. (Where validation
should live is a real design question — see self-check 4.)

<details><summary>Hint</summary>

The whole of `CardDetails.java` becomes:

```java
public record CardDetails(String number, CardBrand brand, int expiryMonth, int expiryYear) {}
```

Then let the compiler drive: every red `getNumber()` in the processor becomes `number()`.
</details>

**Done when** `Checkpoint1RecordsTest` and `ParityTest` are green, and both DTO files are
single-digit lines long.

### Step 2 — Seal the result hierarchy

**Goal.** Turn `PaymentResult` into a `sealed interface` permitting exactly `Approved`,
`Declined`, `Fraud`, `Retryable`, each converted to a record implementing it.

Records cannot extend a class — that is *why* the base type must become an interface. Move
the shared `paymentId` into each record as its first component and declare `String paymentId();`
on the interface: every record satisfies it automatically through its component accessor.

<details><summary>Hint</summary>

```java
public sealed interface PaymentResult permits Approved, Declined, Fraud, Retryable {
    String paymentId();
}
public record Approved(String paymentId, String authCode, BigDecimal fee) implements PaymentResult {}
```

The `instanceof` chain in `summarize` still compiles against records — you only need to
rename the accessor calls (`getFee()` → `fee()`). The chain itself dies in step 3.
</details>

**Done when** `Checkpoint2SealedResultsTest` and `ParityTest` are green. Try adding
`class Sneaky extends ... implements PaymentResult` in a scratch file — the compiler refuses:
the sum is closed.

### Step 3 — Exhaustive switch replaces the instanceof chain

**Goal.** Rewrite `summarize` as a single `switch` *expression* with type patterns and a
`when` guard for the fee-waiver case. Delete the trailing `throw new IllegalStateException`
— and do not write a `default`. Also modernize `feeFor`: arrow labels, all four brands
listed explicitly, no fall-through, no `default`.

This is the payoff of step 2: with the hierarchy sealed and no `default`, the compiler
proves coverage. Comment out the `Retryable` case and recompile — the build breaks. That
compile error is the feature. The old chain would have met a fifth result type at runtime,
in production, via that `IllegalStateException`.

Note the null semantics change: the old chain fell through every `instanceof` and threw
`IllegalStateException` for `null`; a pattern switch throws `NullPointerException` on a null
selector. The checkpoint asserts exactly that difference — it is the proof you are really
running a switch.

<details><summary>Hint</summary>

```java
return switch (result) {
    case Approved a when a.fee().signum() == 0 ->
            "APPROVED " + a.paymentId() + " auth=" + a.authCode() + " (fee waived)";
    case Approved a  -> "APPROVED " + a.paymentId() + " auth=" + a.authCode() + " fee=" + a.fee();
    case Declined d  -> "DECLINED " + d.paymentId() + " reason=" + d.reason();
    case Fraud f     -> "FRAUD " + f.paymentId() + " risk=" + f.riskScore() + " rule=" + f.rule();
    case Retryable r -> "RETRY " + r.paymentId() + " in " + r.retryAfterSeconds() + "s: " + r.reason();
};
```

The guarded case must come before the unguarded `Approved` case — the compiler enforces
dominance ordering.
</details>

**Done when** `Checkpoint3ExhaustiveSwitchTest` and `ParityTest` are green, and commenting
out any case produces a compile error.

### Step 4 — Flexible constructor bodies

**Goal.** `PaymentException` currently performs no argument validation, because in 2014
nothing could run before `super(...)`. Add a prologue (JEP 513, final in 25 — no flags):
reject a null/blank/non-uppercase `code` and a null `message` with
`IllegalArgumentException`, *then* call `super("[" + code + "] " + message)`.

Why before and not after? Failing in the prologue means no half-constructed object ever
exists: `RuntimeException`'s constructor never runs, no stack trace is captured for an
object that will never be thrown, and (for non-final classes generally) a subclass can no
longer observe a partially-initialized instance.

<details><summary>Hint</summary>

```java
public PaymentException(String code, String message) {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("error code is required");
    if (!code.equals(code.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("error code must be uppercase");
    if (message == null) throw new IllegalArgumentException("message is required");
    super("[" + code + "] " + message);
    this.code = code;
}
```
</details>

**Done when** `Checkpoint4FlexibleConstructionTest` and `ParityTest` are green.

### Step 5 — ScopedValue replaces the ThreadLocal

**Goal.** Rework `RequestContext`: make it a record (`requestId`, `region`), replace the
static `ThreadLocal` with a `ScopedValue<RequestContext>`, delete `set()` and `clear()`
outright, and reimplement `callWith`, `current()`, and `isSet()` on top of it. Fix the two
call sites in `PaymentProcessor`.

The discriminating checkpoint test nests two scopes. Run it against the *old* code first
(enable it before refactoring) and watch it fail: the inner `callWith`'s `finally { clear(); }`
wipes the **outer** context — a real bug class in `ThreadLocal` code, usually met in
production as "the request id vanished halfway through the request." `ScopedValue` makes
that mistake unwritable: the inner binding shadows for its dynamic extent and the outer one
is restored automatically. Unbound access is also no longer a silent `null`: `get()` throws.

<details><summary>Hint</summary>

```java
public record RequestContext(String requestId, String region) {
    private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

    public static boolean isSet()            { return CONTEXT.isBound(); }
    public static RequestContext current()   { return CONTEXT.get(); }
    public static <T> T callWith(RequestContext ctx, Supplier<T> action) {
        return ScopedValue.where(CONTEXT, ctx).call(action::get);
    }
}
```
</details>

**Done when** `Checkpoint5ScopedValueTest` and `ParityTest` are green and the word
`ThreadLocal` no longer appears in the project.

### Step 6 — Data-oriented wrap-up

**Goal.** Finish the transformation of `PaymentProcessor` into "data as records, behavior
as functions over a closed sum":

- Rewrite `summarize` once more using **record deconstruction patterns** —
  `case Approved(String id, String auth, BigDecimal fee) when fee.signum() == 0 -> …` —
  binding components directly instead of calling accessors. Use `_` for components a case
  ignores.
- Replace the anonymous `Comparator` with `Comparator.comparing(PaymentRequest::amount).reversed().thenComparing(PaymentRequest::id)`
  (or a stream pipeline), and the anonymous `ResultCallback` with a lambda. Annotate
  `ResultCallback` with `@FunctionalInterface`.

Then step back and map what happened to Goetz's DOP principles: the records model the data
and nothing but the data; the sealed interface makes the four legal outcomes the *only*
representable outcomes; `summarize` is now a total function whose totality the compiler
checks. The processor kept its behavior bit-for-bit — `ParityTest` is the witness.

**Done when** `Checkpoint6DataOrientedTest` and `ParityTest` are green and
`grep -c instanceof src/main/java/vlearning/payments/PaymentProcessor.java` prints `0`.

## Self-check

1. Why can a record not extend an abstract class, and what does that force you to do with a
   shared field like `paymentId` when sealing a hierarchy of records?
2. What exactly does the compiler prove about a `switch` over a sealed interface with no
   `default` — and which single keyword silently un-proves it?
3. A colleague adds `record ChargedBack(...) implements PaymentResult` next quarter. List
   every place this project stops compiling, and compare that with what the Java-8 version
   would have done instead.
4. Where should validation of a `PaymentRequest` live — record compact constructor or
   processor — and what are the trade-offs of each (think: who constructs, what exception
   types, parity with wire formats)?
5. Why does the refactored `summarize(null)` throw `NullPointerException` while the old
   chain threw `IllegalStateException`, and how would you opt back into explicit null
   handling inside the switch?
6. Before JEP 513, name two workarounds for validating a superclass constructor argument,
   and one concrete drawback of each.
7. The old nested-`callWith` bug wiped the outer context. Explain mechanically why the
   `ScopedValue` version cannot lose the outer binding — and name one use case where
   `ThreadLocal` is still the correct choice.
8. DOP puts behavior in switches over data; OOP puts behavior in methods on types. Give one
   criterion for choosing each (hint: which axis of change is open — new shapes, or new
   operations?).

## Stretch goals

1. **Measure compact object headers (JEP 519).** Your refactor left the heap full of small
   records — exactly the shape that benefits from 8-byte instead of 12-byte object headers.
   Write a probe as a compact source file (JEP 512), e.g. `HeapProbe.java`:

   ```java
   void main() {
       record Card(String number, int month, int year) {}
       var cards = new Card[2_000_000];
       for (int i = 0; i < cards.length; i++) cards[i] = new Card("4242424242424242", 12, 2030);
       System.gc();
       long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
       System.out.printf("%,d objects, ~%.1f MB heap used%n", cards.length, used / 1e6);
   }
   ```

   Run it both ways on the Homebrew JDK 25 (`JAVA_HOME=/opt/homebrew/opt/openjdk`):

   ```bash
   $JAVA_HOME/bin/java -Xmx1g -XX:-UseCompactObjectHeaders HeapProbe.java
   $JAVA_HOME/bin/java -Xmx1g -XX:+UseCompactObjectHeaders HeapProbe.java
   ```

   The flag is a product option in Java 25 (off by default). Expect roughly 10–20% less
   heap for small-object-dominated workloads; cross-check with
   `jcmd <pid> GC.heap_info` if you want numbers the free/total arithmetic can't fudge.

2. **A taste of structured concurrency (still preview).** In a scratch file (keep this
   project preview-free), fan out the fraud check and the regional-limit check as concurrent
   subtasks with `StructuredTaskScope`, and observe automatic cancellation when one fails —
   the research doc's §1c exercise. Run with
   `java --enable-preview Fanout.java`. Project 14 does this properly.

3. **Compact source files for real.** Rewrite any demo/probe from this lesson per JEP 512
   (instance `main`, no class declaration, `IO.println`) and run it directly with
   `java File.java` — the 2026 answer to "Java is too ceremonial to teach."

## Resources

- Brian Goetz — [Data-Oriented Programming in Java](https://www.infoq.com/articles/data-oriented-programming-java/) (InfoQ) — the founding article for the style this lesson teaches
- Nicolai Parlog — [Data-Oriented Programming v1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/) (inside.java) — refined DOP principles, plus the [inside.java](https://inside.java) JEP Café series (José Paumard) for scoped values and pattern matching
- Sven Woltmann — [HappyCoders: Java 25 features](https://www.happycoders.eu/java/java-25-features/) — per-release deep dives, including flexible constructor bodies and compact headers
- Ben Evans, Jason Clark, Martijn Verburg — *The Well-Grounded Java Developer, 2nd ed.* (Manning) — chapters on the modern type system and concurrency
- JEPs, all on openjdk.org: [395 Records](https://openjdk.org/jeps/395) · [409 Sealed Classes](https://openjdk.org/jeps/409) · [440 Record Patterns](https://openjdk.org/jeps/440) · [441 Pattern Matching for switch](https://openjdk.org/jeps/441) · [506 Scoped Values](https://openjdk.org/jeps/506) · [512 Compact Source Files](https://openjdk.org/jeps/512) · [513 Flexible Constructor Bodies](https://openjdk.org/jeps/513) · [519 Compact Object Headers](https://openjdk.org/jeps/519)
- Keyhole Software — [What's New in Java 25](https://keyholesoftware.com/java-25-whats-new/)
- This repo — [research: platform & production](../docs/research/platform-and-production.md), sections 1a and 1c, for the 2026 context this lesson is built on
