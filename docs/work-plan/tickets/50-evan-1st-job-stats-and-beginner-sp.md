# 50 - Evan 1st job: STR/DEX spread kept, and beginner SP banked

Two defects reported live by the owner on an Evan he had just advanced to job 2200:

1. "i was job advanced to evan, but my stats remained str and dex. as i was as a begginer"
2. "i got skill points, a lot of skill points like 40 or 30 as if I acquired them but I don't
   think that's the case"

Both are the same family as the already-recorded `Character.isBeginnerJob()` defect: **job 2001 is
Evan's beginner job and the only beginner id that is not a round thousand**, so every predicate
written as an id test rather than as `isBeginnerJob()` misclassifies it.

## 1. Stat spread - `Character.resetStats()`

`resetStats()` reassigns a beginner's auto-assigned STR/DEX into the new job's primary stat. Its
`switch (job.getId())` had no `case 2200`, so an Evan fell through to the defaults
(`tstr/tdex/tint/tluk = 4`), keeping whatever the beginner auto-assign had built. `scripts/quest/
22100.js` also never called it, unlike Aran's `scripts/quest/21101.js` and the five Explorer 1st-job
NPCs (`1012100/1022000/1032001/1052001/1090000.js:137-188`).

Fix: `case 2200` in the same switch, next to the magician group, plus `qm.resetStats()` in
`22100.js` after `changeJobById(2200)`.

**`tint = 20`, the magician value, is the closest analogue and NOT a measured v84 fact.** Auto-assign
is a Cosmic convenience - v84 makes the player spend AP by hand - so no GMS v84 number exists to
copy. Nothing in the WZ pins one either: the level-8 wand `01372005` has `reqINT 0`. Evan is a
magician-type class (`Character.getJobStyleInternal` maps `2200/100` to `Job.MAGICIAN`), so the
magician spread is the analogue. Aran's `tstr = 35` is the wrong model - copying it would reproduce
the reported bug.

### The SP hazard in that switch

`resetStats()` ends in
`updateStrDexIntLukSp(..., tsp, GameConstants.getSkillBook(job.getId()))`.

- `getSkillBook(2200)` returns **0** - `getSkillBook` is `if (job >= 2210 && job <= 2218) return
  job - 2209; return 0;` (GameConstants.java:502). Only Evan's 2nd-10th jobs get books 1-9.
- `changeStatPool` writes SP through `setRemainingSp(sp, skillbook)`, a single-slot assignment, so
  slots 1-9 are never addressed by this call.
- The packet path already agrees: `PacketCreator.updatePlayerStats` serialises `AVAILABLESP` through
  `addRemainingSkillInfo` whenever `writesExtendedSp(chr)`, which follows `hasSPTable`. So the SP
  delta for an Evan is emitted in the extended ten-slot form either way. **No shape change, no
  character-select crash risk.**

What *would* have been destroyed is the value: `changeJob` grants an Evan **3** SP
(`spGain = 1; if (hasSPTable) spGain += 2`, Character.java:1152), while every `tsp` formula in the
switch produces 1 at the minimum advancement level. So `case 2200` reads book 0 back and writes it
unchanged - the SP write is an identity.

## 2. Beginner SP - `Character.levelUpGainSp()`

The guard was `GameConstants.getJobBranch(job) == 0`. `getJobBranch` is

```
if (jobid % 1000 == 0) return 0;
else if (jobid % 100 == 0) return 1;
else return 2 + (jobid % 10);
```

`2001 % 1000 == 1`, so Evan's beginner returned `2 + 1 = 3` and banked **3 SP per level from level
1** - about 27 by the level-10 advancement. Job 0, NOBLESSE 1000 and Aran's LEGEND 2000 all hit the
`% 1000 == 0` arm and correctly return 0.

Fix: guard on `isBeginnerJob()`, which already lists 2001. The two predicates agree on every other
job in the enum - `getJobBranch == 0` holds exactly for `{0, 1000, 2000}` and `isBeginnerJob()` for
`{0, 1000, 2000, 2001}`.

### Why `getJobBranch` itself was left alone

Changing `getJobBranch(2001)` from 3 to 0 is defensible and has a small, enumerable blast radius,
but it is a structural edit and was not taken. For the record, every reader that sees 2001:

| caller | effect of 3 -> 0 |
| --- | --- |
| `GameConstants.getJobMaxLevel` | 120 -> 10, i.e. what Aran's 2000 already returns. Read only via `Character.getMaxLevel()` behind `USE_ENFORCE_JOB_LEVEL_RANGE` (false in config.yaml:244) |
| `Character.getChangedJobSp` (6215) | unreachable for 2001: gated behind `!hasSPTable(newJob)`, and `hasSPTable(EVAN)` is true |
| `Character.getJobMaxSp` / `getSpGain` (6246/6271) | same gate, same conclusion |
| `Character.java:1272` `"[Nth Job]"` broadcast | cosmetic, and never fires for 2001 - `EvanCreator` builds the character at 2001 rather than changing job into it |
| `Character.levelUpGainSp` | the defect, now fixed at the caller |

**`GameConstants.getSkillBook` does not consult `getJobBranch` at all**, so the ten-slot SP table is
unaffected by either choice.

## Expected values after the fix

A freshly-advanced Evan, no Evan-chain SP quests done (the 28 quests granting 29 SP live in
22500-22580), none spent:

- **SP in book 0 = `3 + 3 * (level - 10)`.** Level 10 -> 3, level 11 -> 6.
- STR/DEX/INT/LUK = 4/4/20/4.
- AP = `(old str + dex + int + luk + ap) - 32`.

## Not fixed here

`scripts/quest/22101.js` through `22109.js` still carry a `ponytail:` comment about `resetStats`.
They are correct to not call it: 2210-2218 advance an already-built Evan, and resetting there would
wipe a real stat allocation. Left as-is.

`server.life.MobSkillFactoryTest.shouldThrowExceptionOnNonExisting` fails on this branch. It is
pre-existing and unrelated: `MobSkillFactory` imports nothing from `client`/`constants`, and both it
and `wz/Skill.wz/MobSkill.img.xml` are clean at HEAD.
