# 45 - Early game, walked in play order: what works and what still blocks

Status: **verification only, nothing patched.** Findings below are evidence for whoever fixes them.

Sampled at 2026-08-17. `drop_data` = **23,014 rows** at the time of sampling (re-count before
trusting; another agent is adding changeSets). Baseline suite before this ticket: **2163 passed /
0 failed** at working tree `ea3968399` + three other agents' uncommitted work. The brief said 2162;
2163 is what this tree actually measured. After: **2163 + 61 = 2224 passed / 0 failed** - see B0 for
why that is two numbers and not one.

New test: `src/test/java/server/EarlyGamePlayOrderRealLoad.java` (10 tests).

### B0 - `mvnw test` has never run a single `*RealLoad` class

`pom.xml` configures `maven-surefire-plugin` with no `<includes>`, so surefire uses its defaults:
`Test*`, `*Test`, `*Tests`, `*TestCase`. **`*RealLoad` matches none of them.** Measured: a full
`mvnw.cmd -o test` run that reported `Tests run: 2163` contains **zero** occurrences of the string
`RealLoad` in its output.

The `*RealLoad` convention was adopted to dodge the `WZFiles.DIRECTORY` static race. It also dodges
the runner. Every "full suite N passed / 0 failed" reported tonight as evidence for a `*RealLoad`
test was true about the suite and silent about that test - those classes only ever ran when someone
typed `-Dtest=<Name>`.

Run explicitly, all nine of them are green:

```
mvnw.cmd -o test -Dtest=*RealLoad
  EarlyGamePlayOrderRealLoad     10     EvanQuestSourcesRealLoad       12
  EarlyGameQuestScriptsRealLoad   4     MapAndPortalScriptsRealLoad     5
  EvanChainRealLoad               7     MedalQuestFallbackRealLoad      6
  EvanFarmChainSourcesRealLoad    8     Quest1021RealLoad               5
  V84EvanQuestRealLoad            4
  Tests run: 61, Failures: 0, Errors: 0
```

Not fixed here - `pom.xml` is outside this ticket's file ownership, and the fix needs a decision
(add `<includes>` and solve the `WZFiles.DIRECTORY` race for real, or keep them out and add
`-Dtest=*RealLoad` as a second CI step). **Until then, `mvnw test` alone is not a regression check
for any of tonight's Evan work.**

---

## Walk 1 - fresh Explorer

| # | Step | Verdict | Evidence |
|---|------|---------|----------|
| 1 | Character creation, job=1 -> `BeginnerCreator`, level 1, map 10000 | WORKS | `BeginnerCreator.java:45` `MapId.MUSHROOM_TOWN` |
| 2 | 10000 -> 20000 -> 30000 -> 40000 -> 50000 (Maple Road) | WORKS server-side | all `pt=2` portals; `everyHopFromExplorerCharacterCreationToLithHarbourExists` |
| 3 | Every Maple Road `onUserEnter` hook (`go10000`..`go2000000`) has a file | WORKS | `everyMapOnEitherWalkHasTheOnUserEnterScriptItDeclares` |
| 4 | Quest 1035 "Todd's Hunting Method": kill 9300018, collect 4031802 | WORKS server-side | mob spawned 3x on 40000; `Mob.wz/9300018.img.xml`; drop row `(9300018, 4031802, 1, 1, 1035, 999999)` in `152-drop-data.sql` and live in `drop_data` |
| 5 | **Attacking on map 40000 with the v84 client** | **BLOCKED (client-side)** | client crash reports uploaded 2026-08-17T00:01, `tools/v84/cutover-server.2026-08-17-0002.log`: `ver(84), CharacterName(uguuh), ..., FieldID(40000), ZException (error code : 38 (Reached the end of the file.))` x3. Already being worked - `config.yaml` carries `USE_DEBUG_SHOW_PACKET: true  #TEMPORARY - capturing the attack sequence to find the v84 combat crash` |
| 6 | 50000 -> Amherst 1000000 -> 1010000 -> 1020000 -> Southperry 2000000 | WORKS | plain portals |
| 7 | Shanks (npc 22000) sails to Lith Harbour 104000000 | WORKS | `scripts/npc/22000.js:72` `cm.warp(104000000, 0)`; npc in `life` of 2000000 |
| 8 | Quest 1028 "To Lith Harbor!" declares `q1028s`, no file | COSMETIC | silent no-op; the boat works without it (step 7) |
| 9 | 1st job advancement, all five classes | WORKS | `everyExplorerFirstJobAdvancerIsPlacedWithAScriptThatChangesJob` - 1022000/102000003, 1032001/101000003, 1012100/100000201, 1052001/103000003, 1090000/120000101, each with a `changeJob` in its script |

Early-game quests (id < 3000) declaring a script with no `scripts/quest/<id>.js`, complete list:
`1028, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 2147`. 1048-1054 are the retired 2009 event chain
(`MedalQuestFallbackRealLoad`). Pinned by `theOnlyEarlyGameQuestsMissingADeclaredScriptAreTheKnownRetiredOnes`.
Repo-wide the figure is 367 of 660, but none of them is in the early game and none is an Evan quest.

## Walk 2 - fresh Evan

| # | Step | Verdict | Evidence |
|---|------|---------|----------|
| 0 | Character creation sends job=3 | **UNVERIFIABLE server-side** | `CreateCharHandler.java:62` handles `case 3`. Whether the v84 race-select screen sends 3 needs the client. MANUAL TEST 1 |
| 1 | 900010000 -> 900010100 (`in00`, pt 2) | WORKS | |
| 2 | 900010100 -> 900090100 (`contactDragon`, pt 7) | WORKS | script warps 900090100 |
| 3 | 900090100 `meetWithDragon` scene -> 900010200 | WORKS | returnMap 900010200, whitelisted in `ChangeMapHandler` |
| 4 | 900010200 npc 1013001 -> 900090101 | WORKS | npc in `life`; `scripts/npc/1013001.js:21` |
| 5 | 900090101 `PromiseDragon` Scene0 -> 100030100 (Evan's room) | WORKS | gender bug fixed in 7d291d81 |
| 6 | 100030100 -> 100030101 (Mom) -> 100030102 (Utah, Bull Dog, Hen) | WORKS | `out00` pt2, `evanlivingRoom` pt7 |
| 7 | Quest 22000 "Strange Dream", end grants 20 exp -> level 2 | WORKS | `scripts/quest/22000.js` |
| 8 | 22001 (dog food 4032447 from `q22001s`), 22002 (sandwich), 22003 (lunch box) | WORKS | items granted by script/Act |
| 9 | 100030102 -> 100030200 -> 100030300 (farm, Gustav, 8x Stump) | WORKS | `evanGarden0` pt7, `west00` pt2 |
| 10 | 22004: 3x 4032498 from Stump 130100 | WORKS | changeSet 155 row live in `drop_data` (id 23015, chance 80000) |
| 11 | 22005 start at Gustav, farm `in00` (`inDragonEgg`) -> 900020100 | WORKS | gated on `isQuestStarted(22005)` |
| 12 | 900020100 `evanFall` -> 900090102 `crash_Dragon` -> 900020200 -> 900020210 -> 900020220 | WORKS | plain pt2 chain + whitelisted scene warp |
| 13 | 900020220 npc 1013002 -> 900090103 `getDragonEgg` -> 900020110 | WORKS | only route to 900020110, which has no inbound portal |
| 14 | **900020110 `babyPigMap` arms marker quest 22015** | **BLOCKED** | see B1 below |
| 15 | 22005 completion, and therefore 22006, 22007, 22100 | **BLOCKED by 14** | |
| 16 | 22100 1st job advancement (2001 -> 2200) | UNREACHABLE today; script itself is sound | `scripts/quest/22100.js` uses `qm.changeJobById`, which resolves - `QuestActionManager extends NPCConversationManager` |
| 17 | Dragon spawns on job change so 22500 can talk to Mir | LOOKS CORRECT, UNPROVEN in play | `Character.java:1263-1268` `createDragon()` fires for `hasSPTable(2200)`; `GameConstants:614-626` lists all Evan jobs |
| 18 | 22500 needs 30x mob 1210100, on 100030310 behind `evanFarmCT` (`job != 2001`) | LOOKS CORRECT, UNPROVEN | gate opens once job is 2200 |

---

## Blockers, most-blocking first

(B0, above, blocks *verification* rather than play, but it undercuts every other claim made tonight,
so read it first.)

### B1 - `babyPigMap.js` cannot start its marker quest; the Evan chain hard-stops at 22005

`scripts/map/onUserEnter/babyPigMap.js:25`

```js
ms.getClient().getQM().forceStartQuest(22015);
```

`Client.getQM()` (`Client.java:1210-1212`) delegates to `QuestScriptManager.getQM(this)`, the map of
*open quest-script sessions*. Arriving on a map is not a quest script, so it is **null**, and the
call throws inside Graal. `MapScriptManager.runMapScript` swallows it (`MapScriptManager.java:78-80`)
and returns false, so the player sees only the `unlockUI()` from the line before.

Then, in order: 22015 never starts -> `scripts/npc/1013200.js` answers *"You are too far from the
Piglet"* forever -> 4032449 is unobtainable -> 22005 cannot complete -> 22006 -> 22007 -> **22100,
the 1st job advancement**, are all unreachable. A fresh Evan stops at level 6.
`scripts/portal/babyPigOut.js` additionally refuses to open until 22015 is COMPLETE, so the player
is stuck on 900020110 until they log out (`Character.java:8328` saves them at the map's
forcedReturn, 100030300).

**This is new tonight, and it is a composition bug.** The script is old (`0c1545f81`, `0a2e382c3`).
It had never run, because 900020110 has no inbound portal anywhere in Map.wz and the only route to
it is the client scene warp that `7d291d814` taught `ChangeMapHandler` to honour. Making the map
reachable exposed the script that runs on arrival.

Fix is one word, and does not need a manager at all: `MapScriptMethods extends
AbstractPlayerInteraction`, which already carries `forceStartQuest(int)`, so
`ms.forceStartQuest(22015);`. **Not applied here - this ticket is verification.**

**FIXED** in the commit that follows this ticket: `babyPigMap.js` now calls `ms.forceStartQuest(22015)`.
Pinned by `EarlyGamePlayOrderRealLoad#theBabyPigMapScriptStartsItsMarkerQuestSoQuest22005IsFinishable`,
whose assertions were inverted at the same time - it now guards the fix instead of the bug, and
additionally asserts the script never routes through `getQM()` again.

### B2 - v84 client crashes on Maple Road map 40000 (client-side, already being worked)

`ZException (error code : 38 (Reached the end of the file.))` with `FieldID(40000)`, three times,
in the crash backlog the client uploaded at 00:01:38 on 2026-08-17. 40000 is the combat tutorial map
(quest 1035, 3x mob 9300018, `infoAttack` portal). Server-side everything on that map checks out, so
this is the client reading its own WZ. Another agent has `USE_DEBUG_SHOW_PACKET` on for exactly this.

### B3 - Evan cannot reach level 10 from the chain alone (design wall, not a defect)

The eight farm quests pay out **1830 exp total** (Act.img `exp` + `qm.gainExp` in the scripts).
Against the real `ExpTable` that is **level 8**. Quest 22100 wants level 10 (3249 cumulative), so
~1419 exp has to be ground. The only mobs an Evan can reach before advancing are the **8 Stumps on
100030300** - 100030310 (20x Dragon Turtle) and 100030320 are behind `evanFarmCT.js`, which requires
`job != 2001`. Also note `GameConstants.getExpRateForLevel` returns exactly `1.0f` below level 10,
so there is no rate help. Expect a slow grind on 8 stumps.

Pinned by `theChainsOwnExpClearsEveryGateUpTo22007ButLeavesTheJobAdvancementShort`.

### B4 - farm portal `in00` is dead outside quest 22005

`scripts/portal/inDragonEgg.js` warps to **100030301** when 22005 is not started, and
`wz/Map.wz/Map/Map1/100030301.img.xml` **does not exist** as sampled. `MapFactory.loadMapFromWz:136`
NPEs on the null image; `PortalScriptManager.executePortalScript` catches it and returns false, so
the portal silently does nothing and logs one warn. Not a softlock. May resolve on its own - another
agent is still merging into `wz/`.

### B5 - item 1003028 has no name

Granted by `scripts/quest/22002.js`. `wz/Character.wz/Cap/01003028.img.xml` exists (the hat renders
and its stats load) but `String.wz/Eqp.img` has no entry - its neighbours 1003027 and 1003031 do.
`ItemInformationProvider.getName` returns null, which is handled. Cosmetic: a working, nameless hat.

---

## MANUAL TEST LIST - run in this order, ~10 minutes

1. **Race select shows Evan, and creating one works.** Character creation -> is there a 4th race?
   Create one. Expected: you land on map 900010000, job 2001, level 1, with a Legend's Guide.
   If the 4th option is absent or creation is rejected, everything in Walk 2 is moot and ticket 15b
   is the blocker. *(Unverifiable server-side - `CreateCharHandler` handles `case 3`, but only the
   client decides whether it sends 3.)*
2. **Evan intro cutscenes play and hand off.** Walk right on 900010000, touch the dragon on
   900010100. Expected: `meetWithDragon` plays, then you are on 900010200; talk to the dragon npc,
   `PromiseDragon` plays, then you are in Evan's room 100030100. Do this on a **female** Evan too -
   that is the Scene1 crash 7d291d81 fixed.
3. **Farm chain 22000 -> 22004.** Expected: 22000 ends at 20 exp and you hit level 2. Watch for
   quest windows that close and then swallow every later quest click - that is the X-close lockout
   d91ef028 netted.
4. **22005 - THIS IS WHERE IT STOPS (B1).** Start it at Gustav, take farm portal `in00`, fall,
   walk the cave, click npc 1013002. Expected today: you arrive on the piglet map, clicking the
   Baby Pig says *"You are too far from the Piglet"*, and the exit portal says *"Please rescue the
   baby pig!"*. **Log out to escape** - you will be put back on the farm.
5. **Explorer, Maple Road to map 40000, then attack a Jr. Sentinel (B2).** Expected today: client
   crash. This is the highest-value repro for whoever is on the combat crash.
6. If 5 survives: **finish 1035 at Peter, sail with Shanks, advance to 1st job** at any of the five
   advancers.
7. Only if B1 is fixed: **22006 -> 22007 -> grind to level 10 -> 22100** and confirm Mir appears
   beside you and 22500 can be started by clicking him.

---

## What this ticket could not settle

- Anything needing rendering: cutscene playback, the Complete button appearing, the race-select
  screen. All of it is on the manual list.
- Whether the v84 client sends `job=3` for Evan (step 0).
- Whether the Dragon actually appears client-side after `changeJobById(2200)`. The server-side
  branch is right; nobody has seen it in a client.
- The 40000 crash's root cause. The evidence is a client-uploaded `CLIENT_START_ERROR` blob, not a
  server stack.
