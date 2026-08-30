# Example mapping — facilitation guide

Example mapping (Matt Wynne, 2015) is a 25-minute, four-colour card workshop that turns one
user story into a set of agreed, concrete examples — and, more importantly, into an explicit
list of the things nobody knew yet. It is the cheapest part of BDD and the part teams skip.

> **Do not scroll to the bottom of this file yet.** The last section is the answer key. It
> exists so that a solo learner gets the same outcome as a group, and so the reference
> feature file (`../src/test/resources/features/pricing.feature.EXPECTED`) is reproducible.
> Reading it before you run the session turns a discovery exercise into a transcription
> exercise.

## The four cards

| Colour | Card | Contains | Rule of thumb |
|---|---|---|---|
| **Yellow** | Story | The one story under discussion. Exactly one per session. | If you need two, you have two sessions. |
| **Blue** | Rule | An acceptance criterion — one business rule, stated as a rule, not as an example. | "Tier discount applies only above a threshold" |
| **Green** | Example | A concrete illustration of one rule: real numbers, real names, one outcome. | "€100.00 for a member → no discount" |
| **Red** | Question | Anything nobody in the room can answer, or any disagreement. | Never guess to clear a red card. |

Cards sit under the rule they belong to. Green cards hang off blue cards; red cards hang off
whatever they block. The board *is* the meeting minutes.

## Roles and timebox

- **Product owner / domain expert** — owns the rulings. The only person who may turn a red
  card into a blue or green one.
- **Developer(s)** — hunt for edge cases, boundaries, rounding, error paths, and states the
  system can be in. Your job in the room is to be *specifically* awkward.
- **Tester / QA** — hunts for the examples nobody wants: zero, negative, exactly-on-the-
  boundary, twice-in-a-row, and "what if the customer does both at once".
- **Facilitator** — keeps the timebox, keeps examples concrete, and refuses to let the group
  design the solution.

**25 minutes, hard stop.** Longer means the story is too big; that is a result, not a
failure. Typical rhythm: 3 min to read the story, 5 min to write down the rules everyone
*thinks* they know, 12 min turning rules into examples (this is where the red cards appear),
5 min to review and decide what to do with what is left.

### Done when

- Every blue rule has **at least two** green examples — one that illustrates it and one that
  bounds it.
- Every red card is either **answered** (and has become a blue/green card) or **explicitly
  deferred** with a named owner and a decision about what to build meanwhile.
- Somebody can say out loud what is *not* in scope.

### Anti-patterns to catch yourself doing

- **Examples that restate the rule** ("a member gets a discount") — that is a rule with
  different words. An example has numbers in it.
- **Designing** ("we'll store the balance in a `points_ledger` table") — park it. Mapping is
  about behaviour, not implementation.
- **Writing Gherkin in the session.** Cards are faster and less precious. Gherkin comes
  after, and only for the examples worth automating.
- **The PO not being in the room.** This is the failure mode that killed BDD's reputation
  (see the README's honest framing). If they cannot come, the session moves; it does not
  proceed without them.

## Blank mapping template

Copy this into your notes and fill it in during the session. One table per rule.

```markdown
## Story (yellow)
As a … I want … so that …

### Rule 1 (blue): …

| # | Example (green) — concrete, with numbers | Expected outcome |
|---|------------------------------------------|------------------|
| 1.1 |  |  |
| 1.2 |  |  |

### Rule 2 (blue): …

| # | Example (green) | Expected outcome |
|---|-----------------|------------------|
| 2.1 |  |  |
| 2.2 |  |  |

### Questions (red)

| # | Question | Ruling | Who decided | Status |
|---|----------|--------|-------------|--------|
| Q1 |  |  |  | open / answered / deferred |

### Out of scope
- …
```

## Running the session solo, against an AI product owner

Paste the prompt below into a fresh chat with a capable model, then interview it exactly as
you would interview a human PO: one question at a time, in business language, pushing for a
concrete number on every answer. Do not paste your list of ambiguities in one go — the value
of the exercise is in how the conversation branches when an answer surprises you.

The persona is deliberately a *realistic* PO: decisive, occasionally inconsistent, and prone
to answering a different question than the one you asked. When it drifts, pin it down:
"so for an order of exactly €100.00, the discount is zero — yes or no?"

````text
You are Pia Aalto, Product Owner for the checkout team at a European online training
platform. I am a developer on your team. We are running a 25-minute example-mapping session
on ticket VLEARN-482, which you wrote:

  "Members get 10% off orders over €100, gold members 20%, plus 1 loyalty point per €10
   spent; points can pay for orders (100 points = €10); discounts don't stack with point
   payments... probably?"

Two promo codes already exist in the CMS: WELCOME15 (15% off) and SPRING5 (5% off).

How to play the role:
- You know the business intent, not the implementation. Answer in business language.
- Answer ONLY the question I actually asked. Do not volunteer the rest of the rules, do not
  produce a summary table, and never write Gherkin or code.
- Keep answers under three sentences.
- You are decisive: when I ask about a boundary or an edge case, you make a ruling rather
  than saying "good question, I'll get back to you". You may reason out loud for one
  sentence first ("hmm, finance would hate that, so…").
- Stay consistent with every ruling you have already given in this conversation. If I point
  out that two of your rulings contradict each other, pick one, say which you are dropping,
  and explain the business reason in one sentence.
- You care about: revenue protection (no unlimited stacking), the loyalty programme feeling
  generous, not surprising customers with rounding, and never owing a customer money.
- If I ask a question that is really about implementation ("should we store points as an
  integer?"), say that is our call, not yours.

Start by greeting me and asking what I want to know first. Do not summarise the ticket.
````

When the session ends, ask the persona one last thing — *"give me your rulings as a numbered
list so I can check my notes"* — and diff that list against your own. Anything you wrote
down that it never said is tacit knowledge you invented. That is exactly the material that
leaks into code as a silent assumption.

---
---

# ⚠️ ANSWER KEY — after the session only

These are the rulings the reference solution implements, and the ones
`pricing.feature.EXPECTED` and `Checkpoint5PricingMathTest` encode. Your session may
legitimately have decided differently on any of them; the point of the exercise is having
*decided*, and the point of the answer key is that the repo has one consistent outcome to
compare against. If you diverged, note where — and then implement your own rulings and edit
the checkpoint test to match. Divergence with a written reason is a pass.

| # | Latent ambiguity in the ticket | Ruling |
|---|---|---|
| **A1** | "orders over €100" — measured on what? The goods subtotal, the discounted total, or what the customer actually pays after points? | The **goods subtotal as submitted**, before any discount and before any point redemption. Shipping is never part of it. |
| **A2** | Is an order of exactly €100.00 "over €100"? | **No.** Strictly greater than €100.00. €100.00 gets nothing; €100.01 gets the full tier discount. |
| **A3** | "discounts don't stack with point payments… probably?" | They **do** combine: the percentage discount applies first, then points pay against the discounted total. What Pia was afraid of was double-dipping on *earning*, not on paying — see A4. |
| **A4** | "1 loyalty point per €10 spent" — spent on the full price, the discounted price, or the cash actually paid? | On the **cash actually paid**: after the discount and after the point credit. Points spent do not earn points. |
| **A5** | Do percentage discounts round up, down, or to nearest? (10% of €107.55 is €10.755.) | **Half up to the nearest cent** — €10.76. Rounding in the customer's favour by a cent is cheaper than explaining a rounding policy. Points earned are **floored** to whole €10 (no partial points, ever). |
| **A6** | Can points be redeemed partially — 150 points, or 1 point? And what happens if the customer offers more points than the order is worth? | Redemption is in **whole 100-point blocks only** (€10.00 each). A request that is not a multiple of 100 is **rejected**. An over-large request is **clamped** to the number of whole blocks the amount due can absorb; the surplus stays in the balance. The amount due never goes below zero, and we never refund points as cash. |
| **A7** | Does a promo code stack with the tier discount? (Gold 20% + WELCOME15 = 35%?) | **No.** Percentage discounts never stack: the customer gets the **better of the two**. Gold + WELCOME15 is 20%, not 35%. A member below the €100 threshold still gets the promo code's 15%, because promo codes have no threshold. |
| **A8** | Guests: no discount, obviously — but do they earn points, and can they spend them? | Guests **earn nothing and redeem nothing**. Redeeming as a guest is **rejected**, not silently ignored. Only members and gold members participate in the loyalty programme. |

Two more that a good session usually surfaces, both **deferred** (out of scope here, worth
noting on the board):

- **A9** — refunds and partial returns: are earned points clawed back? *Deferred: separate
  story, owner Pia. Meanwhile the API has no refund path, so nothing to build.*
- **A10** — does a gold member's tier apply as of the order date or the payment date for
  subscriptions? *Deferred: no subscriptions at checkout yet.*

### Error messages (so the reference scenarios match yours)

The rejections are `IllegalArgumentException` with exactly these messages:

- `only members can redeem points`
- `points must be redeemed in blocks of 100`
- `not enough points`

### What the conversation caught

Coding straight from the ticket, the four assumptions a developer makes silently — and
which of them are wrong under these rulings:

1. "over €100" means `>=` (**wrong**, A2).
2. Points are earned on the order value (**wrong**, A4 — earned on cash paid).
3. `WELCOME15` adds to the tier discount because the ticket only forbade stacking with
   *points* (**wrong**, A7 — and note the ticket's "probably?" was pointing at the wrong
   risk entirely).
4. Point redemption is a simple subtraction (**wrong**, A6 — blocks, clamping, and a
   rejection path the ticket never mentions).

Every one of those is a plausible reading. That is the argument for the session, and it is
the whole lesson: none of these were caught by better testing, better types, or a better
prompt. They were caught by asking.
