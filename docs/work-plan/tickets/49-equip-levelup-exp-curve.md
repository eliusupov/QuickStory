# 49 — the level-up EXP curve is HeavenMS's, not v84's, and both trees already carry it

**Status: measured, nothing to apply. One guard test added.**
Measured 2026-08-17 on the server tree, `D:\games\wz-stage\v84-base\Character.wz` (read-only) and
`D:\games\wz-stage\phaseB\tree\Character.wz`. Every number below came from a content scan, not an
exit code.

## The decision this ticket records

`info/level/info/<n>/exp` is **not** the owner's tuning and **not** stock. It is HeavenMS upstream:
ronancpl's `3a8377c28 "Minor XML patch"` (2018-09-24). Owner's ruling, verbatim: *"no no use heavenms
curve on the new stuff as well"* — the flat `10000` governs anything the v84 backport brings in too.
**This supersedes the maximum-v84-parity policy for this one field and only this field.**

## What ronancpl's patch actually did — verified on `Cap/01002777.img.xml`

* stock (v83 **and** v84) ships `80, 90, 100, 110, 120, 0` over levels 1-6.
* the patch rewrote all six to `10000`, **including level 6, whose stock value is `0`** — confirmed
  in the diff, not assumed.
* it also appended levels 7-30, each an exp-only node at `10000`.
* and it gave that same 30-level flat curve to 2,876 equips stock never made levelable at all.

## Scope, re-derived

| tree | images | levelable equips | flat 10000 | non-flat |
|---|---:|---:|---:|---:|
| server `wz/Character.wz` | 7,361 | **2,961** (30 levels each, 88,830 exp nodes) | 2,961 | **0** |
| v84 stock (read-only ref) | 7,357 | **85** (43×6-level, 41×4-level, 1×2-level) | 0 | 85 |
| phase B client tree | 7,361 | **2,961** (30 levels each) | 2,961 | **0** |

**v84 introduces no new level-up gear — and no new `Character.wz` images at all.**
`v84 images − server images = 0`. All 85 v84 levelable equips already exist in the server tree with
the flat curve. `server − v84 = 4` images (server-only, unrelated). phase B's levelable set is
**identical** to the server's, symmetric difference 0.

The 17 rows that surfaced as v84 conflicts (`01002777`, `01002791`, the four Evan dragons
`0194/0195/0196/01972002`, `01082235/240`, `01052156/161`, `01092057`, `01072356/362`,
`01372044/045`, `01382057/059`) are just the subset v84 also edited. They were already resolved
`C-KEEP-HIS` in phase B and verified by content — see `backport/phase-b/VERIFY-conflicts.tsv`.

**Nothing was applied to either tree. There was nothing to apply.** No `.wz` was written, so
`WzMerge` was not invoked for a merge (only `dump`, which writes nothing).

## Guard

`src/test/java/server/EquipLevelExpCurveRealLoad.java` walks every image in `Character.wz` through
the real `DataProvider` and asserts every `info/level/info/<n>/exp` is `10000`, that the levelable
set is still 2,961, and that each curve is still 30 levels. Mutation-checked: setting
`Cap/01002777.img` level 1 to `80` fails both tests naming the exact path; reverting is green again.
