# 52 - Evan sweep of job-dependent branches (stat gains, skills, halls, creation)

Follows ticket 51. Same root shape throughout: a branch keyed on the job, written before Evan
existed, that Evan silently falls out of. Two mechanisms do all the damage.

1. `Job.isA(Job.MAGICIAN)` is **false** for 2200-2218, because `isA` compares `id/100` (22 vs 2).
2. `getJobType()` is `id/1000`, so Evan's **2001** beginner skill block collapses onto Aran's 2000.

`isA` and `getJobType` are both left alone - they are load-bearing everywhere - and Evan is named in
the individual chains instead, the way f7657c736 did.

## Fixed

| Site | What an Evan got before |
|---|---|
| `MonsterBook.applyMainStatBuff` | **+1 STR per completed card set**, persisted. See data correction. |
| `MonsterBook.addCard` beginner gate | `isA(BEGINNER)` is only true for job 0, so 1000/2000/2001 walked through it |
| `AssignAPProcessor.getMinHp/getMinMp` | 0, i.e. no AP-reset floor at all (also Aran's 2000) |
| `MakerProcessor.getMakerSkillLevel` | read 20001007, always 0 - **Maker was unusable** |
| `Character` mount id + tiredness dispel | Mount built on 20001004 while the buff applies under 20011004; tired mounts never dismounted |
| `PlayerLoggedinHandler` / `Character` Blessing of the Fairy | wrote and read skill 20000012 |
| `AbstractDealDamageHandler` ultra Three Snails | never matched (`USE_ULTRA_THREE_SNAILS` is on) |
| `SpecialMoveHandler` + `CloseRangeDamageHandler` dojo secret skills | Bamboo Thrust / Invincible Barrier / Power Explosion cast **free** - the energy check and reset were skipped; Echo of Hero took the wrong apply path; Monster Rider missed a `readShort` |
| `GameConstants.isPqSkill` | Evan's secret skills were the only ones bindable to a key |
| `GameConstants.getHallOfFameMapid` | `KNIGHTS_CHAMBER_2`, the **Cygnus** hall (`PLAYERNPC_AUTODEPLOY` is on) |
| `MakeCharInfo.verifyCharacter` | starting equipment accepted **unverified** |
| `CheckDmgCommand` | read the caller's jobType against the victim (pre-existing, all classes) |

The skill-block sites all now go through one new accessor, `Character.getBeginnerSkillBlock()`.

## Deliberately not given an Evan case

- **`Character.levelUp` improvingMaxMP guard.** Evan has no Improved Max MP passive. Skill.wz
  2001.img and 2200-2218.img list every skill Evan has and none of them is one, which is why the
  Evan branch never assigns `improvingMaxMP` either. The code was already correct; commented.
- **`RescueGaga.giveSkill`.** jobType 2 hands Aran *and* Evan the Cygnus ids. There is no Legend-side
  reward to point them at: 2000.img stops at 20001013 and 2001.img has no 1013-1016 at all. An
  "Evan's own" id here would hand `changeSkillLevel` a null skill. Commented, not changed.
- **`CashShop` inventory partition.** jobType 2 puts Evan in `CASH_ARAN`, sharing with the account's
  Arans. The client only has three cash partitions and Evan is a Legend-branch class, so this is
  defensible rather than broken. `USE_JOINT_CASHSHOP_INVENTORY` is false, so the split is live.
- **`Character.isAran()`** (`2000 <= id <= 2112`) is **true for 2001** while `GameConstants.isAran`
  is false for it. Its only caller is gated on the `WK_CHARGE` buff, which an Evan can never hold, so
  it is harmless today. Left per ticket 51.
- **`Character.dispelSkill`'s `skillid == 0` branch** has the same `% 10000000 == 1004` defect, but
  nothing in the tree calls `dispelSkill(0)`. Dead; left.
- **`Job.isA(Job.LEGEND)`** is true for 2001 and false for 2200+, so it splits the Evan class in half
  at 1st job advancement. Only `MaxSkillCommand`/`ResetSkillCommand` use it, GM-only and cosmetic.

## Data correction needed on existing characters

1. **Monster card sets.** Every set an Evan completed added +1 STR instead of +1 INT, and it was
   written to the DB. The count is recoverable:
   `SELECT COUNT(*) FROM monsterbook WHERE charid = 50 AND isGainedMainStatBuff = 1;`
   Repair is `str = str - n`, `int = int + n` for that character. Applies to any Evan, and to any
   Noblesse/Legend/Evan **beginner** who completed sets under level 10 (they bypassed the gate).
2. **Blessing of the Fairy.** Evans have a stale `skills` row for **20000012** (Legend's) written by
   every login. It is no longer read, so it grants nothing and costs nothing - delete at leisure:
   `DELETE FROM skills WHERE skillid = 20000012 AND characterid IN (SELECT id FROM characters WHERE job BETWEEN 2200 AND 2218 OR job = 2001);`

## Balance changes the owner did not ask for

- Evan **loses** free dojo secret skills; they now consume dojo energy like every other class.
- Noblesse/Legend/Evan beginners under level 10 can **no longer** complete card sets. That was always
  the intent of the gate; only job 0 was actually being stopped.
- A max-level Evan PlayerNPC now deploys to the Hall of Magicians instead of the Cygnus hall. Any
  Evan pnpc already standing in `KNIGHTS_CHAMBER_2` stays there until redeployed.
