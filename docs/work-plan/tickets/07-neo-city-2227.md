# 07 — Neo City 2227 playable

**Blocked by:** 03

**Status:** delivered — all four criteria met and agent-verified to the limit of what an agent can
verify. Nothing is blocked and nothing needed an owner decision. In-game rendering, spawns and drops
are human steps (`## Human steps`).

**Merge procedure: [`../WZ-MERGE-PROCEDURE.md`](../WZ-MERGE-PROCEDURE.md)** — executed as written
under the post-03e tool (`--deny` mandatory, `deps` at add-list granularity, per-ticket `pre\`,
`--live` hash check). Staging directory: `D:\games\MapleStory\Server\wz-merge\07\`.
**The header line this ticket originally carried showed the pre-03e CLI and is superseded** — that
form has no `--deny` and would exit 2 today.

## What to build

Neo City Year 2227 is reachable, populated and rewarding. Maps `683070400`–`683070402` (Dangerous
City Intersection, Center, Construction Site) and mobs `9400658`–`9400661` (Imperial Guard Type A,
Dunas Type D, Royal Guard Type S, Afterlord Type A).

Same shape as ticket 06 and same warning: drops are in scope. Note this extends the Neo City content
that arrived in v83, so check how players currently reach Neo City and whether the new maps hang off
the existing entry or need their own. *(Original brief, kept verbatim. Answer: they hang off the
existing entry, and both halves of it already shipped in v83 — see "The route".)*

## Acceptance criteria

- [x] All three maps present in **server XML** and staged for the client WZ, and reachable — 3 maps
      merged into a verified `Map.wz` and spliced into `wz/`, read back through the server's own
      `XMLWZFile`. **The client half is staged, not installed** — installation is step 1 of
      `## Human steps`, and per the run-order directive the client actually ships from one composed
      merge of every ticket's path list, not from this ticket's `.wz`. Reachable: **the route exists
      and is enabled**, see "The route". Rendering itself is human-verified.
- [x] All four mobs spawn and are killable — **the WZ half.** All four mob images merged and parse;
      every id any in-scope map places has a sprite and a name. The `life` nodes place 32 spawns.
      Spawn rates and killability in game are human-verified.
- [x] Drop tables added for each new mob — 80 rows, 4 dropperids,
      `db/data/154-neo-city-2227-drop-data.sql`, Liquibase changeSet `154`.
- [x] Connection to existing Neo City content works — **delivered, both halves, no client edit and
      no force decision.** See "The route".

---

## The scope statement was right this time, and it was checked before it was believed

Ticket 06's stated mob list turned out to name nine ids no `life` node anywhere places. So this
ticket dumped `life/` for all three maps before accepting anything. It agrees:

| map | life entries | ids placed |
|---|---:|---|
| `683070400` Dangerous City Intersection | 6 | `9400658` ×4, `9400661` ×2 |
| `683070401` Dangerous City Center | 12 | `9400658` ×6, `9400659` ×1, `9400661` ×5 |
| `683070402` Dangerous City Construction Site | 14 | `9400658` ×9, `9400660` ×1, `9400661` ×4 |

**The `683070402` row was wrong in the first draft of this report** (`9400658` ×6, `9400661` ×5) and
code review caught it. The totals were right, the breakdown was not — and the test only asserted
per-map totals, so it passed straight over the error. Both are fixed: the numbers above are
re-derived from the data, and the test now asserts the **per-mob-per-map** matrix, so a table that
drifts from the WZ fails rather than being believed.

**Every one of the 32 entries is `type="m"`.** There are zero `n` (npc) and zero `r` (reactor)
entries in any of the three maps, so **this ticket merges no `Npc.wz` and no `Reactor.wz` at all** —
not "found nothing worth merging", but "the maps place none". Asserted, so a later edit that
introduces one fails rather than shipping a missing sprite.

The two ids with `mobTime = 43200` (12-hour respawn) are exactly the two flagged `boss=1`:

| id | name | level | maxHP | exp | boss | placed in |
|---|---|---:|---:|---:|---|---|
| `9400658` | Imperial Guard Type A | 143 | 9,430,000 | 11,500 | — | all three |
| `9400659` | Dunas Type D | 174 | 100,000,000 | 2,000,000 | ✓ | `683070401` |
| `9400660` | Royal Guard Type S | 160 | 200,000,000 | 15,500,000 | ✓ | `683070402` |
| `9400661` | Afterlord Type A | 135 | 260,000 | 10,200 | — | all three |

All four carry `summonType=1` — the same flag that on ticket 06 marked mobs nothing could spawn.
**Here it is a red herring:** these ids *are* placed by `life` nodes, so the flag only affects the
spawn animation. Worth stating because the two tickets would otherwise look contradictory.

## `deps` output and the merge order derived from it

`WzMerge deps <v84>\Map.wz <id> docs\wz-baseline\add-list` run for all three ids, **all exit 0**.
Raw output: `D:\games\MapleStory\Server\wz-merge\07\<id>.deps.txt`.

| map | references | add-list rows owed | already in v83 |
|---|---:|---:|---:|
| `683070400` | 21 | **1** (itself) | 20 |
| `683070401` | 55 | **1** (itself) | 54 |
| `683070402` | 59 | **1** (itself) | 58 |

**Zero dependency rows are owed, and that is the finding, not an absence of one.** v84 reuses the
v83 Neo City tileset wholesale and adds no scenery for this area. Every one of the 135 references
resolves inside the live client already: `Back/neoCity2.img`, `MapHelper.img/mark/TokyoK`, 41
distinct `Obj/Tdungeon2.img/zone4/{acc,buiding,foot,foot2}/*` nodes,
`Obj/Tdungeon2.img/neoCity/teraForest_out/14`, `Obj/Tdungeon.img/mushCatle/gate/6`,
`Obj/connect.img/rope/14/*`, and the BGM `Sound.wz/Bgm21.img/2215year`.

So the merge order is trivial — the three map images, in any order, with nothing before them. That
is asserted rather than assumed: `theMapAssetsTheseMapsReferenceAreAllPreExisting` reads those
assets back out of the server tree, because "deps says nothing is owed" and "the client actually has
it" are different claims and only the second one prevents a black background.

None of the three maps is an `info/link` stub.

**`deps` had to find the bucket itself.** These are `Map6` maps; the pre-03e procedure text
hardcoded `Map2` and would have failed to a silent exit 0 here. It resolves `683070400 → Map/Map6/`
and says so on line 1 of each file.

## Path lists — the authoritative deliverable

`docs/wz-baseline/merge-lists/07/`. No ordering constraint applies, since nothing is owed.

| file | rows | contents |
|---|---:|---|
| `Map.paths.txt` | 3 | the three map images, no dependency rows |
| `Mob.paths.txt` | 4 | `9400658`–`9400661` |
| `String.paths.txt` | 7 | 3 `Map.img/ossyria`, 4 `Mob.img` |
| **total** | **14** | |

**No `Npc.paths.txt` and no `Reactor.paths.txt`** — the maps place neither.

### `Sound.wz` was merged, then reverted — it is not this ticket's file

`add-list/Sound.txt` carries `Sound.wz/Mob.img/9400658`–`9400661`, the four mobs' attack/hit/die
sound banks, absent from the live client. This ticket merged them, spliced them into
`wz/Sound.wz/Mob.img.xml`, and then **backed all of it out on code review**, because `Sound.wz`
belongs to **ticket 06** under the batch-A ownership split — it is not in this ticket's owned list.
The XML was reverted with a path-scoped `git checkout` and `wz/Sound.wz/` is clean; no
`Sound.paths.txt` is delivered.

**Handoff, so the finding is not lost.** Those four rows are real v84 content nobody has claimed:

```
Sound.wz/Mob.img/9400658   Sound.wz/Mob.img/9400660
Sound.wz/Mob.img/9400659   Sound.wz/Mob.img/9400661
```

Whoever owns `Sound.wz` next should add them to `merge-lists/06/Sound.paths.txt`. Two things worth
inheriting rather than rediscovering: the merge is clean (`content OK Sound.wz/Mob.img`, added 4,
refused 0) but **cannot be promoted** — every `Sound.wz` merge exits 4 on the unparseable
`BgmGL.img`, exactly as 06 recorded; and the cost of not having them is only that the four mobs are
silent. The maps' own BGM (`Bgm21/2215year`) is already in the live v83 client, so **the area is not
silent** and no `Sound.wz` row is needed for anything in this ticket to work.

**Overlap with ticket 06's lists: none, checked path by path.** 06's `Map.paths.txt` is
`Back/dragonRoad.img/{ani,back}/*`, `Obj/dungeon3.img/skyValley`, `Tile/blackTileFly.img` and 22
`240080xxx`/`683010000` map images; its `Mob` list is `83000xx`/`95003xx`; its `String` list is
`ossyria/240080xxx` + those mob ids + five `Npc.img` ids; its `Sound` row is
`Bgm14.img/DragonRider`. Every one is disjoint from all 18 rows here. Nothing on 06's lists is
re-added. Overlap with ticket 04 is structurally impossible — 04 owns `String.wz/{Eqp,Etc,Consume,
Ins,Cash}.img` and this ticket touches only `Map.img` and `Mob.img`.

## Dry runs, conflicts, force decisions

`--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt` on every `merge` and every `xml`, dry runs
included, both sides.

| file | requested | added | refused | denied | forced |
|---|---:|---:|---:|---:|---:|
| Map | 3 | 3 | 0 | 0 | 0 |
| Mob | 4 | 4 | 0 | 0 | 0 |
| String | 7 | 7 | 0 | 0 | 0 |
| *(Sound)* | *4* | *4* | *0* | *0* | *0* | *(run, then reverted — not this ticket's file)* |

**Zero conflicts, zero denials, zero force decisions. `--force` was never passed on either side.**
Every `*.conflicts.txt` in the staging directory is empty, on the binary and the XML side, on the
dry run and the real run. None of the 28 deny roots intersects these 18 paths: the 10 `NpcLocation`
ids and `Npc.wz/9000021.img` are in files this ticket never opens, and the 17 denied
`MonsterBook/<mob>/reward` parents are Leafre/Ludibrium ids — this ticket never names
`MonsterBook.img` at all.

**Nothing was decided by omission either.** Unlike ticket 06 (which deliberately left
`String.wz/Npc.img/9201144` off its list to protect Cosmic's "Steward"), every v84 row in scope here
was taken. There is no id in this area that Cosmic has already claimed.

## Merge results

Live vs backup SHA-256, before anything (§5.0), all equal:

```
Map.wz      A39DA5AC66CB3CB1803B1A8F70F19CDF67CA191016E16C853F521B3C8156ACA4
Mob.wz      BEC9D9E15C1D16E7B9CCE1A900938363411FBFD44C52BE7DB86735A2BCB210F1
String.wz   9437DEB8CE481DAE4909097EBFB366D24BACCD73D55D3ED00FA3198603CAE499
Sound.wz    BC6570D39AE1C021AF433616ED4EC5F0C8917513B63D28536D116AAC74BDFD76
```

The per-ticket `pre\` snapshots hash-match all four, and every real merge passed `--live` (the
`snapshot check OK` line appears in each). Staged output (§6.4):

| file | exit | SHA-256 | bytes |
|---|---|---|---:|
| `Map.wz` | 0 | `FB4059994527B7F76AF72AC337628EEB863AEB4D9530AF29D02839EBC543CC0A` | 638,479,963 |
| `Mob.wz` | 0 | `98C4283D20675025165DF5A4759C6DF9261C798879547D7E22DA156CE5A6E788` | 482,764,081 |
| `String.wz` | 0 | `D0A168F6F27D299482AEA1C2EA7282F189BA18817D804740FE622A14CD78E4D8` | 3,561,721 |
| `Sound.wz` | **4** | — not promoted — | — |

### `Sound.wz` failed verification, for the known pre-existing reason ticket 06 measured

```
content OK  Sound.wz/Mob.img  6ea6e8c530b12213…
UNPARSEABLE image Sound.wz.partial\BgmGL.img: InvalidDataException WZ extended property exceeds its declared block.
verify: 44 images parsed, 1 unparseable, 0 requested paths missing, 1 images content-checked, 0 drifted
```

Identical shape to 06's. `Sound.wz/BgmGL.img` is unreadable by MapleLib in **all three trees**
(procedure §11); the write itself was clean and the post-write verifier counts the pre-existing
unreadable image. **The `.partial` was deleted, not renamed** — the procedure says never install one.

Recorded because the measurement is useful to whoever owns `Sound.wz`, but **this ticket ships no
`Sound.wz` deliverable at all** — see "`Sound.wz` was merged, then reverted" above. Nothing here
depends on it: the maps' BGM is already in v83, and the server never reads `Sound.wz`.

## Verification

**§6.2 diff tool**, pre vs post, all three promoted files:

| file | add-list | removed-list | modified-list |
|---|---:|---|---|
| Map | 3 | empty | **empty** |
| Mob | 4 | empty | **empty** |
| String | 7 | empty | `Map.img` 253,193→253,438 and `Mob.img` 65,924→66,115 only |

21,686 images parsed, **2 parse failures — both `Sound.wz/BgmGL.img`, one in each of the two
pre-merge roots**, i.e. the known defect and nothing new. Add-lists are exactly the path lists — no
more, no fewer. `Map` and `Mob` have *empty* modified-lists because every row is a whole new `.img`;
nothing existing was even re-serialized.

**§6.1 content digest**, per direct child, pre vs post, for each image inserted into:

- `String.wz/Mob.img` — 4 children differ (`9400658`–`9400661`) + `TOTAL`. Every other child
  identical.
- `String.wz/Map.img` — 1 child differs (`ossyria`, the parent the 3 rows nest under) + `TOTAL`.
- `String.wz/Map.img/ossyria` — digested a level deeper on purpose, since the level above collapses
  ~600 map names into one line: **exactly `683070400`, `683070401`, `683070402` differ** + `TOTAL`.

Added nodes also digest **identical to their v84 source**: comparing
`WzMerge hash <v84>\String.wz String.wz/Mob.img` against the post file, zero `94006xx` lines differ.
The version trap proven on content, not headers.

**§6.3 the gate fires**, both sides:

```
binary: SKIP Mob.wz/9400658.img (already exists in target) … added 0 (forced 0), refused 4   exit 5
xml:    SKIP String.wz/Map.img/ossyria/683070400 (already exists in wz\String.wz\Map.img.xml)
                                                 … added 0 (forced 0), refused 7             exit 5
```

**§5.6 server XML**, all four files, `--deny` passed, `--force` not:

```
wz/String.wz/Map.img.xml | 12 ++++++++++++
wz/String.wz/Mob.img.xml | 12 ++++++++++++
2 files changed, 24 insertions(+)
```

plus 7 new `.img.xml` files (3 maps, 4 mobs). **0 deletions anywhere.** `wz/Sound.wz/Mob.img.xml`
was also spliced (+24) and then reverted on review; `git status -- wz/Sound.wz/` is clean.

Ticket 05's uncommitted XML (`wz/Skill.wz/*`, `wz/Character.wz/TamingMob/*`) was in the same working
tree throughout and is untouched. **Nothing here ever ran a blanket `git checkout -- wz/`.**

**Live client, after the ticket:** all **18** `.wz` in `D:\games\MapleStory\` still SHA-256-match
`_backup\client-v83-EzorsiaV2-2026-08-15\` — re-checked file by file at the end, 0 mismatches — and
no `.partial`, `.TEMP` or `.merged` exists beside them. Nothing was written to
`D:\games\MapleStory\` at any point, and `porting-resources\wz-data\**` was opened read-only.

**§5.8 server-side**, `src/test/java/server/V84NeoCity2227NodeTest.java`, **14 tests, all green**;
**full suite 1,928 tests, 0 failures, 0 errors** (the 1,910 baseline plus this ticket's 14 and the
sibling classes tickets 04 and 05 landed meanwhile). It reads the real tree through an explicitly constructed `XMLWZFile` for the
`WZFiles.DIRECTORY` static-init reason the tracer test documents. What it proves:

- all three maps parse; `info`, `portal`, `foothold` present; `mapMark`, `bgm`, `returnMap`,
  `lvLimit` are the v84 values; `fly=0` (unlike Crimson Sky, no flying-skill gate here)
- **the route exists on both sides** — `683070400/left00 → 240070000` at portal `TD_neo`, and
  `240070000` itself carries a `TD_neo` portal with `pt=8` and `script=TD_chat_enter`
- the three maps form one chain `400 ↔ 401 ↔ 402`, each has a spawn point, and `402` has no
  unexpected `right00`
- every `life` id was merged; every entry is `type="m"`; the set of mobs placed is **exactly**
  `9400658`–`9400661`; and the spawn counts match **per mob per map**, not merely per map —
  negative-controlled by patching one count to the wrong value and confirming it fails
- no map places a reactor
- the six asset families `deps` said were already present really are in the tree
- the mobs' levels, HP, exp and `boss` flags read back; names are non-blank and not `MISSING NAME`
- **each analogue is genuinely a name match** — `String.wz/Mob.img` is read for both ids and the new
  name must be the old name plus a `Type` suffix, so the quest-row rule below cannot come to rest on
  an assumption
- the teleporter script routes selection 6 to `683070400`, is not still commented out, and
  **`quests.length == array.length`** — the failure mode that would have made 2227 silently
  unreachable
- the drop SQL parses as one well-formed statement, 80 rows, 4 dropperids, exactly one `;` in the
  file and no apostrophe in any comment
- **every one of the 80 rows exists verbatim under its declared analogue in `152-drop-data.sql`, and
  each dropper's row count equals its analogue's** — subset *and* completeness, so a truncated copy
  fails too
- exactly one quest-gated row is copied and it is the specific Dunas one
- neither `152-drop-data.sql` nor `153-crimson-sky-drop-data.sql` contains any of the four new
  dropperids, and changeSet `154` is registered

The suite is not vacuous: it failed for real while being written, on
`total life entries ==> expected: <29> but was: <32>` — a miscount in the assertion, not in the
data. The data won and the assertion was rewritten as per-map counts.

**Handed to ticket 08, deliberately not done here.** Code review's strongest finding is real:
`V84NeoCity2227NodeTest` shares roughly 120 lines with `V84CrimsonSkyNodeTest` — `wz()`, `map()`,
`DROP_ROW`, `analogueOf()`, and most of the two drop-file assertions. **08 will make that a third
copy, and that is the right moment to extract a package-private `V84Drops` helper.** It was not done
in this ticket because the extraction has to edit ticket 06's committed test file, which is outside
this ticket's stated ownership, and doing it while 05 was live in the same tree buys a merge risk
for no delivery. Two other review findings *were* fixed here: the file had two regexes for one
`drop_data` row grammar (now one, with named group indices), and the teleporter assertion counted
the two arrays with different split delimiters, which would have gone vacuous on any reformat of
`array` (now both are counted by matching elements).

## The route into existing Neo City — delivered, and why this one was not blocked

This is the criterion ticket 06 could not close. Here it closes, and the reason is worth recording
because it is the opposite shape.

**The client half already exists, in stock v83, unmerged and unmodified.**

```
683070400/portal/1  pn=left00  pt=1  tm=240070000  tn=TD_neo
240070000/portal/3  pn=TD_neo  pt=8  tm=999999999  script=TD_chat_enter
```

`240070000` is the existing Neo City hub. Its portal list is **byte-identical in the live v83 client
and in v84** — dumped and compared, all 10 portals, same order, same values. `240070000` appears in
neither `add-list/Map.txt` nor `modified-list/Map.txt`. So unlike 06, where the inbound node existed
in no vendor's data and could not be authored by a copying tool, **here there was nothing to merge
because the edge was already there.** No hand-authored node, no HaRepacker, no force decision, and
additive-only never had to refuse anything.

`pt=8` is a script portal, so the entry is server-side by design — which is exactly how GMS gated
this content, and option 1 of the three 06 listed.

**The server half was pre-built by Cosmic and commented out.** `scripts/portal/TD_chat_enter.js`
opens NPC `2083006` (the Neo Tokyo Teleporter), and that script already carried the 2227
destination — authored, then commented out, presumably because the maps did not exist in v83:

```js
var array = [ …, "Year 2503 - Air Battleship Bow"/*, "Year 2227 - Dangerous City Intersection"*/];
…
/*case 6:
    mapid = 683070400;
    break;*/
```

Both are now live. **One thing had to be decided rather than uncommented**, and it is a real
decision, stated rather than buried: `quests[i]` gates `array[i]`, and the menu is built from
`Math.min(limit, array.length)` where `limit` counts consecutively-completed gate quests. A seventh
destination behind six gates is silently unreachable — the menu would simply never show it, with no
error. So the array needed a seventh gate.

**v84 ships no quest for Year 2227.** Its new `37xx` quests are `3756`–`3761`, and those are the
Crimson Sky chain (`The Dragon Rider's Identity`, `Towards the Sky`, `Dragonica's Horn`,
`Tears of Repentance`) — ticket 06's area, not this one. The live client's Neo City quest chain
stops at `3749`, and no quest in either tree has a `Year 2227` parent. So the gate is a choice.

Chosen: **`3749` "Nibelung's Song"** — the quest that *closes* the Year 2503 chain (destroy Nibelung,
report to Ashura). That makes 2227 unlock on finishing the last previously-reachable area rather than
alongside it, which fits the data: 2227's mobs are Lv.135–174 against 2503's Lv.91, and the maps
carry `lvLimit = 120`. `3749` is fully defined in the live server tree (`QuestInfo`, `Check`, `Act`
and `Say`), so it is genuinely completable and not a dead gate. The alternative — reusing `3748`, so
2227 opens at the same moment as 2503 — is a one-character change if the owner prefers it.

Two smaller things checked because they would have quietly broken the criterion:

- **`partyOnly = 1`** on all three maps. Cosmic never reads it — `partyOnly` and `PARTY_ONLY` appear
  nowhere in `src/`. Solo entry works, so the human verification step below is possible.
- **`fieldLimit = 72`** = `0x48` = `DOOR | CANNOTVIPROCK`. It does **not** include `DROP_LIMIT`
  (`0x400000`), so items drop. Checked for the same reason 06 checked its fly maps: a drop-limited
  map would have made the whole table below pointless. It does mean no Mystic Door and no VIP rock
  out, which is intended for a dungeon.

## The drop tables

`src/main/resources/db/data/154-neo-city-2227-drop-data.sql`, **80 rows, 4 dropperids**, registered
as Liquibase changeSet `154` in `src/main/resources/db/changelog-data.xml`. Generator kept beside the
merge output at `D:\games\MapleStory\Server\wz-merge\07\gen-drops.ps1` so the file is reproducible
rather than hand-maintained.

**A new file, for the reason 06 established.** `152-drop-data.sql` and `153-crimson-sky-drop-data.sql`
are both `<sqlFile>` changeSets that have already run; editing either changes its checksum and fails
validation at startup. Both are byte-untouched, asserted by test.

**Where the rates came from: nowhere new.** Every row is a verbatim copy of a row that already exists
in `152-drop-data.sql` for the live analogue, with only `dropperid` swapped — same `itemid`,
`minimum_quantity`, `maximum_quantity`, `questid`, `chance`, including the meso rows (`itemid 0`).
Nothing was scaled, rounded or invented.

**All four analogues are exact name matches, so no level/HP fallback was needed** — v84 named these
mobs after live ones and appended a variant suffix. Names read from the live client's
`String.wz/Mob.img`, and the name-match property is asserted in the test rather than asserted in
prose:

| new | name | ← analogue | rows | basis |
|---|---|---|---:|---|
| `9400658` | Imperial Guard Type A | `8140511` Imperial Guard | 24 | name; the live Neo City mob placed by `240070600` |
| `9400659` | Dunas Type D | `8220010` Dunas | 1 | name; the live Neo City party boss in `240070303` |
| `9400660` | Royal Guard Type S | `8140512` Royal Guard | 40 | name; `8140511`'s live sibling |
| `9400661` | Afterlord Type A | `8120102` Afterlord | 15 | name; the live Neo City mob placed by `240070400` |

Three of the four are the mobs the *existing* Neo City maps spawn, so the new area's drops are
continuous with the old one's by construction rather than by taste.

**The quest-row rule, applied.** 06's rule is that a `questid != 0` row may be copied only from a
name-matched analogue, or an existing quest silently becomes completable on a new mob. Here every
analogue is a name match, so the rule permits every row, and exactly one row is quest-gated:
`(9400659, 4032516, 1, 1, 3735, 400000)` — Time Sand, quest `3735` "The Wreckage of the Missile"
(defeat Dunas, deliver the sand). Copying that onto a Dunas variant is precisely the case the rule
exists to allow. It is also inert in practice: `683070401` sits behind the 2503 gate, which requires
`3748` → … → `3736`, which requires `3735` already complete. Asserted both ways — the row must be
present *and* it must be the only one.

**Why `9400659` gets one row, and why that is not a truncated copy.** It is the shape this server
already uses for Neo City bosses: `8220010`, `8220011` and `8220012` each carry exactly one row —
the quest item at chance `400000` — and `8220013`, the final boss, carries none at all. Their real
rewards come from the `TD_Battle` event manager, not `drop_data`. Copying the analogue verbatim
reproduces that convention. **Stated so the owner can override it deliberately:** if Dunas Type D
should drop loot like a field boss rather than like an instance boss, the honest way is to give it
`8140512` Royal Guard's 40-row table as a *second* analogue — a design call this ticket has no
mandate for, and one that inventing rates would have hidden.

## Human steps — staged, not performed

I cannot launch the client, walk into the area, or kill anything. Everything below is unverified.

**Before anything: close MapleStory and any HaRepacker window.** All three staged files are large
and Windows will not replace a `.wz` the client holds open.

1. **Install the three staged files.** One at a time, checking size after each.
   ```
   copy D:\games\MapleStory\Server\wz-merge\07\Map.wz     D:\games\MapleStory\Map.wz
   copy D:\games\MapleStory\Server\wz-merge\07\Mob.wz     D:\games\MapleStory\Mob.wz
   copy D:\games\MapleStory\Server\wz-merge\07\String.wz  D:\games\MapleStory\String.wz
   ```
   Expected sizes and SHA-256 are in the "Merge results" table. **There is no `Sound.wz` to
   install** — this ticket delivers none.
   **Coordination — read this before copying.** Tickets 04, 06 and 07 each stage a `String.wz` from
   the same v83 base, and 06 also stages `Map.wz` and `Mob.wz`. **Staged merges from the same base
   do not compose** — installing two loses one set. Compose from the path lists under
   `docs/wz-baseline/merge-lists/{04,06,07}/` in one merge instead of copying two files. The lists
   are disjoint, so a composed run is a concatenation with no conflict resolution needed.
2. **Run the DB migration.** Liquibase changeSet `154` inserts the 80 rows. Confirm:
   `SELECT dropperid, COUNT(*) FROM drop_data WHERE dropperid BETWEEN 9400658 AND 9400661 GROUP BY dropperid;`
   → `9400658`=24, `9400659`=1, `9400660`=40, `9400661`=15.
3. **Walk the route, which is the point of this ticket.** Do **not** start with `!warp`.
   Take a character that has completed quest `3749` into Neo City (`240070000`), and click the
   **`TD_neo`** portal on the right-hand side of the map.
   - **Pass:** the Neo Tokyo Teleporter (NPC `2083006`) opens and the menu now has a **seventh**
     line, "Year 2227 - Dangerous City Intersection". Selecting it lands you in `683070400`.
   - **Fail signature to look for specifically:** the menu shows only six lines. That is the
     `min(limit, array.length)` gate — it means the character has not completed `3749`, not that the
     merge failed. Check with a character that has, before touching anything else.
   - A character that has *not* finished `3749` should see six lines and nothing else. Worth
     checking too — it is the half of the gate that proves it gates.
   - **The one thing about the gate I could not verify.** `3749` is completed by killing `8220015`,
     and the only thing that spawns `8220015` is the party-boss instance in
     `scripts/event/TD_Battle5.js` (entered via the `TD_Boss_enter` portal, which needs a party of
     **at least 2** and the leader to click). So the gate is only as alive as that event flow. If
     `TD_Battle5` is broken on this server, `3749` is uncompletable and 2227 is unreachable through
     the front door — that would be a pre-existing Neo City fault, not a merge fault, but it lands
     on this ticket's criterion. **If it turns out to be broken, the one-token fix is to change the
     seventh gate from `3749` back to `3748`**, which is the gate 2503 already uses and is therefore
     known-passable by anyone who reached 2503.
4. **Check the maps render.** `683070400` should be named "<Year 2227> Dangerous City Intersection",
   show the Neo City world-map marker, and draw the ruined-city background.
   - **Fail signature:** a black or missing background would mean an `Obj/Tdungeon2.img/zone4`
     reference did not resolve. This ticket merged **no** scenery — `deps` said the live client
     already had all 135 references — so a missing background here means that claim was wrong and is
     the single most valuable thing to look at.
5. **Walk the chain and fight.** `683070400 → right00 → 683070401 → right00 → 683070402`, and back
   the same way. Confirm Imperial Guard Type A and Afterlord Type A spawn and are killable in all
   three; confirm **Dunas Type D** appears in `683070401` and **Royal Guard Type S** in `683070402`
   — both on a 12-hour timer, so if neither is there, wait or check `mobTime` rather than assuming a
   merge fault. All four should have names, not blanks, over their heads.
   Expect the four mobs to be **silent** — their SFX live in `Sound.wz`, which this ticket does not
   own and does not ship. Cosmetic and expected, not a fault.
6. **Check drops.** Kill Imperial Guard Type A and Royal Guard Type S and confirm items and mesos
   fall (`fieldLimit=72` does not block drops — checked, but this is the observation that proves
   it). Mystic Door and VIP rock *should* be blocked in these maps; that is intended.
7. **Rollback, if any of it goes wrong.** All-or-nothing, both sides:
   ```
   copy D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\<Name>.wz D:\games\MapleStory\<Name>.wz
   ```
   for **all three** files, and on the server side revert **only this ticket's paths** —
   `wz/Map.wz/Map/Map6/68307040*.img.xml`, `wz/Mob.wz/94006*.img.xml`,
   `wz/{String,Sound}.wz/*.img.xml`, `scripts/npc/2083006.js`. **Never `git checkout -- wz/`
   wholesale**: ticket 05's uncommitted XML lives in the same tree.

## What I could not do

- **Launch the game.** Every rendering, spawn, reachability and drop-in-practice claim above is
  staged, not observed.
- **Deliver the four `Sound.wz` mob SFX rows.** Not mine to deliver — `Sound.wz` is ticket 06's
  file. Merged, then reverted; the rows and the measurement are handed off above. The four new mobs
  will be silent; the maps' BGM is unaffected.
- **Prove the seventh gate is passable end to end.** Quest `3749` exists and is fully defined, but
  completing it needs the `TD_Battle5` party-boss instance to work, which needs two players and a
  running server. Fallback recorded in human step 3.
- **Correct my own first draft without help.** The `683070402` spawn breakdown in this report was
  wrong and code review caught it, not the test — because the test asserted per-map totals only.
  Both are fixed, and the test now asserts the per-mob-per-map matrix the docs quote.
- **Merge the two `UI.wz` boss HP-bar frames.** `add-list/UI.txt` carries
  `UI.wz/UIWindow.img/MobGage/Mob/9400659` and `/9400660` — the boss gauge art for Dunas Type D and
  Royal Guard Type S. `UI.wz` is out of scope by ticket-03 decree (procedure §11: "take `SkillEx` /
  `SkillMacroEx` only, never bulk"), and its dry run refuses nothing, so these two rows are
  importable whenever that decree is revisited. Without them the two bosses fall back to the generic
  HP bar. Cosmetic; recorded so it is a decision rather than an oversight.
- **Give Year 2227 a quest chain.** v84 ships none, so the seventh gate is a choice
  (`3749`), not a port. If a later ticket adds real 2227 quests, that gate is where to point them.
