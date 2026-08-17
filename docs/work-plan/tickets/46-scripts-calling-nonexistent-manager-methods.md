# 46 - Scripts calling manager methods that do not exist

Follow-up to `dec59a692` (`qm.sendImage` -> `showInfo`, 13 call sites). That was one instance of a
class: **Graal resolves a method name only when the call executes**, so a script can name anything
and nothing complains until a player walks into that exact branch. Nothing logs at load time.

Guard now exists: `src/test/java/server/ScriptManagerApiRealLoad.java`. It reflects the real public
methods of each bound manager (`Class.getMethods()`, so inherited ones are included) and fails on any
`handle.name(` with no match. Known-dead calls are in a visible `ALLOWED` map, and a second test
fails if an allowlist entry stops matching, so the list cannot rot.

## Sweep result

22 distinct names over 36 call sites across `scripts/{npc,quest,portal,map}`. Checked every one
against the Java sources by hand.

**False-positive rate: 0/22.** None of the 22 names is declared anywhere under
`src/main/java/scripting/`, and the manager chain the sweep assumed is the real one
(`QuestActionManager -> NPCConversationManager -> AbstractPlayerInteraction`,
`PortalPlayerInteraction`/`MapScriptMethods -> AbstractPlayerInteraction -> Object`). Every hit is a
genuine `TypeError` waiting for a player.

The heuristic's weakness is not false positives, it is **classification**: "the method does not
exist" is not the same as "this matters". Three of the eight the sweep flagged as likely-reachable
are in scripts nothing in this wz tree can reach.

`scripts/item` (`im`) and `scripts/reactor` (`rm`) were swept too and are clean.

## Fixed (all three are placed by this wz tree, i.e. live)

| script | was | now | why |
|---|---|---|---|
| `npc/2040020.js` | `cm.gainRandomItem(newItem)` | `cm.gainItem(item, 1, true, true)` | Sarah, Ludibrium glove refining. `newItem` is not even declared in the file. The three sibling refiners - `2040021`, `2040022`, `2080000` - all spend the stimulator with exactly `cm.gainItem(item, 1, true, true)`; 2040020 is the stale copy. Every stimulator craft threw *after* the mats, meso and stimulator were already taken. |
| `portal/metro_Chat00.js` | `pi.showWZEffect(path)` | `pi.showIntro(path)` | Named by `910320000` (subway platform). `Effect/Direction2.img/metro/Im` is a Direction node (`type`/`visual`/`start`/`x`/`y`) - the same shape as the aranTutorial scenes `showIntro` plays, not a `Map/Effect.img` path, so `showEffect`/`mapEffect` (FIELD_EFFECT mode 3) would render nothing. The node is present in this tree, so this is not the missing-path client crash `MapAndPortalScriptsRealLoad` guards against. |
| `npc/9000011.js` | `cm.getChannelServer().getEvent()` x4 | `cm.getEvent()` | GM event NPC. `getEvent()` exists on `NPCConversationManager` but returns `server.events.gm.Event`, not the bare map id an older `Channel.getEvent()` gave back - so `> -1` and the warp argument both had to be re-expressed via `getMapId()`. Ternary split into if/else so the `warp` overload is chosen statically rather than off a mixed int/String value. |

## Left alone, deliberately

No Java method was added. Adding to `AbstractPlayerInteraction` widens the scripting API for every
script in the game, and not one of these earns that.

- **`npc/1052115.js`** (Mr. Lim) - **the only unresolved reachable one.** NPC and maps
  (`910320000`, `910330001`, `9103201xx`/`9103301xx`) are all in this tree, so two of its three menu
  options are live and both throw: `cm.start_PyramidSubway(-1)` and `cm.bonus_PyramidSubway(-1)`.
  There is no subway party quest in this server at all - the only Pyramid it has is Nett's, via
  `NPCConversationManager.createPyramid(mode, party)`, which is different content. Renaming would
  send players somewhere else, so it is left throwing. The third option (the Honorary Employee
  medal) works. Cost of a real fix is a party-quest implementation, not a method.
- **`npc/9000004.js`** (Ola Ola stage NPC, on `109010100`) - `mapMobCount`, `clear`, `warpMembers`.
  All three are behind an earlier failure: every path first does `eim.getPlayers()` on
  `cm.getPlayer().getEventInstance()`, and no script in `scripts/event` puts a player into an
  instance covering `109010100` or `109020001`, so `eim` is null and the script throws before
  reaching any of them. Fixing the three would not make it run. For the record they would be
  `countMonster()` and `eim.warpEventTeam(109020001)`; `clear()` has no equivalent and no other
  script in the tree calls it.
- **`npc/2141000.js`** (Kryston, the Pink Bean summon) - `removeNpc`, `forceStartReactor`.
  `Npc.wz` has 2141000 but **no map in this tree places it** and no script spawns it;
  `PinkBeanBattle.js` spawns the boss itself. Dead script. Note the casing hypothesis fails here:
  neither `removeNpc` nor `removeNPC` exists on any manager, only `PacketCreator.removeNPC` (static,
  not exposed to scripts).
- **`npc/1096003.js`, `npc/1096005.js`** - the sweep listed these as likely-reachable; they are not.
  They are Cannoneer tutorial NPCs. `Npc.wz` has neither id, and the map they warp to
  (`912060300`) does not exist in this tree.
- **`cannon_tuto_*` and `Resi_tutor*`** (portal + `map/onUserEnter`) - no map in `Map.wz` names any
  of them, confirmed by grep over the whole tree. They want the client "direction" cutscene API
  (`sendDirectionInfo`, `setDirectionStatus`, `lockUI2`, `startDirection`, `setDirectionMode`,
  `setStandAloneMode`) which this server has never had.
- **`quest/23011.js`, `quest/2570.js`** - `showItemGain`. Neither quest id is in
  `Quest.wz/QuestInfo.img.xml`, so `QuestScriptManager` never opens either file.

## Sweep counts

- before: 22 distinct names / 36 call sites
- after: 19 / 33 (`gainRandomItem`, `getChannelServer`, `showWZEffect` gone)

The 19 that remain are all covered by `ALLOWED` in `ScriptManagerApiRealLoad`, with the evidence for
each written next to it.

## What the guard does not see

A call written literally as `handle.name(`. Not `Java.type` interop, not calls on returned objects
(`cm.getPlayer().foo()`), not a handle passed on to another function. Green means no script names a
method its own manager lacks; it does not mean the scripts are correct.
