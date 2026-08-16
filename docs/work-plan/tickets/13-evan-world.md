# 13 — Evan world and quest chain playable

**Blocked by:** 10, 09

**Status:** ready-for-agent

## What to build

Evan's starting area and story quests are playable start to finish.

Maps `900010000`–`900020220` (Dream Forest Entrance/Trail/Forest, Lush Forest, Lost Forest Entrance/Trail/Forest), `900090100`–`900090103` (Tutorial 0–2, Job Advancement), `900030000` Behind the Stronghold, plus `100030301` Forest Hall and `100030320` Large Forest Trail 2. NPCs Afrien, Hiver, Olaf, Glowing Stele.

Three gaps found during the audit that this ticket closes, none of which the Evan XML pack covers — it contains no Quest and no Map data:

1. **No Evan quest data in v83.** `QuestInfo.img.xml` has Aran's `21000/21001/21010…` and zero `22xxx`. Confirmed both ways: GMS/84 serves quest 22000, GMS/83 returns 404.
2. **Six referenced maps missing.** Cosmic's Evan scripts already reference `100030102, 100030103, 100030200, 100030300, 100030310, 100030400`; v83 has only `100030000` and `100030001`.
3. **Evan NPCs missing.** `1013101`, the giver of quest 22000, does not exist in v83 — that range holds only `1013000`.

Cosmic already ships 13 Evan quest scripts (`22000-22008`, `22500-22507`) that are inert until the WZ data lands.

## ~~⚠ Inherited hazard — read before merging `Skill.wz/2001.img`~~ — CLOSED

*Recorded by 03f (review finding F4), closed by ticket 10 and re-confirmed independently here.
The eight ids `buildSkillMounts` mints for job 2001 ARE the mounts the table pairs them with,
8/8 by name; the warning is deleted rather than carried forward. See "Delivered" below.*

## Acceptance criteria

- [x] Evan maps merged into **server XML** and reachable — client WZ needs nothing, see "the
      client half is a no-op" below
- [x] Evan NPCs present — 14 `Npc.wz` images + the 3 missing `String.wz/Npc.img` names, which is
      the half the server actually reads
- [x] Evan quest data merged — **already done by ticket 33**, verified present here, not re-merged
- [~] The intro chain can be played start to finish — every map, NPC, mob and name it needs is
      now in the tree; **five scripts it also needs are still missing** and belong to ticket 31
- [x] The six previously-missing maps referenced by existing scripts now resolve

---

# Delivered — 2026-08-16

**Server XML only (`wz/`). Nothing under `D:\games\MapleStory\`, `D:\games\MSv84\client\` or
`D:\games\dreamms\` was read for writing or written to. The running server was not touched.**

## What was actually blocking, after checking the brief against the tree

The dispatch named two blockers. **Only one of them was real**, and finding that out first is what
this ticket's first hour bought:

| dispatch said | measured |
|---|---|
| "`Skill.wz/2001.img` was never merged server-side, that is why Evan NPEs" | **False.** `wz/Skill.wz/2001.img.xml` (136 KB) and all ten Evan job images `2200`/`2210`–`2218` have been in this tree since commit `15f1e81fe`, **ticket 10**. `V84EvanNodeTest` already asserts they parse and resolve. Nothing to merge. |
| "`EvanCreator` uses `MapId.MUSHROOM_TOWN`; `!warp 900010000` fails" | **True.** `wz/Map.wz/Map/Map9/` held exactly one 9000xxxxx image, `900000000`. All 19 Evan maps were absent, as were the six `1000301xx`–`100030400` maps and every Evan NPC image. |

**So the NPE is not a WZ gap and merging WZ will not fix it.** `AbstractDealDamageHandler.java:656`
reads `Skill skill = SkillFactory.getSkill(ret.skill); effect = skill.getEffect(...)` with no null
guard, so *any* id the factory cannot resolve is that exact stack trace. With `2001.img` and all ten
job images present and loading, the remaining explanation is that `ret.skill` is being **read from
the wrong offset of a v84 attack packet** — the same class of defect as `MOVE_PLAYER` being 33 bytes
(`404ec864d`) and the mob-move counts (`cf9d9afa0`). **Routed to whoever owns the v84 packet
layouts; not touched here, per the dispatch's file boundary.** The one-line hardening that would turn
a crash into a logged drop is `if (skill == null)` at that line, and it belongs in the same hand as
the layout fix.

## Merged — 65 nodes, 4 runs, exit 0 on all four

`WzMerge xml <v84>\<Name>.wz wz <paths> <conflicts> --deny <lists>\COLLISION-DENY.txt`, manifests in
`docs/wz-baseline/merge-lists/13/`. **Dry run first on all four; `--deny` on every run.**

| source | rows | result | lands as |
|---|---:|---|---|
| `Map.wz` | 28 | `added 28 (forced 0), refused 0`, exit 0 | 28 new `.img.xml` |
| `Npc.wz` | 14 | `added 14 (forced 0), refused 0`, exit 0 | 14 new `.img.xml` |
| `Mob.wz` | 2 | `added 2 (forced 0), refused 0`, exit 0 | 2 new `.img.xml` |
| `String.wz` | 21 | `added 21 (forced 0), refused 0`, exit 0 | **spliced into 2 existing files** |

- **Maps (28) — nine more than this ticket's prose names, deliberately.** The ticket's line 11 lists
  `900090100`–`900090103` but not `900090000`–`900090004` ("Teaser") or `900090104`, and names
  `100030301`/`100030320` but not `100030100`/`100030101`. Taking the whole `add-list/Map.txt` range
  instead of a hand-cut subset is the safer call **and the cheaper one**: these are new files, they
  cannot collide, and §7's ordering rule plus §5.3's asset-reference rule both punish a partial set.
  The one map deliberately left out is `100030301`, on safety grounds — see Deferred #1. The full
  set is `900010000`,
  `900010100`, `900010200`, `900020100`, `900020110`, `900020200`, `900020210`, `900020220`,
  `900030000`, `900090000`–`900090004`, `900090100`–`900090104` (19) — plus the Henesys-farm story
  street `100030100`, `100030101`, `100030102`, `100030103`, `100030200`, `100030300`, `100030310`,
  `100030320`, `100030400` (9). **None of the 28 has an `info/link`**, checked before merging, so no
  further map image is owed. `900010000` has an empty `life` and an empty `reactor`.
- **NPCs (14).** `1013001`, `1013002`, `1013100`–`1013105`, `1013200`–`1013202`, `1013204`–`1013206`.
  `1013101` — the giver of quest `22000`, this ticket's gap 3 — is among them. `1013106`, `1013203`
  and `1013207` were already here; the dry run named them and they were dropped from the manifest.
- **Mobs (2).** `1210111` and `9300385`, the only two life ids the merged maps spawn that this tree
  did not have. `0130100`, `1210100`, `1210101` were already present.
- **Names (21).** 18 `String.wz/Map.img/etc/<id>` and 3 `String.wz/Npc.img/<id>`. `MapFactory`
  routes every id ≥ 900000000 to `etc`, which is where v84 puts them too.

**The dry runs also proved the deny-list and the gate were in play:** re-running all four manifests
against the merged tree gives `added 0, refused 65`, **exit 5 on every one** — 28/14/2/21. Conflicts
files are committed beside the manifests (`*.xml.conflicts.txt`, `*.postmerge-recheck.conflicts.txt`).

`WzMerge selftest` was run before any of this and **passed all 20 checks**, including both halves of
the T23 `MapLogin.img/back` partial-fill regression.

## The zero-change proof — `merge-lists/13/verify.ps1`, output in `verify-report.txt`

Adapted from ticket 33's, because "an empty conflicts list is not evidence of safety" and 44 of the
65 nodes landed as new files where that is *structurally* true but still unmeasured.

**PROOF 1, whole tree, byte level.** Recompute the git blob SHA-1 of **every one of the 22,445 files
`wz/` held at the baseline commit**, from the bytes on disk, through `git hash-object --stdin-paths`
(so the CRLF clean filter is applied and the SHAs are comparable). This deliberately does not ask
`git diff`, which may answer out of the index's stat cache.

```
P1 : 22445 baseline files under wz/ re-hashed from disk -> changed 2, missing 0
P1 :   changed: wz/String.wz/Map.img.xml
P1 :   changed: wz/String.wz/Npc.img.xml
P1 : untracked new files under wz/: 44 (non-.img.xml: 0)
```

**PROOF 2, those two files, node by node.** A canonical whitespace-independent digest of every
pre-existing id's whole subtree, compared against the baseline blob. This is the half that catches a
write *into* an existing record — a value edit, an added child, a dropped child, a reorder — none of
which PROOF 1 can localise and none of which `conflicts.txt` can see at all. Run at both levels in
`Map.img`, because the 18 rows go one level down:

```
P2 : Map.img/<root>  : pre-existing 12 categories -> changed 1 [etc], missing 0, new 0
P2 : Map.img/etc     : pre-existing 2458 -> changed 0, missing 0; new 18 (expected 18)
P2 : Npc.img/<root>  : pre-existing 7089 -> changed 0, missing 0; new 3 (expected 3)
```

**The self-checks, which are the reason either number may be quoted.** All three run in the same
invocation, against the real files, with the real comparators:

| self-check | result |
|---|---|
| PROOF 1 calls the real tree clean **and** catches one appended byte in `wz/Npc.wz/1013000.img.xml` (mutated on disk, restored in a `finally`, restore verified by SHA-256) | **True / restored True** |
| PROOF 2 reports 0 changed on the real file **and** flags a `streetName` edit inside pre-existing id `910050300` | **True** |
| PROOF 2 flags a **dropped child** of pre-existing id `910050300` — the direction a value test does not cover | **True** |

`RESULT: PASS`. The first version of self-check 2 searched the whole document, landed on
`106021100` (which lives under a different category) and reported "the comparator cannot see an
edit" about an edit the comparator was never asked to look at — it is scoped to `etc` now, and the
trap is written into the file so the next person does not repeat it.

## The other half: the 44 new files are FAITHFUL, not merely present — `13/fidelity.ps1`

`verify.ps1` is blind to the new files by construction. So every one of the 44 was re-read from the
v84 archive with `WzMerge dump … 30` (MapleLib's *reader* plus a printer) and compared with the
committed `.img.xml` (MapleLib's *XmlSerializer* — a different code path):

```
compared 44 images, 50670 nodes, 42284 scalar name=value pairs
RESULT: PASS
```

Node count, the full **name multiset**, and the full **`name`→`value` multiset of every scalar leaf**
— all three identical on all 44, 0 divergences. Comparator self-checked on four mutations of a real
merged image first, and the script exits 2 without comparing anything if any escapes: a node
renamed, a node dropped, a node duplicated, and **a scalar value edited with the name left
untouched** (the one a name-only comparison misses). Depth 30 requested with an assertion that the
observed depth is under it, because `dump` truncates silently at its limit.

The **only** normalisation applied is on `float`/`double`: `dump` prints `mobRate` as `1`, the
serializer writes `1.0`. Ints, longs and strings are compared verbatim — normalising those would let
a re-rendered id or a lost leading zero through, which is the class of damage the script exists to
find.

## The `buildSkillMounts` trap — verdict: **the rows are correct, `2001` stays, warning deleted**

Dumped from the v84 archive directly, not taken from add-list naming and not taken from ticket 10 on
trust. **Two dumps, because the first one alone answers the wrong question.**

**Dump 1 — `WzMerge dump <v84>\Skill.wz 2001.img/skill/<id> 3`, which is what the dispatch asked
for and is the one that settles it at node level.** All eight mount ids have the *same shape*, and
it is a mount's shape:

```
20011025 / 27 / 28 / 29 / 30 / 37 / 38 / 39
  children: icon, iconMouseOver, iconDisabled, level, effect, effect0, invisible, timeLimited, disable
  level/1 : mpCon=10  time=2100000 (35 min)  pdd=10  mdd=10  x=1
```

**Not one of them has `damage`, `attackCount` or `mobCount`** — none is an attack, so none can be an
Evan skill that "silently becomes a mount". `20011026` is visibly a different animal in the same
image (`children: info, …, level, effect, repeat, invisible`; `level/1: x=3 y=1 mpCon=90`, no
`timeLimited`, no `disable`) — flight, per-second drain, exactly as its name says.

**Dump 2 — `WzMerge dump <v84>\String.wz Skill.img/<id> 2`, the names**, which is what pairs each id
with the sprite the table hands it:

| id | v84 name | `SKILL_MOUNTS` claims | verdict |
|---|---|---|---|
| `20011025` | Charge! Wooden Pony | 1932006 Wooden Pony | ✅ |
| `20011027` | Croco | 1932007 Croco | ✅ |
| `20011028` | Black Scooter | 1932008 Black Scooter | ✅ |
| `20011029` | Pink Scooter | 1932009 Pink Scooter | ✅ |
| `20011030` | Nimbus Cloud | 1932011 Nimbus Cloud | ✅ |
| `20011037` | Unicorn | 1932018 Unicorn | ✅ |
| `20011038` | Low Rider | 1932019 Low Rider | ✅ |
| `20011039` | Red Truck | 1932020 Red Truck | ✅ |
| `20011026` | **Soaring** (flight, MP -90 then -3/sec) | *not in the table* | ✅ correctly excluded |

**8/8 by name, plus the negative control.** The dispatch asked for "nine ids"; the ninth,
`20011026`, is the one the table deliberately omits. So `2001` is **not** removed from the job-prefix
loop — removing it would have broken eight working mounts. The stale HAZARD comment in
`StatEffect.buildSkillMounts` is replaced with the measured result and an instruction not to re-raise
it from the id pattern alone; the `⚠` section at the top of this ticket is struck out.

## The start map — `900010000`, verified twice from the archive before the constant moved

| source | evidence |
|---|---|
| v84 `String.wz/Map.img/etc/900010000` | `mapName` = "Dream Forest Entrance", `streetName` = "Dream World" |
| Edelstein v95.1 `UserOnPacketCreateNewCharacterPlug.cs:71` | `RaceSelectType.Evan => 900010000`, in the same switch that maps `Aran => 914000000` — which is Cosmic's own `MapId.ARAN_TUTORIAL_START` |

The `9000901xx` ids are street **"Video"** — `Tutorial 0/1/2` and `Job Advancement`, i.e. cutscene
maps, and `900090000`–`900090004` are all named "Teaser". They are merged, but none of them is the
creation map. `MapId.EVAN_TUTORIAL_START = 900010000` added beside `ARAN_TUTORIAL_START`;
`EvanCreator.START_MAP` now points at it and the placeholder comment is gone.

## Code changed

| file | change |
|---|---|
| `constants/id/MapId.java` | `+ EVAN_TUTORIAL_START = 900010000` |
| `client/creator/novice/EvanCreator.java` | `START_MAP` = `MapId.EVAN_TUTORIAL_START` (was `MUSHROOM_TOWN`) |
| `server/StatEffect.java` | the F4 HAZARD comment replaced by the measured verdict |
| `src/test/java/server/V84EvanWorldNodeTest.java` | new, 6 tests |

Suite **2096 passed, 0 failed** (baseline 2090 + these 6).

`V84EvanWorldNodeTest` is a sibling of `V84EvanNodeTest` and opens the tree through `V84Wz.wz` only.
Two of its tests are worth naming because they are *derived*, not restated:

- `everyLifeIdTheMergedMapsSpawnHasItsImage` walks the `life` of all 28 merged maps and asserts every
  id resolves the way the server resolves it. **It failed on its first run** — `100030300 -> m
  130100.img` — and the failure was the *test's*: `LifeFactory:100` left-pads a mob id to 11 chars
  (`0130100.img`) and an NPC is looked up **by name out of `String.wz/Npc.img`** (`LifeFactory:294`),
  never out of `Npc.wz` at all. That is why the three missing `String.wz/Npc.img` name rows matter
  more, server-side, than the 14 `Npc.wz` images do.
- `forestHallIsDeliberatelyNotMerged` pins the one deliberate omission below so that merging it is a
  decision, not an accident.

## Deferred, with reasons

1. **`Map/Map1/100030301.img` ("Forest Hall") — REFUSED ON SAFETY, not on effort.** Its `life`
   places static NPCs on ids `9901910`–`9901919`. `PlayerNPC.java:66` allocates `9901910`–`9906599`
   **at runtime**; this is the same hazard that puts `Etc.wz/NpcLocation.img/990191x` on
   `COLLISION-DENY.txt`. Merging it would drop fixed Nexon NPCs onto ids this server hands out.
   Taking it later means hand-authoring the map without those ten `life` slots — a hand-authored
   node, not a merge. Pinned by a test. **Note this is a map the ticket's own line 11 asks for**, so
   it is a spec-listed item that did not land, not an oversight: `100030320` (the other map named on
   that line) did land.
2. **The client-side binary `.wz` merge is a no-op and was not performed.** The owner plays on a
   genuine GMS v84 client (`D:\games\MSv84\client\`), which already ships every node in this ticket.
   Nothing was staged under `Server\wz-merge\`, nothing was installed, and all 18 files under
   `D:\games\MapleStory\` are untouched. If the v83 client is ever revived, sections 5.0–5.7 of
   `WZ-MERGE-PROCEDURE.md` still apply and these four manifests drive it unchanged.
3. **`Map.wz/{Back,Obj,Tile}` and `Sound.wz` bgm for the merged maps.** Pure client render/audio;
   `MapFactory` never reads them. `900010000/info/bgm` is `Bgm00/DragonDream`, resolved by the
   client's own archive.
4. **`Npc.wz/1013000.img/{condition1-6, stand/1-11}`** (17 add-list rows). Interior writes into a
   record this tree already has, and `stand` is a positional array — exactly the shape §4.4/§4.5
   warn about. Not needed: `1013000` already works.
5. **`String.wz/Npc.img/1013000/{d0,d1,quest}` and `/1013201/{n0,n1}`.** Same reason — interior
   writes into existing name records.
6. **`String.wz/Map.img/etc/900090104`.** v84 ships the map image but **no name entry**; not an
   omission, there is nothing to take.
7. **Five scripts the merged maps reference and this repo does not have** — map `onUserEnter`:
   `evanAlone` (this is the start map's), `evanPromotion`, `evanTogether`, `incubation_dragon`;
   portal: `outAfrienMemory`. `MapScriptManager:72` returns silently on a missing script, so the maps
   load and are simply quiet. The other 25 portal scripts and 6 map scripts the merged maps reference
   **already exist**. → **ticket 31**.
8. **`Skill.wz` — nothing owed.** Ticket 10 merged `2001.img` and all ten job images; re-verified,
   not re-merged. See `12-evan-skills.md`.
9. **`Quest.wz` — nothing owed.** Ticket 33 merged all 135 v84 quest ids including the 22xxx block;
   `QuestInfo.img/22000` verified present by test, not re-merged.
10. **`Etc.wz/NpcLocation.img/9901910-19` — never, by standing rule.**

## Review — two axes, both run before the commit

**Standards.** No `CONTRIBUTING.md` / `CODING_STANDARDS.md` exists, so there were no documented
rules to breach; the findings were against the sibling-file conventions and the Fowler baseline.
Four acted on: `fidelity.ps1`'s hardcoded `D:\…\wz-data\v84` is now a `-V84` parameter (every other
input was already one, so that line silently made the script machine-specific); a redundant
`assertFalse(MUSHROOM_TOWN == …)` that could not fail once the `assertEquals` above it passed was
deleted; `everyMergedEvanMapIsLoadable`'s javadoc promised a `life` assertion the loop never made;
and the `StatEffect` comment was cut down to point at the test that *proves* the pairing rather than
restating it, since a comment can drift and the test cannot. The 18-vs-19 map-id asymmetry between
`verify.ps1` and the other two lists (`900090104` has a map image but no `String.wz` name) is now
written into `verify.ps1` beside the list, and the hardcoded `44` carries a note saying it is
hardcoded *on purpose* — deriving it from the tree would make that check compare the tree with
itself.

**Spec.** Confirmed additive on independent spot-check: 44 untracked, all `.img.xml`; 2 tracked
files modified; `83 insertions(+), 0 deletions(-)`; `100030301` genuinely absent; `wz/Etc.wz`
untouched. It caught one procedural gap worth fixing rather than arguing: **the mount verdict had
been read from `String.wz/Skill.img`, and the dispatch said to dump it from `2001.img`.** So it was
dumped from `2001.img`, and that dump is now the primary evidence above — it is strictly stronger
than the names, because it shows the eight ids have no `damage`/`attackCount`/`mobCount` at all and
therefore cannot be Evan attack skills under any reading. The conclusion did not move.

## Repeating this

```
docs\wz-baseline\tool-merge\bin\Release\net10.0-windows\WzMerge.exe selftest
# dry run each of the four, read the conflicts, then drop the trailing "-" for the real run:
WzMerge.exe xml <v84>\Map.wz    wz docs\wz-baseline\merge-lists\13\Map.paths.txt    <c> --deny <lists>\COLLISION-DENY.txt -
powershell -File docs\wz-baseline\merge-lists\13\verify.ps1   -Root <root> -Scratch <tmp> -Rev <deliveredSha>~1
powershell -File docs\wz-baseline\merge-lists\13\fidelity.ps1 -Root <root>
```

`verify.ps1` defaults `-Rev` to `HEAD`, which is correct only *before* the merge is committed. After
the commit, pass `<deliveredSha>~1` — the script prints the baseline it resolved so a self-diff
cannot hide, and PROOF 2 fails loudly with "0 new ids … the baseline is the merged file itself".

**That recipe was RUN, not just written, and it failed the first time.** PROOF 1 counted the added
files as *untracked*, which is only the same number before the merge is committed; against
`831e9d023~1` it reported `0` and exited 1 while every other check passed. It now counts "paths
under `wz/` that exist now and were not in the baseline tree", which is 44 in both directions.
Verified against the delivered commit:

```
baseline: 831e9d023~1 -> 3438720643a87db47bd18b841ea241b1f9310786
P1 : 22445 baseline files under wz/ re-hashed from disk -> changed 2, missing 0
P1 : files under wz/ added since the baseline: 44 (non-.img.xml: 0)
P2 : Map.img/etc : pre-existing 2458 -> changed 0, missing 0; new 18 (expected 18)
P2 : Npc.img/<root> : pre-existing 7089 -> changed 0, missing 0; new 3 (expected 3)
RESULT: PASS
```
