# 53 - eleven maps whose NPCs stay misplaced until their whole v84 image is taken

**Owner decision required. No code in this ticket.** Follow-on to `b3cb60503`.

## What is already fixed, and what these eleven still have wrong

`b3cb60503` corrected two index-addressed references that the server sends the client as bare
numbers for the client to resolve against *its own* copy of the map:

* **portal array position** - `PacketCreator.getWarpToMap` writes `portal.getId()`, and
  `PortalFactory.loadPortal` takes that id from the node's *name*, i.e. its array slot. Fixed on all
  17 towns, plus `220000300` and `100030000` in the follow-up.
* **foothold id** - `life/*/fh`, and every runtime `map.getFootholds().findBelow(pos).getId()`
  (scripted npc spawns, pets, PlayerNPCs, mobs). Fixed on eight maps by taking v84's foothold table
  verbatim, because v84 had **renumbered those tables without moving a single platform**.

On the eleven maps below that second fix is not available. v84 did not merely renumber their
footholds - it **re-laid the terrain**, so no id-to-id mapping exists. Their portal indices are
correct; their NPCs and mobs are still drawn on the wrong platforms in the v84 client, because the
server keeps sending v83 foothold ids.

## Why a renumber is impossible (and where it actually is possible)

A renumber is only sound when our table and v84's describe the *same set of platforms* - then each
of our ids has exactly one v84 id with identical `(layer, group, x1, y1, x2, y2)` and the swap is
provably geometry-preserving. Measured against `D:\games\MSv84\client\Map.wz`:

| map | | ours | v84 | added | removed | verdict |
|---|---|---:|---:|---:|---:|---|
| 100030000 | Evan farm entrance | 489 | 511 | +22 | **0** | **v84 only ADDS platforms - nothing of ours disappears** |
| 251010403 | Herb Town area | 105 | 105 | 0 | **0** | **identical geometry; one duplicated foothold pair blocks a 1:1 map** |
| 102000000 | Perion | 521 | 521 | +1 | -1 | genuine terrain edit |
| 251000000 | Herb Town | 333 | 333 | +3 | -3 | genuine terrain edit |
| 103000000 | Kerning City | 519 | 518 | +3 | -4 | genuine terrain edit |
| 200080600 | Orbis Tower | 200 | 206 | +8 | -2 | genuine terrain edit |
| 106010102 | Golem's Temple | 104 | 141 | +43 | -6 | genuine terrain edit |
| 261000000 | Magatia | 741 | 739 | +5 | -7 | genuine terrain edit |
| 250000000 | Mu Lung | 597 | 590 | +2 | -9 | genuine terrain edit |
| 109090000 | Battle Square | 48 | 60 | +22 | -10 | genuine terrain edit |
| 101000000 | Ellinia | 1203 | 1238 | +160 | **-125** | genuine terrain edit, by far the largest |

"removed" counts platforms present in our tree whose exact geometry does not exist anywhere in v84's
table for that map.

**The top two rows are not terrain edits and should be split off from this ticket.** `100030000` is
strictly additive - every platform we have survives, v84 just adds 22 more that our server does not
know about (so the client already draws ground the server does not have). `251010403` has byte-identical
geometry and fails only because one foothold pair shares coordinates, which a
`(layer, group, geometry)` tie-break resolves. Both are low-risk and both would fix their maps
outright. They were **not** applied here because the instruction was to write this up rather than
attempt a partial renumber - but they need none of the risk discussion below.

## What taking the whole v84 image would require

For the remaining nine, the only route to correct NPC placement is replacing the image's
**`foothold` section together with its `life` section**, from `porting-resources/wz-data/v84/Map.wz`
(byte-identical to `D:\games\MSv84\client\Map.wz`, read with `docs/wz-baseline/tool-peek`). The two
must move together: v84's `life` rows cite v84 foothold ids, and ours cite ours. Taking one without
the other is worse than taking neither.

`portal` is already at v84 parity on all eleven and must not be re-derived. `back` / `tile` / `obj` /
`info` are client-side render data the server never reads; leaving them alone keeps the diff to what
the server actually consumes.

### The risk, stated plainly

1. **Terrain replacement is a new authorisation.** The owner has authorised deleting *map life
   arrays* for exact v84 parity (ticket 46). `foothold` is not a life array. Replacing it changes
   where players stand, walk and fall on nine live maps - including Ellinia, Perion, Kerning and Mu
   Lung. On Ellinia that is 125 platforms this tree has and v84 does not.
2. **Physics is server-side.** `FootholdTree.findBelow` drives `calcPointBelow`, mob spawn
   placement, drop landing and `Character.getFh`. A wrong swap does not misdraw an NPC - it drops
   players through the floor.
3. **`life` replacement deletes rows.** Per map, what disappears (ids that appear only on our side;
   an id on *both* sides means v84 merely moved it, which is a coordinate change, not a loss):

   | map | rows deleted | what they are |
   |---|---|---|
   | 101000000 | 1022101, 9250052 | seasonal `limitedname=xmasvillage` decorations |
   | 102000000 | 1022101, 9250052 | same two |
   | 103000000 | 1022101, 1052012, 9000036 | seasonal, plus **npc 1052012** and **9000036** |
   | 250000000 | 1022101 | seasonal |
   | 251000000 | 1022101, 9000036 | seasonal, plus **9000036** |
   | 106010102 | 7x mob 5130101 | ours holds 8, v84 holds 1 |
   | 100030000, 109090000, 200080600, 251010403, 261000000 | none | life arrays already match v84 row for row |

   Only `103000000` and `251000000` lose a non-seasonal NPC. **`1052012` and `9000036` must be
   checked against `scripts/npc/` and the quest tables before either map is replaced** - the
   `196000000` precedent in ticket 46 is exactly this: v84 parity there would have deleted the Cafe
   PQ entry NPC, so it was refused.
4. **Five of the eleven need no life change at all** (last row of the table above). For those the
   ask is narrower: swap `foothold`, remap `life/*/fh` by geometry where it still resolves, and
   delete nothing.

### Order of work, cheapest and safest first

1. `100030000`, `251010403` - not terrain edits; no deletions; no new authorisation needed.
2. `109090000`, `200080600`, `261000000` - terrain edit but zero life deletions.
3. `102000000`, `101000000`, `250000000`, `106010102` - deletions are seasonal decorations or
   surplus mobs only.
4. `103000000`, `251000000` - **blocked** until `1052012` and `9000036` are shown to be unreferenced.

Whatever subset is taken, the check is the one `b3cb60503` already uses: after the write, every
`life` row must cite a foothold its own map has, `portal`/`life` slots must stay consecutive from 0,
and `V84TownIndexParityRealLoad` must stay green. A restart is required either way - `Map.wz` is read
at map load.
