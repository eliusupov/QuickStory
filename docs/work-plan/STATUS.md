# STATUS — GMS v83 → v84 upgrade

Orchestrator log. One row per ticket dispatch. State: `in-flight` / `done` / `partial` /
`blocked-on-human` / `failed`.

| # | Ticket | Agent | State | Note | Updated |
|---|---|---|---|---|---|
| 01 | Evan client gate patched | `gp-opus-high` | blocked-on-human | Patch done + orchestrator-verified: pattern unique at `0x361714` in both binaries, 21 bytes → `0x90`, originals byte-identical to backup. Criteria 4+5 ticked. 1–3 need a human to launch the client. | 2026-08-15 |
| 02 | WZ baseline diff | `gp-sonnet-high` | partial | Manifests delivered + verified (commit `550bf8580`): add-list 10 files, protect-list 11, `SUMMARY.md`, reusable MapleLib diff tool. All 4 substantive criteria met. Open: Map.wz source integrity (below). | 2026-08-15 |
| 02b | Map.wz source-integrity probe | `gp-sonnet-high` (resumed) | in-flight | Is `wz-data/v84/Map.wz` safe to import from? | 2026-08-15 |
| R1 | Code review — batch 01+02 | `gp-opus-high` | in-flight | Focus: the reusable diff tool, manifest usability, adequacy of the additive-only merge rule. | 2026-08-15 |

## Frontier

Dispatched: 01, 02 (the whole starting frontier — everything else is blocked).
Next once 02 lands: 03 (WZ merge pipeline). Once 01 **and** 03 land: 10 (Evan exists).

## Reviews

**R1 — batch 01+02** — in flight (`gp-opus-high`, `/code-review`). Ticket 03 is held until it
returns; 03 is the tracer bullet that sets the precedent for 04–09, so it should not be dispatched
on top of unreviewed foundations.

## Open findings carried forward

1. **Character.wz protect-list blind spot.** The protect-list is presence-only. Ezorsia's HD work
   is ~18.6 MB of content substituted under node paths that *already exist in stock v83*, so only
   4 nodes / 63 KB appear in `protect-list/Character.txt`. The manifest is not wrong, but it must
   not be read as "everything not listed here is safe to overwrite."
   **Resolution:** ticket 03's merge rule is **additive-only** — never overwrite an existing node
   path or sub-key, only add absent ones — plus a post-merge re-diff proving no pre-existing node
   changed. Cheaper and stronger than content-hashing the whole tree. R1 is judging whether that
   rule has a hole.
2. **Map.wz: v83 5,616 nodes → v84 4,862.** A 754-node net drop, against the README's founding
   premise that "v84 removed zero nodes." `wz-data/v84/` is the import source for 04–09, so this
   is a source-integrity question, not bookkeeping. Under investigation (02b): genuine removal /
   structural counting difference / damaged copy.
3. Map 93-vs-79 and NPC `9000071` (Keroben) manifest gaps — minor, folded into 02b.

## Human-required queue

Batched, presented together — nothing here stops other tickets.

| From | Step | Staged? |
|---|---|---|
| 01 | Double-click `D:\games\MapleStory\local.evan.exe` → login screen? Then `localhome.evan.exe`. Then log a character in and play. Full pass/fail signatures + rollback in ticket 01 `## Human steps`. | **ready** |

**Orchestrator verification of 01** (independent, not taken on trust): pattern occurs exactly
once per binary at `0x361714`; each patched copy differs from its original in exactly 21
contiguous bytes `0x361714`–`0x361728`, all `0x90`, size unchanged; live `local.exe` /
`localhome.exe` still hash-match the backup copies. Confirmed.
