# 55 - Slumbering Dragon Island: the door that opens it, and the ferry that also should

**Class:** v84 parity
**Work rows:** R03, R46 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately. One owner decision is embedded (OWNER Q1), and it gates
only the `enterSnowDragon` fallback room, not the rest of the ticket.

The island is unreachable. Five Evan quests - **22580, 22588, 22589, 22590, 22591** - are behind it,
and nothing today can walk in. Two routes exist in v84's own data and both are shut by a missing
script, not by missing content: the Frog House door needs `enterSDI`, and Olaf's ferry needs
`move_RitSDI` outbound and `move_SDIRit` returning. Everything else on the island is already here -
`onSDI.js`, `blackSDI.js`, `stopIceWall.js`, `outSDI.js`, `outAfrienMemory.js`, `evanTogether.js` and
`reactor/1409000.js` are all shipped.

**Nothing can regress.** The island is disconnected from the playable graph today, so every change
in this ticket is strictly additive to reachability.

The ice-wall mechanic itself - `summonIceWall` and `stopIceWall2` - is **not** in this ticket and
must stay refused. That is work row R47, ticket 70.

## R03 - `enterSDI` and `enterSnowDragon`

Absent scripts, both declared by pristine v84 map nodes:

| script | declared by | node |
|---|---|---|
| `scripts/portal/enterSDI.js` | `Map9/922030000.img` **portal/2** | `pn=tel00 pt=8 x=-205 y=32 tm=999999999` |
| `scripts/portal/enterSnowDragon.js` | `Map9/914100010.img` **portal/2** | `pn=in00 pt=7 x=2545 y=84 tm=999999999` |

**`enterSDI` alone opens the island.** The route in front of it is already live:
`220000300/scr00` -> `enterBlackFrog.js` -> `922030000`, so `922030000/tel00` is the single remaining
gate. Recommended destination **914100000** - it is the island's only town and the landing the ferry
itself declares (`200090090/portal/1..8` are all `tm=914100000 tn=st00`).

`enterSnowDragon` is the shared front door of four sibling rooms, and each room is claimed by exactly
one quest from its own contents plus that quest's `Check.img` gate:

| room | contents | claimed by |
|---|---|---|
| 914100020 | ten `stopIceWall` triggers | **22580** (infoNumber 22599 = "2") |
| 914100021 | Afrien `1205000` + `evanTogether` | **22590** (`Check.img/22590/1/npc` = 1205000) and **22591** |
| 914100022 | reactor `1409000` + `summonIceWall` | **22588** |
| 914100023 | ten `9300392` Black Wing Henchmen | **22589** (infoNumber 22604) |

**OWNER Q1 covers the fallback room only** - which room a character on none of those quests lands
in. The four gated branches above are decided by data and need no decision.

The negatives were re-proved over all 4,848 pristine v84 map images: **zero** `tm` values point at
914100020/21/22/23, and **zero** point at 200090080, 200090090 or 922030000. So no portal anywhere
supplies these destinations; a script is the only mechanism v84 gives.

## R46 - the ferry, and the tracker's missing sixth name

Absent:

* `scripts/portal/move_RitSDI.js` - outbound, declared by pristine `Map2/200090080.img` **portal/9..14**
* `scripts/portal/move_SDIRit.js` - return, declared by `Map2/200090090.img` **portal/9..14**

Both are six portals each, `pn=out00`..`out05`, `pt=9`, `tm=999999999`, `delay=200`, `onlyOnce=0`.
**The old tracker's list of eight missing names records `move_SDIRit` only; `move_RitSDI` is a sixth
missing name it never records.**

`pt=9` is a trigger, not a warp. This codebase does boat arrival as a scheduled `warpEveryone`
(`scripts/event/Boats.js:69-73`), so the ride duration is not stated anywhere in v84 and must come
from the existing boat implementation rather than being made up.

**One thing here needs a client to settle.** Olaf `1002101` carries `Npc.wz` `info/script` =
`"contimoveSDIRit"`, which is client-side, and `ChangeMapHandler.java:113-166` has no branch for
`divi == 1040000`. A portal-less self-transfer would be silently dropped - the exact failure ticket
41 fixed for the Evan cutscenes. So: write the two scripts against the `Boats.js` shape, add the
missing `ChangeMapHandler` branch if the transfer is dropped, and record that confirming a click on
Olaf produces a transfer request at all **requires a client launch, which no agent may perform**.

## Precedent

* Branch shape: `scripts/portal/enterBlackFrog.js` (commit `dda2d5f5a`).
* Multi-value quest ladder: `scripts/portal/evanDollGR.js`.
* Boat ride: `scripts/event/Boats.js:69-73`.
* The room-to-quest claims above come from `Check.img` and each room's own `life`/`reactor`/portal
  contents, not from a wiki.
* Ticket 47 is the standing record for this cluster and its six refusals; this ticket retires two of
  them (`enterSDI`, `enterSnowDragon`) on the room-claim evidence and leaves the other four alone.

## Acceptance criteria

- [ ] `scripts/portal/enterSDI.js` exists, loads under the real `GraalJSScriptEngine`, and warps to
      **914100000**.
- [ ] From El Nath 220000300, a character can walk `scr00` -> 922030000 -> `tel00` -> 914100000 with
      no script error logged.
- [ ] `scripts/portal/enterSnowDragon.js` routes 22580 -> **914100020**, 22588 -> **914100022**,
      22589 -> **914100023**, 22590/22591 -> **914100021**, and the owner's chosen fallback
      otherwise. Each branch is asserted by name in a test.
- [ ] `scripts/portal/move_RitSDI.js` and `scripts/portal/move_SDIRit.js` exist and load; each is
      driven by all six `out00`..`out05` portals on its map without duplicating the ride.
- [ ] `MapAndPortalScriptsRealLoad` covers all four new scripts: real engine load, `enter(pi)`
      invoked against a Mockito `PortalPlayerInteraction`, exact interactions asserted with
      `verifyNoMoreInteractions`, and **no `Effect.wz` `Direction*` scene named by any of them**.
- [ ] Quests 22580, 22588, 22589, 22590 and 22591 are each walkable end to end on a level-70 Evan -
      accept, reach the room, satisfy the objective, hand in.
- [ ] The ferry half states, in the ticket's own Delivered section, whether the `divi == 1040000`
      branch was needed, and names the one step that only a client can confirm.

Tests run as `-Dtest=MapAndPortalScriptsRealLoad`. **Do not run maven while sibling agents are
active.**

## Do not

- Do not write `summonIceWall` or `stopIceWall2`. Mob 9300391 is placed in none of v84's 4,848 map
  images and produced by no revive chain in any of its 1,605 mob images; writing either means
  inventing a mob count and coordinates. See ticket 70.
- Do not choose `enterSnowDragon`'s fallback room without the owner. The four gated branches are
  data; the fallback is not.
- Do not play an `Effect.wz` scene from any of these four scripts. None of `onSDI`,
  `enterSnowDragon`, `stopIceWall`, `summonIceWall`, `blackSDI`, `outSDI` appears among the 25
  distinct `Direction*.img` nodes, and a scene path the client cannot resolve crashes the client.
- Do not add portals to 200090080 or 200090090 to "fix" the ferry. Ticket 37's refusal stands: extra
  door names on a ferry map create a ride-skip exploit.
- Do not launch a client to check Olaf. Record the question and hand it to the owner.
