# The composed install — one merge per `.wz`, all content tickets at once

Built by **ticket 03f**; ticket **08** folded in and re-run end to end by **03g**, ticket **09** by
**03h**, and re-run again by **03i** with the corrected positional-array gate (two more rows refused
— see the 03i section at the bottom, which is the current state).
Tickets 04, 05, 06, 07, 08 and 09 each staged their own `.wz` **from the same pristine v83 base**, so
their outputs do not compose: installing two of them loses one set (05's ticket says so at its
"Human steps → Step 0", 06's at its step 1). The fix is to merge **once per file from the ticket
path lists**, which is what this directory is.

`compose.ps1` regenerates every `*.paths.txt` here from `..\{04,05,06,07,03f,08,09}\`. Those are the
source of truth; no `*.paths.txt` here is hand-edited. **`FORCE.txt` and this README are
hand-maintained** — `compose.ps1` does not touch either, so if you add a ticket, update `FORCE.txt`
yourself.

## What it is

| file | rows | from |
|---|---:|---|
| `Character.paths.txt` | 254 | 04 (246) + 05 (8) |
| `Item.paths.txt` | 391 | 04 |
| `String.paths.txt` | **510** | 04 (394) + 05 (7) + 06 (35) + 07 (7) + 03f (28) + 08 (39) |
| `Morph.paths.txt` | 25 | 05 |
| `Skill.paths.txt` | 27 | 05 |
| `Map.paths.txt` | **133** | 06 (34) + 07 (3) + 08 (95) + 1 composition fill |
| `Mob.paths.txt` | 28 | 06 (17) + 07 (4) + 08 (7) |
| `Npc.paths.txt` | 15 | 06 (6) + 08 (9) |
| `Reactor.paths.txt` | 3 | 06 (2) + 08 (1) |
| `Sound.paths.txt` | 24 | 06 (1) + 08 (23) |
| `Quest.paths.txt` | **252** | 09 |
| `FORCE.txt` | **41 roots** | `COLLISION-FORCE.txt` (37) + 03f's `Npc.img/9201144` + 08's 3 |

**Ticket 09 is the cheapest fold-in this directory will ever get.** No other ticket's path list
contains a single `Quest.wz` row, so `Quest.paths.txt` is 09's 252 rows verbatim and the other ten
files are **unchanged in content** — the header comment lines moved, no row did. 09 forces nothing,
so `FORCE.txt` stays at **41 roots**, all `String.wz`. The 540 rows for the 135 `22xxx` Evan ids are
**ticket 13's** and are not here; add `13` to the `Quest` entry of `compose.ps1`'s `$files` table
when they land.

**Composability was checked, not assumed.** Across all eleven files **1,662 rows, every one unique,
and no row is an ancestor of another**, so nothing can be written twice or shadow another ticket's
row. The one order-sensitive mechanism is the force path — the `existing?.Remove()` branches of the
`switch (parent, srcObj)` in `Program.cs`'s `Merge()`: a force row removes and re-adds, and
`Eqp/Dragon` is a **container-level** force root — but 04 has zero rows beneath it, so 04→05 and
05→04 give identical trees. Ordering inside each ticket block **is** load-bearing for `Map.wz`
(06's 12 dependency rows must precede the 22 map images; 08's 67 asset rows must precede its 22)
and is preserved verbatim. (03g's figure for the ten pre-09 files read **1,409**; the actual count
is **1,410** — the `Map` composition fill row was added after the sentence was written. Re-counted
mechanically here, not carried forward.)

### `FORCE.txt` is 41 roots, and reusing the old 38-root file reverts three names

08 keeps its three forces in `..\08\String.force.txt`, deliberately **not** as an edit of
`COLLISION-FORCE.txt` — that file states one rule end to end ("the live value is the literal
`MISSING NAME` / `MISSING INFO` stub") and its 37 rows are consumed exactly once, by 04 (30) and
05 (7). 08's three are a different rule again: the live value is **untranslated Korean** shipped in
the GMS v83 client for content that had not been released, and v84 is the English translation of
the same node — same shape, no field lost. They are Olaf's Voyage's two ship maps
(`Map.img/ossyria/2000900{80,90}`) and NPC `1013203` Hiver.

`String.wz/Npc.img/9201144` (03f's "Steward" → "Shadow Knight Rene") **is already applied** on the
XML side: it landed in the same `.img.xml` as 08's `1013203`, which is why 08's commit shows 14
deletions rather than 10. Do not re-apply it by hand; the binary merge re-applies it from this list
because the binary side always starts from the pristine `pre\` snapshot.

### The one composition fill

`compose.ps1` has a `$fill` table for rows the **composition** needs that no ticket's list carries.
One entry today: `Map.wz/Obj/effect.img/quest/gate/6`. `WzMerge deps` resolves the assets a map
*references*, so it emitted `gate/7` for 08's map and stopped; v84 appends **both** 6 and 7 to that
array (`add-list/Map.txt:581-582`) and the live client has `0`–`5`, so taking 7 alone leaves the
array `0-5,7`. The positional-array gate (WZ-MERGE-PROCEDURE.md §4.4) refuses that hole. Every row
in `$fill` must already be on an add-list, must be a pure addition, and must carry its reason.

## Running it

Procedure: `..\..\..\work-plan\WZ-MERGE-PROCEDURE.md` §5, one file at a time, from the repo root.

```
WzMerge merge <v84>\<Name>.wz <stage>\<T>\pre\<Name>.wz <stage>\<T>\<Name>.wz `
  docs\wz-baseline\merge-lists\composed\<Name>.paths.txt <stage>\<T>\<Name>.conflicts.txt `
  --deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt `
  --live D:\games\MapleStory\<Name>.wz
```

`--force docs\wz-baseline\merge-lists\composed\FORCE.txt` on **`String.wz` only** — all 41 force
roots are `String.wz` paths. Add it to the `xml` run for `String.wz` too, or the client and the
server disagree about the same ids.

## ⚠ Exit 3 on `Character`, `String` and `Item` is the CORRECT result, not a failure

A scripted install that aborts on non-zero will stop on these three. It should not.

| file | exit | added | forced | refused |
|---|---:|---:|---:|---:|
| `Morph` | 0 | 25 | 0 | 0 |
| `Skill` | 0 | 27 | 0 | 0 |
| `Sound` | 0 | 24 | 0 | 0 |
| `Reactor` | 0 | 3 | 0 | 0 |
| `Npc` | 0 | 15 | 0 | 0 |
| **`String`** | **3** | 501 | 41 | **9** |
| **`Item`** | **3** | 389 | 0 | **2** |
| **`Character`** | **3** | 242 | 0 | **12** |
| `Mob` | 0 | 28 | 0 | 0 |
| `Map` | 0 | 133 | 0 | 0 |
| `Quest` | 0 | 252 | 0 | 0 |

*(This table is 03g/03h's run. **03i refuses two more on `Character` — 240 added, 14 refused.**
The 03i section at the bottom is the current state; everything else in this table still holds.)*

The 23 refusals are 15 decisions taken before 04 shipped, plus 8 the **positional-array gate**
(§4.4, added by 03g) found that nobody had enumerated:

- **`String.wz`, 9 rows** — `Eqp/Hair/31660`–`31667` and `33101`. Ezorsia already names these and
  v84's string is byte-identical, so the refusal is a no-op. `V84CosmeticNodeTest
  .ezorsiaHairNamesWereNotOverwritten` pins them.
- **`Character.wz`, 2 rows** — `Accessory/01142153.img`, `01142154.img`. Cosmic turned both into
  level-up medals, so the live node is a strict **superset** of v84's; taking v84's would delete
  Cosmic content. `V84CosmeticNodeTest.cosmicLevelUpMedalsSurvivedTheRefusal` pins it.
- **`Character.wz`, 4 rows** — `Dragon/019{4,5,6,7}2002.img/info/level`. The one genuinely open
  question, recorded as an owner call in `COLLISION-FORCE.txt` (commented out, with the reason)
  and in ticket 04. Not a fault; an undecided decision.
- **`Character.wz`, 6 rows — NEW, `POSITIONAL ARRAY`.** `Glove/01082262.img/{stabTF,swingP2,
  swingPF,swingT3}/<n>/{l,r}Glove`. The live client already ships this glove ("Dragon Master's
  Proof") with **different art**: live `stabTF/2/rGlove` is 6x5 at origin (-7,-15), v84's is 5x5 at
  (2,2), and the frames the rows target hold a *live* `rGlove` whose digest differs from v84's.
  Merging only the `lGlove` layer pairs v84's left glove with Ezorsia's right one inside one
  animation frame. **This is the same shape ticket 08 refused at `220011000.img/portal/4`** — a
  partial merge of an entity both trees have and disagree about — and 04 merged it because nothing
  was looking. Cost of the refusal: one glove item renders without its left-hand layer on four
  frames. Cosmetic, and reversible by force-listing the four frames if an owner decides so.
- **`Item.wz`, 2 rows — NEW, `POSITIONAL ARRAY`.** `Consume/0202.img/020225{03,14}/reward/43`.
  This is the MonsterBook hazard again, in `Item.wz`: the live box has 43 reward slots, v84 has 44,
  and v84's slot 43 (`item 2020014`) is **content-identical to the live client's slot 16**. The
  arrays diverged — Ezorsia rewrote the table — so the "append" doubles an entry's drop weight
  rather than adding anything. Refusing is the only correct answer.

`Sound.wz` exits **0** only on a `WzMerge` built after ticket 03f: the post-write verifier now
discounts, per image, images that were **already** unparseable in the merge target, which is what
`Sound.wz/BgmGL.img` is in all three trees. An image that parses in the target and fails in the
output still fails. Confirmed again here — `Sound` merged 24 rows, verified, promoted, exit 0.

The composed `Sound` list is **24 rows and disjoint**: 06's single `Bgm14.img/DragonRider` plus
08's 23 `Mob.img/<id>` SFX banks — 08's own seven mobs, the four 07 merged and reverted because
`Sound.wz` was not 07's file, and twelve of 06's mobs that `06\Sound.paths.txt` never claimed.
Nothing in any ticket *depends* on any of it; the cost of dropping it would be silent mobs.

## Verified end to end, 2026-08-16 (ticket 03g)

Staging `D:\games\MapleStory\Server\wz-merge\03g\`. All 18 live `.wz` SHA-256-matched
`_backup\client-v83-EzorsiaV2-2026-08-15\` before and after; every `pre\` snapshot hash-matched its
live file (`--live` on every run). All ten outputs passed the tool's own post-write verification —
path re-resolution, full-file parse, and per-image content digest — and were promoted; no
`.partial` was left behind. Gate re-fire against the tool's own output:
`String added 0, refused 510` / `Map added 0, refused 133` / `Sound added 0, refused 24`, **exit 5**
on all three.

§6.1 content digest, `pre\` vs post, on every image the composition inserted into:

| image | children | differing lines | reads as |
|---|---|---:|---|
| `String.wz/Map.img` | 12 → 12 | 6 | `etc` (19 adds), `ossyria` (2 forced), `TOTAL` |
| `String.wz/Npc.img` | 7,075 → 7,089 | 26 | 14 new ids, 5 changed children (`1013203` + `9201144` forced, `1063018`/`1205000`/`2012034` gained `d0`/`d1`), `TOTAL` |
| `String.wz/Mob.img` | 1,597 → 1,620 | 25 | 23 new ids, `TOTAL`; nothing pre-existing moved |
| `Map.wz/…/200080600.img` | 16 → 16 | 6 | `1` (two `obj` appends), `portal` (one append), `TOTAL` |
| `Map.wz/…/251010403.img` | 16 → 16 | 6 | `4`, `portal`, `TOTAL` |
| `Map.wz/…/106010102.img` | 17 → 17 | 4 | `portal`, `TOTAL` |

The three route maps are the ones 08 appends into, and the digest confirms the appends touched
**only** the arrays they appended to.

Output SHA-256:

```
Character  25AEA9B984E23AE17C9D9FBF63CE8FEC783A38B60AAAB73FCE927A0608C94337   211,839,641
Item       E32C6DA8E71F4222D8BC90F37B88DCF887EBCA1876012D65B81CED3AED70145D    19,086,443
Map        A5ECD6491265A9A50BAC88F702E90BF118298841D12A30961E846C01927474B9   646,394,097
Mob        C358A522458BDF501DDB84BE97B9DCBA6493B179A0B4A0977B23AAE5528BED34   497,224,252
Morph      E8E3D94E19B6CC8B3ADA097152216423547B9A63ACB59569AE0C76E7BBE4852D     6,322,806
Npc        F1F49B42880B8A9C2BC9DF185C813F6E076043D9F91919F49F2D509F1023ECEF    54,155,520
Reactor    83ABEDA9F290223CD9B1159AF3114020E31CC2DAE07725C3A317E67594BFD7C3    54,876,823
Skill      69AE95DF8380EC2268665A1205CD35F42B6DCBEBC85E6049C12657518BF95B49    80,213,925
Sound      09870377512C832C4F44A62DB0C3B98638FAF529AC0CE14A71D1FA974019A796   365,641,276
String     04ADEF719A3A9CE0AD12ADDA929B848ADE5F27F60A70ACF1CE2C3E722C40336B     3,612,239
```

## Re-verified end to end with ticket 09 folded in, 2026-08-16 (ticket 03h)

Staging `D:\games\MapleStory\Server\wz-merge\03h\`, **eleven** files, same run book, deny-list now
**156 roots** (03c's 28 + 08's 12 + 09's 116).

| file | exit | added | forced | refused |
|---|---:|---:|---:|---:|
| `Morph` `Skill` `Sound` `Reactor` `Npc` `Mob` `Map` | 0 | 25 / 27 / 24 / 3 / 15 / 28 / 133 | 0 | 0 |
| **`Quest`** | **0** | **252** | 0 | **0** |
| `String` | 3 | 501 | 41 | 9 |
| `Item` | 3 | 389 | 0 | 2 |
| `Character` | 3 | 242 | 0 | 12 |

**All ten pre-existing outputs are byte-identical to 03g's**, not just `Morph` and `Skill` — every
SHA-256 in the table above reproduces exactly. Folding in a ticket that owns a file no one else
touches changes nothing else, and the run proves it rather than asserting it. `Quest.wz` is
`5F37E5F56970FFD01DC5B28D082BF02CE130FF7B56C7C1B592C67D77996F04FE`, 6,083,413 bytes — **the same
hash ticket 09 staged from its own list**, so 09's merge re-derives bit-for-bit from the composed
manifest.

`Quest.conflicts.txt` is header-only; the 23 refusals across `String`/`Item`/`Character` are the
same 23 documented above, unchanged. All eleven outputs passed the tool's own post-write
verification and were promoted (`Quest`: `6 images parsed, 0 unparseable, 0 requested paths missing,
4 images content-checked, 0 drifted`); no `.partial` was left behind. Gate re-fire on the tool's own
`Quest.wz`: **`added 0, refused 252`, exit 5.** All 18 live `.wz` still SHA-256-match
`_backup\client-v83-EzorsiaV2-2026-08-15\` — 0 mismatches, no stray `.partial`/`.TEMP`/`.merged` in
the client directory. Nothing was written to `D:\games\MapleStory\`.

**`Morph` and `Skill` are byte-identical to 03f's run** — same lists, same base, no refusal change,
and the merge is deterministic, so the composition re-derives exactly. The other eight differ, each
for a stated reason: `Map`/`Mob`/`Npc`/`Reactor`/`Sound`/`String` gained 08's rows (and `Map` the
fill row), `Character` lost 6 rows and `Item` 2 to the positional-array gate. 03f's claim that
`Npc` and `Reactor` were byte-identical to their single owner no longer applies — they have two
owners now.

## Server XML

The XML tree in `wz/` is **already composed** — every ticket spliced its own rows in place, so
there is no XML equivalent of this directory to run, and 08's splices (including the `9201144`
force) were in the tree before this run. What would need re-applying is only what a *new* ticket
adds. `WzMerge xml` enforces the same positional-array rule as the binary side, minus the
content-identical check (§4.4).

## Re-run with the corrected positional-array gate, 2026-08-16 (ticket 03i)

Staging `D:\games\MapleStory\Server\wz-merge\03i\`, eleven files, same run book, deny-list now
**188 roots** (03c's 28 + 08's 12 + 09's 116 + 03i's 32).

03i widened the array rule to any **consecutive run of integers**, not only one starting at 0.
That is one clause, and it changes exactly one file:

| file | exit | added | forced | refused |
|---|---:|---:|---:|---:|
| `Morph` `Skill` `Sound` `Reactor` `Npc` `Mob` `Map` `Quest` | 0 | 25 / 27 / 24 / 3 / 15 / 28 / 133 / 252 | 0 | 0 |
| `String` | 3 | 501 | 41 | 9 |
| `Item` | 3 | 389 | 0 | 2 |
| **`Character`** | **3** | **240** | 0 | **14** |

**All ten other outputs are byte-identical to 03g's and 03h's** — every SHA-256 in the table above
reproduces exactly, and `Quest.wz` is again
`5F37E5F56970FFD01DC5B28D082BF02CE130FF7B56C7C1B592C67D77996F04FE`, 6,083,413 bytes, still the file
ticket 09 staged from its own list. `Character.wz` is
`06F0FA886E5F53C487E1FC4AEA2151189203E0D50186C73122AB142BB13C46E4`, **211,839,146 bytes** — 495
bytes smaller than 03h's, the two refused rows and nothing else.

**Proof that "nothing else" is not an assertion.** `WzMerge hash` at the `Character.wz` root: the
**only** differing child between 03h's output and 03i's is `Glove` — `Hair`, `Weapon`, `Dragon`,
`Afterimage`, `Accessory`, `Cap` and every other directory digest-identical. Inside
`Glove/01082262.img`, the only children that differ are `swingT2` and `swingO3`, and both now digest
**equal to the live client's**. Against the `pre\` snapshot, the only children of that image which
changed at all are `ladder` and `rope` — the two containers the live client ships **empty**.

The two rows are also on `COLLISION-DENY.txt` (Hazard 2d), and `Item.wz`'s two `reward/43` rows were
added to it (Hazard 2e) at 03j's request: the binary gate refuses them by digest, but `WzMerge xml`
is a text scan and cannot, so without a deny row the next XML run silently re-applies the duplicate
that 03j had just reverted. Their `conflicts.txt` line is now `DENIED by deny-list` rather than
`POSITIONAL ARRAY`; the output is unchanged, which is why `Item.wz` still reproduces byte-identical.

All eleven outputs passed the tool's own post-write verification and were promoted; no `.partial`
was left behind. All 18 live `.wz` SHA-256-match `_backup\client-v83-EzorsiaV2-2026-08-15\` **before
and after** this run — 18/18, no stray `.partial`/`.merged`/`.TEMP` beside them. Nothing was
installed.

### The 25 refusals

The 23 above **plus the two glove rows**, and two of the 23 are now reported as `DENIED` rather than
`POSITIONAL ARRAY` (same rows, same outcome — `Item.wz` `reward/43`):

- **`Character.wz`, 2 rows — NEW at 03i, `POSITIONAL ARRAY`.**
  `Glove/01082262.img/swingT2/2/rGlove` and `.../swingO3/0`. Live `swingT2` is `{1,2}` and `swingO3`
  is `{1}` — consecutive runs that do not start at 0, which the old rule did not recognise as arrays
  at all. `swingT2/2/rGlove` put v84's 12x9 right glove into the frame holding Ezorsia's 12x8 left
  one; `swingO3/0` prepended a v84 frame whose right glove is 47x9 against the live 6x5. Same item,
  same reasoning, same cost as the six already refused above: four animation frames keep Ezorsia's
  art. Reversible the same way — force-list them if an owner decides otherwise.

### ⚠ Six forced `String.wz` names take untranslated Korean, and are reversible in one place

Called out here because `COLLISION-FORCE.txt` discloses it in its own row comments and nothing
surfaced it where an owner would look. 08's three Korean **NPC** names were flagged as reversible;
these six are the same class and the same choice is available:

| force root | live value | v84 value |
|---|---|---|
| `String.wz/Eqp.img/Eqp/Accessory/1142143` | `MISSING NAME` | untranslated KMS text |
| `String.wz/Eqp.img/Eqp/Accessory/1142145` | `MISSING NAME` | untranslated KMS text |
| `String.wz/Eqp.img/Eqp/Accessory/1142149` | `MISSING NAME` | untranslated KMS text |
| `String.wz/Eqp.img/Eqp/Accessory/1142150` | `MISSING NAME` | untranslated KMS text |
| `String.wz/Eqp.img/Eqp/Accessory/1142151` | `MISSING NAME` | untranslated KMS text |
| `String.wz/Eqp.img/Eqp/Longcoat/1051176` | `MISSING NAME` | untranslated KMS text |

**To reverse:** delete those six lines from `composed\FORCE.txt` and re-run `String.wz` on both the
binary and the XML side. The items then read `MISSING NAME` again, which is what they read today on
the live client. Nothing else in the composition depends on them. The other 35 force roots replace a
`MISSING NAME` / `MISSING INFO` stub with an **English** name and are not in question.

**These outputs supersede 03g's and 03h's.** Re-run this composition whenever a ticket is folded in
— add it to `compose.ps1`'s `$files`, bump the `$expect` total in the same commit, and run §5 again.
The run is cheap (minutes) and the point of it is that it is repeatable.
