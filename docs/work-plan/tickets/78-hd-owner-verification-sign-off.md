# 78 - HD S5: owner verification sign-off on a copied client directory

**Class:** owner-requested-hd
**Slice:** S5 of `docs/work-plan/HD-CLIENT-V84-SPEC.md`
**Blocked by:** 77 - the bring-up walkthrough must be clean before the final sign-off means anything.
**Startable now:** NO.
**Human gate:** a client launch. Human-only; no agent, ever.

The final gate: the owner plays the HD client on v84 and signs off that it works. This is the
acceptance the whole effort exists for - the owner's own verdict, on his own play session.

**Owner-requested HD row, not a v84-parity gap.** Do not relabel.

## What to do

- Run against a **copied** client directory, never the shared root install - launching rewrites a
  shared registry key (CLAUDE.md), so the verification runs on an isolated copy.
- The owner plays through the screens S4 covered and confirms the HD client is fully playable at
  1280x720 with all his current features intact.
- On sign-off, the ledger rows for this effort close.

## Precedent

The isolated-copy launch discipline this project already uses for v84 verification (ticket 20's
isolated client install) is the shape. No agent precedent - this is human-only.

## Acceptance criteria

- [ ] The owner launches the HD client from a **copied** directory, not the shared root.
- [ ] The owner confirms the client is fully playable at 1280x720 and signs off.
- [ ] The HD ledger rows (74-78) close on that sign-off.

## Do not

- Do not dispatch to an agent. Human-only, launch required.
- Do not launch against the shared root client directory.
- Do not close this row without the owner's explicit sign-off.
- Do not relabel as parity.
