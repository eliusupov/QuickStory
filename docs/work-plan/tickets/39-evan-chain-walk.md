# 39 - Evan chain, walked end to end server-side

Verification only. No production code, no wz, no script changed. One new test class,
`src/test/java/server/EvanChainRealLoad.java` (6 tests, run with `-Dtest=EvanChainRealLoad`).

## The chain the data actually declares

Read out of `Check.img` / `Act.img` for all 135 merged Evan ids, not off a wiki.

```
farm chain, job 2001, level 2..7
  22000 Strange Dream            start npc 1013100 q22000s | end npc 1013101 q22000e
  22001 Feeding Bull Dog         start npc 1013101 q22001s | end npc 1013102 item 4032447
  22002 Sandwich for Breakfast   start npc 1013101 q22002s | end npc 1013100 q22002e
  22003 Delivering the Lunch Box start npc 1013100 q22003s | end npc 1013103 item 4032448
  22004 Fixing the Fence         start npc 1013103 q22004s | end npc 1013103 item 4032498 x3
  22005 Rescuing the Piglet      start npc 1013103           | end npc 1013103 item 4032449
  22006 Returning the Lunch Box  start npc 1013103           | end npc 1013100 item 4032450
  22007 Collecting Eggs          start npc 1013101           | end npc 1013101 q22007e item 4032451
  side: 22008 -> 22009 -> 22010, hangs off 22007, npc 1013101/1013103

gate: 22100 wants job 2001, level 10, quest 22007 COMPLETED

advancements, npc 1013000 (Mir), autoStart=1, all ten scripted
  22100 lv10 -> 2200, 22101 lv20, 22102 lv30, 22103 lv40, 22104 lv50,
  22105 lv60, 22106 lv80, 22107 lv100, 22108 lv120, 22109 lv160

Mir chain, job 2200+, npc 1013000 for most of it
  22500 -> 22501 -> ... -> 22596, plus 22400-22413 (saddle/mount), 22602/22603, 22300

Only 22000-22010, 22100-22109, 22500 onward are one chain. 22400+ branches off 22546.
```

## Ranked blockers

1. **PROVEN BROKEN - the farm chain cannot reach the job advancement.** 22004, 22005 and 22007
   each complete on an etc item the player has to find in the world, and their own `Act.img/0`
   hands out nothing. No mob drop and no reactor drop grants any of them:
   - `4032498` Thick Branch x3 (22004). `22004.js:22` points the player at mob `0130100`; mob
     130100 has 20+ drop rows in `152-drop-data.sql` and none is 4032498.
   - `4032449` Piglet (22005).
   - `4032451` Egg (22007). `22007.js:20` does `gainItem(4032451, -1)`, i.e. it assumes you hold it.

   Verified against the seed files *and* the live database: `SELECT ... FROM drop_data` and
   `FROM reactordrops` for all three return zero rows (23013 / 1116 rows in those tables). The one
   reactor on the farm, 1002008 on map 100030300, drops only `4032452` (Bundle of Hay, quest 22502).
   22007 is the prerequisite of 22100, so nothing past level 7 is reachable.
   Gate: `EvanChainRealLoad.threeFarmChainCompletionItemsHaveNoDropSourceAtAll`.

2. **PROVEN BROKEN - the whole 22500 Mir chain is unreachable.** NPC 1013000 is spawned on no map:
   the literal does not occur in a single one of the 5,337 images under `Map.wz/Map`, and `plife`,
   the only other source `MapFactory` reads, is empty on the live DB (0 rows) and unseeded in the
   repo. `QuestActionHandler.isNpcNearby` returns false on `getNPCById(1013000) == null` for any
   quest that is not `autoStart` or `autoComplete`, and `QuestScriptManager.end` repeats the check
   as `containsNPC`. 28 quests are start-blocked and 25 end-blocked this way, 22500 "Baby Dragon
   Awakens" first - the quest immediately after the 1st job advancement.
   Full list, start-blocked: 22401 22402 22406 22409 22500 22501 22503 22504 22507 22509 22512
   22514 22521 22529 22537 22539 22547 22563 22565 22578 22580 22585 22589 22590 22592 22596 22602
   22603. End-blocked: 22401 22402 22406 22409 22500 22502 22503 22506 22508 22509 22513 22526
   22529 22539 22547 22563 22565 22578 22580 22585 22589 22592 22596 22602 22603.
   Three other npcs are equally unplaced: 9901000 (22402), 2092100 (22409), 1022106 (22529).
   Gate: `EvanChainRealLoad.mirIsSpawnedOnNoMapWhichKillsTheEntire22500Chain`.

3. **PROVEN BROKEN, cosmetic-ish - a female Evan is stranded in the tutorial.**
   `scripts/map/onUserEnter/PromiseDragon.js:25` calls
   `showIntro(".../PromiseDragon/Scene" + gender)`, but `Effect/Direction4.img/PromiseDragon` has
   only `Scene0`. Map 900090101 has no portal but `sp`, and its only exit is the `type=2` warp
   inside that scene (`-> field 100030100`). Gender 1 gets no scene, so no warp. The sibling
   cutscenes are fine: `meetWithDragon`, `getDragonEgg` and `crash` each have Scene0 *and* Scene1.
   Not fixed here - `scripts/map/**` is not this ticket's to touch. The live char 50 is gender 0,
   so the owner is not currently exposed.

4. **PROVEN BROKEN, later - quest 22524 cannot complete.** Its end requires 100 kills of mob
   `9101004`, which has no image in `Mob.wz`. Two more end-requirement mobs are in `Mob.wz` but
   spawned on no map: `2230112` (22532, 100 kills) and `9300393` (22596, 1 kill).

5. **PROVEN BROKEN, minor - `scripts/map/onUserEnter/evanTogether.js` is absent.** Declared by map
   100030102 (Evan's living room, where 1013101 gives 22001/22002/22007) and by 914100021. It is
   the only missing script of the 24 maps in the Evan areas; every portal script those maps name
   exists. Nothing gates on it, so the cost is a missing cutscene, not a stop.

6. **NOT A BLOCKER - the live Evan is simply in the wrong place.** Character 50 `evan` is level 1,
   job 2001, map 10000. `EvanCreator.START_MAP` is already `MapId.EVAN_TUTORIAL_START` (900010000),
   so a newly made Evan starts correctly; char 50 predates that or was warped. He needs a warp to
   900010000 (or straight to 100030101) before any of this is exercisable.

## Proven working

- The five farm quest givers are all in `Map.wz` life, read back through the same `DataProvider`
  `MapFactory` uses: 1013100 on 100030101, 1013101 + 1013102 on 100030102, 1013103 on 100030300,
  1013105 on 100030310.
- Every `startscript` / `endscript` the farm chain declares has a file. Across all 135 ids, no
  declared script file is missing.
- Every reward item and every check item in all 135 quests resolves in `Item.wz` / `Character.wz`.
  Zero missing ids.
- All ten job advancements are `autoStart`, which is the *only* reason they survive Mir being
  unplaced.
- The tutorial route resolves: 900010000 --in00--> 900010100 --contactDragon--> 900090100
  --meetWithDragon scene--> 900010200 ... --PromiseDragon scene--> 100030100 --out00-->
  100030101. Every portal script on that route exists.

## The dialogue lockout, Evan's variant

Thirteen Evan quest scripts - 22000 22001 22002 22003 22004 22007 22008 22500 22501 22502 22503
22504 22507 - spell the window X as `if (mode == 0 && type == 0) status--`, not as a missing
dispose. The `mode == 0 && type > 0` sweep does not see this shape (25 of 315 quest scripts and 5
of 708 npc scripts carry it; 13 of those 25 are Evan's). The fall-off is identical: from the first
box `status` goes to -1, no branch matches, the handler returns having sent nothing.

`disposeIfStalled` (commit d91ef0287) catches it, because it keys on "pushed no dialogue and did
not dispose" rather than on the guard's spelling. Asserted by execution on the real 22000.js in
`EvanChainRealLoad.theWindowXOnTheVeryFirstEvanDialogueDoesNotWedgeTheSession`. Only two npc
scripts in the whole Evan chain carry the *first* shape: 1040000 and 2012012.

## Verdict on the four recently added scripts

- `incubation_dragon.js` - **sound.** `Effect/Direction4.img/incubation` really is an empty node
  with no Scene children, so there is nothing to `showIntro`, and 900090104's own
  `returnMap`/`forcedReturn` really is 100030300. The map has no portal but `sp`, so without the
  explicit warp the player would be stuck.
- `evanPromotion.js` - **sound.** `Direction4.img/promotion` has exactly Scene00/Scene01/Scene1/
  Scene20/Scene21/Scene3, and their `type=2` fields are 900090001/900090001/900090002/900090003/
  900090003/900090004, which is the order the script assumes.
- `evanAlone.js` - **plausible, still UNPROVEN.** No `evanAlone` node exists in any of the five
  `Direction*.img`, so `unlockUI()` alone is the only defensible reading, and both maps it covers
  (900010000, 900020100) do carry their content on `scr*` trigger portals whose scripts all exist.
  Nothing observed contradicts it; nothing positively confirms it either.
- `outAfrienMemory.js` - **not Evan's, and the destination stays a guess.** Map 900030000 is Aran
  content (Afrien is Aran's dragon); no Evan quest, portal or map in this WZ routes to it. Its
  `returnMap`/`forcedReturn` is 914100021, which is what the script warps to, so the fallback is
  at least the map's own declared one. Note 914100021's `onUserEnter` is `evanTogether`, the one
  missing map script - so the player lands somewhere whose entry script does not exist.

## Not verified

- Whether the client offers a Complete button at all for the `autoStart` advancements with Mir
  unplaced. The server would accept it (`isNpcNearby` exempts autoStart, `canComplete` only wants
  the npc id in the packet), but nothing here drove a real client.
- The 22400-22413 saddle/mount branch and everything past 22546 beyond the npc/item/mob checks.
- `infoNumber`/`infoex` quests (22530, 22556, 22557, 22580, 22588, 22589, 22591) - their progress
  keys were not walked.
