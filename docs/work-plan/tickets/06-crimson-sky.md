# 06 — Crimson Sky playable

**Blocked by:** 03

**Status:** partial — 4 of 5 criteria delivered and agent-verified; criterion 5 (travel route) is
blocked on a decision nobody can merge their way out of. See "Travel route".

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — executed as written,
under the post-03e tool (`--deny` mandatory, `deps` at add-list granularity, per-ticket `pre\`,
`--live` hash check). Staging directory: `D:\games\MapleStory\Server\wz-merge\06-r2\`.
**The pre-03e output in `Server\wz-merge\06\` is stale and was discarded**, along with the server
XML that run had spliced into the working tree (reverted file-by-file with `git checkout` /
`git clean` scoped to this ticket's files only — ticket 04 was running concurrently in the same
tree).

## What to build

The Crimson Sky area is reachable, populated, and rewarding: you can travel there, fight the
dragons, and get drops.

This is the largest genuine content win in v84 — a Leafre/Dragon-Nest expansion. Maps
`240080000`–`240080800` plus `683010000` Dragon's Nest. Mobs `9500374`–`9500382`. NPCs Matada,
Crimson Sky Doorway, Dragon Rider, Giant Twin Dragon's Egg.

**Drops are part of this ticket, not a follow-up.**

## Acceptance criteria

- [x] All Crimson Sky maps present in client WZ and server XML — 22 maps, staged and XML-spliced,
      read back through the server's own `XMLWZFile`. **Not** ticked for "reachable in game" — see
      criterion 5 and `## Human steps`.
- [x] All nine mobs spawn at correct rates and are killable — **the WZ half only.** All 17 mob
      images merged and parse; every mob id any in-scope map places has a sprite. **Spawn rates in
      game are human-verified**, and the nine ids the ticket names are placed by no map at all —
      see "What the scope statement got wrong".
- [x] NPCs present and interactive — six NPC images merged and parse; names read back. Dialogue is
      script-driven and none of these NPCs has a Cosmic script, so "interactive" means "clickable
      and named", not "has a conversation". Stated rather than ticked past.
- [x] Drop tables added so every new mob drops something appropriate — 776 rows,
      `db/data/153-crimson-sky-drop-data.sql`, 16 dropperids.
- [ ] **Travel route into the area works from existing content — BLOCKED, owner decision.**

---

## What the scope statement got wrong, measured

The ticket names mobs `9500374`–`9500382` (Green Cornian … Leviathan) as the area's population.
Dumping `life/` for all 22 maps says otherwise:

| what the maps actually place | where |
|---|---|
| `8300000` Soaring Hawk (×29) | `240080100` |
| `8300001` Soaring Eagle (×29) | `240080200` |
| `8300002` Soaring Red Wyvern (×14) | `240080300` |
| `8300003` Soaring Blue Wyvern (×23) | `240080300`, `240080400` |
| `8300004` Soaring Black Wyvern (×21) | `240080400`, `240080500` |
| `8300005` Soaring Griffey (boss) | `240080500` |
| `8300006` Dragonica (boss, 120M HP) | `240080600` |
| NPCs `2085000`/`2085001`/`2085002` | `240080000`, `240080100`, `240080800` |
| NPCs `9201144`/`9201145` | `683010000` |

**`9500374`–`9500382` appear in no `life` node anywhere in scope.** They are `summonType=1` clones
of mobs the live v83 client already has, at the same exp values:

| clone | live original | exp |
|---|---|---|
| 9500374 Green Cornian | 8150200 | 3000 = 3000 |
| 9500375 Dark Cornian | 8150201 | 3700 = 3700 |
| 9500376 Jr. Newtie | 8190000 | 3800 = 3800 |
| 9500377 Nest Golem | 8190002 | — |
| 9500378 Blue Dragon Turtle | 8140700 | 1780 = 1780 |
| 9500379 Red Dragon Turtle | 8140701 | 2100 = 2100 |
| 9500380 Skelegon | 8190003 | 4500 = 4500 |
| 9500381 Skelosaurus | 8190004 | 4750 = 4750 |
| 9500382 Leviathan | *(none — new)* | 47355 |

`683010000` "Dragon's Nest" is not a hunting map either: `hideMinimap=1`, one spawn portal, two
NPCs, `onUserEnter = dragonLair_GL`. It is a scripted story room.

**Consequence, stated plainly:** merging the nine named mobs gives them sprites, names, server XML
and drop tables, but **nothing spawns them**. `8300006` Dragonica carries mob skill `128` (summon)
level 15, and v84's `Skill.wz/MobSkill.img/128/level/15` contains no mob-id list, so it does not
summon them either. Making those nine killable needs a server-side spawner or quest, which is not
WZ work and is not in this ticket. The seven `830000x` are the reachable population.

Both sets were merged. Only `8300007` (Dragon Rider, 130M HP) is placed by nothing and summoned by
nothing; it is merged for completeness and deliberately has no drop table.

---

## `deps` output and the merge order derived from it

`WzMerge deps <v84>\Map.wz <id> <add-list>` run for all 22 ids, all exit 0. Raw output:
`D:\games\MapleStory\Server\wz-merge\06-r2\<id>.deps.txt`.

Union of everything owed, and the order it forces:

| # | rows | why |
|---|---|---|
| 1 | `Map.wz/Back/dragonRoad.img/ani/20`–`24`, `back/42`–`46` | 10 frames added to an image v83 *has*. 9 maps draw them. This is exactly the granularity the pre-03e `deps` missed. |
| 2 | `Map.wz/Obj/dungeon3.img/skyValley` | referenced by all 22 maps; absent from v83 |
| 3 | `Map.wz/Tile/blackTileFly.img` | `240080700`/`701` only |
| 4 | the 22 map images | must come after 1–3 |
| — | `Sound.wz/Bgm14.img/DragonRider` | cross-file, `240080700/701/800/801` `info/bgm` |

Reported and **already satisfied by the live client, nothing owed**: `MapHelper.img/mark/Leafre`,
`Obj/connect.img/rope/24/*`, `Obj/guide.img/miniD/portalEff/0`, `Obj/Tdungeon.img/mushCatle/gate/6`,
`Tile/allblackTile.img`, `Tile/woodCave.img`, `Back/woodCave.img`, `Back/dragonValley.img`,
`Obj/dungeon3.img/dragonValley/*` (14 nodes), `Sound.wz/Bgm14.img/DragonLoad`,
`Sound.wz/BgmGL.img/Courtyard`.

The eight link stubs are no longer "0 dependencies": each walks through to its target and reports
`2 map image(s) walked`, so `240080101` correctly owes `240080100` as well as itself.

Mob / NPC / reactor ids are outside what `deps` resolves. Checked by hand against
`add-list/{Mob,Npc,Reactor}.txt` and re-checked as a test
(`V84CrimsonSkyNodeTest.everyLifeIdInEveryMapWasMerged`).

## Path lists — the authoritative deliverable

`docs/wz-baseline/merge-lists/06/`. Dependency rows are ordered before the maps that reference them.

| file | rows | contents |
|---|---:|---|
| `Map.paths.txt` | 34 | 12 dependency rows, then 22 map images |
| `Mob.paths.txt` | 17 | `8300000`–`8300007`, `9500374`–`9500382` |
| `Npc.paths.txt` | 6 | `2085000`–`2085003`, `9201144`, `9201145` |
| `Reactor.paths.txt` | 2 | `2408005` (heal), `2408006` (damage) |
| `Sound.paths.txt` | 1 | `Bgm14.img/DragonRider` |
| `String.paths.txt` | 35 | 13 `Map.img/ossyria`, 17 `Mob.img`, 5 `Npc.img` |
| **total** | **95** | requested |

**95 is the XML total. The binary side installed 94.** `Sound.wz`'s single row merged correctly
but its output was discarded for the verifier reason below, so the staged client is 94 and the
server tree is 95. Ticket 03f fixed the verifier and the composed install gets all 95 on both
sides. (Added by 03f — "95 added" read as a single figure for both halves and it never was.)

## Dry runs, conflicts, force decisions

`--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt` on every `merge` and every `xml`, dry runs
included.

| file | requested | added | refused | denied | forced |
|---|---:|---:|---:|---:|---:|
| Map | 34 | 34 | 0 | 0 | 0 |
| Mob | 17 | 17 | 0 | 0 | 0 |
| Npc | 6 | 6 | 0 | 0 | 0 |
| Reactor | 2 | 2 | 0 | 0 | 0 |
| String | 35 | 35 | 0 | 0 | 0 |
| Sound | 1 | 1 | 0 | 0 | 0 |

**Zero force decisions. `--force` was never passed on either side.** Every `*.conflicts.txt` in the
staging directory is empty. None of the 28 deny roots intersects this ticket's 95 paths — the 17
`MonsterBook/<mob>/reward` roots are all Leafre/Ludibrium mobs unrelated to these ids, and this
ticket never touches `MonsterBook.img`, `NpcLocation.img` or `Npc.wz/9000021.img`.

**One collision decided by *not* listing it.** v84 renames `String.wz/Npc.img/9201144` to "Shadow
Knight Rene"; the live client has that id as **"Steward"**. Adopting v84's value looked like
renaming a server-owned NPC, so the row was absent from `String.paths.txt` by choice rather than
refused by the gate.

> **REVERSED by ticket 03f, 2026-08-16 (review finding F2). The premise did not survive checking.**
> `Steward` is **not** Cosmic's: `porting-resources/wz-data/v83-stock/String.wz` carries the
> identical node — same name, same `d0`/`n0`/`n1` — so v84 **renamed a stock GMS npc** and rewrote
> its lines to match the sprite. And `9201144` is referenced by exactly one thing in this repo:
> `wz/Map.wz/Map/Map6/683010000.img.xml:280`, the `life` node **this ticket added**. There is no
> `scripts/npc/9201144.js`, no other placement, no Java or SQL reference; the live `String.wz`
> entry sits at `Npc.img.xml:7598`, appended out of sort order after `9201145` — an orphan row.
> Meanwhile this ticket merged the *sprite* (`Npc.wz/9201144.img`, `info/script = blackKnight_GL`),
> so the player saw a black knight labelled "Steward". 03f forces v84's node on both sides;
> `String.wz/Npc.img/9201144` is on `merge-lists/composed/FORCE.txt` and in
> `merge-lists/03f/String.paths.txt`, and `V84CrimsonSkyNodeTest` now pins "Shadow Knight Rene".

## Merge results

Live vs backup SHA-256, all six files, before anything (§5.0):

```
Map.wz      A39DA5AC66CB3CB1803B1A8F70F19CDF67CA191016E16C853F521B3C8156ACA4
Mob.wz      BEC9D9E15C1D16E7B9CCE1A900938363411FBFD44C52BE7DB86735A2BCB210F1
Npc.wz      2992910AC5F65FA3D1CA4B2469FA4105F948F6CEB4A6C47EE6953BE9D04DEE17
Reactor.wz  9F10FC59D51D355750E55E3D252003B8F325F03EF13F2E6DB123056BBD84F66D
String.wz   9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499
Sound.wz    BC6570D39AE1C021AF433616ED4EC5F0C8917513B63D28536D116AAC74BDFD76
```

All equal to the backup, all still equal after the ticket. Staged output (§6.4):

| file | exit | SHA-256 | bytes |
|---|---|---|---:|
| `Map.wz` | 0 | `8AB71D234D068B06A0886FBF35F3523A09883E2CD0FC4951FC838C537DD8EFBC` | 640,941,925 |
| `Mob.wz` | 0 | `B368C6943AD694687E70C4C20822B0455C9AEC1E541EC7C925BA1CDF50E37B14` | 493,098,037 |
| `Npc.wz` | 0 | `A2109FCB8AB53C34D21C2F781BE62C185AF6AD872ECCF7626C403C4EBF00C098` | 53,917,831 |
| `Reactor.wz` | 0 | `ABBA71C6B881F18AF520C5929BC58F711CAAFD9E02388E4E51C3FF97F51B99AC` | 54,827,370 |
| `String.wz` | 0 | `83C99959E13D84B24AFEB4F84B06172389C34AA1287E69D35AA28FF13EB93139` | 3,563,277 |
| `Sound.wz` | **4** | — not promoted — | — |

### `Sound.wz` failed verification, for a known and pre-existing reason

```
UNPARSEABLE image Sound.wz.partial\BgmGL.img: InvalidDataException WZ extended property exceeds its declared block.
verify: 44 images parsed, 1 unparseable, 0 requested paths missing, 1 images content-checked, 0 drifted
```

`Sound.wz/BgmGL.img` is unreadable by MapleLib in **all three trees** — procedure §11, and 02d
measured it as the only real parse failure in the whole sweep. The write itself was clean
(`content OK Sound.wz/Bgm14.img`, 0 drifted, 0 missing); the post-write verifier counts the
pre-existing unreadable image and exits 4. **The `.partial` was deleted, not renamed** — the
procedure says never install one, and leaving a 400 MB plausible-looking file beside the good ones
is a footgun.

Cost: `240080700`/`701`/`800`/`801` play no BGM **in the client**. Cosmetic, non-blocking. The owed
row stays in `Sound.paths.txt` and **was applied to the server XML tree**, so only the client half
is outstanding; a composed install can take it once the verifier learns to ignore an image that was
already unparseable in the target.

> **Fixed by ticket 03f, 2026-08-16.** `VerifyFile` now pre-scans the merge target and discounts,
> **per image**, only those that were already unparseable there — so `BgmGL.img` no longer counts
> as damage this merge caused, while an image that parses in the target and fails in the output
> still fails, which is the corruption the check exists for. Re-run with `06/Sound.paths.txt`
> against the same live base: `added 1 (forced 0), refused 0`,
> `verify: 44 images parsed, 0 unparseable (1 pre-existing, discounted)`, `verified OK`, **exit 0**.
> Output is `D:\games\MapleStory\Server\wz-merge\03f\Sound.wz`,
> SHA-256 `6FF56D43138DDDADAB0FEE24E1DF718EA913134B827E1FBD2CB3CB9A0F41B678`. Discarding the
> `.partial` was still the right call at the time.

## Verification

**§6.2 diff tool**, pre vs post, all five promoted files:

| file | add-list | removed-list | modified-list |
|---|---:|---|---|
| Map | 34 | empty | `Back/dragonRoad.img`, `Obj/dungeon3.img` only |
| Mob | 17 | empty | empty |
| Npc | 6 | empty | empty |
| Reactor | 2 | empty | empty |
| String | 35 | empty | `Map.img`, `Mob.img`, `Npc.img` only |

43,788 images parsed, 0 parse failures. Add-lists are exactly the path lists — no more, no fewer.

**§6.1 content digest**, per direct child, pre vs post, for each of the 5 images inserted into:

- `String.wz/Mob.img` — 17 children differ (the 17 added ids) + `TOTAL`. Every other child identical.
- `String.wz/Npc.img` — 5 children differ (the 5 added ids) + `TOTAL`.
- `Map.wz/Obj/dungeon3.img` — 1 child differs (`skyValley`) + `TOTAL`.
- `String.wz/Map.img` — 1 child differs (`ossyria`, the parent my 13 rows nest under) + `TOTAL`.
- `Map.wz/Back/dragonRoad.img` — 2 children differ (`ani`, `back`) + `TOTAL`.

Added nodes also digest **identical to their v84 source**: `String.wz/Mob.img` children `8300000`,
`8300007`, `9500374`, `9500382` all match, and `Npc.wz/2085000.img` differs from the v84 source in
zero non-`TOTAL` lines.

**§6.3 the gate fires**, both sides:

```
binary: SKIP Reactor.wz/2408005.img (already exists in target) ... added 0, refused 2   exit 5
xml:    SKIP String.wz/Npc.img/2085003 (already exists in wz\String.wz\Npc.img.xml)
                                        ... added 0, refused 35                          exit 5
```

**§5.6 server XML**, all six files, `--deny` passed, `--force` not:

```
wz/Map.wz/Back/dragonRoad.img.xml |  150 +++
wz/Map.wz/Obj/dungeon3.img.xml    | 1112 +++
wz/Sound.wz/Bgm14.img.xml         |    1 +
wz/String.wz/Map.img.xml          |   52 ++
wz/String.wz/Mob.img.xml          |   51 ++
wz/String.wz/Npc.img.xml          |   23 +
```

plus 47 new `.img.xml` files (22 maps, 17 mobs, 6 NPCs, 2 reactors). **0 deletions** anywhere.
The `Sound.wz` XML row **was** applied even though the binary merge could not be promoted: the two
sides are independent, and leaving the server tree disagreeing with the path list would be a silent
gap. Only the client half of `Bgm14/DragonRider` is outstanding.

**§5.8 server-side**, `src/test/java/server/V84CrimsonSkyNodeTest.java`, 12 tests, all green; full
suite 1,910 tests green. It reads the real tree through an explicitly constructed `XMLWZFile` for
the `WZFiles.DIRECTORY` static-init reason the tracer test documents. What it proves:

- all 22 maps parse; `mapMark`, `portal`, `info` present
- the 8 link stubs' `info/link` targets exist in the tree
- every mob and NPC id any in-scope map places has a merged `.img.xml` — and that the set of mobs
  actually placed is exactly `8300000`–`8300006`
- every reactor id any map places is merged
- all 10 `dragonRoad` frames, `dungeon3/skyValley` and `blackTileFly` are present, **and** the v83
  nodes beside them (`dragonRoad back/0`, `dungeon3/dragonValley`) still are
- names read back and none is blank or `MISSING NAME`; `9201144` read "Steward" — **now
  "Shadow Knight Rene"**, see the reversal above
- the drop SQL parses as one well-formed statement, 776 rows, 16 dropperids, exactly one `;` in the
  whole file and no apostrophe in any comment — the two ways the header could split the INSERT
- **every one of the 776 rows exists verbatim under its declared analogue in `152-drop-data.sql`,
  and each dropper's row count equals its analogue's** — subset *and* completeness, so a truncated
  copy fails too
- no quest-gated row was copied from a non-name-matched analogue
- `152-drop-data.sql` contains no row for any of the 17 new dropperids

The suite is not vacuous. It failed for real three times while being written, each time on something
true: `expected <Matada> but was <Matada >` (a genuine trailing space in Nexon's string, carried
through faithfully rather than "fixed"), a group-index bug in its own regex, and — twice — a
semicolon inside the SQL header comment, which is exactly the hazard that assertion exists for.

## The drop tables

`src/main/resources/db/data/153-crimson-sky-drop-data.sql`, **776 rows, 16 dropperids**, registered
as Liquibase changeSet `153` in `src/main/resources/db/changelog-data.xml`. Generator kept beside
the merge output at `D:\games\MapleStory\Server\wz-merge\06-r2\gen-drops.ps1` so the file is
reproducible rather than hand-maintained.

**Why a new file rather than appending to `152-drop-data.sql`.** 152 is a Liquibase `<sqlFile>`
changeSet that has already run on every existing database. Editing it in place changes its checksum
and fails validation at startup. A new changeSet is both the additive answer and the only one that
deploys. `152-drop-data.sql` is byte-untouched, asserted by test.

**Where the rates came from: nowhere new.** Every row is a verbatim copy of a row that already
exists in `152-drop-data.sql` for the closest live-client Leafre mob, with only `dropperid` swapped
— same `itemid`, `minimum_quantity`, `maximum_quantity`, `questid`, `chance`, including the meso
rows (`itemid 0`) and the quest-gated rows. Nothing was scaled, rounded or invented.

Analogues, by name identity first and level/HP second. Names were read from the **live client's**
`String.wz/Mob.img`, not guessed:

| new | name | ← analogue | rows | basis |
|---|---|---|---:|---|
| 8300000 | Soaring Hawk | 8190003 Skelegon | 54 | no name analogue; nearest live Leafre mob by level (both Lv.110) |
| 8300001 | Soaring Eagle | 8190004 Skelosaurus | 52 | no name analogue; nearest by level (110 vs 113) |
| 8300002 | Soaring Red Wyvern | 8150300 Red Wyvern | 45 | name |
| 8300003 | Soaring Blue Wyvern | 8150301 Blue Wyvern | 40 | name |
| 8300004 | Soaring Black Wyvern | 8150302 Dark Wyvern | 51 | name |
| 8300005 | Soaring Griffey | 8180001 Griffey | 75 | name, **and identical maxHP 3,700,000** |
| 8300006 | Dragonica | 8180000 Manon | 63 | no name analogue; the other live Leafre boss |
| 9500374 | Green Cornian | 8150200 Green Cornian | 36 | name, identical exp |
| 9500375 | Dark Cornian | 8150201 Dark Cornian | 49 | name, identical exp |
| 9500376 | Jr. Newtie | 8190000 Jr. Newtie | 44 | name, identical exp |
| 9500377 | Nest Golem | 8190002 Nest Golem | 39 | name |
| 9500378 | Blue Dragon Turtle | 8140700 Blue Dragon Turtle | 32 | name, identical exp |
| 9500379 | Red Dragon Turtle | 8140701 Red Dragon Turtle | 27 | name, identical exp |
| 9500380 | Skelegon | 8190003 Skelegon | 54 | name, identical exp |
| 9500381 | Skelosaurus | 8190004 Skelosaurus | 52 | name, identical exp |
| 9500382 | Leviathan | 8180000 Manon | 63 | no name analogue; nearest live boss |

`8300007` Dragon Rider: no table. Nothing places it and nothing summons it, so nothing can kill it.

**One deliberate deviation from "copy the analogue verbatim", and it is the only one.** 53 of the
copied rows carry a non-zero `questid` — a quest-gated drop. Copying those onto a *name-matched*
clone is right: a Skelegon clone should count toward a Skelegon quest. Copying them onto a mob that
merely shares a level band is not — it would have made Manon's boss quests `7301`/`7303` completable
on **Dragonica and Leviathan at chance 1000000**, i.e. guaranteed, which is a design change nobody
asked for. So: **a `questid != 0` row is copied only where the analogue is a name match.** That
drops 14 rows, 7 each from `8300006` and `9500382`; the other 39 quest rows (Wyverns, Griffey,
Cornians, Dragon Turtles) are all name matches and are kept. Asserted in the test both ways.

The fly maps do **not** block drops — `fieldLimit = 32768` is `0x8000`, which `FieldLimit.java`
does not map to `DROP_LIMIT` (`0x400000`). Checked because dropping items on a map you fly over
would have made the whole table pointless.

## Travel route — the criterion that is blocked

Full portal graph of all 22 maps, from the v84 data:

```
240080000 --left00--> 240030102 "The Forest That Disappeared" (portal right00)
everything else:      no portal to any map outside 240080xxx
```

That is the **only** edge to existing content in the entire area, and it points **outward**. The
rest of the area moves players by script: `info/onUserEnter = Sky_GateMapEnter`, plus the two
"Crimson Sky Doorway" NPCs and `returnMap` chains.

**The far side does not exist, in either vendor's data.** `Map/Map2/240030102.img` in the live v83
client and in v84 are the same five portals — four `sp` and one `out00` to `240030100`. There is no
`right00`. `240030102` appears in neither `add-list/Map.txt` nor `modified-list/Map.txt`: **v84 ships
it unchanged.**

**I checked the one existing Leafre map v84 *did* edit, in case the route hid there.**
`modified-list/Map.txt` carries `Map/Map2/240000000.img` (Leafre town) at 109,629 → 109,701 bytes.
The change is not a route: v84 **adds** `portal/23` (`pn=tp`, `pt=6`, `tm=999999999` — a scripted
town point, no destination), **renumbers** the existing `tp` portal `22` to different coordinates,
and **removes** one `life` entry (23 → 22). Worth recording for whoever merges Leafre later: this
map's `portal` node is a positional array that v84 reindexed, so it is the same hazard class as
`MonsterBook/*/reward` and must not be merged slot-by-slot.

So this is *not* a force-write decision, and `conflicts.txt` is empty by construction rather than by
luck: additive-only never refused anything, because **there is no source node to merge**. The route
needs a hand-authored addition:

```
Map.wz/Map/Map2/240030102.img/portal/5
    pn = right00   pt = 2   x = ?   y = ?   tm = 240080000   tn = left00   script = ""
```

**Why it was not just written.** It has to exist on **both** sides. The server half is a one-node
XML splice, but `WzMerge` only ever *copies from a source `.wz`* — it cannot author a node, and
there is no source. Producing the client half means HaRepacker by hand, which §8 of the procedure
explicitly steers away from. Shipping only the server half would leave the server accepting a portal
the client never draws: strictly worse than a documented gap.

**Owner decision required — three options, in ascending cost:**

1. **NPC-script warp.** Fully server-side, needs no client change at all, and is how GMS actually
   gated this content. Costs a design call this ticket has no mandate for: which Leafre NPC,
   gated on which quest or level. `240030102` has no NPC of its own — only Green and Dark Cornians —
   so it would have to be an NPC in Leafre town.
2. **Hand-author `portal/5` in both trees.** Faithful to the map geometry, and the `x`/`y` can be
   read off `240080000`'s `left00`. Needs HaRepacker on the client `Map.wz`, outside the merge
   pipeline, and re-doing after every future `Map.wz` install.
3. **Leave it GM-only.** `!warp 240080000` works today; everything else in the ticket is delivered.

There is a second gate underneath all three, worth knowing before anyone picks: `240080000` has
`fly = 1` **and `needSkillForFly = 1`**. Without the Dragon Rider flying skill — ticket 05/13
territory — a player who reaches the dock still cannot move through the area.

## Human steps — staged, not performed

I cannot launch the client, walk into the area, or kill anything. Everything below is unverified.

**Before anything: close MapleStory and any HaRepacker window.** All five staged files are large and
Windows will not replace a `.wz` the client holds open.

1. **Install the five staged files.** One at a time, checking size after each.
   ```
   copy D:\games\MapleStory\Server\wz-merge\06-r2\Map.wz      D:\games\MapleStory\Map.wz
   copy D:\games\MapleStory\Server\wz-merge\06-r2\Mob.wz      D:\games\MapleStory\Mob.wz
   copy D:\games\MapleStory\Server\wz-merge\06-r2\Npc.wz      D:\games\MapleStory\Npc.wz
   copy D:\games\MapleStory\Server\wz-merge\06-r2\Reactor.wz  D:\games\MapleStory\Reactor.wz
   copy D:\games\MapleStory\Server\wz-merge\06-r2\String.wz   D:\games\MapleStory\String.wz
   ```
   Expected sizes and SHA-256 are in the "Merge results" table. **`Sound.wz` is not installed** —
   there is no verified output for it.
   **Coordination:** ticket 04 also stages a `String.wz` from the same v83 base. Staged merges from
   the same base do **not** compose — installing both loses one set. Compose from the path lists
   under `docs/wz-baseline/merge-lists/{04,06}/` instead of copying two `String.wz`.
2. **Run the DB migration.** Liquibase changeSet `153` inserts the **776** drop rows (this step
   said 790; the ticket's own acceptance criterion, its verification section and the SQL header
   all say 776 — corrected by ticket 03f). Confirm:
   `SELECT dropperid, COUNT(*) FROM drop_data WHERE dropperid IN (8300000,8300005,9500380,9500382) GROUP BY dropperid;`
   → `8300000`=54, `8300005`=75, `9500380`=54, `9500382`=63.
3. **Reach the area.** No player route exists yet (see "Travel route"). Use `!warp 240080000`.
   - **Pass:** map loads, is named "Crimson Sky Dock"-ish rather than blank, background renders
     (this is what the 10 `dragonRoad` frames are for), Matada and the Crimson Sky Doorway are
     visible and named.
   - **Fail signature to look for specifically:** a map that loads with a **black or missing
     background** means a `Back/dragonRoad` frame did not make it — that is the exact bug the
     pre-03e `deps` would have shipped, so it is the one worth checking hardest.
4. **Walk the area.** `!warp 240080100` … `240080600`. Confirm the Soaring Hawk / Eagle / Wyverns
   spawn and are killable, that Soaring Griffey appears in `240080600`, and that the reactor
   (`2408006`, "damage") in each is present. `fly=1` + `needSkillForFly=1` may block movement
   without the Dragon Rider skill — if so, that is criterion 5's second gate, not a merge fault.
5. **Check drops.** Kill anything in `240080100`–`240080500` and confirm items fall. Then the two
   things this ticket exists for: `!warp 240080600` → Dragonica, and a Skelegon/Leviathan spawn if
   and when a spawner exists.
6. **Rollback, if any of it goes wrong.** All-or-nothing, both sides:
   ```
   copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz D:\games\MapleStory\<Name>.wz
   ```
   for **all five** files, and `git checkout -- wz/` on the server side — **check `git status`
   first**, another ticket's uncommitted XML lives in the same tree.

## What I could not do

- **Launch the game.** Every rendering, spawn-rate, reachability and drop-in-practice claim above is
  staged, not observed.
- **Install `Sound.wz`.** Blocked by the pre-existing `BgmGL.img` MapleLib defect, not by anything
  this ticket did. Four maps will be silent.
- **Deliver the travel route.** Blocked on an owner decision, because neither vendor ships the node
  and the merge tool cannot author one.
- **Make `9500374`–`9500382` spawn.** No map places them and no mob skill summons them. Everything
  else about them is delivered; the spawner is server work, not WZ work.
- **Give the NPCs dialogue.** No Cosmic script exists for `2085000`–`2085003`, `9201144`, `9201145`.
