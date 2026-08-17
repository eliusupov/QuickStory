# 51 - Job-id arithmetic: the rest of the Evan defects in jobs, stats and skills

Status: **one fixed, five reported.** Hunt for further instances of the defect class behind
ticket 50: Cosmic classifies jobs by arithmetic on the job id, and Evan's ids break the arithmetic's
assumptions. Evan's beginner job is **2001**, the only beginner id that is not a round thousand, and
Evan has **ten** advancements (2200-2218) where every other class has four.

Every finding below has a runnable assertion behind it. Nothing here is a lead.

Config as shipped (`config.yaml`) decides what is live and what is dormant, so it is quoted per
finding: `USE_ENFORCE_JOB_LEVEL_RANGE: false`, `USE_ENFORCE_JOB_SP_RANGE: false`,
`USE_STARTING_AP_4: false`, `USE_ENFORCE_HPMP_SWAP: false`, `USE_RANDOMIZE_HPMP_GAIN: true`,
`USE_AUTOASSIGN_STARTERS_AP: true`.

## Ranked

### 1 - FIXED. An Evan pays real job SP for Three Snails, and a beginner Evan cannot learn it at all

`src/main/java/client/processor/stat/AssignSPProcessor.java:72`

```java
if (skillid % 10000000 > 999 && skillid % 10000000 < 1003) {          // the free-skill window
    total += player.getSkillLevel(SkillFactory.getSkill(player.getJobType() * 10000000 + 1000 + i));
```

Three Snails / Recovery / Nimble Feet are free everywhere: the window sets `remainingSp` to a
level-derived budget and skips the `gainSp(-1, ...)` charge. Each beginner line has its own three,
at `<beginner job id> * 10000 + 1000..1002`:

| line | job | skills | `% 10000000` | in window |
|---|---|---|---|---|
| Explorer | 0 | 1000-1002 | 1000-1002 | yes |
| Noblesse | 1000 | 10001000-10001002 | 1000-1002 | yes |
| Legend (Aran) | 2000 | 20001000-20001002 | 1000-1002 | yes |
| **Evan** | **2001** | **20011000-20011002** | **11000-11002** | **no** |

So an Evan fell through to the paid path. Two live consequences:

- **Beginner Evan (job 2001) cannot learn them.** `remainingSp` came from
  `getRemainingSps()[getSkillBook(2001)]` = book 0, which is 0 once ticket 50 stops Evan beginners
  banking SP. `remainingSp > 0` is false, nothing is sent, the client reverts.
- **Advanced Evan (2200+) is charged.** `gainSp(-1, getSkillBook(2001) = 0, false)` takes a point out
  of book 0 - the same book Magic Missile is bought from.

The loop inside the window has the mirror defect: `getJobType()` is `2` for an Evan, so the
already-spent total it accumulates is **Aran's** Legend skills, which an Evan never has. The 6-point
budget would never deplete.

Actual vs expected, measured (`AssignSPProcessorTest`, run against the unpatched file):

```
everyBeginnerLineLearnsItsThreeSnailsWithNoSp
  job 2001, skill 20011000 ==> expected: <1> but was: <0>
beginnerSkillsDoNotSpendJobSp
  book 0 untouched ==> expected: <3> but was: <2>
```

Fix: test the skill's own job id against the same four-id beginner list `Character.isBeginnerJob()`
carries, and derive the loop's base from the skill rather than from `getJobType()`. The job id has to
be part of the test - Evan's Magic Missile is `22001001`, which the level window alone lets through;
`realJobSkillsStillSpendJobSp` is the negative control for exactly that and it still charges a point.
No behaviour changes for jobs 0 / 1000 / 2000: `skillJob * 10000` reproduces
`getJobType() * 10000000` for all three.

Test: `src/test/java/client/processor/stat/AssignSPProcessorTest.java` (3 tests). `SkillFactory` is a
WZ-backed static map, empty outside a running server, so it is stubbed with `Mockito.mockStatic`;
only `getMaxLevel()` matters to the code under test. This is the first `mockStatic` in the suite.

### 2 - REPORTED. Evan is not a magician for AP-to-HP/MP: 6 MP per point instead of 18

`src/main/java/client/processor/stat/AssignAPProcessor.java:669` `calcHpChange`, `:764`
`calcMpChange`, and the same chains in `takeHp:842` / `takeMp:862`.

Every branch is `job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1)` and friends. `EVAN1.isA(MAGICIAN)`
is **false**: `Job.isA` compares `getId() / 100`, which is 22 for job 2200 and 2 for job 200. Evan
matches no branch and lands in the trailing "everything else" case.

Measured (`AssignAPProcessorTest.evanIsNotClassifiedAsAMagicianForApGains`, the deterministic
AP-reset path; the values are the same with `USE_RANDOMIZE_HPMP_GAIN` either way):

| | magician | Evan (2200 and 2218) |
|---|---|---|
| `calcHpChange` | 6 | **8** |
| `calcMpChange` | **18** | **6** |

Live on the shipped config. Every AP point an Evan puts into MP is worth a third of a magician's,
and it is permanent. On the level-up path (`usedAPReset = false`, `USE_RANDOMIZE_HPMP_GAIN: true`)
the same split is `rand(12,16) + int/20` plus the Improved MP Increase effect for a magician against
`rand(4,6) + int/10` for an Evan.

Recommendation, conforming to the file (add a disjunct to the existing chain, no new construct):
add `|| job.isA(Job.EVAN1)` to the magician branch of `calcHpChange`, `calcMpChange`, `takeHp` and
`takeMp`. `isA(Job.EVAN1)` is true for 2200-2218 and false for 2001, which is what is wanted - a
beginner Evan already gets the beginner numbers, same as job 0. The skill lookups inside those
branches are safe for an Evan: `getSkillLevel` of a magician skill an Evan cannot have returns 0.

**Not applied here.** It is a two-way change: MP per point goes 6 -> 18, but HP per point goes
8 -> 6, which is a nerf to a character already levelling on the live server. That is the owner's
call, not mine.

### 3 - REPORTED. Evan and Aran's Legend have no AP-reset HP/MP floor at all

`src/main/java/client/processor/stat/AssignAPProcessor.java:882` `getMinHp`, `:933` `getMinMp`.

Same `isA` chains, plus a `job == Job.BEGINNER || job == Job.NOBLESSE` case that omits `LEGEND`
(2000) and `EVAN` (2001). Nothing matches Evan, so `multiplier` and `offset` stay 0 and the function
returns **0** for jobs 2000, 2001 and 2200-2218.

Measured (`AssignAPProcessorTest.evanAndLegendHaveNoMinimumHpMpFloor`): `getMinHp` and `getMinMp`
both return 0 for `LEGEND`, `EVAN`, `EVAN1` and `EVAN10`, against 2054 / 4399 for `MAGICIAN` at the
same level.

That value is the entire guard on `APResetAction`:

```java
if (player.getMaxHp() + hplose < getMinHp(player.getJob(), player.getLevel())) { ... refuse ... }
```

With 0 it can never refuse, so an Evan can AP-reset max HP or MP down past the floor every other
class is held to. `USE_ENFORCE_HPMP_SWAP: false` means there is no second guard. Reachable only
through an AP Reset, so lower than #2, but it is the same omission.

Recommendation: give Evan the magician rows and `LEGEND`/`EVAN` the beginner row. Note the two-tier
split the magician entries already use - `job == Job.MAGICIAN` for 1st job against `isA(FP_WIZARD)`
etc. for 2nd and up - so the conforming shape is `job == Job.EVAN1` in the first tier and
`job.isA(Job.EVAN2)` (true for exactly 2210-2218) in the second. **Deliberately not applied**: the
offsets are HP-pool constants, and picking magician's numbers for Evan is a guess about Evan's real
pool. Setting the floor too high turns a silent gap into a false "you don't have the minimum HP pool
required to swap". Wants the owner's number, not mine.

### 4 - REPORTED, dormant. `getJobBranch(2001)` is 3, and its whole remaining blast radius

`src/main/java/constants/game/GameConstants.java:469`. Ticket 50 left this function alone and moved
`levelUpGainSp()` onto `isBeginnerJob()`. This is the audit of what is still downstream of it.

Measured (`GameConstantsJobBranchTest`):

```
getJobBranch:  0 -> 0    1000 -> 0    2000 -> 0    2001 -> 3       (should be 0)
               2200 -> 1  2210 -> 2   2213 -> 5    2218 -> 10      (112, 1112, 2112 all -> 4)
getJobMaxLevel: LEGEND (2000) -> 10   EVAN (2001) -> 120           (should be 10)
```

Reachable callers, all of them:

- `Character.getMaxLevel()` -> `getJobMaxLevel`. **Behind `USE_ENFORCE_JOB_LEVEL_RANGE`, which ships
  `false`.** Dormant. If it were on, an Evan beginner would be capped at 120 rather than 10.
- `getChangedJobSp` / `getJobMaxSp` -> `getJobUpgradeLevelRange` / `getChangeJobSpUpgrade`. **Behind
  `USE_ENFORCE_JOB_SP_RANGE` (`false`) and additionally behind `!hasSPTable(job)`, which is false for
  every Evan job.** Doubly unreachable.
- the `"[Nth Job]"` broadcast in `changeJob`, behind `USE_ANNOUNCE_CHANGEJOB`. Cosmetic; never fires
  for 2001 anyway, since a character is created at 2001 rather than advancing into it.
- **`getSkillBook()` does not consult `getJobBranch`.** The ten-slot SP table is not reachable from
  here. Neither is packet serialisation.

Blast-radius verdict on changing it: the 3 -> 0 move is smaller than it looks - two dormant config
flags and one cosmetic string - but it is not zero, and the function is the wrong place to encode
"Evan is special" when `isBeginnerJob()` already does. **Leave it.** Ticket 50's choice was right.

### 5 - REPORTED, unreachable. The job-upgrade tables are five wide; Evan's branch number reaches 10

`src/main/java/constants/game/GameConstants.java:49` `jobUpgradeBlob = {1, 20, 60, 110, 190}` and
`:50` `jobUpgradeSpUp = {0, 1, 2, 3, 6}`, both indexed by the branch number. No other class exceeds
branch 4. Evan reaches 10.

Measured (`GameConstantsJobBranchTest.theJobUpgradeTablesDoNotReachEvansLaterBranches`):
`getJobUpgradeLevelRange(getJobBranch(EVAN5))` and `getChangeJobSpUpgrade(getJobBranch(EVAN10))` both
throw `ArrayIndexOutOfBoundsException`.

Unreachable today for the reasons in #4 - it would take an Evan job advancement with
`USE_ENFORCE_JOB_SP_RANGE: true` **and** `hasSPTable` losing its Evan cases. Recorded because it is
the trap the next caller of `getJobBranch` walks into, and because it turns a config flip into a
crash on the advancement packet rather than a wrong number.

### 6 - REPORTED, no live effect found. `Skill.isBeginnerSkill()` is false for every 2001xxxx skill

`src/main/java/client/Skill.java:85`: `return id % 10000000 < 10000;`. Same assumption, same break -
`20011000 % 10000000` is 11000. True for jobs 0, 1000 and 2000; false for 2001. Evan's Blessing of the
Fairy (`20010012`) is caught by it too.

Two callers, neither of which produces a live defect:

- `Character.getUsedSp` - only called from `getChangedJobSp`/`getSpGain`, which are behind
  `!hasSPTable(job)`, false for every Evan job. Never sees an Evan.
- `SkillAction:82` - `skill.jobsContains(chr.getJob()) || skillObject.isBeginnerSkill()`. A quest
  skill action that lists Evan's job ids is already handled by `jobsContains`.

The correct form is `job == 0 || job == 1000 || job == 2000 || job == 2001` (`job` is `id / 10000`),
which is identical to the current predicate on every non-Evan skill. Left alone under YAGNI: no
caller is currently wrong because of it. Worth doing if `getUsedSp` ever becomes reachable for Evan.

## Checked and correct - do not look here again

- `Job.getJobNiche()` - 2001 -> 0, 2000 -> 0, 2100 -> 1, 2200 -> 2. Right for all of them; the
  `(id/100) % 10` trick happens to survive Evan.
- `GameConstants.getSkillBook(int)` - 2210-2218 -> 1-9, everything else -> 0. Job 2200 sharing book 0
  with beginners is deliberate and is what `resetStats`/`levelUpGainSp` rely on.
- `GameConstants.hasSPTable(Job)` - an explicit switch over EVAN..EVAN10. No arithmetic, no gap.
- `GameConstants.isCygnus` (`job / 1000 == 1`) and `isAran` (`2000 || 2100..2112`) - both correctly
  false for 2001 and 2200-2218.
- `GameConstants.isInJobTree` - Evan's own skills resolve at Evan's jobs; 2001's skills resolve at
  2200+. (It also lets an Aran assign Evan's beginner skills and vice versa, both directions of a
  pre-existing 2000/2001 sibling overlap. Harmless: same three skills.)
- `Skill.isFourthJob()` - already carries an explicit Evan patch (job 2212 excluded plus five
  master-level ids listed). Without it an Evan could not put a point into any 2212 skill, because the
  cap would be `getMasterLevel()` = 0. Already handled.
- `Character.isBeginnerJob()`, `getJobType()`, `levelUp()`'s HP/MP curve (`isA(Job.EVAN1)` branch) -
  all carry Evan.
- `Character.getJobMapChair()` gives an Evan `Legend.MAP_CHAIR` (20000100). Not a defect: **skill
  20010100 does not exist in `wz/Skill.wz/2001.img.xml`**, so there is nothing better to point at.
  Evans get no chair extra-heal; that is a WZ fact, not a code one.
- `changeJob` AP grants for Evan: 2200 and 2210 give 0 (`USE_STARTING_AP_4: false`), 2211-2218 give 5
  each - the same rule Explorers get at x11/x12. Evan getting eight of those where an Explorer gets
  two is a **GUESS** at best; not asserted, not counted as a defect.
- **Dual Blade does not exist on this server.** No `Job` enum entries for 430/434, no
  `wz/Skill.wz/43*.img`, no Java reference to either id. Nothing to check.
- Cygnus (1000, 1100-1512) and Aran (2000, 2100-2112) are covered explicitly in every chain audited
  here except `getMinHp`/`getMinMp`, where **Aran's LEGEND (2000) shares Evan's zero floor** - see #3.

## Latent, noted, not fixed

`AbstractCharacterObject.getRemainingSp(int jobid)` indexes `remainingSp[getSkillBook(jobid)]`, but
`Character.getJobRemainingSp` calls it in a loop with a **skill-book index**, not a job id:
`getSkillBook(1)` is 0, so the loop sums book 0 repeatedly instead of books 0..n. Correct by accident
for every non-Evan job (the loop runs once), and unreachable for Evan for the reasons in #4. Same
family - a number reused as two different kinds of id.

## Tests

Baseline measured at `7a03a5e10` + ticket 50's uncommitted work: **2184 run, 1 failure**
(`MobSkillFactoryTest.shouldThrowExceptionOnNonExisting`, the `wz-path` JVM-sharing race, since
fixed by `d9cc215da`), and the `*RealLoad` fork never ran because the first execution aborted the
build. After this ticket, at `d9cc215da`: **main 2191 / 0 failures, `*RealLoad` 75 / 0 failures.**

New: `src/test/java/client/processor/stat/AssignSPProcessorTest.java` (3),
`src/test/java/constants/game/GameConstantsJobBranchTest.java` (4).
Extended: `src/test/java/client/processor/stat/AssignAPProcessorTest.java` (+2).

The four classes that pin wrong values say so in their names and javadoc, following
`CharacterLevelUpSpTest.getJobBranchStillMisreadsEvansBeginnerJob`. They are documentation that
executes; if someone fixes #2, #3, #4 or #5, those tests are supposed to go red.
