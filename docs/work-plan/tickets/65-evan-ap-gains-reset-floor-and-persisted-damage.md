# 65 - Evan's AP: two rows already closed, and the damage that was never there

**Class:** v84 parity
**Work rows:** R41, R42, R43 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None. R41 and R42 are **DONE**; R43 item 2 is the only open work and needs no
decision.

This ticket used to describe three open defects. Two of them were fixed on 2026-08-17 while the
ticket sat unrevised, and the third turns out to be aimed at the wrong character. What follows is
the corrected record.

## R41 - DONE. An Evan gains the magician spread

**Fixed by commit `f7657c736` "Evan gains HP and MP as the magician he is" (2026-08-17).**

`Job.isA` compares `id/100`, which is 22 for Evan's 2200 and 2 for `Job.MAGICIAN`, so every branch
written as `job.isA(Job.MAGICIAN) || ...` silently dropped Evan into the trailing default. All four
sites in `AssignAPProcessor.java` now carry `|| job.isA(Job.EVAN1)`:

| site | line in HEAD |
|---|---|
| `calcHpChange` | `:706` |
| `calcMpChange` | `:782` |
| `takeHp` | `:851` |
| `takeMp` | `:871` |

`Job.isA` itself was left alone, as ticket 52 established.

**Proved by `AssignAPProcessorTest.evanGainsHpAndMpAsAMagician` (`:300`)**, which asserts 6 HP and
18 MP per AP point for jobs 2200 and 2218 and passes.

> The earlier revision of this ticket cited `f7657c736` as the *precedent* for the fix. It is the
> fix. It also cited a test named `evanIsNotClassifiedAsAMagicianForApGains`, which does not exist
> in `AssignAPProcessorTest` and never did under that name. Its line numbers (`:669`, `:764`,
> `:842`, `:862`) were 37, 18, 9 and 9 lines short of the real sites.

**Nothing is owed on the already-spent points.** `f7657c736`'s message records that the loss is
permanent because MP is banked per point as it is spent. Recovering it would require knowing how
many points were spent before the fix, and that number is not stored anywhere. The code is correct
going forward; that is the whole of R41.

## R42 - DONE. Evan and the beginner jobs have their AP-reset floor

**Fixed by commit `48a413961` "Evan and Legend get the AP-reset HP/MP floor they were missing",
corrected by `a4f804f78` "Correct the record: the AP-reset floors are live, not dormant"
(both 2026-08-17).**

`getMinHp` (`:886`) and `getMinMp` (`:945`) previously matched no branch for 2001, 2200-2218 or
Aran's 2000 and returned **0**, so the guard at `:560` / `:589` could never fire. Both methods now
carry `Job.EVAN1`, `Job.EVAN2` and `Job.EVAN` branches with the derivation written into the code as
comments.

**The numbers were derivable and were derived** - the earlier revision claimed they could not be
and gated the row on an owner-supplied number:

* **HP.** `levelUp()` gives Evan the magician's own `rand(20, 28)`, and `changeJob()` grants mages no
  HP on advancement, which is why the magician HP branch has no second-job tier. All of 2200-2218
  take `multiplier 10 / offset 54`.
* **MP.** The magician tier split (`-1` for job 200, `449` for 210+) is a fit to what `changeJob()`
  pays out, and `changeJob` keys on `getId() % 1000`. Evan's 2200 lands on "1st mage" (+100~150 MP)
  and 2210-2218 on "2nd~4th mage" (+450~500 MP), so they take `-1` and `449`.

**Proved by `AssignAPProcessorTest.evanAndLegendTakeTheirClassMinimumHpMpFloor` (`:259`)**, which
asserts 2054 HP / 4399 MP at level 200 for jobs 2200 and 2218 - the same values a magician gets -
and passes.

### Job 2001 takes the beginner floor, and that is the owner's decision

`Job.EVAN` (2001, Evan's pre-first-growth job, distinct from `Job.EVAN1` = 2200) falls to the
beginner branch at `getMinHp:936` / `getMinMp:1000` alongside `BEGINNER`, `NOBLESSE` and `LEGEND`,
giving `12/38` HP and `10/-5` MP - 2438 / 1995 at level 200. `levelUp()` runs one beginner branch
for jobs 0, 1000, 2000 and 2001 alike, so the floor is consistent with what the job actually gains.

**The owner has decided to leave this as is. It is not a defect and no work is filed against it.**
`evanAndLegendTakeTheirClassMinimumHpMpFloor` pins the values at `:262` and `:267`, so the decision
cannot drift silently.

`USE_ENFORCE_HPMP_SWAP` is `false` (`config.yaml:242`), and `a4f804f78` established that this does
**not** make the floors dormant: that flag only forces the swap *direction*, and the two
minimum-pool guards run unconditionally. The comment at `AssignAPProcessor.java:556-559` states it
in the code.

## R43 - the repair, re-aimed and mostly empty

The code defect behind item 1 is fixed - `MonsterBook.applyMainStatBuff:131` now names
`Job.EVAN1` and `Job.EVAN` in the INT branch (commit `a50bff451`). What remains is the question of
what it left behind in the database, and the answer measured today is: **almost nothing.**

### 1. Monster-card STR/INT - wrong character, and probably a no-op

The statement copied from ticket 52 targets **charid 50**. Measured against the live database:

```
SELECT COUNT(*) FROM monsterbook WHERE charid = 50 AND isGainedMainStatBuff = 1;  -- 0
SELECT COUNT(*) FROM monsterbook WHERE charid = 50;                               -- 0
```

Character 50 ("evan", job 2200) has **no `monsterbook` rows at all**, so `n = 0` and the repair
`str = str - n, int = int + n` is a no-op. **Ticket 52's statement is wrong about which character
it applies to, and this ticket inherited the error verbatim.**

The only Evan in this database with completed card sets is **charid 51** ("evan2", job 2210):

```
SELECT c.id, c.name, c.job, c.str, c.`int`, COUNT(*)
  FROM characters c JOIN monsterbook m ON m.charid = c.id AND m.isGainedMainStatBuff = 1
 WHERE c.job = 2001 OR (c.job BETWEEN 2200 AND 2218) GROUP BY c.id;
-- 51  evan2  2210  str=4  int=124  n=3
```

**Even charid 51 shows no damage.** The `characters.str` column default is **12**; charid 51 reads
**4**. Three mis-credited `+1 STR` would sit on top of whatever the character actually has, and
nothing above the floor is visible. Either the three sets were completed after `a50bff451` landed,
or the STR was reset away afterwards - the database does not record which, and a `monsterbook` row
carries no timestamp to settle it.

**Decision, from the data:** no STR/INT correction ships. `n = 0` on the character the ticket named,
and the character it should have named shows no recoverable discrepancy. Writing an `UPDATE` that
moves 3 points on charid 51 would be guessing at damage that cannot be shown to exist, against the
standing rule that a value which cannot be derived does not get invented. If the owner later
observes a real STR/INT discrepancy on charid 51, this row reopens with that observation as its
evidence.

Note also that the earlier revision's acceptance criterion, `SELECT str, int FROM characters WHERE
id = 50`, is not valid SQL - `int` is reserved and needs backticks.

### 2. Stale `skills` rows for 20000012 - the one genuinely open item

Every Evan login used to write a `skills` row for **20000012** (Legend's Blessing of the Fairy).
Evan's own constant is **20010012** (`constants/skills/Evan.java:5`), so 20000012 is no longer
written for Evans and no longer read. The rows grant nothing and cost nothing.

Eight rows exist today:

```
SELECT skillid, characterid FROM skills WHERE skillid = 20000012;
-- characterids 4, 8, 17, 19, 21, 27, 48, 50
```

Only **48** and **50** are Evans (both job 2200; charid 51 has no such row). Charids **17** and
**27** are job **2110** - Aran third job - where 20000012 is Legend's own skill and the row is
**legitimate**. Charids 4, 8, 19 and 21 are jobs 230, 1100, 421 and 300 and are outside the delete's
scope.

The delete is therefore correctly narrow as written:

```sql
DELETE FROM skills WHERE skillid = 20000012
  AND characterid IN (SELECT id FROM characters WHERE job BETWEEN 2200 AND 2218 OR job = 2001);
```

It removes exactly **2** rows (charids 48 and 50) and spares the two Aran rows. This is the only
work this ticket still owes.

## Precedent

* `f7657c736`, `48a413961` and `a4f804f78` are the three commits that closed R41 and R42. They are
  the record, not a pattern to copy.
* Naming Evan in a job chain rather than touching `Job.isA`: the twelve sites ticket 52 fixed.
* `Character.getJobStyleInternal:425-426` maps `Job.EVAN1.getId() / 100` to `Job.MAGICIAN` - the
  in-repo statement that Evan is a magician-type class.
* Correcting an already-applied row with a new changeSet rather than editing a frozen one:
  changeSets 164, 165 and 167.

## Acceptance criteria

- [ ] A new Liquibase changeSet carries the 20000012 `DELETE` exactly as written above, scoped to
      jobs 2200-2218 and 2001.
- [ ] Its header records that charids **17** and **27** (job 2110, Aran) hold legitimate 20000012
      rows and are deliberately spared, and that the delete is expected to remove **2** rows.
- [ ] After it runs, `SELECT COUNT(*) FROM skills WHERE skillid = 20000012` returns **6**, and
      returns **0** when restricted to characters whose job is 2001 or 2200-2218.
- [ ] The changeSet contains no `UPDATE` to `characters.str` or `` characters.`int` ``. R43 item 1
      closed as a no-op; see above.
- [ ] No change to `AssignAPProcessor.java`. R41 and R42 are closed; a diff there means this ticket
      was misread.

`-Dtest=AssignAPProcessorTest` still passes untouched. **Do not run maven while sibling agents are
active** - they collide on `target/`.

## Do not

- Do not re-apply R41 or R42. All four AP branches already carry `|| job.isA(Job.EVAN1)` and both
  floor methods already carry their Evan branches in HEAD. Re-adding a disjunct that is already
  there is the failure this ticket was rewritten to prevent.
- Do not file job 2001's beginner floor as a defect. The owner has decided to leave it.
- Do not change `Job.isA` or `getJobType`. Both are load-bearing across the whole tree.
- Do not write a STR/INT `UPDATE` for charid 50. It has no `monsterbook` rows; `n` is 0.
- Do not widen the 20000012 delete to all characters. Charids 17 and 27 are Arans and their rows are
  real.
- Do not run an `UPDATE` or `DELETE` against the live database from an agent. SELECT only; changes go
  through a changeSet the owner applies.
