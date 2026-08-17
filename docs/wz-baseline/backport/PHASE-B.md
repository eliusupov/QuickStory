# Ticket 23 phase B — the v84 tree carrying the owner's content, built and content-verified

**Output tree: `D:\games\wz-stage\phaseB\tree\` — 18 `.wz`, 2.1 GB. NOT installed anywhere.**
Hashes in `phase-b/TREE-MANIFEST.tsv`. The live client and `D:\games\MSv84\client\` were read only.

Everything below was measured on 2026-08-17. Every number was **re-derived**, not carried forward,
and where it disagrees with the phase-A README the disagreement is named. **Exit codes are not
evidence anywhere in this document** — every claim about content is a digest comparison.

---

## 0. Selftest, first

```
WzMerge selftest   ->  38 PASS / 0 FAIL / 7 SKIP, exit 0
```
The 7 `SKIP` lines are the intentional `UI.wz/MapLogin.img/back/48..54` positional-array refusal
demonstrations. Re-run after the one tool change in §7: **still 38 PASS / 0 FAIL**.

---

## 1. The three sets, re-derived

| set | phase-A claim | **re-derived** | verdict |
|---|---:|---:|---|
| removed (v84 deleted it, the owner still has it) | 3,945 | **3,945** | confirmed; 3,969 rows − 24 the owner deleted himself |
| protect (owner-only, in neither stock tree) | 17,569 | **17,569** | confirmed; raw `wc -l` is 17,633, overhead is exactly 16 × 4 = 64 |
| live-edited (stock path, owner changed the content) | 6,248 | **6,248** | confirmed; **6,112 LIVE-ONLY + 136 CONFLICT** |
| ↳ of which really overwrites | 6,073 | **6,073** | confirmed **by set arithmetic**, independently of the merge |

**The 6,112 → 6,073 correction is reproducible without running anything.** `removed ∩ LIVE-ONLY` is
exactly **39 rows, all `Map.wz/Map/Map9`** — `925020610.img` (Mu Lung Dojo 6F) plus the `9700322xx`,
`9700327xx`, `9700422xx` event/PQ blocks. They are absent from the v84 base entirely, so they are
removed-set rows that the BlockSize diff also flagged as live-edited. T23b found them by noticing
`added 720 (forced 681)`; they fall straight out of intersecting the two manifests. **They are
force-listed nowhere in this phase — they land as ordinary additive adds from the removed set**, so
every row in this backport has exactly one owner.

### 1.1 The overlap nobody had measured: 4,044 protect rows ride inside forced images

`--force` is **subtree-replace wholesale** from the live client. **4,044 of the 17,569 protect rows
sit inside one of the 6,073 forced images**, so they arrive with it and must not also be listed as
additive rows — listing them would produce 4,044 `already exists in target` refusals that read like
damage and are not. They are recorded as `SKIP … inside a forced image, arrives with it` in the
manifest. `removed ∩ protect` is 0 and `protect ∩ LIVE-ONLY` is 0; no other set overlaps exist.

### 1.2 What that adds up to

```
force   (live-edited, subtree-replace)                   6,073
removed (additive)                                       3,945
protect (additive, minus the 4,044 that ride along)     13,525
                                                       ------
manifest rows handed to WzMerge                         23,543
+ protect rows arriving inside forced images             4,044
+ CONFLICT rows deliberately NOT merged (§5)               136
                                                       ------
total units accounted for                               27,723
```

---

## 2. The merge — 12 archives, one archive per process

Source = the live client (read-only). Target = `phaseB\pre\` (a copy of `v84-base`,
**17/17 SHA-256-verified against `backport/v84-base-tree.sha256` before use**). Output =
`phaseB\out\`. Every run: `--deny merge-lists/COLLISION-DENY.txt` (192 roots) `--force <per-archive>`
`--live <v84-base file>`.

| archive | rows | force | added | forced | refused | exit | sec | peak |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Sound | 2 | 0 | 2 | 0 | 0 | 0 | 1.3 | 108 MB |
| Etc | 2,033 | 1 | 962 | 1 | 1,071 | 3 | 3.2 | 177 MB |
| Skill | 8 | 5 | 8 | 5 | 0 | 0 | 1.5 | 165 MB |
| Reactor | 21 | 19 | 21 | 19 | 0 | 0 | 0.4 | 28 MB |
| Item | 103 | 3 | 83 | 3 | 20 | 3 | 1.7 | 136 MB |
| UI | 48 | 0 | 48 | 0 | 0 | 0 | 1.1 | 138 MB |
| Quest | 244 | 0 | 10 | 0 | 234 | 3 | 3.0 | 325 MB |
| String | 7,604 | 0 | 6,221 | 0 | 1,383 | 3 | 3.7 | 202 MB |
| Npc | 5,654 | 261 | 5,648 | 261 | 6 | 3 | 5.0 | 210 MB |
| Mob | 838 | 28 | 838 | 28 | 0 | 0 | 9.2 | 380 MB |
| Map | 1,768 | 681 | 1,621 | 681 | 147 | 3 | 16.2 | 1,435 MB |
| Character | 5,220 | 5,075 | 5,211 | 5,075 | 9 | 3 | 43.0 | **3,575 MB** |
| **total** | **23,543** | **6,073** | **20,673** | **6,073** | **2,870** | | **89.3 s** | **3.5 GB** |

`exit 3` is "rows refused", which is the by-design outcome for every archive that touches a
positional array. Every archive printed `verified OK` — the tool's own re-open, re-resolve of every
manifest path and per-image content digest. **Peak memory is 0.7 MB per forced image and does not
depend on archive size**; Character is the worst case and fits. Four archives (`Base`, `Effect`,
`Morph`, `TamingMob`) and `List.wz` carry no owner content in any of the three sets, so v84 stock is
correct for them and no merge was run.

---

## 3. Content verification — the part that is evidence

### 3.1 The 6,073 forced images: exhaustive, three trees

For every forced image, three digests: `out` (merged), `live` (the owner's client), `stock`
(v84 base). `WzMerge hash <wz> <dir>` emits one digest per child, so a whole directory costs one
invocation per tree.

| | Etc | Item | Skill | Reactor | Mob | Npc | Map | Character | **total** |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| checked | 1 | 3 | 5 | 19 | 28 | 261 | 681 | 5,075 | **6,073** |
| **restored** (`out == live`) | 1 | 3 | 5 | 19 | 28 | 261 | 681 | 5,075 | **6,073** |
| still stock (edit lost) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| wrong (neither) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| missing from output | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| **`live == stock` (test vacuous)** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| siblings dropped | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** (30 dirs) |

**The discriminator is real.** `live == stock` is 0 for all 6,073 — the owner's content differs from
v84's for every single row — so `out == live` necessarily implies `out != stock`. Without that line
the whole table would be a tautology.

### 3.2 The 14,600 additive rows: exhaustive on 11 archives, sampled on Mob

Each landed additive row's own subtree in `out` must equal the owner's client, and must be **absent
from the v84 base** (that absence is the discriminator here — an additive row that v84 already had
would prove nothing).

```
rows checked  13,858     match 13,858     mismatch 0     missing 0     already-in-v84 0
   exhaustive  13,790  (Character, Etc, Item, Map, Npc, Quest, Reactor, Skill, Sound, String, UI)
   sampled         68  (Mob: 60 of 711 parent nodes)
```

**UNPROVEN:** 742 of Mob.wz's 810 additive rows were not individually content-checked. They are
`<mobid>.img/info/<scalar>` rows on the same `DeepClone` path as the 68 that were, and all 735
inserted-into Mob images passed the merge's own pre-save/post-save digest check; but they were not
compared to the owner's client one by one.

### 3.3 "Only the named rows changed" — executed, not asserted

Digest each archive at its root in `pre` vs `out` and descend **only where the digests differ**,
until image level. A subtree whose digest matches is proven untouched by one comparison.

```
13,052 images changed across 11 archives.  ALL 13,052 named by the manifest.
collateral changes: 0        images that disappeared: 0        86 probe pairs
```

**UNPROVEN: `Sound.wz`.** `hash` cannot walk it — `BgmGL.img` is unparseable by MapleLib in all
three trees (phase A note 4). The probe fails symmetrically, so its "0 changed" is a failed
measurement, not a result. Its 2 rows landed and its own merge verify passed with the pre-existing
unparseable image discounted.

---

## 4. Positional arrays — bigger than phase A saw, and refused correctly

Phase A measured this only inside `removed-list/Map.txt`. Across the whole backport it is
**2,854 rows over 544 distinct arrays**, 99.4% of all 2,870 refusals. `phase-b/ARRAYS.tsv` lists
every array; `phase-b/REFUSED.tsv` lists every row.

| shape | arrays | rows | what each row would need |
|---|---:|---:|---|
| `String.wz/<X>.img/<id>/reward` (MonsterBook) | 291 | 1,383 | 292 appends that would **duplicate an entry the array already holds** — v84 inserted earlier, so every later slot is his content shifted one place — plus 1,091 that cascade off them. Align the two `reward` arrays **by content**, then re-author the tail. |
| `Etc.wz/Commodity.img` (Cash Shop) | 2 | 1,044 | rows writing **into an existing slot**. The Cash Shop commodity table is renumbered between v83 and v84. Ticket 28 already built `append-commodity.py` for exactly this table — reuse it, do not hand-merge. |
| `Quest.wz/<X>.img/<id>/<step>` | 171 | 233 | rows writing a field into a quest step that already exists. This is ticket 09's `lvmax` case: the indices line up **and it is still wrong**, because the row changes what an existing record means. Needs a per-quest decision, not an index check. |
| `Map.wz/.../life`, `/portal`, `/foothold`, `/<layer>/obj` | 39 | 136 | dump both arrays, match entries **by name/position/id rather than by index**, then re-author or deny. |
| `Item.wz/Consume/<X>.img/<id>/reward` | 20 | 20 | same duplicate-append shape as MonsterBook. |
| `Character.wz/<Cat>/<id>.img/<anim>` | 7 | 9 | 4 are **PREPENDs** (`swingPF/0`, `stabT2/0` — the source numbers the container from a different origin than the target, i.e. the arrays are not aligned at all); 5 write into an existing frame. |
| `Npc.wz/1013000.img/stand`, `Map.wz/WorldMap` | 2 | 2 | writes into an existing slot. |

### 4.1 The specific question phase A asked: `removed-list/Map.txt`

| | |
|---|---:|
| rows | 1,017 |
| whole deleted map images | 832 |
| sub-node rows in maps that survive at v84 | 181 |
| **of those, positional-array rows** | **165** |
| ↳ minus rows the owner had already deleted himself | 3 → **162 backport targets** |
| ↳ **landed** (the gate allowed them as pure appends) | **38** |
| ↳ **refused** | **124** |
| non-positional sub-node rows (`info/VR{Top,Bottom,Left,Right}`) | 16, all landed |

**Phase A's `165` is the right total but its breakdown is wrong.** It reads
"165 of them sit under `life` (70), `portal` (64), `foothold` (11)" — that sums to **145**. The
missing 20 are `<layer>/obj/<n>` (19) and `ladderRope/1` (1), which are positional arrays too.
Corrected: `life` 70 · `portal` 64 · `foothold` 11 · `obj` 19 · `ladderRope` 1 = **165**.

And **38 of the 162 landed**, which phase A did not anticipate: they are genuine tail appends
(`life/26`, `life/27` …) onto arrays that still agree up to that index. The hand-work is **124 rows**
in `Map.wz`, not 165.

---

## 5. The 136 three-way conflicts — grouped, NOT resolved

v84 and the owner both edited these stock images. Nothing was merged for any of them; the tree
carries **v84's version**, and the owner's version is intact in his client. Full table with the
per-child and per-field breakdown: `phase-b/CONFLICTS-136.tsv`.

Method: digest each image's direct children in **v83-stock, v84 and live**. A child differing
live-vs-v83 is *his* edit; differing v84-vs-v83 is *v84's*. Where both moved the same child, descend
one more level and repeat.

| group | n | meaning | recommendation |
|---|---:|---|---|
| **A — disjoint at child level** | 8 | the two sides edited different children of the image | **Keep both.** Merge his children in; nothing of v84's is displaced. `Item.wz/Consume/0202.img` (his 9 ids vs v84's 9 different ids), `0204.img`, `Item.wz/Etc/0400.img`, `Install/0301.img`, `String.wz/Pet.img`, `Map.wz/MapHelper.img` (his `AvatarMegaphone` vs v84's `mark`), `Mob.wz/6300005.img` and `9300196.img` (his `die1` vs v84's `info`). |
| **B — disjoint one level down** | 71 | same child (`info`), **different fields inside it** | **Keep both.** Of these **54 are `Mob.wz/<id>.img/info`** — he set `boss`/`hpTagColor`/`HPgaugeHide`, v84 set other fields. 11 Character, 3 Skill, 1 Etc, 1 Quest, 1 String. A field-level merge is mechanical; an image-level `--force` would be wrong here because it discards v84's field. |
| **C — true overlap, same field** | 40 | both sides moved the same field | **His decision.** Largest sub-group: **17 `Character.wz/<Cat>/<id>.img/info/level`** — worked example `Cap/01002777`: `level/info/1/exp` is `80` in both v83 and v84, **`10000` in his client**. He re-tuned the equip level-up curve; v84 also touched `level`. He also changed `info/icon`/`info/iconRaw` on all 17 (his HD art), and those are *his alone*. **Recommend keeping his version** — it is a deliberate balance change and his rule is additive-only — but say so rather than do it. Rest: 12 `Map.wz` (colliding `life`/`obj` slots — these are the §4 array problem wearing a different hat), 3 `Item.wz/<id>/info`, 3 `Character` TamingMob animation sets, 1 `Skill.wz/MobSkill.img/200/level`, 4 String/Quest. |
| **D — wholesale, overlap too wide to drill** | 17 | both sides rewrote a large table | **His decision, per table.** `String.wz/MonsterBook.img` (224 colliding ids), `Consume.img` (155), `Npc.img` (145), `Skill.img` (71), `Cash.img` (10); `Quest.wz/Check.img` (93), `Say.img` (5); `Etc.wz/Commodity.img` (31); `UI.wz/UIWindow.img` (13); 7 `Character.wz` equip/mount images (9–30 each). These are the same tables §4 flags — `Commodity.img` and `MonsterBook.img` appear in both lists. Deciding the table decides both. |

**Zero `SAME-EDIT`**: there is no conflict where the two sides happened to make the identical change.

**79 of the 136 (groups A and B) are mechanically resolvable with a field-level merge and no
judgement.** That is the single largest actionable reduction in this phase — phase A costed all 136
as hand work.

---

## 6. What the deny list blocked, and why it needs a decision

`COLLISION-DENY.txt` was **required** on every run and it hard-exits on any deny/force overlap.
**Overlap measured across the real 192-root list and the real 6,073 force roots: 0 in both nesting
directions**, so it was admissible.

It refused **15 additive rows**. All 15 are *the owner's own content*, and **every deny reason is a
forward-direction reason** ("v84 portal appended to a live Leafre map"). In this direction the source
IS his client, so those reasons do not describe what the row does:

| rows | root | forward-direction reason | in THIS direction |
|---:|---|---|---|
| 9 | `Map.wz/.../{portal,foothold}` on 109090000, 220011001, 221000000, 240000000 | v84 appends spawn points / portals to a live map | his v83 portal fields — and **all 9 write into an existing slot, so the array gate would refuse them anyway**. The deny is redundant for these; they belong to the §4 pile. |
| 4 | `Npc.wz/9000021.img` | 24 UOL-shape refusals leave the NPC partially merged | blocks restoring his `say2`/`stand2`/`observation`/`info/script` on Maple Administrator. |
| 1 | `Npc.wz/9901000.img` | PlayerNPC allocator band 9900000–9906599 (`NpcId.java:38`) | **correct to keep**: a rank-NPC slot the server allocates. |
| 1 | `Quest.wz/Exclusive.img/medal` | live groups medals under `medal`, v84 under `0`/`1`/`2` | **correct to keep**: ticket 09 proved the two partitions are not mergeable piecemeal. |

**Decision needed:** a backport-direction deny list. Of the 15: 2 are clearly right as they stand
(`9901000`, `Exclusive.img/medal`), 9 are redundant (the array gate refuses them regardless), and
**4 — `Npc.wz/9000021.img`, Maple Administrator — are the only rows the deny list uniquely costs
him.** Nothing was overridden; the dispatch required the current list and it was used unchanged.

---

## 7. One tool change, and the false-failure that forced it

**`Mob.wz` failed content verification on the first real run: `230 of 735 inserted-into images
drifted`, exit 4, output left as `Mob.wz.partial` and not promoted. That refusal was correct
behaviour and it was investigated, not overridden.**

What the drift was, measured before any change:

* dimensions, `origin`, `head`, `delay` and every scalar **identical**; the only structural
  difference in a 12-image sample was **the row the merge added** (`info/mobType`).
* PNG payloads **1–2% smaller**: `840→834`, `938→934`, `619→611`. A colour-format downgrade would be
  about −50%.
* the sibling animations that drifted (`die1`, `hit1`, `move`, `stand`) are **byte-identical between
  v83 and v84** — the merge had no reason to touch them at all.

The cause, read off MapleLib (`a7c38edf`): `WzCanvasProperty.WriteValue` serialises **every** canvas
through `WzPngProperty.GetCompressedBytesForExtraction` (`WzCanvasProperty.cs:183`), which is not a
pass-through. A payload stored in **listWz form** (XOR-blocked, non-standard zlib header) is
decrypted, **inflated to exactly `Format.GetDecodedSize(w,h)` bytes**, and **re-deflated at
`CompressionLevel.Optimal`** behind a standard `78 9C` header. `Width`, `Height` and `Format` are
written verbatim beside it. **The pixel buffer is bit-identical across that; only the deflate
encoding changes.** `Canon` was hashing the *raw stored* bytes, so it reported a merge that had
changed nothing as content drift. Npc.wz drifted 0 in the same run because its canvases are already
standard zlib — the false positive fires per canvas, on whichever archive Nexon packed in listWz form.

**Change: one expression in `Canon` (`Program.cs`), `GetCompressedBytes` → `GetCompressedBytesForExtraction`.**
The digest is now *what this canvas serialises to*, so the pre-save/post-save comparison is an
identity the writer itself guarantees, and a cross-tree `hash` no longer reports a packing difference
as a content difference. **It does not weaken the check**: a truncated or garbled payload still
fails, because the inflate is length-validated, throws, and the function falls back to the raw bytes
— which differ.

Proof, run on the *already-written* `Mob.wz.partial` with the new digest and no re-merge:

```
Mob.wz/0100100.img          v84-base            merged out          owner's client
  die1                      bde00bc9…    ==     bde00bc9…    ==     bde00bc9…
  hit1 / move / stand       identical across all three
  info                      9a1ce528…     !=    7759f35e…     !=    fb82abf6…
```

Four subtrees that genuinely never changed now agree three ways; `info` — the one the merge touched —
still differs three ways. **The check discriminates.** Re-merged: `verified OK`, **0 drifted**,
838/838 added, exit 0.

`selftest` after the change: **38 PASS / 0 FAIL**, unchanged. No merge behaviour was touched.

> Consequence for the record: canvas digests printed by `WzMerge hash` before this change are not
> comparable to ones printed after it, for listWz-packed canvases. T23b's 6,112-row table used the
> old digest; §3.1 re-establishes the same result with the new one on 6,073 rows.

---

## 8. Reproduce it from scratch

Nothing here needs me. From PowerShell, in this order (the live client must be closed for step 6
only if you install; the build itself never writes to it):

```powershell
# 0. tool
cd docs\wz-baseline\tool-merge; dotnet build -c Release
.\bin\Release\net10.0-windows\WzMerge.exe selftest          # expect 38 PASS / 0 FAIL / 7 SKIP

# 1. lists  (re-derives all three sets from the manifests; no prior number is trusted)
python docs\wz-baseline\backport\phase-b\build-lists.py D:\games\wz-stage\phaseB\lists

# 2. base   (and prove it IS the base)
Copy-Item D:\games\wz-stage\v84-base\*.wz D:\games\wz-stage\phaseB\pre\
#   then Get-FileHash each against backport\v84-base-tree.sha256 - expect 17/17

# 3. dry run every archive, read the conflicts, then merge for real
.\run-phaseB.ps1 -Dry
.\run-phaseB.ps1            # ~90 s total; ONE ARCHIVE PER PROCESS, never in parallel

# 4. verify by content
.\verify-phaseB.ps1         # the 6,073 forced images vs owner and vs stock
.\verify-additive.ps1       # the additive rows vs owner  (-SampleParents 60 for Mob)
.\collateral-phaseB.ps1     # prove only the named images changed

# 5. assemble + hash the deliverable
.\assemble-tree.ps1         # -> D:\games\wz-stage\phaseB\tree\  + TREE-MANIFEST.tsv

# 6. conflicts (report only, resolves nothing)
.\conflicts-triage.ps1 ; .\conflicts-drill.ps1
```

Scripts: `docs/wz-baseline/backport/phase-b/`. Peak RSS ≈ 0.7 MB per forced image; budget 4 GB for
`Character` and run it alone. `run-phaseB.ps1` orders archives smallest-first so a failure surfaces
in seconds and the 3.5 GB archive runs last.

---

## 9. What is NOT done, stated plainly

1. **2,870 rows of his content did not land.** 2,854 refused by the positional-array gate (§4),
   15 by the deny list (§6), 1 "already exists in target". **This portion cannot be safely
   automated** — index `n` is not an identity, and the tool is right to refuse. Every row is listed
   in `phase-b/REFUSED.tsv` with its array in `phase-b/ARRAYS.tsv`.
   *Correction to that last row:* `REFUSED.tsv` labels
   `Map.wz/Map/Map9/970032200.img/foothold/6` as `ALREADY EXISTS in v84 base`. It does not — it
   already existed in the **target**, because the whole image was added earlier in the same run from
   the removed set. Its content is present in the output. **That row is not a gap.**
2. **The 136 conflicts are unresolved by design.** 79 are mechanically resolvable (§5 A+B) but
   nothing was merged for them; 57 need his decision.
3. **A backport-direction deny list does not exist.** The forward-direction one was used unchanged,
   as instructed, and it blocked 13 rows for reasons that do not apply in this direction (§6).
4. **742 of Mob.wz's 810 additive rows are content-UNPROVEN** (§3.2).
5. **`Sound.wz`'s collateral check is UNPROVEN** — `BgmGL.img` cannot be walked (§3.3).
6. **Nothing was launched.** No client started, no server touched, nothing installed. Whether the
   tree boots is unmeasured and needs a launch.
7. **`EzorsiaV2_UI.wz` was copied wholesale** into the tree. It exists only in his client and has no
   stock baseline in any manifest, so it was not diffed — ticket 30.
8. **The 810 Boss Rush maps' asset self-sufficiency is still unproven** (phase A note 3). The map
   images are in the tree; whether the `Back`/`Obj`/`Tile`/`Bgm` they reference survived v84 was not
   checked, and a reversed-direction `deps` is still the way to check it.
