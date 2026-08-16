# Ticket 40 - Catching v84 packet-structure bugs without the owner playing

Eleven v84 packet-structure bugs were all found the same way: the owner plays, the client dies,
he reports it, we disassemble. The last one - `DROP_ITEM_FROM_MAPOBJECT` one byte short - killed
the client on every monster drop. This ticket makes two of those steps automatic.

Two things shipped. A third was scoped and deliberately not built; see the end.

---

## 1. The client's own crash log is now decoded (runs on the live server)

`CLIENT_START_ERROR` (recv `0x19`) is the client's crash history, in plain ASCII, cumulative,
re-uploaded on the connect after every crash. Nothing handled it - it only ever reached the debug
hex dump, where nobody read it.

`tools.packetvalidator.ClientStartErrorHandler`, registered in `PacketProcessor.registerCommonHandlers`,
now parses it. `validateState` returns `true` because the upload arrives on the login server before
the account logs in.

Each entry parses to `(version, character, worldId, channelId, mapId, errorCode, errorText, source)`.
Entries whose `ver(...)` matches `ServerConstants.VERSION` are logged at WARN behind a
`*** CLIENT CRASH REPORT ***` banner. Everything else is historical noise and goes to DEBUG, so
the v83/11001 DNS failures cannot drown out live v84 failures.

Error 38 is `CInPacket::Decode*` running past the end of the buffer (`mov dword ptr [ebp-4], 0x26`
at v84 `0x4066C9`), so the log line says it in words: *the server sent a packet that was too short*.

De-duplicated by exact entry text against a 512-entry LRU. Without that, every reconnect
re-screams the entire accumulated history.

Decoded from the real 1663-byte capture in `tools/v84/cutover-server.log` line 16
(opcode `0x19`, string length `0x067B` = 1659, 12 entries + trailing CRLF):

```
Client crash log from 127.0.0.1: 12 entries uploaded, 3 new
Older client crash report (not v84): client v83 crashed: no character @ no map (not in game yet)
    (world -1, ch -1) error 11001 (No such host is known.)
*** CLIENT CRASH REPORT (current version v84) *** client v84 crashed: no character
    @ no map (not in game yet) (world -1, ch -1) error 38 (Reached the end of the file.)
    -- THE SERVER SENT A PACKET THAT WAS TOO SHORT; the client read past the end of it
*** CLIENT CRASH REPORT (current version v84) *** client v84 crashed: uguuh @ map 40000
    (world 0, ch 0) error 38 (Reached the end of the file.)
    -- THE SERVER SENT A PACKET THAT WAS TOO SHORT; the client read past the end of it
```

12 entries in that capture, only 3 distinct: 5 x v83/11001 (DNS noise from before the cutover) and
7 x v84/error 38, of which 3 name map 40000. The exact wording above is pinned by
`ClientCrashReportTest.logLinesReadTheWayTheTicketSaysTheyDo`, not transcribed by hand.

Cost on the live server: one `readString` and one regex per connect, on a packet that arrives at
most once per session. Nothing on the hot path.

**What this will not catch:** anything that does not crash the client. A packet that is too LONG,
or one that decodes cleanly into wrong values, produces no crash entry. It also tells you the map,
never the opcode - it narrows the search, it does not name the bug.

## 2. Offline packet-structure validator

`tools.packetvalidator.PacketStructureValidator` replays a field model over real `PacketCreator`
output and reports UNDER_SEND (the error-38 crash) or OVER_SEND (bytes the client never reads).

### Where the models come from

`tools/v84/derive-decode-models.py` reads the Chronicle20/atlas IDA exports and writes
`tools/v84/decode-models-v84.tsv`, which is committed so the tests do not need the atlas checkout.

Structure comes from **gms_v83.json**, not gms_v84.json. The v84 export is machine-generated and
is not usable as a field list: every comment is empty, guards are decompiler locals (`v4 > 50`),
branches are flattened into one array (`CLogin::OnCheckPasswordResult` is 25 entries covering both
the success and failure paths), and `Delegate` entries call unfollowed subroutines of unknown
byte width. The genuine v84 deltas are applied on top from a table in that script, each carrying
the binary address it was proven at, matching the `ServerConstants.VERSION >= 84` branches already
in `PacketCreator`.

An opcode is rejected unless it has exactly one shape. Guarded packets survive only when the
SERVER pins the discriminator - `dropItemFromMapObject` is always called with a constant `mod`,
so the `nEnterType != 2` branch is decided server-side. Those are declared as named variants.

### Coverage - the honest numbers

| | count |
|---|---|
| sendops in `sendops-84.properties` | 307 |
| clientbound opcodes in the atlas v84 registry | 330 |
| modellable at all (rows in the TSV) | 106 |
| of those, still `candidate` - modelled but nobody vetted the emitter | 77 |
| **`verified` and actually checked against real PacketCreator output** | **29** |

**29 of 307 sendops, ~9%.** Rejections, by reason:

| reason | count |
|---|---|
| no `gms_v83` IDA entry for the symbol at all | 134 |
| conditional (`guard` on at least one field) | 47 |
| conditional in prose only - comment says "only if X", no `guard` key | 17 |
| dispatcher-only entry, `calls: null` | 16 |
| `DecodeBuf` / `DecodeBuffer` of unknown length | 14 |
| `Delegate` into an unfollowed subroutine | 3 |

Structurally un-modellable, and named because these are the ones people will ask about:
`SERVERMESSAGE` and `SHOW_STATUS_INFO` (dispatcher families - the shape depends on a mode byte),
`SPAWN_MONSTER` (tail is three `Delegate`s), `MOVE_PLAYER` and `MOVE_MONSTER` (variable-length
movement path), the whole `NPC_TALK` family (shape depends on dialog type), `LOGIN_STATUS`
(has both a success and a failure shape; the export models only success), and every packet
carrying an item record or a character look.

Four more were modelled cleanly but NOT promoted, because the v83 export entry is a stub:
`SKILL_EFFECT` and `CANCEL_SKILL_EFFECT` start after the dispatcher-consumed `characterId`,
`REMOVE_SPECIAL_MAPOBJECT` omits the summon object id, and `SPAWN_PLAYER` covers only the 4-byte
dispatcher prefix and none of the `CUser::Init` body. The export is inconsistent about this -
`FACIAL_EXPRESSION` and `SHOW_CHAIR` do include their prefix. Promoting these would have reported
large false OVER_SENDs against correct packets.

### The 29 verified models

`DROP_ITEM_FROM_MAPOBJECT/spawn-item`, `/spawn-meso`, `/update-item`, `KILL_MONSTER/normal`,
`SKILL_LEARN_ITEM_RESULT/result`, `COOLDOWN`, `REACTOR_SPAWN`, `REACTOR_HIT`, `REACTOR_DESTROY`,
`SPAWN_DOOR`, `REMOVE_DOOR`, `SPAWN_NPC`, `SPAWN_NPC_REQUEST_CONTROLLER`, `REMOVE_PLAYER_FROM_MAP`,
`SHOW_MONSTER_HP`, `FACIAL_EXPRESSION`, `SHOW_CHAIR`, `MOVE_MONSTER_RESPONSE`,
`SCRIPT_PROGRESS_MESSAGE`, `LAST_CONNECTED_WORLD`, `DELETE_CHAR_RESPONSE`, `CHAR_NAME_RESPONSE`,
`INCUBATOR_RESULT`, `SPAWN_KITE`, `REMOVE_KITE`, `REMOVE_MIST`, `SHOW_COMBO`,
`DESTROY_HIRED_MERCHANT`, `MONSTER_BOOK_SET_COVER`.

### One real finding

`PacketCreator.killMonster` writes the animation byte **twice**. `CMobPool::OnMobLeaveField` reads
`Decode4` + `Decode1` and stops - identically in the v79, v83, v84, v87, v92 and v95 exports. One
surplus trailing byte on every mob death. Trailing over-send is harmless to the client, so this is
pinned by a test rather than fixed, and fixing it will be a visible change instead of a silent one.

### Mutation checks

Byte shaved from a known-good packet, then restored; and a byte appended. Three opcodes, both
directions, all in `PacketStructureValidatorTest`:

| opcode | good | shave 1 byte | restored | append 1 byte |
|---|---|---|---|---|
| `DROP_ITEM_FROM_MAPOBJECT/spawn-item` | OK, 39 body bytes | UNDER_SEND at offset 40 | OK | OVER_SEND |
| `SKILL_LEARN_ITEM_RESULT/result` | OK, 16 body bytes | UNDER_SEND | OK | OVER_SEND |
| `SPAWN_NPC_REQUEST_CONTROLLER` | OK, 21 body bytes | UNDER_SEND | OK | OVER_SEND |

And a source-level mutation, which is the stronger proof: deleting `p.writeByte(0)` from
`PacketCreator.writeV84DropSpawnExtra` - i.e. re-introducing the exact bug that killed the client -
fails 4 tests and prints

```
DROP_ITEM_FROM_MAPOBJECT/spawn-item: UNDER_SEND (model ran out at offset 40 of 40)
PACKET TOO SHORT: client field 'v84 drop spawn effect' (U8) wants 1 byte(s) at offset 40 but the
packet is only 40 bytes. The client throws ZException error 38 here.
```

on all three drop variants. Restored, all 6 tests pass.

### What this will NOT catch

- **The 278 sendops with no model.** Most of the interesting ones. It is blind there, silently.
- **Wrong values.** It checks byte counts and field widths, nothing else. A correct-length packet
  with a swapped field order or a wrong id passes.
- **Anything only reachable through a real game object it cannot construct.** Emitters are
  Mockito-driven; a packet needing a live `MapleMap` or DB is not exercised.
- **New v84 deltas.** The models encode the deltas we already know. For an opcode where v84 differs
  from v83 in a way nobody has found yet, the model is wrong in the same direction as the code and
  will happily pass. This harness locks in what we have proven; it does not discover new deltas.
  Discovering those still means the binary.
- **Regenerating the TSV needs the atlas checkout**, which lives in a session scratchpad and is not
  in this repo. The TSV is committed, so the tests are self-contained; only re-derivation needs it.

## 3. Live wire-length assertion - scoped, not built

A runtime mode comparing emitted lengths against the model would only cover the same 29 opcodes the
offline harness already checks exhaustively, on a server the owner is playing on right now. Same
information, new hot-path code, nonzero risk. It becomes worth building the day models exist for
opcodes the offline harness cannot drive - the conditional and variable-length ones - because those
are the ones only real traffic can exercise. Not before.

## Files

- `src/main/java/tools/packetvalidator/ClientCrashReport.java` - crash-log entry + parser
- `src/main/java/tools/packetvalidator/ClientStartErrorHandler.java` - recv `0x19` handler
- `src/main/java/tools/packetvalidator/DecodeModel.java` - field model
- `src/main/java/tools/packetvalidator/PacketStructureValidator.java` - the replay check
- `src/main/java/tools/packetvalidator/PacketStructureModels.java` - TSV loader
- `src/test/java/tools/packetvalidator/ClientCrashReportTest.java`
- `src/test/java/tools/packetvalidator/PacketStructureValidatorTest.java`
- `tools/v84/derive-decode-models.py` - derivation, with the reject and promote rationale
- `tools/v84/decode-models-v84.tsv` - generated, committed
- `src/main/java/net/PacketProcessor.java` - one `registerHandler` line and its import
