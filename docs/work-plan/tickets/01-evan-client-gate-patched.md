# 01 — Evan client gate patched and client boots

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

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

There is no client-side launcher indirection. `D:\games\MapleStory\launch.bat.lnk` points at
`Server\Cosmic\launch.bat`, which is the **server** (`java -jar target\Cosmic.jar`) — not the
client. `MapleStory.exe` is the stock updater and is not used. The client is started by running
`local.exe` / `localhome.exe` directly, so a patched copy is launched by double-clicking it. No
config change is needed to use the patched copy.

The two binaries differ in 208,356 bytes starting at `0x6FE085`: `local.exe` carries the string
`127.0.0.1`, `localhome.exe` carries `192.168.1.109` (the rest of the delta is Themida-encrypted
data that shifts with it). **`local.exe` is the one to test if the server runs on the same
machine.** The patch site `0x361714` is outside that differing range and is byte-identical in
both. Note `config.ini` also exposes `ServerIP_Address=127.0.0.1`, which `dinput8.dll` applies at
runtime — so the baked-in IP may well be overridden anyway.

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

**Both a static-patch failure and a fallback build are avoidable work until the human step below
says the static patch failed. Do not build `CUSTOM.dll` speculatively.**

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
