# 48 — WZ backport phase B: resolve the 136 conflicts to "Maximum v84 parity"

**Status: done, verified by content. Two follow-ups need the owner, not a guess.**

Record: `docs/wz-baseline/backport/phase-b/CONFLICT-RESOLUTION.md`
Sequence: `docs/wz-baseline/backport/PHASE-B.md` §5.1 and §8 step 6.

## The decision

The owner was shown the 136 three-way conflicts (PHASE-B.md §5) and chose **take v84 on the
conflicts, keep his on the 17 `Character.wz` `info/level` level-up EXP curves only**, accepting that
his map spawn / item stat / quest / skill edits *inside those 57 images* go back to v84. A
deliberate, informed override of his additive-only rule, **scoped to these 136 rows and nothing
else**. Do not extend it.

## What shipped

`D:\games\wz-stage\phaseB\tree\` rebuilt, still not installed. 8 archives changed.

- partition re-derived from `CONFLICTS-136.tsv` before writing: A=8, B=71, C=40, D=17, and
  `C ∧ collidingFields == info/level` is exactly 17, all Character.wz equips — as he was shown
- A+B merged both ways, 17 keep his, 40 keep v84 (no merge needed, the tree was already v84)
- 295 field roots requested, **290 landed**, 5 refused; `WzMerge selftest` 38 PASS / 0 FAIL / 7 SKIP
- **1,637 digest checks over four trees: 0 WRONG**, 83 vacuous, discriminator real on 1,554
- worked example: `Cap/01002777` `info/level/info/*/exp` is `10000 ×30` (his) in the tree, against
  `80,90,100,110,120,0` in both v83 and v84

## Open — needs the owner

1. **5 rows where his side is a DELETION** and the merge tool has no delete verb:
   `String.wz/Pet.img/500004{0,3,6}`, `Mob.wz/8520000.img/info/removeAfter`,
   `Mob.wz/9500343.img/info/default`. v84's node stands. Wanted or not?
2. **38 his-alone fields inside the 17 kept images** — `info/icon` + `info/iconRaw` on all 17 (his
   HD art) and `shootF/0,1` on two Longcoats. v84 never touched them, so the merge-both logic used
   for the other 79 rows would keep them for free, but "the EXP curves **only**" was read literally
   and they stayed v84. One-line change to reverse (`CONFLICT-RESOLUTION.md` §7).

## Open — engineering

3. **Clone-path digest bug in WzMerge** (`CONFLICT-RESOLUTION.md` §5): forcing a canvas subtree in a
   listWz-packed archive can make the pre-save digest disagree with both the source's and the saved
   copy's, failing the merge's own content check on correct output. Residual of PHASE-B.md §7, in
   the DeepClone path rather than the read path. One row hit it; it was dropped as a proven no-op
   rather than worked around in the tool, so the selftest on record is the build that ran.
4. **"Reverts to v84" does not reach the additive layer.** 17 of the 40 take-v84 images still carry
   his content in fields v84 deleted or never had, because phase B's `removed`/`protect` sets put it
   there under an earlier decision and nothing authorised removing it (`CONFLICT-RESOLUTION.md` §6).
   Extending v84 parity that far is a separate, much larger decision.
