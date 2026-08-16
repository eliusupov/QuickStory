# 01 — Evan client gate patched and client boots

**Blocked by:** None — can start immediately

**Status:** static patch failed (`local.exe` is a memory dump, not the client) — superseded by
**`## 01b — runtime patch, staged`** at the bottom of this file, which is built, self-tested, and
waiting on one human run.

## What to build

A copy of the localhost binary has the Evan job-check NOPed out, and it still launches and reaches the login screen.

The v83 client contains Nexon's Evan support already compiled in, disabled behind a job check in `CSkillInfo::GetSkill`. Removing that check is what makes the class possible at all. This ticket proves the binary survives the edit — it does not need Evan to work yet.

**This is the project's go/no-go and it costs about an hour. Do it before anything else.** If the binary refuses to run after patching, tickets 10–15 need a different approach (runtime patching through the existing `dinput8.dll` hook layer) and you want to know that before investing a week in WZ work.

Verified facts to work from: the 21-byte pattern `83 F8 16 0F 84 D7 00 00 00 81 FE D1 07 00 00 0F 84 CB 00 00 00` occurs **exactly once** in both `local.exe` and `localhome.exe`, at file offset `0x361714`. Because the match is unique you can search for it rather than seek to an address. Replace with 21 bytes of `90`.

Note both binaries are Themida-marked and have different SHA256 (different baked-in IPs), so they are patched separately and either may behave differently.

## Acceptance criteria

- [ ] Patched copy of `local.exe` launches and reaches the login screen
- [ ] Patched copy of `localhome.exe` does the same
- [ ] A character can log in and play normally — no regression for existing classes
- [x] If either binary fails to launch, the runtime-patch fallback is assessed and the finding recorded in the ticket
- [x] Original unpatched binaries remain untouched in the backup

---

## Findings

Investigated 2026-08-15. Everything below was computed from the real files, not taken from the
prior audit. Scripts used: Python 3.12, byte-level; no hex editor involved.

### Backup verification — PASS

Both originals exist in the backup and are byte-identical to the live client:

| File | Size | SHA256 |
|---|---|---|
| `D:\games\MapleStory\local.exe` | 9,920,523 | `7FA7E956D45119EB5910B67CFD4ECE16D7E7C8EF40E176502CF791EC1E0880FE` |
| `_backup\client-v83-EzorsiaV2-2026-08-15\local.exe` | 9,920,523 | *(identical)* |
| `D:\games\MapleStory\localhome.exe` | 9,920,523 | `CE05CC3E3CC112B225EC071C4B82C0C99C82004A4B594507E5FCCB54B58AD594` |
| `_backup\client-v83-EzorsiaV2-2026-08-15\localhome.exe` | 9,920,523 | *(identical)* |

### Pattern verification — ticket confirmed exactly

Searched the full 21-byte pattern, all occurrences, in each binary independently:

| Binary | Occurrences | Offset(s) | Ticket says `0x361714` |
|---|---|---|---|
| `local.exe` | **1** | `0x361714` | **match** |
| `localhome.exe` | **1** | `0x361714` | **match** |

Bytes present at that offset in both: `83 F8 16 0F 84 D7 00 00 00 81 FE D1 07 00 00 0F 84 CB 00 00 00`.

### Patched copies produced

Written **alongside** the originals so they inherit the same `dinput8.dll`, WZ files and
`config.ini` (Windows loads the hook DLL from the application directory, so the patched copy gets
the full Ezorsia HD mod layer with no extra setup). The originals were re-hashed after the write
and are unchanged.

| File | Size | SHA256 |
|---|---|---|
| `D:\games\MapleStory\local.evan.exe` | 9,920,523 | `43D7862754CC6183616AE3076E08E720B1EBF34A1945C48218E1F0D81DB1B800` |
| `D:\games\MapleStory\localhome.evan.exe` | 9,920,523 | `101D584CBD8A57955B678F1F9052D410DE31EFEFF0A762AB3B14C95211ADC602` |

Diff verification, per file, against its own original:

- bytes changed: **21**
- range: `0x361714`–`0x361728`, contiguous, no other byte in the file differs
- every changed byte is `0x90`
- file size unchanged

### Launcher arrangement

> **CORRECTED 2026-08-16 (ticket 01b).** The two claims below struck through were wrong and they
> misled this project into a dead end. See "Correction" immediately after.

There is no client-side launcher indirection. `D:\games\MapleStory\launch.bat.lnk` points at
`Server\Cosmic\launch.bat`, which is the **server** (`java -jar target\Cosmic.jar`) — not the
client. ~~`MapleStory.exe` is the stock updater and is not used.~~ ~~The client is started by
running `local.exe` / `localhome.exe` directly, so a patched copy is launched by double-clicking
it.~~ No config change is needed to use the patched copy.

The two binaries differ in 208,356 bytes starting at `0x6FE085`: `local.exe` carries the string
`127.0.0.1`, `localhome.exe` carries `192.168.1.109` (the rest of the delta is Themida-encrypted
data that shifts with it). **`local.exe` is the one to test if the server runs on the same
machine.** The patch site `0x361714` is outside that differing range and is byte-identical in
both. Note `config.ini` also exposes `ServerIP_Address=127.0.0.1`, which `dinput8.dll` applies at
runtime — so the baked-in IP may well be overridden anyway.

### Correction — `MapleStory.exe` is the client, and it is the only one that runs

The owner confirms `MapleStory.exe` is the executable they launch, and it works. `local.evan.exe`
does nothing when launched, with the server confirmed up (java listening on 8484/7575) and no
crash event in the Windows Application log.

`local.exe` and `localhome.exe` are not sibling builds of the client — they are **memory dumps of
a running `MapleStory.exe`**, saved back out as PEs. The PE headers say so unambiguously:

| | `MapleStory.exe` | `local.exe` |
|---|---|---|
| TimeDateStamp | `4B7C15C9` | `4B7C15C9` — same |
| Checksum | `004213DC` | `004213DC` — same |
| Export dir | rva `00A8E774` size `8F` | identical |
| Resource dir | rva `007F9000` size `1F4D0` | identical |
| ImageBase / DllCharacteristics | `0x400000` / `0x0000` (no ASLR) | identical |
| section 1 VA / VSize | `0x1000` / `0x7F8000` | identical |
| section 1 **RawSize** | `0x2DD000` (compressed) | `0x7F8000` (**== VSize**) |
| EntryPoint | `00A90000` — Themida stub, in section `albasygk` | `00663FF3` — the real OEP, inside section 1 |
| Import dir | `00819043` in `.idata`, naming **one** DLL: `kernel32.dll` | `00A92000` in an appended `.mackt` section, naming **17** DLLs |

Raw size == virtual size on every section, file offset == RVA, entry point relocated to the OEP,
and an import table rebuilt into a tacked-on section are the signature of an ImpREC/Scylla-style
process dump. That is why the gate pattern is findable in `local.exe` and absent from
`MapleStory.exe`: **`MapleStory.exe` is Themida-compressed and its code only exists after
unpacking, at runtime. There is nothing to patch on disk.** Static patching is dead.

The dumps stay useful as a *map* of the client's runtime memory — that is how ticket 01b
establishes the gate's runtime address, and how it proved `dinput8.dll` is loaded (see below).

`local.evan.exe` and `localhome.evan.exe` are now **unused artifacts**. Recommendation: **keep
them** until a human has confirmed 01b's runtime patch actually works — they are the only
existing evidence of what the patched byte sequence looks like, they cost 20 MB, and deleting
them before the replacement is verified removes a fallback for no gain. Delete both once
acceptance criteria 1–3 pass by the 01b route. `local.exe` / `localhome.exe` must be kept
permanently; they are the address map.

### Runtime-patch fallback via `dinput8.dll` — assessed, buildable, cheap

**The static patch is very likely to be the only thing needed, and if it isn't, the fallback is
small.** Evidence:

*The client image is trivially addressable.* `local.exe` PE header: `ImageBase 0x00400000`,
`DllCharacteristics 0x0000` — **ASLR is off**. Section 1 spans RVA `0x1000`–`0x7F9000` with raw
pointer `0x1000` and identical raw/virtual size, so **file offset == RVA**. Therefore:

```
file offset 0x361714  ->  RVA 0x361714  ->  VA 0x00761714   (fixed, every launch, no rebase math)
```

That section's characteristics are `0xE0000040` = READ | WRITE | EXECUTE. The target page is
**already writable** — a runtime patch does not strictly need `VirtualProtect`, though any
implementation should still call it defensively and follow with `FlushInstructionCache`.

*The hook layer is already a fixed-address memory patcher.* `dinput8.dll` (2,840,064 B, SHA256
`1C0835181B529CFD7CEA720688B294E6AE0CCE83D72EBACBDA003AEB7262A61A`) is a Detours-based proxy DLL
(`.detourc` / `.detourd` sections) exporting `DirectInput8Create` and `GetdfDIJoystick`. Its
`.text` contains **1,536 references to 774 distinct addresses inside the client's image range**,
including `0x00760EE8` — 0x82C bytes from our gate, i.e. it already writes to the same code
region. It imports exactly the toolkit required: `VirtualProtect`, `VirtualAlloc`, `VirtualQuery`,
`FlushInstructionCache`, `OpenThread`, `SuspendThread` / `ResumeThread`,
`GetThreadContext` / `SetThreadContext`, `LoadLibraryA`, `GetProcAddress`, plus `fopen_s`/`fgets`
for `config.ini`. It is confirmed to implement the `config.ini` keys — `ServerIP_Address`,
`RemoveLogos`, `setDamageCap`, `speedMovementCap`, `sleepTime`, `use_custom_dll_1..3` all appear
as literals in `.rdata`.

*There is a supported extension point — no reverse-engineering of `dinput8.dll` required.*
`config.ini` lines 37–41 expose:

```
use_custom_dll_1=CUSTOM.dll
use_custom_dll_2=CUSTOM2.dll
use_custom_dll_3=CUSTOM3.dll
```

and line 44 comments *"sleeps before loading custom dlls because they may not have themida bypass
techniques built in"* — i.e. `dinput8.dll` already defers custom-DLL loading until after Themida
finishes unpacking, and `sleepTime` (default 0, recommended range 30–160) is the knob for tuning
that wait.

**So the fallback is: a ~30-line `CUSTOM.dll`.** Its `DllMain` on `DLL_PROCESS_ATTACH` does
`VirtualProtect(0x00761714, 21, PAGE_EXECUTE_READWRITE, &old)`, `memset(0x00761714, 0x90, 21)`,
restore protection, `FlushInstructionCache`. Point `use_custom_dll_1` at it, launch the
**unmodified** `local.exe`. Because the DLL is loaded after unpack, any Themida self-CRC has
already run and passed against a clean on-disk image.

Cost estimate: **2–4 hours**, mostly toolchain (32-bit MSVC build, `/MT`) and tuning `sleepTime`
until the write lands after unpack. Risks, in order: (a) Themida may re-verify or re-encrypt that
region *after* custom DLLs load — mitigate by reading back the 21 bytes after writing and
retrying on a timer thread; (b) if the write lands too early it is overwritten by the unpacker —
raise `sleepTime`; (c) `dinput8.dll` may load custom DLLs before its own patches, ordering
unknown — irrelevant here since the addresses do not overlap.

~~**Both a static-patch failure and a fallback build are avoidable work until the human step below
says the static patch failed. Do not build `CUSTOM.dll` speculatively.**~~
**Superseded.** The static patch is dead (see Correction above), so this is no longer a fallback —
it is the only route. It is executed by **01b** below, which also corrects two details in the
paragraphs above: `sleepTime` does **not** defer loading until after unpack (it is a flat
`Sleep(ms)` with no unpack awareness, default 0 = no wait at all), and the DLL cannot be named
`CUSTOM.dll` (that literal is the sentinel meaning "disabled").

### What was NOT done

Acceptance criteria 1–3 require launching the game, and that cannot be done from this
environment. Nothing was launched, no login screen was reached, no character was logged in. Those
three boxes stay unticked until a human runs the steps below.

---

## Human steps — staged, not performed

Server does not need to be running for step 1 (the login screen appears regardless; a dead server
just means login fails afterwards). For step 4 the server must be up.

**1. Test the primary binary.**
Double-click `D:\games\MapleStory\local.evan.exe`.

- **PASS** — the window opens (1280x720 windowed, logos skipped per `config.ini`) and you reach
  the MapleStory login screen with the ID/password fields. Themida accepted the patch. Tick
  acceptance criterion 1.
- **FAIL, Themida CRC** — a dialog appears, usually titled `Themida` or with no title, reading
  something like *"This application has been modified"*, *"A debugger has been found running"*,
  or *"Protected application file is corrupted"*. Themida sometimes shows no dialog at all and
  the process simply exits after 1–3 seconds with nothing on screen — that is the same failure.
- **FAIL, crash** — Windows "local.evan.exe has stopped working", or a `0xC0000005` /
  `0xC0000409` exit. Also counts as failure.
- **Ambiguous** — a black window that never becomes the login screen: wait 60 s, then treat as
  failure.

If it fails, check Event Viewer → Windows Logs → Application for the Application Error entry and
record the faulting module and exception code — that distinguishes a Themida CRC abort (clean
`ExitProcess`, often no event) from a real crash.

**2. Test the second binary.**
Double-click `D:\games\MapleStory\localhome.evan.exe`. Same pass/fail criteria. This one connects
to `192.168.1.109`, so on a single-machine setup it may reach the login screen and then fail to
log in — reaching the login screen is all criterion 2 asks for.

**3. Sanity-check the originals still work.**
Run `D:\games\MapleStory\local.exe`. It must behave exactly as it did before. If it does not,
something outside this ticket changed — restore from
`_backup\client-v83-EzorsiaV2-2026-08-15\local.exe`.

**4. Regression check (criterion 3).**
With Cosmic running (`Server\Cosmic\launch.bat`), log in through `local.evan.exe` with an existing
character. Move, attack, change map, open the skill window. Nothing should differ from the
unpatched client — the NOPed branch is only reached for job 2001 / job-group 22, neither of which
any current character has.

**Rollback — nothing to undo.**
The originals were never modified; they are still in place and still hash-match the backup. To
revert entirely, delete `D:\games\MapleStory\local.evan.exe` and
`D:\games\MapleStory\localhome.evan.exe`. Nothing else on disk was touched.

**If step 1 or 2 fails**, do not retry with a different hex edit — the edit is provably correct
(21 bytes, right offset, all `0x90`). Go straight to the `CUSTOM.dll` fallback described under
Findings, and record which failure mode you saw.

> **Steps 1–4 above are obsolete as of 2026-08-16.** They were run and step 1 failed:
> `local.evan.exe` does nothing when launched. `local.exe` is a memory dump, not a runnable
> client — the client is `MapleStory.exe`. Do not repeat them. **Use `## 01b` below instead.**

---

## 01b — runtime patch, staged

**Status:** built and self-tested; requires one human run to confirm. Nothing in
`D:\games\MapleStory\` was modified.

### What changed vs the plan

Ticket 01b was specified as a `CUSTOM.dll` loaded through `dinput8.dll`. **No C toolchain exists
on this machine** (see "Toolchain" below), so the DLL could not be built, and installing one
unprompted was out of scope.

It is not needed. The same 21 bytes can be written from **outside** the process with
`OpenProcess` + `WriteProcessMemory`, which needs no compiler, **no change to `config.ini`, and no
new file in the game directory at all**. Rollback is "close the game". That is strictly less
invasive than the DLL, so it is the primary route. The DLL source is written and staged as the
fallback for the one risk the external route has: Themida or an elevation mismatch refusing the
process handle.

| | external patcher (primary) | `CUSTOM.dll` (fallback) |
|---|---|---|
| toolchain | none — PowerShell 5.1 | 32-bit MSVC or mingw-w64 (**not installed**) |
| files added to `D:\games\MapleStory\` | **none** | `EVANGATE.dll` |
| `config.ini` change | **none** | one line |
| rollback | close the game | delete DLL, restore `config.ini` |
| fails if | `OpenProcess` denied (Themida / elevation) | nothing known |

### Where the address comes from

The load-bearing assumption — that the gate sits at VA `0x00761714` in `MapleStory.exe` — rests on
`local.exe` being a **dump of that same image** (proof in the Correction section above: identical
TimeDateStamp, checksum, export/resource/security directories, ImageBase, and section-1 virtual
layout; raw==virtual sizes and a rebuilt import table mark it as a dump).

In the dump, file offset == RVA, so:

```
local.exe file offset 0x361714  ->  RVA 0x361714  ->  VA 0x400000 + 0x361714 = 0x00761714
```

ASLR is off in both (`DllCharacteristics 0x0000`, `ImageBase 0x400000`), so that VA is fixed every
launch. Section 1's characteristics are `0xE0000040` — the page is already RWX.

Corroboration that the address is *meaningful* and not a coincidental byte match — the 21 bytes
decode as an Evan job-ID test, with 8 bytes of context before:

```
8B 4D 0C           mov  ecx, [ebp+0Ch]        ; skill id
8B C1 99           mov  eax, ecx / cdq
BE 10 27 00 00     mov  esi, 10000
F7 FE              idiv esi                   ; esi = skillid / 10000  = job id
6A 64 5F           push 100 / pop edi
8B F0 99 F7 FF     mov esi,eax / cdq / idiv edi ; eax = jobid / 100    = job group
--- gate starts at 0x00761714 ---
83 F8 16           cmp  eax, 22               ; job group 22 == Evan
0F 84 D7 00 00 00  je   ...
81 FE D1 07 00 00  cmp  esi, 2001             ; job 2001 == Evan beginner
0F 84 CB 00 00 00  je   ...
--- gate ends (21 bytes) ---
81 F9 F3 30 31 01  cmp  ecx, 20001011         ; falls through into the generic skill chain
```

That is `CSkillInfo::GetSkill`'s Evan special-case, exactly as ticket 01 described. It occurs
**once** in the image (the patcher's `-SelfTest` re-proves uniqueness on every run).

**This is still an inference until a human runs it.** `-DryRun` settles it empirically: it reads
the live process and reports whether those 21 bytes are actually there, without writing anything.
**Run `-DryRun` first.**

### How `dinput8.dll` was confirmed loaded

`MapleStory.exe`'s import table is packed — its on-disk `.idata` names only `kernel32.dll`, the
Themida stub's own import. Two independent proofs, both from files, neither requiring a launch:

1. **The dump's rebuilt import table lists it.** `local.exe`'s import directory (`0x00A92000`)
   was reconstructed from the *live process's* IAT and contains 17 DLLs. Entry **[1] is
   `dinput8.dll`**, IAT slot VA `0x006F0024` — alongside `ijl15.dll`, `mss32.dll`, `nmcogame.dll`.
   The client statically imports `dinput8.dll`; the real table only materialises after unpack,
   which is what the dump captured.
2. **`dinput8.dll` owns the entire `config.ini` contract.** The literals `config.ini`,
   `ServerIP_Address`, `WindowedMode`, `RemoveLogos`, `setDamageCap`, `MsgAmount`, `sleepTime`,
   `use_custom_dll_1..3` are all in `dinput8.dll`'s `.rdata` and appear in **neither**
   `MapleStory.exe` nor the unpacked `local.exe` dump. The owner's client honours
   `ServerIP_Address=127.0.0.1` and connects to the local server — only `dinput8.dll` can be doing
   that, so it is in the process.

A third, human-verifiable check is in the steps below (`tasklist /m dinput8.dll`).

### Corrections to the custom-DLL mechanism

Disassembly of `dinput8.dll` `0x10008960`–`0x10008C20` (its config-read + custom-DLL loader):

- **`sleepTime` is a flat `Sleep(ms)`, not an unpack-aware wait.** `0x10008B8D`:
  `mov eax,[ebp-18h]; test eax,eax; jz skip; push eax; call Sleep`. It has no idea when Themida
  finishes. Ticket 01's reading of the comment was too generous. **Default is 0 — no wait at
  all.** This is exactly why the patch must poll rather than fire once.
- **The DLL must NOT be called `CUSTOM.dll`.** `0x10008BAF` loads `"CUSTOM.dll"` into `ecx` and
  runs an inline `strcmp` against the config value; `0x10008BE7 je` **skips the load when they are
  equal**. `CUSTOM.dll` is the sentinel for "disabled", which is why the shipped default disables
  it. The staged DLL is therefore named `EVANGATE.dll`.
- **A bad DLL name fails safe.** If `LoadLibraryA` returns NULL, `dinput8.dll` shows a MessageBox
  *"Failed to find the first custom dll file"* / *"Missing file"* and calls `ExitProcess`. Wrong
  name or wrong bitness = clean exit with a dialog, no corruption.

### Toolchain — what is missing

Searched `C:\` and `D:\` to depth 6 plus every standard install root. Found **no C/C++ compiler**:
no `cl.exe`, no `gcc.exe`, no `i686-w64-mingw32-gcc.exe`, no `clang.exe`, no `tcc.exe`; no
`C:\Program Files\Microsoft Visual Studio`, no `...(x86)\Microsoft Visual Studio`, no Windows SDK
`bin`, no msys2/mingw/LLVM/TDM-GCC. Scoop is installed but holds only 7zip, godot, pandoc,
tesseract. (`C:\Program Files\Git\usr\bin\link.exe` is the Unix `link` utility, not MSVC's linker.)
Present but insufficient: .NET SDK 10.0.400 / 9.0.317 and .NET Framework `csc.exe` — managed only;
NativeAOT would still need MSVC's `link.exe`.

**To build the fallback DLL, install one of:**

- **Visual Studio Build Tools 2022** with workload *Desktop development with C++* and the
  **MSVC v143 x86** component — `winget install Microsoft.VisualStudio.2022.BuildTools`, then in
  the installer tick that workload. Build from the *x86 Native Tools Command Prompt*.
- **or** mingw-w64 i686 — `scoop install mingw-winlibs` (or the `i686-...-dwarf` MSYS2 package).

### Files

| Path | What |
|---|---|
| `tools\patch-evan-gate.ps1` | primary. External runtime patcher. Poll → guard → write → verify → retry. |
| `tools\evan-gate-dll\evan-gate.c` | fallback. `EVANGATE.dll` source. **Not built** — no compiler. |
| `tools\evan-gate-dll\config.ini.pre-01b` | byte-exact copy of the live `config.ini` taken before this ticket. |
| `tools\evan-gate-patch.log` | written by the patcher, next to it. |
| `D:\games\MapleStory\evan-gate-dll.log` | written by the DLL, next to it, if the fallback is used. |

Nothing in `D:\games\MapleStory\` was created, modified, or deleted by this ticket.

### Guard / verify / retry behaviour

Identical in both implementations:

1. Poll every 250 ms (patcher: 180 s budget; DLL: 60 s) — this replaces `sleepTime` tuning, since
   it simply waits for Themida to finish rather than guessing how long that takes.
2. Skip while the address is unreadable (patcher: `ReadProcessMemory` fails; DLL: `VirtualQuery`
   says not `MEM_COMMIT`, or `PAGE_NOACCESS`/`PAGE_GUARD`) — never fault on a page Themida has not
   decompressed.
3. **GUARD — write only when all 21 bytes equal `83 F8 16 0F 84 D7 00 00 00 81 FE D1 07 00 00 0F
   84 CB 00 00 00`.** Anything else is logged verbatim as hex and **not written**. Already
   `90`×21 → report "already patched" and stop.
4. `VirtualProtect(→ PAGE_EXECUTE_READWRITE)`, write, restore old protection,
   `FlushInstructionCache`.
5. **Read back.** Match → log `PATCHED and verified` and stop. Mismatch → log the actual bytes and
   keep polling; that is the Themida re-encrypt / re-verify case.
6. Timeout → log `gave up` plus the last bytes seen. Never silent.

`-SelfTest` (run, passes) checks the constants against `local.exe` on disk: pattern matches at
`0x361714`, `VA == offset + ImageBase`, in-length == out-length, and the pattern is unique in the
9.9 MB image. It catches a mistyped constant without the game running.

### `config.ini` — not changed

| | SHA-256 | size |
|---|---|---|
| live, before **and after** this ticket | `D11BFCE137DDF8F6E2D516FC8A0AEE19BAAD7F50CAA3922E097EB500B4BEC34E` | 1859 |
| byte-exact backup at `tools\evan-gate-dll\config.ini.pre-01b` | `D11BFCE137DDF8F6E2D516FC8A0AEE19BAAD7F50CAA3922E097EB500B4BEC34E` | 1859 |
| *candidate* if the DLL fallback is used (`use_custom_dll_1=EVANGATE.dll`) | `3484A9BFBAD8E58B4DDDB72ECA7B8420C79CC9CB7732DA79C2CE5A12159B68A2` | 1861 |

Restore command (only needed if you take the fallback route):

```
copy /Y "D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\tools\evan-gate-dll\config.ini.pre-01b" "D:\games\MapleStory\config.ini"
```

Note: live `config.ini` differs from `_backup\client-v83-EzorsiaV2-2026-08-15\config.ini` by one
line — `ServerIP_Address` is `127.0.0.1` live vs `25.36.29.46` in the backup. That is the owner's
own change and is correct for a local server. **Do not restore `config.ini` from
`_backup\client-v83-EzorsiaV2-2026-08-15\`** — it would point the client at a remote IP. Use
`config.ini.pre-01b` above.

### Client integrity

All 44 backed-up files, including all 18 `.wz`, SHA-256-match
`D:\games\MapleStory\Server\_backup\client-v83-EzorsiaV2-2026-08-15\` — verified at the start and
at the end of this ticket, unchanged. The only file differing from that backup is `config.ini`,
for the `ServerIP_Address` reason above, and 01b did not touch it.

---

## Human steps — 01b, staged, not performed

Server up: `D:\games\MapleStory\Server\Cosmic\launch.bat` (java listening on 8484/7575).

**1. Dry run — settle the address before writing anything.**

Open PowerShell in `D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade`:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-evan-gate.ps1 -DryRun
```

It waits up to 120 s for `MapleStory.exe`. **Now launch `MapleStory.exe` normally.** Read
`tools\evan-gate-patch.log`:

- `GUARD PASS` then `dry run - address confirmed` → the gate is at `0x00761714`. Go to step 2.
- `ABORT: OpenProcess failed, win32 error 5` → access denied. Re-run the same command from an
  **Administrator** PowerShell. Still 5 → Themida is blocking handle access; go to step 5.
- `gave up after 180s` with `read:` lines showing bytes that are neither the pattern nor `90`×21 →
  the address is wrong or the region stays encrypted. **Stop. Do not write.** Paste the logged
  bytes into this ticket; that is the real finding.
- `not readable yet` for the whole window → same, stop and report.

**2. Patch.** Close the client, then:

```
powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-evan-gate.ps1
```

Launch `MapleStory.exe`. Expect `GUARD PASS` then `RESULT: PATCHED and verified`. The client
should be at the login screen, behaving normally.

Timing note: this must run **every launch** — it patches memory, not disk. If it ever lands too
early or too late, that is already handled: it polls for 180 s and only writes on an exact match.
Raise `-Timeout` if the client takes longer than that to unpack.

**3. Regression check (ticket 01 criterion 3).** Log in with an existing character. Move, attack,
change map, open the skill window. Nothing should differ — the NOPed branch is only reached for
job group 22 / job 2001, which no current character has.

**4. Confirm `dinput8.dll` is in the process** (closes the last inferred loop). With the client
running:

```
tasklist /m dinput8.dll
```

`MapleStory.exe` must be listed.

**5. Only if step 1 gave `OpenProcess failed` twice — the DLL fallback.**

Requires installing a 32-bit C toolchain first (see "Toolchain" above). Then:

```
cd tools\evan-gate-dll
cl /nologo /MT /O2 /LD /Fe:EVANGATE.dll evan-gate.c kernel32.lib
copy EVANGATE.dll "D:\games\MapleStory\EVANGATE.dll"
```

Edit `D:\games\MapleStory\config.ini` line 39, exactly this one line:

```
-  use_custom_dll_1=CUSTOM.dll
+  use_custom_dll_1=EVANGATE.dll
```

Resulting file must hash `3484A9BF…159B68A2` (1861 bytes). Do not rename the DLL to `CUSTOM.dll` —
`dinput8.dll` treats that literal as "disabled" and will not load it.

Launch `MapleStory.exe` and read `D:\games\MapleStory\evan-gate-dll.log`:

- `RESULT: PATCHED and verified` → done.
- `gave up after 60s` with `read:` lines → the DLL loaded before Themida decrypted the region and
  60 s was not enough. Set `sleepTime=160` in `config.ini` `[debug]` and retry; that delays the
  load itself. (`sleepTime` is a plain `Sleep(ms)` before `LoadLibraryA` — it does not detect
  unpack, it just waits.)
- MessageBox *"Failed to find the first custom dll file"* then the game exits → the DLL is not at
  `D:\games\MapleStory\EVANGATE.dll`, is 64-bit, or the config line is misspelled. Harmless; fix
  and retry.
- No log file at all → `dinput8.dll` never loaded it. Check the config line.

**Rollback.**

- Primary route: **nothing to roll back.** Close the game; the patch lives only in memory and no
  file on disk was touched.
- Fallback route, in this order:
  1. `del "D:\games\MapleStory\EVANGATE.dll"`
  2. `copy /Y "…\tools\evan-gate-dll\config.ini.pre-01b" "D:\games\MapleStory\config.ini"`
  3. Optionally `del "D:\games\MapleStory\evan-gate-dll.log"`

  Nothing else. `MapleStory.exe` and the `.wz` files were never modified.

### What was NOT done

The game was never launched, so no in-game result is claimed. `MapleStory.exe` was not modified.
`config.ini` was not modified. No file in `D:\games\MapleStory\` was created, changed, or deleted.
The fallback DLL was **not compiled** — no toolchain exists on this machine. Whether the write
takes, and whether Themida permits an external `OpenProcess`, are both unknown until step 1 runs.
