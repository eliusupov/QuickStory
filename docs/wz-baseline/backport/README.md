# Ticket 23 phase A — the verified backport inventory

Everything here was **measured on 2026-08-16**, not carried forward. Where a number disagrees with a
prior claim, the prior claim is named and the disagreement explained. Every count in this file has a
reproducible command beside it.

## 0. The base tree

| | |
|---|---|
| **Staged at** | `D:\games\wz-stage\v84-base` (17 `.wz`, 2.0 GB) |
| copied from | `D:\games\MapleStory\Server\porting-resources\wz-data\v84` |
| provenance | that source was already confirmed byte-identical to a fresh carve of `GMSSetupv84.exe` |
| re-verified | `Get-FileHash -Algorithm SHA256`, **foreground**, 17/17 identical, **0 mismatches** |
| hashes | `v84-base-tree.sha256` (this directory) |
| patchVersion | **84**, read back off the staged files by `WzMerge` (`iv=GMS patchVersion=84`) |

It is **not** the live client and **not** `D:\games\MSv84\client`. Neither was written to. Rollback is
`Copy-Item` from `wz-data\v84` again.

## 1. The three sets that must travel into it

Prior claim: **21,602** roots = 3,969 removed + 17,633 protect. **Measured: 27,762 units in three
sets**, and the middle number is wrong.

| set | prior claim | **measured** | verdict |
|---|---:|---:|---|
| **removed** — v84 dropped it, the owner still has it | 3,969 | **3,945** | 3,969 is right for "v84 deleted it"; **24 of those the owner had already deleted himself** |
| **protect** — the owner's own content, in neither stock tree | 17,633 | **17,569** | **prior claim wrong by exactly 64** |
| **live-edited** — stock paths whose *content* the owner changed | *never counted* | **6,248** | **a third set nobody had counted**, and it is invisible to a presence diff |

### 1.1 removed set — **3,945**, not 3,969

`removed-list/*.txt` sums to **3,969** content rows and that number is correct *for what the manifest
measures*: v83-stock minus v84. Confirmed absent from the staged v84 base, all of it:

```
WzMerge verify D:\games\wz-stage\v84-base\<X>.wz docs/wz-baseline/removed-list/<X>.txt
  Character 136/136 missing · Etc 2028/2028 · Item 35/35 · Map 1017/1017 · Mob 674/674
  Npc 31/31 · Quest 39/39 · Sound 2/2 · UI 7/7        → 3969/3969 absent from v84
```

But the manifest answers *"what did the patch delete"*, not *"what would the owner lose"*. Run the same
paths against the **live client** and 24 rows are missing there too — the owner deleted them himself:

```
WzMerge verify D:\games\MapleStory\<X>.wz docs/wz-baseline/removed-list/<X>.txt
  Item 34/35 · Quest 19/39 · Map 1014/1017 · everything else 100% present
```

Listed with reasons in **`removed-set-excluded.txt`**. They are 20 quest date-windows he stripped off
events 4300–4309, one item quest-binding, and three `life/` spawns he deleted. Backporting them would
*undo his edits*, and the three `Map.wz` ones are positional-array slots as well
(WZ-MERGE-PROCEDURE 4.4), so restoring them is an index hazard on top of being wrong.

**Backport target = 3,969 − 24 = 3,945.** Source tree for these rows is the **live client**, not
v83-stock, for exactly this reason.

### 1.2 protect set — **17,569**, not 17,633. The 64 are header lines.

`protect-list/*.txt` holds **17,569** content rows. Raw `wc -l` over the same 16 files is **17,633** —
the ticket-17 figure — because each file carries a 3-line comment header plus a blank line, and
16 × 4 = **64**. Verified both ways in one pass: `raw=17633, header/blank=64, content=17569`.

Ticket 17 line 561 records the command it used: `wc -l docs/wz-baseline/protect-list/*.txt`. That is
the defect. The same command on `removed-list/` gives **4,001**, and ticket 17 correctly quotes 3,969
there, so only the protect half was ever counted this way.

**Total backport therefore = 3,945 + 17,569 = 21,514 copy roots, not 21,602** — plus set 3 below.

Both halves verified by computation, not inspection:

```
WzMerge verify D:\games\MapleStory\<X>.wz docs/wz-baseline/protect-list/<X>.txt
  → 17569/17569 PRESENT in the live client,   rc=0 on every file
WzMerge verify D:\games\wz-stage\v84-base\<X>.wz docs/wz-baseline/protect-list/<X>.txt
  → 17569/17569 ABSENT from the v84 base,     rc=4 on every file
```

### 1.3 live-edited set — **6,248**, and nobody had counted it

**`protect-list/` is a presence diff.** It finds paths the live client has that the stock trees do not.
It is structurally blind to a path that exists in *both* trees and whose **content** the owner changed
— Ezorsia's HD art is the mass of it, sitting under stock `Character.wz` ids. Under a v84 base those
images arrive from v84, the owner's versions are gone, **and no merge conflict is raised, because there
is no manifest row for them to conflict with.** TOOL-NOTES.md already flags this for `Character.wz`;
the measurement below is the whole-tree version.

**`live-edited-set.txt`** (this directory), from `modified-list/<X>.live.txt` intersected with
`modified-list/<X>.txt`:

| | rows | meaning |
|---|---:|---|
| **LIVE-ONLY** | **6,112** | v84 shipped the image **unchanged from v83** → the live image can be restored **wholesale**. Mechanical, one op each. |
| **CONFLICT** | **136** | v84 **also** edited it → three-way, needs a human decision per row. |
| total | 6,248 | Character 5,114 · Map 733 · Npc 261 · Mob 84 · Reactor 19 · Item/String 10 · Skill 9 · Quest 4 · Etc 3 · UI 1 |

Only **24** of the 6,248 differ by ≤4 bytes (re-encode noise); **6,224 are substantive**.

**Stated ceiling:** `BlockSize` is a change *detector*, not a content hash. A replacement that
compresses to the same length is invisible to it. Escalating to a per-image `WzMerge hash` across three
trees is affordable now that it is measured at ~15 s per 628 MB file — but it has not been done.

## 2. Corrections to the corrected record

The ticket's own "corrected record" survives re-measurement, with one refinement:

- **Monster Carnival never deleted** — not contradicted by anything measured here.
- **832 deletions** — exactly **832** whole `Map/MapN/<id>.img` roots in `removed-list/Map.txt`. Confirmed.
- **"810 of the 832 are Boss Rush"** — confirmed: `Map9` holds 811 deleted images, 810 in the
  `9700301xx`–`9700427xx` block plus `925020610` (Mu Lung Dojo 6F).
- **"`MapId.java:213-214` matches the deleted range exactly"** — ⚠️ **off by six.** `BOSS_RUSH_MAX =
  970042711`, but the deleted WZ block runs to **970042717**. **804** of the 810 fall inside Cosmic's
  declared range; `970042712`–`970042717` are six Boss Rush maps the server's own predicate excludes.
  Backport all 810 regardless — *add, don't remove* — but the "exact match" wording is wrong.

**New, and not in the ticket:** the 1,017 `removed-list/Map.txt` rows are not 1,017 maps.

| shape | rows | |
|---|---:|---|
| whole deleted map images | 832 | drop-in copies, the easy half |
| **sub-node rows inside maps that SURVIVE in v84** | **181** | **165 of them sit under `life` (70), `portal` (64), `foothold` (11) — POSITIONAL ARRAYS** |
| `Map.wz/Obj/login.img/WorldSelect/{aran,killing,tema}` | 3 | login-screen assets — the area that has already killed the client once |
| `Map.wz/Obj/tutorial_jp.img` | 1 | |

Those 165 are the shape `WzMerge` refuses by design (4.4). They cannot be merged as rows; each needs
its arrays dumped and compared **by name, not by index**, then re-authored or denied. **This is the
single largest hidden cost in phase B and it was not visible in any prior estimate.**

## 3. Tooling proof

Nothing below was assumed. `PROVE THE INSTRUMENT BEFORE TRUSTING THE MEASUREMENT.`

| instrument | proof | result |
|---|---|---|
| `WzMerge selftest` | 20+ checks incl. the T23 partial-refusal hole and the 9 legitimate gapped id tables | **all checks passed, rc=0** |
| row counter | run against all 48 manifest files and compared to each file's **own** independently generated header count | **48/48 agree, 0 mismatches** — and the filter is byte-identical to `WzMerge`'s `ReadPaths` (`Program.cs:189-201`) |
| `WzMerge verify` | two known-answer cases: `add-list/Item.txt` vs v84 (all should be present) and vs v83-stock (all should be absent) | **0/391 missing, rc=0** and **391/391 missing, rc=4** |
| `WzMerge hash` | determinism: same file twice. sensitivity: same command on the known-different live `Map.wz` | **0 differing lines** / **18 differing lines** |
| `Get-FileHash` | run in the **foreground** only, per the standing warning about the backgrounded false-diff | 17/17 match |

## 4. The proven slice — one image, end to end

`Map.wz/Map/Map9/970030100.img` — the **Boss Rush entry map**, the ticket's headline content, deleted
by v84, implemented by Cosmic. Staged at `D:\games\wz-stage\slice-970030100\`. Artifacts:
`slice-970030100.paths.txt`, `slice-970030100.conflicts.txt` (this directory).

```
1  guard   out path                                  → ALLOWED, rc=0
2  snapshot pre\Map.wz == v84-base\Map.wz            → SHA256 equal
3  dry run --deny COLLISION-DENY.txt (188 roots)     → added 1, refused 0, rc=0
4  merge   live Map.wz -> pre -> out, --deny, --live → added 1, refused 0, rc=0, 8 s
           tool's own content digest, re-read off disk: content OK 46bd7532…
5  guard   out --baseline pre                        → array continuity OK, rc=0
                                                       820 gapped id tables discounted via the baseline
6  verify  out paths.txt --baseline pre              → 0 missing, 0 drifted, rc=0
```

**Step 7 is the one that matters — an empty conflicts list is not evidence of safety.** Content
digests, pre vs post:

```
WzMerge hash <Map.wz> Map.wz     → Back · Effect.img · MapHelper.img · Obj · Physics.img · Tile
                                    · WorldMap  ALL BYTE-IDENTICAL.  Only `Map` moved.
WzMerge hash <Map.wz> Map        → Map0..Map8 ALL BYTE-IDENTICAL.  Only `Map9` moved.
WzMerge hash <Map.wz> Map/Map9   → 1979 → 1980 children.  Exactly ONE new line.
                                    ZERO existing lines changed.
```

And the inserted image is the owner's, not a re-encode of it:

```
live  D:\games\MapleStory\Map.wz          970030100.img  46bd753264bbf6d2…
post  slice-970030100\Map.wz              970030100.img  46bd753264bbf6d2…   IDENTICAL
```

A whole-file content digest of a 628 MB `.wz` costs **15 s**. That makes "prove nothing else moved" a
routine per-merge check for phase B, not an aspiration. **`WzMerge hash <wz> <WzName>.wz` at the root
is the acceptance-criterion instrument** — acceptance criterion 1 ("only named rows changed") is
directly executable this way, and it was not obvious that it could be.

## 5. What phase B actually costs

| work | units | shape | honest cost |
|---|---:|---|---|
| whole deleted map images | 832 | drop-in `.img` copies from the live client | mechanical; batchable, one merge per `.wz` |
| protect roots | 17,569 | node copy roots, mostly `String.wz` (7,604) and `Npc.wz` (5,981) | mechanical **but** collision-heavy — the existing `addlist-dryrun-String.conflicts.txt` is 56 KB |
| **LIVE-ONLY live-edited images** | **6,112** | whole-image restore over v84's copy | mechanical, *not* additive — needs a **force/overwrite** path, which the current tool deliberately refuses without `--force` |
| removed sub-node rows | 3,113 | ordinary node rows | mechanical |
| **positional-array rows in `Map.wz`** | **165** | `life`/`portal`/`foothold` slots in surviving maps | **hand work.** Dump both arrays, compare by name, re-author or deny. |
| **CONFLICT images** | **136** | v84 and the owner both edited | **hand work**, one decision each |
| `Obj/login.img/WorldSelect/*` | 3 | login-screen assets | **hand work + a launch test.** This area has already broken the client once. |

**~28,000 units, of which ~300 are genuinely hand work.** The 2–3 week estimate still looks right, but
the *reason* has changed: it is not the 21,602 mechanical roots, it is (a) the 6,112-image overwrite
path the tool does not yet have, and (b) the ~300 hand-resolved rows. **Both were invisible before this
phase.** The mechanical bulk is cheap now that `hash` is proven at 15 s/file.

## 6. What I could not determine

1. **Whether the 6,112 LIVE-ONLY images are truly safe to restore wholesale.** `BlockSize` says the
   live copy differs from v83 and v84 does not; it does **not** prove the v84 copy is byte-identical to
   v83. That needs `WzMerge hash` per image across v83/v84/live and has not been run.
2. **Whether `WzMerge` can do the overwrite at all.** It is additive-only by design. The 6,112 images
   need replacement, not addition. `--force <forceList>` exists but has not been exercised at this
   scale, and the existing `COLLISION-FORCE.txt` is 7 KB. **This may be phase B's first blocking task.**
3. **Whether the 810 Boss Rush maps are self-sufficient in v84's asset tree.** A map references
   `Back`/`Obj`/`Tile`/`Bgm` by name. `WzMerge deps` resolves references against the *add-list* (v84
   additions), which is the wrong direction for a *removed* map. **Nothing here proves the referenced
   backgrounds and tilesets survived v84.** Not run; needs a reversed-direction `deps`.
4. **`Sound.wz/BgmGL.img` is unparseable by MapleLib in all three trees.** Two removed-set rows sit in
   `Sound.wz`; both verified present in live and absent from v84, but the image itself cannot be read.
5. **`EzorsiaV2_UI.wz` is live-only and has no stock baseline at all** — outside every manifest here.
   Ticket 30.
6. **`List.wz` is not a WZ archive** and no tool here can diff it. It is byte-identical between the
   staged base and `wz-data\v84`; nothing more is known.
7. **The 3-level manifest depth ceiling still applies.** An entity nested 4+ deep is invisible to every
   count above. Documented in TOOL-NOTES.md; not re-tested.
8. **Nothing was launched.** No client was started, no server touched. Every claim above is static
   analysis by computation.
