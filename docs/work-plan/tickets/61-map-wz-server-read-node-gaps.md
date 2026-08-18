# 61 - the 40 Map.wz nodes the server reads and our tree does not have

**Class:** v84 parity
**Work rows:** R11 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Forty `Map.wz` nodes that `MapFactory` demonstrably reads are missing from our tree, mostly reactor
array entries. Two of the affected maps are live and walkable - `102000003` is the Warrior job
instructor's map and the six Aquarium maps carry the treasure chests ticket 43's quest 22407 route
depends on - so this is not latent. Every value is in the pristine carve.

## R11 - 40 server-read Map.wz nodes are missing

| map | missing | count |
|---|---|---:|
| **109090300** | `reactor/14` through `reactor/31` | 18 |
| **230010400**, **230020000**, **230040000**, **230040100**, **230040200**, **230040400** | Aquarium reactors across the six | 12 |
| **220011001** | the whole `info` block: `fly`, `swim`, `noMapCmd`, `onUserEnter`, `onFirstUserEnter`, plus `2/info/tS` and `2/info/tSMag` | 7 |
| **102000003** | `life/1` | 1 |
| **120000105** | `ladderRope/1` | 1 |

Full list: `V84-COVERAGE.tsv`, rows with `archive=Map` and `read_by=GAP`. Paths in
`add-list/Map.txt`; values from `porting-resources/wz-data/v84/Map.wz` via `WzPeek`.

**The two portal/script rows are not in this ticket.** `220011000/portal/4/{script,horizontalImpact}`
and `106010101/portal/5/script` belong to **ticket 54** (packet A, the Evan route). They appear in
the same coverage table and they are a different edit with a different risk profile. Do not merge
them here.

**`life`, `portal` and `reactor` are arrays.** A missing index N means our array is *shorter* than
v84's, which is a real difference. It does **not** follow that a present index N holds the same
content - the coverage tool cannot see that. Ticket **53** is the precedent for how array divergence
actually gets resolved: compare the shared indices on content before appending, and re-point
anything that cites an index by number.

## Precedent

- **Ticket 53** - the whole-image `foothold`/`life`/`portal` take across 52 maps. It states the rules
  this ticket inherits: v84 owns the client-facing fields and the slot position; `script` is
  server-only and ours wins; custom nodes are appended past v84's last index; a `life` row's `fh`
  must cite a foothold its own map has.
- **Ticket 46** is the standing refusal that governs the `life` row: taking v84's `life` array
  verbatim on `196000000` would have deleted the Cafe PQ stage-5 NPC. `102000003/life/1` is an
  *addition*, not a take - keep it that way.
- Values from `porting-resources/wz-data/v84/Map.wz` - `SOURCES.md` tier 1, **read-only**. The Map.wz
  carve was re-confirmed by SHA256 against the owner's installed client.
- `Map.wz` in this tree is **CRLF**. `SOURCES.md` names the trap: a Python round-trip silently
  rewrites the whole file.

## Acceptance criteria

- [ ] `109090300` has `reactor` indices 0-31 consecutive, indices 14-31 matching the carve node for
      node, and indices 0-13 byte-identical to what they held before the edit.
- [ ] Each of `230010400`, `230020000`, `230040000`, `230040100`, `230040200` and `230040400` has a
      `reactor` array whose length equals v84's, with the 12 added entries matching the carve and
      every pre-existing entry unchanged.
- [ ] `220011001/info` carries `fly`, `swim`, `noMapCmd`, `onUserEnter`, `onFirstUserEnter`, and
      `220011001/2/info` carries `tS` and `tSMag`, all with the carve's values.
- [ ] `102000003` has `life/1` with the carve's row, and its pre-existing `life/0` is unchanged. The
      added row cites a foothold `102000003` actually has, and that foothold spans the row's own `x`
      and lies at or below it - the ticket-53 check.
- [ ] `120000105` has `ladderRope/1` with the carve's values, `ladderRope/0` unchanged.
- [ ] A `*RealLoad` test loads all nine maps through `MapFactory` and asserts the reactor counts,
      the `220011001` info flags (including that `onUserEnter`/`onFirstUserEnter` name scripts that
      either exist or are recorded here as known-absent), the `102000003` NPC, and the
      `120000105` ladder.
- [ ] Every added `onUserEnter`/`onFirstUserEnter` script name is checked against `scripts/map/`; a
      missing map script is silent (`AbstractScriptManager` returns null with no log), and any name
      with no file is listed by name in this ticket rather than left implicit.
- [ ] Each edited file's diff is confined to the added nodes, with line endings preserved (CRLF).
      Re-running `python tools/playthrough/v84coverage.py` drops the `Map` GAP count from 40 to 2 -
      the two remaining being ticket 54's portal/script rows.

## Do not

- Do not merge `220011000/portal/4/script`, `220011000/portal/4/horizontalImpact` or
  `106010101/portal/5/script`. They are ticket 54's, and `106010101/portal/5` in particular is a
  **deliberate** divergence - taking v84's node there kills the only entrance to Golem's Temple
  (ticket 53, `golemsTempleEntranceKeptAWorkingDestination`).
- Do not take any of these `life`, `portal` or `reactor` arrays wholesale. This is an append of the
  missing indices, verified against the shared ones - not a whole-section take.
- Do not delete a row to make our array match v84's length. Ticket 46 governs; nothing on these nine
  maps is deleted by this ticket.
- Do not renumber or compact an array. `characters.spawnpoint` stores an index and
  `PortalFactory.loadPortal` addresses by node name; a shift strands stored values.
- Do not round-trip a `Map.wz` XML file through a writer that normalises line endings.
- Do not assume a present index matches. Compare content on the shared indices first.
