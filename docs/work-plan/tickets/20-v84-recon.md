# 20 — Recon and instrument-proofing

**What to build:** a working v84 client on this machine that boots to **its own login screen**,
routed to localhost, with every input artefact hash-verified. Nothing downstream can be trusted
until the instruments are proven.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

**Why "instrument-proofing" is in the title:** this project has drawn wrong conclusions **three
times in one day** from broken instruments — a client binary that could not run (`localhome.evan.exe`,
an ImpREC memory dump that self-relaunched forever), a debug trap that caused the crash it was
meant to catch (wrote `esp` into a read-only code page), and a merge tool that silently punched a
five-index hole in a positional array. **Prove the instrument before trusting the measurement.**

## Scope

- Extract/install the v84 client from `Server\porting-resources\clients\GMSSetupv84.exe` (1,760.8 MB)
  into an isolated directory. **Never touch `D:\games\MapleStory\` — the v83 client stays the fallback.**
- Re-hash `porting-resources\wz-data\v84\` against the installer's own WZ. It is treated as
  hash-verified evidence elsewhere; confirm that still holds.
- Localhost routing via `Chronicle20/gms-83-dll`, which ships **GMS v84.1 as a first-class,
  CI-built, released target** (`release-GMS-84.1.zip`, v2.1.2) with a 145-key memory map. `[FACT-sourced]`
- Cross-validate the v84 opcode table against a **second independent source**
  (Riremito `GMS_v84_*.properties`). **No public GMS v84 opcode table exists** `[NOT-FOUND]` — the
  community MapleShark archive has v83/85/86/88 and skips 84 — so expect derivation, not lookup.

## Facts that de-risk this `[FACT-sourced]`

- **v84 needs zero new anti-cheat work.** In `gms-83-dll`'s `bypass/security_hooks.cpp`,
  `DR::check` is gated `BUILD_MAJOR_VERSION >= 87` and `CeTracer::Run` `>= 95`. v84 is below both.
- **Themida is not new** — the v83 client is already Themida-packed.
- **Localhost routing is version-independent**: `redirect/redirect.ini` maps Nexon's
  `63.251.217.2/.3/.4` → `127.0.0.1:8484` via a Winsock `WSPPROC_TABLE` hook, not byte-patched IPs.
- `VERSION_HEADER=8`, `PLAYER_LOGGED_IN=0x14`, `CLIENT_START_ERROR=0x19` verified **identical to v83**.

## Acceptance criteria

- [ ] v84 client installed in an isolated directory; the v83 client and its backup are byte-identical afterwards
- [ ] Extracted v84 WZ hash-matches `porting-resources\wz-data\v84\`, or the difference is explained
- [ ] Client launches and reaches **its own login screen** with no server running
- [ ] Localhost routing verified — connection attempts land on `127.0.0.1:8484`
- [ ] Anti-cheat/bypass configuration documented and reproducible from a script, not by hand
- [ ] A v84 opcode table exists from at least one source, with its provenance recorded
- [ ] Every tool used is self-tested first; record what was proven and how

## Verification gate

**v84 client reaches its own login screen offline, localhost-routed.** Owner launch: **1**.

## Rollback

Nothing to undo — the v84 client is a separate directory; the live v83 client is untouched.

**Size:** 2–4 days.
