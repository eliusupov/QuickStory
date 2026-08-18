# The HD / Ezorsia v2 client on v84

**Scope:** finish the owner's 1280x720 widescreen HD client so it launches and plays on v84,
decomposed into five tickets S1-S5 (files `74`-`78`). This is the specification; the tickets carry
the addresses, the acceptance criteria and what blocks each one. Nothing here restates a file path
or an offset, because both go stale and the tickets are where they belong.

This continues tickets `30-hd-client-v84` and `30b-hd-v84-phase1`. Those two are the record of the
port and the verified patch set; this spec does not repeat them. Read them for the history.

---

## The exception, stated first so nobody re-files it

HD / Ezorsia v2 is a **client-side resolution mod** - a widescreen renderer that runs on top of the
v84 client. It is **not in the v84 data.** By the standing rule (*is it in the v84 data? if no, we
do not build it*) it fails the parity test outright.

It is here anyway because the **owner asked for it** - the same shape of exception as the
`v83 legacy` rows. It is **owner-requested**, not a v84-parity gap, and **must never be relabelled
as one.** If a future audit finds an HD row and reasons "this isn't in v84, refuse it," the audit is
wrong: the owner gated this work in, and only the owner gates it out. Class: `owner-requested-hd`.

Licence: MapleEzorsia v2 is **AGPL-3.0**. The owner runs it himself, which is fine; anything derived
and ever redistributed inherits AGPL. Note it in whatever ships.

---

## Problem statement

The v84 cutover is done and an Evan plays on a real v84 client - but on the stock 800x600 client.
The owner's HD client is the one feature the cutover has not yet carried across. Ezorsia's effect
stack is hardcoded against v83 addresses; on v84 those addresses have moved, so the patches have to
be relocated to the v84 binary before the HD client will run.

The relocation is essentially done. An offline harness ports Ezorsia's hardcoded-v83 patches to a
v84 formula-based patch table and verifies them without ever launching the client. The shipping set
is at **99.7%** (288/289): the v83->v84 address port passes, the loader DLL builds and deploys, and
a full HD client is already assembled. What remains is one unresolved patch op, a short tail of UI
drift values, a mount check on the packaged UI archive, and the one thing an agent cannot do - a
human launching the client and walking every screen.

---

## The server-is-a-no-op finding - do not re-open server work

**HD requires zero server-side work.** It adds no opcodes and no packet deltas. `sendops-84` and
`recvops-84` already serve both v84 clients, HD and stock alike; every v84 wire fix this project
needed landed under tickets 22, 32, 36, 38, 40 and 41. HD is a rendering mod - it changes how the
client draws, not what it says on the wire.

This is stated plainly so nobody opens a server ticket for it. If a bring-up screen misbehaves, the
cause is a patch op or a UI value in the **client** stack, never a packet layout. No changeSet, no
opcode, no PacketCreator edit belongs to this effort.

---

## What "full support" means

The HD client **launches on v84 and plays end to end** at 1280x720: login, character select,
character creation, in-game with a correct status bar and chat, the inventory and equip windows, the
cash shop, the boss HP bar, super-megaphone rendering, and the Mu Lung Dojo UI - each drawing
correctly at the widescreen resolution, with no leftover v83 patch relocating a wrong address and no
UI element clipped or misplaced. The owner signs off after playing it on a copied client directory.

---

## The five slices

1. **S1 - close the last patch op and the flagged cave edits.** One op is still open
   (`ccLoginDescriptorFix` at `0x0060D85B`) and two cave-body edits are flagged. RE/harness work,
   fully offline - no launch. Agent.
2. **S2 - fold the 17 vanilla-drift UI values into the loader formula.** Seventeen UI coordinates
   that drift from the formula, folded in as explicit entries. Agent writes it; only the owner can
   confirm the pixels are right, on launch.
3. **S3 - verify `EzorsiaV2_UI.wz` mounts on v84 and its four StringPool ids resolve.** Offline
   structural check. Whether it renders needs a launch. Agent.
4. **S4 - first-launch bring-up walkthrough.** Boot the assembled HD client and walk every screen,
   fixing per screen. **Cannot be an agent** - it is a client launch, which agents may not do.
5. **S5 - owner verification sign-off** on a **copied** client directory. Human-only, launch.

S1-S3 are startable now, offline, by an agent. S4-S5 are blocked on the client launch and are the
owner's to run.

---

## Asset HD treatment for v84 new/changed content - PENDING RESEARCH

**This section is a placeholder. Its mechanism is not yet known and must not be invented here.**

The owner already runs a working v83 HD client and wants v84 to match it - including that v84's
**new and changed** maps and assets receive the **same HD treatment** the project already applies to
its assets, done the same way. Whether that treatment exists as an asset step at all is exactly the
open question:

- Upstream's README describes HD as mostly **runtime resolution scaling** plus optional **manual UI
  edits**, with **no automated asset pipeline**.
- The local v84 fork and the owner's on-disk v83 HD client may tell a different story. A research
  agent is investigating precisely this now.

**Do not author the asset mechanism, tickets, or acceptance criteria until that research lands.** The
five slices S1-S5 above are the *loader and bring-up* work and are independent of this question - S2
in particular is UI-layout only (the 17 known vanilla-drift coordinates), not the new-map asset
question. The asset tickets will be added once the mechanism is known and sourced, not before.

## Out of scope

- **Any server change.** See the no-op finding above.
- **Any `.wz` data edit or client/loader code change** authored from this spec. The tickets that
  touch the harness and loader are S1-S3; this document authors nothing.
- **Upstream's dinput8-proxy architecture.** Our fork delivers effects through the `ijl15.dll` +
  `edits\` stack; the port targets that, not upstream's proxy.
- **Re-deriving the patch table or the assembled client.** Both exist. This effort closes the tail
  and verifies, it does not rebuild.
- **Relabelling this as parity work.** It is owner-requested. Permanent.
