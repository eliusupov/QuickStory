# Ticket 23c — phase B executed: a v84 tree carrying the owner's content

**Full report and every number: `docs/wz-baseline/backport/PHASE-B.md`.**
**Artifacts and reproduction scripts: `docs/wz-baseline/backport/phase-b/`.**
**Output tree: `D:\games\wz-stage\phaseB\tree\` — 18 `.wz`, 2.1 GB, NOT installed.**

## Result

| | |
|---|---|
| selftest | 38 PASS / 0 FAIL / 7 intentional SKIP, before and after the one tool change |
| merged | 12 archives, 23,543 manifest rows, **20,673 landed**, 2,870 refused, 89 s, 3.5 GB peak |
| live-edited restored | **6,073 / 6,073** content-verified `out == owner` and `out != v84`, 0 vacuous, 0 siblings dropped |
| additive verified | 13,858 rows `out == owner`, 0 mismatch (exhaustive on 11 archives, Mob sampled) |
| collateral | **13,052 images changed, all 13,052 named by the manifest. 0 collateral, 0 disappeared** |
| held back | 2,870 refused rows + 136 three-way conflicts |

## The three sets, re-derived (§1)

3,945 removed · 17,569 protect · 6,248 live-edited (6,112 LIVE-ONLY + 136 CONFLICT). All three
phase-A figures confirmed. The 6,112 → **6,073** correction reproduces from set arithmetic alone:
`removed ∩ LIVE-ONLY` = exactly 39 `Map.wz/Map/Map9` rows.

**New:** 4,044 protect rows sit *inside* a forced image and arrive with the wholesale replace. They
must not also be listed additively or they read as 4,044 false collisions.

## Corrections to phase A

* **`removed-list/Map.txt` positional rows: the total 165 is right, the breakdown is not.** Phase A
  attributed all 165 to `life`(70)/`portal`(64)/`foothold`(11) — which sums to **145**. The missing
  20 are `<layer>/obj/<n>` (19) and `ladderRope/1` (1). Corrected breakdown in PHASE-B §4.1.
* **38 of those 162 backport targets LANDED** as genuine tail appends. The hand-work is **124 rows**,
  not 165.
* **The array problem is 17× larger than phase A saw.** Whole-backport: **2,854 rows over 544 arrays**,
  99.4% of all refusals — mostly `String.wz` MonsterBook rewards (1,383), `Etc.wz/Commodity.img`
  (1,044) and `Quest.wz` steps (233), none of which were in phase A's estimate.
* **79 of the 136 conflicts are mechanically resolvable**, not hand work: 8 disjoint at child level,
  71 disjoint one level down (54 of them `Mob.wz/<id>.img/info`, where he set `boss`/`hpTagColor` and
  v84 set different fields). 57 need a decision. Phase A costed all 136 as hand work.

## One tool change — `Program.cs`, `Canon`, one expression

`Mob.wz` failed content verification on the first run: **230 of 735 images drifted**, exit 4, output
left `.partial`. Investigated rather than overridden. Cause, read off MapleLib `a7c38edf`:
`WzCanvasProperty.WriteValue` serialises every canvas through `GetCompressedBytesForExtraction`,
which **inflates a listWz-packed payload to exactly `Format.GetDecodedSize(w,h)` bytes and re-deflates
it at `CompressionLevel.Optimal`**. Same pixels, same format, same dimensions — different deflate
stream. `Canon` hashed the *raw stored* bytes, so it reported a merge that had changed nothing as
drift.

Fix: hash `GetCompressedBytesForExtraction` instead — *what the canvas serialises to*. Strictly
stronger: a truncated payload still fails, because the inflate is length-validated and the fallback
bytes differ. Proof on the already-written output, no re-merge: `die1`/`hit1`/`move`/`stand` become
byte-identical across v83-stock, v84 and the merged tree, while `info` — the node the merge actually
touched — still differs three ways. Re-merged: **0 drifted, verified OK**.

> Canvas digests printed before this change are not comparable to ones printed after, for
> listWz-packed canvases. T23b's table used the old digest; PHASE-B §3.1 re-establishes it.

## Open, and needing his decision

1. **2,870 rows did not land** and **this portion cannot be safely automated** — the positional-array
   gate is right to refuse them. `phase-b/REFUSED.tsv` + `phase-b/ARRAYS.tsv`.
2. **57 of the 136 conflicts** (`phase-b/CONFLICTS-136.tsv`, groups C and D). Largest: 17 equips
   where he raised the level-up EXP curve (`Cap/01002777`: `80` in both stock trees, **`10000` in
   his**) and v84 also touched `level`. Recommend keeping his version; not done.
3. **A backport-direction deny list.** The forward-direction one was required and used unchanged. Of
   the 15 rows it blocked, 9 are redundant, 2 are right, and **4 (`Npc.wz/9000021.img`, Maple
   Administrator) are the only content it uniquely costs him.**
4. Unproven: 742 Mob additive rows not individually content-checked; `Sound.wz` collateral check
   cannot run (`BgmGL.img`); Boss Rush asset self-sufficiency still unmeasured; **nothing launched**.

## Do not install without a launch test

`assemble-tree.ps1` builds the tree and hashes it. Installing it is his call and needs a client
launch to validate — the login-screen assets in particular have already broken a client once.
