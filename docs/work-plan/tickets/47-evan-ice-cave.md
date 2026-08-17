# 47 — Evan's ice cave (Slumbering Dragon Island), and the `inDragonEgg` warp

**Status:** investigated. One script written (`outSDI`), six refused with reasons, one live broken
warp adjudicated and left alone deliberately. Follow-up from ticket 43's "Blocked on content that
does not exist yet".

## The maps

Six maps, all present in this tree and all confirmed present in hash-verified v84 stock
(`WzMerge dump D:\games\wz-stage\v84-base\Map.wz`, `info/onUserEnter` matches this tree on all six).

| map | name (`String.wz/Map.img`) | `onUserEnter` | portal scripts | life | reactor |
|---|---|---|---|---|---|
| 914100000 | Slumbering Dragon Island : Temporary Harbor (`town=1`) | — | — | `n 1013207` Olaf | — |
| 914100010 | Slumbering Dragon Island : Snowy Forest | **`onSDI`** | **`enterSnowDragon`** ×1 | *empty* | — |
| 914100020 | Slumbering Dragon Island : Cave of Silence | — | **`stopIceWall`** ×10 | *empty* | — |
| 914100021 | Slumbering Dragon Island : Cave of Silence | `evanTogether` ✔ written (43) | — | `n 1205000` Afrien | — |
| 914100022 | Slumbering Dragon Island : Cave of Silence | **`summonIceWall`** | **`stopIceWall2`** ×10, **`outSDI`** ×1 | *empty* | `1409000` |
| 914100023 | Slumbering Dragon Island : Cave of Silence | **`blackSDI`** | — | `m 9300392` ×10 (Black Wing Henchman) | — |

The four Cave of Silence rooms are **siblings hanging off 914100010**, not a chain: every one has
`returnMap = forcedReturn = 914100010`, and every one puts a portal named `out00` at exactly
`(-548, 143)`. Three of them (…020, …021, …023) make that portal a plain `pt=2` with
`tm=914100010 / tn="in00"`. Only 914100022 makes it `pt=7` with `script=outSDI` and `tm=999999999`.

## Reachability — measured, and it changes what is worth building

Traced by hand over `Map.wz` portal `tm`, `scripts/`, `wz/Quest.wz` and `src/main/java`:

- **No quest routes here.** `grep` for `9141000` over the whole of `wz/Quest.wz` returns nothing.
- **Quest 22580 does not exist as content.** It, and every id from 22570 to 22596, appears in
  `QuestInfo.img` with the placeholder name *"Spot On : An Interesting Chore"* and has **no entry at
  all** in `Check.img`, `Act.img` or `Say.img`. v84 shipped these map images ahead of the storyline
  that uses them. Any implementation keyed on quest 22580 would be keyed on nothing.
- **The ferry is not boardable.** `200090080` ("To the Slumbering Dragon Island") and `200090090`
  ("To Lith Harbor") have no inbound portal from any map, no script warps to either, and
  `scripts/npc/1013207.js` (Olaf, the NPC placed on both ends) **does not exist**. Ticket 37 already
  proved the six `move_*` ride scripts inert.
- **The only live door in is `900030000`** ("Behind the Stronghold"), whose `out00` runs
  `scripts/portal/outAfrienMemory.js` → 914100021 → `out00` → 914100010. But `900030000` itself has
  no inbound portal and nothing warps to it either.

So the whole Slumbering Dragon Island cluster is **disconnected from the playable map graph** in this
tree. That is not a reason to invent the encounter; it is the reason not to.

## `Effect.wz` — none of these is a cutscene

Re-derived rather than trusted: `Direction.img`…`Direction4.img` hold **33 top-level nodes, 25
distinct** (`effect` and `sound` repeat in all five files). Full list: effect, sound, cygnus,
cygnusJobTutorial, aranTutorial, aranDirection, mushCatle, gasi, open, piramid, metro, goLith,
goAdventure, swordman, magician, archer, rogue, pirate, ghostShip, meetWithDragon, crash,
getDragonEgg, incubation, PromiseDragon, promotion.

**Not one of `onSDI`, `enterSnowDragon`, `stopIceWall`, `stopIceWall2`, `summonIceWall`, `blackSDI`,
`outSDI` is among them.** A scene path the client cannot resolve crashes the client — that is how
every female Evan was crashing on `PromiseDragon/Scene1` — so none of these files may ever play one.
`MapAndPortalScriptsRealLoad` enforces that by name on every file added here.

## Portal types, read off the data

`pt=7` in this data set is always a scripted **warp** portal (`inDragonEgg`, `evanFarmCT`,
`outAfrienMemory`, `enterSnowDragon`, `outSDI` — all `pt=7`, all `tm=999999999`). `pt=9` is a
**trigger** portal that fires on touch and does not warp (`scr*`; ticket 43's `investigate1`,
`tutorWorldmap`, `hontale_morph` are all `pt=9` and all `openNpc`/`showInfo`). So the ten `scr`
portals per ice-wall room are not warps, and `outSDI`/`enterSnowDragon` are.

## Written

### `scripts/portal/outSDI.js` — 914100022 `out00`

`pi.playPortalSound(); pi.warp(914100010, "in00");`

The only one of the seven whose behaviour the map data **determines**. Same portal name, same pixel
`(-548, 143)`, and the same declared `returnMap`/`forcedReturn` as the three sibling rooms that spell
the destination out as `tm`/`tn`. Without it 914100022 is a one-way trip: its only other portals are
the spawn point and a `pt=1` `st00`.

What it does **not** do: whatever gate GMS puts here. This is the exit of the ice-wall encounter that
`summonIceWall`, `stopIceWall2` ×10 and reactor 1409000 drive, and none of those exist. The gate is
not invented, and its absence cannot hurt — an unconditional exit only ever lets a player *leave* a
room. Marked `ponytail:` in the file.

## Refused — six names, and why each

### `enterSnowDragon` — 914100010 `in00` (`pt=7`, `tm=999999999`), a dead portal

The shared front door of the cave. There are **four** rooms behind it (…020 ice wall, …021 Afrien,
…022 ice wall + reactor, …023 ten Black Wing Henchmen), all four reachable only through this one
portal, and the selector is quest state — quest state that does not exist in v84 (see above). Ticket
43 recorded that the reference implementation keys on quest 22580 and otherwise starts an
`EvanCaveAttack` event manager; this server has neither. **Choosing one of four destinations with no
evidence is inventing the storyline.** Left dead.

### `onSDI` — 914100010 `onUserEnter`

Not a cutscene (no Direction node). The `unlockUI()` used by `evanAlone` / `evanTogether` /
`aranTutorAlone` is justified there because those maps are entered *out of* a cutscene; 914100010 is
only ever entered through ordinary walk portals from 914100000 or the cave rooms, so `unlockUI` would
be a guess too. `onSDI` sits in the same name family as `blackSDI` / `outSDI` / `summonIceWall` — it
is part of the encounter, not a greeting. A missing **map** script is merely silent
(`AbstractScriptManager` returns null without logging), so this costs nothing today.

### `summonIceWall` — 914100022 `onUserEnter`

The wall itself. `Map.wz` carries **no ice-wall art** on either room (`obj` on …020 and …022 is only
`acc12/dragon/{cave,dragon}` background), no `ladderRope` (the node exists and is empty), and no life.
So the wall is a client-side or spawned object whose id, shape and behaviour are nowhere in this
tree. Nothing to derive.

### `stopIceWall` ×10 (914100020) and `stopIceWall2` ×10 (914100022)

Both are a **2 × 5 grid** of `pt=9` triggers, 200 ms delay each: …020 at x = 56 / 153, y = 99, 2,
−95, −193, −290; …022 at x = 58 / 156 over the same five rows. That is the footprint of the wall the
`summonIceWall` hook raises. Without the wall the triggers have nothing to stop. Twenty portals, one
unbuilt mechanic.

### `blackSDI` — 914100023 `onUserEnter`

The room already ships its own fight: ten `m 9300392` "Black Wing Henchman" (Lv.70) at `mobTime=-1`,
i.e. spawn-once, placed in `life`, which `MapFactory` spawns with no script at all. What the hook adds
beyond that — a banner, a lock, a boss phase — is not in the data.

### And the reactor, for the record

`Reactor.wz/1409000.img`: `info/info` = 얼음 벽 무너뜨리기 ("break down the ice wall"), two states,
`event/0` `type=100` → waits for **item 4032473** ("Gruesome Bone") ×1 dropped inside
`lt(-100,-53)…rb(98,49)`, then `action = "SDIScript0"`. **`scripts/reactor/SDIScript0.js` does not
exist**, so the reactor is inert as well. Listed here because it is the one piece of the encounter
whose trigger *is* fully specified by data — but its action script is not, and it is on an
unreachable map.

## `scripts/portal/inDragonEgg.js` → 100030301: verdict

**The script is right and must not be changed. The missing map is this backport's own deliberate
refusal, and the fix belongs in `wz/Map.wz`.**

- The file is **upstream Cosmic**, not written during this cutover (`git log --follow` bottoms out at
  the `source` commit).
- `Map.wz` corroborates the else-branch exactly: `100030300` ("Farm Center") `in00` is a `pt=7`
  script portal, and in v84 stock `100030301` ("Forest Hall") has precisely two portals — `sp` and
  `out00`, `tm=100030300 / tn=in00`. Forest Hall is a leaf whose only neighbour is that portal. So
  "in00 leads to Forest Hall unless you are on Evan quest 22005, in which case it leads to 900020100
  (Lush Forest)" is what the data says.
- `100030301` was refused by ticket 13 on safety, not effort: its `life` places fixed NPCs on ids
  **9901910–9901919**, the band `PlayerNPC.java:66` allocates at runtime. Pinned by
  `V84EvanWorldNodeTest.forestHallIsDeliberatelyNotMerged` and `V84ContentMergeNodeTest`.

**Measured consequence today** (traced through the code, not assumed):
`pi.warp(100030301, 0)` → `Character.changeMap(int,int)` → `MapManager.getMap` →
`MapFactory.loadMapFromWz:137-138`, where `mapSource.getData(...)` returns null and
`mapData.getChildByPath("info")` throws NPE. `PortalScriptManager.executePortalScript` catches it,
logs `Portal script error in: inDragonEgg`, and returns false; `GenericPortal.enterPortal` then sends
`enableActions`. **So: an inert portal plus one WARN per touch. No crash, no hang, no cache
poisoning** (`maps.put` is never reached). It is live — `100030300` is walkable from Henesys via
100030000 → 100030100/101/102 → 100030200 → 100030300 — and it affects every player who has not
started quest 22005, which is every non-Evan.

**Not fixed here, on purpose.** Every repair available inside `scripts/portal/` either invents a
destination or deletes the only diagnostic. The real fix is to hand-author
`Map.wz/Map/Map1/100030301.img` **without** the ten `9901910`–`9901919` `life` slots — a hand-authored
node, not a merge, and in a file this change does not own. `MapAndPortalScriptsRealLoad
.inDragonEggStillWarpsToTheOneMapThisTreeRefusedToMerge` now pins both halves together so the day the
map lands, the warp is re-verified rather than assumed.

## Verification

    mvnw.cmd -o test -Dtest=MapAndPortalScriptsRealLoad      # 6 tests (was 5)

`outSDI` goes through every guard the file already applies to the 13 hooks of ticket 43: it loads
under the real `GraalJSScriptEngine`, `enter(pi)` is invoked for real against a Mockito
`PortalPlayerInteraction` and asserted to call exactly `playPortalSound()` + `warp(914100010, "in00")`
and nothing else (`verifyNoMoreInteractions`), and it is asserted to have no `Direction` node and to
contain no scene call. That last guard fired for real during development — on a comment — which is
the proof it is not decorative.
