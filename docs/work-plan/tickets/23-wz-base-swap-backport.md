# 23 — WZ base swap and backport

**What to build:** a v84 WZ tree that contains **everything v84 shipped plus everything this server
still needs that v84 dropped** — so no content the owner has today disappears when the client changes.

**Blocked by:** 20 (needs the verified v84 tree). Runs **in parallel with 21 and 22** — it shares no
files with the protocol work and is the single biggest schedule saving available.

**Status:** ready-for-agent

## The corrected record — read before planning `[FACT-measured]`

Long-standing project claims that were **wrong** and must not be repeated:

- **Monster Carnival was NEVER deleted.** Intact in v84 *and* v92. Only one Dojo floor and the
  Sheep Ranch *lobby* went.
- **810 of the 832 deletions (97%) are Boss Rush** — which Cosmic implements
  (`MapId.java:213-214` matches the deleted range exactly).
- **maplestory.io's map *list* endpoint lies.** It is built from `String.wz` names and reports all
  832 deleted maps as present. Only the *detail* endpoint is authoritative (`/84/map/970030100` → 404).
  Any measurement taken from the list endpoint is void.
- **The backport is 21,602 roots, not 3,969.** 3,969 removed-roots **plus 17,633 protect-roots** —
  the owner's own custom content, which nobody had counted. This is why the phase is weeks, not days.

## Scope

- v84 WZ as the base; backport the removed set **and** the full protect set
- Use the existing tooling — `WzMerge` with its additive / deny / positional-array gates,
  the census and diff tools, and the `add-list` / `removed-list` / `protect-list` manifests
- ~~**Known tool defect to work around or fix first:** a *partial* array refusal leaves a **hole**.~~
  **FIXED (part 1, landed before any base swap).** `MapLogin.img/back` was `0..47`; the gate refused
  48–52 as duplicates and allowed 53–54, giving `{0..47, 53, 54}`, which broke the client before the
  login screen — each index was judged against the *baseline* count, not the running state, and
  `WzMerge guard` returned rc=0 on it. Now: the gate is evaluated against the **running** state and a
  continuity sweep **undoes** any append left sitting above a hole, so *if any index of an array is
  refused, every later index of that array is refused too*; and `WzMerge guard <outWz> --baseline
  <pre>` asserts positional-array continuity and exits 4 on a holed array. `WzMerge selftest`
  reproduces this exact scenario and fails if either behaviour regresses.
  See WZ-MERGE-PROCEDURE.md **4.4.1** and **4.4.2**.

## Acceptance criteria

- [ ] `WzMerge hash` over every image shows **only named rows changed** — no incidental drift
- [ ] All 3,969 removed-roots and all 17,633 protect-roots present and verified by computation
- [ ] Boss Rush, Monster Carnival, Mu Lung Dojo and Sheep Ranch all **enterable in game**
- [ ] Zero removals proven by the census tool, never by inspection
- [ ] No positional array left with a hole — assert continuity explicitly on every array touched
- [ ] Owner's custom content (the protect set) intact — spot-verified against the v83 client
- [ ] Output carries the correct `patchVersion` for the v84 client

## Verification gate

Content playthrough: the four backported areas are enterable and the owner's custom content is
visibly intact. Owner launch: **1**.

## Rollback

Rebuild from `wz-data\v84`, which is hash-matched to the installer. The live v83 client is untouched
throughout.

**Size:** 2–3 weeks.

---

## Delivered — phase A `[FACT-measured]` (2026-08-16)

Foundation only. **No full merge attempted.** Full inventory, every command and every caveat:
**`docs/wz-baseline/backport/README.md`**. Nothing was written to `D:\games\MapleStory\*.wz`,
`D:\games\MSv84\client` or `D:\games\dreamms`. No client launched, no server restarted, no Java touched.

### Base tree

`D:\games\wz-stage\v84-base` — 17 `.wz` copied from `wz-data\v84` and re-hashed **in the foreground**:
**17/17 byte-identical, 0 mismatches**. `WzMerge` reads them back as `iv=GMS patchVersion=84`.
Hashes: `backport/v84-base-tree.sha256`. Rollback is a re-copy.

### The numbers — one prior claim confirmed, one wrong, one set missing

| set | ticket said | **measured** | |
|---|---:|---:|---|
| removed | 3,969 | **3,945** | 3,969 is correct for "v84 deleted it" — but **24 of those the owner had already deleted himself**. Backporting them would undo his edits. `backport/removed-set-excluded.txt`. |
| protect | 17,633 | **17,569** | ❌ **the ticket is wrong by exactly 64.** 17,633 is `wc -l`, which counts the 4-line header of each of the 16 manifests. Ticket 17:561 records that exact command. |
| **live-edited** | — | **6,248** | 🆕 **a third set nobody counted.** `protect-list/` is a *presence* diff and is structurally blind to stock paths whose **content** the owner changed. Under a v84 base these arrive from v84 and his edits vanish **with no conflict raised**. `backport/live-edited-set.txt`. |

**Backport = 21,514 copy roots + 6,248 images, not 21,602.** Verified by computation, both directions:
protect **17,569/17,569 present in live** (rc=0) and **17,569/17,569 absent from the v84 base** (rc=4);
removed **3,969/3,969 absent from v84**, of which **3,945 present in live**.

### Corrections to the "corrected record"

The corrected record holds, with one refinement and one new hazard:

- ✅ **832** whole deleted map images — exact. **810 are Boss Rush** — exact.
- ⚠️ **"`MapId.java:213-214` matches the deleted range exactly" is off by six.** `BOSS_RUSH_MAX =
  970042711`; the deleted WZ block runs to **970042717**. 804 of 810 fall inside Cosmic's predicate.
  Backport all 810 anyway — *add, don't remove* — but drop the "exact match" wording.
- 🆕 **`removed-list/Map.txt` is 1,017 rows but only 832 maps.** **181 rows are sub-nodes inside maps
  that SURVIVE in v84**, and **165 of those sit under `life` (70), `portal` (64) or `foothold` (11) —
  positional arrays.** These are the shape 4.4 refuses by design; each needs its arrays dumped and
  compared *by name, not index*, then re-authored or denied. Plus 3 rows under
  `Map.wz/Obj/login.img/WorldSelect/*` — the login-screen area that has already killed the client once.
  **Largest hidden cost in phase B; invisible in every prior estimate.**

### How the instruments were proven

`WzMerge selftest` — **run, all 20+ checks passed, rc=0**, including the T23 partial-refusal hole and
the 9 legitimate gapped id tables. Then, before any number was believed:

- the row counter was pointed at all **48** manifest files and compared to each file's *own*
  independently generated header count → **48/48 agree**; its filter is byte-identical to `WzMerge`'s
  `ReadPaths` (`tool-merge/Program.cs:189-201`).
- `WzMerge verify` on two known-answer cases → `add-list/Item.txt` vs v84 **0/391 missing rc=0**, vs
  v83-stock **391/391 missing rc=4**.
- `WzMerge hash` for determinism (same file twice → **0 differing lines**) and sensitivity (vs the
  known-different live `Map.wz` → **18 differing lines**).
- `Get-FileHash` foreground only.

### The proven slice

`Map.wz/Map/Map9/970030100.img` — the **Boss Rush entry map**. guard → snapshot hash → dry run → merge
(`--deny`, `--live`) → `guard --baseline` → `verify`. **added 1, refused 0, rc=0 at every step**, 8 s,
`patchVersion=84` preserved, 820 gapped id tables correctly discounted against the baseline.

An empty conflicts list is not evidence, so nothing-else-moved was proven by **content digest**:

- root: `Back` `Effect.img` `MapHelper.img` `Obj` `Physics.img` `Tile` `WorldMap` **all byte-identical**
- `Map`: **Map0..Map8 all byte-identical**, only `Map9` moved
- `Map/Map9`: **1979 → 1980 children, exactly ONE new line, ZERO existing lines changed**
- the inserted image digests **identical** to the live client's copy — the owner's data, not a re-encode

**`WzMerge hash <wz> <Name>.wz` digests a 628 MB file in 15 s.** Acceptance criterion 1 ("only named
rows changed") is therefore directly executable per merge in phase B.

### Phase B cost, with real numbers

~28,000 units, of which **~300 are genuinely hand work** (165 positional-array rows + 136 three-way
CONFLICT images + 3 login assets). 2–3 weeks still looks right, but for **different reasons than the
ticket gives**: not the mechanical roots, but

1. **6,112 images need OVERWRITE, not addition** — `WzMerge` is additive-only by design. `--force`
   exists but has never been exercised at this scale. **Likely phase B's first blocking task.**
2. the ~300 hand-resolved rows above.

### Not determined — read before planning phase B

1. Whether the 6,112 LIVE-ONLY images are safe to restore wholesale. `BlockSize` shows live ≠ v83 and
   v84 = v83 *by row absence*; it does not prove v84 is byte-identical to v83. Needs per-image `hash`.
2. Whether `WzMerge --force` can perform that overwrite at all. Untested at scale.
3. **Whether the 810 Boss Rush maps are self-sufficient in v84's asset tree.** A map names its
   `Back`/`Obj`/`Tile`/`Bgm` by string. `WzMerge deps` resolves against the *add-list*, which is the
   wrong direction for a *removed* map. **Nothing proves those assets survived v84.** Not run.
4. `Sound.wz/BgmGL.img` unparseable by MapleLib in all three trees (2 removed rows sit in `Sound.wz`).
5. `EzorsiaV2_UI.wz` has no stock baseline and is outside every manifest here — ticket 30.
6. `List.wz` is not a WZ archive; only its byte-identity to `wz-data\v84` is known.
7. The 3-level manifest depth ceiling still applies; an entity nested 4+ deep is invisible to every
   count above.
8. **Nothing was launched.** Every claim is static analysis by computation.
