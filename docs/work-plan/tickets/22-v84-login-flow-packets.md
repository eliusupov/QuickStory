# 22 — v84 login-flow clientbound packet layouts

**Status:** fix landed, awaiting live confirmation.
**Branch:** `worktree-evan-dualblade`.
**Symptom:** v84 client reaches `SERVERLIST_REQUEST (0x0B)` and dies. No server-side exception.
**Verdict:** **the world-list packets were never the problem.** All four are byte-identical v83→v84.
The packet that broke it is `LOGIN_STATUS (0x00)` — `getAuthSuccess` — which is **8 bytes short** for a
v84 client. One line, one version gate.

---

## 0. The headline, and the correction it forces

Ticket 17 §1.3 says *"the login band is byte-identical (clientbound `0x00–0x3E`…)"*. That is true of the
**opcode values** and has been read as if it were also true of the **packet bodies**. It is not, and the
related standing belief that *"packet structure only breaks at v86"* (ticket 17 §3.3 frames structure
drift as movement/attack at v84 and the equip serialiser at v88) is **wrong**.

**Structure breaks at v84, in the login flow, on the first packet after the handshake.** Ticket 17 has
been annotated in place with this correction.

The premise handed to me — *"this is a packet STRUCTURE difference"* — was right. The scoping — *"in the
world list reply"* — was wrong, and the evidence says so unambiguously.

---

## 1. Prove the instrument first

Everything below rests on one artifact, so it gets tested before it gets trusted.

### 1.1 The instrument

`D:\games\MSv84\opcodes\ida_export_gms_v84.json` was described in the dispatch as "866 v84 functions
with addresses, direction and `calls[]`… it DOES describe packet-handling functions". It is better than
that. For every clientbound handler, `calls[]` is **an ordered static trace of that function's
`CInPacket::Decode*` calls, loop bodies included, with a `guard` per entry naming the branch it sits
under.** That is a decode trace: it *is* the wire layout, as the v84 client reads it.

Reproduce any of it with:

```
python -c "import json;d=json.load(open(r'D:\games\MSv84\opcodes\ida_export_gms_v84.json'));\
[print('%2d %-10s %-14s %s'%(i,c['op'],c.get('guard',''),c.get('comment','')))\
 for i,c in enumerate(d['functions']['CLogin::OnCheckPasswordResult']['calls'])]"
```

### 1.2 Test A — does it reproduce a layout whose answer is already known?

`CLogin::OnWorldInformation` @0x60e5b3, 17 calls. The v83 (Cosmic `getServerList`) and v95
(`Rebirth95-csharp/src/Rebirth/Packets/CLogin.cs:12`, `Henesys-v95-java/…/Login.java:96`) layouts agree
with each other field-for-field. The trace reproduces that agreed layout exactly, loop structure and
all — see §2.1. **Pass.**

### 1.3 Test B — the discrimination test (the one that actually matters)

Test A only proves the export isn't garbage. It does not prove the export is *v84* rather than a v95
table wearing a v84 filename. So: find a field where **v83 and v95 disagree** and check which answer the
export gives.

`CLogin::OnCheckPasswordResult`, the region after `dwAccountId`:

| | bytes | fields |
|---|---|---|
| v83 (`PacketCreator.getAuthSuccess:719-724`; `LucianMS-v83/…/MaplePacketCreator.java:1573-1577`) | **4** | gender(1), gmBool(1), adminByte(1), countryCode(1) |
| v95 (`Edelstein-v95.1-csharp/…/UserOnPacketCheckPasswordPlug.cs:74-78`; `Rebirth95/CLogin.cs:165-168`) | **5** | nGender(1), nGradeCode(1), **nSubGradeCode(2)**, nCountryID(1) |
| **v84 export, indices 8–11** | **4 × Decode1** | sides with **v83** |

A v95 copy could not produce that. The export tracks v84 specifically.

### 1.4 Test C — a second discrimination, on the other packet in scope

`CLogin::OnSelectWorldResult` (CHARLIST) tail:

| | tail |
|---|---|
| v83 (`getCharList:913-914`) | `byte bLoginOpt`, `int nSlotCount` |
| v95 (Rebirth `CLogin.cs:74-77`, Henesys `Login.java:173-175`) | `byte bLoginOpt`, `int nSlotCount`, **`int nBuyCharCount`** |
| **v84 export, indices 7–8** | `Decode1`, `Decode4`, **and nothing after** → sides with **v83** |

So the instrument sides with v83 twice, with v95 zero times — and then, at exactly one field, sides with
v95 (§3). That asymmetric pattern is not something a broken or mislabelled instrument produces. **The
instrument is sound.**

### 1.5 Known limitations, stated up front

- The exporter **normalises adjacent `Decode1` pairs into `Decode2`** — the annotation on
  `CLogin::OnCheckUserLimitResult` says so explicitly (*"v84 reads as 2 × Decode1, atlas writes
  WriteShort — wire-equivalent"*). So **byte counts are reliable; op granularity is not.** Every claim
  below is a byte-count claim.
- `DecodeBuf` entries carry **no size**. Where a size matters it is taken from the v83/v95 bracket and
  labelled as such.
- Branch attribution comes from the `guard` field, not from a real CFG walk. For
  `OnCheckPasswordResult` the guards are clean and consistent (`v101 == 2` = ban branch,
  `v36 <= 1u` = success branch) but this is the softest part of the evidence.

---

## 2. Measured v84 layouts — the packets in the dispatch's scope

**All five check out unchanged. Nothing here was modified.**

### 2.1 `SERVERLIST 0x0A` — `CLogin::OnWorldInformation` @0x60e5b3 — **UNCHANGED**

| # | trace op | field | Cosmic `getServerList:796-818` | bytes |
|---|---|---|---|---|
| 0 | Decode1 | nWorldID | `writeByte(serverId)` | 1 |
| 1 | DecodeStr | sName | `writeString(serverName)` | var |
| 2 | Decode1 | nWorldState | `writeByte(flag)` | 1 |
| 3 | DecodeStr | sWorldEventDesc | `writeString(eventmsg)` | var |
| 4 | Decode2 | nWorldEventEXP_WSE | `writeByte(100); writeByte(0)` | 2 |
| 5 | Decode2 | nWorldEventDrop_WSE | `writeByte(100); writeByte(0)` | 2 |
| 6 | Decode1 | nBlockCharCreation | `writeByte(0)` | 1 |
| 7 | Decode1 | channel count | `writeByte(channelLoad.size())` | 1 |
| 8–12 | Str,4,1,1,1 | *per channel:* name, nUserNo, nWorldID, nChannelID, bAdultChannel | the channel loop | var |
| 13 | Decode2 | balloon count | `writeShort(0)` | 2 |
| 14–16 | 2,2,Str | *per balloon:* x, y, message | not sent (count is 0) | — |

The interesting part is rows 4–6. Cosmic writes five bytes `100, 0, 100, 0, 0`; the client reads
`short, short, byte`. `100` as LE short is `64 00`, so **HeavenMS's "rate modifier / event xp" byte pair
has always just been a `short 100` misread by the OdinMS lineage.** Same bytes, wrong names. v95 names
them correctly and writes the same bytes. Nothing to fix.

### 2.2 End-of-serverlist — same handler — **UNCHANGED**

`getEndOfServerList:825-829` writes `byte 0xFF`. The client's first read is `Decode1 nWorldID`, sees
`0xFF`, stops. Rebirth95 `CLogin.cs:49` does the same (`Encode1(0xFF)`).

> Henesys-v95 `Login.java:133` writes `encodeInt(255)` instead. **Henesys is wrong here, not v84.** The
> trailing three zero bytes are harmless only because MapleStory clients ignore trailing data — a good
> illustration of why single-repo evidence is not evidence. `byte` is correct.

### 2.3 `LAST_CONNECTED_WORLD 0x1A` — `CLogin::OnLatestConnectedWorld` @0x60d26e — **UNCHANGED**

One `Decode4`. `selectWorld:2723-2727` writes `writeInt(world)`. Matches Rebirth95 `CLogin.cs:53-58`.

### 2.4 `RECOMMENDED_WORLD_MESSAGE 0x1B` — `CLogin::OnRecommendWorldMessage` @0x60d2ba — **UNCHANGED**

`Decode1` count, then per entry `Decode4` worldId + `DecodeStr` message. Exactly
`sendRecommended:2729-2737`. In the failing session the list was empty, so this was a single `00` byte.

### 2.5 `SERVERSTATUS 0x03` — `CLogin::OnCheckUserLimitResult` @0x60e275 — **UNCHANGED (2 bytes)**

`getServerStatus:840-844` writes `writeShort(status)`. The client reads 2 bytes. Wire-compatible.

> **One real caveat, deliberately not "fixed".** v83 puts `status` in the **first** byte; Rebirth95
> (`CLogin.cs:92-93`) and Henesys (`Login.java:144-145`) both put `bOverUserLimit` first and
> `bPopulateLevel` second — i.e. **the two bytes are swapped relative to v83.** The v84 trace is a
> single normalised `Decode2` and **cannot tell me which order v84 uses** (§1.5). It does not matter in
> practice: Cosmic only ever sends status `0` or `2`, and at `0` both orderings are `00 00`. At `2` the
> worst case is a "Highly populated" glyph that should have said "Full". Cosmetic, not a crash. Left
> alone — changing it would alter v83 behaviour on a guess. **Unresolved, see §6.**

---

## 3. The packet that was actually broken

### `LOGIN_STATUS 0x00` — `CLogin::OnCheckPasswordResult` @0x60d368 — **v84 reads 8 bytes Cosmic never sent**

Success branch (`guard: v36 <= 1u`, i.e. `nRet <= 1`), trace vs. `getAuthSuccess:711-747`:

| # | trace | field | Cosmic writes | bytes |
|---|---|---|---|---|
| 0,1,2 | 1,1,4 | nRet, nRegStatID, nUseDay | `writeInt(0); writeShort(0)` | 6 |
| 7 | Decode4 | dwAccountId | `writeInt(c.getAccID())` | 4 |
| 8–11 | 1,1,1,1 | gender, gm, admin, country | four byte writes | 4 |
| 12 | DecodeStr | sNexonClubID | `writeString(accountName)` | var |
| 13 | Decode1 | nPurchaseExp | `writeByte(0)` | 1 |
| 14 | Decode1 | nChatBlockReason | `writeByte(0)` | 1 |
| 15,16 | DecodeBuf + Delegate | dtChatUnblockDate → FILETIME | `writeLong(0)` | 8 |
| 17,18 | DecodeBuf + Delegate | dtRegisterDate → FILETIME | `writeLong(0)` | 8 |
| 19 | Decode4 | nNumOfCharacter / flag | `writeInt(1)` | 4 |
| 22 | Decode1 | PIN opt | PIN byte | 1 |
| 23 | Decode1 | PIC opt / bLoginOpt | PIC byte | 1 |
| **24** | **DecodeBuf** | **login session key** | ***nothing*** | **0 sent, 8 read** |

*(indices 3–6 are the ban branch, `guard: v101 == 2`; 20–21 are Delegates with no decode.)*

**Every field lines up until index 24, where the packet ends and the client keeps reading.**

Evidence that index 24 is real and is 8 bytes:

1. **v95, two independent emulators, same position, same size.**
   `Edelstein-v95.1-csharp/…/UserOnPacketCheckPasswordPlug.cs:90-94` — after the two trailing bytes:
   `message.User.Key = new Random().NextInt64(); packet.WriteLong(message.User.Key);`
   `Rebirth95-csharp/src/Rebirth/Packets/CLogin.cs:179-180` — `Encode1(1); Encode8(0);`
   Edelstein and Rebirth are unrelated codebases (C#, different authors, different architectures) and
   agree on position and width. Size **8** is taken from them, since the export records `DecodeBuf`
   without a size (§1.5).
2. **v83 does not have it.** Cosmic (`getAuthSuccess`), HeavenMS upstream
   (`MaplePacketCreator.java:716-745`) and LucianMS (`MaplePacketCreator.java:1567-1589`) all stop after
   the PIN/PIC bytes. Those three are one lineage, not three witnesses — but **the owner's v83 Ezorsia
   client works today against exactly this code**, which is a live measurement and settles v83.
3. **The bracket closes.** v83 absent, v84 trace present, v95 present in two places ⇒ added at or before
   v84.
4. **A structural tell.** Indices 15 and 17 are `DecodeBuf` *followed by a `Delegate`* — a buffer read
   feeding a FILETIME constructor. Index 24 is a bare `DecodeBuf` with no Delegate: a raw 8-byte value
   stored as-is. That matches Edelstein's `User.Key` (an opaque session key) and not Rebirth's guessed
   name `dwHighDateTime`. Small point, but it is the export agreeing with the better-sourced repo.

### Why this kills the client at the *world list* and not at login `[INFERRED — not observed]`

I cannot attach a debugger to a Themida-packed binary, so the mechanism is reasoned, not measured:
`CInPacket` over-reads past `m_uLength`, `OnCheckPasswordResult` aborts before completing the
transition into the world-select step, and the world-info packets then arrive at a `CLogin` whose
world-select state was never constructed. The log fits — the client still emits `SERVERLIST_REQUEST`,
then dies on the reply with nothing further on the wire.

**Treat the mechanism as a hypothesis. Treat the missing 8 bytes as measured fact.** The fix stands on
the fact, not on the story: this is the only field in the entire login flow where the server's output
and the v84 client's decode trace disagree.

---

## 4. What changed

`src/main/java/tools/PacketCreator.java` — `getAuthSuccess`, plus one import:

```java
if (ServerConstants.VERSION >= 84) {
    p.writeLong(0);
}
```

Version-gated on `ServerConstants.VERSION`, which is already the field that drives the hello packet and
both cipher keys — so the gate cannot drift out of sync with what the client is. **Setting `VERSION`
back to 83 restores the byte-exact v83 packet.** No other line of the v83 path is touched.

Why `0`: Rebirth95 sends a constant `0` and works; Cosmic never reads the value back. Edelstein sends a
random key because it uses it for its own migration bookkeeping, which Cosmic does not do.

**This change is fail-safe in the direction that matters.** MapleStory clients do not validate trailing
packet length, so if index 24 turned out not to exist, 8 surplus bytes are ignored. The failure mode
that actually hurts is the one we had — too few.

---

## 5. Proof, as far as an agent gets without the client

- Instrument validated on three known answers before use — §1.2/1.3/1.4, including two discrimination
  tests the export could only pass by genuinely being v84.
- Every clientbound packet in the login flow walked field-by-field against the trace: `LOGIN_STATUS`
  (success, `getLoginFailed`, `getPermBan`, `getTempBan`), `SERVERSTATUS`, `SERVERLIST`,
  end-of-serverlist, `LAST_CONNECTED_WORLD`, `RECOMMENDED_WORLD_MESSAGE`, `CHARLIST`. One mismatch
  found, one mismatch fixed.
  - `getLoginFailed:639-645` → `Decode1, Decode1, Decode4` = 6 bytes. ✓
  - `getPermBan`/`getTempBan:685-703` → ban branch `Decode1, Decode1, Decode4, Decode1, DecodeBuf(8)`. ✓
- Opcode premise re-verified independently rather than taken on trust:
  `diff src/main/resources/opcodes/sendops-83.properties sendops-84.properties` first differs at
  `BUDDYLIST 0x3F`; the recv diff first differs at `ADMIN_CHAT 0x76`. Every login opcode is identical.
- `mvnw.cmd -o test`: **2090 passed, 0 failed** — baseline held.
- Compiles clean. Not deployed; running server untouched; `ServerConstants.VERSION` left at 84.

**No unit test added.** The change is a one-line version-gated `writeLong(0)`; `getAuthSuccess` needs a
live `Client` plus `Server.getInstance()` plus the DB to construct, and a mock harness for that would be
more code than the fix and would assert only that the branch I just wrote is the branch I just wrote.
The real check is the owner's client. The packets I verified but did *not* change are likewise left
untested — a regression test over code I did not touch is theatre.

---

## 6. Unresolved — read before trusting

1. **`DecodeBuf` size at index 24 is inferred, not measured.** The export has no size field. **8** comes
   from Edelstein and Rebirth agreeing. If the client is still unhappy, this is the first thing to
   re-measure — and note the failure would be *"still 8 short"*, not a new failure, since surplus bytes
   are ignored.
2. **`SERVERSTATUS` byte order.** v83 sends `(status, 0)`, v95 sends `(0, status)`. The v84 trace is a
   fused `Decode2` and cannot distinguish. Left as v83 — cosmetic at worst (§2.5), and changing it on a
   guess would risk the working v83 path.
3. **`getServerList` writes a hardcoded `writeByte(1)` for the per-channel `nWorldID`
   (`PacketCreator.java:812`) while the world is 0.** Both v95 sources write the real world id. The byte
   *count* is right so it cannot desync the parse, but it is a wrong value the client may index with.
   Not changed: the working v83 client tolerates it, and there is no v84 evidence that anything changed
   here. Flagged because if a world-list crash survives this fix, this is suspect #2.
4. **Branch attribution for index 24** rests on the export's `guard` strings, not a CFG walk (§1.5).
5. **Not examined:** anything past `CHARLIST`. `SELECT_CHARACTER_BY_VAC`, `CHAR_NAME_RESPONSE`,
   `ADD_NEW_CHAR_ENTRY`, `SERVER_IP` and the whole channel-server flow are untouched by this ticket. The
   same instrument and the same method apply — `CLogin::OnSelectCharacterResult` @0x61085f already has
   its trace in the export and is the obvious next one to walk.
6. **`Received packet id 35` (0x23) at 18:46:33 is still unexplained.** Not in Cosmic's table at either
   version, ignored by the server, and the client proceeded regardless — so it is not implicated. Noted
   so the next reader does not re-derive it.
