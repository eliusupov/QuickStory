# Ticket 41 - Evan chain unblock (farm chain -> Mir chain)

Goal: "as it was in GMS v84 for Evan". Authenticity is the acceptance criterion, not "the chain
becomes completable". Nothing below invents a drop source, a rate, or an NPC placement.

## The headline correction

The starting premise was that quests 22004 / 22005 / 22007 were all dead because their completion
items (4032498 Thick Branch, 4032449 Piglet, 4032451 Egg) had **zero rows in `drop_data` and
`reactordrops`**. The absence was real. The conclusion was wrong, and wrong in the expensive
direction - acting on it would have invented three drop sources.

**Only one of the three was ever a drop.** The other two are obtained by CLICKING AN NPC, which is
exactly why searching the drop tables found nothing and always would have.

| Item | Quest | Real v84 source | Status before |
|---|---|---|---|
| 4032498 Thick Branch x3 | 22004 | mob **130100 Stump**, drop | genuinely missing - **fixed, changeSet 155** |
| 4032449 Piglet | 22005 | npc **1013200 Baby Pig**, click | script + placement already existed; map was unreachable |
| 4032451 Egg | 22007 | npc **1013104 Hen**, click | already worked |

### Evidence, per item

**4032498 - mob 130100 (Stump).** Four independent client-data nodes name it in plain words:
- `wz/String.wz/Etc.img.xml:9421` item desc: "A tree branch from a **Stump**."
- `wz/String.wz/Mob.img.xml:63` mob 130100 = "**Stump**"
- `wz/Quest.wz/QuestInfo.img.xml:6806` objective: "Defeat some of the `#o0130100#`s nearby"
- `scripts/quest/22004.js:22` dialogue: "You'll be able to find Thick Branches from the nearby
  `#o0130100#`s"

It must be a drop, not a grant: `wz/Quest.wz/Act.img.xml:3389-3393` - 22004's Act nodes `0` and `1`
are **both empty**. Contrast 22003 (`Act.img.xml:3362`) and 22006 (`Act.img.xml:3410`), which DO
grant their fetch items on the start node. That contrast is what settles it. Not a reactor either:
map 100030300 has exactly one reactor, 1002008, whose only drop is 4032452 for quest 22502.
Stump is spawned on 100030300, the Evan farm (`wz/Map.wz/Map/Map1/100030300.img.xml`; map life mob
ids are zero-padded to 7 chars, hence `0130100`).

**4032451 - npc 1013104 (Hen).** `QuestInfo.img.xml:6832` says it outright: "You can obtain an
**Egg** by clicking on a **Hen**". `scripts/npc/1013104.js` already grants it during quest 22007,
and the Hen is already placed on 100030102.

**4032449 - npc 1013200 (Baby Pig).** `QuestInfo.img.xml:6814` sends the player to map 900020100.
`scripts/npc/1013200.js` grants it, gated on hidden marker quest **22015**, which
`scripts/map/onUserEnter/babyPigMap.js` force-starts on entry to 900020110. 22015 is deliberately
absent from Quest.wz - `Quest.java:121-127` returns an empty requirement-free Quest for an unknown
id, which is what a pure marker wants. The trio (map script starts it / npc consumes it /
`scripts/portal/babyPigOut.js` checks it) is coherent and authentic.

### Rate for the one real drop

GMS drop rates were server-side and are in no WZ file, so the exact v84 number is **not
recoverable** - stated as a gap rather than invented. Instead of picking one, changeSet 155 copies
the rate this server already uses for a quest item dropped by **this exact mob**: `drop_data`
already holds three quest-gated rows on dropperid 130100 and all three are `chance 80000`
(4031773/q2145, 4032374/q2405, 4032378/q2408). Effective rate is 16% per Stump - `MapleMap.java:722`
doubles ETC chance - so roughly 19 kills for the 3 the quest wants. `questid=22004` makes the drop
visible only while the quest is live and the player holds fewer than 3
(`Character.java:5810-5830 needQuestItem`), so it cannot leak into the economy.

## The blocker that actually killed 22005 - and the whole intro

**`ChangeMapHandler` never whitelisted Evan.** Every Evan cutscene map ends in a client-side type-2
scene warp: `Effect/Direction4.img/<scene>/SceneN` carries an `<int name="field">` and the client
asks to move itself, with **no portal**. That request lands in `ChangeMapHandler`, which only
honours a portal-less map change when the SOURCE map is in a hardcoded whitelist
(`ChangeMapHandler.java:113-141`). Cygnus (`divi==9130401`) and Aran (`divi==9140900`) are there.
Evan's `9000900xx` / `9000901xx` were not, so **every Evan cutscene warp was silently dropped** and
the player left standing on a map that has no portal at all, only `sp`.

Proof of the mechanism: the existing whitelist entries correspond one-for-one to Aran's
Direction3 scene `field` values (`Direction3.img.xml:2356` -> 10000 matches the `divi==0` branch;
`:2501` -> 1020000 matches the `divi/10==1020` branch). Same shape, Evan just never got an entry.

| Source map | Scene | Target |
|---|---|---|
| 900090100 | meetWithDragon | 900010200 |
| 900090101 | PromiseDragon | 100030100 (Evan's room) |
| 900090102 | crash_Dragon | 900020200 |
| 900090103 | getDragonEgg | **900020110** (the piglet hollow) |
| 900090000-3 | evanPromotion | 900090001-4 |

900020110 has **no inbound portal anywhere in Map.wz** - the scene warp is its only route. So this
one gap is what made 4032449 unobtainable and killed 22005 -> 22006 -> 22007 -> 22100.
900090104 is deliberately excluded: its Direction4 node is empty and
`scripts/map/onUserEnter/incubation_dragon.js` already warps by hand.

## Mir is a summon, not a field NPC - `plife` would have been a hack

Mir (1013000) is on no map, and that is **correct**. `wz/Etc.wz/NpcLocation.img.xml:168` gives
1013000 the location **`-1`** (no field), where his immediate neighbour 1013001 gets a real
`900010200` - and 1013001 really is placed there. This server already models Mir the way Nexon did:
`server/maps/Dragon.java` (`MapObjectType.DRAGON`, positioned at its owner), created per-player for
every Evan past job 2001 in `PlayerLoggedinHandler.java:407` and on job change
(`Character.java:1263`). `PacketCreator.java:7605` javadoc literally says "Sends a request to remove
Mir". Quest 22500's own text: "Talk to him by **clicking on the Baby Dragon**".

Seeding `plife` would have parked a static statue of Mir on one map while the real Mir flies beside
the player. **Rejected as a hack.** The faithful fix is one guard in
`QuestActionHandler.isNpcNearby`: a summoned Mir is always at his owner's position, so "is the npc
nearby" is answered by "does this player have their dragon out". Scoped to `npcId == 1013000 &&
getDragon() != null`, so it is not a blanket exemption.

This unblocks **25 quest starts and 22 ends**. Gate 2 (`QuestScriptManager.end`) never fires in the
Mir chain - none of the 33 Mir end nodes declares an `endscript` - so `isNpcNearby` was the single
effective blocker and one guard covers all four QUEST_ACTION cases.

## Female Evan client crash

`Effect/Direction4.img/PromiseDragon` has **only Scene0**, while its siblings meetWithDragon /
getDragonEgg / crash each carry Scene0 AND Scene1. PromiseDragon's Scene0 is a gender-neutral
"word" text effect plus the type-2 warp - no character sprite - so v84 never needed a gendered
copy. `PromiseDragon.js` appended `getGender()`, so a FEMALE Evan got `.../Scene1`, a path that does
not exist, and `showIntro` on a missing path crashes the client. Fixed to always use Scene0.

## Remaining blockers (NOT fixed - reported)

Later in the chain, all from the same root cause: **this repo's `wz/Map.wz` is a v83-era dump while
`wz/Quest.wz` is v84**, so v84-era NPCs and mobs are absent from the map data. These need an
additive Map.wz merge, not a drop row or a `plife` hack.

- **Unspawned mobs:** 9101004 (quest 22524), 2230112 (22532). ~~9300393 (22596)~~ closed by
  `enterBlackfrog.js`.
- **Unplaced npcs:** 1022106 (22529). ~~1052002 (22535)~~, ~~2092001 (22587)~~ and ~~2030015
  (22576)~~ were placed by the 38 life placements in `ce3895453`, each on the map
  `Etc.wz/NpcLocation.img` names for it (103000000 / 251000000 / 211040400). ~~1013202
  (22575/22576/22577/22581)~~ was never a gap: `NpcLocation.img/1013202/0` is **-1**, the same
  no-field marker Mir carries, and all four of his quests set `autoStart 1` in QuestInfo.img, so
  `QuestActionHandler.isNpcNearby` skips the proximity block entirely. Their END npcs - 2022003,
  2030015, 1013203 - are all placed.
- **9901000 (quest 22402) is a Hall-of-Fame PlayerNPC slot**, not a real NPC - it is filled at
  runtime with a level-200 warrior's name, and `playernpcs` has 0 rows. Do NOT place it.
  Reserved allocator range is **9900000-9906599 and 9977777** (`PlayerNPC.java:321-323`,
  `NpcId.java:38`) - the "9901910-9901919" figure in the original brief describes which `Npc.wz`
  images are fabricated, not what the allocator hands out.
- Quest items still lacking a source further along: 4032453 (22503), 4032459 (22524), 4032460
  (22529), 4032461 (22531), 4032462 (22532), 4032463 (22548), 4032466 (22559), 4032467 (22562),
  4032470 (22572), 4032472 (22586). Each needs the same evidence pass done above before any row is
  written. Two came off this list on re-verification and neither was ever a drop:
  **4032455** (22510) is handed over by 22510's own `startscript q22510s` -
  `scripts/quest/22510.js:13` - which is why the drop tables have no row for it; **4032468**
  (22567) is granted 10 at a time by `Act.img/22568/1`, the repeatable hand-in at npc 2030012 that
  takes 5 each of 4000070/4000071/4000072/4000068, all four of which already have `drop_data` rows
  in `152-drop-data.sql`.
- **`scripts/portal/inDragonEgg.js:8`** falls back to `warp(100030301, 0)` and map **100030301 does
  not exist** in Map.wz. Fires when an Evan clicks the 100030300 `in00` portal without quest 22005
  started. Not blocking; left alone because `scripts/portal/` was outside this ticket's ownership.

## Files

- `src/main/resources/db/data/155-evan-tutorial-drop-data.sql` (new) + `changelog-data.xml`
  changeSet **155** (with `<rollback>`)
- `src/main/java/net/server/channel/handlers/ChangeMapHandler.java` - Evan intro whitelist branch
- `src/main/java/net/server/channel/handlers/QuestActionHandler.java` - Mir dragon guard
- `scripts/map/onUserEnter/PromiseDragon.js` - Scene0 unconditionally
- `src/test/java/server/EvanFarmChainSourcesRealLoad.java` (new, 8 tests)
- `src/test/java/server/EvanChainRealLoad.java` - two tests whose premise this ticket disproved,
  rewritten to assert the real model

Full suite: **2151 passed / 0 failed**.

## Activation

Everything here needs a **SERVER RESTART**. Drop tables are cached at startup
(`MonsterInformationProvider`), the two handler changes are compiled Java, and changeSet 155 runs
via Liquibase on boot. There is no GM reload command for `drop_data`. The restart is the owner's
call - he was mid-session when this was written.
