# 32 — the v84 client crashes when the player attacks a monster: the clientbound combat family

**Status:** delivered 2026-08-16. One fix landed, one delta found and deliberately not fixed, the rest
of the family measured clean. Suite green. Not deployed; running server untouched;
`ServerConstants.VERSION` still 84; the v83 path is byte-identical by construction.

## The symptom

```
21:59:25.896  Received packet id 44   (0x2C CLOSE_RANGE_ATTACK)
21:59:26.530  Received packet id 229
21:59:26.535  Attempting to save chr uguuh     <- client disconnected
```

No server-side exception. The earlier `NullPointerException: … "skill" is null` at
`AbstractDealDamageHandler.java:656` is gone, so ticket 25's serverbound attack head (+24 bytes of dr
words, +48 for magic — `6c55b036a`) is parsing correctly. The client dies on something we send back.

## Answer in one line

`PacketCreator.moveMonster` — the clientbound `MOVE_MONSTER` broadcast (`CMob::OnMove`) — is **8 bytes
short at v84**, and the *only* thing in the whole codebase that broadcasts it to the attacker himself is
`Monster.refreshMobPosition()`, whose *only* caller is the distance check inside the attack handler.

---

## 1. Was the clientbound combat family ever compared? No.

Ticket 25's family 4 ("Skills / buffs / damage") recorded a v83-agreement check on: `skillBookResult`,
the **serverbound** attack head, `character_temporary_stat.go`, `BuffGive`/`BuffCancel`. Its three
damage deltas (#8 attack head, #9 summon attack, #10 `MOVE_LIFE`) are **all serverbound**. Not one
clientbound combat writer — not the attack broadcast, not `damagePlayer`, not mob damage/HP/death, and
not clientbound `MOVE_MONSTER` — appears in its comparison table.

So the "no delta here" verdict never covered this family. Same shape as ticket 31: ticket 25 exonerated
the packet layer for quest 1021 while the scripted-conversation family sat outside all six of its
compared families, and ticket 31 then found a real, wide bug there.

## 2. Proving the instrument first

### 2a. The coarse-export convention, derived and then validated

Ticket 25 established that atlas's v84 IDA export is unannotated and manufactures phantom deltas, and
used one discriminator: *a difference at v84 that vanishes at v87 is an artifact.* That is a heuristic.
Reading **all nine** exports (v48 → v95) instead of three gives a stronger and checkable rule:

The exports split into two families.

| family | versions | behaviour |
|---|---|---|
| **annotated** | v83, v87, v95 | per-field comments; include the pool-level leading `Decode4` (the mob id read by `CMobPool::OnMobPacket`) and the trailing `DecodeBuf` |
| **coarse** | v48, v61, v72, v79, **v84**, v92 | no comments; **omit** the leading pool-level read and the trailing buffer; duplicate call sites from inlined branches |

That convention is not assumed — it is **validated on versions where the answer is already known**:

```
CMob::OnMove       v79 coarse  1,1,1,4                     == v83 annotated  [4],1,1,1,4,[Buf]   ✓
CMob::OnMove       v92 coarse  1,1,1,1,4,4,4,4,4,4         == v87 annotated  [4],1,1,1,1,4×6,[Buf] ✓
CMob::OnDamaged    v84 coarse  1,4,4,4                     == v83 annotated  [4],1,4,4,4         ✓
CMob::OnHPIndicator v84 coarse 1                           == v83 annotated  [4],1               ✓
CMob::OnCtrlAck    v84 coarse  2,1,2,1,1                   == v83 annotated  [4],2,1,2,1,1       ✓
```

A convention that reproduces four v84 packets we already know are unchanged, and reproduces v92 from
v87 exactly, has earned the right to be applied to the fifth. This is the ticket-31 discipline
(eight-for-eight before believing the ninth), applied to the export format rather than to a field.

### 2b. Cosmic's v83 encoders vs atlas's v83, field for field

| packet | Cosmic | atlas / v83 IDA | verdict |
|---|---|---|---|
| `moveMonster` | `int oid, byte 0, bool skillPossible, byte skill, byte skillId, byte skillLevel, short pOption, pos, moves` | `Decode4 mobId, Decode1 bNotForceLandingWhenDiscard, Decode1 bNextAttackPossible, Decode1 bLeft, Decode4 sEffect.m_Data, DecodeBuf movement` | **exact** — Cosmic's `skillId+skillLevel+pOption` is the 4-byte `sEffect.m_Data` (LOBYTE=skillId, BYTE1=level) |
| `showMonsterHP` | `int oid, byte pct` | `Decode4 mobId, Decode1 nHPpercentage` | exact |
| `damageMonster` | `int oid, byte 0, int damage` | `Decode4 mobId, Decode1 damageType, Decode4 damage` | exact |
| `killMonster` | `int oid, byte animation` | `Decode4 mobID, Decode1 destroyType` (+`Decode4` only when destroyType==4, which Cosmic never sends) | exact |
| `addAttackBody` | `int cid, byte packed, byte 0x5B, byte slv, [int skill], byte display, byte direction, byte stance, byte speed, byte 0x0A, int projectile, …` | `Decode1 packed, Decode1 level, Decode1 nSLV, [Decode4 skillId], Decode1 option, Decode2 (bLeft<<15\|nAction), Decode1 nActionSpeed, Decode1 nMastery, Decode4 nBulletItemID, …` | exact — the `0x5B` "?" byte is the character-level byte; `direction`+`stance` are the little-endian halves of the `nAction` short |
| `getShowExpGain` | mode 3 body | `CWvsContext::OnMessage#IncreaseExperience`, 16 reads | exact |

Every v83 encoder in the family reproduces the v83 client. The instrument agrees with us everywhere we
already work.

## 3. The delta, and where it reaches the attacker

### 3a. `CMob::OnMove` — clientbound `MOVE_MONSTER`

Normalised through the §2a convention (leading mob id and trailing movement buffer removed):

```
v83 @0x66be61   3 x Decode1 + 1 x Decode4      no blocks, no bNotChangeAction
v84 @0x6820ea   3 x Decode1 + 6 x Decode4      BLOCKS PRESENT, still no bNotChangeAction
v87 @0x6a6cb3   4 x Decode1 + 6 x Decode4      blocks + bNotChangeAction
v92             4 x Decode1 + 6 x Decode4      as v87
v95 @0x6521e0   4 x Decode1 + 6 x Decode4      as v87
```

The five extra `Decode4` at v84 are exactly what two count-prefixed blocks generate: `nMultiTargetForBall`
(int count, then count × {int x, int y}) and `nRandTimeForAreaAttack` (int count, then count × int) —
v87's export names them one by one. **8 bytes on the wire when both counts are zero, which is the
normal case.**

The serverbound mirror moves in lockstep: `CMob::GenerateMovePath` goes 10 → 14 across v83 → v84, the
same +5 `Decode4` — and that is the delta **ticket 25 already trusted and landed** as
`MoveLifeHandler.skipV84MobMoveExtras`. Cosmic now *reads* the two blocks off the wire at v84 and never
*writes* them back. The read side was fixed; the write side was not.

### 3b. Where this contradicts atlas, and why atlas is the one that is wrong

atlas gates the two blocks at **`>= 84` serverbound** (`monster/serverbound/movement.go`) and at
**`>= 87` clientbound** (`monster/clientbound/movement.go:57-89`), with the comment
`"v87+ fields; v84..86 == v83 (off-by-one fix)"`. Someone deliberately moved the clientbound gate up.

That is the exact failure mode atlas's own `docs/packets/audits/VERIFYING_A_PACKET.md` §4 warns about:
*"beware the v84 off-by-one class."* Ticket 25 §5 already recorded the corollary — **some of atlas's
`>=87` gates are probably still wrong and should be `>=84`** — and listed four gates atlas had itself
corrected in the other direction. This is the fifth, found by measurement rather than by argument:

- the v84 export shows six `Decode4` where v83 shows one, under a convention validated on four other
  v84 packets and on v92;
- the same +5 appears on the serverbound side, where **atlas itself gates at 84**;
- a wire field cannot plausibly be added at 84, removed at 87 and re-added at 92 — but atlas's two
  gates require exactly that asymmetry between the two directions of the *same* struct.

atlas's own v84 audit (`audits/gms_v84/MonsterMovement.md`) is ❌ and carries a `Flat-diff-invalid`
cap, so it neither supports nor refutes; it is consistent with the writer being 8 bytes short.

### 3c. Why this is the packet that reaches a solo attacker

`MOVE_MONSTER` has two senders in Cosmic:

```java
// MoveLifeHandler.java:170  — excludes the source (the controller), i.e. never the attacker
map.broadcastMessage(player, PacketCreator.moveMonster(...), serverStartPos);

// Monster.java:1421 (resetMobPosition) — NO source argument: every player in the map, attacker included
map.broadcastMessage(PacketCreator.moveMonster(this.getObjectId(), false, -1, 0, 0, 0,
        this.getPosition(), this.getIdleMovement(), IDLE_MOVEMENT_PACKET_LENGTH));
```

`resetMobPosition` is reached only through `refreshMobPosition()`, and `refreshMobPosition()` has
**exactly one caller in the entire source tree**:

```java
// AbstractDealDamageHandler.java:253-256   (inside applyAttack, per attacked monster)
if (distance > distanceToDetect) {
    AutobanFactory.DISTANCE_HACK.alert(player, "Distance Sq to monster: " + distance + …);
    monster.refreshMobPosition();
}
```

So: attack a monster the server thinks is out of range → the map broadcasts, **to the attacker
himself**, a `MOVE_MONSTER` that is 8 bytes shorter than the v84 client's decoder expects → the client
under-runs the packet → `ZException (error code : 38 (Reached the end of the file.))` → silent
disconnect. Server-side: nothing thrown, because nothing on our side went wrong. That is the whole
observed signature.

It also explains the shape of the failure: it fires on **attack** and nothing else, it needs no second
player, and it is not deterministic per swing — it needs the server's mob position to have drifted past
the detect radius, which is why the crash trails the attack rather than landing on it.

**Check that costs the owner nothing:** the same code path logs a `DISTANCE_HACK` /
`"Distance Sq to monster: … MID: …"` line for `uguuh` at 21:59:25.896. If that line is in tonight's log,
this is confirmed end to end.

## 4. What was landed

`tools/PacketCreator.java` — `writeV84MobMoveExtras(OutPacket)`, called from `moveMonster` between the
packed skill data and the start position:

```java
if (ServerConstants.VERSION < 84) return;
p.writeInt(0);  // nMultiTargetForBall count
p.writeInt(0);  // nRandTimeForAreaAttack count
```

`bNotChangeAction` is a genuine **87** field and is deliberately **not** written.

**ponytail:** the counts are written as 0 rather than echoed from the inbound `MOVE_LIFE`. Non-zero
counts only occur for mobs performing ball/area attacks, and `resetMobPosition` has no inbound packet to
echo at all. Thread the raw block through `MoveLifeHandler` if a ball-attack mob ever renders wrong for
an observer.

**Check:** `src/test/java/tools/MoveMonsterPacketTest.java` pins the body byte-for-byte and asserts the
8 extra bytes at `VERSION >= 84` and their absence below. It fails if the fix is reverted or moved.

**Failure direction, stated up front.** If this reading is wrong, the only packet affected is one that
today reaches a solo player exclusively through the distance-check path — which is already broken under
either reading — plus the observer rebroadcast, which reaches nobody when the owner tests alone. It is
not on the login, character-select or field-entry path, so **it cannot move a crash earlier** the way
the SP guess did.

## 5. Ruled out — measured, not assumed

Every clientbound packet Cosmic can send to a *solo* attacker between the attack and the kill, checked
across all nine exports:

| packet | client fn | v83 vs v87 | verdict |
|---|---|---|---|
| `SHOW_MONSTER_HP` | `CMob::OnHPIndicator` | identical | **no v84 delta** (v84's single read is the coarse convention) |
| `DAMAGE_MONSTER` | `CMob::OnDamaged` | identical | no v84 delta |
| `KILL_MONSTER` | `CMobPool::OnMobLeaveField` | identical | no v84 delta |
| `SPAWN_MONSTER` / `SPAWN_MONSTER_CONTROL` | `CMobPool::OnMobEnterField` / `OnMobChangeController` | identical | no v84 delta |
| `MOVE_MONSTER_RESPONSE` | `CMob::OnCtrlAck` | identical | no v84 delta |
| `APPLY_/CANCEL_MONSTER_STATUS` | `CMob::OnStatSet` / `OnStatReset` | identical | no v84 delta |
| `DROP_ITEM_FROM_MAPOBJECT` | `CDropPool::OnDropEnterField` | identical | **artifact confirmed, see below** |
| `STAT_CHANGED` | `CWvsContext::OnStatChanged` | identical (25 reads both) | no v84 delta |
| `SHOW_STATUS_INFO` exp gain | `CWvsContext::OnMessage#IncreaseExperience` | identical (16 reads at v83, v84 **and** v87) | no v84 delta |
| `GIVE_FOREIGN_BUFF` | `CUserRemote::OnSetTemporaryStat` | identical | no v84 delta |
| `SHOW_FOREIGN_EFFECT` | `CUser::OnEffect` | identical | no v84 delta (v84's 48 reads are dispatcher lumping) |

Three near-misses worth recording because each looked like a delta and is not:

- **`CUserRemote::OnAttack`** (`closeRangeAttack` / `rangedAttack` / `magicAttack`, the ticket's first
  suspect). v84 shows one extra `Decode4` immediately after the damage loop. **Artifact.** The extra
  `Decode4` is present in *every* coarse export (v72, v79, v84, v92) and absent from *every* annotated
  one (v83, v87, v95) — a duplicated call site, not a field. The real change in the window is the
  `Decode1`+`Decode4` pair for skill 3211006 (Sniper), inserted at **v87**, present at v95, absent at
  v84. atlas's `character/clientbound/attack.go` carries 79/83/95 gates and **no 84 gate**. Conclusion:
  **the v84 attack broadcast is byte-identical to v83. Not changed.**
- **`CUserRemote::OnHit`** (`damagePlayer`). Same artifact class: the `Decode2,Decode2` pair that makes
  v84 look like 16 reads appears in all four coarse exports and in none of the annotated ones. The one
  real change is a `Decode1` "stance flags" byte added at **v87**. **Not changed at v84.**
- **`CDropPool::OnDropEnterField`** (drops on mob death — the right timing, and a trailing byte would be
  a perfect under-read). v84 and v92 carry an extra trailing `Decode1`; v48, v61, v72, v79, v83, v87 and
  v95 do not. A field cannot appear at 84, vanish at 87, reappear at 92 and vanish again at 95.
  **Artifact**, confirmed with better evidence than ticket 25 had. atlas's `drop/clientbound/spawn.go`
  has no version gate at any version.

Also checked and clean: Cosmic's `sendops-84.properties` against atlas's v84 clientbound registry —
**293 opcodes agree, and every combat opcode is among them** (`CLOSE_RANGE_ATTACK` 0xBE, `RANGED_ATTACK`
0xBF, `MAGIC_ATTACK` 0xC0, `DAMAGE_PLAYER` 0xC4, `SPAWN_MONSTER` 0xF2, `KILL_MONSTER` 0xF3,
`SPAWN_MONSTER_CONTROL` 0xF4, `MOVE_MONSTER` 0xF5, `MOVE_MONSTER_RESPONSE` 0xF6, `APPLY_MONSTER_STATUS`
0xF8, `DAMAGE_MONSTER` 0xFC, `SHOW_MONSTER_HP` 0x100, `DROP_ITEM_FROM_MAPOBJECT` 0x113,
`SHOW_STATUS_INFO` 0x27). The crash is not a mis-routed opcode.

## 6. Found, evidenced, and deliberately NOT fixed

### 6.1 `SHOW_STATUS_INFO` mode enum shifts +1 from index 4 at v84 — a real, wide bug, off this path

`CWvsContext::OnMessage` gains a new arm, `OnIncSPMessage`, at **case 4** in v84, pushing everything
from `INCREASE_FAME` down by one. atlas's `docs/packets/dispatchers/character_status_message.yaml` is
the declared source of truth and gives per-version switch addresses and case counts — gms_v83 `0xA209D4`
with **14** cases, gms_v84 `0xA6BDD9`, gms_v87 `0xAB8076`, gms_v95 `0xA06C90` with **15**. The IDA
exports confirm it independently: the `#IncreaseSkillPoint` arm exists at v84, v87 and v95 and is
**absent at v83**.

| meaning | v83 mode | v84+ mode | Cosmic writes | `PacketCreator` |
|---|---|---|---|---|
| drop pick-up | 0 | 0 | 0 | unchanged |
| quest record | 1 | 1 | 1 | unchanged |
| cash item expire | 2 | 2 | 2 | unchanged |
| **increase EXP** | **3** | **3** | 3 | **unchanged — this is why the kill path survives** |
| *(new)* increase SP | — | 4 | — | — |
| increase fame | 4 | **5** | 4 | `getShowFameGain` |
| increase meso | 5 | **6** | 5 | `getShowMesoGain(_, inChat=true)` |
| increase guild point | 6 | **7** | 6 | `getGPMessage` |
| give buff / item | 7 | **8** | 7 | `getItemMessage` |
| system message | 9 | **10** | 9 | three sites (~6389, 6717, 6941) |
| quest record ex | 10 | **11** | 10 | `updateAreaInfo` (already commented `//0x0B in v95`), 6710, 6763 |

This is the same class as ticket 31's NPC dialog-type `+1`, and it is real. It is **not** fixed here for
one reason: **it is not on the attack path** (EXP is mode 3 and quest progress is mode 1, both
unshifted), and `updateAreaInfo` is driven by quest scripts on field entry. Landing an unrelated
multi-site enum remap in the same test cycle as the combat fix would make tonight's result ambiguous —
which is the specific mistake this whole line of work exists to stop. It deserves its own ticket, its own
launch, and a `v84StatusInfoMode()` helper mirroring the existing `mapNpcDialogType` pair at
`PacketCreator.java:3419/3429`.

Two more shifts from the same sweep, recorded for whoever takes that ticket:
`CWvsContext::OnBroadcastMsg` (`SERVERMESSAGE`) shifts **+2** from mode 12 at v84
(`dispatchers/worldmessage.yaml`), and `CWvsContext::OnPartyResult` `TOWN_PORTAL` moves 37 → 40
(`dispatchers/party.yaml`).

### 6.2 Ten `sendops-84` opcodes disagree with atlas's current registry

None is combat, so none can be tonight's crash, but they are live risks and the disagreement is
one-directional (Cosmic is on an older snapshot; atlas's notes cite "task-100 … by READING the v84
dispatcher"):

| op | Cosmic | atlas v84 |
|---|---|---|
| `ALLIANCE_OPERATION` | 0x44 | 0x42 |
| `SERVERMESSAGE` | 0x46 | 0x44 |
| `QUICKSLOT_INIT` | 0xA2 | 0x9F |
| `SET_NPC_SCRIPTABLE` | 0x10E | 0x107 |
| `CANNOT_SPAWN_KITE` / `SPAWN_KITE` | 0x115 / 0x116 | 0x10E / 0x10F |
| `MTS_OPERATION2` / `MTS_OPERATION` | 0x165 / 0x166 | 0x15B / 0x15C |
| `MAPLELIFE_RESULT` / `MAPLELIFE_ERROR` | 0x167 / 0x168 | 0x15D / 0x15E |

`SERVERMESSAGE` is the one that will bite first — it carries every notice and every drop message. Four
more Cosmic send opcodes have **no atlas v84 entry at all** and are therefore unverified:
`ARIANT_THING` (0xFF), `MESO_BAG_MESSAGE` (0xFFFF, deliberately unresolved), `SERVERLIST` (0xA),
`SHOW_MAGNET` (0x103).

## 7. Could not be determined

- **Recv opcode 229 (0xE5), the last packet before the disconnect.** Cosmic's `recvops-84` maps it to
  `PARTY_SEARCH_UPDATE`; atlas's v84 registry puts `PARTY_SEARCH_UPDATE` at 0xDF (223) and leaves
  226/228/229/230 **unassigned**, and atlas's v84 handler template has no entry for it either. So we
  cannot say what the client sent. `PartySearchUpdateHandler` is registered and cannot disconnect, and
  no handler exception was logged, so it is very unlikely to be causal — most probably the client's last
  write before the socket closed. Worth capturing properly rather than guessing: `MOVE_LIFE` is in
  `LoggingUtil.ignoredDebugRecvPackets`, so the 634 ms between the attack and the disconnect is full of
  unlogged mob-movement traffic that this log simply does not show.
- **Whether the owner was alone in the map.** If he was not, the same `moveMonster` under-write reaches
  every observer through `MoveLifeHandler:170` as well, which would make the fix cover both routes.
- **`CTradingRoomDlg::PutItem`** — still the unresolved v84 delta ticket 25 §4.2 named; untouched.
- The blind spot ticket 25 named remains: the mob record inside `SPAWN_MONSTER` /
  `MOB_CHANGE_CONTROLLER` is a `Delegate`/`DecodeBuf` in every export, so no trace covers its body. It
  is *circumstantially* clean — the owner enters maps and sees mobs — but it is not measured.

## 8. If the next launch still crashes on attack

In order, cheapest first:

1. **Grep tonight's log for `Distance Sq to monster`** around the crash timestamp. Present → §3c is
   confirmed and the fix is the right one. Absent → `resetMobPosition` never ran, the fix is correct but
   inert, and the cause is elsewhere.
2. **Capture `CLIENT_START_ERROR` (recv `0x19`) on the next connect.** It carries the client's own crash
   text; `ZException (error code : 38 …)` confirms an under-read and rules the whole class of
   content/WZ problems out.
3. **One discriminating click, if it is still alive:** attack a monster **with nothing else on screen**
   versus attack **while standing still next to a monster that has not moved since spawn**. The second
   cannot trip the distance check. If only the first crashes, §3c is proven behaviourally without any
   log at all.
4. Only then reach for a hex capture: run a private server on other ports with
   `USE_DEBUG_SHOW_PACKET: true` and replay.
