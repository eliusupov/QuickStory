# 38 — MOVE_DRAGON / MOVE_SUMMON / MOVE_PET: does the v84 movement header change too?

**Status:** delivered 2026-08-16. **No code fix was needed and none was shipped.** All three headers
are byte-identical between v83 and v84 and the handlers were already correct. What shipped is the
proof, as comments at each site, plus a test with teeth. One unrelated **live bug found and not
fixed** (it is another agent's file): `recvops-84.properties` has the three summon opcodes 2 too low.

## The question

`MovePlayerHandler` needed a v84 gate — serverbound header 9 → 33 bytes, commit `404ec864d`, confirmed
live (portals work). `MoveDragonHandler`, `MoveSummonHandler` and `MovePetHandler` have no v84 handling
at all. Did the v84 anti-cheat "dr words" pass touch the **shared** movement prologue — in which case
all three are broken — or only `CUser`'s own encoder — in which case they are already right?

This failure class is silent. `updatePosition` throws `EmptyMovementException`, every one of these
handlers catches and ignores it, the object never moves, nothing is logged. A green log proves
nothing, so the question was settled at the binary and on the wire, not by reasoning.

## Answer in one line

The pass rewrote **`CVecCtrlUser::EndUpdateActive` only** — and virtualised it. `CMovePath::Encode`,
which every movement packet in the game shares, is instruction-for-instruction identical between the
two clients, and the dragon / summon / pet encoders are unchanged. 4, 8 and 12 bytes at both versions.

## The measurement

Instruments: v83 `D:\games\MapleStory\localhome.exe` (read-only), v84 memory dump based at
`0x400000` (file offset == VA − 0x400000), capstone 5.0.7. Both images: `COutPacket::Encode1/2/4` are
the `push esi; mov esi,ecx; push N; call Reserve` thunks —
v83 `0x00406549 / 0x00427F74 / 0x004065A6`, v84 `0x0040661F / 0x00428A68 / 0x0040667C`;
`EncodeBuffer` (memcpy of exactly `nLen`) v83 `0x0046C00C`, v84 `0x0046E5FE`.

Method: for each packet, read the send-side encoder between the `COutPacket` constructor and the
`CMovePath::Flush` call, then add `CMovePath::Encode`'s own head. Callers of `Flush` were enumerated by
direct xref — v83 `0x0068A88D` has 6, v84 `0x006A1567` has 5 — which is how the dragon was found
(it is unnamed in the atlas export) and how the missing sixth v84 caller exposed the virtualisation.

### The shared blob — unchanged

`CMovePath::Encode` v83 `0x0068A563`, v84 `0x006A121A`. Same instructions, same struct offsets
(`[esi+4]`, `[esi+0xC]`, `[esi+0x18]`, `[esi+0x1C]`, `[esi+0x30..0x3C]`), same call sequence:

| field | v83 | v84 |
|---|---|---|
| `Encode2` startX | `0x0068A57C` | `0x006A1233` |
| `Encode2` startY | `0x0068A592` | `0x006A1249` |
| `Encode1` element count | `0x0068A5C3` | `0x006A127A` |

**Head = 4 bytes in both.** Everything below is that 4 plus the packet's own prologue.

### Per packet

| packet | encoder | v83 | v84 | prologue | header | Δ |
|---|---|---|---|---|---|---|
| MOVE_DRAGON | `CVecCtrlDragon::EndUpdateActive` | `0x009B7B9C` | `0x009FF057` | none, either version | **4 → 4** | 0 |
| MOVE_SUMMON | `CVecCtrlSummoned::EndUpdateActive` | `0x009C84E9` | `0x00A0FD89` | `Encode4` owner cid `[this+0x248]` | **8 → 8** | 0 |
| MOVE_PET | `CVecCtrlPet::EndUpdateActive` | `0x009C4E41` | `0x00A0C600` | `EncodeBuffer(pet+0xA0, 8)` = `m_liPetLockerSN` | **12 → 12** | 0 |
| MOVE_PLAYER | `CVecCtrlUser::EndUpdateActive` | `0x009CB992` | `0x00A1334E` | see below | **9 → 33** | +24 |

Exact sites:

- **Dragon** — v83: opcode push `0xB5` `@0x009B7BBD`, ctor `@0x009B7BC5`, `Flush` `@0x009B7BD8`.
  v84: push `0xBA` `@0x009FF078`, ctor `@0x009FF080`, `Flush` `@0x009FF093`.
  **Zero `Encode*` calls between ctor and Flush in either version** — the dragon writes no
  packet-level prologue at all. This is why `MoveDragonHandler`'s two bare `readShort`s are right.
- **Summon** — v83: push `0xAF` `@0x009C8523`, ctor `@0x009C852B`, `Encode4` `@0x009C853D`,
  `Flush` `@0x009C854C`. v84: push `0xB4` `@0x00A0FDC3`, ctor `@0x00A0FDCB`, `Encode4` `@0x00A0FDDD`,
  `Flush` `@0x00A0FDEC`.
- **Pet** — v83: push `0xA7` `@0x009C4E65`, ctor `@0x009C4E6D`, `EncodeBuffer` `@0x009C4E8F`,
  `Flush` `@0x009C4E9E`. v84: push `0xAC` `@0x00A0C624`, ctor `@0x00A0C62C`, `EncodeBuffer`
  `@0x00A0C64E`, `Flush` `@0x00A0C65D`. The handler's `readInt` + `readLong` = 12 = 8 SN + 4 origin,
  which is why `parseMovement` reads the command count straight away: the `readLong` ate the origin.

## The MOVE_PLAYER self-check (mandatory — result: reproduced, one caveat)

**v83, from the binary — reproduced exactly.** `CVecCtrlUser::EndUpdateActive` `@0x009CB992`:
opcode push `0x29` `@0x009CBB2A`, ctor `@0x009CBB2F`, `Encode1` fieldKey (`[ctx+0x134]`) `@0x009CBB4B`,
`Encode4` field CRC (`[ctx+0x7B0]`) `@0x009CBB5E`, `Flush` `@0x009CBB6C`. 1 + 4 + 4 = **9**. Matches
the shipped `V83_MOVEMENT_HEADER` exactly.

**v84, from the binary — NOT readable.** `@0x00A1334E` opens identically (SEH frame,
`lea ecx,[this+0x1AC]`, `call 0x006A1502`, `test/je`), then at `0x00A13382` loads two magic dwords
(`0x19DEA1BD`, `0xBE02FF0A`), calls `0x00496001`, and at `0x00A133A5` jumps to `0x00DD1E03` — a
`push imm32; jmp 0x00C7B8DE` code-virtualizer stub. **The v84 user movement encoder is VM-obfuscated**
and cannot be read statically. That is also the finding: the dr-words pass hit exactly one encoder, and
it is exactly the one that is now protected. It has no `Flush` xref for the same reason.

**v84, from the wire — reproduced exactly.** Five real `ClientSend:MOVE_PLAYER` packets in
`tools/v84/cutover-server.prev.log` (22:44:50.767 – 22:44:52.807). Parsed at header 9 all five read a
command count of `0xFF`; parsed at 33 all five give counts 4 / 2 / 1 / 2 / 3 with exactly that many
well-formed commands and a clean 18–20 byte trailer. **Five for five.** Layout, handler offsets:

```
 0..7   FF FF FF FF FF FF FF FF   dr pair, obfuscated             [NEW in v84]
 8      01                        bFieldKey                       (v83 had this at offset 0)
 9..16  FF FF FF FF FF FF FF FF   dr pair, obfuscated             [NEW in v84]
17..20  AE 12 4A BB               field CRC - constant all 5      (v83 had this at offset 1)
21..28  8 bytes, vary per packet  dwKey + crc32                   [NEW in v84]
29..32  startX, startY            CMovePath::Encode head          (unchanged)
33      count
```

9 → 33 is +24 = 8 + 8 + 8, all three additions inside `CVecCtrlUser` and none in `CMovePath`.

## Corroboration found along the way: the three v84 summon opcodes (fixed elsewhere, `6ea21ac2d`)

Every `COutPacket` constructor site in `0xA0`–`0xC5` was mapped in both images and matched pairwise.
**Every pair is exactly v83 + 5** — pet family, BEHOLDER, MOVE_DRAGON, CHANGE_QUICKSLOT, MOVE_LIFE.
`recvops-84.properties` disagreed on three, which this ticket found independently while identifying
the encoders, and which a concurrent agent measured to the same values and fixed in `6ea21ac2d`:

| entry | v83 | table before | measured v84 | site |
|---|---|---|---|---|
| MOVE_SUMMON | `0xAF` | `0xB2` ✗ | **`0xB4`** | push `@0x00A0FDC3` (`CVecCtrlSummoned::EndUpdateActive`) |
| SUMMON_ATTACK | `0xB0` | `0xB3` ✗ | **`0xB5`** | `CSummoned::TryDoingAttackManual` — not read here |
| DAMAGE_SUMMON | `0xB1` | `0xB4` ✗ | **`0xB6`** | push `@0x007CBD30` (`CSummoned::SetDamaged`) |

MOVE_SUMMON and DAMAGE_SUMMON were read directly off the pushes here. SUMMON_ATTACK was **not**:
this ticket's constructor-site scan does not see it (it uses the sized `COutPacket` overload, as the
opcode ticket records), so `0xB5` is that ticket's finding, not a measurement made here.

This matters for the header question only as a warning about ordering: until `6ea21ac2d` the v84
client's MOVE_SUMMON (`0xB4`) was dispatched to Cosmic's DAMAGE_SUMMON handler, so
`MoveSummonHandler` was never reached and "summons look broken at v84" was **not** evidence about its
header. Routing is fixed; the header measured above is now what decides whether summon movement works.

## What shipped

- `MoveDragonHandler.java`, `MoveSummonHandler.java`, `MovePetHandler.java` — comment blocks only,
  recording the addresses above. **No behavioural change; the v83 path is untouched byte for byte.**
- `src/test/java/net/server/channel/handlers/MovementHeaderTest.java` — five tests pinning all four
  headers, each asserting the target actually reached the encoded destination (not merely that no
  exception escaped), plus one test that replays a real captured v84 MOVE_PLAYER byte for byte.
- Mutation-checked, because a green suite proves nothing here: injecting a 24-byte v84 skip into the
  three handlers and reverting MOVE_PLAYER to 9 fails all five tests; a subtle 2-byte shift on the
  dragon alone fails that test too.
