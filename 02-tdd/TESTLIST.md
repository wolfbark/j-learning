# TESTLIST — the Canon TDD scenario list

Kent Beck's Canon TDD starts here: **step 1 is writing a list of test scenarios**, in plain
words, before any test code. This file is that list, pre-seeded. The rules of the loop:

1. Pick **exactly one** unchecked item.
2. Turn it into a concrete failing test (red). Never start a second test while one is red.
3. Make it pass without breaking anything (green) — faking is legal.
4. Refactor if the code asks for it.
5. Check the item off, **add any new scenarios you discovered while implementing**, repeat.

The list is yours: reword items, split them, strike ones that turn out to be wrong,
append ones the code teaches you. A growing-and-shrinking list is the sign you're doing
it right. Items marked `[x]` below are the worked examples shipped with the scaffold.

## Display contract (shared by both rounds)

| Situation | Message |
|---|---|
| Idle, no credit | `INSERT COIN` |
| Idle, something stocked, float can't break a quarter | `EXACT CHANGE ONLY` |
| Credit in the machine | Balance, e.g. `$0.40` |
| Successful vend | `THANK YOU` (one-shot) |
| Selection costs more than the credit | `PRICE $1.00` (one-shot) |
| Selection is out of stock | `SOLD OUT` (one-shot) |

One-shot messages show once, then the display falls back to the balance (if any credit
remains) or the idle message. In the classicist round "one-shot" means: the *next read*
of `display()` gets the message, the read after that gets the fallback; any new coin or
button event replaces a pending one-shot. Format money with integer cents — e.g.
`String.format(Locale.ROOT, "$%d.%02d", cents / 100, cents % 100)` — never floats.

**House rule for exact change:** the machine "can make change" when its float can pay
out 25¢ using nickels and dimes only (it can break a quarter). If any product is stocked
and the float can't do that, the idle display reads `EXACT CHANGE ONLY`.

## Round 1 — classicist: core vending (step 1)

- [x] An idle machine displays `INSERT COIN` *(the worked first cycle — see README)*
- [ ] Inserting a nickel shows `$0.05`
- [ ] Credit accumulates: nickel then dime shows `$0.15`
- [ ] A penny is not credited; it lands in the coin return, display unchanged
- [ ] With $1.00 in, selecting COLA puts a cola in the dispense bin
- [ ] After a vend the display reads `THANK YOU` once, then `INSERT COIN`
- [ ] After a vend the credit is zero
- [ ] Selecting with too little credit shows `PRICE $1.00` once, then the balance
- [ ] Selecting with no credit shows `PRICE …` once, then `INSERT COIN`
- [ ] Exact payment leaves the coin return empty

## Round 2 — classicist: change and edges (step 2)

- [ ] Paying $0.75 for $0.50 candy returns 25¢ to the coin return
- [ ] Change is paid from the float — denominations need not match what was inserted
- [ ] The coin-return button refunds the full credit
- [ ] The coin-return button resets the display to `INSERT COIN`
- [ ] Selecting an unstocked product shows `SOLD OUT` once, then balance / `INSERT COIN`
- [ ] Buying the last unit empties the slot — the next attempt is `SOLD OUT`
- [ ] Stocked machine + float that can't break a quarter → idle reads `EXACT CHANGE ONLY`
- [ ] Loading nickels/dimes into the float switches the idle display back to `INSERT COIN`

## Round 3 — outside-in: same features from the boundary (step 3)

- [x] An unrecognised object goes straight to the coin return *(the worked example)*
- [ ] A recognised coin grows the escrow; the display shows the new balance
- [ ] Valid selection with enough credit: dispense, then `THANK YOU`
- [ ] Not enough credit: `PRICE $X.XX`, nothing dispensed, escrow kept
- [ ] Overpayment: change released through the coin return
- [ ] Out of stock: `SOLD OUT`, escrow kept
- [ ] Unknown keypad code: you decide what happens — record the decision here
- [ ] The return button refunds the whole escrow
- [ ] Rejected objects never enter the escrow

## Parking lot — discovered scenarios (append your own)

- [ ] What if the return button is pressed with an empty escrow?
- [ ] What if the same product is selected twice quickly?
- [ ] Can the float go negative? What stops it?
- [ ]
