# VLEARN-482 — Checkout: member discounts + loyalty points

**Type:** Story · **Priority:** High · **Sprint:** 2026-34 · **Reporter:** P. Aalto (Product)
**Labels:** `checkout` `growth-experiment` `needs-estimate`

## Description

Growth wants the loyalty programme live before the autumn course launch. From the pricing
one-pager:

> Members get 10% off orders over €100, gold members 20%, plus 1 loyalty point per €10
> spent; points can pay for orders (100 points = €10); discounts don't stack with point
> payments... probably?

Marketing also has two campaign codes in the CMS already (`WELCOME15`, `SPRING5`) so
whatever you build should keep working with those.

## Acceptance criteria

- [ ] Members see their discount at checkout
- [ ] Points are shown before and after the order
- [ ] Nothing breaks for guests

## Notes

Finance says the numbers "just need to add up". Legal has no opinion. Same behaviour on
web and mobile — mobile team will call whatever API you expose.

Happy to answer questions, but I'm at a conference Tue–Thu and this is estimated at
3 points, so hopefully it's straightforward. 🙏

---

*(This is the input to the lesson, not a specification. Read `../README.md` step 1 before
you write any code — including before you ask an AI assistant to write any code.)*
