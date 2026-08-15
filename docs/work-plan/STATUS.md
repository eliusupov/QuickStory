# STATUS — GMS v83 → v84 upgrade

Orchestrator log. One row per ticket dispatch. State: `in-flight` / `done` / `partial` /
`blocked-on-human` / `failed`.

| # | Ticket | Agent | State | Note | Updated |
|---|---|---|---|---|---|
| 01 | Evan client gate patched | `gp-opus-high` | blocked-on-human | Patch done + orchestrator-verified: pattern unique at `0x361714` in both binaries, 21 bytes → `0x90`, originals byte-identical to backup. Criteria 4+5 ticked. 1–3 need a human to launch the client. | 2026-08-15 |
| 02 | WZ baseline diff | `gp-sonnet-high` | in-flight | Protect-list (custom nodes) + authoritative v83→v84 add-list, cross-checked vs the 771-node manifest. | 2026-08-15 |

## Frontier

Dispatched: 01, 02 (the whole starting frontier — everything else is blocked).
Next once 02 lands: 03 (WZ merge pipeline). Once 01 **and** 03 land: 10 (Evan exists).

## Reviews

None yet. Reviewer (`gp-opus-high`, `/code-review`) due after 01+02 return, or after 3 tickets.

## Human-required queue

Batched, presented together — nothing here stops other tickets.

| From | Step | Staged? |
|---|---|---|
| 01 | Double-click `D:\games\MapleStory\local.evan.exe` → login screen? Then `localhome.evan.exe`. Then log a character in and play. Full pass/fail signatures + rollback in ticket 01 `## Human steps`. | **ready** |

**Orchestrator verification of 01** (independent, not taken on trust): pattern occurs exactly
once per binary at `0x361714`; each patched copy differs from its original in exactly 21
contiguous bytes `0x361714`–`0x361728`, all `0x90`, size unchanged; live `local.exe` /
`localhome.exe` still hash-match the backup copies. Confirmed.
