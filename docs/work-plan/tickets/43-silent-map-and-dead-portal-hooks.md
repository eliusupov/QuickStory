# 43 — Silent map hooks and dead portals on reachable maps

**Status:** partially closed — 13 hooks written, the rest adjudicated below with reasons.

## What this is

Two failure modes that produce **no log line at all**:

- `scripting/AbstractScriptManager.java:48-51` — a missing script file returns `null` silently, so a
  map declaring `info/onUserEnter` with no `.js` behind it just does nothing.
- `server/maps/GenericPortal.java:132-156` — `if (getScriptName() != null) { script } else if
  (getTargetMapId() != MapId.NONE) { warp }`. A portal that NAMES an absent script never warps and
  never falls back to `tm`/`tn`. It is a **dead portal**.

Raw counts over the real `wz/` tree are 703 dead portals and 896 silent maps, but most of that data
is unreachable. After a reachability filter (map graph from portal `tm` + `forcedReturn` + script
warp targets, BFS from starting towns, declared-but-missing script portals contributing no edge):

| | before | after this ticket |
|---|---|---|
| dead portal instances on reachable maps | 58 | 49 |
| dead portal names on reachable maps | 21 | 17 |
| silent map instances on reachable maps | 50 | 35 |
| silent map names on reachable maps | 28 | 19 |

36 of the 49 remaining dead-portal instances are the six ferry `move_*` scripts, which ticket 37
proved **inert** — do not write them. Excluding those, the real dead-portal backlog went 22 → 13.

The reachability model **under**-estimates reachability (no event-instance maps, no PQ entries with
computed map ids, no town-scroll edges), so these are lower bounds.

## Evidence rule applied throughout

`showIntro` on a path Effect.wz does not hold **crashes the client** — that is exactly how every
female Evan was crashing on `PromiseDragon/Scene1`. So the first question asked of every name was
whether `Effect.wz/Direction*.img` has a node of that name.

**It has 25 Direction nodes in total** — `effect`, `sound`, `cygnus`, `cygnusJobTutorial`,
`aranTutorial`, `aranDirection`, `mushCatle`, `gasi`, `open`, `piramid`, `metro`, `goLith`,
`goAdventure`, `swordman`, `magician`, `archer`, `rogue`, `pirate`, `ghostShip`, `meetWithDragon`,
`crash`, `getDragonEgg`, `incubation`, `PromiseDragon`, `promotion`.

**Not one of the 28 silent map names or 15 non-ferry dead portal names is among them.** None of this
backlog is a cutscene. `MapAndPortalScriptsRealLoad` pins that finding as an assertion and refuses a
`showIntro` in any of the added files.

## Written

### Map hooks — `scripts/map/onUserEnter/`

| hook | maps | what it does | evidence |
|---|---|---|---|
| `aranTutorAlone` | 914000000, 914000300, 914000400, 914000410, 914000420, 914000500 | `unlockUI()` | no Direction node. 914000000 is `MapId.ARAN_TUTORIAL_START`, where `LegendCreator` drops a new Aran; 914000400 is entered straight out of the `ClickChild` intro fired by `scripts/portal/aranTutorLost.js`; 914000300 from quest 21001's forced warp. Same two-liner as `iceCave.js` / `evanAlone.js`. |
| `go1010400` | 1010400 | `mapEffect("maplemap/enter/1010400")` | `Map.wz/Effect.img` has `maplemap/enter/1010400`; identical in shape to `go1010100`..`go1010300`. |
| `go2000000` | 2000000 | `mapEffect("maplemap/enter/2000000")` | node exists. No `unlockUI` — 2000000 is only ever walked into, never handed over from a cutscene, unlike `go10000`/`go20000`. |
| `TD_MC_title` | 106020000 | `unlockUI()` + `mapEffect("temaD/enter/mushCatle")` | `Map.wz/Effect.img` has `temaD/enter/mushCatle`. |
| `TD_NC_title` | 240070000 + 240070100..600 | `mapEffect("temaD/enter/teraForest")` or `.../neoCity1..6`, selected by the hundreds digit | `Map.wz/Effect.img` has `temaD/enter/{teraForest,neoCity1..neoCity6}` — exactly seven nodes for exactly seven maps. |
| `TD_MC_gasi2` | 106020501 | `unlockUI()` | the thorn cutscene is `Effect/Direction2.img/gasi`, fired by the `TD_MC_gasi` hook on 106020502; there is no `TD_MC_gasi2` Direction node, so the "2" hook is the tail. |
| `undomorphdarco` | 240000110 | `cancelItem(2210016)` | 2210016 is the Leafre→Temple of Time flight morph: `scripts/npc/2082003.js` applies it, `scripts/portal/undodraco.js` and `scripts/portal/templeenter.js` both cancel it. |
| `reundodraco` | 270000100 | `cancelItem(2210016)` | same morph, far end of the same flight (`scripts/portal/outTemple.js` applies it here). Distinct from the same-named **portal** script, which is the arrival trigger on 240000110 and only blocks itself. |
| `evanTogether` | 100030102, 914100021 | `unlockUI()` | no Direction node; both are walk-around maps whose content hangs off `scr*` trigger portals (`evanGarden0/1`) or a placed NPC (Afrien 1205000). Same as `evanAlone.js`. |

### Portal hooks — `scripts/portal/`

| hook | map / portal | what it does | evidence |
|---|---|---|---|
| `hontale_morph` | 240040700 `cs00`..`cs05` | `openNpc(2081005)` | **the Horntail entrance.** All six triggers sit at y≈700-712 on the cave floor, on top of gatekeeper NPC 2081005 at (235, 731); the map has no ordinary portal into the cave, so walking the floor is the entry. `openNpc` already returns early while a conversation is open, so the row cannot stack dialogues. |
| `investigate1` | 106020300 `investigate1` | `openNpc(1300014)` | NPC 1300014 has `hide=1`, so the trigger is its only reachable path. **Caveat: `scripts/npc/1300014.js` is currently a stub (`cm.dispose()`), so this restores the GMS call path but produces no dialogue yet.** |
| `tutorWorldmap` | 130030006 `scr00` | `showInfo("UI/tutorial.img/26")` + `blockPortal()` | last of the Cygnus tutorial hint triggers (`tutorHelper` on …000, `tutorMinimap` on …001). `UI.wz/tutorial.img` has both 25 and 26; the explorer tutorial on map 50000 uses exactly that pair via `infoMinimap.js` (25) and `infoWorldmap.js` (26). |
| `piramid_in00` | 926010000 `in00` | `openNpc(2103013)` | Duarte stands at (1013, 212) beside the gate at (935, 214) and `scripts/npc/2103013.js` already has the whole 926010000 branch. Today that answers "The PyramidPQ is currently unavailable" — still the right answer, and better than a silent dead end. |

## Refused, with reasons

### Would duplicate work the server already does

- **`rnj6_act` ×2 @926100203, `jnr6_act` ×2 @926110203** — GMS spawns the Yulete ambush from these
  triggers. Cosmic already does it: `scripts/event/MagatiaPQ_Z.js:305-311` schedules `yuleteAction`
  from `changedMap`, which spawns 9300143/9300144 and then opens the `rnj6_out` reactor on
  `monsterKilled`. Writing the trigger would **double-spawn**.
- **`hontale_boss2` @240060100** — the reference implementation spawns 8810025 off an event property
  `preheadCheck`, which Cosmic's `HorntailBattle.js` does not have; that script already spawns
  8810001 into 240060100 at `setup()` (line 112-114). Writing it would spawn an extra head.
- **`in_secretroom` @106021001** — the reference sets quest-info 2335. `scripts/portal/go_secretroom.js`
  is the only way into that map and it already `forceCompleteQuest(2335, 1300002)` on the way in.
- **`PinkBeen_before` @270050100** (`onFirstUserEnter`) — `scripts/event/PinkBeanBattle.js:107-115`
  already resets the map and spawns 8820000 in `setup()`. Anything here risks a second Pink Bean.

### Cannot be written without breaking something

- **`Depart_topFloor` @103040400** — this is a **case collision, not a missing file**.
  `scripts/portal/Depart_TopFloor.js` exists and does exactly the right thing (`openNpc(1052125)`,
  and NPC 1052125 is on that map). On Windows/NTFS the lookup already resolves; on a case-sensitive
  filesystem it would not. Creating `Depart_topFloor.js` here would **overwrite** the existing file.
  Fix is a rename, which is not additive — left for a deliberate call.

### No evidence — a documented gap beats an invented one

Every one of these has **no Direction node**, no implementation in any surveyed v90/v95 server tree,
and no derivable behaviour from map data. Writing them would mean inventing content.

| name | instances | what is known |
|---|---|---|
| `StageMsg_davy` | 5 (925100100-400) | Davy Jones PQ stage banner. `PiratePQ.js` handles the maps; the banner text is not in any source available here. |
| `StageMsg_romio` | 5 (926100001-300) | same, Magatia PQ Alcadno side. |
| `StageMsg_juliet` | 5 (926110001-300) | same, Zenumist side. |
| `party6weatherMsg` | 3 (930000100/200/300) | Ellin Forest PQ (`EllinPQ.js` owns the instance maps). "weather" is the on-screen banner packet; text unknown. |
| `dojang_QcheckSet` | 3 (925020002 Dojo Exit, 925020003 Dojo Rooftop, 925040000 Back Alley) | dojo record bookkeeping; Cosmic keeps dojo records in Java, so what is left for the hook is undetermined. |
| `TD_MC_keycheck` | 1 (106021400) | NPC 1300012 "Door to East Castle Tower" is hidden on the map; `scripts/portal/TD_MC_enterboss1.js` already gates the boss on quest 2330. |
| `findvioleta` | 1 (106021600 Wedding Hall) | Direction2.img has `open/violeta0`+`violeta1`, but those are children of the `open` node used by `TD_MC_Openning`, not a `findvioleta` node. |
| `mirrorCave` | 1 (140030000) | `scripts/portal/enterMCave.js` already carries the whole Aran 2nd/3rd job mirror logic. |
| `outCase` | 1 (105100100) | Bottom of the Temple; `BalrogBattle.js` + `balog_gate` reactor already present. |
| `Depart_BossEnter` / `Depart_Boss_F_Enter` | 1 each (103040430) | Kerning Square boss floor; no Kerning Square boss event exists in this server. |
| `dollCave00` / `dollCave02` | 1 each (910510200 / 910510202) | `Puppeteer.js` only covers 910510000. 910510200 has a single spawn portal and NPC Francis 1204001. |
| `start_itemTake` | 1 (920010000) | Tower of Goddess entrance. |
| `sealGarden` | 1 (920030001) | Sealed Garden. |
| `space_first` | 1 (922240200) | `RescueGaga.js` uses 922240200 as exit/recruit map; `Map.wz/Effect.img` has `event/space/start` but nothing ties it to this hook rather than to the entry map 922240000. |
| `find_james` | 1 (106021201) | NPC 1300008 "James" has **no** script in `scripts/npc/`, so `openNpc` would be a no-op. |
| `piramid_Chat00` | 1 (926010000, x=540) | far from Duarte at x=1013; firing his dialogue there would be wrong, and the `ad/piramid` effect nodes are advertising art, not a chat trigger. |
| `davy_next00` | 1 (251010404) | the portal sits at x=1490 while the map's VR bounds are −2000..−1154 — **outside the map**. Not walkable. |
| `nooutShip` | 1 (914000500) | a pure blocking portal ("no out"). A dead portal already blocks and already sends `enableActions` (`GenericPortal:157-159`), so a script adds nothing but invented message text. The real exit is Athena Pierce 1209007 → quest 21001's `end`, which warps to 914090010; that path works. |
| `tH_Out` / `PB_wich` | 1 each (980040000) | Witch Tower entrance, GM-event only (`scripts/npc/9000049.js` gates on `isGM`). Destination undetermined. |

### Blocked on content that does not exist yet

- **`enterSnowDragon` @914100010** and **`onSDI` @914100010** — Evan's Slumbering Dragon Island.
  The reference implementation warps into 914100020 on quest 22580 and otherwise starts an
  `EvanCaveAttack` event manager. This server has no such event, and 914100020's own mechanics
  (`stopIceWall` ×10 portals, `summonIceWall` map hook) are also absent, so opening the door would
  lead into a half-built room. **Follow-up: needs the ice-wall chain, not a portal file.**

## Follow-ups for owners of files outside this change

1. `scripts/npc/1300014.js` is a stub — `investigate1` now reaches it but it says nothing.
2. `scripts/portal/Depart_TopFloor.js` → `Depart_topFloor.js` rename (case), for case-sensitive hosts.
3. Evan's ice cave (914100020) chain, per above.
4. No script added here depends on a client-side scene warp, so **no `ChangeMapHandler` whitelist
   change is needed**.

## Verification

- `node --check` on all 13 files.
- `src/test/java/server/MapAndPortalScriptsRealLoad.java` — 5 tests. Loads each file through the same
  `GraalJSScriptEngine` construction `AbstractScriptManager` uses, invokes `start(ms)` / `enter(pi)`
  against Mockito mocks of `MapScriptMethods` / `PortalPlayerInteraction` and asserts the exact
  effect paths and NPC ids; asserts none of these names has a Direction node and none of the files
  contains `showIntro`; and re-derives the map sets straight out of `Map.wz`, so the test fails if
  either the data or this ticket's lists go stale.

      mvnw.cmd test -Dtest=MapAndPortalScriptsRealLoad
