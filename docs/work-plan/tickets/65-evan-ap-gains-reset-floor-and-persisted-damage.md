# 65 - Evan's AP: the magician spread, the missing floor, and the damage already persisted

**Class:** v84 parity
**Work rows:** R41, R42, R43 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None technically - all three edits can be written today. **All three are owner-gated
on numbers and consent** (OWNER Q4, Q5, Q6). Nothing here may be applied to the live database
without an explicit yes.

These are the most character-damaging rows on the list, and none of them was in the old tracker. An
Evan gains the wrong stats per AP point, has no AP-reset floor at all, and the owner's own character
carries persisted damage from a bug that is already fixed in code. One root cause runs through the
first two: `Job.isA` compares `id/100`, which is **22** for Evan's 2200 and **2** for `Job.MAGICIAN`,
so every branch written as `job.isA(Job.MAGICIAN) || ...` silently drops Evan into the trailing
default.

v84 ships Evan as a magician-type class, and this codebase already says so:
`Character.getJobStyleInternal` maps 2200/100 to `Job.MAGICIAN`. The class intent is stated in our
own code; only the AP branches disagree with it.

## R41 - an Evan gains 6 MP per AP point where a magician gains 18

Four sites, all in `AssignAPProcessor.java`, all keyed the same way:

* `:669` `calcHpChange`
* `:764` `calcMpChange`
* `:842` `takeHp`
* `:862` `takeMp`

Measured by `AssignAPProcessorTest.evanIsNotClassifiedAsAMagicianForApGains`: a magician gets
**6 HP / 18 MP**, an Evan gets **8 HP / 6 MP**.

The fix is one disjunct per site - `|| job.isA(Job.EVAN1)` - and `Job.isA` itself is left alone. It
is load-bearing everywhere, and ticket 52 established the pattern of naming Evan in the individual
chains instead, the way commit `f7657c736` did.

**This is a two-way change.** MP per point goes 6 -> 18, but HP per point goes 8 -> 6, and **every
point already spent stays mis-valued**. That is OWNER Q4: whether to correct the code only, or to
also recompute the already-spent points on the live character.

## R42 - Evan has no AP-reset floor, so the guard can never fire

`AssignAPProcessor.java:882` `getMinHp` and `:933` `getMinMp` return **0** for jobs 2000, 2001 and
2200-2218. A magician at the same level returns **2054 / 4399**. `YamlConfig` `USE_ENFORCE_HPMP_SWAP`
is **false**, so there is no second guard behind it - as ticket 52 recorded, that flag only forces
the swap *direction*; the `getMinHp`/`getMinMp` guards run unconditionally.

**The numbers are OWNER Q5 and cannot be derived here.** Copying magician's constants is a guess
about Evan's actual pool, and setting the floor too high converts a silent gap into a false refusal
on a character that could legally swap yesterday. Ticket 51 item 3 raised it; ticket 52 corrected the
record that it is live rather than dormant.

## R43 - the repair for damage already written to the database

Both statements are already written out verbatim in
`docs/work-plan/tickets/52-evan-job-dependent-sweep.md` under "Data correction needed on existing
characters". The code defect is fixed; this is the repair.

1. **Monster-card sets wrote +1 STR instead of +1 INT**, and it persisted.
   `SELECT COUNT(*) FROM monsterbook WHERE charid = 50 AND isGainedMainStatBuff = 1;` gives `n`;
   the repair is `str = str - n`, `int = int + n` for that character.
2. **Stale `skills` rows for skill 20000012** (Legend's Blessing of the Fairy), written by every Evan
   login and no longer read. They grant nothing and cost nothing; delete at leisure via
   `DELETE FROM skills WHERE skillid = 20000012 AND characterid IN (SELECT id FROM characters WHERE
   job BETWEEN 2200 AND 2218 OR job = 2001);`

OWNER Q6: it is his character and his database. Agents are **SELECT only**; the repair ships as a new
Liquibase changeSet and is applied by the owner, never by an agent's `mysql` invocation.

## Precedent

* Naming Evan in a job chain rather than touching `Job.isA`: commit `f7657c736`, and the twelve sites
  ticket 52 already fixed this way.
* `Character.getJobStyleInternal`'s 2200 -> `Job.MAGICIAN` mapping is the in-repo statement that Evan
  is a magician-type class.
* The two SQL statements in R43 are copied from ticket 52, not re-derived.
* Ticket 52's "Balance changes the owner did not ask for" is the template for how a live-effect
  change gets written down before it ships.

## Acceptance criteria

- [ ] `AssignAPProcessorTest` asserts an Evan gains **6 HP / 18 MP** per AP point at the same level a
      magician does, at all four sites (`calcHpChange`, `calcMpChange`, `takeHp`, `takeMp`).
- [ ] `AssignAPProcessorTest.evanIsNotClassifiedAsAMagicianForApGains` is inverted or replaced, so
      the suite records the new behaviour rather than the old.
- [ ] `Job.isA` is unchanged - the diff touches only the four disjuncts and the two floor methods.
- [ ] `getMinHp` and `getMinMp` return the owner-supplied Evan floor for 2200-2218, and a test
      asserts an AP-reset swap below it is refused with "You don't have the minimum HP pool required
      to swap".
- [ ] The floor numbers, and where they came from, are written into the ticket's Delivered section.
      If the owner supplies no number, this half stays open and is **not** filled with magician's
      constants.
- [ ] A new Liquibase changeSet carries the STR/INT correction and the 20000012 delete, with the
      measured `n` recorded in its header comment.
- [ ] After the changeSet runs, `SELECT str, int FROM characters WHERE id = 50` matches the value the
      header predicts, and `SELECT COUNT(*) FROM skills WHERE skillid = 20000012` returns 0 for every
      Evan.

`-Dtest=AssignAPProcessorTest`. **Do not run maven while sibling agents are active.**

## Do not

- Do not change `Job.isA` or `getJobType`. Both are load-bearing across the whole tree; ticket 52
  settled that Evan gets named in the chains instead.
- Do not invent Evan's HP/MP floor. A wrong number refuses a legal swap on a live character.
- Do not run an `UPDATE` or `DELETE` against the live database from an agent. SELECT only; changes go
  through a changeSet the owner applies.
- Do not apply the R41 code fix and quietly leave the already-spent points unaddressed. Whichever way
  the owner decides, the decision goes in the ticket.
