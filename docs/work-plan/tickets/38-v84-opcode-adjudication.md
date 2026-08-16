# 38 - v84 opcode table vs atlas: adjudicated against the client binary

Closes the open finding from ticket 32 ("10 sendops disagree with atlas; SERVERMESSAGE will bite
first"). The count and the direction were both wrong, and every disagreement it named resolves in
our favour.

## Instruments

Everything below is read out of the client images, not out of atlas. File offset = VA - 0x400000
for both.

* v83: `D:\games\MapleStory\localhome.exe` (read-only). Cosmic's `*ops-83.properties` is
  known-good, so every method here was first run against v83 and required to reproduce it.
* v84: the `MemDump.exe` capture of the live client (`v84_mem.bin`, session scratchpad).

Three instruments, in the order they were used:

1. **The clientbound registry.** `CWvsContext::OnPacket` is a dense jump-table switch and *is* the
   clientbound opcode table for 0x1D..0x7F.
   * v84 `0x00A51CD0`: `add eax,-0x1D` / `cmp eax,0x62` / `jmp [eax*4 + 0x00A52170]` (99 arms)
   * v83 `0x00A07A08`: `add eax,-0x1D` / `cmp eax,0x5F` / `jmp [eax*4 + 0x00A07E8E]` (96 arms)

   Each arm is a uniform 13-byte body `push [ebp+0C]; call <handler>; jmp end`, so slot -> handler
   is mechanical. Handlers are named from atlas's IDA exports; opcode = 0x1D + slot.
2. **Sub-pool dispatchers.** Opcodes above 0x7F are routed by `CField::OnPacket`
   (v83 `0x005314F0`, v84 `0x0053D772`) into per-pool `OnPacket`s, most of which are short
   `sub eax,N / jz / dec eax / jz` chains carrying the opcode as a literal immediate.
3. **The serverbound registry.** The client builds every outgoing packet with
   `COutPacket::COutPacket(nType)` - v83 `0x006EC9CE`, v84 `0x00703CFA`. Walking all 482 call
   sites and reading the pushed immediate gives the serverbound table directly. Pairing a v83 site
   to its v84 twin by wildcarded code signature mapped 134 opcodes; 128 confirmed our table
   unchanged.

## Verdicts

Real disagreement count is **20**, not 10: 10 clientbound + 10 serverbound. Rows present in only
one table are not disagreements and are excluded.

### Clientbound (sendops) - 10 rows, atlas wrong on all 10

Every one is a stale `provenance: csv-import` row in `gms_v84.yaml` that task-100's reshift missed;
in each case atlas's own neighbouring ida-discovered rows already bracket the correct value.

| key | ours | atlas | verdict | evidence |
|---|---|---|---|---|
| ALLIANCE_OPERATION | 0x44 | 0x42 | **ours** | jt slot 0x27 body `0x00A51D9F` -> `0x00A8592D`, between OnGuildResult(0x43) and OnTownPortal(0x45) |
| SERVERMESSAGE | 0x46 | 0x44 | **ours** | jt slot 0x29 body `0x00A51DC6` -> `0x00A6DC97` `CWvsContext::OnBroadcastMsg`; v83 slot 0x27 -> 0x44 reproduces the known-good table |
| QUICKSLOT_INIT | 0xA2 | 0x9F | **ours (unproven directly)** | no dispatch site exists for it in either image; boxed in by `CUserPool::OnPacket` v84 `0x009B202C` `sub edx,0xA3 / jz -> OnUserEnterField` vs v83 `0x0097208C` `sub edx,0xA0` |
| SET_NPC_SCRIPTABLE | 0x10E | 0x107 | **ours** | `CNpcPool::OnPacket`, byte-identical bodies: v83 `0x006D976D mov eax,0x107` / v84 `0x006F090D mov eax,0x10E` |
| CANNOT_SPAWN_KITE | 0x115 | 0x10E | **ours** | `CMessageBoxPool::OnPacket` v84 `0x00670A66` `sub eax,0x115 / jz -> 0x00670A95 OnCreateFailed` |
| SPAWN_KITE | 0x116 | 0x10F | **ours** | same chain, `dec eax / jz -> 0x00670AC0 OnMessageBoxEnterField` |
| MTS_OPERATION2 | 0x165 | 0x15B | **ours** | `CITC::OnPacket` v84 `0x005B46C0` `sub eax,0x164` then `dec eax / jz -> 0x005B4743 OnQueryCashResult` |
| MTS_OPERATION | 0x166 | 0x15C | **ours** | same chain, second `dec eax` -> `0x005B47C8 OnNormalItemResult` |
| MAPLELIFE_RESULT | 0x167 | 0x15D | **ours** | stub v84 `0x007FD849 sub eax,0x167` is byte-identical to v83 `0x007D758A sub eax,0x15D` |
| MAPLELIFE_ERROR | 0x168 | 0x15E | **ours** | second arm of that same stub |

Nothing in sendops-84 was changed. Evidence added as comments so the rows are not re-opened.

### Serverbound (recvops) - 10 rows

| key | ours (before) | atlas | verdict | evidence |
|---|---|---|---|---|
| WEDDING_ACTION | 0x8E | 0x8F | **ours - not a value disagreement** | Cosmic and atlas disagree on the *name* already at v83: Cosmic WEDDING_ACTION=0x8A == atlas WEDDING_WISH_LIST_REQUEST=0x8A. Joining by name shifts the row by one. Client emits 0x8E at `0x00A214B2` from v83 `0x009D9218` push 0x8A. |
| WEDDING_TALK | 0x8F | 0x90 | **ours - same naming offset** | Cosmic WEDDING_TALK=0x8B == atlas WEDDING_ACTION=0x8B. Client emits 0x8F at `0x0059176A` from v83 `0x00581AC1` push 0x8B (its neighbour 0x8C -> 0x90 sits at the identical +0x9CA9 delta). The WEDDING_TALK/WEDDING_TALK_MORE collision at 0x8F is inherited verbatim from v83's 0x8B, not new. |
| AUTO_AGGRO | 0xC2 | 0xBD | **ours** | ctor site v84 `0x0068456F` pushes 0xC2 (v83 `0x0066E213` pushes 0xBD) |
| TOUCHING_REACTOR | 0xD4 | 0xCE | **ours** | v84 `0x007535AB`+`0x00753613` push 0xD4; v83 `0x00735FB9`+`0x00736021` push 0xCE - same two sites |
| PLAYER_MAP_TRANSFER | 0xD5 | 0xCF | **ours** | v84 `0x005353B0` pushes 0xD5 (v83 `0x00529496` pushes 0xCF) |
| PARTY_SEARCH_START | 0xE4 | 0xDE | **ours** | v84 `0x00A89C29` pushes 0xE4 (v83 `0x00A3E271` pushes 0xDE) |
| PARTY_SEARCH_UPDATE | 0xE5 | 0xDF | **ours** | v84 `0x00A89C9C` pushes 0xE5 (v83 `0x00A3E2E4` pushes 0xDF) |
| OPEN_ITEMUI | 0xFFFF | 0xEC | **neither - FIXED to 0xF3** | v83 `0x0089124E` push 0xEC <-> v84 `0x008C536B` push 0xF3, identical bodies. atlas's 0xEC collides with its own reshifted COUPON_CODE=0xEC. |
| CLOSE_ITEMUI | 0xFFFF | 0xED | **neither - FIXED to 0xF4** | v83 `0x00890EAA`,`0x00890F79` <-> v84 `0x008C4FC7`,`0x008C5096`, both at delta 0x3411D |
| USE_ITEMUI | 0xFFFF | 0xEE | **neither - FIXED to 0xF5** | v83 `0x008913CF` push 0xEE <-> v84 `0x008C6854` push 0xF5, identical bodies |

## Found while auditing: three recvops-84 rows that were wrong and that atlas also got wrong

These do not appear in any atlas diff because atlas agrees with the wrong value. They were caught
by checking every recvops-84 value against the set of nTypes the v84 client can actually emit.

| key | was | now | evidence |
|---|---|---|---|
| MOVE_SUMMON | 0xB2 | **0xB4** | v83 `0x009C8523` push 0xAF <-> v84 `0x00A0FDC3` push 0xB4, identical bodies |
| SUMMON_ATTACK | 0xB3 | **0xB5** | uses the sized `COutPacket` overload so it has no site in the scan; bracketed by the two proven neighbours, only value left |
| DAMAGE_SUMMON | 0xB4 | **0xB6** | v83 `0x007A627A` push 0xB1 <-> v84 `0x007CBD30` push 0xB6; the same +0x25AB6 delta also maps BEHOLDER's two sites 0x7A5BED/0x7A5F64 -> 0x7CB6A3/0x7CBA1A (0xB2 -> 0xB7, already correct) |
| CLICK_GUIDE | 0xFFFF | **0xA8** | v83 `0x0095005C` push 0xA2 <-> v84 `0x00987E6E` push 0xA8 |

The summon block was the live bug: the pet block above it is +5 and binary-confirmed, but the
summon rows had been given +3. `MOVE_SUMMON` at 0xB2 matched nothing the client sends, while the
client's real `MOVE_SUMMON` (0xB4) was landing on Cosmic's `DAMAGE_SUMMON` handler.

## Unproven, deliberately left alone

* **QUICKSLOT_INIT = 0xA2.** Neither image contains a `cmp`/`sub` against this opcode and it is in
  no jump table, so there is nothing to read. Kept at 0xA2 on the boxed-in argument above. Do not
  move it to atlas's 0x9F without a dispatch site.

## Standing conclusion

atlas's `gms_v84.yaml` is wrong on **20 measured rows**, not merely off-by-one on version gates.
Its unstarred (`provenance: csv-import`) rows above 0x3E are v83 values that task-100 failed to
reshift, and its serverbound table still self-collides at 0xEC. Treat any atlas v84 opcode whose
provenance is not `ida-discovered` as unverified. The client binary is the registry.
