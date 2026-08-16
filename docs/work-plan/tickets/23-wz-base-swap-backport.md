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
