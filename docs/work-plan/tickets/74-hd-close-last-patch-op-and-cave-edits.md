# 74 - HD S1: close the last patch op and the two flagged cave edits

**Class:** owner-requested-hd
**Slice:** S1 of `docs/work-plan/HD-CLIENT-V84-SPEC.md`
**Blocked by:** None - continues tickets 30 / 30b, whose verified patch set this closes out.
**Startable now:** YES - fully offline. No client launch.

The v83->v84 address port is at 306/317 ops PASS and the shipping set is 288/289 = 99.7%. One op is
still open and two cave-body edits are flagged. This ticket closes all three. It is the last of the
harness work that can be done without a human at the client.

**This is an owner-requested HD row, not a v84-parity gap.** Do not relabel it. See the spec's
exception section.

## The open op

`ccLoginDescriptorFix` at **`0x0060D85B`** is the single unresolved patch site in the shipping set.
It is the one op that the v83->v84 formula-based relocation did not settle mechanically. Resolve its
v84 target the way the harness resolves every other op - by the formula table, corroborated against
the v84 binary - and land it so the shipping set reaches 289/289. If it genuinely cannot be resolved
from the binary, say so and record why, exactly as the harness records every other `[UNKNOWN]`; do
**not** invent an address.

## The two flagged cave-body edits

Two cave-body edits are flagged in the harness (the `AdjustStatusBarBG`-class body redesign that
ticket 30b named). Finish both against the v84 binary. Group I is already 23/23 solved and is not in
scope; only the two flagged bodies are.

## Precedent

The harness itself - `tools/hd/` - is the precedent and the tooling. Its pipeline is
extract -> resolve -> verify -> gen_loader -> test, entirely offline, and every solved op in the
current table is the shape to copy. The most recent harness commits are `4488aecf2` -> `7fa3cea74`;
`tools/hd/README` carries the current 288/289 state and the one open op. Copy the resolution method
of an already-passing op in the same class; name the analogue op in the commit.

## Acceptance criteria

- [ ] `ccLoginDescriptorFix` (`0x0060D85B`) is resolved and lands, OR is recorded as `[UNKNOWN]`
      with the binary evidence for why it cannot be resolved - no invented address.
- [ ] With the op resolved, the shipping set is **289/289** and `tools/hd/verify.py` reports it.
- [ ] The two flagged cave-body edits are finished and pass the harness verifier.
- [ ] `tools/hd/test_hd.py` passes; the address-port count and shipping-set count in
      `tools/hd/README` are updated to match.
- [ ] The loader DLL rebuilds from the updated table via the harness `gen_loader` step. **No client
      launch** - the harness proves the patch offline.

## Do not

- Do not launch a client. The whole point of the harness is that this closes offline.
- Do not invent an address for `ccLoginDescriptorFix`. Resolve it or mark it `[UNKNOWN]`.
- Do not touch Group I - it is 23/23 and closed.
- Do not open any server work. HD adds zero opcodes and zero packet deltas (spec, no-op finding).
- Do not relabel this as v84 parity.
