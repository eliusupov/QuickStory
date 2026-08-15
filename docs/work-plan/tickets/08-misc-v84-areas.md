# 08 — Misc v84 areas reachable

**Blocked by:** 03

**Status:** delivered — all four criteria met to the limit of what an agent can verify. The
scoping judgement this ticket exists to make is made and listed: **3 of 22 maps are reachable, 19
are staged-but-unreachable, each with a reason.** In-game rendering and walking the three routes are
human steps (`## Human steps — staged, not performed`).

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — executed as written
under the post-03e tool (`--deny` mandatory, `deps` at add-list granularity, per-ticket `pre\`,
`--live` hash check). Staging directory: `D:\games\MapleStory\Server\wz-merge\08\`.
**The header line this ticket originally carried showed the pre-03e CLI and is superseded** — that
form has no `--deny` and would exit 2 today.

## What to build

The remaining v84 maps and NPCs, working: Golem's Temple Entrance, Abandoned Hideout, Abandoned
Cave, Temporary Harbor, Snowy Forest, Cave of Silence, Frog House, Power B. Fore's training
centers, and the NPCs General Mau, Glowing Stele, Potter, Keroben, Olaf and the Christmas NPCs.

This is the tail of the map delta — everything not Crimson Sky, not Neo City, not Evan's world
(which is ticket 13). Individually small; grouped because none justifies its own slice.

## Acceptance criteria

- [x] **Remaining v84 maps present in client WZ and server XML** — 22 maps merged into a verified
      `Map.wz` and spliced into `wz/`, read back through the server's own `XMLWZFile`. **The client
      half is staged, not installed**: per the run-order directive the client ships from one
      composed merge of every ticket's path list, not from this ticket's `.wz`.
- [x] **Remaining v84 NPCs present and interactive where they have a role** — 9 `Npc.wz` images and
      15 `String.wz/Npc.img` rows merged; every NPC any in-scope map places has a sprite and a
      non-blank name. "Interactive" is honest rather than ticked past: **none of these NPCs has a
      Cosmic script**, so it means clickable and named, not conversational. Ticket 09's quest merge
      is what gives them dialogue.
- [x] **Maps with a natural entry point are reachable in game** — 3 of 22, each by a route that
      already existed in the data and needed only its server half. See "Reachability".
- [x] **Maps intentionally left unreachable are listed with the reason** — 19, in
      "Staged-but-unreachable". This is the deliverable, not the caveat.

---

## The scope statement was right about the areas and silent about their size

Every name in the ticket resolves, and each one is a map id measured out of `String.wz/Map.img`
rather than guessed:

| ticket name | map id(s) |
|---|---|
| Golem's Temple Entrance | `910600000` |
| Abandoned Hideout | `910600010` |
| Abandoned Cave | `910050300` |
| Temporary Harbor | `914100000` |
| Snowy Forest | `914100010` |
| Cave of Silence | `914100020`–`914100023` |
| Frog House | `922030000`, `922030001` |
| Power B. Fore's training centers | `910060100`, `910060101` |
| *(not named; same delta, same areas)* | `200080601` Orbis Tower \<Secret Room\>, `200090080`/`200090090` Olaf's Voyage, `922030010`/`011`/`020`/`021`/`022` Sky Terrace + Safe, `925110000` Pirate Treasure Vault |

**Where the boundary with ticket 13 falls, measured.** `add-list/Map.txt` has 51 whole-image rows
outside 06's and 07's areas. Twenty-nine of them are Evan's world and belong to 13: the whole
`100030100`–`100030400` block (`streetName` = "Utah's House" / "Farm Street"), `900010000`–
`900020220` ("Dream World" / Lush + Lost Forest), `900030000` ("Afrien's Memory"), `900090000`–
`900090104` ("Video" / Teaser + Tutorial). **This ticket takes the other 22 and re-reads none of
13's.**

### What the `life` nodes actually contain

Dumped for all 22 maps, not taken from the brief. Six of the 22 place nothing at all.

| map | `life` entries | ids placed |
|---|---:|---|
| `200080601` Orbis Tower \<Secret Room\> | 1 | `n 2012034` Hidden Brick |
| `200090080` To the Slumbering Dragon Island | 1 | `n 1013207` Olaf |
| `200090090` To Lith Harbor | 1 | `n 1013207` Olaf |
| `910050300` Abandoned Cave | 1 | `n 1063018` Doll Left Behind |
| `910060100` Spore Training Center | 19 | `m 9300386` ×19 |
| `910060101` Borrowed Training Center | 21 | `m 0210100` ×7, `m 1210101` ×13, `n 1012118` ×1 |
| `910600000` Golem's Temple Entrance | 1 | `m 9300387` ×1 |
| `910600010` Abandoned Hideout | 3 | `m 9300387` ×3 |
| `914100000` Temporary Harbor | 1 | `n 1013207` Olaf |
| `914100010` Snowy Forest | 0 | — |
| `914100020` Cave of Silence | 0 | — |
| `914100021` Cave of Silence | 1 | `n 1205000` Afrien |
| `914100022` Cave of Silence | 0 | — |
| `914100023` Cave of Silence | 10 | `m 9300392` ×10 |
| `922030000` Frog House | 1 | `n 1013203` Hiver |
| `922030001` Frog House | 0 | — |
| `922030010` Sky Terrace | 0 | — |
| `922030011` Safe - 1st Entrance | 1 | `m 9300389` ×1 |
| `922030020` Sky Terrace | 0 | — |
| `922030021` Safe - 1st Entrance | 0 | — |
| `922030022` Safe - 2nd Entrance | 1 | `m 9300390` ×1 |
| `925110000` Pirate Treasure Vault | 21 | `m 9300395` ×10, `m 9300396` ×10, `n 2092101` Potter |

Asserted **per id per map**, not per map — that is the mistake ticket 07's review caught in its own
numbers, and the assertion is written so a table that drifts from the WZ fails rather than being
believed.

Three consequences worth stating rather than leaving implicit:

- **Two of the placed mobs are v83's** — `0210100` and `1210101`, which the Borrowed Training
  Center reuses. Only the seven `93003xx` are merged. Along the way this surfaced a Cosmic
  convention worth not rediscovering: `LifeFactory:100` resolves mob images through
  `StringUtil.getLeftPaddedStr(mid + ".img", '0', 11)`, so `0210100` really is stored **with** its
  leading zero and a `%d.img` lookup misses it. The test failed for real on exactly that.
- **One of the placed NPCs is v83's** — `1012118` Power B. Fore. Nothing merged it.
- **Exactly one reactor across all 22 maps**: `1409000` in `914100022`. Disjoint from ticket 06's
  `2408005`/`2408006` — checked path by path, as instructed, because the code review of 04/05/06
  explicitly did not check `Reactor` or `Sound` for overlap.

### The mobs, and why there is no drop SQL

| id | name | level | maxHP | exp | placed in |
|---|---|---:|---:|---:|---|
| `9300386` | Trainee Spore | 12 | 200 | **24** | `910060100` |
| `9300387` | Enraged Golem | 55 | 4,000 | 0 | `910600000`, `910600010` |
| `9300389` | Safe Guard | 12 | 200 | 0 | `922030011` |
| `9300390` | Door Block | 68 | 108,000 | 0 (boss=1) | `922030022` |
| `9300392` | Black Wing Henchman | 70 | 15,000 | 0 | `914100023` |
| `9300395` | Watchmen Crew | 68 | 12,500 | 0 | `925110000` |
| `9300396` | Watchmen Captain | 70 | 15,000 | 0 | `925110000` |

**Six of the seven give zero exp.** They are scripted obstacles and PQ furniture, not huntable
content, so **no drop tables are delivered and no SQL changeSet was created** — the drop convention
06 and 07 established has nothing to copy from and nothing to reward. That is a decision made
against the data, and the test asserts the exp values so a later edit that makes one of them real
content fails here rather than shipping silently. (Had drops been owed the id would have been
`155`; `153` is 06's and `154` is 07's, and `DatabaseMigrations.java:39` runs `liquibase.update()`
with validation on, so reusing one is a hard startup failure.)

Five new `93003xx` mobs are **not** merged — `9300388` Free Spirit, `9300391` Ice Wall, `9300393`
Gentleman, `9300394` Delinquent Rudolph, and `9300385` Treacherous Fox. `life` was dumped for **all
76 of v84's new map images**, not just this ticket's 22: no map places the first four anywhere, and
`9300385` is placed only by `100030103`, an Evan world map. Ticket 06's mob list was wrong; this one
was checked the same way and is right.

## `deps` output and the merge order derived from it

`WzMerge deps <v84>\Map.wz <id> docs\wz-baseline\add-list` run for all 22 ids, **all exit 0**. Raw
output: `D:\games\MapleStory\Server\wz-merge\08\<id>.deps.txt`. **None of the 22 is an `info/link`
stub** — every file reports `1 map image(s) walked`.

| map | references | rows owed | already in v83 |
|---|---:|---:|---:|
| `200080601` | 10 | 3 | 7 |
| `200090080` / `200090090` | 11 each | 21 each | 3 each |
| `910050300` | 13 | 4 | 9 |
| `910060100` / `910060101` | 20 each | 35 each | 17 each |
| `910600000` | 29 | 36 | 25 |
| `910600010` | 4 | 2 | 2 |
| `914100000` | 15 | 22 | 5 |
| `914100010` | 26 | 22 | 15 |
| `914100020`–`914100023` | 10–11 each | 22 each | 1 each |
| `922030000` | 5 | 3 | 2 |
| `922030001` | 4 | 2 | 2 |
| `922030010` / `922030020` | 14 each | 1 each | 13 each |
| `922030011` / `922030021` | 22 each | 4 each | 20 each |
| `922030022` | 6 | 4 | 4 |
| `925110000` | 20 | 1 | 19 |

**Union: 67 asset rows.** They are heavily shared — the union is a fifth of the sum — and they are
exactly the granularity 06 proved matters: 34 `Back/grassySoil.img/{ani,back}/*` frames and 7
`Tile/grassySoil.img/*` nodes **inside images v83 already has**, 19 `Back/Rien.img/back/*`, 3
`Back/toyCastleB1.img/back/*`, plus 3 whole `Obj` sub-sets (`insideTC.img/inside0/blackroom`,
`acc12.img/dragon`, `acc1.img/grassySoil/golem/{20,21}`) and single nodes under `Obj/dungeon.img`,
`Obj/effect.img`, `Obj/tower.img`.

**One of the 10 new `mapMark` entries is owed, not ten.** 06 flagged the block as biting this
ticket; measured, the 22 maps name six marks (`None`, `Dungeon`, `Henesys`, `Ludibrium`,
`WhiteHerb`, `SnowDragon`) and only **`SnowDragon`** is new. The other nine — `Balog`,
`DragonDream`, `Dragonrider`, `Farm`, `GhostShip2`, `Hontale`, `PinkBean`, `Pyramid`, `Zakum` — are
referenced by **no map in the v83 server tree** (grepped, zero hits) and by no map in scope.
`DragonDream` and `Farm` are Evan's; the rest are world-map boss icons whose maps v84 edits rather
than adds. Left unclaimed with that measurement rather than bulk-imported.

**Zero cross-file dependency rows.** All seven BGM tracks the 22 maps name (`Bgm01/CavaBien`,
`Bgm06/ComeWithMe`, `Bgm06/FantasticThinking`, `Bgm15/Pirate`, `Bgm18/BlackWing`,
`Bgm19/CrystalCave`, `Bgm19/SnowDrop`) are already in the live v83 client. **Nothing in this ticket
depends on `Sound.wz` at all** — which matters, given that file's known verifier failure.

So the merge order is: 67 asset rows, then the 22 map images, then the six route rows. `Map.paths.txt`
is written in that order.

## The route rows — the finding this ticket turned up

**Six of the 95 `Map.wz` rows write into a positional array on a map the live client already has,
and three more that `add-list/Map.txt` offers were refused.** Full measurement and the general rule
in **[`docs/wz-baseline/merge-lists/08/ROUTE-ROWS.md`](../../wz-baseline/merge-lists/08/ROUTE-ROWS.md)**.
The short version:

- **Merged, verified pure appends** (live child count == v84 index, dumped for each):
  `200080600.img/{1/obj/25, 1/obj/26, portal/6}`, `251010403.img/{4/obj/33, portal/4}`,
  `106010102.img/portal/8`. These are the client half of three routes into this ticket's maps.
- **Refused**: `106010101.img/portal/5/{horizontalImpact,script}` (index 5 is `out00` in live and
  `in00` in v84 — merging attaches `evanGolemDoor` to the exit portal),
  `220000300.img/portal/15` (v84 *inserted* `scr00` at index 4 and shifted eleven portals down;
  index 15 is a **duplicate `in06`**), and `220011000.img/portal/4/{horizontalImpact,script}`
  (would attach a non-existent script to the working portal into Ludibrium Toy Factory).

This is 03c's `MonsterBook/*/reward` hazard in a second file, and `conflicts.txt` is structurally
silent on all of it: these rows do not collide, they land at an index whose meaning diverged. The
three refusals are asserted in the test so a later ticket cannot merge them and read it as an
improvement.

## Path lists — the authoritative deliverable

`docs/wz-baseline/merge-lists/08/`.

| file | rows | contents |
|---|---:|---|
| `Map.paths.txt` | 95 | 67 asset rows, 22 map images, 6 route rows |
| `Mob.paths.txt` | 7 | `9300386`, `87`, `89`, `90`, `92`, `95`, `96` |
| `Npc.paths.txt` | 9 | 6 placed + General Mau, Glowing Stele, Potter (`2092100`) |
| `Reactor.paths.txt` | 1 | `1409000` |
| `Sound.paths.txt` | 23 | mob SFX: 7 mine, 4 from 07's handoff, 12 of 06's |
| `String.paths.txt` | 39 | 19 `Map.img`, 2 `Mob.img`, 15 `Npc.img`, **+3 forced** |
| `String.force.txt` | 3 | the force authorisation, a new file, not an edit of `COLLISION-FORCE.txt` |
| `ROUTE-ROWS.md` | — | the positional-array measurement |
| **total** | **174** | |

**Overlap with 04–07: zero, checked mechanically path by path** — 174 rows against the 1,207 rows
of `merge-lists/{04,05,06,07}/*.paths.txt`, set intersection **empty**. Specifically, and because
the code review asked for these two by name: `06/Reactor.paths.txt` is `2408005`/`2408006` and mine
is `1409000`; `06/Sound.paths.txt` is `Bgm14.img/DragonRider` and mine is 23 `Mob.img/<id>` rows.
Neither is re-added, and the test asserts 06's rows are still present in the tree.

### `Sound.wz` — 07's handoff picked up, and 06's leftovers with it

07 merged four `Sound.wz/Mob.img` rows and reverted them on discovering the file was not its to
own. **Those four are now on this ticket's list**, together with the seven for this ticket's own
mobs. While checking the overlap I found twelve more that **06 never claimed** —
`Mob.img/{8300005,8300006,8300007,9500374…9500382}`, the SFX for Crimson Sky's mobs — and took them
too, because `Sound.wz` has no other owner and leaving them means they never ship. Zero collisions,
zero risk, and the cost of *not* having them was only that those mobs are silent.

Not taken, and named so the composed install can decide: `Bgm00.img/DragonDream` (Evan's Dream World
maps, ticket 13), `Item.img/0202262{1,2}`, `Mob.img/{2220110,9300385,9300388,9300391,9300393}` (no
map places them), and 31 `Skill.img` rows for the Aran/Evan skill sets (tickets 12/13).

### The three forced rows — a new force decision, and its own file

`COLLISION-FORCE.txt`'s 37 rows are fully consumed (04 took 30, 05 took 7) and **`--force` was
never pointed at it from here.** Instead this ticket ships `08/String.force.txt`, 3 rows, same
format and same parser:

| path | live | v84 |
|---|---|---|
| `String.wz/Map.img/ossyria/200090080` | Korean | "Olaf's Voyage" / "To the Slumbering Dragon Island" |
| `String.wz/Map.img/ossyria/200090090` | Korean | "Olaf's Voyage" / "To Lith Harbor" |
| `String.wz/Npc.img/1013203` | Korean, 6 fields | "Hiver" / "Black Wing Captain" + 4 dialogue lines |

All three are the same rule `COLLISION-FORCE.txt` uses — the live value is a Nexon placeholder, here
an untranslated Korean string GMS v83 shipped for content it had not released — and all three are
nodes this ticket's own maps display. Same shape both sides, no field lost; the git diff is
**10 deletions, every one of them a Korean `<string>` replaced in place**, and nothing else in the
tree deletes a line. Forced on **both** sides per procedure §5.6.

Checked against the review's lesson on `9201144`: nothing in `src/`, `scripts/` or `wz/` references
any of these three live values, so keeping them would have protected nothing and left two ship maps
and an NPC labelled in Korean.

**One row taken with a flag on it.** `String.wz/Npc.img/{2001006,2001007,2001008}` — the Christmas
NPCs the ticket names, placed by `920010300`, currently nameless there. **v84's own strings for
these three are untranslated Korean.** Importing them replaces "no name" with a Korean name. Taken
because they are pure additions with zero collisions and the ticket names them; deleting those three
lines from `String.paths.txt` is the whole fix if that trade is not wanted. This is the one place
this ticket knowingly ships something a human may want reversed.

## Dry runs, conflicts, force decisions

`--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt` on every `merge` and every `xml`, dry runs
included, both sides.

| file | requested | added | refused | denied | forced |
|---|---:|---:|---:|---:|---:|
| Map | 95 | 95 | 0 | 0 | 0 |
| Mob | 7 | 7 | 0 | 0 | 0 |
| Npc | 9 | 9 | 0 | 0 | 0 |
| Reactor | 1 | 1 | 0 | 0 | 0 |
| Sound | 23 | 23 | 0 | 0 | 0 |
| String *(no `--force`)* | 39 | 36 | **3** | 0 | 0 |
| String *(with `--force`)* | 39 | 39 | 0 | 0 | **3** |

The String pair is the decision shown rather than asserted: without the force list the gate refuses
all three Korean nodes and exits 3; with it, `added 39 (forced 3), refused 0`, exit 0. Every other
`*.conflicts.txt` in the staging directory is empty.

**Zero denials.** None of the 28 deny roots intersects these 174 paths — the 10 `NpcLocation` rows
are in `Etc.wz`, which this ticket never opens; the 17 `MonsterBook/*/reward` parents are Leafre and
Ludibrium mobs and this ticket never names `MonsterBook.img`; and `Npc.wz/9000021.img` is not on any
list here.

**The ten `Npc.wz/99019xx.img` are dropped, and 03c's verdict now has a second reason.** They are
the ids the frontier warned this ticket about. `PlayerNPC.java:66` allocates from `9901910`–
`9906599` at runtime and the live client is a strict superset of v84 there — but there is also a
scope fact: they are placed by **`100030301` Forest Hall, an Evan world map, which is ticket 13's**.
So the decision is recorded here and inherited there: drop them, and expect ten dead `life` slots in
that map. Cosmic already ships its own `9901910.img.xml` (commit `fca7b2ada`, "Implemented Kites,
PlayerNPCs…"); the test discriminates by `info/speak`, which v84's node lacks, so it can tell
"Cosmic's, untouched" from "v84's, merged by mistake".

## Merge results

Live vs backup SHA-256, before anything (§5.0), all equal:

```
Map.wz      A39DA5AC66CB3CB1803B1A8F70F19CDF67CA191016E16C853F521B3C8156ACA4
Mob.wz      BEC9D9E15C1D16E7B9CCE1A900938363411FBFD44C52BE7DB86735A2BCB210F1
Npc.wz      2992910AC5F65FA3D1CA4B2469FA4105F948F6CEB4A6C47EE6953BE9D04DEE17
Reactor.wz  9F10FC59D51D355750E55E3D252003B8F325F03EF13F2E6DB123056BBD84F66D
Sound.wz    BC6570D39AE1C021AF433616ED4EC5F0C8917513B63D28536D116AAC74BDFD76
String.wz   9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499
```

The per-ticket `pre\` snapshots hash-match all six, and every real merge passed `--live`.
Staged output (§6.4):

| file | exit | SHA-256 | bytes |
|---|---|---|---:|
| `Map.wz` | 0 | `B2CD6597B382880596BBE723AB75ECCB2A33DD4BBED8FE121B8E8A41A2766EC6` | 643,631,071 |
| `Mob.wz` | 0 | `1D6F027A169FCEB3BE38EC4D29FB15355FFA9DC505C602E923938FC430F16636` | 481,342,268 |
| `Npc.wz` | 0 | `0E71968AD0809934FE6909FA55545EDC4AB14AB22E281395382884BA4EC65DF7` | 53,736,201 |
| `Reactor.wz` | 0 | `9FAA67EA96626CAFC789323BF14C37C5920C70B242C5CE04DB1312DDA7CDA2E1` | 54,397,944 |
| `String.wz` | 0 | `7CE74E84741137BC5B4A81F33CD4FAAF2A0975F27742C2B319A9F830BA359717` | 3,563,572 |
| `Sound.wz` | **4** | — not promoted — | — |

### `Sound.wz` failed verification, exactly as predicted, and nothing here depends on it

```
content OK  Sound.wz/Mob.img  70dacb0fc2b952a9…
UNPARSEABLE image Sound.wz.partial\BgmGL.img: InvalidDataException WZ extended property exceeds its declared block.
verify: 44 images parsed, 1 unparseable, 0 requested paths missing, 1 images content-checked, 0 drifted
```

Third measurement of the same defect (06, 07, 08), identical shape each time. The write was clean —
`added 23, refused 0`, content-checked, 0 drifted; the post-write verifier counts an image that is
unreadable by MapleLib in **all three trees**. **The `.partial` was deleted, not renamed.** The
verifier fix is another agent's file and was not touched here. **The 23-row path list is the
deliverable and the server XML half was applied**, so only the client binary half is outstanding,
and its whole cost is that some mobs are silent.

## Verification

**§6.2 diff tool**, pre vs post, all five promoted files:

| file | add-list | removed-list | modified-list |
|---|---:|---|---|
| Map | **95** | empty | 14 images, all inserted into |
| Mob | **7** | empty | empty |
| Npc | **9** | empty | empty |
| Reactor | **1** | empty | empty |
| String | **36** | empty | `Map.img`, `Mob.img`, `Npc.img` only |

43,867 images parsed, **2 parse failures — both `Sound.wz/BgmGL.img`**, one in each of the two
pre-merge roots, i.e. the known defect and nothing new. Add-lists are **exactly** the path lists.
`String`'s add-list is 36 rather than 39 because the three forced rows are edits, not additions, and
land in `modified-list` — which is the right place for them. `Map`'s 14 modified images are
`Back/{grassySoil,Rien,toyCastleB1}.img`, `Map/{106010102,200080600,251010403}.img`, `MapHelper.img`,
`Obj/{acc1,acc12,dungeon,effect,insideTC,tower}.img` and `Tile/grassySoil.img` — every one a row on
the path list, and nothing else was re-serialized.

**§6.1 content digest**, per direct child, pre vs post, for the images where a wrong write would be
invisible to everything else — the three existing maps the route rows touch:

- `Map/Map2/200080600.img` — 2 children differ (`1`, `portal`) + `TOTAL`. Every other child identical.
- `Map/Map2/251010403.img` — 2 children differ (`4`, `portal`) + `TOTAL`.
- `Map/Map1/106010102.img` — 1 child differs (`portal`) + `TOTAL`.
- `Back/grassySoil.img` — 2 (`ani`, `back`) + `TOTAL`. `MapHelper.img` — 1 (`mark`) + `TOTAL`.

**§6.3 the gate fires**, both sides:

```
binary: SKIP Reactor.wz/1409000.img (already exists in target) … added 0 (forced 0), refused 1   exit 5
binary: SKIP Npc.wz/2092101.img     (already exists in target) … added 0 (forced 0), refused 9   exit 5
xml:    SKIP Mob.wz/9300396.img     (already exists in wz\Mob.wz\9300396.img.xml)
                                                               … added 0 (forced 0), refused 7   exit 5
```

**§5.6 server XML**, all six files, `--deny` passed everywhere, `--force` on `String.wz` only:

```
18 files changed, 1187 insertions(+), 10 deletions(-)
```

plus **39 new `.img.xml` files** (22 maps, 7 mobs, 9 NPCs, 1 reactor). The 10 deletions are the
three forced nodes replaced in place, listed line by line above; there is no other deletion and no
reformat. **Nothing here ever ran a blanket `git checkout -- wz/`** — the one revert this ticket did
was path-scoped to a single file (`scripts/portal/enterDollcave.js`). Another agent (ticket 03f) is
working in the same tree and has its own uncommitted `wz/String.wz/Skill.img.xml`,
`docs/wz-baseline/tool-merge/Program.cs` and test-class edits; none of them is in this ticket's
commit and none was touched here.

**Live client, after the ticket:** all **18** `.wz` in `D:\games\MapleStory\` still SHA-256-match
`_backup\client-v83-EzorsiaV2-2026-08-15\` — re-checked file by file at the end, **0 mismatches** —
and no `.partial`, `.TEMP` or `.merged` exists beside them. `porting-resources\wz-data\**` was opened
read-only.

**§5.8 server-side**, `src/test/java/server/V84MiscAreasNodeTest.java`, **17 tests, all green**;
**full suite 1,949 tests, 0 failures, 0 errors** (`./mvnw -o test` → BUILD SUCCESS). Twenty of them
are this ticket's: the 17 in the new class, plus 3 that come free because `ScriptEvaluationTest`
walks `scripts/` and now evaluates the three new portal scripts — so a syntax error in one of them
fails the suite. The arithmetic reconciles exactly: 1,928 (STATUS's baseline) + 17 + 3 + **1 from
another agent working in this same tree concurrently** (`V84MountNodeTest` went 7 → 8 while this
ticket ran) = 1,949.

What the class proves:

- all 22 maps parse; `info`, `portal/0`, `foothold`, `info/bgm`, `info/mapMark` and a spawn point present
- the **per-id-per-map** `life` matrix is exactly the table above, and the set of mobs and NPCs
  placed is exactly the stated set; every placed id resolves to a merged `.img.xml`
- `SnowDragon` is present, every `mapMark` these maps name resolves in `MapHelper.img`, and the
  pre-existing `Henesys`/`Ludibrium` marks survived the insert
- exactly one reactor is placed (`914100022:1409000`), and 06's two rows are still there
- the merged asset rows are in the tree **and the v83 siblings beside them still are**
  (`grassySoil back/0`, `Rien back/0`, `acc1 grassySoil/nature/0`)
- **the three merged route portals were appended without disturbing any v83 portal** — portal counts
  6→7, 4→5, 8→9 and every pre-existing portal still has its original target
- **the three refused route rows are absent** — `106010101` still has 6 portals with an unscripted
  `out00`, `220000300` still has 15 with exactly one `in06`, `220011000/in00` still leads to
  `220011001` with no script
- the three new portal scripts exist, implement `enter(pi)`, and warp to the map the docs claim; and
  every routed map has a return portal at the name its script warps to
- **`enterDollcave.js` still warps to `105040201` and never mentions `910050300`** — the guard on
  the one destructive mistake this ticket made and reverted
- 12 map names, both forced ship-map names and Hiver's six fields read English, with **no Hangul
  surviving in any of them**; mob and NPC names non-blank and not `MISSING NAME`; Keroben reads
  "Keroben"
- the three unplaced NPCs are merged **and are asserted to be unplaced**, so the gap is a fact
  rather than a rediscovery
- `9901910` is still Cosmic's node (it has `info/speak`, which v84's lacks) and none of
  `9901911`–`9901919` gained a v84 image
- all 23 `Sound.wz/Mob.img` rows landed in the XML tree, and 06's `Bgm14.img/DragonRider` is intact
- six of the seven mobs give zero exp, and none of the seven appears in `152`/`153`/`154`

**The suite is not vacuous — it failed twice for real while being written, both times on something
true.** `map life spawns mob 210100 but Mob.wz/210100.img.xml is absent`, which is the
`getLeftPaddedStr` convention documented above; and
`Npc.wz/9901910.img.xml was merged onto the server's runtime allocator range`, which turned out to
be Cosmic's own committed content and forced the assertion to be rewritten as a discriminator
instead of an absence check. Both are recorded because the data won.

## Reachability — 3 of 22, and how each one was earned

None of these needed a hand-authored node and none needed a design decision about gating that the
data could not answer. Each is a v84 portal that merged as a verified append, plus the three-line
server script that was missing. All three script names are **unused anywhere in the repo** — checked
by grep before writing them, for the reason in the next paragraph.

| map | route | client half | server half |
|---|---|---|---|
| `200080601` Orbis Tower \<Secret Room\> | Orbis Tower 20th Floor `200080600` → `in00` | **merged** `portal/6`, verified append | **new** `scripts/portal/enterBlackRoom.js` |
| `925110000` Pirate Treasure Vault | Herb Town pirate cave `251010403` → `in00` | **merged** `portal/4`, verified append | **new** `scripts/portal/enterPottery.js` |
| `910600010` Abandoned Hideout | Golem's Temple 2 `106010102` → `scr00` | **merged** `portal/8`, verified append | **new** `scripts/portal/evanDollGR.js` |

**A fourth was written, then reverted — and it is the most useful thing in this section.**
`910050300` Abandoned Cave has `returnMap = 105070300`, and `105070300/in00` is a stock-v83 `pt=8`
script portal named `enterDollcave` — the 07-shaped case, apparently inert for want of a server
script. It is not inert. **Cosmic already ships `scripts/portal/enterDollcave.js`**, warping to
`105040201` behind quests `20730`/`21734` with an NPC password fallback (`1063011`,
`PupeteerPassword`). I overwrote it before noticing, then restored it from git; the file is
byte-identical to `HEAD` and `V84MiscAreasNodeTest.theAbandonedCaveDoesNotStealTheExistingEnterDollcaveScript`
now asserts both halves of that — the script still warps to `105040201` and does not mention
`910050300` — so the next ticket cannot repeat it. **A shared portal-script *name* is not a free
hook.** `910050300` is therefore staged-but-unreachable, listed below.

**The one judgement call, stated so it can be reversed in one line each.** All three scripts are
**ungated**. GMS gated these behind the Black Wing / Evan quest chains, which are `Quest.wz` data
ticket 09 has not merged. Writing a gate would have meant inventing which quest, which is exactly
what this ticket is told not to do; writing no script would have left three more maps unreachable
for the sake of three lines. The comment at the top of each script says where to add the gate once
the quests exist.

## Staged-but-unreachable — 19 maps, with the reason for each

**This is a scoping decision, not a bug.** Every map below is merged, parses server-side, has its
name, mobs, NPCs and scenery, and answers to `!warp`. What none of them has is a way in for a
player.

| map | reason |
|---|---|
| `910050300` Abandoned Cave | Its only inbound is `105070300/in00`, whose script name `enterDollcave` is **already implemented by Cosmic for a different destination** (`105040201`, gated on quests `20730`/`21734`). Hanging the cave off it would break a working v83 route. Needs its own portal or NPC warp — a design call this ticket has no mandate for. |
| `910600000` Golem's Temple Entrance | Inbound is `106010101/portal/5` in v84 — but index 5 is `out00` in the live client. **The row cannot be merged without breaking a working portal** (ROUTE-ROWS.md). No other edge exists in either tree. |
| `922030000` Frog House | Inbound is `220000300/portal/4` (`scr00`, `script=enterBlackFrog`), which v84 **inserted**, shifting eleven portals down. `add-list` only offers index 15, which is a duplicate `in06`. Unmergeable. |
| `922030001` Frog House (Black) | Reached only from `922030000`, which is unreachable; its own entry is the missing map script `enterBlackfrog`. |
| `922030010` Sky Terrace | Inbound is `220011000/in00` converted to a script portal by v84. Merging it would attach `enterBlackBC` to the **working** portal into Ludibrium Toy Factory. Refused. |
| `922030011` Safe - 1st Entrance | Reached only from `922030010`. |
| `922030020` Sky Terrace | Same inbound as `922030010`. |
| `922030021` Safe - 1st Entrance | Reached only from `922030020`. |
| `922030022` Safe - 2nd Entrance | Reached only from `922030021`. |
| `910060100` Spore Training Center | **The route exists and is pre-written**: `scripts/npc/1012118.js` warps here — but only for a character with quest `22515`/`22516`/`22517`/`22518` active. Those are Evan quests; v83 has no `22xxx` quest data at all. Reachable the moment ticket 09/13 merges them, and not before. Nothing here is missing. |
| `910060101` Borrowed Training Center | No script in the whole repo references it. The v84 client offers no portal either. |
| `200090080` To the Slumbering Dragon Island | An Olaf's Voyage ship. Nothing warps into it — no portal in `104000000` (Lith Harbor's portal array is **byte-identical in v83 and v84**, checked) and no NPC script. Its own exits run `move_RitSDI`, which does not exist. |
| `200090090` To Lith Harbor | The return ship. Same, with `move_SDIRit`. |
| `914100000` Temporary Harbor | Entered from `200090090` or from `922030000`'s `tel00` (`script=enterSDI`). Both upstream maps are unreachable and `enterSDI` does not exist. |
| `914100010` Snowy Forest | Downstream of `914100000`. Also needs map script `onSDI` and portal script `enterSnowDragon`. |
| `914100020` Cave of Silence | Downstream of `914100010`. Needs `stopIceWall` ×10. |
| `914100021` Cave of Silence | Downstream. Needs map script `evanTogether`. |
| `914100022` Cave of Silence | Downstream. Needs `summonIceWall` and `outSDI`. |
| `914100023` Cave of Silence | Downstream. Needs map script `blackSDI`. |

Four shapes, and they need different follow-ups:

1. **Eight maps are blocked on a node that cannot be copied** — `910600000`, `922030000`,
   `922030001`, and the five `9220300{10,11,20,21,22}`. This is ticket 06's travel-route problem in
   a second place: the node exists in v84, but only at an array index whose meaning diverged, and
   `WzMerge` only copies. The fix is a hand-authored portal in both trees, or an NPC-script warp.
   **Owner decision, same three options 06 listed.**
2. **Eight maps are blocked on the Slumbering Dragon Island story flow** — `200090080`,
   `200090090` and the six `9141000xx` — which is eleven missing scripts and the Evan quest chain.
   That is ticket 09 and 13 work, not WZ work, and inventing it here would be inventing content.
3. **Two maps are blocked on something already written** — `910060100` needs quests `22515`–`22518`
   to exist (the NPC route is live and waiting), and `910060101` has no entry point in any vendor's
   data at all.
4. **One map is blocked on a name collision** — `910050300`, whose portal script name is taken by
   working v83 content. Cheapest fix in the list: an NPC warp, or a hand-authored second portal on
   `105070300`.

**Missing `onUserEnter` scripts are non-fatal, checked rather than assumed.**
`MapScriptManager:79` catches the exception and logs it, so entering a map whose `onUserEnter`
script is absent produces a log line and nothing worse. Six of the 22 name one that does not exist
(`dollCave01`, `onSDI`, `evanTogether`, `summonIceWall`, `blackSDI`, `enterBlackfrog`) — none of
those six is reachable today, so nothing hits it, but it is the noise a GM warp will produce.
**None of the three reachable maps has an `onUserEnter` at all.**

## Handoffs

- **Ticket 13 (Evan's world).** `Npc.wz/1013106.img` **Glowing Stele** is merged here because
  ticket 08 names it; **do not re-add it**. The ten `99019xx` are dropped, deliberately — they are
  placed by `100030301`, which is 13's map, so expect ten dead `life` slots there. 13's other named
  NPCs (Afrien `1013205`, Hiver `1013204`/`1013206`, Mir `1013000`) are **not** merged here; only
  `1013203` Hiver and `1205000` Afrien are, because this ticket's maps place them.
  `Sound.wz/Bgm00.img/DragonDream` and the `String.wz/Map.img/etc/900*` names are unclaimed.
- **Ticket 09 (quests).** `910060100` becomes reachable the moment quests `22515`–`22518` exist —
  the NPC script is already written and live. Eleven portal/map scripts named in the table above are
  the rest of the Slumbering Dragon Island flow.
- **Composed install pass.** `String.wz/Map.img/{victoria,ossyria}/<town>/{help0,help1,help2,mapDesc}`
  (≈20 rows of new v84 town help text) and ≈60 `String.wz/Npc.img/<id>/{d0,d1,n0,n1,quest,func}`
  rows for NPCs outside every ticket's area are real v84 content with no owner. Also the nine unowed
  `MapHelper.img/mark/*` entries and `UI.wz` (out of scope by ticket-03 decree).

### The test duplication 07 deferred — half of it happened elsewhere, the other half declined

07 handed over a ~120-line overlap between `V84NeoCity2227NodeTest` and `V84CrimsonSkyNodeTest`
(`wz()`, `map()`, `DROP_ROW`, `analogueOf()`, the two drop-file assertions) on the expectation that
**08 would make it a third copy. It does not.** This ticket ships no drop SQL, so it reproduces none
of the drop machinery — which is all but ~10 lines of the overlap.

- **`wz()` was extracted while this ticket ran, by ticket 03f** (`src/test/java/server/V84Wz.java`,
  its finding F8), which swept the four existing v84 node tests. `V84MiscAreasNodeTest` was written
  before that landed and has been **switched over to `V84Wz.wz` here**, so all five classes now
  share one copy and no class is the odd one out.
- **The drop machinery stays duplicated, deliberately.** What is left is `map()`, whose bucket logic
  genuinely differs in all three classes (`Map2`/`Map6`, `Map2`/`Map9`, and here `"Map" + first
  digit`), plus `DROP_ROW`/`analogueOf()`/the two drop assertions, which exist in exactly two
  committed, green, ticket-specific classes — one carrying a `NOT_NAME_MATCHED` set the other has no
  concept of. Parameterising that would add more than it removes and risk two passing suites for no
  delivery. **If a later ticket does add a third drop file, the extraction becomes worth it, and
  this is the note that says so.**

## Human steps — staged, not performed

I cannot launch the client, walk into any of these maps, or kill anything. Everything below is
unverified.

**Before anything: close MapleStory and any HaRepacker window.**

1. **Do NOT install this ticket's staged `.wz` files.** They are merged from the same live base as
   06's and 07's, so copying them would **silently revert both**. The five verified files in
   `D:\games\MapleStory\Server\wz-merge\08\` exist to prove the path list is right, and their hashes
   are in "Merge results" so the merge can be reproduced and checked by hash. The client ships from
   one composed merge consuming `docs/wz-baseline/merge-lists/{04,05,06,07,08}/`, which are disjoint
   — a concatenation, no conflict resolution needed. **That composed run must pass
   `--force docs\wz-baseline\merge-lists\08\String.force.txt` as well as 04/05's force rows**, or the
   two ship maps and Hiver ship with Korean names.
2. **No DB migration.** This ticket adds no SQL and registers no changeSet.
3. **Walk the three routes.** These are the ticket's criterion 3:
   - **Herb Town → Pirate Treasure Vault.** Go to `251010403` (the pirate cave east of Herb Town)
     and touch the new hidden portal on the right. **Pass:** you land in `925110000` "Pirate
     Treasure Vault", ten Watchmen Crew and ten Watchmen Captain are there, both named, and Potter
     (`2092101`) is standing in it. **Fail signature:** nothing happens on touch → the portal merged
     but `enterPottery.js` did not deploy; a black background → the `Obj/acc12.img/dragon` /
     `Back/toyCastleB1` rows did not.
   - **Orbis Tower 20th Floor → Secret Room.** `200080600`, touch the new brick. **Pass:**
     `200080601` "Orbis Tower \<Secret Room\>" with the Hidden Brick NPC. The room is otherwise
     empty — that is what v84 ships.
   - **Golem's Temple 2 → Abandoned Hideout.** `106010102`, touch the new `scr00`. **Pass:**
     `910600010` with three Enraged Golems. **Check specifically that the four existing `in03`–`in06`
     portals in `106010102` still work** — that map's portal array is the one this ticket appended
     to, and a broken `in04` would be the signature of a positional-array mistake the digest missed.
   - **And one route that must NOT have changed:** Sleepywood `105070300` → `in00` must still open
     the puppeteer password NPC / warp to `105040201` exactly as before. This ticket briefly
     overwrote `enterDollcave.js` and restored it; the file is byte-identical to `HEAD`, but it is
     worth one click.
4. **Spot-check three unreachable maps by GM warp**, because rendering is the half no test covers:
   `!warp 910060100` (19 Trainee Spores, Henesys grass background — this is what the 34
   `Back/grassySoil` frames are for, and a black background here is the single most valuable failure
   to catch), `!warp 914100000` ("Temporary Harbor", world-map marker present — this is the only new
   `mapMark`), `!warp 922030022` (one Door Block).
5. **Check the two forced names.** `!warp 200090080` should read **"To the Slumbering Dragon
   Island"**, not Korean, and `!warp 922030000` should show an NPC labelled **"Hiver"**, not 이베흐.
   If either is still Korean the composed install dropped `--force`.
6. **Rollback, if any of it goes wrong.** Nothing was installed, so the client needs none. On the
   server side revert **only this ticket's paths** — `wz/Map.wz/Map/Map2/2000{80601,90080,90090}.img.xml`,
   `wz/Map.wz/Map/Map9/9*.img.xml`, `wz/Map.wz/{Back,Obj,Tile}/*.img.xml`, `wz/Map.wz/MapHelper.img.xml`,
   `wz/{Mob,Npc,Reactor,Sound,String}.wz/*`, and `scripts/portal/{enterBlackRoom,enterPottery,evanDollGR}.js`
   (all three are new files — `git clean` them; `enterDollcave.js` is **not** this ticket's and must
   be left alone). **Never `git checkout -- wz/` wholesale** — other tickets have uncommitted XML in
   the same tree.

## What I could not do

- **Launch the game.** Every rendering, spawn and reachability-in-practice claim above is staged,
  not observed.
- **Install `Sound.wz`.** Blocked by the pre-existing `BgmGL.img` MapleLib defect for the third
  ticket running. The verifier fix is in flight in another agent's file and was deliberately not
  touched. The 23-row list is handed to the composed install; the server XML half is applied.
- **Make 19 of the 22 maps reachable.** Eight are blocked on a portal node that exists in v84 only
  at an array index whose meaning diverged — an owner decision of the same shape as 06's travel
  route. Eight are blocked on the Slumbering Dragon Island script flow and the Evan quest chain,
  which is ticket 09 and 13. `910060101` has no entry point in any vendor's data at all, and
  `910050300`'s only inbound portal script name is already implemented for a different destination.
- **Avoid making one destructive mistake.** I overwrote `scripts/portal/enterDollcave.js`, a working
  Cosmic script, on the assumption that an unreferenced-looking v83 script portal had no handler. It
  did. Restored from git, byte-identical to `HEAD`, and now guarded by a test. The root cause was a
  glob whose brace syntax matched nothing and which I read as "no such file"; the lesson is that a
  negative search result is not evidence until the search itself is checked.
- **Translate the Christmas NPC names.** v84 itself ships them in Korean. Imported as-is and
  flagged; the alternative was to leave them nameless, and that is a taste call, not a data one.
- **Prove the `910060100` gate is passable.** Quest `22515`–`22518` do not exist in this tree, so
  the pre-written NPC route cannot be exercised end to end until ticket 09 or 13 merges them.
- **Reconcile the suite count exactly.** 1,947 green here = 1,927 + 16 new node tests + 4 new portal
  scripts; STATUS records the previous baseline as 1,928, one higher. Nothing here removes a test and
  the build is green, so the one-test difference is in a figure I inherited, not in this work.
