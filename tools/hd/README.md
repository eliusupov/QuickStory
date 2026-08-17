# HD client on v84 — phase 2 patch set

Turns the Ezorsia v2 HD mod (v83, hardcoded addresses) into a **verified** v84 patch
set, plus an offline harness that proves each patch without launching the client.

Nothing here writes to a client directory. `D:\games\MapleStory\`,
`D:\games\MSv84\client\` and `D:\games\dreamms\` are read-only inputs.

## Run it

```
git clone https://github.com/444Ro666/MapleEzorsia-v2   # into <scratchpad>/ezorsia-src
python tools/hd/extract.py      # source      -> data/ezorsia-v83-patches.json
python tools/hd/resolve.py      # v83 -> v84  -> data/v84-resolved.json      (~6 min)
python tools/hd/verify.py       # per-patch   -> data/v84-patchset.json
python tools/hd/gen_loader.py   # C++ table   -> loader/hd_patches.inc
python tools/hd/test_hd.py      # self-check
python tools/hd/probe.py        # hand-RE helper for what is left
```

Paths come from `paths.py`; override with `HD_V83`, `HD_V84_A`, `HD_V84_B`,
`HD_EZORSIA`. Target resolution is `WIDTH, HEIGHT` at the top of `extract.py` — but the
generated loader table is a **formula** in W and H, so the DLL reads `hd-res.ini` at
runtime and nothing needs regenerating to change resolution.

## What the numbers actually are

327 patch operations across 319 source addresses → 317 distinct *instruction* anchors
(after collapsing two duplicate spellings). Phase 0's 327/319 reproduce exactly.

| | anchors | share |
|---|---:|---:|
| T1 masked context signature | 184 | 58.0% |
| T1b context with the patch's own target masked | 10 | 3.2% |
| T2 neighbour-delta anchoring | 43 | 13.6% |
| T2b interval-identical bracketing | 1 | 0.3% |
| T2c one-sided identity extension | 6 | 1.9% |
| T3 function-scoped | 5 | 1.6% |
| T6 monotone-envelope | 8 | 2.5% |
| T7 forward-idiom in envelope | 11 | 3.5% |
| M hand-resolved | 34 | 10.7% |
| **resolved and verified** | **302** | **95.3%** |
| **rejected as false positive** | **1** | 0.3% |
| unresolved | 14 | 4.4% |

Per **operation**: **306 PASS, 0 FAIL, 14 unresolved, 7 dropped**. Restricted to the
shipping set (groups C–J; A/B/L are already done by `edits\`, K is optional gameplay
caps): **289 ops → 288 PASS, 0 FAIL, 1 unresolved (99.7%).**

Groups **C, D, F, G, H, I and J are complete.** E is 32/33. The single open op is
`ccLoginDescriptorFix` (`0x0060D85B`), and it is not an address problem — see
"What CANNOT be verified" below and `data/manual-sites.json`
under `not_portable_as_is`.

### What phase 2 changed

Phase 1 reported 288/317 and called group I "restructured, needs design not
translation". Four of its conclusions were wrong, and each was wrong for a reason
worth keeping:

- **Group I is a clean translation, and it is now 23/23.** Eleven of the twelve pop-up
  blocks are byte-identical to v83 apart from two immediates. Every tier missed them
  because v84 changed *both* patched immediates in the same signature window and T1b
  masks only the site's own. Phase 1's "there are fewer blocks in v84" was backwards —
  v84 has two *more*; the count compared `mov edx,0x1D0` occurrences, which undercounts
  exactly because v84 rewrote most of them to `mov edx,0x19D`.
- **`AdjustStatusBarBG` is portable**, at 9 displaced bytes rather than 5. Phase 1's
  proposed "3 NOPs" could not have worked — a 5-byte `E9` does not fit in 3 bytes.
- **`AlwaysViewRestoreFix` needs no body edit at all.** Its `je` is `74 06` in both
  images; phase 1 compared capstone's rendered *absolute* targets.
- **"97% of sites hold a vanilla 800/600" really measures 65%**, and separately, 17
  shipping sites hold a v84 constant that is *not* v83's — see below.

Hand-resolved sites live in `data/manual-sites.json` with their evidence written out;
`resolve.py` applies them last and they **override** any search-tier hit, because a
recorded chain of reasoning outranks a signature match. A manual answer that disagrees
with a T1 hit is printed as a warning rather than silently preferred.

## The three techniques phase 0 measured, re-measured

T1 and T2 reproduce phase 0 exactly at 184 and 50 raw hits. Phase 0's third tier
("function-scoped constant", 21 hits) was an instruction search inside an arbitrary
±0x300 window; rebuilt as a search inside a *located* function it yields 6, and the
rest of the gap is made up by two techniques phase 0 did not have:

- **T6 monotone envelope.** v84 only inserts code, so a site bracketed by resolved
  neighbours has a delta between theirs. That turns the image into a window of tens of
  KB in which the site's own bytes are often unique — and uniqueness inside a
  *provably correct* window is evidence, unlike uniqueness inside an arbitrary one.
- **T7 forward-idiom.** When the window still holds several `push 600`s, decode forward
  from each candidate and compare the mnemonic sequence to v83's by LCS. Forward
  decoding is aligned and reliable; backward decoding is not, so this only looks ahead.
- **T1b, the one that should have been obvious.** The bytes a patch overwrites are the
  one part of the site guaranteed not to matter — they are about to be replaced. And
  v84 changed exactly those on a whole class of sites: `push 0x122 → push 0x15E` (gain
  message canvas 290→350), `push 0x1F8 → push 0x1BC`, `push 0x320 → push 0x384` (avatar
  megaphone 800→900). Wildcarding the write range in the site's own signature recovers
  them. It is capped at 8 bytes so a 46-NOP code cave cannot mask its whole signature.

T1b was validated the same way as T7: `0x0089AF33`, `0x0089B2C6`, `0x0089B6F7` and
`0x0045B97E` were resolved **by hand first**, and T1b reproduced all four addresses
independently.

T7 was validated against ground truth: `0x009F6E99`, `0x009F6EA0`, `0x009F7078`,
`0x009F707D` were resolved **by hand first** (the `neg/sbb/and 0x7FF50000/add 0xB0000`
window-style idiom and the `CWnd::CreateWnd` argument block), and T7 then reproduced
all four addresses independently. It also reproduced pass3's `0x009F7B1D → 0x00A4127E`,
which is `CWvsApp::InitializeGr2D` — the single most important site in the set.

Three tiers were tried and **deleted**; the measurements are recorded in `resolve.py`
so nobody rebuilds them:

- widening T2's confirm window to ±0x4000 — 4 hits, all 4 rejected by injectivity. A
  signature unique in an *arbitrary* window is not evidence; one unique in an envelope
  derived window (T6) is.
- T8, propagating a sibling's delta to an exact predicted address — 0 hits.
- T9, the same tolerating v84's register reallocation within ±0x40 — 1 net hit and 4
  new collisions, one of which displaced a good result. See "group I" below for why.

## Group I: solved, 23/23

`0x00522C73`–`0x005243EF`, the twelve party/guild/trade/family/quest pop-up blocks.
Eleven are byte-identical to v83 apart from two immediates and the relocated call
targets. v84 lowered the pop-up base Y from `0x1FC` (508) to `0x1EC` (492) and the X
from `0x1D0` (464) to `0x19D` (413) on the majority of them.

The blocks are near-identical to *each other*, so no signature can separate them once
the changed fields are wildcarded — masking both immediates makes a ±32B window match
3 v83 sites and 7 v84 sites. **The join is ordinal**, and four independent constraints
force it and all agree:

- **five unique per-image markers** — delay literal `0x7530`, delay `0x1770`, a
  `neg esi ; sbb esi,esi` head, head immediate `0x1F2` with a `sub`, and the sid-less
  `push 0` variant. All five were resolved *independently* by T1/T2 in phase 1 and every
  one lands exactly where the ordinal join puts it.
- **dialog id** — each block ends `lea eax,[ebp+0xc] ; push <sid>`, and v84 shifted the
  string table by exactly +2. The two v84 blocks that disobey (`0x0052EB3F` sid `0x159A`,
  `0x0052EF50` sid `0x1599`) are dialogs v84 **added**; excluding them makes it 12 = 12.
- **gap runs** — v83 `+0x1F4,+0x1FE,+0x1E9` equals v84 `+0x1F4,+0x1FE,+0x1E9`.
- **monotonicity** — the twelve deltas are non-decreasing, and P042 falls in its slot.

One block, `0x0052307E` (trade), v84 genuinely did restructure: registers swapped
(`mov ecx,0x1FC ; sub ecx,eax`, so `regalloc` is set and operand geometry is the test),
and a runtime `test esi,esi` branch added selecting a second variant. Its identity is
certain — both blocks end with the same computed-sid idiom and both carry a
`push 0 ; push 0xFF ; push 0` tail no other block has. **Its second arm
(`0x0052E85C`, `mov ecx,0x1EC` and `add edx,-0x33`) has no v83 counterpart and is
UNPATCHED**; if that path is taken, that variant stays at its vanilla position.

## The values are as important as the addresses

Ezorsia's values are tuned to the **v83 literal at each site**. `m_nGameHeight - 92` is
really `508 + H - 600`, where 508 is what v83 happened to hold there. Where v84 shipped
a different literal, writing Ezorsia's number unchanged moves the widget by exactly the
difference — and every such row still passes ADDR, SHAPE, SLOT, DUAL and FIT. *The
address is right and the number is wrong*, which no address-oriented check can see.

`verify.py`'s **VANILLA DRIFT** table lists them; `gen_loader.py` subtracts the drift
from the fitted formula's constant term. 17 shipping ops are affected:

| group | ops | drift |
|---|---:|---|
| I | 12 | pop-up Y 508→492, X 464→413 |
| H | 3 | gain-message canvas 290→350, inventory X 504→444 |
| J | 2 | avatar megaphone 800→900 |

The group-I **Y** adjustment is *proven*, not inferred: Ezorsia used `H - 92` on the 508
sites and `H - 102` on the 498 sites, so its rule is demonstrably `vanilla + (H - 600)`.
The rest preserve the same intent, but that intent was eyeballed against v83's `UI.wz`
— **UNPROVEN**. Immediates inside the image VA range are excluded as relocated
addresses, not constants.

## The 9 false positives — and why shape checking alone does not catch them

Every accepted hit must (a) reproduce in the second, independent v84 dump, (b)
disassemble to the same instruction shape as v83 — same mnemonic, same register
skeleton, immediate/displacement at the same offset and width.

**All 9 false positives passed the shape check.** `push 578` looks exactly like
`push 578` wherever it is. They were caught by two structural invariants instead:

- **injectivity** — two distinct v83 sites cannot be one v84 site.
- **monotonicity** — a delta far outside its neighbours' band is wrong. Note this is a
  strong *heuristic*, not a law: v84 removed a few bytes between `0x008D1D50` and
  `0x008D1FF4` (v83 gap `0x2A4`, v84 gap `0x185`), so the check runs against a band over
  four neighbours a side with a `0x800` margin rather than a strict ordering.

Tie-breaking on the tier alone is wrong: `0x00523FA3`'s delta `+0xBC42` matches its
T1-resolved neighbours on *both* sides exactly, so it beats four better-ranked
claimants on the same address. Collisions are resolved on delta agreement with the
T1/hand-resolved skeleton first, tier second.

The damage they would have done is concentrated: 8 of the 23 group-I operations
collapsed onto two addresses, and four group-D status-bar writes collapsed onto the
addresses that already belonged to `0x008CFD4B`/`0x008CFD50` — those four were then
resolved correctly by hand to `0x00906C07`/`0x0C` and `0x00906D8C`/`0x91`. Applying the
false positives would have written eight resolution values into two instructions.

Full list in the `resolve.py` output and in `data/v84-resolved.json`
(`status: false-positive`, with `reason`).

## Source bugs found and fixed

`data/manual-sites.json` carries the evidence for each; `verify.py` applies them.

| id | site | fault | fix |
|---|---|---|---|
| P031 | `0x004D59B2` | instruction is `cmp ecx,0x258` (`81 F9 …`), imm32 at **+2**; source uses +1. As shipped it writes `81 D0 02 00 00 00` = `adc eax,2` plus a stray `add [esi+0xE],bh`. The comment "mov eax,800" describes the neighbouring `0x004D599D`. | offset 2 |
| P311 | `0x00A448B0` | instruction is `add eax,0xFFFFFED4` (`05 D4 …`), imm32 at **+1**; source uses +2, writing one byte past the immediate into the following `cmp`. Comment says "push -300"; it is an `add`. | offset 1 |
| P158 | `0x0064061D` | is `idiv ecx` (`F7 F9`) — no immediate at all. The `mov ecx,600` the comment means is `0x00640618`, which the previous source line already patches. As shipped it writes `F7 D0 02 00 00` = `not eax` over the divisor setup. | **delete** |
| P302/P304 | `0x009F7079`, `0x009F707E` | not destructive, but the same two dwords as `0x009F7078+1` / `0x009F707D+1`. The source writes them twice under two spellings. | drop the duplicate spelling |
| P323 | `0x00C08459` | manifest blank count is the v83 literal length. v84's literal is `requireAdministrator`, one byte longer; using v83's `0x15` leaves a stray quote and an invalid manifest. | count `0x16` |
| P113 | `0x005E3FA0` | **not a resolution constant.** The site is `push 0x10 ; push 0x258 ; call 0x403196 ; pop ecx ; pop ecx ; mov [edi],eax` — a two-argument cdecl allocator. The 600 is a `sizeof`; Ezorsia matched it because the literal happened to be 600. v84's counterpart (`0x005F8BCF`, inside the monotone band and the only such call in it) reads **608**, while every genuine resolution site still reads 600. A structure grew by 8 bytes; a screen did not. | **do not port** — writing a height there over-allocates at 720 and *under*-allocates below 608 |

Two more that are not bugs but are worth knowing:
`0x0049C2CD/0x0049CFE8/0x0049D398` mean to turn `push 0x80000002` (HKEY_LOCAL_MACHINE)
into `0x80000001`; in `localhome.exe` the push already reads `0x80000001`, so the write
is a no-op there. And `FillBytes(0x00AFE084, 0, 0x006FE0B2 - 0x006FE084)` computes its
count from two unrelated addresses — the difference (0x2E) is right, the expression is
a copy-paste artefact.

## A finding that changes what needs porting

`D:\games\MapleStory\localhome.exe` is a genuine full unpack, but of an **already
patched localhost repack**, not of a stock v83 client:

- `0x009F1C04` already holds five `0x90` (the start-up modal nop)
- `0x009F242F` already holds `0xEB` (the ad-balloon `jz`→`jmp`)
- `0x009F7A9B` already holds `B8 00 00 00 00` (forced window mode)
- `0x00AFE084` already holds `192.168.1.109` ×3, not `127.0.0.1`
- the embedded manifest already says `asInvoker`; v84's still says `requireAdministrator`

That is direct evidence for the architecture decision: **groups A, B and L are things a
localhost client already does.** On the v84 route they belong to `bypass`, `redirect`,
`no-patcher`, `skip-logo` and `window-mode` in `edits\`. Porting them would put a second
injection path over the same functions.

It also means the "97% of resolution sites rewrite a literal 800/600/…" figure in the
brief does not reproduce: measured against this image it is **170/262 = 65%**, because
a large minority of sites hold derived constants (578, 799, 464, 423, …) rather than a
raw 800/600. The sites are parsed correctly — the constant is simply not always vanilla.

## The loader

`loader/dllmain.cpp` + generated `loader/hd_patches.inc`, built as
`edits\hd-res-1.0.0.dll` with `edits\hd-res.ini` beside it.

The contract was read out of `D:\games\MSv84\client\ijl15.dll` (read-only): the proxy
is statically imported by `MapleStory.exe`, hooks `GetStartupInfoA` (the call the packed
client makes after unpacking), and from there calls its exported
`LoadDLLsFromDirectory` over `edits/` with mask `*.dll`. None of the five existing edit
DLLs export anything, so they all work from `DllMain(DLL_PROCESS_ATTACH)`. That is the
injection point, and it is already correct — no Detours, no `dinput8.dll`, no
`config.ini sleepTime` race.

**MinHook is not needed for v1.** All 30 resolved code caves are raw 5-byte `E9` jumps
into our own naked thunks, which is not a hook engine's job. Of Ezorsia's 20 Detours
hooks, 14 are CRC/anti-tamper, socket-connect redirect, or `CWvsApp` lifecycle rewrites
that only exist because Ezorsia mods a packed client — all owned by `edits\` on this
route. MinHook becomes necessary only for the optional `EzorsiaV2_UI.wz` side archive,
which needs exactly two hooks (`CWvsApp::InitializeResMan`, `StringPool::GetString`).
The hook table in `dllmain.cpp` is deliberately empty.

**`codecaves.h` must be copied from upstream verbatim** and its `dw…Retn` constants
replaced with the generated `HD_<name>_RETN` values. The cave bodies are ~600 lines of
naked `__asm`; retyping them is how you get a silent crash.

**31 caves resolve.** All tile their NOP run, and — new in phase 2 — nothing in either
image branches *into* any displaced range (`JMPIN`). Exactly **two need their body
edited**, because a cave body *replays* what it displaced:

| cave | v83 displaced | v84 displaced | edit |
|---|---|---|---|
| `AdjustStatusBarInput` `0x008D217C` → `0x00906EBE` (9B) | `push 0x16 ; push edi ; lea ecx,[esi+0xCD0]` | `… lea ecx,[esi+0xD08]` | member `+0x38` |
| `AdjustStatusBarBG` `0x008D1F65` → `0x00906D39` (**9B, not 5**) | `push 0x16 ; movsd ; push 0` | `push 0x16 ; push edi ; lea ecx,[esi+0xD04]` | **redesigned** |

`AdjustStatusBarBG` is a redesign, not a re-point: v84 recompiled the call from a vtable
call taking two `ZXString`s **by value** into a direct thiscall taking them **by
pointer**, so the v83 5-byte run does not exist in v84. It becomes the same shape as
`AdjustStatusBarInput` — 9 displaced bytes, 4 NOPs after the jmp, retn `0x00906D42`,
body `push nStatusBarY ; push edi ; lea ecx,[esi+0xD04]`. Its address is settled by four
independent lines of evidence (member offset, a constant +0xC SEH-state alignment with
no insertion across ~0x700 bytes, the identical `ZXString`-pair preamble, and argument
position); ordinal position alone was *not* sufficient, because the 2nd v84 `push 0x16`
has the shape of the *Input* cave.

`AlwaysViewRestoreFix` needs **no** edit. Its `je` is `74 06` — the same two bytes in
both images — and the cave body does not even replay that branch; it inverts the test
onto a local label.

**The `CUIStatusBar` shift is not uniform.** Members below ~`0xC40` moved `+0x30`
(`0xA90`→`0xAC0`); those above moved `+0x38` (`0xCD0`→`0xD08`, `0xCD4`→`0xD0C`, the
latter an ordered 11↔11 match). Do not assume one number — re-check any struct offset
against `verify.py`'s printed sequence. `gen_loader.py` emits the v84 displacement as
`HD_<name>_MEMBER` so the stale v83 literal need not stay in the naked asm.

### Building it — there is no toolchain on this machine

`where cl / gcc / g++ / clang / clang-cl / zig / rustc / nasm / ml / link` all return
nothing; `C:\Program Files\Microsoft Visual Studio`, the `(x86)` path, `LLVM`, `msys64`,
`mingw*`, `MinGW`, `Strawberry`, `TDM-GCC-64`, `Windows Kits` and `chocolatey` do not
exist; the VS7/VC7 registry keys are absent; scoop has only 7zip, godot, pandoc,
tesseract, typst. The only SDK present is `dotnet`, which cannot build a native x86 DLL
with MSVC-style `__asm`. **So the loader has NOT been compiled, and no claim that it
compiles should be believed until someone runs the command below.**

Note that MSVC is not merely convenient here, it is required: the cave bodies are
`__declspec(naked)` with `__asm { }` blocks, which GCC and Clang do not accept — they
would need rewriting into AT&T/extended asm first.

```
"C:\...\VC\Auxiliary\Build\vcvars32.bat"
cl /LD /O2 /GS- /MT /DNDEBUG dllmain.cpp /link /OUT:hd-res-1.0.0.dll kernel32.lib user32.lib
```
x86, no CRT dependency beyond `memcpy`/`memset`; `user32` only for the optional
`report=1` MessageBox. Match the other edit DLLs (~30–70 KB x86).

In place of a compile, `test_hd.py` lints the generated header for what the compiler
would have caught — field count, brace balance, unknown `HdKind`, duplicate `#define`,
rows that did not PASS — and checks that **no two shipped patches write the same byte**.
That last check is what caught the `VersionNumberFix` conflict described below.

## Offline harness: what each check proves

`verify.py` runs per operation and prints per-category pass/fail.

| check | proves |
|---|---|
| `ADDR` | the v84 target lies inside the dumped image |
| `SHAPE` | the bytes decode to the same instruction shape as the v83 site |
| `SLOT` | the write lands on that instruction's own immediate/displacement, at the right width — **this is the check the three latent bugs fail** |
| `CAVE` | for code caves: the NOP run tiles a whole number of v84 instructions, so the cave's `jmp` back at origin+N lands on an instruction boundary |
| `JMPIN` | **new** — nothing in the image branches *into* the range the cave overwrites. Not implied by tiling: tiling only proves the range *ends* on a boundary. Branch sources are filtered to real instruction starts by requiring the decode to pass through the origin |
| cave body (informational) | whether the displaced sequence is identical in v83 and v84 — if not, the naked `__asm` has to be edited. Relative branch targets are normalised *relative*, so a byte-identical `je` is not reported as a difference |
| `BLOCK` | same tiling check for `FillBytes` / `WriteByteArray` |
| `DUAL` | the same bytes appear in the second, independent dump |
| `FIT` | the value fits the operand width |
| `VANILLA` | **new**, informational — v84's own literal still equals v83's. Where it does not, Ezorsia's value means something different (above) |

Plus, across the whole set: injectivity, monotonicity, and — new — **write overlap**
(`test_hd.py`): no two shipped patches may write the same byte. Injectivity only ever
said two v83 sites cannot share one v84 *address*; it said nothing about two write
*ranges* intersecting.

That check found a real crash. `Client.cpp:541` is

```c
if (MainMain::bigLoginFrame) { WriteInt(0x005F464D + 1, W - 165); }   // P115
else                         { CodeCave(VersionNumberFix, 0x005F464D, 10); }  // P114
```

and `extract.py` was flattening the source without tracking conditionals at all, so
both were emitted. P115 lands on bytes +1..+4 of the cave's own `E9 rel32` — the login
screen jumps to a garbage address. `bigLoginFrame` defaults to false, so the cave arm is
the correct one. `extract.py` now tags every patch with its innermost guard and
`verify.py` drops the arms the shipped configuration does not take.

Two more things nothing had checked, both now fixed:

- `gen_loader.py` was emitting **every** PASS row regardless of group, so the DLL carried
  groups A, B and L — anti-tamper, **server IP**, window mode — on top of what
  `bypass` / `redirect` / `window-mode` already do in `edits\`. Group B would have fought
  `redirect-1.0.0.dll` over the server address. The table is the shipping set only now.
- Every emitted row carries the bytes it **expects** to find, read from the verified v84
  image, and the DLL compares before it writes. A client that is not the build this table
  was verified against gets skipped rather than corrupted. Being skipped is a visibly
  wrong screen; being wrong is a crash nobody can diagnose. Counts go to
  `OutputDebugStringA`, plus a MessageBox under `report=1` in `hd-res.ini`.

### Known false-positive lesson from phase 2

Injectivity **only detects a collision once both claimants are resolved.** P037
(`0x00522C87`) sat on the wrong group-I block through all of phase 1: it passed SHAPE
(`mov edx,0x1d0` on both sides) and nothing flagged it, because the address's real owner
was itself unresolved. Resolving a group can retro-actively expose false positives
inside it — so re-run the whole resolver after any manual batch, never just the new rows.

## What CANNOT be verified without launching

Offline analysis proves *where* to write and that the write is well-formed. It cannot
prove any of this:

1. **That a patched constant produces the intended pixels.** Every "?? possibly related
   to X" comment in the Ezorsia source is an untested guess even on v83; on v84 the same
   instruction may drive a different widget.
2. **That v84's UI layout wants the same numbers.** Ezorsia's magic offsets (578, 464,
   −92, −427, 647) are tuned to v83's `UI.wz`. v84 ships a different `UI.wz`.
3. **Timing.** That `DllMain` from the `GetStartupInfoA` hook fires before the patched
   code executes is established from strings and from the fact that the other five edit
   DLLs work — not from a trace.
4. **Interaction with `edits\`.** No two-writers-to-one-address conflict is possible from
   this table (groups A/B/L are excluded), but nothing proves `bypass` does not itself
   relocate code.
5. **The one unresolved shipping operation**, `ccLoginDescriptorFix` (`0x0060D85B`,
   group E). Its *function* is resolved beyond doubt — the enclosing code brackets
   instruction-for-instruction and the three descriptor pointers relocate 1:1
   (`0xAF70D0/84/80` → `0xB486D0/84/80`). The **cave** is not portable: it displaces 51
   bytes of argument setup that no longer exists — `and edx,0x3f ; add edx,0x21` does not
   occur *anywhere* in the v84 image — because v84 hoisted the literal coordinates into
   stack locals and added a second arm (`cmp ebx,4 ; jne` at `0x00622907`) with four
   constants v83 has no counterpart for.

   Two of the cave's four effects do map onto plain immediate writes
   (`0x00622956+2`, `and ebx,0x64` → 25; and `0x00622911+3`, the `-149` → `W-949`); two
   do not, one of them because v84's `add eax,0x21` is a sign-extended `imm8` that
   overflows above 706px of height. **Shipping the two portable halves alone would put
   the descriptor somewhere neither version intends, so it is deliberately left out.**
   Full working in `data/manual-sites.json` under `not_portable_as_is`. Finishing it
   needs a running client — it is a design job, not address translation.

   The `0x0052307E` group-I block's **second arm** is likewise unpatched (see above).
6. **The `0x0040013E` 4 GB edit.** It patches the PE `Characteristics` field, which the
   loader has already consumed by the time any DLL runs. It is a no-op from inside the
   process — the source says as much. Port it into a file patcher or drop it.

## Manual test procedure (for when you are awake)

Do this on a **copy**, never on `D:\games\MSv84\client\` in place.

```
1.  robocopy D:\games\MSv84\client D:\games\MSv84\hd /E      (a full copy; ~2 GB)
2.  Build loader\ as hd-res-1.0.0.dll (x86) -- see "Building it" above; there is no
    toolchain on this machine, so this step has never been run. Put the DLL and
    hd-res.ini in
        D:\games\MSv84\hd\edits\
    Leave the five existing edit DLLs exactly as they are.
    Put `report=1` under [general] in hd-res.ini for the first launch.
3.  BEFORE launching, record the current registry value so you can put it back:
        reg query "HKLM\SOFTWARE\WOW6432Node\Wizet\MapleStory" /v ExecPath
    A launch REWRITES this machine-wide value and will repoint your other client.
4.  Launch D:\games\MSv84\hd\MapleStory.exe.
5.  A message box should appear before the window opens, reading
        hd-res 1280x720: applied 288, skipped 0, byte-mismatch 0 of 288
    ANY non-zero byte-mismatch means your client is not the build this table was
    verified against -- STOP, and re-run verify.py against a fresh dump of it. Those
    rows were skipped rather than applied, so nothing is corrupted, but the rest of
    the table is then also suspect.
6.  AFTER the test, restore ExecPath to whatever step 3 printed.
```

Check in this order — each step gates the next, so stop at the first failure:

| # | what | proves | if it fails |
|---|---|---|---|
| 1 | window opens at 1280×720, not 800×600 | `0x00A4127E` / `0x00A41283` (`InitializeGr2D`) landed | the whole set is mis-timed or mis-addressed; nothing below will be meaningful |
| 2 | login screen: version number and world-select buttons in place | group E, 32/33 ops, + the `VersionNumberFix` / `LoginBackCanvas` / `LoginViewRec` caves | only `ccLoginDescriptorFix` (`0x0060D85B`) is unported — **expect the world-select descriptor to stay at its v84 position**; everything else on this screen should move |
| 3 | mouse cursor reaches all four screen edges | `0x0059AC09/22`, `0x0059A898/8B1` cursor clamps | cursor clamp group in C |
| 4 | in game: status bar spans the bottom, HP/MP/EXP bars aligned, **background layer moves with it** | group D, 31/31, + all three status-bar caves | if the background alone stays at 22px, `AdjustStatusBarBG`'s redesigned 9-byte cave is wrong — it is the one cave in the set whose body was rewritten rather than re-pointed |
| 5 | open a skill window, hover a buff icon: tooltip stays on screen | `0x008F32CC/DF` tooltip clamp, both resolved | if it clips, the regalloc-tolerant match at `0x00929BE1` is wrong |
| 6 | receive a party/guild/trade invite; then a **trade** invite specifically | group I, 23/23 | the pop-up should sit bottom-right and clear of the chat box. If it is 16px low or 51px right, the VANILLA-DRIFT adjustment is being double-applied; if only the *trade* pop-up misplaces, that is `0x0052307E`'s unpatched second arm |
| 7 | open the cash shop | group F — 11/11 verified, all 9 caves pass | if this breaks, the cave mechanism itself is wrong |
| 8 | Mu Lung Dojo | group G — 11/11 verified, 10 caves | same |
| 9 | pick up an item / gain EXP: messages readable, not clipped | group H, 11/11 | v84 already widened this canvas 290 -> 350, so check it is not double-counted |

Groups F and G are the strongest evidence in the set (11/11 each, all caves verified
including NOP tiling), so **if the cash shop and Dojo render correctly the mechanism
works** and everything else is an address-coverage problem, not a design problem.

Capture a fresh memory dump while the client is running
(`docs/work-plan/tools/memdump/MemDump.exe`) and re-run `verify.py` against it — that
converts "verified against two dumps of an unpatched client" into "verified against the
client that is actually running".
