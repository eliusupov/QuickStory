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

### ~~This row overturns ticket 37's standing refusal~~ - WITHDRAWN, see "Delivered - R46"

**Everything from here to the end of R46 is superseded.** It was written before the carve was read.
The ferry is real, but it is an NPC ride; ticket 37's refusal of `move_RitSDI` / `move_SDIRit`
stands and is now *more* firmly correct, not less. The rest of this section is kept as the record of
what was believed.


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
- [x] ~~`scripts/portal/move_RitSDI.js` and `scripts/portal/move_SDIRit.js` exist and load~~
      **WITHDRAWN - see "Delivered - R46".** These two are the wrong mechanism and ticket 37's
      refusal of them is correct and now live. The ferry is boarded from an NPC, not from
      `out00`..`out05`.
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

## Delivered - R03

`scripts/portal/enterSDI.js` warps to **914100000** slot 0 (the island's only `town=1` map, the
landing `200090090/portal/1..8` declare, and the map whose `in00` reaches 914100010 where `onSDI.js`
writes 22599="1"). Slot 0 by index, not by the ferry's dangling `tn="st00"`.

`scripts/portal/enterSnowDragon.js` routes 22580 -> 914100020, 22588 -> 914100022,
22589 -> 914100023, 22590/22591 -> 914100021. Every room claim was re-verified against the map image
and `Check.img` before writing; all four hold as the ticket states.

**OWNER Q1 answered provisionally, one line to change.** The fallback is **914100020** because it is
the only inert room: `life` empty, `reactor` empty, `info/onUserEnter=""`, and its ten `scr0*`
triggers run `stopIceWall.js`, which no-ops unless 22580 is STARTED. 914100021 places Afrien,
914100022 declares the unwritten `summonIceWall`, 914100023 places ten mobs. Nothing fires for a
passer-by in 914100020. If the owner prefers another room it is the single `warp` in the else-branch.

No WZ edit was needed: both portal nodes are already merged and byte-identical to pristine.
`MapAndPortalScriptsRealLoad` covers both names (real-engine load, every arm invoked against a
Mockito `PortalPlayerInteraction` with `verifyNoMoreInteractions`, and the no-`Direction`-node
guard). 11 tests, 0 failures.

**R46 not implemented** - out of scope for this pass. Its two blocking facts were re-verified and
both hold: `wz/Npc.wz/1002101.img.xml` carries no `info/script` node at all (only `speak/0..1`), and
`grep -rl contimoveSDIRit` over `wz/`, `scripts/` and `src/` returns nothing, while
`wz/Npc.wz/1013207.img.xml:6` does carry `contimoveRitSDI`. R46 therefore still needs an add-list
merge plus the ticket-37 reversal, and remains open.

## Delivered - R46: the ferry is in v84, and it is an NPC ride, not two portal scripts

**Adjudication: the ferry IS in v84's data, decisively.** Four facts, all from the pristine carve
`D:\games\MapleStory\Server\porting-resources\wz-data\v84\`, read with `WzPeek`:

1. `Npc.wz/1002101.img/info/script/0/script` = **`contimoveSDIRit`** in pristine. Our tree carried
   only `speak/0..1`. The leaf is now merged.
2. `Etc.wz/ScriptInfo.img/contimoveSDIRit` = **"Ask about boarding the ship."** - and it is the
   **only** `contimove*` entry in the whole of `ScriptInfo.img`. v84 wrote a boarding tooltip for
   this route and for no other.
3. Both ends ship complete. `Map2/200090080` and `Map2/200090090` each carry `sp`, `hd00`..`hd07`
   and `out00`..`out05`, and each places `life/0 n 1013207`. `WzPeek scan Map.wz id` over all 4,848
   pristine images: **1002101 appears in exactly one map, `Map1/104000000` (Lith Harbour) `life/11`;
   1013207 in exactly three, `914100000`, `200090080`, `200090090`.** A boarding NPC at each end and
   a deckhand on each ship.
4. Add-list rows exist for both unmerged leaves: `docs/wz-baseline/add-list/Npc.txt:5`
   (`Npc.wz/1002101.img/info/script`) and `docs/wz-baseline/add-list/Etc.txt:10633`
   (`Etc.wz/ScriptInfo.img/contimoveSDIRit`).

### The claim that blocked this for two passes is false

Both this ticket and two shipped script comments asserted that an `info/script` leaf makes an NPC
**client-scripted and therefore unreachable by a server conversation**, so `contimoveSDIRit` had to
be some client mechanism we could not drive. That is wrong, and this tree already disproves it:

| NPC | `info/script` leaf (pristine **and** `wz/Npc.wz` today) | server script | status |
|---|---|---|---|
| 1200004 Puro, Lith Harbour | `contimoveRitRie` | `scripts/npc/1200004.js` | **works in production** |
| 1100008, Orbis Station | `contimoveOrbEre` | `scripts/npc/1100008.js` | **works in production** |
| 1002101 Olaf, Lith Harbour | `contimoveSDIRit` | added here | - |

1200004 stands on the same map as Olaf, 104000000. So the `divi == 1040000` worry above is answered
too: **no `ChangeMapHandler` branch was needed.** That branch list only whitelists client-requested
cutscene transfers; a ferry boards through a server NPC conversation and `cm.warp`, which never
reaches it. The proof is that the Rien whale on that exact map works today with no branch.

### Ticket 37 is NOT overturned - it governs, and it is right

The "R46 overturns ticket 37" section above is **withdrawn**. Ticket 37 refused
`move_RitSDI` / `move_SDIRit` because implementing `out00`..`out05` is either a ride-skip exploit or
a duplicate of the `hd**` portals. Its own escape clause - *"if a scheduled ride is later added to
these two maps, ticket 37's refusal becomes live again"* - has now fired: **this row added the ride.**
And the parity argument is decisive on its own: **every working ferry in this server has its
`out00`..`out05` scripts missing.** `move_RitRie`, `move_RieRit`, `move_OrbEre`, `move_EreOrb`,
`move_EliEre`, `move_EreEli` are all absent and all six rides work. Writing the SDI pair would make
this the only ferry in the game with a door that skips its own ride.
`SlumberingDragonFerryScriptTest.theOutPortalScriptsStayRefused` pins all three names as absent.

### What shipped

* `wz/Npc.wz/1002101.img.xml` - `info/script/0/script` = `contimoveSDIRit`, merged from the carve
  (add-list row `Npc.txt:5`). `Etc.wz/ScriptInfo.img/contimoveSDIRit` was **not** merged: it is a
  client tooltip, the server never reads `Etc.wz/ScriptInfo.img`, and `Etc.wz` is another row's lane.
* `scripts/npc/1002101.js` - Olaf at Lith Harbour, warps to **200090080**.
* `scripts/npc/1013207.js` - Olaf at 914100000, warps to **200090090**; on the two ride maps he
  refuses to board and just talks. That guard is the ticket-37 exploit closed by construction.
* `constants/id/MapId.java` - `FROM_LITH_TO_SDI` 200090080, `FROM_SDI_TO_LITH` 200090090,
  `SDI_TEMPORARY_HARBOR` 914100000.
* `server/maps/MapleMap.java` `addPlayer` - two branches copied from the `FROM_LITH_TO_RIEN` pair.

Every derived value, and where it comes from:

| value | derived from |
|---|---|
| outbound ride map 200090080 | its `hd00..hd07` warp to 104000000 and its `returnMap`/`forcedReturn` are 104000000, so Lith is the ORIGIN - same shape as `200090020`; its `out00..out05` name `move_RitSDI` |
| return ride map 200090090 | `hd**` -> 914100000, `out**` name `move_SDIRit` |
| island landing 914100000 slot **0** | 914100000 has two portals, `sp`(0) and `in00`(1). The `tn="st00"` that `200090090/hd**` name does not exist there. Same slot `enterSDI.js` already uses |
| Lith landing 104000000 slot **3** | verbatim from the shipped `FROM_RIEN_TO_LITH` arrival - the other ship docking at Lith |
| ride time **1 minute** | v84 states no duration for this route anywhere. Copied from `FROM_LITH_TO_RIEN`, the only other ship off the same dock |
| fare **none** | 1200003/1200004 charge 800 mesos, but v84 prices this route nowhere - `ScriptInfo` is one sentence with no number. Inventing a fare is what the evidence rules forbid; one `gainMeso` line if the owner wants one |

### Which NPC drives which direction

**1002101** (Lith Harbour) drives **outbound**, Lith -> island. **1013207** (island harbour, and the
deckhand on both ships) drives the **return**. Note the `contimove` suffixes are crossed relative to
the `move_` portal names - the Lith NPC carries `SDIRit`, the island NPC carries `RitSDI` - so the
map the click comes from decides the direction, never the name.

### The one step only a client can confirm

Whether clicking Olaf opens the conversation at all cannot be checked without launching a client,
which no agent may do. The risk is as low as it can be made without one: 1200004 and 1100008 carry
the identical `info/script` node, stand on the same or an equivalent map, and their conversations
open on the owner's live server today. If Olaf turns out to be silent, so would Puro be.

Also corrected: the "client-scripted, therefore unreachable" claim written into
`scripts/map/onUserEnter/onSDI.js` and `scripts/portal/outAfrienMemory.js`. Both comments now state
the fact without the false inference. Neither script's behaviour changed.

No maven was run - ticket 68 holds `target/`.
