# Ticket 36 — SHOW_STATUS_INFO and SERVERMESSAGE mode enums at v84

**Branch** `worktree-evan-dualblade` · **Status** both claims verified against the client binaries, fix landed

Ticket 32 §6.1 recorded two suspected v84 enum shifts and deliberately left them unfixed. Both are
now **confirmed by direct disassembly of the two client images**, not by atlas. atlas was used only
to name the functions; every number below was read out of a jump table.

## 0. Method

Both images are based at `0x400000` with **file offset == VA − 0x400000**:

- **v83** `D:\games\MapleStory\localhome.exe` (read-only)
- **v84** `v84_mem.bin`, a `ReadProcessMemory` dump of the live client (session scratchpad).
  Every result below reproduces in the second independent dump `v84_mem2.bin`.

Anchor for both packets: `CWvsContext::OnPacket`, the clientbound dispatcher.

| | v83 | v84 |
|---|---|---|
| `CWvsContext::OnPacket` | `0xA07A08` | `0xA51CD0` |
| dispatch | `add eax,-0x1D` / `cmp eax,0x5F` / `jmp [eax*4+0xA07E8E]` (96 arms, ops 0x1D..0x7C) | `add eax,-0x1D` / `cmp eax,0x62` / `jmp [eax*4+0xA52170]` (99 arms, ops 0x1D..0x7F) |

Reading those two tables gives the handler for any opcode in range at either version, which is what
located both functions and, as a free by-product, checked two opcodes (§3).

## 1. `SHOW_STATUS_INFO` — `CWvsContext::OnMessage`, +1 from mode 4 — **CONFIRMED**

Dispatcher case **0x27** at both versions → `OnMessage`.

| | v83 | v84 |
|---|---|---|
| function | `0xA209D4` | `0xA6BDD9` |
| switch | `cmp eax,0xD` / `jmp [eax*4+0xA20A88]` | `cmp eax,0xE` / `jmp [eax*4+0xA6BE9A]` |
| arms | **14** (modes 0..13) | **15** (modes 0..14) |

### The inserted arm

v84 mode 4 → `0xA6CEFA`, a function with **no v83 counterpart**. Its body:

```
00a6cf0a  call 0x425200          ; Decode2  -> si   (job id)
00a6cf17  call 0x4066c9          ; Decode1  -> edi  (amount)
00a6cf25  push 0x64 / cdq / idiv ecx
00a6cf2f  cmp eax, 0x16          ; job / 100 == 22   (the 22xx Evan family)
00a6cf34  cmp esi, 0x7d1         ; or job == 2001    (Evan beginner)
```

Reads a short and a byte, then special-cases job 2001 and the 22xx family — the Evan SP window.
That is the `OnIncSPMessage` arm, and it is exactly why this enum moved in "the Evan patch".

### Arm-by-arm proof of the mapping

Every v83 arm's body was compared instruction-for-instruction (to its first `ret`) with the v84 arm
this mapping sends it to. **13 of 14 are byte-shape identical:**

| v83 mode | v83 handler | → v84 mode | v84 handler | bodies identical |
|---|---|---|---|---|
| 0 | `0xA20AD9` | 0 | `0xA6BEEF` | yes |
| 1 | `0xA20F4C` | 1 | `0xA6C362` | **no** — rewritten at v84, index unchanged |
| 2 | `0xA216FC` | 2 | `0xA6CB31` | yes |
| 3 | `0xA21AC5` | 3 | `0xA6CFD7` | yes (this is `#IncreaseExperience`, the attack path) |
| — | — | **4** | `0xA6CEFA` | **new** — matches no v83 arm |
| 4 | `0xA2212D` | 5 | `0xA6D63F` | yes |
| 5 | `0xA221F3` | 6 | `0xA6D705` | yes |
| 6 | `0xA222C9` | 7 | `0xA6D7DB` | yes |
| 7 | `0xA2238F` | 8 | `0xA6D8A1` | yes |
| 8 | `0xA217A2` | 9 | `0xA6CBD7` | yes |
| 9 | `0xA21A78` | 10 | `0xA6CEAD` | yes |
| 10 | `0xA2160B` | 11 | `0xA6CA40` | yes |
| 11 | `0xA2187E` | 12 | `0xA6CCB3` | yes |
| 12 | `0xA2195A` | 13 | `0xA6CD8F` | yes |
| 13 | `0xA219BE` | 14 | `0xA6CDF3` | yes |

The one exception is mode **1** (quest record): its v84 body differs from instruction 3 onward
(different register allocation, `sub esp,0x74` → `0x7C`), so it was genuinely rewritten. Its *index*
is still 1 at both versions, and it is boxed in on both sides by exact matches at 0, 2 and 3.

A second, independent confirmation of the insertion point: the v83 mode-9 handler `0xA21A78` is 25
instructions long, and the function laid out immediately after it in v83 is `0xA21AC5` (mode 3). At
v84 the function laid out immediately after `0xA6CEAD` is `0xA6CEFA` — the new arm. So the new
function was inserted at exactly that point in source order, which is what pushes every later
function's rank up by one.

**Shift point 4, +1.** Modes 0, 1, 2 and 3 are unchanged — which is why the kill path (EXP is 3) and
quest progress (1) never broke on v84 while everything from fame up has been wrong the whole time.

## 2. `SERVERMESSAGE` — `CWvsContext::OnBroadcastMsg`, +2 from mode 12 — **CONFIRMED**

Dispatcher case **0x44** at v83, **0x46** at v84 → `OnBroadcastMsg`. Identified by body (reads a
type byte, then `cmp esi,4` gates a second byte — the scrolling-header flag), and the v83 entry
transfers to the v84 entry by masked signature in **both** dumps.

| | v83 | v84 |
|---|---|---|
| function | `0xA22785` | `0xA6DC97` |
| switch | `0xA229B0` `cmp esi,0xD` / `jmp [esi*4+0xA236D3]` | `0xA6DF39` `cmp esi,0xF` / `jmp [esi*4+0xA6ED68]` |
| arms | **14** (modes 0..13) | **16** (modes 0..15) |

| v83 mode | v83 body | → v84 mode | v84 body | identical |
|---|---|---|---|---|
| 0 | `0xA229C0` | 0 | `0xA6DF49` | yes |
| 1 | `0xA22B85` | 1 | `0xA6E11A` | yes |
| 2 | `0xA22B95` | 2 | `0xA6E12A` | yes |
| 3, 8, 9, 10 | `0xA22D3A` (one shared body) | 3, 8, 9, 10 | `0xA6E2CF` (one shared body) | yes |
| 4 | `0xA234D1` | 4 | `0xA6EB5A` | yes |
| 5 | `0xA23542` | 5 | `0xA6EBCB` | yes |
| 6 | `0xA23546` | 6 | `0xA6EBCF` | yes |
| 7 | `0xA235FA` | 7 | `0xA6EC83` | yes |
| 11 | `0xA231E8` | 11 | `0xA6E78C` | yes |
| — | — | **12** | `0xA6EA8E` | **new** (string resource `0x1620`) |
| — | — | **13** | `0xA6EAF7` | **new** (string resource `0x1621`) |
| 12, 13 | `0xA22AA4` (one shared body) | **14, 15** | `0xA6E039` (one shared body) | yes |

All 14 v83 bodies match under this mapping, including the 3=8=9=10 shared-body fingerprint and the
shared 12/13 → 14/15 pair. **Shift point 12, +2.** Modes 0..11 are unchanged.

## 3. Free by-product: two opcodes checked (NOT changed here)

Reading the dispatcher tables incidentally settles two rows the opcode-table ticket owns. Recorded,
not acted on — `sendops-84` belongs to ticket 21/32.

- `SHOW_STATUS_INFO` = **0x27** at both v83 and v84 (dispatcher case 0x27 → `OnMessage` in both).
  Cosmic agrees.
- `SERVERMESSAGE` = 0x44 at v83, **0x46** at v84 (`OnPacket` case 0x46 → `0xA6DC97`).
  **Cosmic's `sendops-84` value 0x46 is right and atlas's 0x44 is wrong**, contradicting ticket 32
  §6.2. Same for `SPAWN_PORTAL`/`OnTownPortal`, which the v84 table puts at 0x45 (`0xA6DBB8`),
  matching atlas's note there.

## 4. What changed

`src/main/java/tools/PacketCreator.java` — two named helpers next to the existing `dialogType`, both
gated on `ServerConstants.VERSION >= 84`, evidence in their javadoc:

```java
private static int statusInfoMode(int v83Mode) {
    return ServerConstants.VERSION >= 84 && v83Mode >= 4 ? v83Mode + 1 : v83Mode;
}

private static int broadcastMsgMode(int v83Mode) {
    return ServerConstants.VERSION >= 84 && v83Mode >= 12 ? v83Mode + 2 : v83Mode;
}
```

### SHOW_STATUS_INFO — 10 sites changed

| site | function | v83 mode | v84 mode |
|---|---|---|---|
| 1759 | `getShowFameGain` | 4 | 5 |
| 1787 | `getShowMesoGain(_, inChat=true)` | 5 | 6 |
| 6260 | `updateAreaInfo` | 10 | 11 |
| 6268 | `getGPMessage` | 6 | 7 |
| 6275 | `getItemMessage` | 7 | 8 |
| 6525 | `showInfoText` | 9 | 10 |
| 6845 | `getDojoInfo` | 10 | 11 |
| 6853 | `getDojoInfoMessage` | 9 | 10 |
| 6898 | `updateDojoStats` | 10 | 11 |
| 7077 | `bunnyPacket` | 9 | 10 |

### SHOW_STATUS_INFO — 8 sites deliberately untouched (below the shift point)

`getShowExpGain` (3), `getShowMesoGain(_, inChat=false)` (0), `getShowItemGain` else-branch
(`writeShort(0)` = mode 0 + pad), `forfeitQuest` (1), `completeQuest` (1), `updateQuest` (1),
`getShowInventoryStatus` (0 — its *second* byte is a sub-mode inside the drop-pickup arm, not the
enum), `itemExpired` (2).

### SERVERMESSAGE — 1 site changed, and it is a guard, not a fix

The only SERVERMESSAGE mode byte Cosmic writes non-constant is in the private
`serverMessage(int type, …)`, so the helper goes there. **Every mode this server actually sends is
below 12**: the literal `serverNotice` types across `src/` and `scripts/` are 0, 1, 2, 3, 5 and 6;
`serverMessage(String)` sends 4; `itemMegaphone` 8; `getMultiMegaphone` 10; `gachaponMessage` 11.
The one route that can reach 12+ is `AdminChatHandler`, which takes the type from the wire.

So §6.2's *"`SERVERMESSAGE` is the one that will bite first"* is **wrong on the mode enum** — no
Cosmic call site is at or above the shift point. (That ticket's opcode concern is separate, and §3
above resolves it in Cosmic's favour anyway.)

## 5. Test

`src/test/java/tools/StatusInfoModeTest.java` — 4 tests, mode byte read off the real emitted wire
bytes, expectations branched on `ServerConstants.VERSION` so both versions stay pinned. Covers every
producer named above on both sides of both shift points.

Suite: **2130 passed, 0 failed.**

## 6. Unproven / not done

- **`CWvsContext::OnPartyResult` `TOWN_PORTAL` 37 → 40** (ticket 32 §6.1's third shift) — **CONFIRMED,
  +3.** v83 `localhome.exe` `OnPartyResult` jump table @`0xA3F260` (modes 4..37 via `add eax,-4`/
  `cmp eax,0x21`). Its top door cluster: mode `0x23`=35 @`0xA3EC92` (`Decode4`×3, stores town→`+0x2F56`
  / target→`+0x2F3E`), mode `0x24`=36 @`0xA3F19D` (`Decode1`+`DecodeStr`), mode `0x25`=37 @`0xA3ECD5`
  (`Decode1`+`Decode4`+`Decode4`+`Decode2`+`Decode2`, stores town/target/x/y per member — the
  position-bearing door update = `TOWN_PORTAL`). v84 `CWvsContext::OnPartyResult` @`0xA89CF3`
  (`D:\games\MSv84\opcodes\ida_export_gms_v84.json`, the export that settled ticket 62) is a clean
  compare chain whose data modes are `0x26`=38 (`Decode4`×3), `0x27`=39 (`Decode1`+`DecodeStr`+delegate),
  `0x28`=40 (`Decode1`+`Decode4`+`Decode4`+`Decode2`+`Decode2`), plus a new `0x45`=69. The three v83
  door shapes match the three v84 shapes one-for-one, uniformly +3: `0x23/0x24/0x25 → 0x26/0x27/0x28`.
  **Fixed:** `PacketCreator.partyPortal` (the only live `TOWN_PORTAL` sender, from `DoorObject`) now
  writes `0x26` when `VERSION >= 84`, else `0x23`. Test `partyPortalDoorModeShiftsByThreeOnV84`.
  Note: `updateParty`'s modes (`JOIN` 0x0F=15, `CHANGE_LEADER` 0x1B=27, etc.) sit below this cluster;
  the v84 export did not expose where the 3 modes were inserted, so whether any of those shifted is
  **not proven here** and was left untouched.
- **`getShowItemGain`'s `writeShort(0)`** is mode 0 followed by a zero pad. Mode 0 is unshifted, so
  it is out of scope here, but whether the v84 mode-0 arm reads that second byte the same way was
  not checked. **UNPROVEN.**
- **v84 mode 1 (quest record) has a rewritten body.** Only its *index* was verified as unchanged.
  Whether its field layout still matches Cosmic's `forfeitQuest`/`completeQuest`/`updateQuest`
  encoding is **UNPROVEN** and is the obvious next thing to read, since three call sites depend on
  it and it is the one arm in the table that v84 touched.
- **v84's new SERVERMESSAGE arms 12 and 13** were not decoded beyond their string-resource ids
  (`0x1620`, `0x1621`). Nothing sends them, so it did not matter.
