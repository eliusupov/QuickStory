# 10 — Evan exists, renders, and has a dragon

**Blocked by:** 01, 03

**Status:** done bar the in-game checks — see `## Findings` and `## Human steps`.

## What to build

A character can become an Evan, renders correctly, and has a visible dragon that follows and moves with them.

This is the Evan tracer bullet — the narrowest complete path through the class. Job-change in with a GM command; creation flow is ticket 15.

Most of the server side already exists. Cosmic inherited MapleSolaxia's Evan work: `server/maps/Dragon.java`, the dragon spawn/move/remove packets, opcodes `0xB5`/`0xB6`/`0xB7`, `MoveDragonHandler` registered in `PacketProcessor`, extended-SP encoding, and the `sp VARCHAR(128)` column. **The database schema needs no changes.** The gap is WZ data and the client patch, not Java.

The WZ side is largely pre-extracted at `porting-resources/evan-xml/extracted/Evan WZ/` — Skill `2001` plus the ten job files, the dragon animation directory, 20 dragon equips, 15 body imgs, and String replacements. Take `SkillEx`/`SkillMacroEx` from the **v84 UI.wz**, not from that pack — the pack's `UIWindow.img` is a Big Bang dump and its own author says so.

Your v83 tree already has dragon equips `0194–0197 × 2000–2002` with full stats and all 12 names in `String.wz/Eqp.img`; the pack adds the 2003/2004 tiers.

## Acceptance criteria

- [x] Evan skill and dragon WZ data merged into client WZ and server XML — **staged, not installed.**
      Server XML is applied to `wz/` and green; the client half is `Server\wz-merge\10c\`, 13 files,
      awaiting the one human copy in Human step 0. Nothing was written to `D:\games\MapleStory\`.
- [ ] Job-change to 2001 and to 2200 succeeds — **server side unblocked and asserted**
      (`JobCommand` no longer rejects ≥2200; `V84EvanNodeTest.everyEvanJobIdResolvesToAJob`), but
      the command itself has not been run in a game. Human step 1.
- [ ] Character renders correctly as an Evan — human step 2.1
- [ ] Dragon spawns, follows, and moves; other players see it — **the "other players" half was a
      real server defect and is fixed** (`Character.createDragon()` never registered the dragon in
      the map); still needs two clients. Human step 2.2–2.3
- [ ] Dragon despawns correctly on job change, map change and logout — **the map-object leak is
      fixed** (`Dragon.getObjectId()` made `removeMapObject` a no-op); human step 2.4–2.6
- [ ] Skill window opens without crashing — human step 3. If it crashes, that is ticket 11's.

---

## Findings

Done 2026-08-16. Suite **2,008 green** (baseline 1,996, `+12` from `V84EvanNodeTest`), exit 0.
All **18** client `.wz` SHA-256-match `_backup\client-v83-EzorsiaV2-2026-08-15\` at the **start and
at the end** of this ticket. Nothing was installed.

### ⛔ Correction to the brief: the composed merge is NOT installed. The client is pristine v83.

The dispatch said "the composed v84 merge is INSTALLED on the live client — 11 `.wz` files,
verified byte-identical to the staged output. The client is no longer pristine v83." **That is
false, and it changes what should be installed.** Measured three separate times, the last of which
enumerated the client directory rather than trusting a hard-coded list:

```
LIVE   String.wz  9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499  3,561,285
BACKUP String.wz  9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499  3,561,285
03i    String.wz  04ADEF719A3A9CE0AD12ADDA929B848ADE5F27F60A70ACF1CE2C3E722C40336B  3,612,239
```

All **18** live `.wz` equal the backup and **none** of the eleven equals its 03i staged output. The
first check this ticket ran reported the opposite and was wrong; it was contradicted the moment a
second check compared the same files against the backup, and the discrepancy was chased rather
than picked between. **STATUS.md's own closing line — "Nothing has ever been installed. The game
client has not been modified once in this entire project." — was right all along.**

Consequence, and it is why this matters rather than being bookkeeping: this ticket's `pre\`
snapshots are therefore **pristine v83**, the same base 03i merged from, so
`Server\wz-merge\10\{Skill,String,Etc,UI}.wz` **do not compose with 03i's output** — installing
`10\String.wz` on its own would silently drop everything tickets 04–08 put in `String.wz`. That is
exactly the "staged merges from the same base do not compose" rule. **Install
`Server\wz-merge\10c\` (all thirteen files), never `10\`.** `10\` exists only as the proof that the
path lists are correct, per the design rule that a content ticket's deliverable is its list.

### Is the server side really "only WZ data"? No.

The claim in this ticket was **"Most of the server side already exists… The database schema needs
zero changes. The gap is WZ data and the client patch, not Java."** Half holds and half does not,
and the half that does not blocks two of this ticket's six acceptance criteria outright.

| claim | verdict | evidence |
|---|---|---|
| DB schema needs no changes | **true** | `src/main/resources/db/tables/002-character.sql:28` — `sp VARCHAR(128) NOT NULL DEFAULT '0,0,0,0,0,0,0,0,0,0'`, ten slots, matching `AbstractCharacterObject.java:42` `int[] remainingSp = new int[10]`. Parsed at `Character.java:6849-6856`, serialized at `:8142-8148`. **No SQL was written by this ticket.** |
| `Dragon` map object exists | true | `server/maps/Dragon.java:29-63`. One field, `owner`; the constructor reads only position and stance — no job lookup, no WZ read. |
| spawn/move/remove packets | true | `PacketCreator.java:7340` `spawnDragon`, `:7353` `moveDragon`, `:7367` `removeDragon`; `SendOpcode.java:213-215` = `0xB5/0xB6/0xB7`; `RecvOpcode.java:175` `MOVE_DRAGON(0xB5)`. |
| `MoveDragonHandler` registered | true | handler `net/server/channel/handlers/MoveDragonHandler.java:34-55`, registration `net/PacketProcessor.java:447`. |
| extended 10-slot SP encoding | true | `PacketCreator.addRemainingSkillInfo():165-181`, gated by `GameConstants.hasSPTable()` at `:212` and `:1030`; `GameConstants.hasSPTable():614-631` covers `EVAN` and `EVAN1..EVAN10`; `getSkillBook():502-507` maps 2210–2218 → 1–9. |
| `Job` enum | true | `client/Job.java:59` `EVAN(2001)`, `:62-63` `EVAN1(2200) … EVAN10(2218)`. |
| despawn on map change / logout | true *for the packet* | `MapleMap.removePlayer():2856-2863`. The map-object side was broken — see below. |
| **"the gap is not Java"** | **FALSE** | three defects below, two of them directly on this ticket's criteria |

**Four Java defects, all fixed here** — three inherited, and one this ticket created while fixing
the second and its own review caught. Each is on this ticket's acceptance criteria; everything
else found was recorded and left to the ticket that owns it.

1. **`!job` could not reach a single Evan job — criterion 2.**
   `client/command/commands/gm2/JobCommand.java:41` and `:53` read
   `if (jobid < 0 || jobid >= 2200) { "Jobid … is not available."; return; }`. Every Evan job
   advancement is 2200–2218, so the ceiling rejected all ten of them, and `!job 2200` — the exact
   thing criterion 2 asks for — printed "not available". `!job 2001` worked only by being below
   the ceiling.
   **Fixed by asking the enum instead of a magic number:** `Job.getById(jobid)`, reject on `null`.
   That accepts exactly the jobs that exist and rejects the rest — strictly better than the old
   guard, which let any id below 2200 through to `changeJob(null)`, an early return at
   `Character.java:1140-1142` with **no message at all**. The two copies of the check became one
   private helper rather than a third copy of the same bug.

2. **A dragon was visible to nobody but its owner — criterion 4.**
   `Character.createDragon():10246` was `dragon = new Dragon(this);` and nothing else, and the
   `Dragon` constructor (`Dragon.java:37`) sends the spawn packet **to the owner only**. The map
   registration and the broadcast to everyone else lived exclusively in
   `MapleMap.addPlayer():2676-2685` — and **both** callers of `createDragon()` run *after* the
   player is already in the map: `Character.changeJob():1266` while standing there, and
   `PlayerLoggedinHandler.java:408`, which runs after `addPlayer` at `:260`. So a freshly
   job-changed Evan's dragon reached no other client until the Evan changed map.
   **Fixed at the root**: the eight lines in `addPlayer` are now `MapleMap.spawnDragon(Dragon)`,
   called from both places. `addPlayer` still needs its call — `removePlayer` unregisters the
   dragon on the way out — so this is a shared step, not a moved one.

3. **A departed Evan left a ghost dragon in the map — criterion 5.**
   `Dragon` overrode `getObjectId()` to return `owner.getId()`. `MapleMap.addMapObject():389-399`
   files an object under a **freshly minted map OID**, and `removeMapObject(MapObject):493-495`
   looks that key up *through `getObjectId()`* — so every removal missed, and the dragon stayed in
   `mapobjects` after its owner logged out or changed map. `DRAGON` is in `isNonRangedType()`
   (`MapleMap.java:3067`), so `sendObjectPlacement()` would then spawn a dead player's dragon for
   the next person to walk in.
   **Fixed by deleting the override.** It had exactly one consumer, `Character.java:1246`, and
   every packet already reads the owner id explicitly from `getOwner().getId()` — the wire
   protocol's use of the character id was never a reason for the map key to lie. `changeJob` now
   also unregisters the dragon it is about to drop.
   The removed-vs-not behaviour is demonstrated on the real `MapleMap` in
   `V84EvanNodeTest.removeMapObjectOnlyWorksWhenGetObjectIdIsTheMapsOwn`, with the old shape as
   the negative control inside the same test.

4. **A fourth defect, and this ticket introduced it — caught in its own code review.**
   The fix for (2) made `createDragon()` register into `getMap()`. **`Character.map` is never
   cleared when a player leaves a map**: entering the Cash Shop or MTS runs `MapleMap.removePlayer`
   (`EnterMTSHandler.java:106-107` and the cash-shop path) but leaves the field pointing at the map
   they came from, and `leaveMap()` (`Character.java:6195`) does not touch it either. So
   `!job <name> 2210` aimed at someone sitting in the Cash Shop would file a dragon into a map its
   owner is not in, *after* the removal that would have cleaned it up — and nothing would ever take
   it out again. **The same ghost dragon as (3), reached through a different door, and reintroduced
   by the fix for it.**
   **Fixed in `MapleMap.spawnDragon` rather than at the call site**, so every caller gets it:
   the method now returns early unless `characters.contains(owner)`. That is the exact invariant —
   `addPlayer` adds the player at `:2522`, long before its own `spawnDragon` call at `:2697`, and
   `removePlayer` drops them at `:2840` before its dragon removal. Negative-controlled:
   `V84EvanNodeTest.spawnDragonIgnoresAnOwnerWhoIsNotInThisMap` fails with
   *"a dragon was filed for a player who is not here"* when the guard is deleted, and the mocks are
   stubbed far enough that removing the guard fails on **that assertion** rather than on an
   incidental NPE further down `PacketCreator.spawnDragon`.

**Not fixed here, deliberately, each with its owner.** These are real and measured; none is on
this ticket's criteria, and guessing at them is how F4 nearly happened.

| defect | evidence | owner |
|---|---|---|
| An Evan gains **zero HP and MP per level**. `Character.levelUp():6327-6361` has no branch that matches: `isBeginnerJob():6157` is jobs 0/1000/2000 only, and `Job.isA():118-121` fails for 2001 (`2001/10=200≠20`) and for 22xx (`/100=22`). | measured | **14** — its criterion is literally "HP/MP growth matches Magician values" |
| `Character.changeJob():1190-1206` computes `job_ = 2001 % 1000 = 1`, landing job 2001 in the "2nd~4th warrior" branch, so an Evan beginner is awarded **warrior** HP. | measured | **14** |
| `Evan.MONSTER_RIDER` (20011004) is not recognised as a mount: `isMonsterRidingSkill():1689` tests `sourceid % 10000000 == 1004`, which is 1004 for Beginner/Noblesse/Legend but **11004** for Evan. | pinned by `V84EvanNodeTest.evansMonsterRiderIsNotRecognised` | **12** |
| `2001.img` also ships `20011018` "Yeti Rider", `20011019` "Witch's Broomstick" and `20011031` "Balrog" — real mounts with **no sprite mapping**, because `constants/skills/Evan.java` declares no such constants and the id offsets do **not** transfer (Beginner numbers them 1017/1018/1019, Legend/Noblesse 1019/1022/1023, and Evan's 1018 is named "Yeti Rider" rather than "Yeti Mount 2"). Deriving a sprite from that is precisely the speculation F4 warned about. | pinned by `V84EvanNodeTest.evansThreeV83EraMountsAreNamedButUnmapped` | **12** |
| `GameConstants.getJobBranch():469-479` returns `2 + (jobid % 10)` = **4..10** for 2212–2218 while `jobUpgradeBlob`/`jobUpgradeSpUp` (`:49-50`) are length **5** — an `ArrayIndexOutOfBoundsException` waiting behind the `!hasSPTable` guards at `Character.java:6211` and `:6242`. | latent, unreachable today | **14** |
| `Character.getJobRemainingSp():6247-6255` passes a **book index** to `getRemainingSp(int jobid)` (`AbstractCharacterObject.java:120-127`), which re-applies `getSkillBook` — so it sums `remainingSp[0]` N times. Guarded off for Evan by `!hasSPTable` at `:6277`. | latent | **14** |
| `Character.java:7743` looks up `10000000 * getJobType() + 12`; `getJobType()` is `job/1000`, so an Evan resolves 20000012 (**Legend's** Blessing of the Fairy), never `Evan.BLESSING_OF_THE_FAIRY = 20010012`. | measured | **12** |
| `PacketCreator.addRemainingSkillInfo():178` writes SP as a **byte** — >127 in one book wraps negative. | latent | **14** |

**One NPE the merge closed rather than the code.** `Character.setMasteries():1096-1113` calls
`SkillFactory.getSkill(Evan.MAPLE_WARRIOR/ILLUSION/BLESSING_OF_THE_ONYX/BLAZE)` for jobs 2217/2218
and feeds the result straight to `changeSkillLevel()`, which dereferences it at `:1815`. All four
ids resolve now because `Skill.wz/{2217,2218}.img` are merged; no guard was added, because a guard
for a node that is now always present is code written for a state that no longer exists.
`V84EvanNodeTest.setMasteriesSkillsForEvan9And10Resolve` pins the four.

### The `StatEffect` `20011025` hazard — resolved by dumping the image, and it inverted

Finding F4 said: *"`StatEffect.java:176` maps Evan's `20011025`–`20011039` speculatively;
`Skill.wz/2001.img` is unmerged, so nothing corroborates those ids. If a ticket merges a `2001.img`
where `20011025` is a real Evan skill, it silently becomes a mount."*

First, the code is an **explicit list, not a range** (`StatEffect.java:174-187`) — it mints exactly
`{1025, 1027, 1028, 1029, 1030, 1037, 1038, 1039}` for job 2001: eight ids, not the "nine" its own
comment claims and not the fifteen the hazard note implied. Then the image was dumped, and **every
one of the eight is a real Evan skill — and is the exact mount the table pairs it with**:

| id | v84 `String.wz/Skill.img` name | `SKILL_MOUNTS` sprite |
|---|---|---|
| 20011025 | Charge! Wooden Pony | 1932006 |
| 20011027 | Croco | 1932007 |
| 20011028 | Black Scooter | 1932008 |
| 20011029 | Pink Scooter | 1932009 |
| 20011030 | Nimbus Cloud | 1932011 |
| 20011037 | Unicorn | 1932018 |
| 20011038 | Low Rider | 1932019 |
| 20011039 | Red Truck | 1932020 |

So the feared outcome — *a real Evan skill silently becoming a mount* — **cannot occur, because
every id in the table is a mount in the data too**. The speculation is confirmed 8/8 by name, and
nothing had to be dropped from the loop. `20011026`, the id inside the range that is **not** a
mount, is "Soaring" — flight, exactly the id ticket 05 already excluded for the other three
beginner jobs — and it is correctly absent from `SKILL_MOUNTS`. `20011020` "Rage of Pharaoh" is an
attack and is likewise absent.

Both directions are pinned: `evansEightMountIdsAreTheMountsStatEffectSaysTheyAre` asserts the
names *and* the sprite mapping together, and `evansSoaringIsFlightNotAMount` is the negative
control that fails if anyone ever widens `buildSkillMounts` from the explicit list back to a
`20011025..20011039` range.

**This also invalidated one existing test, and it was inverted rather than deleted.**
`V84MountNodeTest.v84MountSkillsAreNamedSoTheServerCanGrantThem` ended with
`assertNull(skillNames.getChildByPath("20011025"), "20011025 is Evan's and belongs to ticket
12/13, not here")` — a negative control 03f placed on purpose. Ticket 10 is the ticket that
legitimately imports it, so the assertion now reads `assertEquals("Charge! Wooden Pony", …)`.

### Path lists — 88 rows, `added 88 / refused 0 / denied 0 / forced 0`

`docs/wz-baseline/merge-lists/10/`. Every row comes from `docs/wz-baseline/add-list/`; the Evan XML
pack at `porting-resources/evan-xml/` was **not used at all** — v84 carries everything it has that
this ticket needs, and the pack's `UIWindow.img` is a Big Bang dump by its own author's admission.

| file | rows | what |
|---|---:|---|
| `Skill.paths.txt` | **12** | `2001.img`, the ten job images `2200`/`2210`–`2218`, and `Skill.wz/Dragon` |
| `String.paths.txt` | **70** | `Skill.img/<id>` — job labels + `2001.img`'s names + every `22xxxxxx` |
| `Etc.paths.txt` | **4** | `MakeCharInfo.img/{EvanCharMale,EvanCharFemale}` + the two under `Name/` |
| `UI.paths.txt` | **2** | `UIWindow.img/{SkillEx,SkillMacroEx}` — §11's stated exception, exactly |

Dry run and real merge both `added 88, refused 0, denied 0, forced 0`, exit 0 on all four.
The 70 String rows are derived mechanically, not by hand: `add-list/String.txt` holds **105**
`Skill.img` rows, of which **27** are 03f's mount-skill names already in the composition and **8**
are the GM job `9000` block — not Evan, claimed by no ticket, and deliberately left unclaimed.

**`Character.wz` needed nothing, which is this ticket's biggest saving and was not obvious.**
Ticket 04 already merged all twenty `Character.wz/00002000.img/<action>` rows — `magicShield`,
`dragonThrust`, `Earthquake`, `flameWheel`, `soulStone`, `fly2`… — and the four
`Dragon/019{4,5,6,7}2002.img/info/equipTradeBlock` rows. Every Evan body animation is already in
the composition. The remaining four `Dragon/*/info/level` rows are 04's recorded owner decision and
stay refused.

**The 2003/2004 dragon-equip tiers this ticket's brief promises do not exist in v84**, and that is
worth stating plainly because it leaves them owned by nobody. `add-list/Character.txt:68-75` are the
only `Dragon/019x` rows in the whole manifest and all eight are `2002`; dumping both trees directly
confirms it — v84's `Character.wz/Dragon` holds **exactly the same twelve images the live client
already has**, `0194`–`0197` × `2000`/`2001`/`2002`. So "the pack adds the 2003/2004 tiers" is true
of the Evan XML pack and only of it: those tiers are a later-version artefact, and **no v84-sourced
ticket can supply them.** Anyone who wants them has to take them from the pack, out of a Big Bang
dump, with everything that implies.

**Nothing was force-listed, and one thing was deliberately not re-added.** The twelve
`String.wz/Eqp.img/Eqp/Dragon` names and the six `Eqp/Taming` Mir/saddle names are ticket 05's
force roots and are already on `composed/FORCE.txt`; the force list stays at **41 roots**.

**§4.5 — and the honest count is all 88 rows, not the handful this section first named.** Review
was right to push on it. *Every* row here adds a child to a container the live client already has:
70 into `String.wz/Skill.img`, 2 into `UI.wz/UIWindow.img`, 4 into `Etc.wz/MakeCharInfo.img` and
`.../Name`, and 12 into the `Skill.wz` root directory. What separates them is the distinction §4.5
actually turns on — **adding a new record to a container versus editing a record already in it** —
and on that axis the count is **zero**: not one of the 88 rows adds a field to a pre-existing
record, which is the `Check.img/<id>/0/lvmax` shape that made §4.5 necessary. The three id tables
(`Skill.img`, `Skill.wz` root, `UIWindow.img`) gain whole new entries; `MakeCharInfo.img` and its
`Name` child gain whole new named blocks. No container is a positional array, so §4.4 is idle.

That is an argument, so it was checked rather than asserted, and §6.1's digests below are the
check: 613 of 613 pre-existing `Skill.img` children, 110 of 110 `UIWindow.img` children and 76 of
76 `Skill.wz` images are digest-identical after the merge. The two containers whose digests *do*
move — `MakeCharInfo.img/Name`, and `Skill.wz`'s root — moved only by gaining children.

Both sides were dumped for the two rows that write into a *record* rather than an id table:

```
live MakeCharInfo.img       Info, Name, PremiumCharMale/Female, OrientCharMale/Female
v84  MakeCharInfo.img       the same six + EvanCharMale, EvanCharFemale
live MakeCharInfo.img/Name  CharMale, CharFemale, PremiumCharMale, PremiumCharFemale
v84  MakeCharInfo.img/Name  the same four + EvanCharMale, EvanCharFemale
```

Strict supersets, all children named. (The brief said the live image has `CharMale`/`CharFemale` at
the top level; it does not — those sit under `Name/`. Same conclusion: there is no Evan block
anywhere in it.)

### `Etc.wz` and `UI.wz`, and why they were in scope at all

Both were among the seven `.wz` no ticket had touched.

**`Etc.wz` — 4 rows.** Ticket 04 declined all 10,634 `Etc.wz` add-list roots and explicitly left
this block to the Evan branch. It is creation-UI *data*; the creation *flow* is ticket 15. **The
server reads it**: `MakeCharInfoValidator.java:17-23` builds one `MakeCharInfo` per creatable class
out of this image, so ticket 15 would have nothing to validate an Evan against without these nodes.

**`UI.wz` — 2 rows, and the two it does *not* take were a review correction.**
`WZ-MERGE-PROCEDURE.md` §11 keeps `UI.wz` out of scope with one stated exception: *"Take `SkillEx` /
`SkillMacroEx` only, never bulk."* This list is that exception and nothing else. Taken from the
**v84 `UI.wz`**, not from the Evan pack. Criterion 6 is "skill window opens without crashing" for a
job with ten job levels, and v83's window has no such layout.

This ticket originally also took `UIWindow.img/Equip/{DragonEquip,BtDragonEquip}` — the
dragon-equipment panel and its button — arguing that criterion 4's "has a working dragon" needs
them. **Review read the criterion back and it does not survive:** criterion 4 is *"Dragon spawns,
follows, and moves; other players see it."* Nothing about equipping. The rows are neither `SkillEx`
nor `SkillMacroEx`, so §11 does not license them, and *"no other ticket has claimed them"* is not a
reason to widen a scope rule — it is how a scope rule stops meaning anything. **Handed to ticket
14**, which owns the dragon's appearance and growth stages. Both are pure additions to an existing
named container and will merge clean whenever 14 wants them; the cost of not having them now is
that a dragon equip has no window to go in, which nothing in this ticket tests. **59 of the 61
`UI.wz` add-list roots are left**, including every `Login.img/RaceSelect/BtEvan` and `NewCharEvan`
row, which are ticket 15's.

`EzorsiaV2_UI.wz` was checked in case the HD mod serves the UI instead: it holds no `UIWindow.img`,
`Login.img` or `Basic.img` at all, so `UI.wz` is the file the client reads these from.

### Verification — §6.1 content digests, per inserted-into image

Every pre-existing child digest-identical; the only differing lines are the ids added.

| image | children before → after | differing lines | reads as |
|---|---|---:|---|
| `String.wz/Skill.img` | 613 → 683 | 72 | the 70 new ids + `TOTAL` on both sides. **All 613 pre-existing children identical.** |
| `UI.wz/UIWindow.img` | 110 → 112 | 4 | `SkillEx`, `SkillMacroEx` new; `TOTAL`. **All 110 pre-existing children identical** — including `Equip`, which moved in the first run and stopped moving once the two dragon-equip rows were handed to ticket 14. |
| `Etc.wz/MakeCharInfo.img` | 6 → 8 | 6 | `EvanCharMale`, `EvanCharFemale` new; `Name` changed (gained 2); `TOTAL`. |
| `Skill.wz` root | 76 → 88 | 26 | the 12 new roots + `TOTAL`. **All 76 pre-existing images identical.** |

**The whole-directory gap was closed by a different route.** `WZ-MERGE-PROCEDURE.md` §5.4 states
that a whole-`WzDirectory` manifest row is **not** content-checked — there is no single image to
digest — and `Skill.wz/Dragon` is the first such row anyone has actually written out. So it was
checked against the source instead: `WzMerge hash` on the merged `Dragon` directory and on v84's
own give the identical digest,
`d27e4899a6239adf5da65896bd340d34fb474da6548d300dad30b1dddbb1e4ec`, across all ten images and their
decoded canvas payloads. `Dragon/2200.img` carries `info`, `stand`, `move`, `magicmissile` — which
is the render-follow-move set criteria 3 and 4 ask for.

Gate re-fire on this ticket's own output: `already exists in target` on every row, exit 5.

Output hashes (`Server\wz-merge\10\`, the list-proving merge from pristine v83 — **not the install**):

```
Etc     10F19943398838E821B43890074EC1F0BEADBF41CEDC1EE3C8940576EC65C89A     1,803,928
UI      E312D8E60A9D739FCF61DAD9F07BEF0A12199D0196EEA9E244DDFC3A4159E28B    28,715,550
String  46AEFAC5904EBEC75CB2D927275BE50EF22352702C8564BF866F9AE3B02655E9     3,605,181
Skill   A334579124C8928949D7A6E3C3556B4440EB6800255D0F6546FF10A63AA510AD   114,035,663
```

`Etc.wz` and `UI.wz` come out **byte-identical in `10\` and in the composed `10c\`** — same
pristine base, same rows, deterministic merge — because no other ticket touches either file.
`Skill.wz` and `String.wz` do not, and that asymmetry is the whole reason the install target is
`10c\`: 05's 27 rows and 04–08's 510 are in the composed copies and absent from `10\`'s.

### Server XML

`WzMerge xml` into `wz/`, same four lists, `--deny` on every run, no `--force`:
`Etc 4/0` · `UI 2/0` · `String 70/0` all exit 0, and `Skill` **11 added / 1 refused, exit 3** —
`Skill.wz/Dragon` refused as *a directory row has no `.img` segment and cannot map to an XML file*.
That refusal is correct and needs no separate list: `wz/Skill.wz/` is 76 flat `<id>.img.xml` files
with no subdirectory, and the server resolves skills only as `Skill.wz/<jobid>.img/skill/<id>`.
(A separate XML-only path list was written and then deleted — the tool already draws the line, and
two lists that must agree is a divergence waiting to happen.)

`git diff --stat wz/` is **1,870 insertions, 0 deletions** across three files, plus eleven new
`wz/Skill.wz/{2001,2200,2210..2218}.img.xml`.

### Composition — folded in, re-run, 13 files

`compose.ps1`'s `$files` gained `"10"` on `Skill` and `String` and two new entries, `Etc` and `UI`;
`$expect` 1,662 → **1,750**. Full re-run staged at `Server\wz-merge\10c\`.
**Nine of the eleven pre-existing outputs are byte-identical to 03i's** — only the two files this
ticket adds rows to differ, and `Quest.wz` is still `5F37E5F5…`. Detail, per-file exit codes and
hashes are in `docs/wz-baseline/merge-lists/composed/README.md`.

**A `compose.ps1` defect this fold-in created and then closed.** The first run emitted a **manifest
row that was a fragment of a comment**: a backtick used as a quote mark inside one of `$perFile`'s
double-quoted strings — `` `no `` — is a PowerShell newline escape, so the comment block split in
two and its second half was written out as a row. Nothing downstream looked wrong: the list was
plausible and `WzMerge` folded it into an exit 3 the table already expects elsewhere.
`compose.ps1` now asserts **every emitted row starts with `<Name>.wz/`** and throws naming the
offender; proven both ways, and with the stray row removed `Skill.wz` re-merges byte-identical,
confirming it had cost nothing but the exit code.

Two smaller doc defects fixed in the same file while it was open, both pre-existing: the `String`
block claimed the 41 force roots were *"38 + ticket 08's 3"*, which is 41 by luck and 40 by
arithmetic — `COLLISION-FORCE.txt` holds **37** and the unnamed 41st is 03f's `Npc.img/9201144`;
and a mojibaked em dash (`Ã¢â‚¬â€`) in a comment left by an earlier UTF-8 round-trip.

### Ticket 09's handoff, taken

`scripts/quest/3759.js` — the quest that grants Soaring, which ticket 06's Crimson Sky maps gate on
— branched on job and, for Evan, printed *"Evan's Soaring (20011026) needs Skill.wz/2001.img, which
is not merged yet"* instead of teaching the skill. `V84QuestNodeTest`
`.quest3759GrantsSoaringAndItsScriptTeachesIt` pinned that state by asserting
`wz/Skill.wz/2001.img.xml` **does not exist**, with the message *"…appeared - 3759.js's Evan guard
can now be replaced by a teachSkill"*. It appeared. The guard is gone, all four job variants now
call `qm.teachSkill`, and the assertion is inverted into the positive one it was waiting to become.

### What this ticket could not do

- **Nothing in-game.** The game cannot be launched from here. Criteria 3, 4, 5 and 6 — renders,
  dragon follows and moves and other players see it, despawn, skill window — are **human-verified**
  and are staged below. No playability claim of any kind is made.
- **Criterion 1** is met as *merged and staged*, not as *installed*: nothing was written to
  `D:\games\MapleStory\`.
- **Ticket 11's crash audit is not pre-empted.** Some Evan skills reference client-side string-pool
  actions v83 does not have and will crash the client; this ticket merged the full skill set on
  purpose, because 11's method is to fire every skill and strip the crashers. If the skill *window*
  itself crashes, that is 11's finding and criterion 6 is what surfaces it.

---

## Human steps — staged, not performed

### ⚠ Run the gate patcher after **every** client launch

`tools\patch-evan-gate.ps1` writes 21 × `0x90` at VA `0x00761714` **in the live process** —
`MapleStory.exe` is Themida-*compressed* and cannot be patched on disk. It is not persistent.
**Without it no Evan skill resolves in the client**, and every step below involving an Evan will
fail in a way that looks like bad WZ data. Ticket 01 `## 01b` has the full procedure; the short
form, from the worktree root, with the client *not yet* running:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-evan-gate.ps1
```

then launch `MapleStory.exe` and confirm `tools\evan-gate-patch.log` ends
`GUARD PASS` → `RESULT: PATCHED and verified`.

### Step 0 — install the composed merge. **Thirteen files, from `10c\`, not `10\`.**

Client closed. This supersedes `composed/README.md`'s ten-file install and ticket 03's
`wz-merge\post\` step; do this one instead of, not as well as, either.

```
copy D:\games\MapleStory\Server\wz-merge\10c\<Name>.wz D:\games\MapleStory\<Name>.wz
```

for **`Character` `Etc` `Item` `Map` `Mob` `Morph` `Npc` `Quest` `Reactor` `Skill` `Sound`
`String` `UI`** — one at a time, checking the size after each. Expected sizes and SHA-256 are in
`composed/README.md`. `Base`, `EzorsiaV2_UI`, `List` and `TamingMob` are untouched and must stay so.

**Do not install `Server\wz-merge\10\`.** Those four files were merged from pristine v83 to prove
this ticket's path lists; `10\String.wz` alone would drop everything tickets 04–08 added.

Rollback is per-file from `_backup\client-v83-EzorsiaV2-2026-08-15\`, and on the server side
`git checkout -- wz/`. Both are all-or-nothing.

### Step 1 — job change, criterion 2

Server up. With an existing character and the gate patched:

- `!job 2001` → the character becomes an Evan beginner. **No dragon yet** — that is correct,
  `Character.changeJob():1262` excludes 2001 deliberately.
- `!job 2200` → **PASS** if it succeeds. Before this ticket it printed "Jobid 2200 is not
  available."
- `!job 2218` → the tenth job level, same expectation.
- `!job 9999` → must still print "Jobid 9999 is not available."

### Step 2 — the dragon, criteria 3, 4 and 5

Needs **two** clients logged in on the same map; one of these criteria is specifically about what
the *other* player sees, and that is the half that was broken.

1. The character renders as an Evan — body, no missing sprites, no crash when it appears.
2. On `!job 2200`, a dragon appears beside you **and on the second client's screen**, without
   either of you changing map. *(This is the `createDragon()` fix; before it the second client saw
   nothing until the Evan walked through a portal.)*
3. Walk around. The dragon follows, and its movement is mirrored on the second client
   (`MoveDragonHandler` → `PacketCreator.moveDragon`).
4. Change map. The dragon disappears from the old map for the second client and reappears with you.
5. Log the Evan out. Have the second client leave the map and walk back in: **there must be no
   dragon there.** *(This is the `getObjectId()` fix; before it the dragon stayed in the map's
   object list and was spawned for the next arrival.)*
6. `!job 100` on the Evan → the dragon disappears for both clients.

### Step 3 — the skill window, criterion 6

Open the skill window on the Evan (at 2200, and again at 2218).

- **PASS** — it opens, shows the ten-job-level layout, and the client survives. Skill *names*
  should be real English, not `MISSING NAME`.
- **FAIL, and it is ticket 11's** — the client closes or freezes on opening it. Record the job
  level it happened at and go to ticket 11; do not start deleting nodes from `Skill.wz` here.

`!maxskill` on the Evan and then firing every skill is **ticket 11's method**, not this ticket's.
If you do run it, note that three skills — `20011018` Yeti Rider, `20011019` Witch's Broomstick,
`20011031` Balrog — will cast and produce no mount. That is the recorded ticket-12 gap above, not a
merge defect.

### Step 4 — regression, the part that protects everything else

With the composed merge installed, log in an **existing non-Evan** character: move, attack, change
map, open the skill window, buy something from a shop. Nothing should differ. The **skill window**
is the one worth opening deliberately: `UIWindow.img` is the only live UI image this ticket added
children to, and `SkillEx`/`SkillMacroEx` sit beside the ordinary `Skill` window in it.

Also worth doing once, since the composed install carries every ticket from 04 onward and this is
the first time any of it reaches a client: `!item 2001500` → a Red Potion that heals 50 HP, is named
"Red Potion", and cannot be traded. That is ticket 03's tracer, and it is in
`composed/Item.paths.txt`, so it doubles as a check that the composed `Item.wz` and `String.wz`
landed rather than only ticket 10's files.
