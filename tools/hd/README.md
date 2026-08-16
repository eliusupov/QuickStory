# HD client on v84 — phase 1 patch set

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
| T2 neighbour-delta anchoring | 42 | 13.2% |
| T2b interval-identical bracketing | 1 | 0.3% |
| T2c one-sided identity extension | 5 | 1.6% |
| T3 function-scoped | 6 | 1.9% |
| T6 monotone-envelope | 8 | 2.5% |
| T7 forward-idiom in envelope | 11 | 3.5% |
| T5 data-site code xref | 1 | 0.3% |
| M hand-resolved | 4 | 1.3% |
| **resolved and verified** | **262** | **82.6%** |
| **rejected as false positive** | **16** | 5.0% |
| unresolved | 39 | 12.3% |

Per **operation**, after the source-bug corrections: **269 PASS, 0 FAIL, 55 unresolved,
3 dropped**. Restricted to the shipping set (groups C–J; A/B/L are already done by
`edits\`, K is optional gameplay caps): **293 ops → 256 PASS, 0 FAIL, 37 unresolved.**

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

T7 was validated against ground truth: `0x009F6E99`, `0x009F6EA0`, `0x009F7078`,
`0x009F707D` were resolved **by hand first** (the `neg/sbb/and 0x7FF50000/add 0xB0000`
window-style idiom and the `CWnd::CreateWnd` argument block), and T7 then reproduced
all four addresses independently. It also reproduced pass3's `0x009F7B1D → 0x00A4127E`,
which is `CWvsApp::InitializeGr2D` — the single most important site in the set.

A tier that was tried and **deleted**: widening T2's confirm window to ±0x4000. It
produced 4 hits and every one was rejected by the injectivity check. Do not
reintroduce it — a signature unique in an arbitrary window is not evidence.

## The 16 false positives — and why shape checking alone does not catch them

Every accepted hit must (a) reproduce in the second, independent v84 dump, (b)
disassemble to the same instruction shape as v83 — same mnemonic, same register
skeleton, immediate/displacement at the same offset and width.

**All 16 false positives passed the shape check.** `push 578` looks exactly like
`push 578` wherever it is. They were caught by two structural invariants instead:

- **injectivity** — two distinct v83 sites cannot be one v84 site. Sixteen sites
  collided on four v84 addresses.
- **monotonicity** — a delta far outside its neighbours' band is wrong.

The damage they would have done is concentrated: 16 of the 23 group-I operations (the
eleven near-identical party/guild/trade/quest pop-up blocks) collapsed onto two
addresses, and four group-D status-bar writes collapsed onto the addresses that
already belonged to `0x008CFD4B`/`0x008CFD50`. Applying them would have written eight
resolution values into two instructions.

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

**`codecaves.h` must be copied from upstream verbatim** and only its `dw…Retn`
constants replaced with the generated `HD_<name>_RETN` values. The cave bodies are
~600 lines of naked `__asm`; retyping them is how you get a silent crash.

To build: x86 DLL, no CRT dependency needed beyond `memcpy`/`memset`, link
`kernel32`. Any MSVC toolchain; match the other edit DLLs (they are ~30–70 KB x86).

## Offline harness: what each check proves

`verify.py` runs per operation and prints per-category pass/fail.

| check | proves |
|---|---|
| `ADDR` | the v84 target lies inside the dumped image |
| `SHAPE` | the bytes decode to the same instruction shape as the v83 site |
| `SLOT` | the write lands on that instruction's own immediate/displacement, at the right width — **this is the check the three latent bugs fail** |
| `CAVE` | for code caves: the NOP run tiles a whole number of v84 instructions, so the cave's `jmp` back at origin+N lands on an instruction boundary |
| `BLOCK` | same tiling check for `FillBytes` / `WriteByteArray` |
| `DUAL` | the same bytes appear in the second, independent dump |
| `FIT` | the value fits the operand width |

Plus, across the whole set: injectivity and monotonicity (above).

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
5. **The 37 unresolved shipping operations.** Their absence is mostly cosmetic
   (mis-placed widgets), but `0x008D1F65` / `0x008D217C` are status-bar code caves: if
   the other status-bar patches apply and these do not, the HUD will be *inconsistently*
   placed, which looks worse than not patching at all. See the manual test below.
6. **The `0x0040013E` 4 GB edit.** It patches the PE `Characteristics` field, which the
   loader has already consumed by the time any DLL runs. It is a no-op from inside the
   process — the source says as much. Port it into a file patcher or drop it.

## Manual test procedure (for when you are awake)

Do this on a **copy**, never on `D:\games\MSv84\client\` in place.

```
1.  robocopy D:\games\MSv84\client D:\games\MSv84\hd /E      (a full copy; ~2 GB)
2.  Build loader\ as hd-res-1.0.0.dll (x86). Put it and hd-res.ini in
        D:\games\MSv84\hd\edits\
    Leave the five existing edit DLLs exactly as they are.
3.  BEFORE launching, record the current registry value so you can put it back:
        reg query "HKLM\SOFTWARE\WOW6432Node\Wizet\MapleStory" /v ExecPath
    A launch REWRITES this machine-wide value and will repoint your other client.
4.  Launch D:\games\MSv84\hd\MapleStory.exe.
5.  AFTER the test, restore ExecPath to whatever step 3 printed.
```

Check in this order — each step gates the next, so stop at the first failure:

| # | what | proves | if it fails |
|---|---|---|---|
| 1 | window opens at 1280×720, not 800×600 | `0x00A4127E` / `0x00A41283` (`InitializeGr2D`) landed | the whole set is mis-timed or mis-addressed; nothing below will be meaningful |
| 2 | login screen: version number and world-select buttons in place | group E + the `VersionNumberFix` / `LoginBackCanvas` / `LoginViewRec` caves | E caves; `0x0060D85B` is known unresolved |
| 3 | mouse cursor reaches all four screen edges | `0x0059AC09/22`, `0x0059A898/8B1` cursor clamps | cursor clamp group in C |
| 4 | in game: status bar spans the bottom, HP/MP/EXP bars aligned | group D + `AdjustStatusBar` cave | **expect partial failure** — `0x008D1F65` and `0x008D217C` are unresolved |
| 5 | open a skill window, hover a buff icon: tooltip stays on screen | `0x008F32CC/DF` tooltip clamp | known unresolved (`0x008F32CC`) |
| 6 | receive a party/guild/trade invite | group I | 15 of 23 group-I ops are unresolved after the false-positive purge |
| 7 | open the cash shop | group F — 11/11 verified, all 9 caves pass | if this breaks, the cave mechanism itself is wrong |
| 8 | Mu Lung Dojo | group G — 11/11 verified, 10 caves | same |
| 9 | pick up an item / gain EXP: messages readable, not clipped | group H | 3 of 11 unresolved |

Groups F and G are the strongest evidence in the set (11/11 each, all caves verified
including NOP tiling), so **if the cash shop and Dojo render correctly the mechanism
works** and everything else is an address-coverage problem, not a design problem.

Capture a fresh memory dump while the client is running
(`docs/work-plan/tools/memdump/MemDump.exe`) and re-run `verify.py` against it — that
converts "verified against two dumps of an unpatched client" into "verified against the
client that is actually running".
