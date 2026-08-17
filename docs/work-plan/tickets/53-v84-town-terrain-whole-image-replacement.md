# 53 - the maps whose NPCs stayed misplaced until their whole v84 foothold table was taken

**Done.** Follow-on to `b3cb60503` (8 towns) and `V84RenumberedFootholdParityRealLoad` (20 maps).
This ticket was written as an owner decision with no code; the decision was given
("do what's needed for this to work... we are trying to be feature paired to v84") and the work is
applied. Pinned by `src/test/java/server/V84TerrainFootholdParityRealLoad.java`.

## The class is 52 maps, not 11

The original eleven came from a hand-picked sample. A whole-archive digest of
`D:\games\MSv84\client\Map.wz` against our XML tree found **52** maps whose foothold *geometry*
differs, not merely its numbering; the eleven were a subset. One of the eleven, `251010403`, is not
in this class at all - its geometry is identical and it belonged to the renumber batch, where it was
handled.

The digest is the one `WzPeek digest` prints: per map, a hash of the foothold multiset with ids
**excluded** (`fhgeom`) and one with ids **included** (`fhid`). `fhid` differing while `fhgeom`
matches is a renumber; both differing is a terrain edit.

## The physics framing in the first draft of this ticket was wrong

That draft called replacing the `foothold` table a physics risk - *"a wrong swap does not misdraw an
NPC, it drops players through the floor"*. It does not, and the reason is what made the work safe:

* `AbstractMovementPacketHandler.updatePosition:174,231` - the only path a player's position takes -
  reads the client's absolute x/y and **`p.skip`s the foothold id the client sends with it**. There
  is no server-side collision, no gravity, and no validation of either. Movement is
  client-authoritative and the client uses **its own** terrain, which is v84's.
* Every `findBelow(...).getId()` in the tree exists to mint an id **to send**: scripted npc spawns
  (`AbstractPlayerInteraction:980`), pets (`SpawnPetProcessor:84`), mobs, `PlayerNPC:303,442`.
  `PacketCreator:1402` writes it raw.
* `Character.getFh:5264` is not a foothold id at all - it returns `getY1()`, a coordinate, read only
  by magic-door placement. The player's own spawn packet sends `writeShort(0)` in its place
  (`PacketCreator:2073`).
* `calcPointBelow:539` yields a coordinate, for drop landing and mob spawn anchoring.

So a foothold our table had and the client did not was a **phantom platform** - the server anchored
npcs, mobs and item drops to ground the player could never see - and a platform the client draws
that we lacked was invisible ground. Both were already-broken states. Taking v84's table verbatim is
what makes server and client agree; no path exists by which it can move a player.

## What was applied

v84's `foothold` section verbatim on all 52 - **99,556 leaves**, gated key-for-key and
value-for-value against the archive so "absent" and "present but empty" stay distinct - and every
`life` row re-pointed onto it, 718 rows, by three rules in order:

1. **the row's own `cy`** (84 rows). `cy` records the y the row stands at and survives a renumber
   untouched. Where our v83 `fh` named the *neighbouring, overlapping* platform rather than the one
   the row is on, `cy` settles it - and on all 84 it agrees with the foothold v84's own life row
   cites. Without this rule a geometry remap faithfully carries the wrong platform forward.
2. **v84's id for the same geometry**, where that platform survives (631 rows).
3. **the nearest v84 ground at or below the row** (3 rows), where v84 deleted the platform outright.

`fh=0` was left alone on 26 rows. It is our own "unanchored" sentinel; v84 emits it on none of its
832 rows across these maps, so it predates this work and is not part of this defect.

### Per-map delta

Platforms compared by `(x1,y1,x2,y2)` alone, which is all `MapFactory:196-217` reads - layer and
group names are iterated and discarded. Comparing *with* layer/group inflates both columns equally,
which is why nine maps show `+0/-0` here yet still failed the `fhgeom` digest.

| map | | ours | v84 | added | removed |
|---|---|---:|---:|---:|---:|
| 101000000 | Ellinia | 1203 | 1238 | +160 | -125 |
| 922010800 | Abandoned Tower Stage 8 | 450 | 450 | +90 | -90 |
| 610030300 | The Test of Agility | 996 | 1028 | +77 | -45 |
| 610030700 | Grandmaster Secret Chamber | 59 | 108 | +67 | -18 |
| 670010200 | Amorian Stage 1 | 464 | 481 | +56 | -39 |
| 610030510 | Warrior Mastery Room | 367 | 385 | +52 | -34 |
| 106010102 | Entrance of Golem's Temple | 104 | 141 | +43 | -6 |
| 670010600 | Amorian Stage 5 | 337 | 351 | +40 | -26 |
| 106010101 | The Breathing Rock | 80 | 93 | +27 | -14 |
| 100030000 | Evan farm entrance | 489 | 511 | +22 | **0** |
| 109090000 | Sheep Ranch Lobby | 48 | 60 | +22 | -10 |
| 674030000 | Treasure Dungeon | 395 | 397 | +21 | -19 |
| 541010110 | The Peaceful Ship | 157 | 154 | +13 | -16 |
| 120000105 | Nautilus Training Room | 32 | 12 | +12 | -32 |
| 220011001 | Ludibrium Sky Terrace | 2 | 11 | +9 | **0** |
| 200080600 | Orbis Tower 16F | 200 | 206 | +8 | -2 |
| 541000000 | Boat Quay Town | 277 | 275 | +6 | -8 |
| 261000000 | Magatia | 741 | 739 | +5 | -7 |
| 240070502 | Neo City emergency exit | 49 | 49 | +4 | -4 |
| 211000102 | El Nath Dept. Store | 25 | 25 | +4 | -4 |
| 610030530 | Thief Mastery Room | 259 | 263 | +4 | **0** |
| 103000000 | Kerning City | 519 | 518 | +3 | -4 |
| 251000000 | Herb Town | 333 | 333 | +3 | -3 |
| 101000004 | Hall of Magicians | 85 | 67 | +3 | -21 |
| 102000004 | Hall of Warriors | 24 | 6 | +3 | -21 |
| 103000008 | Hall of Thieves | 22 | 3 | +2 | -21 |
| 250000000 | Mu Lung | 597 | 590 | +2 | -9 |
| 106020000 | Mushroom Forest Field | 44 | 40 | +2 | -6 |
| 100000204 | Hall of Bowmen | 22 | 2 | +1 | -21 |
| 102000000 | Perion | 521 | 521 | +1 | -1 |
| 551000000 | Kampung Village | 301 | 301 | +1 | -1 |
| 551000200 | Hibiscus Road 2 | 146 | 147 | +1 | **0** |
| 541000100 | Mysterious Path 1 | 281 | 278 | +1 | -4 |
| 600000000 | New Leaf City | 706 | 699 | **0** | -7 |
| 670010400 | Amorian Stage 3 | 88 | 84 | **0** | -4 |
| 195000000 | Dangerous Ant-Hole | 762 | 758 | **0** | -4 |
| 541000300, 610010002, 610030010, 670010100, 670010750, 910510100, 921110000, 926120200 | | | | +1..+12 | same |
| 101030101, 105090310, 130000101, 140010110, 270000100, 300000012, 610030000, 930000300 | | | | **0** | **0** |

The last row is maps where v84 moved every platform to a different `layer`/`group` without changing
one coordinate - a pure renumber that only the layer-aware digest separates from this class.

## What was refused, and why

**1. v84's 96 static `9901xxx` life rows on the seven Hall of Fame maps.** These are genuine Nexon
rows - the archive is pristine v84, SHA-256 matched against `porting-resources/wz-data/v84/Map.wz`.
They are refused on a mechanical ground, not a provenance one:
`PlayerNPC.fetchAvailableScriptIdsFromDb:319-323` allocates PlayerNPC scriptids from
`NpcId.PLAYER_NPC_BASE + 100 * branch`, so **every id v84 places on those maps sits inside a live
PlayerNPC branch**. changeSet 163 already seats real PlayerNPCs at 9901000, 9901100, 9901200,
9901300, 9901301 and 9901400 on those same maps; v84's rows are 9901001-9901008, 9901100-9901107,
9901200, 9901300-9901301, 9901740-9901749, 9901800-9901849 and 9901600-9901616. Adding them puts a
static npc on the exact id the allocator hands the next deployment.
`HallOfFameSeedRealLoad.seededIdsStayInsideTheBand` records that a prior pass already proved this
collision is real corruption. Cosmic fills these halls dynamically instead - that *is* the v84
feature, implemented live rather than statically. **If the owner would rather have v84's static
rows, the route is to drop changeSet 163 and the PlayerNPC halls with it. That is a bigger decision
than this ticket and is not taken here.**

**2. `106010101`, the mob mix.** A first sweep read this as "v84 adds 7 mobs". It is not: v84
rebalanced the map's whole population - Blue Mushroom 12 -> 8, Stone Golem 4 -> 1, Fairy 2 -> 9 -
and moved **every** coordinate. Under additive-only the result would be 25 mobs at 25 spawn points,
which is neither our layout nor v84's. Our 18 rows are kept and re-pointed; taking v84's is an
all-or-nothing life swap needing its own authorisation. `106010102` is the same shape (our 8 Stone
Golems against v84's 1).

**3. Portals.** 28 of the 52 also differ from v84 in `portal`, and
`docs/wz-baseline/protect-list/Map.txt` lists custom portal nodes on many of them - `674030000`
alone has 32 protected portal slots, plus `910510100` @26-32, `106020000` @4-9, `610030000` @3-8,
and `670010600`. A verbatim portal take would delete them. Portals are untouched here; they need the
protect-list as an input, not a whole-section copy.

### Everything the first draft listed as a casualty is still present

None were deleted - the `196000000` precedent from ticket 46 governs. All are kept and re-pointed,
pinned by `theRowsAVerbatimLifeTakeWouldHaveDeletedAreStillHere`: `1022101` and `9250052` (seasonal
`limitedname=xmasvillage`) on `101000000`/`102000000`/`103000000`/`250000000`/`251000000`,
**`1052012` Mong from Kong** and **`9000036` Agent E** on `103000000`, `9000036` on `251000000`,
plus `9000040`/`9000041`/`9010009` on `600000000`, `9010021` on `140010110`, `9201047` on
`670010100`, `9100110` on `211000102`, and `1052013` Computer on `195000000`.

## Custom terrain this did delete, stated plainly

12 protect-listed `foothold` nodes fall inside the 52. Of the 195 footholds they cover, **67 have no
counterpart in v84 and are gone**:

| map | node | footholds | with no v84 counterpart |
|---|---|---:|---:|
| 102000004 | `foothold/1/0` | 21 | 21 |
| 610030300 | `foothold/6` | 12 | 12 |
| 670010200 | `foothold/6` | 9 | 9 |
| 250000000 | `foothold/1/6` | 7 | 7 |
| 106020000 | `foothold/0` | 4 | 4 |
| 195000000 | `foothold/6` | 4 | 4 |
| 541010110 | `foothold/6/2` | 119 | 5 |
| 610030700 | `foothold/6` | 3 | 3 |
| 610030510 | `foothold/6` | 1 | 1 |
| 670010400 | `foothold/6` | 1 | 1 |
| 261000000 `foothold/3`, 610030000 `foothold/1` | | 14 | 0 |

Exactly **one life row stood on any of them**: `195000000` slot 76, npc `1052013` "Computer", itself
protect-listed. It is kept and re-anchored 274px down onto real v84 ground - the ledge it stood on
exists in neither stock v83 nor v84, so the client never drew ground under it.

## Downstream: changeSet 165

changeSet 163 seeded six Hall of Fame PlayerNPCs with `x/cy/fh` computed from
`getGroundBelow(PlayerNPCPodium.calcNextPos(rank, 1))` against **v83's** tables. Three cited ids 12,
40 and 18 on maps whose v84 image has 2, 67 and 12 footholds. 163 is applied and its checksum
frozen, so `165-hall-of-fame-v84-terrain.sql` corrects the rows in place - the same shape as
changeSet 164 after the portal reindex. Largest move: 27px, and `x` is unchanged on every row.

`PlayerNPCPodium.calcNextPos` hardcodes its platform offsets (-50, -170, +70). All five podium halls
were replayed over every `(rank, step)` the allocator can produce, against the real `FootholdTree`:
**every slot still lands on a foothold**. That matters because `getGroundBelow` does `spos.y--` on
`calcPointBelow`'s result with no null check, so a slot over empty space is an NPE, not a misdraw.
Pinned by `HallOfFameSeedRealLoad.everyPodiumSlotOnEveryHallLandsOnAFoothold`. Where v84 has no wing
under one of those offsets the slot now falls to the hall floor, which is what the client draws
there anyway; retuning the offsets to v84's wings is cosmetic and deliberately not done.

## How it was verified

By measurement, not inspection, on all 52:

* the resulting `fhid` digest **equals v84's on every map**, computed by re-running the digest over
  our written XML with a different reader than the one that read the archive;
* every `life` row cites a foothold its own map has (0 dangling), and each cited platform **spans
  that row's own x** and lies at or below it - 733 of 734, the exception a stock row off by 2px that
  was already so before this change;
* `life` slot names still cover 0..n-1 and no row was lost;
* `life`, `portal`, `info`, `reactor`, `ladderRope`, `seat` and `area` are **byte-identical to the
  previous commit except for the `fh` leaf** on life rows - compared by row content, not by slot
  path, so a renumber cannot hide a loss inside the check;
* 99,556 foothold leaves match the archive key-for-key and value-for-value;
* every file's diff is confined to the foothold block and its life `fh` lines. `core.autocrlf` is
  true in this worktree, so the working tree is CRLF while the blobs are LF - a writer that forces
  the wrong style shows every line as changed and buries the real edit.

`V84TerrainFootholdParityRealLoad` (8 tests), `HallOfFameSeedRealLoad` (7),
`V84TownIndexParityRealLoad`, `V84RenumberedFootholdParityRealLoad`, `V84MapLifeParityRealLoad`,
`V84PortalIndexParityRealLoad` and `MapAndPortalScriptsRealLoad`: **60 tests, all green.**

A restart is required - `Map.wz` is read at map load.

## Noted in passing, not fixed

`scripts/portal/Depart_goFoward0.js:10,18` warps to `103040430, "right00"`. Pristine v84 has no
`right00` on `103040430` either (its portals are `sp`, `right01`), and our `103040420`, `103040430`,
`103040450` and `103040460` images match v84's portal arrays exactly. The map data is correct; the
script names a portal that never existed in any version. Harmless today only because the script is
attached to no portal on `103040420`/`103040450`, so nothing reaches the warp.
