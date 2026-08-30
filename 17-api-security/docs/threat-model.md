# Threat model — Expense API

> Step 1 of the lesson. This file is deliberately unfinished: the first row of each table is worked
> through for you, the rest is yours. Budget 30–45 minutes. Do it *before* you read any code, then
> fix it afterwards — noticing what you missed is most of the value.

A threat model is not a document, it is an argument you can be wrong about in public. Four questions,
in order (Adam Shostack's framing): **What are we building? What can go wrong? What are we going to do
about it? Did we do a good job?**

---

## 1. What are we building?

The expense API lets employees file expense reports and lets managers approve the reports of the
teams they manage. Identity comes from Keycloak; the API validates access tokens and never sees a
password. State lives in Postgres. Receipts can be pulled from a URL. One legacy endpoint
(`/session/preferences`) still authenticates with a session cookie.

### Assets

| # | Asset | Why an attacker wants it | Worst case if lost |
|---|-------|--------------------------|--------------------|
| A1 | Expense report contents (merchant, amount, category, owner) | Reveals travel patterns, deals in progress, who met whom | Confidentiality breach; competitive and personal harm |
| A2 | *(you)* Card data on the create path | | |
| A3 | *(you)* The approval decision itself | | |
| A4 | *(you)* Employee identifiers (username, email) | | |
| A5 | *(you)* The service's own network position (it can make outbound HTTP calls) | | |
| A6 | *(you)* Credentials and signing material | | |

### Data classification

Fill in one row per data element the API touches. The point of the exercise is that "personal data"
and "secret" are different categories with different rules.

| Data element | Class | Where it lives | May appear in logs? | Retention |
|--------------|-------|----------------|--------------------|-----------|
| `amount_cents`, `merchant` | Internal / personal | Postgres, API responses | Aggregates only | Per finance policy (7 years) |
| Full card number | *(you — hint: PCI DSS says something very specific about this)* | | | |
| `card_last4` | | | | |
| Email address | | | | |
| Access token | | | | |
| Audit event (subject, action, object, decision) | | | | |

### Trust boundaries

Draw these as a picture if you think better that way; a list is fine too. A trust boundary is any
place where data crosses from something you control to something you do not — or from one privilege
level to another.

1. **Browser / mobile app → API.** Everything crossing this boundary is attacker-controlled: path
   variables, query parameters, headers, and every field of every JSON body. The *token* is the only
   thing here you can trust, and only after you have validated it.
2. **API → Keycloak.** *(you: what exactly do you trust Keycloak for, and what do you still have to
   check yourself?)*
3. **API → Postgres.** *(you)*
4. **API → arbitrary receipt host.** *(you: which direction does the danger flow here?)*
5. **API → log aggregator.** *(you: who can read your logs? Is that the same set of people who may
   read A1 and A2?)*

---

## 2. What can go wrong? (STRIDE-lite)

One row per (asset, boundary) pair worth arguing about. Keep it short — a threat model that takes two
days is a threat model nobody updates. Severity is your judgement call; write the reasoning, not just
the letter.

| # | Boundary | STRIDE | Threat, concretely | Existing control | Severity | Step |
|---|----------|--------|--------------------|------------------|----------|------|
| T1 | Client → API | **I**nformation disclosure / **T**ampering | An authenticated employee changes the id in `GET /api/expenses/{id}` and reads a colleague's report; with `PUT` they can edit it. Authentication proves *who* they are and says nothing about *which objects* they may touch. | None — the query is `where id = ?` | **High**: any user, any record, no tooling, no trace in the logs beyond a normal-looking 200 | 4 |
| T2 | Client → API | S | *(you: what does a request with no token get today?)* | | | 2 |
| T3 | Client → API | E | *(you: what stops an employee from calling `approve`?)* | | | 3 |
| T4 | API → receipt host | *(you)* | *(you: what can a URL you fetch on the client's behalf reach that the client cannot?)* | | | 6 |
| T5 | API → log aggregator | I | *(you)* | | | 7 |
| T6 | Browser → legacy session endpoint | *(you)* | *(you: what can `evil.test` make a logged-in admin's browser do?)* | | | 5 |
| T7 | Client → API | D | *(you: how many approvals per second can one manager perform, and why is that a problem?)* | | | 7 |
| T8 | Repository → anyone with `git clone` | *(you)* | *(you)* | | | 7 |

STRIDE, for reference: **S**poofing (pretending to be someone), **T**ampering (changing data),
**R**epudiation (denying you did it), **I**nformation disclosure, **D**enial of service,
**E**levation of privilege.

### Two threats that are *not* in this model

Being explicit about what you are not defending against is part of the work.

- *(you: name one thing you are consciously accepting, and why — e.g. a compromised Keycloak, a
  malicious DBA, a stolen laptop with a valid refresh token.)*

---

## 3. What are we going to do about it?

That is steps 2–7. When you finish the lesson, come back and fill in the "Existing control" column
with what you actually built, and mark anything still open.

## 4. Did we do a good job?

**Done when** this file has:

- every asset row filled in, with a worst case you would be willing to say out loud in an incident review;
- a data classification table where at least one row says "must never be stored" and one says
  "must never be logged";
- all five trust boundaries described, including which direction the danger flows;
- at least eight STRIDE rows, each mapped to a lesson step or explicitly marked as accepted risk;
- one threat you found that this lesson does *not* cover.

Then read `README.md` §"The project" and see how many of the six planted flaws you had already
predicted from the outside. That number is the score for this step.
