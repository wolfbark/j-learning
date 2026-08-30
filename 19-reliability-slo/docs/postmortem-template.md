# Incident postmortem — <short descriptive title>

> Fill this in during step 7's drill, from what your logs and metrics told you.
> Blameless means the document names systems, defaults, alerts and gaps — never
> people. "An engineer deployed X" is fine; "Sam broke it" is not, and "human
> error" is a description of a system that permitted it.

| | |
|---|---|
| **Incident id** | INC-… |
| **Severity** | S1 / S2 / S3 (and what your definitions are) |
| **Detected at** | (UTC) — and by what: alert, dashboard, or a customer |
| **Mitigated at** | (UTC) |
| **Duration of impact** | |
| **Author / reviewers** | |

## Impact

What a *user* could not do, for how long, and how many of them. Numbers, from
your SLIs — not "the service was degraded".

- Availability SLI over the incident window:
- Latency SLI over the incident window:
- Error budget consumed (% of the 30-day budget), and peak burn rate:

## Timeline (UTC)

Facts only, one line each, with the evidence you used. A timeline you cannot
source from a log line or a graph is a memory, not a timeline.

| Time | Event | Evidence |
|------|-------|----------|
| | first bad request | correlation id …, `settlement.completed` with `outcome=failure` |
| | metric crossed threshold | which meter, what value |
| | detection | how |
| | diagnosis | the metric or log that made it obvious |
| | mitigation | what was changed |
| | verified recovered | which number returned to normal |

## Diagnosis: how you found it

Which signal identified the failure mode, and how long it took. Then the more
useful question: **which signal should have found it faster, and did it exist?**

## Contributing factors

Plural, and none of them a person. For each: was it a design decision, a default
nobody chose, or a gap in what we can see?

1.
2.
3.

## What went well

Detection, tooling, a runbook that worked, an SLO that fired at the right time.
Say it explicitly — this is how good practice survives reorganisation.

## Where we got lucky

The part most postmortems skip, and the most predictive section in the document.
What would have made this materially worse (time of day, one more dependency,
one fewer instance)?

## Action items

Owners are individuals, dates are real, and each item is small enough to finish.
"Improve monitoring" is not an action item.

| # | Action | Type (prevent / detect / mitigate) | Owner | Due |
|---|--------|------------------------------------|-------|-----|
| 1 | | | | |
| 2 | | | | |

## SLO consequences

- Is the error budget for this window exhausted?
- What does your policy say happens next (freeze risky changes, reprioritise
  reliability work, revisit the objective because it never matched the product)?
- If the SLO was met and users were still unhappy, the SLI is measuring the
  wrong thing. Write that down here, because it is the most valuable sentence in
  the document.
