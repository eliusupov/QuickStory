# 75 - HD S2: fold the 17 vanilla-drift UI values into the loader formula

**Class:** owner-requested-hd
**Slice:** S2 of `docs/work-plan/HD-CLIENT-V84-SPEC.md`
**Blocked by:** None to start writing. Pixel-correctness confirmation is owner-gated - see below.
**Startable now:** YES - an agent folds the values in offline. The owner confirms the pixels on launch.

Seventeen UI coordinate values drift from the loader's resolution formula: the formula relocates
most of the HD patch table mechanically, but these seventeen do not follow it and have to be carried
as explicit entries. Fold all seventeen into the loader formula/table so the loader emits the right
value for each without a hardcoded-v83 remnant.

**Owner-requested HD row, not a v84-parity gap.** Do not relabel.

**Scope: UI-layout only.** This ticket is the 17 known vanilla-drift **UI coordinate** values and
nothing else. It is **not** the separate question of HD-treating v84's new and changed map assets -
that is `Asset HD treatment for v84 new/changed content` in the spec, still PENDING RESEARCH, and no
part of it belongs here.

## What to do

For each of the seventeen values, add an explicit entry to the loader table (the formula stays the
default; these are the named exceptions to it), sourced from the v84 binary / the Ezorsia edit that
sets it - not guessed. The harness verifier must confirm each emitted value matches its intended
target. Whether the value is *pixel-correct on screen* is the one thing the harness cannot see; that
is the owner's launch check, not an agent's.

## Precedent

The loader table built under tickets 30 / 30b, and the harness `gen_loader` step, are the shape.
Each of the 288 already-shipping ops is an entry of the same kind; copy that entry shape. Harness
commits `4488aecf2` -> `7fa3cea74`; state in `tools/hd/README`.

## Acceptance criteria

- [ ] All 17 drift values are folded into the loader table as explicit entries; no hardcoded-v83
      value remains for any of them.
- [ ] The harness verifier confirms each of the 17 emitted values matches its intended target.
- [ ] `tools/hd/test_hd.py` passes and the loader DLL rebuilds via `gen_loader`.
- [ ] Each entry names its source (binary offset or Ezorsia edit); none is guessed.
- [ ] **Owner confirmation (launch, out of agent scope):** on the HD client the 17 UI elements draw
      in the right place at 1280x720. This box is ticked by the owner in S4/S5, not by the agent.

## Do not

- Do not guess a coordinate. Source each from the binary or the Ezorsia edit that sets it.
- Do not launch a client.
- Do not open server work - HD is a no-op on the wire (spec).
- Do not relabel as parity.
