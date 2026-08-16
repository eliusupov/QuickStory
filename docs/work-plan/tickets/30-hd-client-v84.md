# 30 — Port the MapleEzorsia V2 HD client from GMS v83 to GMS v84

**What to build:** the owner's 1280x720 HD/widescreen client, working on v84, with the same
`config.ini` surface he has today.

**Blocked by:** 20 (v84 client verified). Should follow 23 (WZ base swap) so the HD client is
tested against the WZ tree it will actually ship with. Owner: *"hd client can be done last"* —
so this is the plan, not the build.

**Status:** phase 0 **delivered** (2026-08-16, see "Delivered — phase 0" at the end) — unpacked v84
code in hand, signature transfer measured, U1 and U2 closed. Phases 1–6 not started.

**Owner requirement, verbatim (ticket 17):**

> *"we need to be at v84, while still having all the features that we have now"*
> *"i dont want cheaper, i want fully working."*
> *"hd client can be done last"*

The HD client **is** one of those features. The v84 migration currently loses it. This ticket is
how it comes back.

**Labels:** `[FACT-measured]` = measured here, reproduction command given · `[FACT-sourced]` = cited
· `[INFERENCE]` = reasoning, marked · `[UNKNOWN]` = could not determine, say so.

**Licence.** MapleEzorsia V2 is **AGPL-3.0** (`LICENSE` in the upstream repo). It is public source
and the owner runs it himself. Porting it for him is fine. If any of it is ever redistributed, the
derived DLL is AGPL too — note it in whatever we ship. The `detours/` directory ships prebuilt
`detours.lib`/`syelog.lib` (Microsoft Detours, MIT since 4.0.1) — the recommended architecture
below drops Detours entirely, which also removes that question.

---

## 0. Verdict, up front

**Portable. Not cheap. Not hopeless. The blocker is one artefact, not the work.**

| | |
|---|---|
| Live patch operations to relocate | **327** `[FACT-measured]` |
| ...distinct target addresses | **319** |
| ...of which resolution-constant rewrites (mechanically findable) | **~97%** of the 230 measurable |
| Detoured client functions to re-point | **20** (+6 Windows API hooks, version-free) |
| v83→v84 anchor pairs already in hand | **131** `[FACT-measured]` |
| ...that land within 0x400 of an Ezorsia site | **8 of 263** |
| The riskiest unknown | **we do not have an unpacked v84 `MapleStory.exe`** |

The 159-key memory map is a superb *instrument* and a poor *shortcut*: it covers the client's
bootstrap/network/singleton skeleton, and Ezorsia lives almost entirely in the UI/render half.
It proves how to relocate. It does not do the relocating.

---

## 1. Prove the instrument before trusting the measurement

Three independent validations, in order. Each one had to pass before the next number was believed.

### 1.1 The v83 image on this disk is address-accurate `[FACT-measured]`

`D:\games\MapleStory\localhome.exe` (9,920,523 B) is a **fully unpacked v83 dump**: PE `.text`
`VirtualSize == SizeOfRawData == 0x7F8000`, so file offset = `VA - 0x400000` with no arithmetic.
(The owner's packed `MapleStory.exe` has `.text` raw 0x2DD000 vs virtual 0x7F8000 — compressed,
unreadable statically. So is the v84 client's.)

Read the bytes Ezorsia claims to patch, and compare to Ezorsia's own comments:

| Ezorsia address | Ezorsia comment | Bytes actually there | Verdict |
|---|---|---|---|
| `0x009F7B1D` | `push 600` | `68 58 02 00 00` | ✔ |
| `0x009F7B23` | `push 800` | `68 20 03 00 00` | ✔ |
| `0x0059AC22` | `mov ecx,600` | `B9 58 02 00 00` | ✔ |
| `0x0059AC09` | `mov ecx,800` | `B9 20 03 00 00` | ✔ |
| `0x0043717B` | `mov edi,600` | `BF 58 02 00 00` | ✔ |
| `0x00437181` | `mov esi,800` | `BE 20 03 00 00` | ✔ |
| `0x0059A15D+2` | `push -300` | `81 C1 D4 FE FF FF` (`add ecx,-300`) | ✔ |
| `0x009F5C50` | first byte must be `0xB8` (Themida wait) | `B8 2C 7E AE 00` | ✔ |
| `0x009F74EA+3` | WZ load-list size | `83 7D E8 0F` (`cmp [ebp-18],0Fh`) | ✔ |

**9/9.** The instrument reads the right bytes, and Ezorsia's annotations are trustworthy.

### 1.2 Independently derive v83→v84 address pairs, and check them against a table nobody
### told us about `[FACT-measured]`

`D:\games\MSv84\bypass\GMS-83.1\edits\*.dll` and `GMS-84.1\edits\*.dll` are the **same modules
built for two client versions**, same byte length, differing only in embedded constants. Byte-diff
them and read the differing DWORDs that fall in the client's VA range:

```
skip-logo      0x0062EDDA -> 0x00644274   +0x1549A
skip-logo      0x0062EEAE -> 0x00644348   +0x1549A
skip-logo      0x0059A338 -> 0x005AA58B   +0x10253
no-patcher     0x009F1C04 -> 0x00A3A1E1   +0x485DD
no-ad-balloon  0x009F242F -> 0x00A3AA0E   +0x485DF
window-mode    0x00BF1AC8 -> 0x00C4B150   +0x59688
```

Two of those v83 addresses appear **verbatim in Ezorsia's `Client.cpp`**:

- `Memory::FillBytes(0x009F1C04, 0x90, 5); //WinMain: nop ShowStartUpWndModal` — the no-patcher site
- `Memory::WriteByte(0x009F242F, 0xEB); //ShowADBalloon` — the no-ad-balloon site

Then, found afterwards: [`Chronicle20/gms-83-dll`](https://github.com/Chronicle20/gms-83-dll)
publishes `memory_maps/GMS/v83_1.cmake` and `v84_1.cmake`, **159 keys each**, hand-relocated and
documented in `docs/tasks/task-006-gms-v84-support/`. Every pair the binary diff produced appears
in that table with the identical value. Two methods, no shared derivation, same answers.

**The relocation instrument is proven.** From here on, `+delta` numbers are load-bearing.

### 1.3 The shape of the v83→v84 shift `[FACT-measured]`

131 code-section anchor pairs, `.text` grows `0x7F8000 → 0x851000` (**+0x59000 = 364,544 bytes**),
and the largest observed delta (`C_CONFIG_SYS_OPT_WINDOWED_MODE`, near the end of the image) is
**+0x59688**. Growth and terminal shift agree. The anchors are real.

The shift is **piecewise constant per function, and drifts between them**:

- `CInputSystem` — 5 anchors spanning 5,139 bytes, **all +0x10253**. Body unchanged in v84.
- `CLogo` — 10 anchors spanning 1,716 bytes, **all +0x1549A**. Body unchanged.
- `CWvsApp::CreateInstance` cluster — 12 anchors over 1,633 bytes, **all +0x49EB8**.
- but `C_FIELD_SEND_CREATE_NEW_PARTY_MSG` `+0xC156` vs `C_FIELD_SEND_JOIN_PARTY_MSG` `+0xC192`:
  **0x3C bytes inserted inside a 494-byte span.**
- and `InitializeResMan` `+0x49172` vs `InitializeGr2D` `+0x49701`: **0x58F bytes inserted between
  two functions 0x8E2 apart.**

Across the 130 consecutive anchor intervals (covering 99.4% of `.text`): **59 intervals (45%) have
zero net insertion**; net insertion density is ~4.4% of code bytes.

**Consequence, and it is the whole cost model:** you cannot interpolate. A site 200 bytes from a
verified anchor may or may not share its delta. Relocation must be per-site, evidenced.

---

## 2. Patch-site inventory `[FACT-measured]`

Reproduce: `scratchpad/live.py`, `groups.py`, `classify.py`, `sig.py`, `coverage.py`
(see Appendix C).

### 2.1 The headline count

**327 live patch operations**, against **319 distinct addresses** in the v83 image.

| operation | count |
|---|---|
| `WriteInt` (rewrite a 32-bit immediate in place) | 262 |
| `CodeCave` (5-byte `jmp` to injected asm + return) | 35 |
| `WriteByte` | 18 |
| `FillBytes` (NOP / blank) | 5 |
| `WriteString` | 4 |
| `WriteByteArray` | 2 |
| `WriteDouble` | 1 |
| **total** | **327** |

Split by entry point: `UpdateGameStartup` 29, `UpdateResolution` 298.

**17 further operations exist but are dead code** — `Client::EnableNewIGCipher()` (5) and
`Client::UpdateLogin()` (12) are never called from `dllmain.cpp`. Do not port them.

Beyond `Client.cpp`: **143 unique function pointers + 14 global pointers** bound to v83 addresses
in `AutoTypes.h`, used by the rewritten functions; **47 code-cave return addresses** in
`AddyLocations.h`; and **94 further addresses that appear only in comments** — the author's
research leads and abandoned attempts. Those 94 are *not* work; they are a map of where he looked.
Several are worth mining during the port rather than re-discovering.

Grand total of distinct v83 addresses referenced anywhere in the project: **539**.
Grand total that must actually be correct for the client to work: **319 + 20 hook entry points**.

### 2.2 By feature group

| # | group | ops | notes |
|---|---|---|---|
| C | **resolution core + unclassified geometry** | **200** | app size, viewport, cursor/mouse clamp, `CWnd::CreateWnd`/`CreateDlg` sizes, tooltip clamp, buff-icon strip, camera VR, screenshot buffers |
| E | login / world select / char select | 33 | includes 5 code caves (`LoginBackCanvas`, `LoginViewRec`, `LoginDescriptor`, `VersionNumber`, `AlwaysViewRestore`) |
| I | pop-up requests & invites | 25 | 11 near-identical blocks (party/guild/trade/family/quest-complete) — one pattern, eleven copies |
| A | bootstrap / anti-tamper / launch | 16 | password-packet cave, `CSecurityClient::OnPacket` kill, elevation strings, start-up modal, ad balloon, `CreateMainWindow` |
| J | smega / boss bar / server message | 14 | |
| G | Mu Lung Dojo (`Muruengraid`) | 11 | 11 code caves, one per HUD element |
| D | status bar / quickslot / chat | 11 | 3 code caves |
| F | cash shop | 10 | 10 code caves — the whole 800x600-centred cash shop |
| H | gain / pick-up messages | 10 | 3 code caves + `MsgAmount` plumbing |
| K | gameplay caps (damage/speed/tubi) | 6 | not resolution; INI-driven |
| B | localhost / IP redirect | 4 | `WriteString` x3 + blank |
| L | window mode / logo removal | 2 | |
| M | WZ load-list injection | 2 | 1 write + 1 code cave |
| | **total** | **327** | |

### 2.3 Detours hooks `[FACT-measured]`

`dllmain.cpp` enables **26** hooks:

- **6 Windows API hooks** (`CreateMutexA`, `WSPStartup`, `CreateWindowExA`, `FindFirstFileA`,
  `GetACP`, `GetModuleFileNameW`) — resolved by name at runtime, **completely version-free**.
  Zero port cost.
- **20 client-function hooks**, each pinned to a hardcoded v83 address. Of these, **6 are
  full function rewrites** whose bodies call into ~143 further hardcoded addresses
  (`CWvsApp::Run`, `SetUp`, `CWvsApp` ctor, `CallUpdate`, `InitializeInput`, `IWzNameSpace::Getitem`);
  the rest are thin instrumentation or single-value modifications.

The 6 rewrites are the expensive ones and, as §3 argues, **most of them should not be ported at all.**

### 2.4 What makes this tractable: the immediates are self-identifying `[FACT-measured]`

For all 230 `WriteInt` sites with a literal address, I read the *original* value the v83 client had
there:

```
sites                                   230
immediate is a vanilla 800x600 constant 220  (96%)
  W (800)      91     H (600)      69
  W/2 (400)    28     H/2 (300)    10
  -W/2 (-400)   9     -H/2 (-300)  13
not a resolution constant                10
```

And 3 of those 10 are resolution-derived products (screenshot buffers: `1440000 = 3*800*600`,
`480000 = 800*600`, `1920000 = 4*800*600`) and 4 are the non-resolution damage/speed caps.
**Effectively 97% of resolution sites are an instruction whose immediate is literally 800, 600,
400, 300, -400, -300 or a small offset thereof.** That is a searchable fingerprint, not a mystery.

### 2.5 Three latent bugs in the v83 source, found while measuring `[FACT-measured]`

Ezorsia writes at the wrong offset in three places. These corrupt neighbouring instructions in v83
today (evidently on cold paths, since the client runs). **Fix them in the port; do not faithfully
reproduce them.**

| site | Ezorsia writes at | actual instruction | what it should be |
|---|---|---|---|
| `0x004D59B2` | `+1` | `81 F9 58 02 00 00` = `cmp ecx,600` | imm is at **+2** |
| `0x00A448B0` | `+2` | `05 D4 FE FF FF` = `add eax,-300` | imm is at **+1** |
| `0x0064061D` | `+1` | `F7 F9` = `idiv ecx` | **not `mov ecx,600` at all** — comment is wrong; drop the site or re-find it |

### 2.6 Signature uniqueness — the relocation key ~~`[FACT-measured]`~~ **VOID — see D.3**

> **The numbers in this section are wrong.** The script that produced them measured addresses
> shifted by `+0x400000` and dropped every site above `0x7F9000`. Exact matching is also the wrong
> matcher across a relocated image. Superseded by **D.3** (the bug) and **D.4** (the real,
> measured numbers). Left in place because the *reasoning* about context-vs-bare-immediate is
> still correct — only the counts are void.

Bare immediates are far too common to key on (`push 600` occurs **30** times in v83 `.text`;
`push 800` **34**; a raw `600` dword **171**). Context is what disambiguates. Across the 172
distinct code sites, taking N bytes of surrounding context and counting occurrences in v83 `.text`:

| window | unique | ambiguous |
|---|---|---|
| ±8 B (16 B) | 104 | 68 |
| ±12 B (24 B) | 126 | 46 |
| ±16 B (32 B) | 133 | 39 |
| ±24 B (48 B) | 146 | 26 |
| **±32 B (64 B)** | **158 (92%)** | **14** |

The 14 that stay ambiguous at 64 bytes are the repeated UI boilerplate — the 11 identical pop-up
request blocks and similar — which are *semantically interchangeable anyway*: they all get the same
patch value. So the practical ambiguity is near zero.

**This is the automation thesis:** a 64-byte v83 context signature identifies 92% of sites uniquely.
Applied to an unpacked v84 image, a signature that still matches means the surrounding code is
byte-identical and the relocation is exact and provable. A signature that fails to match is a
**flag**, not a failure — it tells you precisely which function v84 changed, so RE effort goes
only where it is needed.

---

## 3. Per-group portability verdicts

Legend: **FREE** = already solved on v84 by the existing `edits\` modules or version-free by
construction · **AUTO** = expected to relocate by signature match, verify only · **ANCHORED** = a
verified v84 anchor exists nearby, short derivation · **RE** = needs fresh reverse engineering ·
**DROP** = do not port.

| group | ops | verdict | reasoning |
|---|---|---|---|
| **L** window mode | 1 | **FREE** | `edits\window-mode-1.0.0.dll` already writes `C_CONFIG_SYS_OPT_WINDOWED_MODE` at v84 `0x00C4B150`. Ezorsia's `0x009F7A9B` forced-window patch is redundant. Delete it. |
| **L** logo removal | 1 | **FREE** | `edits\skip-logo-1.0.0.dll` covers `CLogo::Init` / `LogoEnd` at v84 `0x00644274` / `0x00644348`. Ezorsia's `0x0062EE54` is in the same function (v83 `CLogo` block is all `+0x1549A`). Delete it. |
| **B** localhost / IP | 4 | **FREE** | `edits\redirect-1.0.0.dll` + `redirect.ini` already routes v84 to `127.0.0.1` and works today. Ezorsia's IP-string overwrite is a v83 unpacked-client technique; drop it. `config.ini`'s `ServerIP_Address` maps onto `redirect.ini`. |
| **A** bootstrap / anti-tamper | 16 | **FREE / DROP** | `edits\bypass-1.0.0.dll` + `no-patcher` + `no-ad-balloon` already handle CRC bypass, `ShowStartUpWndModal` (`0x00A3A1E1`), the ad balloon (`0x00A3AA0E`) and the security client on the **live, booting v84 client**. Ezorsia's 16 ops here overlap them almost exactly. Port **none** of them; keep the elevation edits only if the owner still needs them (they are annotated *"still not working unfortunately"* in the source). |
| **Detours** 20 client hooks | 20 | **DROP 14, RE 6** | The network rewrites (`CClientSocket::Connect` x3, `OnConnect`, `CWvsApp::ConnectLogin`) exist to make a vanilla client reach localhost — **`redirect` already does that on v84.** The tracking hooks (`Ztl_bstr_t` ctor, `~CWvsApp`, `IWzSeekableArchive`, `Ztl_variant_t::GetUnknown`) are dev logging — drop. What genuinely must survive: `StringPool::GetString` (WZ redirect), `CWvsApp::InitializeResMan` (WZ list), `sub_78C8A6` (v62 exp table, **only if** `useV62_ExpTable` is wanted), and the Themida-wait pattern. |
| **C** resolution core | 200 | **AUTO** | 97% are resolution-constant immediates with a 92%-unique 64 B signature. The bulk of the work, but the *most mechanisable* part of it. |
| **D** status bar / quickslot | 11 | **AUTO + 3 RE** | 8 immediates AUTO; 3 code caves (`AdjustStatusBar`, `...BG`, `...Input`) need their v84 return addresses and a check that the register conventions at the splice point still hold. |
| **E** login / world / charselect | 33 | **ANCHORED + RE** | Two verified anchors sit in this region (`C_LOGIN_UPDATE` v83 `0x005F4C16` → v84 `0x00609A9F` `+0x14E89`; `C_LOGIN_SEND_CHECK_PASSWORD_PACKET` `0x005F6952` → `0x0060B88B` `+0x14F39`). **The delta drifts +0xB0 between them** — code was inserted in `CLogin`. The 5 code caves here are the second-hardest cluster. |
| **F** cash shop | 10 | **RE** | All 10 are code caves that rewrite `CCashShop`'s window rectangles wholesale. `CCashShop::OnPacket` moved to v84 `0x47BF59` (ticket 20's discovery pass) and v84 **added cash-shop opcodes** — so this class is a genuine candidate for having changed. Highest-risk UI cluster. Also the most droppable if it fights: a mis-centred cash shop is cosmetic. |
| **G** Mu Lung Dojo | 11 | **RE** | 11 code caves, zero nearby anchors, and ticket 23 notes **v84 dropped one Dojo floor** — the surrounding code may well have moved. Lowest gameplay value per unit of effort. Ship without it if schedule bites. |
| **H** gain messages | 10 | **AUTO + 3 RE** | The `MsgAmount` feature is 3 code caves + 4 immediates. Owner runs `MsgAmount=26`; this is a feature he sees every time he kills a monster. Do not drop. |
| **I** pop-up requests | 25 | **AUTO** | 11 copies of one pattern. Find one, the rest follow. Cheapest 25 sites in the ticket. |
| **J** smega / boss bar / server msg | 14 | **AUTO** | Includes 2 `WriteByte`s that turn `mov` into `mov imm32` for the boss-bar extension — mechanical. |
| **K** gameplay caps | 6 | **AUTO** | Damage cap (double + int), speed cap x3, tubi. Not resolution; findable by their distinctive constants (`199999.0`, the `0x7FADCE49` clamp). |
| **M** WZ load-list injection | 2 | **RE — do first** | `CWvsApp::InitializeResMan` v83 `0x009F7159` → v84 `0x00A402CB` (`+0x49172`), **but +0x58F of code was inserted between it and `InitializeGr2D`** — so its body probably changed. This is the load-bearing hook for the whole WZ half; if it does not work nothing else matters. |

**Roll-up: 22 of 327 ops are already free, ~14 Detours hooks should be deleted outright, ~245 ops
are expected to relocate mechanically, and ~40 need real reverse engineering** — concentrated in
5 clusters (cash shop 10, Dojo 11, login 5, status bar 3, resman 2, gain msgs 3, plus the ~14
signature misses predicted by §2.6).

---

## 4. Recommended architecture

**MinHook on the existing `ijl15.dll` + `edits\` loader, as `edits\hd-1.0.0.dll`.**
Ticket 35 reached this recommendation from DreamMS; this ticket reaches it again from Ezorsia's own
source. Two independent routes, same answer.

### 4.1 The argument

The decisive fact is not "MinHook is nicer than Detours". It is that **the delivery half is already
solved on v84 and the compatibility half is already solved on v84.** `D:\games\MSv84\client\`
boots and plays today via `ijl15.dll` + `edits\`. Keeping Detours + `dinput8.dll` means porting
Ezorsia's *entire* bootstrap layer — the CRC bypass, the Themida timing dance, `MyGetProcAddress`,
the socket rewrites, the mutex/locale/file hooks — **all of which the `edits\` modules already do
on v84, proven by the client running.** That is ~36 of the 327 ops and 14 of the 20 client hooks
thrown away for free, and with them the hardest, crashiest, least debuggable part of the project.

Concretely, keeping Detours costs:

1. Re-porting `bypass` work that `edits\bypass-1.0.0.dll` has done and verified.
2. A second injection path (`dinput8.dll`) racing the first (`ijl15.dll`) — two DLLs both patching
   the same Themida-unpacked image, with `sleepTime` as the only synchronisation primitive. The
   owner has already been bitten by this: `sleepTime` 0→60 fixed his intermittent launch failures.
   Do not build a second copy of that race.
3. Ezorsia's own 6 whole-function rewrites, each dragging ~143 hardcoded v83 call targets. On v84
   every one of those must be re-resolved. That alone is comparable in size to the entire
   resolution port, for functionality `edits\` already provides.

Against that, Detours buys nothing v84-specific. `SetHook` in `Memory.cpp` is a 25-line wrapper
around `DetourTransactionBegin/Attach/Commit`; the MinHook equivalent (`MH_CreateHook` /
`MH_EnableHook`) is the same shape. The porting cost of the hook framework itself is a day.

### 4.2 What the module actually is

```
D:\games\MSv84\client\
  ijl15.dll                      unchanged  (proxy + LoadDLLsFromDirectory)
  edits\
    bypass-1.0.0.dll             unchanged  — CRC/anti-tamper
    redirect-1.0.0.dll  + .ini   unchanged  — localhost
    skip-logo-1.0.0.dll          unchanged  — group L
    window-mode-1.0.0.dll        unchanged  — group L
    no-patcher-1.0.0.dll         unchanged  — group A
    no-ad-balloon-1.0.0.dll      unchanged  — group A
    hd-1.0.0.dll                 NEW        — groups C D E F G H I J K M
  config.ini                     NEW file at client root, Ezorsia-format
  EzorsiaV2_UI.wz                NEW, re-based (§5)
```

`hd-1.0.0.dll` is one module because the patch groups share `m_nGameWidth`/`m_nGameHeight` and
because a partially-applied resolution set is worse than none. It reads `config.ini` with the same
`INIReader.h` (a single-header MIT-licensed INI parser, portable as-is).

Ordering hazard, and it is real: Ezorsia's `MainMain` waits for Themida to unpack by polling for a
known first byte at a known address (`ReadValue<BYTE>(0x009F5C50) == 0xB8`). `hd-1.0.0.dll` needs
the same guard with a **v84** address — the memory map gives it directly: `C_WVS_APP_RUN` v84
`0x00A3E7E8`. Verify the first byte is still `0xB8` before trusting it; if the `edits\` loader
already runs post-unpack, drop the poll entirely and say so.

### 4.3 `config.ini` compatibility — the owner's settings must survive

His live file (`D:\games\MapleStory\config.ini`, read-only, unchanged by this ticket):

```
width=1280      height=720      MsgAmount=26     WindowedMode=true   RemoveLogos=true
ServerIP_Address=127.0.0.1      setDamageCap=199999.0   useTubi=true  speedMovementCap=140
CustomLoginFrame=false          ownCashShopFrame=false  useV62_ExpTable=false
use_custom_dll_1..3=CUSTOM*.dll sleepTime=60
```

**Rule: the file is the contract. Same path, same sections, same key names, same defaults.** He can
copy his file across and it works. Per-key disposition on v84:

| key | v84 |
|---|---|
| `width` / `height` | `hd-1.0.0.dll` — the core feature |
| `MsgAmount` | `hd-1.0.0.dll` (group H) |
| `WindowedMode` | delegate to `edits\window-mode` — honour the key, act by presence/absence of that module or by writing `C_CONFIG_SYS_OPT_WINDOWED_MODE` directly |
| `RemoveLogos` | delegate to `edits\skip-logo`, same pattern |
| `ServerIP_Address` | write through to `edits\redirect.ini` at startup so one file stays authoritative |
| `setDamageCap`, `speedMovementCap`, `useTubi` | `hd-1.0.0.dll` (group K) |
| `CustomLoginFrame`, `ownCashShopFrame` | `hd-1.0.0.dll` — gate the login-frame and cash-shop caves exactly as v83 does |
| `useV62_ExpTable` | port only if he wants it; it is `false` today and it is a whole extra hook |
| `use_custom_dll_1..3` | **already covered** — the `edits\` folder *is* an arbitrary-DLL loader. Keep the keys working (`LoadLibraryA` from the client dir) so his file does not break, but the folder is the better mechanism; say so in the ticket close-out. |
| `sleepTime` | **keep, and keep it tunable.** He needed 60. Whatever the v84 loader's timing turns out to be, the knob stays — this is exactly the hardware-drift case where the calibration dial is not optional. |

Two keys change meaning rather than disappearing (`WindowedMode`, `RemoveLogos` become delegations;
`ServerIP_Address` becomes a write-through). Everything else is identical. Nothing he has set is
lost.

---

## 5. The WZ half — `EzorsiaV2_UI.wz`

`[FACT-measured]` `EzorsiaV2_UI.wz` is **1,370,002 B**, and the copy in the owner's live install is
**byte-identical (SHA-256 `0fbbed3d…`) to the one in the upstream repo** — he has never edited it.

Header: `PKG1`, `fileSize=1369942`, `hdrStart=0x3C`, `"Package file v1.0 Copyright 2002 Wizet, ZMS"`,
version word **172**. Node names are GMS-encrypted, so contents are not readable without a
version key.

### What it actually is — and why this is the cheap half

It is **not** a modified `UI.wz`. It is a **separate archive appended to the client's resource
load list**, via two mechanisms:

1. `codecaves.h` extends `resmanLoadOrder[]` with a 16th entry `"EzorsiaV2_UI"` and patches the
   list count at `0x009F74EA+3` (`cmp [ebp-18],0Fh` → 16), plus a code cave in
   `CWvsApp::InitializeResMan`.
2. `Hook_StringPool__GetString` redirects **exactly four StringPool IDs** to paths inside it:

   | ID | vanilla resource | redirected to |
   |---|---|---|
   | 1307 | `UI/Login.img/Common/frame` | `.../Common/frame{1024,1280,1366,1600,1920}` |
   | 1301 | `UI/CashShop.img/Base/backgrnd` | `.../Base/backgrnd` |
   | 1302 | `UI/CashShop.img/Base/backgrnd1` | `.../Base/backgrnd1` |
   | 5361 | `UI/CashShop.img/Base/backgrnd2` | `.../Base/backgrnd2` |

**So the whole WZ half is: one 1.37 MB side-archive holding ~8 images (five login frames, three
cash-shop backgrounds), plus four string IDs.** It touches nothing in `UI.wz`.

### Cost, and coordination with ticket 23

**Ticket 23's "`EzorsiaV2_UI.wz` has no baseline" flag is harmless here.** There is no baseline
because there is nothing to diff it against — it is not a v83 archive with v83 content, it is a
bespoke container. It never merges with `UI.wz`, so it cannot conflict with the base swap. Ticket
23 owns `wz/` and `docs/wz-baseline/`; **this ticket touches neither, now or during the build.**

Two things to do, both small:

1. **Re-stamp the archive for v84** — open in HaRepacker (already on disk at
   `D:\games\MapleStory\harepacker\`), save with the v84 version key. Mechanical.
   `[INFERENCE]` The version word gates name/offset decryption, so a v83-stamped archive will not
   mount in a v84 client. Cheap to confirm, cheap to fix, so it is a step and not a risk.
2. **Re-verify the four StringPool IDs against v84.** StringPool IDs shift when strings are
   inserted, and v84 inserted content. If 1307 moved, the login frame silently reverts to vanilla —
   a visible but non-fatal regression. This is the only genuine WZ-side unknown, and it is
   *cheap to test*: log the string every `GetString` returns for those IDs on first login screen.

**Estimated WZ-half cost: 0.5 day.** It is the easy half. The DLL is the hard half.

The `.img` variant (`MapleEzorsiaV2wzfiles.img`, 1,363,421 B) is for `Data\`-directory clients. The
owner runs `.wz`. Ignore it unless the v84 tree becomes `.img`-based.

---

## 6. Sequenced, costed plan

Estimates are **engineering days**, assume one agent, and assume IDA access to a v84 IDB
(see §7 — without it, phase 0 does not complete and nothing after it starts).

| # | phase | days | output |
|---|---|---|---|
| **0** | **Obtain an unpacked v84 `MapleStory.exe`.** Either the `GMS_v84.1_U_DEVM.exe` image behind ticket 20/21's IDA export, or a fresh Themida dump. Verify: `.text` raw size == virtual size; the 131 memory-map v84 addresses land on plausible function prologues; `C_WVS_APP_RUN` @ `0x00A3E7E8` starts with `0xB8`. | **1–5** | `v84-unpacked.exe`, hash-recorded |
| **1** | **Signature harvest + auto-relocate.** Extract a 64 B context signature for each of the 319 v83 sites from `localhome.exe`. Search each in the v84 image. Emit `v84-sites.json` with `{v83, v84, confidence, evidence}`. Cross-check every hit against the 131 anchors and against the piecewise-delta curve; a hit whose implied delta is wildly off its neighbours is a false positive, not a result. | **2** | `v84-sites.json`, a hit-rate number, and a **named list of misses** |
| **2** | **Resolve the misses.** Expected ~40 sites in 5 clusters (§3). Per the gms-83-dll signature-catalog method: prefer string xrefs / call-graph / imports over byte patterns. Record each in a reusable catalogue so v92 is cheaper than v84 was. | **3–6** | catalogue entries + the remaining `v84-sites.json` rows |
| **3** | **Build `hd-1.0.0.dll`.** MinHook; port `Client.cpp` + the 35 code caves + `INIReader`; delete groups A/B/L and 14 Detours hooks; fix the 3 latent bugs (§2.5); generate the address table from `v84-sites.json` rather than hand-typing it. | **3** | `edits\hd-1.0.0.dll` |
| **4** | **WZ half.** Re-stamp `EzorsiaV2_UI.wz` for v84; verify the 4 StringPool IDs. | **0.5** | re-based `.wz` |
| **5** | **Bring-up.** Login screen → world select → char select → in-game → status bar → chat → inventory → cash shop → a boss with a boss bar → a smega → Dojo. Fix per screen. This is where the estimate is softest; UI bring-up is empirical. | **3–5** | a working client |
| **6** | **Owner verification.** One launch, in a **copied** directory. His v83 install and his `D:\games\MSv84\client\` are never written to. | 0.5 | sign-off |
| | **total** | **13–22** | |

**Sequencing note.** Phases 1 and 4 are independent of phase 2 and can overlap. Phase 0 gates
everything. Run this **after ticket 23**, so bring-up happens against the WZ tree that ships.

### Cheapest credible descope, if schedule bites

Drop group **G** (Dojo, 11 caves) and group **F** (cash shop, 10 caves). That removes the two
highest-RE clusters — roughly 4 days — and costs a mis-centred cash shop window and mis-placed Dojo
HUD elements. Both cosmetic, both in content the owner rarely touches. **Ask him first**; he said
*"i dont want cheaper, i want fully working"*, so this is his call to make, not ours. Offer it only
if phase 2 overruns.

---

## 7. Unknowns — stated honestly

**U1 — CLOSED 2026-08-16, see D.1/D.2.** Unpacked v84 code obtained by reading the running client's
memory; no dump-and-rebuild was needed. **U2 — CLOSED, see D.4: 58.2% signature transfer, 80.7%
mechanically located.** Original text kept below for the record.

**U1 — THE RISKIEST: we do not have an unpacked v84 `MapleStory.exe`.** `[FACT-measured]`
`D:\games\MSv84\client\MapleStory.exe` is Themida-packed (`.text` raw `0x2FB000` vs virtual
`0x851000`). The IDA export names its source as `GMS_v84.1_U_DEVM.exe`, but that file is **not on
this machine** (searched `D:\games`, nothing above 6 MB but the two v83 dumps and unrelated
emulators), and the export's `binary`/`md5` fields are **empty strings**, so we cannot even confirm
which image produced it. Static relocation is *impossible* without one: signatures cannot be
searched in compressed bytes.
*Consequences if unobtainable:* the port falls back to live-memory RE against a running client
(slow, and the owner's rules forbid running clients here), or to 300+ manual IDA lookups. That is
the difference between ~15 days and ~40. **Resolve U1 before scheduling anything else.**

**U2 — signature transfer rate is predicted, not measured.** §2.6 measures 92% signature uniqueness
*within v83*. It does **not** measure how many of those signatures still exist in v84 — that
requires U1. The anchors bound it: 45% of anchor intervals have zero net insertion, net insertion
density ~4.4%. `[INFERENCE]` a 70–90% transfer rate is plausible; below 50% the phase-2 estimate
doubles. Phase 1's first deliverable is this number. Do not commit to phase 2's estimate before
seeing it.

**U3 — code caves are harder than immediates and the plan may under-price them.** 35 of the 327 ops
splice hand-written assembly into the client, relying on specific registers being live at the
splice point. A signature can find the *address*; only reading the surrounding v84 disassembly
confirms the *register state*. If v84's compiler allocated registers differently in even a few of
those functions, each becomes a bespoke rewrite. The Dojo (11) and cash shop (10) clusters are 60%
of the caves and have zero nearby anchors.

**U4 — StringPool IDs 1307 / 1301 / 1302 / 5361 are unverified on v84.** Cheap to check
(one login screen with logging), impossible to check without running a client, which this ticket
does not do. Worst case is a cosmetic revert to the vanilla login frame.

**U5 — the v83 baseline is a *pre-modified* localhost, not vanilla.** `localhome.exe` already has
the IP written (`192.168.1.109`), the damage cap set (`1333333337.0`) and the speed cap set
(`77777`). Resolution sites are pristine (§2.4 proves it: 220/230 still hold vanilla 800x600
constants), so the signature harvest is safe. But **do not** harvest a signature for a group-K
site from this image — for those four, use the owner's packed `MapleStory.exe` only as a
cross-check, or accept that they must be found by their v84 constant instead.

**U6 — `[UNKNOWN]` whether v84 changed the UI layout code at all.** Every measurement here is about
*where* code moved, not *what* it does. v84 added cash-shop opcodes and dropped a Dojo floor
(ticket 23) — both hint at edits in exactly the two clusters we rated highest-risk. Nobody has
looked at a v84 `CCashShop::OnCreate`. Phase 1's miss-list is the first evidence either way.

**U7 — `[UNKNOWN]` how `hd-1.0.0.dll` and `bypass-1.0.0.dll` interact.** Both patch the same
unpacked image. The `edits\` loader's ordering is not documented here and was not read. Ezorsia's
own README warns that its edits override conflicting third-party DLLs — the same hazard, mirrored.
Determine load order before writing the first patch, not after the first crash.

---

## 8. Acceptance criteria

- [ ] An unpacked v84 image exists, hash-recorded, `.text` raw == virtual, and 131/131 memory-map
      v84 addresses land on plausible function starts (U1 closed)
- [ ] `v84-sites.json` covers **all 319** live addresses; every row carries evidence
      (`signature-match` / `anchor+offset` / `manual-RE:<catalogue-id>`); **zero rows guessed**
- [ ] The signature-transfer rate is reported as a number, not an impression (U2 closed)
- [ ] The 3 latent v83 bugs (§2.5) are fixed, not reproduced
- [ ] Groups A, B, L are **not** ported — delegated to existing `edits\` modules, and that
      delegation is demonstrated working
- [ ] The owner's existing `config.ini` is copied across **unedited** and every key behaves as it
      does on v83; `sleepTime` still tunable
- [ ] `EzorsiaV2_UI.wz` mounts on v84 and the 1280x720 login frame renders
- [ ] Playthrough at 1280x720: login → world → char select → in-game → status bar → chat →
      inventory → skill window → cash shop → boss bar → smega. No element off-screen or clipped.
- [ ] `wz/` and `docs/wz-baseline/` untouched by this ticket (ticket 23 owns them)
- [ ] `D:\games\MapleStory\`, `D:\games\MSv84\client\`, `D:\games\dreamms\` never written to

## 9. Verification gate

Owner launch: **1**, against a **copy** of the v84 client directory. His v83 HD install remains his
working fallback throughout and is never modified.

## 10. Rollback

Delete `edits\hd-1.0.0.dll`, `config.ini` and `EzorsiaV2_UI.wz` from the v84 client directory. The
client returns to the plain-`edits\` state that boots and plays today. The owner's v83 HD client is
independent and untouched. **There is no state to unwind** — this is the one genuinely reassuring
property of the `edits\` architecture, and a second argument for it over `dinput8.dll`.

---

## Appendix A — verified v83 → v84 anchors (excerpt)

Full table: 131 pairs, `gms-83-dll` `memory_maps/GMS/v83_1.cmake` vs `v84_1.cmake`.
Selected rows relevant to Ezorsia's clusters:

| key | v83 | v84 | delta |
|---|---|---|---|
| `C_INPUT_SYSTEM_INIT` | `0x00599EBF` | `0x005AA112` | `+0x10253` |
| `C_INPUT_SYSTEM_SHOW_CURSOR` | `0x0059A338` | `0x005AA58B` | `+0x10253` |
| `C_INPUT_SYSTEM_GENERATE_AUTO_KEY_DOWN` | `0x0059B2D2` | `0x005AB525` | `+0x10253` |
| `C_LOGIN_UPDATE` | `0x005F4C16` | `0x00609A9F` | `+0x14E89` |
| `C_LOGIN_SEND_CHECK_PASSWORD_PACKET` | `0x005F6952` | `0x0060B88B` | `+0x14F39` |
| `C_LOGO_INIT` | `0x0062EDDA` | `0x00644274` | `+0x1549A` |
| `C_LOGO_LOGO_END` | `0x0062EEAE` | `0x00644348` | `+0x1549A` |
| `WIN_MAIN` | `0x009F19F2` | `0x00A39FA0` | `+0x485AE` |
| `C_WVS_APP_RUN` | `0x009F5C50` | `0x00A3E7E8` | `+0x48B98` |
| `C_WVS_APP_CREATE_MAIN_WINDOW` | `0x009F6D97` | `0x00A3FDD1` | `+0x4903A` |
| `C_WVS_APP_INITIALIZE_RES_MAN` | `0x009F7159` | `0x00A402CB` | `+0x49172` |
| `C_WVS_APP_INITIALIZE_GR2D` | `0x009F7A3B` | `0x00A4113C` | `+0x49701` |
| `C_WVS_APP_INITIALIZE_INPUT` | `0x009F7CE1` | `0x00A4153D` | `+0x4985C` |
| `C_WND_MAN_REDRAW_INVALIDATED_WINDOWS` | `0x009E4547` | `0x00A2C96F` | `+0x48428` |
| `C_CONFIG_SYS_OPT_WINDOWED_MODE` | `0x00BF1AC8` | `0x00C4B150` | `+0x59688` |

The single most useful derived prediction, for phase 1 to falsify first:
`dwApplicationHeight` is `InitializeGr2D + 0xE2`, so **`0x00A4121E`** on v84 if the body is
unchanged — and `dwApplicationWidth` at `+0xE8` → **`0x00A41224`**. If those two hold, the whole
mechanism holds.

## Appendix B — sources

- MapleEzorsia V2 — https://github.com/444Ro666/MapleEzorsia-v2 (AGPL-3.0), read at depth-1 clone
- `Chronicle20/gms-83-dll` — https://github.com/Chronicle20/gms-83-dll — memory maps, the
  `task-006-gms-v84-support` port record, `signature-catalog.md` and `version-porting-workflow.md`.
  **Read `signature-catalog.md` before phase 2**; it is the exact playbook this ticket's phase 2
  assumes, written by people who already did this once for these 159 keys.
- `D:\games\MSv84\bypass\{GMS-83.1,GMS-84.1}\` — the paired binaries the anchors were derived from
- `D:\games\MSv84\opcodes\{ida_export_gms_v84.json, discover_gms_v84.md}` — v84 function addresses
  (866 functions; packet-handler coverage, **not** UI/render coverage — see §0)
- `D:\games\MapleStory\localhome.exe` — the unpacked v83 reference image (read-only)
- Ticket 35 (`docs/work-plan/35-dreamms-client-mining-results.md`) §Q3 — the MinHook + `ijl15`
  recommendation this ticket independently confirms
- Ticket 23 — coordination on `EzorsiaV2_UI.wz`; no shared files

## Appendix C — reproduction

Scripts written during this research (scratchpad, not committed — they are one-shot measurement
tools; re-derive from this appendix if needed):

| script | produces |
|---|---|
| `live.py` | the 327 / 319 counts, by-op breakdown |
| `groups.py` | the §2.2 feature-group table |
| `classify.py` | §2.4 — original immediates read from the v83 image, 800/600-family classification |
| `sig.py` | §2.6 — signature uniqueness at 16/24/32/48/64 B windows |
| `coverage.py` | §1.3 flat-delta spans, and site-to-nearest-anchor distances |

All read-only. The only inputs are the cloned source, the `gms-83-dll` memory maps, the paired
`bypass\` DLLs and `localhome.exe`. Nothing under `D:\games\MapleStory\`, `D:\games\MSv84\client\`
or `D:\games\dreamms\` was written.

---

# Delivered — phase 0

**Status: U1 CLOSED. U2 CLOSED (measured). One of this ticket's own instruments was found broken
and its §2.6 numbers are void — replaced below.**

Executed 2026-08-16 on branch `worktree-evan-dualblade`. Tools committed at
`docs/work-plan/tools/memdump/`.

## D.1 We have unpacked v84 code, and it did not need a dump-and-rebuild

Phase 0 assumed a Themida dump (1–5 days). It cost minutes instead. The client unpacks itself
into memory on every launch, and **for signature matching you do not need a rebuilt PE — you need
the unpacked bytes.**

```
launch D:\games\MSv84\client\MapleStory.exe   (unmodified, its normal ijl15+edits\ path)
OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ) + ReadProcessMemory over the module image
```

No debugger attach, no injection, no patching, no anti-anti-debug. `docs/work-plan/tools/memdump/MemDump.cs`
(~150 lines, builds with in-box `csc.exe`, no SDK).

| | |
|---|---|
| bytes read | **12,337,152** (`0xBC4000`, whole image, `ImageBase 0x400000`) |
| pages | **3012 / 3012, zero failures** |
| blob arithmetic | file offset = `VA - 0x400000`, same as `localhome.exe` |
| dump 1 sha256 | `431e3a7bdcab20a2ba2646d2bfc0d503a710950d6ca1a75ce29d4cbf693fbc0e` |
| dump 2 sha256 | `b8fc370793351cb5c63bc4ce078645a23425e872d34273b38c3ae809402650e7` |

**Themida did NOT block `ReadProcessMemory`.** The one obstacle was mundane: the client's manifest
is `requestedExecutionLevel level="requireAdministrator"`, so it runs at high integrity and a
medium-integrity reader gets `ERROR_ACCESS_DENIED`. Diagnosed, not guessed — the granted access
mask was exactly `QUERY_LIMITED_INFORMATION` + `SYNCHRONIZE` and nothing else, which is the
mandatory-integrity signature, not a Themida DACL strip (Themida denies limited-info too). This
machine has `ConsentPromptBehaviorAdmin = 0`, so an elevated dumper needed no prompt and no trick.
`[FACT-measured]`

The blobs are 12 MB each and are **not committed**; they live in the session scratchpad
(`…\153450ca-…\scratchpad\v84_mem.bin`, `v84_mem2.bin`). Re-derive in ~30 s with the tool.

## D.2 Proof the read is genuine — five independent checks

Ticket 20 rejected its own PE detector for flagging `notepad.exe`. Same standard applied here.

1. **Control, before the target.** Dumped 32-bit `cmd.exe` and compared to its file on disk:
   `.reloc` **100%** identical, `.rsrc` **100%**, `.text` 97.7% (the 2.3% is ASLR base relocation —
   cmd loaded at `0x690000`). The instrument reads the right bytes out of the right process.
2. **Known plaintext inside the target.** Memory vs disk, per section: Themida's own
   `oyihhyms` section **99.74% identical over 1.58 MB**, `qytrjskw` **100%** — so the read landed on
   the right image at the right base. Meanwhile `.text` is **0.4%** identical to its on-disk raw
   bytes (`raw 0x2FB000` vs `virtual 0x851000`), i.e. what we hold is the **decompressed** code.
   Packed and unpacked, in one measurement.
3. **Two independent launches agree.** Killed the client, relaunched, dumped again: over
   `VA 0x401000–0xB41000` the two dumps are **byte-identical**, first difference at `0xB41000`
   (past the code, in image data). The unpacked code is stable run-to-run, so signatures over it
   are meaningful. Every resolved site below was re-verified against dump 2: **zero disagreements**.
4. **An independent prediction lands.** §1.2 derived v84 `0x00A3A1E1` for `ShowStartUpWndModal` by
   byte-diffing the paired `bypass\GMS-8{3,4}.1\edits\*.dll`. In the dump, that address holds
   `90 90 90 90 90` — `no-patcher`'s five NOPs, exactly. A prediction from a completely different
   method, confirmed in the memory image.
5. **Cross-image, cross-version.** At the 14 Appendix A anchors the v84 bytes are the v83 bytes
   modulo operands, e.g. `CInputSystem::Init`:
   ```
   v83 0x00599EBF  B8 AC FF A8 00  E8 CF 6C 4C 00  83 EC 10 8B 45 08 53 8B D9 56 89 03 8B 45
   v84 0x005AA112  B8 C8 DC AD 00  E8 FC 2B 50 00  83 EC 10 8B 45 08 53 8B D9 56 89 03 8B 45
   ```
   Identical code; only `mov eax,<VA>` and one `call rel32` differ. Two anchors
   (`SHOW_CURSOR`, `GENERATE_AUTO_KEY_DOWN`) match **16/16 bytes exactly**.

## D.3 Two broken instruments found — §2.6 of this ticket is void

**B1. `sig.py` measured the wrong addresses.** It parses `0x00XXXXXX` with
`re.finditer(r'0x00([0-9A-Fa-f]{6})')`, which captures the digits *after* the `0x00` prefix, then
**adds `0x400000` again**. So Ezorsia's `0x0043717B` became `0x0083717B`, and every true site
`>= 0x7F9000` — i.e. the whole `0x009Fxxxx` / `0x00A4xxxx` `CWvsApp` half — was silently dropped by
its range filter.

```
site 0x0043717B  true bytes  BF 58 02 00 00  = mov edi,600   (what §1.1 verified)
                 sig.py read 00 C3 6A 18 B9  = unrelated code
```

This ticket's §2.6 table (`158/172 unique @ 64 B`, and the whole "92% signature uniqueness"
automation thesis built on it) was computed over 172 wrong locations. It was reproduced here
exactly — 158/172, 146/26, 133/39, 126/46, 104/68 — which is how the bug was found. **Delete
§2.6's numbers; use D.4's.** `live.py` and `classify.py` parse correctly (`0x00…` prefix included),
so **§2.1 (327/319) and §2.4 (220/230 = 96%) stand** — both reproduced exactly here.

**B2. Exact byte matching is the wrong matcher and always was.** Identical x86 code cannot match
byte-for-byte across a relocated image: absolute VAs and `call rel32` displacements necessarily
differ (see the prologue above). Measured, for the record: exact 64 B matching transfers
**8/172 = 4.7%**. That is a property of the technique, not of v84. Signatures must wildcard operand
bytes. The mask used here needs no length-disassembler: any 4-byte LE dword inside
`[0x400000,0xC00000)`, plus `E8`/`E9` rel32 whose target lands in that range, becomes a wildcard.

## D.4 The measured number U2 asked for

316 of the 319 distinct live addresses lie in v83 `.text` and are what this measures.
Widest-window-first, `±32 / ±24 / ±16 / ±12` bytes, masked. `[FACT-measured]`

| tier | method | sites | % |
|---|---|---|---|
| **resolved** | globally unique masked context signature | **184** | **58.2%** |
| **anchored** | nearest-neighbour delta + short local signature in ±0x1000 | **50** | 15.8% |
| **window-constant** | neighbour delta + the site's own instruction, unique in ±0x300 | **21** | 6.6% |
| drop-anyway | groups A/B/L — `edits\` already does it on v84 (§3) | 8 | 2.5% |
| U5 baseline | `localhome.exe` is pre-modified, signature cannot exist in v84 | 5 | 1.6% |
| **unresolved** | **genuine manual RE** | **48** | **15.2%** |

> **Signature transfer rate: 58.2%.** Mechanically located overall: **255/316 = 80.7%**
> (**82.8%** of the 308 sites that actually need porting).

§7's `[INFERENCE]` of 70–90% was optimistic for pure signature transfer and about right once
anchoring is included. It is **not** below 50%, so the phase-2 estimate does not double.

**Cross-checks on the 255:**

- deltas outside the `[0, +0x59688]` anchor envelope: **0**
- disagreements with the second independent dump: **0**
- resolution constant preserved at the resolved v84 site: **113 same, 0 different**
- 54 "non-monotonic" deltas — **the check is wrong, not the results.** Seven sites in `CLogo`
  resolve at delta **+0x1549A**, which is *exactly* the published `C_LOGO_INIT` anchor delta from
  Appendix A, derived by an unrelated method. v84 removes code as well as inserting it, so
  §1.3's "code is only ever inserted" assumption does not hold and monotonicity must not be used
  as a false-positive filter.

## D.5 Appendix A's headline prediction is falsified — cheaply

> *"`dwApplicationHeight` is `InitializeGr2D + 0xE2`, so `0x00A4121E` on v84 if the body is
> unchanged… If those two hold, the whole mechanism holds."*

It does not hold. `0x00A4121E` contains `A1 6C AB C4 00` (`mov eax,[0xC4AB6C]`). The body of
`InitializeGr2D` changed. **But the two sites are alive and unambiguous** — scanning the function
finds exactly one `push 600` and one `push 800`:

| | v83 | v84 | delta |
|---|---|---|---|
| `dwApplicationHeight` | `0x009F7B1D` | **`0x00A4127E`** | `+0x49761` |
| `dwApplicationWidth` | `0x009F7B23` | **`0x00A41283`** | `+0x49760` |

(v83 has `68 58 02 00 00 A5 68 20 03 00 00`; v84 drops the `A5 movsd`, hence 5 bytes apart not 6.)
So same-offset interpolation is dead — as §1.3 already argued — but **function-scoped constant
search is alive**, and it is the tier that should be built first in phase 1.

## D.6 Answers to open unknowns, obtained in passing

- **U1 — closed.** Unpacked v84 code obtained, reproducibly, without a rebuilt PE.
- **U2 — closed.** 58.2% signature / 80.7% mechanical, above.
- **U5 — confirmed, and worse than stated.** `localhome.exe` is pre-modified beyond the three keys
  §7 lists: `0x009F242F` is already `EB` (ad balloon) in the v83 image while v84 memory holds the
  original `74`. Any site the owner statically patched yields a false miss. 5 sites are affected.
- **U7 — partly answered.** v84 `C_WVS_APP_RUN` (`0x00A3E7E8`) starts `E9 83 53 1B 7A`
  (`jmp 0x7ABF3B70`, into DLL space) — the `edits\` loader **already detours `CWvsApp::Run`** by the
  time the window is up. So §4.2's Themida-wait poll for a `0xB8` first byte would never fire on
  v84: **drop the poll**, the `edits\` loader demonstrably runs post-unpack.
- **Known limitation, not yet quantified.** The dump is of a *live* client with the `edits\` DLLs'
  patches installed, so a window overlapping one of their hooks cannot match. An attempt to count
  those hooks by scanning for `E9`+rel32 leaving the image produced **31,302 hits** — overwhelmingly
  random bytes, a useless instrument, discarded rather than reported. To quantify properly: copy the
  client directory (never modify the original), remove `edits\` from the copy, dump, and diff the two
  images. That also yields an exact list of the loader's patch sites. ~0.25 day, phase 1.

## D.7 Cost impact

| phase | was | now | why |
|---|---|---|---|
| 0 obtain unpacked v84 | 1–5 | **done** | memory read, no rebuild needed |
| 1 signature harvest | 2 | **1–1.5** | matcher, tiers and `v84-sites.json` schema already exist |
| 2 resolve the misses | 3–6 | **3–5** | 48 named sites, not ~40 estimated; but 5 clusters, one pattern each |
| 3–6 build / WZ / bring-up / sign-off | 9.5–13.5 | unchanged | untouched by this work |
| **total** | **13–22** | **13.5–20** | and the **~40-day branch is eliminated** |

The 48 unresolved are **48 sites, not 48 problems** — they cluster `[FACT-measured]`:

| cluster | sites | | cluster | sites |
|---|---|---|---|---|
| `CWvsApp` CreateMainWindow / ResMan / Gr2D (C, M) | 11 | | smega (J) | 2 |
| pop-up requests (I) — one pattern, 7 copies | 7 | | `CLogo` / `Getcanvas` (E) | 2 |
| `CLogin::SendCheckPasswordPacket` (E) | 4 | | buff-icon strip (C) | 2 |
| gain / pick-up messages (H) | 4 | | tooltip clamp (C) | 2 |
| status bar / quickslot (D) | 4 | | unclassified geometry (C) | 2 |
| skill / RelMove (C) | 4 | | login descriptor (E), camera VR (C), other | 4 |

Two of the 11 `CWvsApp` sites are already resolved by hand in D.5, and the 7 pop-up copies are one
pattern found once. §3 predicted the expensive clusters would be **F (cash shop, 10 caves)** and
**G (Dojo, 11 caves)**; both **located mechanically and appear nowhere in this list.**

**U3 is largely answered, and favourably.** §7 feared the 35 code caves would each become a bespoke
rewrite. Measured: **29 of 35 caves located (83%)**, marginally *better* than the `WriteInt`
sites (221/262 = 84%). The remaining risk in a cave is register state at the splice point, which
still needs eyes on the v84 disassembly — but finding them is no longer the problem. The genuinely
poor rates are on the tiny op classes that §3 already says to **drop**: `WriteString` 0/1,
`WriteByteArray` 0/2, `FillBytes` 1/4, `WriteDouble` 0/1 — those are the IP-string, elevation and
damage-cap sites (groups A/B and U5), not resolution work.

`v84-sites.json` (255 located rows, every one carrying `evidence` and `window`) is committed at
`docs/work-plan/tools/memdump/v84-sites.json` as the seed for phase 1's acceptance criterion.

## D.8 Rule compliance

- `D:\games\MapleStory\` and `D:\games\dreamms\` — **read-only**, nothing written.
- `D:\games\MSv84\client\` — launched twice, killed both times, **zero processes left**. File
  snapshot before/after: 59 files, all identical, **except** `ijl15.dll.bak`, whose mtime the
  client's own loader updates on every launch (length unchanged, 352,256 B — it had already been
  rewritten at 18:57 UTC today, before this work started). Nothing here wrote to that directory.
- Server on 8484/7575 never touched; no Java, no `wz/`, no `PacketCreator.java`.

## D.9 Reproduce

```
csc.exe /platform:x64 /out:MemDump.exe MemDump.cs
MemDump.exe dump --pid <client> --base 0x400000 --size 0xBC4000 --out v84_mem.bin   (elevated)
Compare-Dump.ps1 -File D:\games\MSv84\client\MapleStory.exe -Blob v84_mem.bin
python phase0.py   # site parsing, instrument proofs, transfer measurement, anchored pass
python pass3.py    # function-scoped constant tier -> v84-sites.json
```

`phase0.py` / `pass3.py` expect the Ezorsia clone and both dumps beside them; they are read-only
against `localhome.exe`.
