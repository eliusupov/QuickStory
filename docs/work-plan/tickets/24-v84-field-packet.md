# 24 — v84 `SET_FIELD` / `CharacterData` layout, and the Evan-only entry crash

**Status:** one change landed, awaiting live confirmation. **Confidence: moderate, not high — read §4.**
**Branch:** `worktree-evan-dualblade`.
**Symptom:** v84 client crashes on entering the world as Evan (job 2001). Explorer (job 0) on the same
map, same account, enters and plays.
**Verdict:** `SET_FIELD`'s general v84 layout is **correct and unchanged from v83** — I walked it
field-by-field and it matches, including one genuine discrimination win where v83 and v95 disagree.
The crash is in the **one job-conditional branch in the entire encode path**: the extended-SP field in
`addCharStats`. Cosmic writes 1 byte for a fresh Evan where the v84 client appears to want a 2-byte
short.

**The honest headline: this one is a deduction, not a measurement.** The IDA export — the instrument
that carried ticket 22 — is *blind at exactly this field*. §1.2 proves that rather than hiding it.

---

## 1. Prove the instrument first

### 1.1 What it is

Same artifact and same method as ticket 22: `D:\games\MSv84\opcodes\ida_export_gms_v84.json`, whose
`calls[]` is an ordered `CInPacket::Decode*` trace per handler with per-entry branch guards. Reproduce
any function with:

```
python -c "import json;d=json.load(open(r'D:\games\MSv84\opcodes\ida_export_gms_v84.json'));\
[print('%3d %-11s %-30s %s'%(i,c['op'],c.get('guard',''),c.get('ref','')))\
 for i,c in enumerate(d['functions']['CharacterData::Decode']['calls'])]"
```

The two functions in scope are `CStage::OnSetField` @0x798987 (18 calls) and `CharacterData::Decode`
@0x4edde5 (77 calls).

### 1.2 The limitation that matters most — stated before any result

**The instrument does not cover the field this ticket is about.** `CharacterData::Decode` index 7 is a
`Delegate` with `"ref": "GW_CharacterStat::Decode"`, and that function's own export entry is:

```json
"GW_CharacterStat::Decode": {
  "address": "", "direction": "", "unresolved": true,
  "calls": [{"op": "Unresolved", "comment": "function not found in IDB"}]
}
```

The entire char-stats block — id, name, look, level, job, the six stat shorts, **the SP field**, exp,
fame, map id — is a single opaque delegate. The same is true of the item body:
`GW_ItemSlotBase::Decode` @0x4ea719 is `Decode1` followed by
`"packet var passed to unresolved/indirect call; hand-trace"`.

**So the v84 export can adjudicate the *structure* of `CharacterData` but not the *contents* of the
char-stat block or the item record.** Both of this ticket's candidate bugs live in those two blind
spots. Everything in §4 is therefore reasoning from a live A/B, not a decode trace, and is labelled as
such.

### 1.3 I tried to remove the blind spot and failed — recorded so nobody repeats it

`D:\games\MSv84\client\MapleStory.exe` (4,824,872 bytes, 29 Mar 2010) is the real v84 client, so in
principle `GW_CharacterStat::Decode` could be disassembled directly. It cannot, from what is on disk:

```
imagebase 00400000  entry rva 00bc2000
(unnamed)  va=00401000 vs=00851000 ra=00001000 rs=002fb000   <- raw 0x2fb000 << virtual 0x851000
.rsrc      va=00c52000 ...
oyihhyms   va=00e40000 vs=00182000 ra=00307000 rs=00182000
fshekobw   va=00fc2000 ...   <- entry point lands here
qytrjskw   va=00fc3000 ...
```

Randomised section names, entry point outside the code section, code section compressed ~2.8:1. I
disassembled VA 0x798987 (`CStage::OnSetField`) with capstone anyway as a control and got noise
(`fadd`/`fdivrp`/`cwde`), confirming the section is packed on disk. There is **no unpacked dump** in
`D:\games\MSv84\` (`_cab` holds the two install cabs; `bypass\GMS-84.1` and `client\edits` hold only
loader DLLs). Whoever produced the IDA export worked from a dumped image that is not in this tree —
**if that IDB or dump can be found, it settles §4 in one lookup, and it is by far the highest-value
next artifact.**

### 1.4 The discrimination test the instrument *did* win

Ticket 22's standard: find a field where v83 and v95 disagree and see which one v84 sides with. There
is a clean one in this packet — the number of equip lists in the ITEMSLOT block:

| | equip lists / `short(0)` terminators |
|---|---|
| v83 (HeavenMS `MaplePacketCreator.java:486-529`, LucianMS `MaplePacketCreator.java:432-479`) | **4** — equipped, equipped-cash, equip-tab, dragon |
| v95 (Edelstein `CharacterPackets.cs:55-94`, Henesys `Char.java:186-237`, Rebirth95 `Character.cs:683-690`) | **5** — the above plus **Mechanic** (slots −1100..−1199) |
| **v84 export, `CharacterData::Decode`** | indices **15, 19, 23, 27** = **four** `Decode2` loop heads, then index 30 switches to the byte-positioned inventories → **sides with v83** |

All three v95 repos agree with each other, so this is not one repo's bug. v84 has four lists. **Cosmic
writes four** — `addInventoryInfo` (`PacketCreator.java:495-538`) has two `writeShort(0)` plus a
`writeInt(0)` at line 522, and that int is two fused terminators (list 3's, plus an always-empty list
4). Correct as-is. Nothing changed here.

That is the instrument siding with v83 where v95 differs, on this exact packet. Combined with ticket
22's three tests, the export remains trustworthy **for structure**. It says nothing about §4.

---

## 2. Measured v84 layout — `SET_FIELD 0x80` — **UNCHANGED from v83**

`CStage::OnSetField` @0x798987 vs `getCharInfo` (`PacketCreator.java:986-999`) and `getWarpToMap`:

| # | trace | field | Cosmic writes |
|---|---|---|---|
| 0 | Decode4 | nChannelID | `writeInt(channel - 1)` |
| 1 | Decode1 | nPortalCounter | `writeByte(1)` |
| 2 | Decode1 | **bCharacterData** (`v96`) | `writeByte(1)` |
| 3 | Decode2 | notification count (`v97`) | `writeShort(0)` |
| 4,5 | DecodeStr ×2 | *per notification* — guard `v97` | not sent (count 0) |
| 6,7,8 | Decode4 ×3 | crypto seeds — guard `v96` | three `writeInt(Randomizer)` |
| 9 | Delegate | **`CharacterData::Decode`** — guard `v96` | `addCharacterInfo` |
| 10–16 | 1,4,1,2,1,(4,4) | the `bCharacterData == 0` map-transfer branch | `getWarpToMap` |
| 17 | DecodeBuf | FILETIME | `writeLong(getTime(now))` |

Both branches line up. The outer packet is fine.

### 2.1 `CharacterData::Decode` @0x4edde5 — **UNCHANGED from v83**

Cosmic sets the flag mask to `writeLong(-1)` (`addCharacterInfo:236`), so every block is present.

| trace idx | guard | block | Cosmic |
|---|---|---|---|
| 0 | — | DecodeBuf = 8-byte flag mask | `writeLong(-1)` |
| 1 | — | `Decode1` → `v4`, extra-data flag | `writeByte(0)` |
| 2–6 | `v4` | extra blocks | not sent (`v4` = 0) ✓ |
| 7 | `&1` | **`GW_CharacterStat::Decode`** — *opaque, §1.2* | `addCharStats` |
| 8,9,10 | `&1` | buddy capacity, linked-name bool, linked name | lines 239-246 ✓ |
| 11 | — | `sub_4EA015` = one `Decode4` — money | `writeInt(meso)` ✓ |
| 12 | `++j > 5` | 5 × `Decode1` inventory slot limits | the `i = 1..5` loop ✓ |
| 13,14 | — | `Decode4` ×2 = 8 bytes | `writeLong(getTime(-2))` ✓ |
| 15–29 | `&4` | **four** equip lists — §1.4 | 4 terminators ✓ |
| 30–33 | `&v52` | byte-positioned inventories (use/setup/etc/cash) | `writeByte(0)` ×3 + skill-block leading byte ✓ |
| 34–39 | `&0x100` | skills: `Decode2` count, then id/level/expiry, + `Decode4` masterlevel under a skill-id predicate | `addSkillInfo`, incl. `isFourthJob()` ✓ |
| 40–42 | `&0x8000` | skill cooldowns | `addSkillInfo` tail ✓ |
| 43–46 | `&0x200` | quest records | `addQuestInfo` ✓ |
| 47–50 | `&0x4000` | completed quests | `addQuestInfo` ✓ |
| 51–58 | `&0x400` | minigame record (5 × `Decode4`) | `addMiniGameInfo` ✓ |
| 59–61 | `&0x800` | couple/ring records | `addRingInfo` ✓ |
| 62,63 | `&0x1000` | map transfer (teleport rocks) | `addTeleportInfo` ✓ |
| 64 | `&0x20000` | monster book cover | `addMonsterBookInfo` ✓ |
| 65–67 | `&0x10000` | monster book cards | ✓ |
| 68,69 | `&0x40000` | new year cards | `addNewYearInfo` ✓ |
| 70–73 | `&0x80000` | quest record ex / area info | `addAreaInfo` ✓ |
| 74 | `&0x100000` | trailing `Decode2` → 0, ends | `writeShort(0)` (line 258) ✓ |

Flag-bit names cross-checked against three v95 repos in exact agreement (`Rebirth95
DbCharFlags.cs:6-37`, `Edelstein DbFlags.cs:6-38`, `Henesys DBChar.java:7-36`).

**Every block Cosmic writes is read, in order, at the right width. The v84 field packet's structure is
v83's.** The only thing not verifiable this way is what is inside index 7.

> Guard caveat, same as ticket 22 §1.5: guards come from decompiler variable names, not a CFG walk.
> Indices 13/14 carry a `(v143 & 0x100000)` guard that is almost certainly hoisting noise — those 8
> bytes are Cosmic's unconditional `writeLong(getTime(-2))`, and **the live explorer proves they are
> consumed**, since everything after them parses. Worth knowing that guards here can be wrong.

---

## 3. The A/B that localises the bug

Supplied by the owner, and it is the strongest evidence in this ticket because it is a live measurement
rather than a static one:

```
chr 50  evan   job 2001  map 10000   -> crashes on entering the world
chr 49  uguuh  job 0     map 10000   -> enters, plays, server sees normal traffic
```

Same map, same account, same level, same item count. So: the map is exonerated, and every
**job-independent** field in `SET_FIELD` is correct at v84 — the explorer proves it end to end.

**The whole case then rests on one claim, which I verified rather than assumed:**

> In the entire `getCharInfo` encode path there is exactly **one** branch that depends on the
> character's job.

Verified by reading every function on the path — `addCharacterInfo`, `addCharStats`, `addCharLook`,
`addCharEquips`, `addItemInfo`, `addInventoryInfo`, `addSkillInfo`, `addQuestInfo`, `addMiniGameInfo`,
`addTeleportInfo`, `addAreaInfo`, `addMonsterBookInfo`, `addNewYearInfo`, and `addRingInfo`
(`PacketCreator.java:6786`, outside the main block) — and by grepping the range for every job
reference. The only hits in `PacketCreator.java:160-720` are:

```
line 203  p.writeShort(chr.getJob().getId());        // a value, not a branch
line 213  if (GameConstants.hasSPTable(chr.getJob()))  // <-- the only structural branch
line 361  p.writeInt(chr.getJobRank());              // charlist only, a value
```

The other Evan-only paths in the login sequence were checked and cleared:
`PlayerLoggedinHandler:407` already excludes 2001 from `createDragon()`, and the
`changeSkillLevel(10000000 * getJobType() + 12)` at line 364 sends a structurally identical packet for
both jobs (and runs *after* `getCharInfo`).

So the desync starts at `PacketCreator.java:213`.

### Why this crashes rather than merely glitching

The SP field sits **inside** the char-stat block, *before* exp, fame, gacha exp, **map id** and spawn
portal. A desync there corrupts the map id, so the client tries to load a garbage field — an entry
crash. A desync *after* the stat block would instead leave the player correctly on map 10000 with a
corrupted inventory. That distinction matters for §5. `[INFERRED]` — mechanism, not measurement.

---

## 4. The change, and exactly how confident I am

`GameConstants.hasSPTable` (`GameConstants.java:614`) is true for `EVAN(2001)` through `EVAN10(2218)`.
`addCharStats:213` branches on it: for a fresh Evan every SP slot is 0, so `addRemainingSkillInfo`
writes `effectiveLength = 0` and emits **one** byte. The explorer's else-branch emits **two**.

**Correcting the brief's Aran hint:** Aran is *not* the control. `LEGEND(2000)` and `ARAN1-4
(2100-2112)` are absent from `hasSPTable` in every repo checked, deliberately — Aran is not an
extended-SP class, only Evan is (Rebirth95 `JobLogic.cs:206`: `isAran(job) => job >= 2000 && job <=
2112 && job != 2001`). Cosmic's membership is exactly `job == 2001 || job/100 == 22`, which is
identical to LucianMS's `isEvanJob`, Rebirth95's `IsExtendedSPJob` and Edelstein's `IsExtendSPJob`.
There is no inherited-error smell in the job list.

### What the bracket says, and why I went against it

**All six reference repos include 2001 in the extended branch** (HeavenMS `GameConstants.java:630`,
LucianMS `MapleJob.java:128`, Edelstein `JobConstants.cs:32`, Henesys `JobConstants.java:12`, Rebirth95
`JobLogic.cs:212`). Taken at face value, "where v83 and v95 agree, v84 agrees" says Cosmic is right and
I should look elsewhere.

**I do not think the v83 half of that bracket is a witness.** No v83 client has Evan at all — job 2001
does not exist in v83 — so `hasSPTable`'s branch in HeavenMS/LucianMS/Cosmic is dead code that has
**never once been executed against a real client**. It was backported from a later source by the OdinMS
lineage. The bracket therefore collapses to the v95 side alone, eleven versions and a Big Bang later,
and the v95 side is visibly unreliable on this exact field:

- Rebirth95's two encoders **contradict each other** — `CharacterStat.cs:91-109` writes a fixed count
  of 11 with 0-based indices (23 bytes); `StatModifider.cs:526-544` writes a variable, 1-based count
  (1 byte for job 2001). At most one is right.
- Henesys's `isExtendSpJob` is **dead-branched** — `isThirdJob(j) && isAdventurerMage(j)` requires
  `j/1000 == 2` and `j ∈ {200..232}` simultaneously, which is unsatisfiable, so 2200–2218 wrongly take
  the short branch there.
- LucianMS's `encodeEvanSkillPoints` **hardcodes every SP value to 0** and never reads the array.
- `MapleResearch-v95-RE`, the one repo that might have held client ground truth, is a single
  HackShield README with nothing on `GW_CharacterStat`.

Four of the six produce exactly one `00` byte for job 2001 regardless; two produce 15 or 23 bytes. The
sources do not agree on this field in any useful sense.

Meanwhile both of the sources that *compute a slot count* say job 2001 has **zero** SP slots
(LucianMS `nJobLevel == 0`; Rebirth95 `GetExtendedSPIndexByJob(2001) == 0`) — a job with no growth
stages and no SP pools. And Cosmic itself already treats 2001 as not-really-SP-table in two places:
`Character.java:1263` and `PlayerLoggedinHandler:407` both read `hasSPTable(job) && job != 2001`.

So: the deduction in §3 says the branch is taken the wrong way for 2001; the source bracket is too
noisy to overrule it; the client cannot be read. I took the deduction.

### The edit

`src/main/java/tools/PacketCreator.java` — one new private predicate plus two call-site swaps:

```java
private static boolean writesExtendedSp(Character chr) {
    if (ServerConstants.VERSION >= 84 && chr.getJob().getId() == 2001) {
        return false;
    }
    return GameConstants.hasSPTable(chr.getJob());
}
```

used at `addCharStats` (was line 213) and at the stat-delta path (was line 1042).

- **Deliberately not a change to `GameConstants.hasSPTable`.** That predicate also drives SP granting
  (`Character.java:1153`, `:1263`), dragon creation (`PlayerLoggedinHandler:407`) and SP range
  enforcement (`Character.java:6281`). Changing it would alter gameplay and v83 behaviour. The packet
  layer gets its own predicate.
- **Both packet call sites go through it**, per the brief — a mismatch between them would desync
  mid-game instead of at login, which is worse than the bug being fixed.
- **Only job 2001 is affected.** 2200–2218 are untouched: unreachable today (Evan can't reach level 10
  yet) and I have no evidence about them either way. Deliberately not "fixed" on a guess.
- **Version-gated on `ServerConstants.VERSION >= 84`**, the same field that drives the hello packet and
  both cipher keys, exactly as ticket 22. **At VERSION 83 the emitted bytes are unchanged.**

### The failure mode, stated plainly

Unlike ticket 22's fix, **this one is not fail-safe.** That fix appended 8 bytes at the end of a packet,
where surplus is ignored. This field is in the *middle* of the packet, so too many bytes desyncs just
as badly as too few — there is no safe direction and no reason to "pad and see".

What that costs is bounded: if the v84 client really does use the table at job 2001, it wants 1 byte
(count 0) and now gets 2, and **the symptom will be an unchanged Evan entry crash, not a new failure,
and not a regression for the explorer.** One launch decides it. If it still crashes, the next thing to
try is not another guess at this field but finding the IDB/dump from §1.3.

---

## 5. The second report — "explorer is in game with no items visible"

**Not fixed, and I am not confident it is a packet bug at all.** What I can say:

- The equip **list structure** is measured correct — §1.4, a discrimination test the export won. Four
  lists, four terminators, matching what Cosmic sends. This is not where it is wrong.
- The equip **item body** is in the second blind spot (§1.2, `GW_ItemSlotBase::Decode` unresolved). v83
  and v95 differ by **+16 bytes** per non-cash equip there — v95 inserts `nIUC`, `nGrade`, `nCHUC`,
  three option shorts and two socket shorts, and makes the trailing `liSN` conditional on *not* cash.
  Those are the **potential and star-force systems**, which post-date v84 (Big Bang, GMS ~v95). So the
  prior strongly favours v84 using the v83 record — but I cannot prove it.
- If the item body *were* wrong, the desync starts after the stat block, so the player would still
  spawn correctly on map 10000 with a corrupted inventory — which does fit the symptom.

**Before anyone writes code for this, run the free experiment that discriminates it.** With the
explorer in game, check the **skill window and the quest list**. Those blocks are serialised *after*
the equips in the same packet:

- Skills/quests also broken → the packet desynced at the equip records → it is a real layout bug and
  the item body is the place to dig.
- Skills/quests fine → the packet did **not** desync, the inventory was parsed correctly, and "no items
  visible" is a rendering/WZ problem (the equipped ids are `1040006/1060002/1072005/1312004` and
  `1042180/1060138/1072418/1302132`) — i.e. the other agent's `docs/wz-baseline/` work, not this
  packet.

I did not touch it, because guessing at a 16-byte insertion in the middle of a working explorer's field
packet would risk breaking the one character that currently works.

---

## 6. Proof, as far as this goes

- Instrument re-validated on this packet before use, including one discrimination test where the three
  v95 repos agree with each other and the v84 export sides with v83 (§1.4).
- **Instrument's blind spots identified and published up front** (§1.2), plus a failed attempt to remove
  them recorded so it is not repeated (§1.3).
- `CStage::OnSetField` and all 77 entries of `CharacterData::Decode` walked against Cosmic
  field-by-field (§2). One structural mismatch expected, none found — the packet's shape is v83's.
- The load-bearing "only one job-conditional branch" claim verified by reading every encoder on the
  path plus a grep, not assumed (§3).
- Six reference repos bracketed on both the SP predicate and the inventory layout; three of them found
  to carry bugs on this exact field, which is why the bracket was not treated as decisive (§4).
- `mvnw.cmd -o test`: **2090 passed, 0 failed** — baseline held exactly.
- Compiles clean (`package -DskipTests`). **Not deployed.** Running server untouched,
  `ServerConstants.VERSION` left at 84, no jar built into place.

**No unit test added**, same call as ticket 22 §5: `writesExtendedSp` is private and takes a
`Character`, which needs `Server.getInstance()` plus the DB to construct. There is no PacketCreator
harness in `src/test` to hang it on, and a mock for one three-line job-id comparison would be more code
than the fix while asserting only that the branch I just wrote is the branch I just wrote. The real
check is the owner's next launch.

---

## 7. Unresolved — read before trusting

1. **§4 is a deduction, not a measurement.** `GW_CharacterStat::Decode` is absent from the v84 IDA
   export, and the shipped exe is packed, so the discriminator could not be read. The deduction is
   sound only if §3's "exactly one job-conditional branch" claim is complete — that is the thing to
   re-check first if the fix does not take.
2. **Finding the IDB or unpacked dump behind `ida_export_gms_v84.json` would settle both §4 and §5
   outright.** It is not in `D:\games\MSv84\`. Highest-value missing artifact by a wide margin.
3. **Jobs 2200–2218 deliberately untouched.** If Evan's first growth stage also crashes on relog once
   the owner can reach level 10, this predicate is where to look — but there is no evidence today, and
   the honest move was not to guess at ten more job ids.
4. **The item-record body (§5) is unmeasured and unchanged.** Run the skill/quest-window check before
   spending anything on it.
5. **The count semantics of the extended-SP form are unknown for v84** even for jobs where it clearly
   applies. The repos give 1, 15 and 23 bytes for the same character. Ticket 34 (`quest-sp-reward`) and
   Evan progression will hit this; it needs the dump, not more source reading.
6. **Not examined:** everything sent after `getCharInfo` in `PlayerLoggedinHandler` — keymap, macros,
   buddy list, family, guild — beyond confirming none of them branch on job. If a *later* packet turns
   out to be the crash, `getCharInfo` has been cleared by §2 and the search should start there.
