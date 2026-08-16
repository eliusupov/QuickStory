# Ticket 20 — v84 recon and instrument-proofing: results

Run date: 2026-08-16. All artefacts hash-verified. **The live v83 client at `D:\games\MapleStory\`
was never written to** — proven by a SHA256 baseline taken before the first write and re-checked at
the end (48/48 files identical).

## Gate: MET

**The v84 client reaches its own login screen, localhost-routed.** Verified by screenshot
(`MapleStoryClass` window, 806x629, login and password fields rendering) and independently
confirmed by the owner on screen. **Owner launches consumed: 0** — the login screen was reached
without spending the budgeted launch, because the handshake was answered by a local stub instead.

## What exists now

| Path | What |
|---|---|
| `D:\games\MSv84\client\` | v84 client, 52 files, bypass + routing installed |
| `D:\games\MSv84\bypass\` | `gms-83-dll` v2.1.2 releases, GMS-84.1 and GMS-83.1 |
| `D:\games\MSv84\opcodes\` | atlas v84 opcode table + v83/v95 for comparison |
| `tools/v84/setup-v84-client.ps1` | rebuilds the whole client from the installer, reproducibly |
| `tools/v84/FakeV84Login.ps1` | minimal v84 handshake responder (the login-screen instrument) |
| `tools/v84/ClientProbe.ps1`, `DumpWindows.ps1`, `CabScan.cs` | the proven instruments |

`setup-v84-client.ps1` was run end-to-end from scratch with `-Force`; every stage self-asserts and
it refuses to run if its output root is inside the live client.

## Installer archaeology

`GMSSetupv84.exe` (1,846,289,344 B, SHA256 `6F0D3C35…B0F5B51F`) is a Nexon `NGMSetup.exe` PE with
**two spanned MSZip cabinets appended**, not one:

- `MapleStory_1.cab` @ offset 6,553,734, 1,048,576,000 B, flags `0x0002` (NEXT), 29 files
- `MapleStory_2.cab` @ offset 1,055,129,734, 791,157,066 B, flags `0x0001` (PREV), 24 files

Offsets chain exactly; 2,544 trailing bytes are the Authenticode block. 7-Zip pointed at the `.exe`
sees **only the first cabinet and silently truncates `Mob.wz`** — extracting from the `.exe` directly
is a data-loss trap. Carve both volumes with their chain names and extract from volume 1.

Authenticode: signed by Nexon America, cert expired 2010 and since revoked. Expected for 2009-era
software; recorded, not treated as alarming.

## Hash results

- **`porting-resources\wz-data\v84\` is genuine: 17/17 `.wz` byte-identical** to the installer's own
  WZ. That evidence set is confirmed and can continue to be trusted.
- **Live v83 client vs `_backup\client-v83-EzorsiaV2-2026-08-15`: only 8/18 `.wz` match.** This
  divergence **pre-dates this ticket** and is not caused by it — the 10 differing files carry
  mtimes of 12:31, 13:39 and 15:38 on 2026-08-16, while the baseline snapshot was taken at 16:53
  and the first write of this ticket at 17:06. It is in-progress WZ-merge work by another session.
  **Consequence: "live == backup" is not a usable tamper-check invariant; it was already false.**
  The valid integrity instrument is a before/after pair taken within the session, which is what
  `setup-v84-client.ps1` does.

## Corrections to the project's standing facts

| Prior | Reality |
|---|---|
| gms-83-dll v84.1 has a **145-key** memory map | **159 keys.** 145 is stale, from the port's own task docs; the repo contains three conflicting counts (145 docs / 152 comment / 159 actual) |
| Bypass loads as a `dinput8.dll` proxy | **`ijl15.dll` proxy** plus an `edits\` folder. `dinput8.dll` is the *Ezorsia v2* lineage used by the live v83 client — a different project |
| v84 needs **zero** anti-cheat work | Overstated. `DR::check` (`>=87`) and `CeTracer::Run` (`>=95`) genuinely don't apply, but `CSecurityClient::OnPacket` and `SendHSLog` hooks still install for v84. Correct phrasing: **zero *additional* work beyond v83** |
| **No public GMS v84 opcode table exists** | **Wrong.** `Chronicle20/atlas` ships a 553-row IDB-derived v84 table (330 clientbound + 223 serverbound) |
| MapleShark archive skips v84 | **Confirmed** — 47 GMS versions, no 84 |

### The finding that matters most

**v84 opcodes diverge from v83 above `0x3E`.** The shift is monotonic but **non-uniform** —
cumulative +2 / +3 / +4 / +6 / +7 / +10 depending on range, because it is the running count of
opcodes inserted below a given value. **Of 549 shared opcodes, 336 differ.** Login and the
`0x00–0x3E` block are unchanged, which is exactly the trap: the handshake being byte-identical to
v83 tempts the conclusion that the protocol is identical. It is not.

Cosmic already has the seam for this: `OpcodeTable.java:47` reads `-Dopcode-version` (default 83).

## Handshake values (verified from bypass source, not assumed)

Version **84**, locale **8**, subversion string **"1"**. Decode order in `decode_handshake`:
`Decode2` version → `DecodeStr` patch → `Decode4` seqSnd → `Decode4` seqRcv → `Decode1` locale.
Cosmic's `PacketCreator.getHello` (`PacketCreator.java:600`) already emits exactly this shape;
only `ServerConstants.VERSION` (currently `83`) needs to change.

## Instruments: proven, and one rejected

| Instrument | How proven |
|---|---|
| `Get-FileHash` | single-flipped-byte detection, determinism over repeats, copy-identity — all pass. Run in the **foreground** throughout |
| `CabScan` | synthetic file with headers at known offsets, including one straddling the 32 MiB chunk boundary; a wrong-version decoy correctly rejected |
| Dialog/window reader | round-tripped a `MessageBox` with canary text, read back verbatim |
| Fake v84 server | bytes on the wire compared to the expected handshake, exact match |
| Connection watcher | detected a connection known to exist, reported zero after close |
| **PE dump-detector** | **REJECTED.** Flagged `notepad.exe` as suspect and could not separate the known-good from the known-bad binary. Discarded rather than trusted |

Three further broken instruments were caught *in this session*:

1. A byte-at-a-time PowerShell scan over 1.8 GB — abandoned after it blew a 600 s timeout; the
   compiled equivalent finished in 2 s.
2. A self-test that printed **PASS** off an errored `Compare-Object` returning an empty array that
   compared equal — it would have printed PASS no matter what. The scanner was fine; the *test* was
   the broken part.
3. `ClientProbe` reported `cleanup: killed 1` when the kill had actually failed with access denied
   (Themida-protected process); the swallowed error became a false success line.

**Calibration result worth carrying forward:** `notepad.exe` on Win11 is a stub that exits in 0.44 s
with code 0 while the real app runs on under a different PID. **Tracked-PID lifetime and exit code
are lying instruments** — precisely the "0-second lifetime" pattern that produced days of worthless
crash evidence here. Trust window presence, process count by name, and survivors instead.

Bonus correction: `local.exe`, `localhome.exe`, `local.evan.exe`, `localhome.evan.exe` are four
distinct files of **identical size and identical PE section layout** — i.e. byte-patched variants of
one build, not separately-produced dumps. Whatever made `localhome.evan.exe` unrunnable was its
patches, not its provenance.

## What remains unproven

- **Login never completed** — the stub answers the handshake and nothing further. Character
  selection, world list and in-game are all untested.
- The client's 143-byte encrypted reply was **not decrypted**; that it is a well-formed login packet
  is inferred from the client proceeding, not verified.
- **30 clientbound rows in the atlas table still carry `csv-import` provenance at opcode ≥ 0x3F** and
  are stale-v83 suspects. One is already proven wrong: `SERVERMESSAGE` sits at `0x44` but the IDB
  evidence says `0x46`, and `0x46` is vacant.
- Not verified that the local packed `MapleStory.exe` is byte-identical to the `GMS_v84.1_U_DEVM.exe`
  atlas analysed. Address ranges are consistent, which is suggestive, not proof.
- `HShield\` was extracted but never installed or exercised. `Setup.exe`/`HSInst.dll` were
  deliberately not run — they install system-wide.
- The client is Themida-packed and **resists `Stop-Process`** (access denied). Cleanup is not
  guaranteed; check for stray `MapleStory.exe` after testing.

## Next step

`ServerConstants.VERSION = 83` → `84` is the one-line change that lets the real Cosmic server drive
this client to its login screen. It was **not** made here — the owner's v83 server was running on
8484 throughout and was left untouched; the whole login-screen proof was done on port 8485 against a
stub instead.
