# 55 - Slumbering Dragon Island: the door that opens it, and the ferry that also should

**Class:** v84 parity
**Work rows:** R03, R46 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately. One owner decision is embedded (OWNER Q1), and it gates
only the `enterSnowDragon` fallback room, not the rest of the ticket.

**R03 and R46 are not equally ready.** R03 is a two-script job whose every branch is decided by data
already in this tree. R46 rests on a leaf that is **not in our tree at all** and overturns a standing
refusal in ticket 37. Do R03 first; read "R46 - the ferry" in full before starting it.

The island is unreachable. Five Evan quests - **22580, 22588, 22589, 22590, 22591** - are behind it,
and nothing today can walk in. Two routes exist in v84's own data and both are shut by a missing
script, not by missing content: the Frog House door needs `enterSDI`, and Olaf's ferry needs
`move_RitSDI` outbound and `move_SDIRit` returning. Everything else on the island is already here -
`scripts/map/onUserEnter/onSDI.js`, `scripts/map/onUserEnter/blackSDI.js`,
`scripts/map/onUserEnter/evanTogether.js`, `scripts/portal/stopIceWall.js`,
`scripts/portal/outSDI.js`, `scripts/portal/outAfrienMemory.js` and `scripts/reactor/1409000.js` are
all shipped. (Three of those seven are map hooks, not portal scripts - the paths above are exact.)

**Nothing can regress.** The island is disconnected from the playable graph today, so every change
in this ticket is strictly additive to reachability.

The ice-wall mechanic itself - `summonIceWall` and `stopIceWall2` - is **not** in this ticket and
must stay refused. That is work row R47, ticket 70.

## R03 - `enterSDI` and `enterSnowDragon`

Absent scripts, both declared by map nodes **that are already merged into our tree**:

| script | declared by | node |
|---|---|---|
| `scripts/portal/enterSDI.js` | `Map9/922030000.img` **portal/2** | `pn=tel00 pt=8 x=-205 y=32 tm=999999999` |
| `scripts/portal/enterSnowDragon.js` | `Map9/914100010.img` **portal/2** | `pn=in00 pt=7 x=2545 y=84 tm=999999999` |

Both nodes are byte-identical between pristine v84 and `wz/Map.wz` today, `script` leaf included.
**No WZ edit is needed for R03 - only the two script files.**

**On `pt=8`.** Ticket 54 and ticket 47 only establish `pt=7` (scripted warp) and `pt=9` (touch
trigger). `enterSDI` is `pt=8`, which is neither, and that is harmless:
`PortalFactory.java:37-42` makes every type except `MAP_PORTAL(2)` a `GenericPortal`, and
`GenericPortal.java:130-142` runs the script for any type that names one. Do not treat `pt=8` as a
third mechanism needing new code.

**`enterSDI` alone opens the island.** The route in front of it is already live:
`220000300/portal/4` = `pn=scr00 pt=7 tm=999999999 script=enterBlackFrog`, and `enterBlackFrog.js`
lands at `922030000` (or 922030001 if 22596 is started), so `922030000/tel00` is the single remaining
gate. Recommended destination **914100000** - it is the island's only `town=1` map and the landing the
ferry itself declares (`200090090/portal/1..8` are `hd00..hd07`, all `pt=3 tm=914100000 tn=st00`).

**Caveat on `st00`: that portal name does not exist.** 914100000 has exactly two portals, in our tree
and in pristine: `0 sp` and `1 in00` (`pt=2` -> 914100010/west00). `GenericPortal.java:148-150` falls
back to `to.getPortal(0)` when the named target is missing, so an arrival lands on `sp`. That is
survivable, but it is a dangling reference, not a clean landing - do not cite `tn=st00` as evidence
the ferry destination is wired.

`enterSnowDragon` is the shared front door of four sibling rooms, and each room is claimed by exactly
one quest from its own contents plus that quest's `Check.img` gate. Every cell below was verified
against the map images:

| room | contents | claimed by |
|---|---|---|
| 914100020 | ten portals `scr00`..`scr09`, `pt=9 script=stopIceWall` | **22580** (`Check.img/22580/1` infoNumber 22599, infoex value "2") |
| 914100021 | life `n 1205000` x1 (Afrien) + `info/onUserEnter=evanTogether` | **22590** (`Check.img/22590/1/npc` = 1205000) and **22591** |
| 914100022 | `reactor/0 id=1409000` + `info/onUserEnter=summonIceWall` | **22588** |
| 914100023 | life `m 9300392` x10 + `info/onUserEnter=blackSDI` | **22589** (`Check.img/22589/1` infoNumber 22604) |

22599 and 22604 are info-records, not quests, so they are correctly absent from `QuestInfo.img`. The
objectives are already writable by shipped code: `stopIceWall.js` writes 22599="2",
`reactor/1409000.js` writes 22605=1, `blackSDI.js` writes 22604=1, `outSDI.js` writes 22600=1.

**OWNER Q1 covers the fallback room only** - which room a character on none of those quests lands
in. The four gated branches above are decided by data and need no decision.

The negatives were re-proved over all 4,848 pristine v84 map images (`WzPeek scan Map.wz tm <id>`,
which prints "scanned 4848 images"): **zero** `tm` values point at 914100020/21/22/23, and **zero**
point at 200090080, 200090090 or 922030000. So no portal anywhere supplies these destinations; a
script is the only mechanism v84 gives.

## R46 - the ferry, and the leaf that is not in our tree

Absent scripts:

* `scripts/portal/move_RitSDI.js` - outbound, declared by `Map2/200090080.img` **portal/9..14**
* `scripts/portal/move_SDIRit.js` - return, declared by `Map2/200090090.img` **portal/9..14**

Both are six portals each, `pn=out00`..`out05`, `pt=9`, `tm=999999999`, `delay=200`. On 200090090 all
six also carry `onlyOnce=0`; **on 200090080 only `portal/9` does** - slots 10-14 carry neither
`onlyOnce` nor `hideTooltip`, in our tree and in pristine alike. `PortalFactory` reads none of those
leaves, so this is cosmetic, but the earlier "both are six portals each, `onlyOnce=0`" claim was
false and is corrected here.

**The old tracker's list of eight missing names records `move_SDIRit` only.** `V84-OPEN-ITEMS.md:24`
(commit `a6049b733`) reads: *"Slumbering Dragon Island needs 8 missing scripts: `enterSDI`,
`move_SDIRit`, `enterSnowDragon`, `onSDI`, `stopIceWall`, `stopIceWall2`, `summonIceWall`,
`blackSDI`."* Of those eight, five are still missing; `move_RitSDI` is a **sixth** missing name the
tracker never recorded.

`pt=9` is a trigger, not a warp. This codebase does boat arrival as a scheduled `warpEveryone`
(`scripts/event/Boats.js:69-73`, `function arrived()`, four `warpEveryone` calls - verified, no line
drift), so the ride duration is not stated anywhere in v84 and must come from the existing boat
implementation rather than being made up.

### The blocker: `contimoveSDIRit` is not in our tree

**Previously this ticket asserted that Olaf `1002101` carries `Npc.wz` `info/script` =
`"contimoveSDIRit"`. That is false for this repo.** `wz/Npc.wz/1002101.img.xml` lines 3-8: `info`
contains only `speak/0=n0` and `speak/1=n1`. There is no `script` node.
`grep -rl contimoveSDIRit wz/` returns nothing across the entire tree.

The leaf exists only in the pristine carve, and at a deeper path than the old claim gave:
**`1002101.img/info/script/0/script` = `contimoveSDIRit`**. So the leaf the outbound ferry depends on
is an **unmerged add-list row**, and merging it is part of R46 - it is not already present.

### The other end of the ferry: NPC 1013207

The island-side boat NPC is **1013207**, also named "Olaf" in `String.wz/Npc.img`. It is placed at
`wz/Map.wz/Map/Map2/200090080.img.xml:254`, `wz/Map.wz/Map/Map2/200090090.img.xml:254` and
`wz/Map.wz/Map/Map9/914100000.img.xml:299`, and it carries `info/script/0/script =
contimoveRitSDI` **in pristine and in our tree** (`wz/Npc.wz/1013207.img.xml:6`). Ticket 47 names it
as the NPC placed on both ends.

Note the direction names are crossed relative to intuition: the Lith-side NPC carries `SDIRit`, the
island-side NPC carries `RitSDI`. Do not assume 1002101 is the outbound trigger without checking
which map the click comes from.

Neither `scripts/npc/1002101.js` nor `scripts/npc/1013207.js` exists.

### This row overturns ticket 37's standing refusal - say so, do not do it silently

`docs/work-plan/tickets/37-ereve-rien-ferry-portals.md:87-88` lists `move_RitSDI` and `move_SDIRit`
in its refused table, and `:106-114` ("Why no fix was written") refuses implementing `out00..out05`
as either an exploit or a duplicate of the scheduled ride. **R46 reverses that.** The argument for
reversing it, which must be recorded in the Delivered section:

* Ticket 37's refusal rests on a ride-skip exploit (`:110` - "free instant travel, skipping the
  1/2/8-minute ride"). That reasoning holds for Orbis/Ereve, where a working scheduled ride already
  exists and an extra door would bypass it.
* 200090080 and 200090090 have **no working ride today** - the island is disconnected from the
  playable graph entirely. There is nothing to skip. The exploit ticket 37 refuses cannot exist here
  until a ride exists.

If a scheduled ride is later added to these two maps, ticket 37's refusal becomes live again and
these scripts must be re-examined.

### The client question

`ChangeMapHandler.java:113-167` computes `final int divi = chr.getMapId() / 100;` and branches on
`divi == 0`, `20100`, `9130401`, `9140900`, `9000900||9000901`, `divi/10 == 1020`, and `980040..980045`.
There is **no branch for `divi == 1040000`**, and Olaf 1002101 stands on map 104000000, which gives
exactly that divi. A portal-less self-transfer would be silently dropped - the exact failure ticket
41 fixed for the Evan cutscenes.

So: write the two scripts against the `Boats.js` shape, merge the `1002101.img/info/script/0/script`
leaf, add the missing `ChangeMapHandler` branch if the transfer is dropped, and record that
confirming a click on Olaf produces a transfer request at all **requires a client launch, which no
agent may perform**.

## Precedent

* Branch shape: `scripts/portal/enterBlackFrog.js` (commit `dda2d5f5a`).
* Multi-value quest ladder: `scripts/portal/evanDollGR.js`.
* Boat ride: `scripts/event/Boats.js:69-73`.
* The room-to-quest claims above come from `Check.img` and each room's own `life`/`reactor`/portal
  contents, not from a wiki.
* Ticket 47 is the standing record for this cluster. Its refusal list
  (`docs/work-plan/tickets/47-evan-ice-cave.md:67-72`) names **seven**: `enterSDI`, `enterSnowDragon`,
  `onSDI`, `summonIceWall`, `stopIceWall`, `stopIceWall2`, `blackSDI`. Three of those - `onSDI`,
  `stopIceWall`, `blackSDI` - have since shipped. This ticket retires two more (`enterSDI`,
  `enterSnowDragon`) on the room-claim evidence, which leaves **two** still refused
  (`summonIceWall`, `stopIceWall2`), not four. Ticket 47's own header still says "six refused" and is
  stale on the same point.
* The pristine carve is at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\` - a **sibling of this repo**, not a
  subdirectory (`docs/work-plan/SOURCES.md:14`). Read it with
  `docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly two
  subcommands: **`dump`** and **`scan`**.

## Acceptance criteria

- [ ] `scripts/portal/enterSDI.js` exists, loads under the real `GraalJSScriptEngine`, and warps to
      **914100000**.
- [ ] From El Nath 220000300, a character can walk `scr00` -> 922030000 -> `tel00` -> 914100000 with
      no script error logged.
- [ ] `scripts/portal/enterSnowDragon.js` routes 22580 -> **914100020**, 22588 -> **914100022**,
      22589 -> **914100023**, 22590/22591 -> **914100021**, and the owner's chosen fallback
      otherwise. Each branch is asserted by name in a test.
- [ ] `wz/Npc.wz/1002101.img.xml` carries `info/script/0/script` = `contimoveSDIRit`, merged from the
      carve. This leaf is absent today and R46 does not work without it.
- [ ] `scripts/portal/move_RitSDI.js` and `scripts/portal/move_SDIRit.js` exist and load; each is
      driven by all six `out00`..`out05` portals on its map without duplicating the ride.
- [ ] `MapAndPortalScriptsRealLoad` covers all four new scripts: real engine load, `enter(pi)`
      invoked against a Mockito `PortalPlayerInteraction`, exact interactions asserted with
      `verifyNoMoreInteractions`, and **no `Effect.wz` `Direction*` scene named by any of them**.
- [ ] Quests 22580, 22588, 22589, 22590 and 22591 are each walkable end to end on a level-70 Evan -
      accept, reach the room, satisfy the objective, hand in.
- [ ] The ferry half states, in this ticket's Delivered section: whether the `divi == 1040000` branch
      was needed; which NPC (1002101 or 1013207) drives which direction; the argument overturning
      ticket 37's refusal; and the one step that only a client can confirm.

Tests run as `-Dtest=MapAndPortalScriptsRealLoad`. **Do not run maven while sibling agents are
active.**

Caveat on the walkability criteria: `wz/Quest.wz/Say.img.xml` has **zero** entries in the 22550-22600
range (pristine `Say.img/22580` exists; ours does not carry it), and none of `scripts/npc/1013203.js`
(Hiver), `scripts/npc/1013000.js` (Mir), `scripts/npc/1205000.js` (Afrien) or
`scripts/quest/{22580,22583,22584,22588,22589,22590}.js` exists. Accept and complete still work
through the quest-window path (`QuestActionHandler.java:129`, cases 1 and 2), which needs neither a
`Say` entry nor an NPC script, so "walkable end to end" is reachable - but clicking the NPC produces
nothing. Out of scope here; do not write NPC scripts to paper over it.

## Do not

- Do not write `summonIceWall` or `stopIceWall2`. Mob 9300391 is placed in none of v84's 4,848 map
  images and produced by no revive chain in any of its 1,605 mob images; writing either means
  inventing a mob count and coordinates. See ticket 70.
- Do not choose `enterSnowDragon`'s fallback room without the owner. The four gated branches are
  data; the fallback is not.
- Do not assume `contimoveSDIRit` is already merged. It is not - see the R46 blocker above.
- Do not play an `Effect.wz` scene from any of these four scripts. None of `onSDI`,
  `enterSnowDragon`, `stopIceWall`, `summonIceWall`, `blackSDI`, `outSDI` appears among the 25
  distinct `Direction*.img` nodes (33 top-level across 5 files, 25 distinct), and a scene path the
  client cannot resolve crashes the client.
- Do not add portals to 200090080 or 200090090 to "fix" the ferry. That part of ticket 37's refusal
  stands: extra door names on a ferry map create a ride-skip exploit. Only the two *scripts* are
  being un-refused, and only on the argument recorded above.
- Do not launch a client to check Olaf. Record the question and hand it to the owner.
