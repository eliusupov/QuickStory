# Phase B, the 136 conflicts — resolved to the owner's "Maximum v84 parity" decision

**Decision (owner, 2026-08-17):** take v84 on the conflicts; keep his on the 17 `Character.wz`
`info/level` level-up EXP curves only. A deliberate, informed, one-time override of his
additive-only rule, **scoped to these 136 rows and nothing else**.

Output tree: `D:\games\wz-stage\phaseB\tree\` — rebuilt, still **not installed anywhere**.
The live client, `D:\games\MSv84\client\` and `D:\games\dreamms\` were read only; the server was
not restarted and no client was launched.

**Exit codes are not evidence in this document.** Every claim below is a digest comparison, and
every comparison carries a discriminator — the check that his value and v84's genuinely differ at
that field, without which "the tree holds his value" is satisfied by a field nobody ever touched.

---

## 0. Selftest, and the tool build

```
WzMerge selftest   ->  38 PASS / 0 FAIL / 7 SKIP, exit 0
```

The `Canon` fix of PHASE-B.md §7 is in the build used here — `Program.cs:734` hashes
`GetCompressedBytesForExtraction`, not the raw stored bytes — and the binary is built from it.
Every run used `--deny docs\wz-baseline\merge-lists\COLLISION-DENY.txt`, the current **192-root**
list as extended in `ce3895453`. Measured before the first run: **0 overlap between the 192 deny
roots and the 296 force roots, in both nesting directions**, so the deny list was admissible and
nothing was overridden.

---

## 1. The partition, re-derived from the TSV before anything was written

`phase-b/CONFLICTS-136.tsv`, column `category`:

| category | rows | policy applied |
|---|---:|---|
| A DISJOINT at child level | **8** | merge both sides |
| B DISJOINT at field level | **71** | merge both sides |
| C TRUE OVERLAP, `collidingFields == info/level` | **17** | **keep his** |
| C TRUE OVERLAP, everything else | **23** | take v84 |
| D WHOLESALE | **17** | take v84 |
| | **136** | |

The gate the dispatch set: **`C` ∧ `collidingFields == "info/level"` is exactly 17 rows, and all 17
are `Character.wz` equip images** — `Cap/01002777`, `Cap/01002791`, `Dragon/019{4,5,6,7}2002`,
`Glove/01082235`, `Glove/01082240`, `Longcoat/01052156`, `Longcoat/01052161`, `Shield/01092057`,
`Shoes/01072356`, `Shoes/01072362`, `Weapon/01372044`, `Weapon/01372045`, `Weapon/01382057`,
`Weapon/01382059`. 17 + 23 + 17 = 57 decided; 79 + 57 = 136. The partition holds exactly as he was
shown, and `resolve-conflicts-lists.py` asserts all of it rather than trusting this paragraph.

---

## 2. What was applied

Only the *keep-his* side needs a merge: the tree already carried v84 for every conflict, so
**take-v84 emits no row at all** and is verified as "this pass changed nothing there".

296 of his field/child roots were derived from `hisChildren` (A), `hisOnlyFields` (B) and
`info/level` (C-keep). One was dropped (§5), leaving 295 requested; **290 landed**.

| archive | rows | added | refused | exit | sec | peak |
|---|---:|---:|---:|---:|---:|---:|
| Etc | 4 | 4 | 0 | 0 | 0.9 | 87 MB |
| Quest | 1 | 1 | 0 | 0 | 1.3 | 133 MB |
| Skill | 7 | 7 | 0 | 0 | 1.3 | 122 MB |
| String | 28 | 25 | 3 | 3 | 0.8 | 80 MB |
| Item | 44 | 44 | 0 | 0 | 1.5 | 112 MB |
| Character | 40 | 40 | 0 | 0 | 15.9 | 126 MB |
| Mob | 170 | 168 | 2 | 3 | 3.8 | 174 MB |
| Map | 1 | 1 | 0 | 0 | 9.9 | 109 MB |
| **total** | **295** | **290** | **5** | | **35.4 s** | **174 MB** |

One archive per process, never in parallel; peak 174 MB against phase B's 3.5 GB, because this pass
forces field roots rather than whole images. Every row is a `--force` hit: his field already existed
in the target holding v83's value (which *is* v84's for these rows, since v84 did not move them), so
an additive-only write would have been refused. `--force` is subtree-replace and root-scoped, which
is exactly "his value over v84's, at this field, and nowhere else".

---

## 3. Content verification — 1,637 field checks, 0 wrong

`phase-b/verify-conflicts.py`. For every field the decision touches, its digest in four trees:
the assembled `tree\`, his client, the v84 base, and `pre` — the phase-B tree as it stood *before*
this pass. Per-field rows in `phase-b/VERIFY-conflicts.tsv`.

| what | fields | verdict | discriminator |
|---|---:|---|---|
| A, his children → his | 47 | `tree == live` | 47/47 `live != v84` |
| A, v84's children untouched | 112 | `tree == pre` | 112/112 real |
| B, his fields → his | 227 | `tree == live` | 227/227 real |
| B, v84's fields untouched | 249 | `tree == pre` | 249/249 real |
| **C-keep, the 17 `info/level`** | **17** | **`tree == live`** | **17/17 real** |
| C-take-v84, colliding fields | 101 | `tree == pre` | 99 real, 2 vacuous |
| D-take-v84, overlapping children | 879 | `tree == pre` | 798 real, 81 vacuous |
| his deletions, not applicable (§4) | 5 | `UNAPPLIED-DELETION` | real |
| | **1,637** | **0 WRONG** | **83 vacuous** |

**The discriminator is real.** `live != v84` holds for 1,554 of the 1,637 checks, so "the tree holds
his value" cannot be satisfied by a field neither side moved. The 83 vacuous ones are all inside
take-v84 rows where his value and v84's had converged; whatever the tree holds equals both, so there
is no decision content in them, and they are counted separately rather than folded into the pass.

**Take-v84 is verified as "this pass changed nothing there"**, against the pre-pass snapshot, not as
"the field is literally v84's" — see §6 for why those are not the same statement and where they part.

### 3.1 The worked example he was shown, re-measured on the output

`Character.wz/Cap/01002777.img/info/level/info/*/exp`:

```
v83-stock   80, 90, 100, 110, 120, 0                (6 steps)
v84-base    80, 90, 100, 110, 120, 0                (6 steps — identical to v83)
his client  10000 x 30                              (30 steps)
tree\       10000 x 30                              (30 steps — HIS)
```

And the other direction, `Mob.wz/8820010.img/info` — an A/B merge-both row:

```
his client   boss = 1                    (no category)
v84-base                    category = 8 (no boss)
tree\        boss = 1  and  category = 8      <- both sides, neither displaced
```

---

## 4. The 5 rows that could NOT be applied mechanically

His side of these is a **deletion**: the node is in v83 and in v84 and absent from his client. The
merge tool is additive-plus-force; it has no way to express "remove this node", and removal was
never authorised. v84's node therefore stands, which is the direction of this decision anyway.

| row | what v84/v83 hold |
|---|---|
| `String.wz/Pet.img/5000040` | Junior Reaper pet name/desc/descD |
| `String.wz/Pet.img/5000043` | " |
| `String.wz/Pet.img/5000046` | " |
| `Mob.wz/8520000.img/info/removeAfter` | `= 129600` |
| `Mob.wz/9500343.img/info/default` | a 197x210 canvas |

Verified in all three trees by `WzMerge dump`, not inferred from the refusal message. **What it
needs:** a delete verb in the merge tool, or five hand edits — and a decision that his deletions are
wanted at all, which "maximum v84 parity" arguably says they are not.

---

## 5. One tool bug found, worked around not papered over

Forcing `Mob.wz/6300005.img/die1` made the merge's own pre-save/post-save content check fail:
pre-save digest `69a48b9b…`, on-disk `63c0690c…`, exit 4, output left as `.partial`. Isolated with a
one-row probe merge; deterministic.

**The written output was provably correct.** All seven children of `6300005.img` in the `.partial`
are digest-identical to the pre-merge tree's, `die1` equals his client's `8553f7a6…`, and a full
`dump` of `die1` is identical live-vs-output down to every canvas's dimensions and compressed byte
count. The pre-save digest of the DeepCloned subtree matches neither the source's nor the saved
copy's — a residual of PHASE-B.md §7 living in the **clone** path rather than the read path.

**The row was dropped rather than forced, because it is a no-op.** Phase B had already landed the
protect row `Mob.wz/6300005.img/die1/speak`, and that single node was the whole of his edit:
`WzMerge hash` gives `die1 = 8553f7a6…` in both the phase-B tree and his client *before* this pass.
The conflict was already resolved his way. It is recorded in `SKIP` in
`resolve-conflicts-lists.py` with the reason, and §3 verifies it by content like every other row.
The tool was **not** modified — the selftest reported in §0 is the same build that did the merges.

---

## 6. What "reverts to v84 values" actually means in the tree — read this

He accepted the cost "your map spawn edits, item stat edits, quest and skill tweaks inside these 57
images revert to v84 values". That is true of **every field both sides moved**, which is what a
conflict is. It is **not** true of every byte of those 57 images, for one reason that predates this
decision: phase B's *additive* backport — the 3,945 `removed` rows and 17,569 `protect` rows — had
already put his own content inside some of these images, and this pass neither added to it nor took
it away. Removing it was never authorised and the tool cannot do it.

**17 of the 40 take-v84 images are therefore not purely v84.** Per-image counts in
`phase-b/CONFLICT-MANIFEST-136.tsv` (`standingHis`, `standingV84PlusHisAdditive`):

| image | fields not purely v84 | why |
|---|---:|---|
| `String.wz/Npc.img` | 63 | his NPC strings added additively |
| `String.wz/MonsterBook.img` | 53 | ditto |
| `Character.wz/Weapon/01492024.img` | 12 | his frames added additively |
| `Character.wz/Weapon/01452058.img` | 8 | " |
| `String.wz/Eqp.img` | 6 | his equip names added additively |
| `Character.wz/TamingMob/019{02,12}0{41,42},{34,35}.img` | 1–4 each | " |
| `Map.wz/…/10{1,2,3}000000, 25{0,1}000000.img` | 1–2 each | the §4.1 `life/26`, `life/27` appends |
| `Character.wz/Weapon/01382058, 01472069.img`, `UI.wz/UIWindow.img` | 2 each | " |

The same effect runs the other way and is *why* 30 `B` v84-side fields read as his: v84 **deleted**
`info/mobType`, `info/timeLimited`, `info/notSale` and friends, and phase B's `removed` set had
already restored them from his client. Those are not this decision's doing either.

**Nothing here is a defect and nothing here is silent** — every one of the 1,637 checks records its
standing value. But if "maximum v84 parity" was meant to reach the additive layer too, that is a
separate and much larger decision about the `removed` and `protect` sets, and it has not been made.

---

## 7. The one place the instruction was ambiguous, and how it was read

"KEEP HIS on the 17 `info/level` level-up EXP curves **only**" was applied literally: only
`info/level` was taken from his client on those 17 images.

Inside those same 17 images sit **38 more fields that are his alone and that v84 never touched** —
`info/icon` + `info/iconRaw` on all 17 (his HD art), plus `shootF/0` and `shootF/1` on
`Longcoat/01052156` and `01052161`. They are disjoint, exactly like the 79 `A`/`B` rows, so the
merge-both logic used everywhere else would have kept them for free. Under the literal reading they
stayed v84: `Cap/01002777`'s `info/icon` in the tree is v84's 680-byte canvas, his is 748 bytes.

That was the more v84-parity-consistent reading of "only", so it is what shipped. **Reversing it is
one line** — add `split(r['hisOnlyFields'])` to the `C-KEEP-HIS` branch of
`resolve-conflicts-lists.py` and re-run. It needs him, not a guess.

---

## 8. The record

| file | one row per |
|---|---|
| `phase-b/CONFLICT-MANIFEST-136.tsv` | **conflict** — 136 rows: decision, fields moved, fields left, standing value, verified outcome |
| `phase-b/CONFLICT-DECISIONS.tsv` | **decision unit** — 336 rows: every emitted merge path and every take-v84 row, with its reason |
| `phase-b/VERIFY-conflicts.tsv` | **digest check** — 1,637 rows: four digests, verdict, discriminator |
| `phase-b/CONFLICTS-136.tsv` | the input triage, unchanged |
| `D:\games\wz-stage\phaseB\conflicts\reports\` | merge logs, per-archive refusal lists |

Rebuilt tree, SHA-256 in `phase-b/TREE-MANIFEST.tsv`; the 8 archives this pass changed are marked
`merged+conflict-resolved`.

## 9. Not done

1. **The 5 deletions of §4.** Needs a delete verb or five hand edits.
2. **The 38 his-alone fields of §7.** Needs his answer, not a guess.
3. **The additive layer inside the 57 take-v84 images (§6)** is untouched and out of scope.
4. **The clone-path digest bug of §5** is worked around for one row, not fixed. It will fire again
   on any future force of a canvas subtree in a listWz-packed archive.
5. **Nothing was launched.** Whether the tree boots is still unmeasured (PHASE-B.md §9.6).
