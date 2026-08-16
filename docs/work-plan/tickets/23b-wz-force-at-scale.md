# Ticket 23b — `--force` at backport scale: measured, not assumed

**Phase B first blocker.** Phase A staged a stock v84 base (`D:\games\wz-stage\v84-base`, 17/17
byte-identical). The backport then needs to restore **6,112 live-edited images** — stock paths whose
*content* the owner changed. A presence diff cannot see them, so under a v84 base they arrive from
v84 and the owner's edits vanish **with no conflict raised**. Restoring them requires overwrite, i.e.
`--force`, which had never been run on more than a handful of nodes.

**Result: `--force` works at full scale, unchanged. 6,112 of 6,112 restored, byte-checked, nothing
lost.** One 3-line refactor was made to *pin* the invariant that makes it safe; no behaviour changed.

Everything below was measured on 2026-08-16 against **copies** under `D:\games\wz-stage\`. The live
`wz/` tree was never written to; `D:\games\MapleStory\` was opened read-only as the merge *source*.

---

## 0. Selftest, first

| | before | after |
|---|---|---|
| `WzMerge selftest` | **22 PASS / 0 FAIL** (7 SKIP) | **38 PASS / 0 FAIL** (7 SKIP), exit 0 |

The 7 `SKIP` lines are the intentional positional-array refusal demonstrations from section 1 of the
selftest, not failures. The 16 new checks are section 3, added by this ticket (§6).

---

## 1. What `--force` actually does — read off the source

`docs/wz-baseline/tool-merge/Program.cs`

| question | answer | citation |
|---|---|---|
| **Granularity** | A force entry is a **ROOT**, not an exact path. `RootOver` matches the row itself *or anything beneath it*, so a whole-image force row also authorises every node inside that image. The unit actually written is still the **manifest row**. | `LoadRoots` L210-229 ("Every listed path is a ROOT"), `Under` L231-233, `RootOver` L236-240 |
| **vs deny** | Two independent mechanisms, both hard. (a) `AssertNoOverlap` throws `BadArgs` → **exit 2** if any deny root and any force root cover the same node, in *either* nesting direction. (b) Even so, the deny gate runs *before* force is ever consulted in the merge loop, so deny wins structurally too. | `AssertNoOverlap` L282-289 (called L1411); `GateRefusal` L256-269 called at L1495, force first consulted at L1513 |
| **Forced path absent from SOURCE** | Refused `MISSING IN SOURCE — manifest is stale`, and **nothing is deleted**. The source resolve is deliberately placed *before* any `Remove()` so a force row can never degrade into a silent deletion. | L1500-1505, comment: *"a force-list row deletes the live node and puts v84's in its place, so every reason this row might fail has to be known while the live node is still there"* |
| **Forced path absent from TARGET** | Force is a no-op: it is only consulted when `existing != null`. The row lands as an ordinary additive ADD and is *not* counted as forced. | L1512-1514 |
| **Subtree-replace or child-merge?** | **SUBTREE REPLACE, WHOLESALE.** All four write branches are `existing?.Remove()` then `Add*(source.DeepClone())`. The old node and every child under it are gone; the source subtree takes its place entirely. Children present only in the target are **not** preserved. | L1531-1563 (four `case` arms) |
| **Siblings** | Untouched. Only the named node is removed; the parent's other children are never handled. Verified empirically over 29 directories (§4). | L1531-1563, plus the duplicate assertion L1572-1574 |
| **vs the positional-array gate** | A forced row **skips** `PositionalRefusal`. This is safe only because force requires `existing != null`, so a forced row is always a *replace-in-place* and can never splice a new slot into an array or punch a hole. See §5-C. | L1523 `if (fHit == null && PositionalRefusal(...))` |

**Why subtree-replace is the right shape for these 6,112 rows.** Every row is a whole `.img`. The
owner's edit is the *whole image*; v84's version of it is what must go. Wholesale replacement is
exactly the operation wanted, and the "children only the target had are discarded" caveat is
irrelevant here — the target's version is stock v84, which carries nothing worth keeping. It would
matter for a *node-level* force row, and that is the case to be careful with.

---

## 2. Deny/force overlap across the real 6,112 — the suspected blocker, measured

`AssertNoOverlap` hard-exits on any overlap, so if even one of the 188 `COLLISION-DENY.txt` roots sat
inside one of the 6,112 images, no force list for this backport could be handed to the tool at all.
Both containment directions were computed over the real sets:

```
deny roots at or beneath a LIVE-ONLY image : 0
deny roots at or above  a LIVE-ONLY image : 0
```

**Zero overlap. The blocker does not exist.** Confirmed at runtime — all 8 merges below accepted
`--deny COLLISION-DENY.txt` alongside a 6,112-root force list without complaint.

> Caveat for whoever runs the real backport: `COLLISION-DENY.txt` was authored for the *forward*
> direction (source = v84, target = live). This run uses it in the *backport* direction
> (source = live, target = v84 base), where its reasons ("v84 places npc 1022107…") do not apply. It
> is a valid, non-overlapping, safe list to pass here, but it is not a *tuned* one. That is a
> procedure decision, not a tool defect.

---

## 3. The scale run — all 6,112 rows, 8 archives, real save

Source = live client `.wz` (read-only). Target = a copy of the v84 base at
`D:\games\wz-stage\t23-force\pre\`. Output = `D:\games\wz-stage\t23-force\out\`.
`--deny merge-lists/COLLISION-DENY.txt --force <per-archive list> --live <v84-base file>`.

| archive | rows | **forced** | added | refused | exit | sec | peak RSS | out |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Etc | 1 | 1 | 1 | 0 | 0 | 0.9 | 78 MB | 1 MB |
| Item | 3 | 3 | 3 | 0 | 0 | 1.1 | 80 MB | 18 MB |
| Skill | 5 | 5 | 5 | 0 | 0 | 1.3 | 109 MB | 113 MB |
| Reactor | 19 | 19 | 19 | 0 | 0 | 0.5 | 40 MB | 52 MB |
| Mob | 28 | 28 | 28 | 0 | 0 | 3.5 | 152 MB | 475 MB |
| Npc | 261 | 261 | 261 | 0 | 0 | 0.8 | 84 MB | 50 MB |
| Character | 5075 | 5075 | 5075 | 0 | 0 | 43.3 | **3,571 MB** | 202 MB |
| Map | 720 | **681** | 720 | 0 | 0 | 12.0 | 483 MB | 601 MB |
| **total** | **6,112** | **6,073** | **6,112** | **0** | — | **63.4 s** | **3.5 GB** | **1.51 GB** |

All 8 printed `verified OK` — the tool's own post-write re-open, re-resolve and per-image content
digest — and every `conflicts.txt` is empty.

**This is not a sample. It is the entire 6,112-row set.**

### 3.1 The two numbers that are not what the inventory says

**a) 39 Map rows were added but not forced.** `Map` shows `added 720 (forced 681)`. The 39 rows that
landed without a force hit are absent from the v84 base entirely — they are `925020610.img` and the
`97003xxxx` / `97004xxxx` event/PQ maps, i.e. maps **v84 deleted**. They belong to the *removed* set,
not the live-edited set. They merged fine as plain additive adds, so nothing is broken; the
inventory's classification is just 39 rows optimistic. **True overwrite count is 6,073, not 6,112.**

**b) Peak memory scales with forced-image count, not archive size.** Character (192 MB archive,
5,075 images) peaked at **3.57 GB** — 7× the 483 MB peak of Map (629 MB archive, 720 images).
`DeepClone` materialises every cloned image and they are all held until `SaveToDisk`. Per-archive
runs keep this bounded; **do not merge multiple archives in one process**, and expect ~0.7 MB of RSS
per forced image. Character is the worst case in the set and it fits comfortably.

---

## 4. Byte-wise verification — exit code 0 was not trusted

Exit codes and the tool's own `verified OK` were both ignored for this step. Instead, for every one
of the 6,112 rows, three independent content digests were taken with `WzMerge hash` (one invocation
per parent directory, which emits one digest line per child image):

```
out    = D:\games\wz-stage\t23-force\out\<A>.wz     (the merge result)
live   = D:\games\MapleStory\<A>.wz                 (the owner's version — must match)
stock  = D:\games\wz-stage\v84-base\<A>.wz          (v84 — must NOT match)
```

| | Etc | Item | Skill | Reactor | Mob | Npc | Character | Map | **total** |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| rows checked | 1 | 3 | 5 | 19 | 28 | 261 | 5075 | 720 | **6,112** |
| **restored** (out == live) | 1 | 3 | 5 | 19 | 28 | 261 | 5075 | 720 | **6,112** |
| still stock (edit lost) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| wrong (neither) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| missing from output | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| live == stock (test would be vacuous) | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| **siblings dropped** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** (29 dirs) |

The last two rows are the ones that make this evidence rather than a tautology:

* **`live == stock` is 0 for all 6,112.** The owner's content differs from v84's for every single
  row, so `out == live` necessarily implies `out != stock`. The check discriminates.
* **Sibling check:** every child name the v84 base held in each of the 29 touched directories was
  re-asserted present in the output. **0 dropped.** Force does not eat siblings at scale.

Method note, stated precisely: `hash` compares **canonical content digests** (canvas dimensions plus
SHA-256 of the compressed PNG payload, decoded scalars, UOL link targets), not raw container bytes.
Raw bytes necessarily differ — the `.wz` is re-serialized — so a content digest is the only
meaningful comparison here. Scripts are in the session scratchpad (`t23/run.ps1`, `t23/verify.ps1`).

---

## 5. Failure modes — every one probed

| # | probe | result | exit |
|---|---|---|---|
| **A** | force root **==** deny root (`Npc.wz/9000021.img`) | **hard exit**, `deny/force overlap: … Deny wins by rule` | **2** |
| **A2** | force root **above** a deny root (force `Npc.wz`, deny `Npc.wz/9000021.img`) | **hard exit**, same message | **2** |
| **A3** | deny root **inside** a forced image | **hard exit** (selftest, §6) | **2** |
| **B** | forced path present in target, **absent from source** (`Npc.wz/1002101.img/info/script`, a v84 addition the owner never imported) | `SKIP … MISSING IN SOURCE`, **target node confirmed still present** — no silent deletion | 5 |
| **B2** | forced path absent from **both** trees | `SKIP … MISSING IN SOURCE`, nothing added | 5 |
| **C** | forced node **inside a positional array**, slot exists on both sides (`Map/Map1/100000003.img/portal/0`) | forced through, array gate bypassed — **replace-in-place, no hole possible** | 0 |
| **C2** | forced array slot that would be an **append** (`…/portal/99`) | refused — and note *why*: force is unreachable when the node is absent from the target, so the array gate still ran | 5 |
| **C3** | whole image containing positional arrays, forced wholesale (**the shape all 6,112 rows take**) | forced through as one unit — the arrays travel intact, so no splicing hazard exists | 0 |
| **D** | can force silently drop siblings? | **No.** 0 drops over 29 directories / 6,112 forced rows (§4), plus the `dupes != 1` assertion at L1572-1574 | — |

### 5.1 The one real sharp edge, and why it is not a problem here

Force *does* bypass the positional-array gate (L1523). That is only safe because of a coupling two
lines above it: force is consulted **only when the node already exists** (L1512-1514), so a forced
row is always a replace-in-place and can never create a hole. **Hoist the force check above the
`existing != null` test and the force-list silently gains hole-punching power over positional
arrays** — the exact class of damage that broke `UI.wz/MapLogin.img/back`.

That coupling was implicit and untested. It is now a named function and pinned by two selftest
checks (§6). **This is the only code change in this ticket.**

### 5.2 Cosmetic, not fixed

A force entry naming a path absent from the target is silently unused — no warning. The `added N
(forced M)` summary makes the discrepancy visible (that is how the 39 Map rows in §3.1 were found),
so this is a reporting nicety, not a correctness gap. Not worth code.

---

## 6. What changed

`docs/wz-baseline/tool-merge/Program.cs` only. **No behaviour change** — the full 6,112-row dry runs
and every probe reproduce identical output before and after (`added 5075 (forced 5075)`,
`added 720 (forced 681)`, exit 2 / exit 5 on the probes).

1. **Extracted `ForceHit(existing, force, row)`** — three lines replacing an inline `if`. It returns
   a force hit only when the target node exists, making "force never authorises an append" a
   testable fact instead of an emergent property of statement ordering, and carrying the comment that
   explains why hoisting it would be a defect.
2. **16 new selftest checks (section 3), all in memory, no disk.** Built on a 6,112-root force-list
   fixture at real backport size:
   * root semantics: row-is-root, node-beneath-root, unlisted sibling, and `Cape` must not cover
     `Cape2` (segment-boundary matching — the bypass that would force whole directories nobody listed)
   * overlap: 6,112 disjoint roots accepted; deny-inside-force, deny-above-force and deny==force all
     hard-exit
   * deny beats force even for a row on both lists
   * **force never authorises an APPEND**; force does authorise a replace-in-place
   * subtree-replace shape: siblings survive, exactly one node lands (no duplicate), a child only the
     target had is **gone not merged**, and the survivor carries the **source's** content

Selftest: **22 → 38 PASS, 0 FAIL.**

---

## 7. Verdict for Phase B

**`--force` is usable at scale as it stands. Unblock Phase B.**

* 6,112/6,112 rows landed; 6,073 real overwrites, 39 plain adds (§3.1a).
* 6,112/6,112 content-verified against the owner's own client, 0 lost, 0 wrong, 0 siblings dropped.
* 63 seconds of merge for the whole set. Peak 3.5 GB on the worst archive.
* No deny/force overlap exists across the real lists, so the 6,112-root force list is admissible.

Carry-overs for the operator, none of them tool defects:

1. Fix the inventory: 39 `Map.wz` rows are removed-set, not live-edited. Real count **6,073**.
2. Run **one archive per process**. Budget ~0.7 MB RSS per forced image.
3. Decide the deny list for the *backport* direction; `COLLISION-DENY.txt` is safe here but was
   written for the forward direction (§2).
4. The **136 CONFLICT** rows (v84 also edited the image) are still untouched by this work and need a
   per-row human decision. `--force` is the mechanism; the decision is not.
5. Re-run this against a fresh base before shipping — `wz-stage\t23-force\` is a measurement
   artefact, not an installable tree, and its target copies were never snapshot-checked against a
   live client (`--live` pointed at the v84 base, which is correct for a base-copy target).
