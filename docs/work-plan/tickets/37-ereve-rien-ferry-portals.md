# 37 — Ereve / Rien ferry portals — the "stranded passenger" trap is NOT real

**Status:** closed — investigated, refuted, no code change.

## The claim under test

A sweep of all 5,338 `Map.wz` images found 2,674 portals declaring a portal script but only 462
portal scripts on disk — 113 distinct missing scripts across 703 portals. The largest single cluster
is 360 portals from six script names:

    move_OrbEre  move_EreOrb  move_EliEre  move_EreEli  move_RitRie  move_RieRit

plus 12 more from `move_RitSDI` / `move_SDIRit`. All 372 sit on the ferry maps `200090020`–`200090090`.

A missing portal script really is a *dead* portal, not a silent-fallback one:

- `scripting/AbstractScriptManager.java:49` — `if (!Files.exists(scriptFile))` returns `null`, no log.
- `server/maps/GenericPortal.java:131-149` — `if (getScriptName() != null) { script } else if
  (getTargetMapId() != MapId.NONE) { warp }`. The script branch wins; `tm`/`tn` is never consulted.

And these portals have `tm=999999999` (`MapId.NONE`) and `tn=""`, so no fallback warp is even
possible. The inference was therefore: boarding an Ereve or Rien ferry **strands** the player.

**That inference is wrong.** Three independent exits exist.

## Exit 1 — the server force-warps every passenger on a timer (primary)

`server/maps/MapleMap.java:2589-2637`, inside `addPlayer()`, hard-codes all six live ferry maps:

| ride map | `MapId` constant | ride time | destination |
|---|---|---|---|
| 200090060 | `FROM_LITH_TO_RIEN`     | 1 min | 140020300 `DANGEROUS_FOREST` (Rien docks) |
| 200090070 | `FROM_RIEN_TO_LITH`     | 1 min | 104000000 `LITH_HARBOUR`, portal 3 |
| 200090030 | `FROM_ELLINIA_TO_EREVE` | 2 min | 130000210 `SKY_FERRY` |
| 200090031 | `FROM_EREVE_TO_ELLINIA` | 2 min | 101000400 `ELLINIA_SKY_FERRY` |
| 200090021 | `FROM_EREVE_TO_ORBIS`   | 8 min | 200000161 `ORBIS_STATION` |
| 200090020 | `FROM_ORBIS_TO_EREVE`   | 8 min | 130000210 `SKY_FERRY` |

Each also sends `PacketCreator.getClock(...)`, so the passenger sees a countdown. Times are scaled by
`World.getTransportationTime()` (`net/server/world/World.java:449`, divides by `travelrate`).

Logout/login is covered: `PlayerLoggedinHandler.java:260` calls `player.getMap().addPlayer(player)`,
which re-arms the timer. The scheduled task guards on `chr.getMapId() == <ride map>`, so bailing out
early does not cause a stray warp later.

## Exit 2 — every ferry map also carries working non-script portals

`200090020` has `hd00`..`hd08` (`pt=3`, collision) with `tm=200000161 tn="st00"` and
`script=""`. `PortalFactory.java:54-58` nulls an empty script string
(`if (script != null && script.equals("")) script = null;`), so those take the plain warp branch and
work. Same shape on every ferry map (`200090080` has `hd00`..`hd07` → `104000000`).

*(UNPROVEN: whether `hd**` are reachable by walking, or only catch a fall off the deck. Not
load-bearing — Exit 1 alone disproves the trap.)*

## Exit 3 — the dead portals are harmless by construction

The six missing scripts are only ever on `out00`..`out05` (`pt=9`, collision-script), fired by the
client through `ChangeMapSpecialHandler.java:50`. With no `.js`, `executePortalScript` returns false
and `GenericPortal.enterPortal` just sends `enableActions()`. Nothing breaks; nothing warps.

## `fieldLimit` 402046 decoded

402046 = `0x6227E`. Against `server/maps/FieldLimit.java`: `MOVEMENTSKILLS 0x2`, `SUMMON 0x4`,
`DOOR 0x8`, **`CANNOTMIGRATE 0x10`**, `0x20`, `CANNOTVIPROCK 0x40`, `CANNOTUSEMOUNTS 0x200`,
`0x2000`, `CANNOTJUMPDOWN 0x20000`, `0x40000`.

`CANNOTMIGRATE` is set, so channel change / cash shop / town-scroll out is indeed blocked
(`client/Client.java:1485`, `EnterMTSHandler.java:69`, `GameConstants.java:581`). That is what made
the trap plausible — but it only removes a fourth exit, and the first three still hold.
No ferry map declares `timeLimit`.

## The six scripts, resolved

Naming is `move_<From><To>`. `Orb`=Orbis, `Ere`=Ereve, `Eli`=Ellinia, `Rie`=Rien,
`Rit`=Lith Harbor (Korean 리스, L/R merge), `SDI`=Slumbering Dragon Island
(`String.wz/Map.img` 914100000 = "Slumbering Dragon Island / Temporary Harbor").

| script | portals | maps | direction | live map | boarding NPC |
|---|---|---|---|---|---|
| `move_OrbEre` | 60 | 200090020,022,024,026,028,040,042,044,046,048 | Orbis → Ereve   | 200090020 | 1100008 @ 200000161 |
| `move_EreOrb` | 60 | 200090021,023,025,027,029,041,043,045,047,049 | Ereve → Orbis   | 200090021 | 1100004 @ 130000210 |
| `move_EliEre` | 60 | 200090030,032,034,036,038,050,052,054,056,058 | Ellinia → Ereve | 200090030 | 1100007 @ 101000400 |
| `move_EreEli` | 60 | 200090031,033,035,037,039,051,053,055,057,059 | Ereve → Ellinia | 200090031 | 1100003 @ 130000210 |
| `move_RitRie` | 60 | 200090060–069 | Lith Harbor → Rien | 200090060 | 1200004 @ 104000000 |
| `move_RieRit` | 60 | 200090070–079 | Rien → Lith Harbor | 200090070 | 1200003 @ 140020300 |
| `move_RitSDI` |  6 | 200090080 | Lith Harbor → Slumbering Dragon Island | — | none |
| `move_SDIRit` |  6 | 200090090 | Slumbering Dragon Island → Lith Harbor | — | none |

Each map carries exactly 6 such portals (`out00`..`out05`).

Evidence for direction: NPC placement extracted from `Map.wz` `life` nodes, cross-checked against the
`cm.warp(...)` in each NPC script — `scripts/npc/1100003.js:45` → 200090031,
`1100004.js:45` → 200090021, `1100007.js:46` → 200090030, `1100008.js:46` → 200090020,
`1200003.js:46` → 200090070, `1200004.js:46` → 200090060. Every one agrees with the
`MapId` constant name and with `String.wz/Map.img` (`200090020` mapName = "To Ereve", etc.).

**Only 6 of the 62 ferry maps are reachable.** Nothing in `scripts/` or `src/main/java/` references
the other 56 duplicates, nor 200090080/200090090, nor 914100000. Their 336 dead portals are
unreachable dead code.

`info/returnMap` is inconsistent in Nexon's data here — origin for the Orbis/Ereve rides
(200090020 → 200000161), destination for the Rien rides (200090060 → 140020300). Not load-bearing:
the server never uses it on these maps.

## Why no fix was written

Any implementation of `out00`..`out05` is one of two bad things:

1. warp to the destination → free instant travel, skipping the 1/2/8-minute ride the server enforces;
2. warp back to the origin → an exact duplicate of the existing `hd**` portals.

The ferry works end to end today. `scripts/portal/` gains nothing from six files that must either
introduce an exploit or restate a portal that already exists.

**Dead-portal count is unchanged by design: 703 before, 703 after, 113 distinct missing scripts.**
360 of those 703 (plus 12 more) are now accounted for as *cosmetic, not broken* — the remaining
331 across the other 105 script names are the real backlog and are out of scope here.

## Provenance

Pre-existing, not a v84 regression. The affected maps trace to `3990a0820 MapleSolaxiaV2 Reboot`;
no v84 merge touched them.
