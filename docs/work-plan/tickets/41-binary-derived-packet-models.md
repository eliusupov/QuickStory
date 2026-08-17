# Ticket 41 - Packet models derived from the client binary, so the tool can find deltas

Ticket 40 shipped a structure validator whose models come from the hand-annotated gms_v83 atlas
export plus a table of v84 deltas somebody had already proven. Its own closing section says what is
wrong with that:

> **New v84 deltas.** The models encode the deltas we already know. For an opcode where v84 differs
> from v83 in a way nobody has found yet, the model is wrong in the same direction as the code and
> will happily pass. This harness locks in what we have proven; it does not discover new deltas.

That is only true because the model and the code share an ancestor. A model derived from the **v84
client binary** shares nothing with `PacketCreator`, so a disagreement between them is evidence.
This ticket builds that derivation, checks it on v83 where the answers are known, and runs it.

---

## 1. The instrument

`tools/v84/binmodel/` + `tools/v84/derive-binary-models.py`. Two stages:

**Dispatch resolution** (`dispatch.py`) - abstract interpretation of `CClientSocket::ProcessPacket`
with the opcode pinned to a concrete value. Registers hold a constant, the opcode (`OP+k`), or
nothing; the stack is explicit, so `[ebp+8]` and `[esp+0xc]` resolve. Comparisons against the
opcode decide their branches; `jmp [reg*4+table]` is followed; MSVC's two-level
`movzx eax, byte [eax+idx] ; jmp [eax*4+tbl]` is followed because a concrete base is read straight
out of the image. Data-dependent branches fork and every path is walked.

The walk continues into any call that is **handed the opcode** - the sub-dispatchers and the bare
forwarding thunks - and stops at the first opcode-selected call that is not. That call is the
handler.

**Body extraction** (`cfgtrace.py`) - the handler's CFG, replayed, collecting `CInPacket::Decode*`
calls per path. Switch arms are followed, so a handler that branches on a type byte yields one
shape per arm. `DecodeBuffer` lengths are recovered by backwards disassembly when the `push <len>`
was hoisted into a different basic block.

### Why the walk starts at ProcessPacket and not at the handler

Because the dispatchers read part of the packet. `CMobPool::OnMobPacket` reads the 4-byte mob id
before it knows which `CMob::` method to call; `CUserPool` reads the character id. Those bytes are
part of the packet. The atlas exports are inconsistent about whether they are listed -
`FACIAL_EXPRESSION` and `SHOW_CHAIR` include their prefix, `SKILL_EFFECT` and `SPAWN_PLAYER` do not,
and ticket 40 had to reject four models for exactly that reason. Walking from ProcessPacket makes
the question not arise.

### Addresses, both versions, all established here by disassembly

| | v83 | v84 |
|---|---|---|
| `CInPacket::Decode1` | `004065F3` | `004066C9` |
| `CInPacket::Decode2` | `0042470C` | `00425200` |
| `CInPacket::Decode4` | `00406629` | `004066FF` |
| `CInPacket::DecodeBuffer` | `00432257` | `00432EBE` |
| `CInPacket::DecodeStr` | `0046F30C` | `00471DED` |
| `CClientSocket::ProcessPacket` | `004965F1` | `0049B502` |
| `m_pStage->OnPacket` indirect call | `00496662` | `0049B573` |
| `CWvsContext::OnPacket` | `00A07A08` | `00A51CD0` |
| `CField::OnPacket` | `00531325` | `0053D5A7` |
| `CLogin::OnPacket` | `005F80FF` | `0060D075` |
| `__EH_prolog` | `00A60B98` | `00AACD18` |

The Decode\* family was located by its shared bounds check (`movzx esi, [ecx+0Ch]; sub esi, edx;
cmp esi, <width>; jae ok; throw 0x26`), not by trusting any export. `__EH_prolog` is modelled as an
intrinsic (`esp -= 16; ebp = esp_at_call - 4`); without it every SEH-using function looks
frameless, `[ebp+8]` stops resolving, and the opcode silently becomes unknown.

The stage is `CLogin` before entering a map and `CField` after, so the same opcode number can be two
different packets. Both are traced and reported separately.

## 2. The self-check, which is the gate

Run the same extractor over the **v83** binary and compare against the hand-annotated gms_v83
export - the source ticket 40's models were built from, and known-good.

```
python tools/v84/derive-binary-models.py --selfcheck <atlas>
```

For the 27 registry rows behind ticket 40's 29 verified models:

| | result |
|---|---|
| lands on the function the export names for that opcode | **27 / 27** |
| produces a field sequence the export admits | **27 / 27** |

Every search bound in `dispatch.py` (`STEP_BUDGET`, `FORK_CAP`, the CFG instruction budget, the
wall clock) is set to the smallest value that still passes this. Four of them were found by this
check failing first:

- a `seen` set shared across DFS branches threw away whole branches that reconverged on an already
  walked address - and with them the dispatch arm they were about to find (`COOLDOWN`, `SHOW_COMBO`
  resolved to nothing);
- calls that were stepped over did not pop their arguments, so `esp` drifted and `[esp+0xc]` stopped
  being the opcode (`SHOW_MONSTER_HP`, `MOVE_MONSTER_RESPONSE` landed on the wrong function);
- the guarding `cmp eax, N` of a jump table lives in the *previous* basic block, so switch arms
  were invisible (the whole mode-dispatched family traced as one arm);
- a per-`Tracer` instruction budget that was never re-armed returned empty shapes for everything
  after the first few opcodes of a sweep.

Truncated traces are never cached and never emitted: a summary that hit a budget, timeout or path
explosion is discarded rather than memoised, because a model that is short in the wrong direction is
the one failure this tool must not have.

## 3. What the delta scan found

```
python tools/v84/derive-binary-models.py --delta <atlas>
```

Shapes derived from the v83 image and from the v84 image, independently, then diffed. 327
clientbound opcodes are named in both registries; 193 produce a fixed-size shape on both sides.

| | count |
|---|---|
| structurally **identical** in v83 and v84 | **187** |
| **different** | 6 |
| no fixed shape on one side (variable-length or unresolved) | 134 |

### The two known deltas, rediscovered without being told about them

| opcode | v83 shapes | v84 shapes |
|---|---|---|
| `DROP_ITEM_FROM_MAPOBJECT` | 5, 23, 24, 29, 30, 32, 38 | 5, 23, **25**, 29, **31**, **33**, **39** |
| `SKILL_LEARN_ITEM_RESULT` | 4, 15 | **5**, **16** |

Every drop shape gains exactly one byte (`writeV84DropSpawnExtra`, the bug that killed the client on
every monster drop) and the skill-book result gains one leading byte (`bOnExclRequest`). Both are
already implemented in `PacketCreator`; the point is that a derivation with no knowledge of either
produced them. `LOGIN_STATUS` reproduces the third known delta the same way, as a trailing
`DecodeBuffer(8)` present in v84 and absent in v83 - the `m_aClientKey` tail.

### One new structural fact

`SHOW_STATUS_INFO` (`CWvsContext::OnMessage`) gained a message type in v84:

```
v83 00A209D4   call Decode1 ; movzx eax, al ; cmp eax, 0Dh ; ja default ; jmp [eax*4+0A20A88h]
v84 00A6BDD9   call Decode1 ; movzx eax, al ; cmp eax, 0Eh ; ja default ; jmp [eax*4+0A6BE9Ah]
```

14 arms in v83, **15 in v84**; reproduced in both independent v84 dumps. Every v83 arm still
decodes the same shape, and the new arm 14 decodes 12 bytes. This is an **addition, not a change**,
so nothing Cosmic sends today is affected - it is recorded because it is a real v84 difference that
was not previously written down, and because arm 14 is now available if a feature wants it.

### Three differences that are NOT evidence

- `MOVE_MONSTER`, `VIEW_ALL_CHAR` - the difference is how many iterations of a loop the walker
  unrolled before its cap (`VIEW_ALL_CHAR` reached 4 character records on v83 and 3 on v84). The
  per-record size is 110 bytes in **both**, which is the useful negative result.
- `PARTY_OPERATION` - v83 yields a 19-byte shape that v84 does not. The v84 function is larger and
  the enumeration does not complete. **UNPROVEN**: not adjudicated at the binary, and it is not
  claimed as a delta.

### And one candidate that turned out to be my own tool lying

`SPAWN_DRAGON` traced as **4 bytes** against a `PacketCreator.spawnDragon` that writes **16**. That
looks exactly like a delta. It is not: the resolver had stopped on the pool's early-out arm, where
the first opcode-selected call is a destructor (`0049BA9A`, and `0097B50C` on the other path), so
the "model" was the `CUserPool` character-id prefix and nothing else. Fixed by refusing any resolved
handler that cannot reach a `Decode*` at all, and written up here because it is the failure mode
that would otherwise get reported as a finding.

## 4. Coverage

| | before | after |
|---|---|---|
| `verified` model rows checked against real `PacketCreator` output | 29 | **35** |
| distinct sendops those rows cover | 27 | **33** |
| of the 307 sendops in `sendops-84.properties` | 8.8% | **10.7%** |

(Ticket 40 quoted "29 of 307, ~9%". That counted rows: `DROP_ITEM_FROM_MAPOBJECT` contributes three
of them and `KILL_MONSTER` and `SKILL_LEARN_ITEM_RESULT` one each as named variants, so the real
before-figure is 27 distinct sendops. The +6 here are six distinct sendops, no variants.)

Six added, all from the binary table, each promoted only after **both** the handler disassembly and
the emitting `PacketCreator` method were read:

| model | v84 handler | client reads | PacketCreator writes |
|---|---|---|---|
| `AUTO_HP_POT` | `0059DE20` | `Decode4` -> `[this+3C0]`, ret | `writeInt(itemId)` |
| `AUTO_MP_POT` | `0059DE46` | `Decode4` -> `[this+3C4]`, ret | `writeInt(itemId)` |
| `SET_GENDER` | `00A6F416` | `Decode1` -> `[this+202C]`, ret 4 | `writeByte(gender)` |
| `CLAIM_STATUS_CHANGED` | `00A7331C` | `Decode1` -> bool `[this+3110]`, ret 4 | `writeByte(1)` |
| `SET_EXTRA_PENDANT_SLOT` | `00A5E1CA` | `Decode1` -> `[this+38CC]`, ret 4 | `writeBool(toggle)` |
| `REMOVE_NPC` | `006F0BC8` | `Decode4` (npc object id) | `writeInt(objId)` |

That is a small number and it is the honest one. 22 opcodes derive a single fixed shape; only these
six survived reading the disassembly. The other 16 are in the table as `candidate`, which the Java
harness does not load.

### Why the other 285 are not modelled - measured, not guessed

| reason | count |
|---|---|
| no fixed-size shape at all (variable-length body, string, or unresolved) | 149 |
| more than one shape - the client branches on data the packet itself carries | 90 |
| control flow we could not follow (virtual call, mostly `CUser::Init`-style bodies) | 55 |
| the trace hit a budget or timeout | 14 |

The 90 multi-shape ones are the interesting frontier: `SERVERMESSAGE`, `SHOW_STATUS_INFO`,
`GUILD_OPERATION`, `PARTY_OPERATION`, `SHOW_FOREIGN_EFFECT` and the rest of the mode-byte family all
have a clean jump table whose arms this tool already enumerates. What is missing is attributing an
arm to its mode value so the model can be stated as "mode 5 is 12 bytes" - the server pins the mode
at every call site, exactly the way it pins `mod` for `DROP_ITEM_FROM_MAPOBJECT`. That is the next
piece of work and it is where most of the remaining coverage lives.

`MOVE_PLAYER` stays un-modellable for the reason already known: its v84 encoder is code-virtualised
(`00A1334E` jumps to `00DD1E03`) and cannot be read statically.

## 5. Cross-check and mutation matrix

`BinaryDerivedModelTest`:

- **cross-source agreement** - where an opcode is modelled by both the atlas-derived table and the
  independent binary walk, the two must describe the same byte count. They do. This is the assertion
  that makes the second table worth keeping: two unrelated derivations agreeing is evidence, and the
  day they stop agreeing one of them is wrong and the test says so instead of both passing quietly.
- **provenance is pinned** - the models must declare they came from the client image, so nobody can
  regenerate the second table from the atlas export and turn the cross-check into a tautology.

Mutation matrix, both directions, all six models (`shavingOneByteIsCaughtAsUnderSend`,
`appendingOneByteIsCaughtAsOverSend`):

| model | good | shave 1 byte | restored | append 1 byte |
|---|---|---|---|---|
| `AUTO_HP_POT` | OK, 4 body bytes | UNDER_SEND (error 38) | OK | OVER_SEND |
| `AUTO_MP_POT` | OK, 4 | UNDER_SEND | OK | OVER_SEND |
| `SET_GENDER` | OK, 1 | UNDER_SEND | OK | OVER_SEND |
| `CLAIM_STATUS_CHANGED` | OK, 1 | UNDER_SEND | OK | OVER_SEND |
| `SET_EXTRA_PENDANT_SLOT` | OK, 1 | UNDER_SEND | OK | OVER_SEND |
| `REMOVE_NPC` | OK, 4 | UNDER_SEND | OK | OVER_SEND |

## 6. What this still will not catch

- **Wrong values.** Byte counts and field widths only, same as ticket 40.
- **The 90 mode-dispatched opcodes**, which is where the packets a player touches constantly live.
- **Anything behind a virtual call**, which includes every packet whose body is an item record or a
  character look.
- **Re-derivation needs the client images.** `COSMIC_V84_IMAGE` must point at a memory dump of the
  running v84 client (the on-disk executable is packed, so its bytes are not the bytes that run);
  `COSMIC_V83_IMAGE` defaults to the shipped `localhome.exe`, read-only. Both output tables are
  committed, so the Java tests need neither.

## Files

- `tools/v84/binmodel/` - `images.py`, `reach.py`, `dispatch.py`, `cfgtrace.py`, `model.py`
- `tools/v84/derive-binary-models.py` - driver: `--selfcheck`, `--delta`, and table emission
- `tools/v84/decode-models-v84-binary.tsv` - generated, committed
- `src/test/java/tools/packetvalidator/BinaryDerivedModelTest.java`
- `src/main/java/tools/packetvalidator/PacketStructureModels.java` - second table + `loadAll`
