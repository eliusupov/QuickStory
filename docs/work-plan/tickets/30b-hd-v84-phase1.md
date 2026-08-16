# 30b — HD client on v84, phase 1: the verified patch set

Follows ticket 30 (phase 0, the feasibility measurement). Phase 0 asked *can this be
ported*; this ticket answers *to what, exactly, and how do we know*.

Tooling and data: **`tools/hd/`** — see `tools/hd/README.md` for the full write-up,
the tier table, the false-positive list and the manual test procedure.

> **Ticket 30 section 2.6 remains void.** Nothing here is built on it. Sections 2.1 and
> 2.4 were re-derived independently and 2.1 reproduces exactly (327 operations / 319
> addresses). 2.4 does **not** reproduce: see "corrections to phase 0" below.

## Result

| | |
|---|---|
| patch operations parsed from source | 327 (over 319 addresses, 317 distinct instruction anchors) |
| resolved to v84 **and verified** | 269 anchors — 84.9% |
| rejected as false positives | 10 |
| unresolved | 38 |
| **operations** PASS / FAIL / unresolved / dropped | **276 / 0 / 48 / 3** |
| **shipping set** (groups C–J) | **293 ops → 263 PASS, 0 FAIL, 30 unresolved (89.8%)** |

Phase 0 reported 255/316 = 80.7% "mechanically located". That figure is roughly right
in magnitude but was never checked for correctness. Re-derived with verification:
**10 of the hits phase 0's method produces are false positives**, and two new techniques
(monotone-envelope bracketing, forward-idiom scoring) plus nine hand-resolved sites
more than close the gap.

## Code caves: 2 of 30 need the asm body edited, not re-pointed

The harness compares the instruction sequence a cave displaces in v83 with the one it
displaces in v84. All 30 resolved caves tile their NOP run exactly; two replay
something that changed:

| cave | v83 displaced | v84 displaced | edit |
|---|---|---|---|
| `AlwaysViewRestoreFix` `0x00642105`→`0x0065797A` | `test eax,eax ; je 0x64210F ; mov ecx,[eax] ; push eax` | same but `je 0x657984` | retarget the `je` |
| `AdjustStatusBarInput` `0x008D217C`→`0x00906EBE` | `push 0x16 ; push edi ; lea ecx,[esi+0xCD0]` | `… lea ecx,[esi+0xD08]` | `CUIStatusBar` member moved `+0x38` |

The `+0x38` shift is confirmed independently by `0x008D247B → 0x009071BD`
(`[esi+0xCD4]` → `[esi+0xD0C]`). This invalidates the assumption that cave bodies are
v84-neutral — any v83 struct offset baked into one has to be re-checked.

One cave cannot be ported at all as written: `AdjustStatusBarBG` (`0x008D1F65`). Its v84
address is known (`0x00906D39`, by exhaustive enumeration — v83 has exactly three
`push 0x16` in the status-bar region, v84 has the matching two plus `0x00904888`), but
v84 recompiled the construct from a vtable call with an inline struct copy into a direct
thiscall, so a 5-byte NOP run no longer tiles (2+1=3 or 2+1+6=9). It needs a redesigned
cave: 3 NOPs, body `push nStatusBarY ; push edi ; jmp 0x00906D3C`. Recorded in
`tools/hd/data/manual-sites.json` under `not_portable_as_is`.

## Corrections to phase 0

1. **`localhome.exe` is a pre-patched localhost repack, not a stock v83 image.**
   `0x009F1C04` is already NOP'd, `0x009F242F` already `EB`, `0x009F7A9B` already
   `B8 00 00 00 00`, `0x00AFE084` already holds `192.168.1.109`, the manifest already
   says `asInvoker`. This is *direct evidence* for the architecture decision: groups A,
   B and L are what a localhost client already does, and on the v84 route they belong to
   `edits\bypass`, `redirect`, `no-patcher`, `skip-logo`, `window-mode`.
2. **"97% of resolution sites rewrite a literal 800/600/400/300" does not reproduce.**
   Measured: **170/262 = 65%**. The sites parse correctly; a large minority simply hold
   derived constants (578, 799, 464, 423, −427, 647). Section 2.4's 96% figure is wrong.
3. **319 source addresses are only 317 distinct instructions.** `0x009F7079` /
   `0x009F707E` are the immediates of the `push` instructions at `0x009F7078` /
   `0x009F707D`, which the source *also* patches. Two rows are duplicate spellings.
4. Six of the 35 code caves had no return-address constant under the name the author's
   convention implies; four are naming variants and one (`0x005F6994`) is a deliberate
   long jump into a later basic block, not a bug.

## The false positives (the part that matters)

All 10 **passed the instruction-shape check** — `push 578` looks like `push 578`
wherever it is. They were caught by two structural invariants instead:

- **injectivity**: two v83 sites cannot map to one v84 site
- **monotonicity**: v84 mostly only inserts code, so deltas rise with address. Mostly:
  v84 *removed* a few bytes between `0x008D1D50` and `0x008D1FF4` (v83 gap `0x2A4`,
  v84 gap `0x185`), so the check uses a band over four neighbours a side with an
  `0x800` margin, not a strict ordering.

8 of the 23 group-I operations (the eleven near-identical invite/pop-up blocks)
collapsed onto two v84 addresses, and four group-D status-bar writes collapsed onto
addresses already owned by `0x008CFD4B`/`0x008CFD50` — those four were then resolved
correctly by hand. Applying the false positives writes eight different resolution
values into two instructions.

**Consequence for anyone reusing phase 0's method: a shape check is not sufficient.**

Collisions are broken on delta agreement with the T1/hand-resolved skeleton *first*,
tier second — ranking on tier alone discards `0x00523FA3`, whose delta `+0xBC42` matches
its T1 neighbours on both sides exactly.

Three tiers were tried and deleted (measurements kept in `resolve.py`): widening T2's
window to ±0x4000 (4 hits, 4 rejected); sibling-delta point prediction (0 hits);
regalloc-tolerant sibling window (1 net hit, 4 new collisions).

## Group I is not a translation problem

The pop-up/invite cluster is the weakest part of the set (10 of 23 ops) and no
heuristic will close it, because v84 **rebuilt** the code:

- registers reallocated: v83's `mov eax,0x1FC ; sub eax,ecx` is `mov ecx,0x1FC ;
  sub ecx,eax` in v84 — harmless to the write, fatal to context signatures
- a branch was added with no v83 counterpart (`test esi,esi ; jne … ; mov ecx,0x1EC ;
  add edx,-0x33`), so v84 may need *more* patches here, on a path Ezorsia never saw
- there are fewer blocks: `mov edx,0x1D0` occurs **12×** in v83's `0x00522000-0x00525000`
  and **6×** in the matching v84 range. Eleven v83 sites cannot map injectively onto six.

Treat the 10 passing group-I rows as provisional. Everything outside group I is a clean
translation.

## Source bugs fixed in the generated table

The three from the brief, all confirmed against the binary, plus two more.

| id | site | fault | fix |
|---|---|---|---|
| P031 | `0x004D59B2` | `cmp ecx,0x258`, imm32 at **+2**, source says +1 → ships `adc eax,2` + a stray `add [esi+0xE],bh` | offset 2 |
| P311 | `0x00A448B0` | `add eax,0xFFFFFED4`, imm32 at **+1**, source says +2 → writes one byte into the following `cmp` | offset 1 |
| P158 | `0x0064061D` | is `idiv ecx`, no immediate. The intended `mov ecx,600` is `0x00640618`, already patched by the previous line | **delete** |
| P302/P304 | `0x009F7079` / `0x009F707E` | duplicate spelling of P301/P303 | drop |
| P323 | `0x00C08459` | manifest blank count is v83's literal length; v84's is one byte longer | count `0x16` |

## Method

Phase 0's three techniques, re-measured, plus two new ones. Full table in the README.
The load-bearing addition:

- **T6 monotone envelope** — bracket the site between resolved neighbours; v84 only
  inserts code, so the true delta lies between theirs. Search only that window.
- **T7 forward idiom** — when the window still holds several `push 600`s, decode forward
  from each candidate and LCS the mnemonic sequence against v83's.

T7 was validated against ground truth established by hand *first*: `0x009F6E99`,
`0x009F6EA0`, `0x009F7078`, `0x009F707D` were resolved manually via the
`neg/sbb/and 0x7FF50000/add 0xB0000` window-style idiom and the `CWnd::CreateWnd`
argument block; T7 then reproduced all four independently, plus pass3's
`0x009F7B1D → 0x00A4127E` (`CWvsApp::InitializeGr2D`, the most important site in the set).

## Loader

`tools/hd/loader/` — an `edits\` DLL, per the architecture decision. The contract was
read out of `ijl15.dll` (read-only): it hooks `GetStartupInfoA` and from there calls
`LoadDLLsFromDirectory("edits/", "*.dll")`; the five existing edit DLLs export nothing
and work from `DllMain`. That is the injection point and it is already correct.

**MinHook is not needed for v1** — all 30 resolved caves are raw `E9` into naked thunks,
and 14 of Ezorsia's 20 Detours hooks are jobs `edits\` already owns. MinHook is required
only for the optional `EzorsiaV2_UI.wz` side archive, which needs exactly two hooks.

The patch table is generated as a **formula** in W and H (`value = aW·W/2 + aH·H/2 +
aWH·W·H + k`, fitted from four sample resolutions and checked against the source's own
values at each), so resolution stays an ini setting.

## Not verified without a launch

Offline proof covers *where* to write and that the write is well-formed. It does not
cover: whether a patched constant produces the intended pixels; whether v84's `UI.wz`
wants v83's magic offsets (578, 464, −427, 647); DllMain timing; interaction with the
existing `edits\` DLLs; the 37 unresolved shipping operations; and `0x0040013E`, which
patches a PE header field the loader has already consumed and is a no-op in-process.

Manual test procedure — nine gated steps, on a **copy** of the client, with the
`HKLM\...\Wizet\MapleStory\ExecPath` save/restore step first — is in
`tools/hd/README.md`. Groups F (cash shop) and G (Dojo) are 11/11 verified with all
caves passing, so they are the designated canary: if those render, the mechanism works
and everything else is address coverage.

## Remaining effort

| | days |
|---|---|
| 17 non-group-I unresolved shipping ops by hand with `probe.py` | 1 |
| group I: re-RE the v84 pop-up handlers (restructured, not translatable) | 1.5 |
| redesign the `AdjustStatusBarBG` cave for v84's codegen | 0.5 |
| build + link the DLL, port `codecaves.h` verbatim, wire the cave table | 1 |
| first launch, gated walkthrough, fix what moved | 1.5 |
| re-tune v83 magic offsets to v84's `UI.wz` (unknowable until step 3) | 2 – 5 |
| `EzorsiaV2_UI.wz` side archive + its 2 MinHook hooks (optional) | 0.5 |
| **total** | **7.5 – 11** |

Phase 0 estimated 13.5–20 days for the whole job; ~4 of those are now spent and the
resolution work came in at the optimistic end.
