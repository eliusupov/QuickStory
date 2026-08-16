# 24 — v84 `SET_FIELD` / `CharacterData` layout, and the Evan-only entry crash

> ## ✅ RESOLVED IN §9 — the v84 equip record needs a 4-byte `nDurability`. Read §9 first.
>
> One line in `addItemInfo`, version-gated. §1–§8 are the investigation that got there, including two
> wrong turns kept on the record. **§9 is the answer; §10 is what is still open.**

> ## ⚠ ROUND 1 WAS WRONG — READ §8 FIRST
>
> The fix in §4 was **disproved by the live client** and reverted in `b2eed322c`. Job 2001 *does* use
> the extended ten-slot SP table; all six reference repos were right and my "the v83 half of the bracket
> is dead code" argument was wrong. §1, §2 and §3's *structural* findings still stand and are reused in
> §8 — but §4's conclusion, and §3's premise that "the explorer proves every job-independent field is
> correct", are both retracted. **§8 is the current state of this investigation.**

**Status:** round 1 reverted; round 2 changed no code — the remaining candidate is unmeasurable and the
evidence does not support a guess. **Read §8.**
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

---

# 8. Round 2 — what the live client actually said, and where that leaves it

**No code changed this round.** Every field in `SET_FIELD` is now either verified or excluded except
one, that one is unmeasurable with anything on disk, and the indirect evidence points *away* from it
being wrong. Guessing again would cost a launch and could make things worse, as round 1 did.

## 8.1 Two measurements from the owner

**(a) The round-1 fix was disproved.** With job 2001 writing a plain SP short, the client crashed on
**character select** — one step *earlier* than the bug it targeted, because `CHARLIST` runs every
character through the same `addCharStats` (`addCharEntry:348`). Reverting restored it immediately.

**(b) The explorer is not actually working.** > *"i cant go through portal with my explorer, 0 quests,
not items, nothing"* — it spawns and moves, but no inventory, no quests, portals dead.

My round-1 prediction that a wrong guess here would be "an unchanged Evan crash, not a new failure" was
wrong too. **A mid-packet field is not fail-safe in either direction.** Recorded because I argued the
opposite in §4.

## 8.2 What (a) proves, which is more than it looks

Reverting was the right call, but the experiment also **positively confirms** two things:

1. **Job 2001 uses the extended table** — excluded by experiment, not argument. Do not re-try it.
2. **The extended-SP encoding itself is correct.** With 1 byte (count 0) character select works; with
   2 bytes it crashes. Had the v84 client wanted a *fixed-width* SP array — the live hypothesis in §7.5,
   since the repos give 1, 15 and 23 bytes for this character — then 1 and 2 would *both* be short and
   both would crash. Only the 1-byte form works, so v84 reads a variable count byte, exactly as Cosmic
   writes it. **§7.5 is now resolved: the variable form is right.**
3. Because character select renders the Evan through `addCharStats` + `addCharLook` without crashing,
   **the Evan's entire char-stat block parses correctly.** So the Evan entry crash is in a block that
   `CHARLIST` does not contain — i.e. *after* the stat block.

## 8.3 A lead I found, validated, and killed

Worth recording because it looked decisive and was not. `CharacterData::Decode` index 10 is
`DecodeStr` with `guard: "(v143 & 1) != 0 && CInPacket::Decode1(a2)"` — a guard that *contains a decode
call*. Read naively that means the client reads buddy-capacity (idx 8), a byte (idx 9), **and a third
byte inside the `if`**, where Cosmic writes only two — a 1-byte deficit landing exactly at the boundary
between "works" and "broken". It explained every symptom.

It is wrong. 149 guards in the export contain an inline decode, and three of them sit on packets whose
layout is known:

| function | trace | actual packet |
|---|---|---|
| `CWvsContext::OnMonsterBookSetCard` | `[0] Decode1`, `[1][2] Decode4` guarded by inline `Decode1` | `writeByte, writeInt, writeInt` — 9 bytes |
| `CField_Tournament::OnTournamentSetPrize` | `[0][1] Decode1`, `[2][3] Decode4` guarded by inline `Decode1` | two bytes then two ints |
| `CUserRemote::OnPetActivated` | `[0][1] Decode1`, `[2] Decode1` guarded by inline `Decode1` | — |

In every case the inline-guard decode is a **textual duplicate of the immediately preceding listed
entry**, not an extra read. If it were an extra byte, `OnMonsterBookSetCard` would want 10 bytes for a
9-byte packet that demonstrably works.

**So index 9 *is* the linked-name bool, and Cosmic's two bytes are correct.** Adding this to §1.5's
list of instrument quirks: *a guard containing `CInPacket::Decode*` re-states the preceding entry; it
never adds a read.*

## 8.4 Where the desync must be, by elimination

| region | status |
|---|---|
| `OnSetField` header (channel, portal, bCharacterData, notices, 3 seeds) | verified §2 |
| flag mask, extra-data byte | verified §2.1 |
| **char-stat block** | **verified by experiment** §8.2 |
| buddy capacity, linked-name bool + string | verified §8.3 |
| money, 5 slot limits, 8-byte FILETIME | verified §2.1 |
| four equip lists / terminators | verified, **discrimination win** §1.4 |
| **equip + bundle item bodies** | **BLIND — the only candidate left** |
| byte-positioned inventories, skills (incl. masterlevel predicate), cooldowns, quests, completed quests, minigame, rings, teleport, monster book, new year, area info, trailing short | verified §2.1 |

One candidate remains: **`GW_ItemSlotBase::Decode`**. It is `"unresolved"` in the export at *every*
call site — `CharacterData::Decode` indices 16/20/24/28/31 and `CWvsContext::OnInventoryOperation`
index 5 all delegate to the same unreadable function — and the client is packed (§1.3). **It cannot be
measured with anything in this tree.**

## 8.5 The two candidate layouts, and why I did not pick one

Verified myself this round, not via a summary. Cosmic's `addItemInfo` (`PacketCreator.java:401-493`) is
**byte-identical to HeavenMS v83 upstream** (`MaplePacketCreator.java:392-484`) — no local drift. For a
non-cash equip, from `writeShort(flag)` to the end:

| | fields | bytes |
|---|---|---|
| **A — v83, what Cosmic sends** | `byte 0`, `byte itemLevel`, `int exp`, `int vicious`, `long 0`, `long ftEquipped`, `int -1` | **30** |
| **B — v95** (`Rebirth95 GW_ItemSlotEquip.cs:236-259`) | `byte nLevelUpType`, `byte nLevel`, `int nEXP`, `int nDurability`, `int nIUC`, `byte nGrade`, `byte nCHUC`, `short nOption1-3`, `short nSocket1-2`, *(`long liSN` if not cash)*, `long ftEquipped`, `int nPrevBonusExpRate` | **38 or 46** |

**Every field B adds is a post-v84 feature**: `nGrade`/`nOption*` are the potential system and `nCHUC`
is star force, both introduced with Chaos (GMS ~v0.95, Aug 2011); v84 is Mar 2010 and pre-Big Bang. So
the prior strongly favours **A**, which is what Cosmic already sends.

**A second argument against B, from the live client.** If v84 wanted B, each of the explorer's four
equips would be 8–16 bytes short, a cumulative 32–64 byte deficit. A deficit makes `CInPacket` read
past `m_uLength` and throw — the client would **crash**, which is exactly what the Evan does and
exactly what the explorer does *not*. The explorer surviving to a playable state is evidence the packet
was **long enough**, i.e. the item records are not under-sized.

Which leaves the uncomfortable possibility that **`SET_FIELD` is fine and the explorer's symptoms are
not a packet bug at all.**

## 8.6 The competing explanation, which is documented and in another lane

All three explorer symptoms are also textbook client-data symptoms:

- *"0 quests"* — a freshly created character genuinely **has** zero quest records; `addQuestInfo` writes
  two zero counts and that is correct. The quest *window* is populated from the client's `Quest.wz`.
- *"can't go through portal"* — portals come from the client's `Map.wz` and are validated against the
  server's map data. Nothing in `SET_FIELD` carries them.
- *"no items"* — ambiguous between "inventory is empty" (packet) and "items present but not rendering"
  (WZ).

And this repo already documents the merge as incomplete — `EvanCreator.java:36-38`:

> *"Evan's own v84 tutorial maps are not installed yet (Map.wz v84 rows are still unmerged), so starting
> there would strand the character in a map the client cannot load."*

Both characters are on `MUSHROOM_TOWN` (10000). If the server's map XML is v83 and the client's `Map.wz`
is v84, portal names/indices can disagree and portals stop working with a perfectly good field packet.
That is `docs/wz-baseline/` + `wz/` — **another agent's lane, deliberately not touched.**

## 8.7 The one question that decides it

Zero cost, ten seconds, no launch needed — the owner is already in game with the explorer:

> **Open the inventory and look at the ETC tab. Is "Beginner's Guide" (4161001) there, in slot 1?**

The ETC inventory is serialised *after* all four equip lists and after the USE and SETUP lists. For that
item to appear with the right name in the right slot, the client must have walked every equip record at
exactly the right size and landed on the ETC list — which is only possible if the item body layout is
correct.

- **Guide is there** → `SET_FIELD` parsed end to end. The item body is layout **A**, the packet is fine,
  and "no items / 0 quests / dead portals" is client-data (`Map.wz`/`Quest.wz`/`Character.wz`), i.e. the
  WZ lane. Stop looking at `PacketCreator`.
- **ETC tab empty or garbage** → the parse desynced at or before the equips. The item body is the only
  unverified field left, so layout **B** (or some v84 variant) is real, and the next step is to find the
  IDB/unpacked dump (§1.3) rather than to guess which of B's fields v84 has.

Two supporting reads if the first is ambiguous — both are serialised *before* the equips, so they
bracket the desync: **the meso amount** (`writeInt(meso)`, immediately after the linked-name byte) and
**the inventory slot counts** (the five bytes after it). If those are correct but the ETC tab is not,
the desync starts precisely at the first equip record.

## 8.8 Still unresolved

1. **The Evan entry crash has no identified cause.** §8.2 clears the stat block; §8.4 clears everything
   else structural. If the ETC-tab check says the packet is fine, then the crash is *not* in
   `getCharInfo` at all and the search should move to the packets sent after it in
   `PlayerLoggedinHandler` (§7.6) — none of which branch on job, so the differing **data** (Evan's item
   ids, `SkillFactory.getSkill(20000012)` at line 364) would be the thing to look at.
2. **`GW_ItemSlotBase::Decode` and `GW_CharacterStat::Decode` remain unreadable.** The IDB or unpacked
   dump behind `ida_export_gms_v84.json` is still the single highest-value missing artifact.
3. **Why the Evan crashes where the explorer merely misbehaves.** The only byte-level difference between
   their field packets is the SP field: 1 byte (Evan, extended count 0) vs 2 (explorer). A shared
   downstream desync read at a 1-byte-different alignment can easily be fatal in one case and survivable
   in the other — but that is a mechanism, not evidence, and it is only relevant if the ETC-tab check
   comes back "empty".

---

# 9. RESOLVED — the equip record is 4 bytes short at v84

**Status: measured, fixed, one line.** The §8.7 check came back **"Beginner's Guide NOT in the ETC
tab"**, which put the fault at or before the equips — and a better instrument then named the field.

## 9.1 The finding

**v84's non-cash equip record carries a 4-byte `nDurability` field that v83's does not**, between
`experience` and `nIUC`/hammers. Cosmic writes the v83 shape, so every non-cash equip is **4 bytes
short**. A fresh character has **four** starting equips ⇒ the client under-runs by **16 bytes** and
everything after the first equip is garbage.

`src/main/java/tools/PacketCreator.java`, in `addItemInfo`'s non-cash equip branch:

```java
p.writeInt((int) expNibble);
if (ServerConstants.VERSION >= 84) {
    p.writeInt(-1);   // nDurability, -1 = no durability
}
p.writeInt(equip.getVicious());   // nIUC / hammers applied
```

**Cash equips deliberately do not get it** — their 10-byte `0x40` filler stands in for the whole
`levelType/level/experience/nIUC` group and is unchanged at v84. Verified against the reference below,
which gates the cash branch separately and adds no durability there.

`addItemInfo` is the single serialiser for every item-bearing packet in Cosmic (ticket 17 measured **29
call sites all routing through it**), so this one line fixes the field packet, inventory operations,
shops, storage and the cash shop together. Root-cause seam, not a patch on one path.

## 9.2 Where the measurement came from — a new instrument, and a good one

`Chronicle20/atlas` — a Go MapleStory server whose packet writers are **explicitly version-gated**
(`t.MajorAtLeast(84)`) with **IDA-verified comments citing client addresses**. It ships per-version
packet audits for **nine** GMS versions — `docs/packets/audits/gms_v{48,61,72,79,83,84,87,92,95}/` —
including `gms_v83/FieldSetField.json` and `gms_v84/FieldSetField.json`.

I found it by accident: a filesystem sweep for the v84 IDB turned up `scratchpad/atlas/tree.json`, a
GitHub tree listing of the repo left behind by earlier work.

The decisive artifact is `libs/atlas-packet/model/asset_v84_test.go`, a test that exists **because atlas
shipped this exact bug and had to fix it**:

> *"TestEquipableV84ExtraInt pins the v84+ equip durability field. v84's `GW_ItemSlotEquip::RawDecode`
> (a v95-era refactor v83 lacks) reads nDurability as an int between experience and hammersApplied;
> v83's older inline decode does not. atlas wrote the v83 layout for every version, so **a v84 client
> under-ran each equipped item by 4 bytes (×4 starting equips on a fresh character) → ZException →
> silent disconnect entering the channel.** The encoded equip must therefore be exactly 4 bytes longer
> for GMS v84+ than for v83."*

Four starting equips, disconnect entering the channel. That is our bug, described by someone who had
already measured it. And `libs/atlas-packet/model/asset.go:260-262`:

> `w.WriteInt32(-1) // nDurability (-1 = no durability): GMS v84+ equip field, ordered
> experience/durability/hammersApplied (GW_ItemSlotEquip::RawDecode +212; absent v83). IDA-verified.`

**This is not a third guess.** It is an independent implementation that (a) is version-parameterised
rather than pinned to one version, (b) cites client disassembly addresses per field, (c) has a
regression test pinning this specific delta, and (d) reports the identical symptom.

### Corroboration before trusting it

atlas's v83 equip encoder was walked field-by-field against Cosmic's `addItemInfo` and matches
**exactly**, including quirks Cosmic inherited from OdinMS: the `writeShort` slot for GMS≥83, the
`writeBool(isCash)`, the 15 stat shorts, the owner string + flag short, the trailing `writeLong(0)`,
and the permanent-FILETIME constant (atlas's literal `94354848000000000` is what Cosmic's
`getTime(-2)` produces). Its `encodeInventory` likewise reproduces Cosmic's inventory framing exactly —
5 capacity bytes, the 8-byte timestamp under flag `0x100000`, short terminators for GMS≥83, and the
`WriteInt(0)` that folds the empty 4th (dragon/mechanic) equip-loop terminator (§1.4's discrimination
win, independently confirmed). **A source that reproduces every field Cosmic already gets right, and
differs on exactly one, is a source worth believing on that one.**

## 9.3 Where my round-2 reasoning went wrong

§8.5 argued: *"a deficit makes `CInPacket` over-read past `m_uLength` and throw — the client would
crash, which the explorer does not; therefore the records are not under-sized."* The premise was right
(atlas confirms the under-run throws) but I applied it backwards — I treated **one** character
surviving as proof of no deficit, when the correct reading is that the deficit is real and the Evan's
crash *is* the throw. The explorer surviving is the anomaly, not the evidence.

**I still cannot fully explain why the explorer survived** where the Evan disconnected. Their packets
differ by exactly one byte (the SP field: 1 for the extended-table Evan, 2 for the explorer), so a
16-byte under-run lands on different garbage and reaches the buffer end at a different point. That is a
mechanism, not a measurement, and it is now moot — but it is the reason a symptom-shaped inference was
never going to settle this, in either direction.

The coordinator's surplus/truncation hypothesis was also wrong, for the same reason: it was inferred
from symptoms. The direction was **deficit**. Two rounds of symptom-reading, one lookup in a
version-gated reference — that is the lesson worth keeping.

## 9.4 Verification

- Reference cross-checked field-by-field against Cosmic before adopting (§9.2), not taken on the
  strength of one comment.
- Cash-equip branch checked separately and correctly excluded.
- `mvnw.cmd -o test`: **2090 passed, 0 failed** — baseline held.
- Compiles clean. **Not deployed**; running server untouched; `ServerConstants.VERSION` still 84.
- The §8 bisection probe (`-Dv84.diag.skipEquips`) was **removed** — the measurement made it
  unnecessary, and a live diagnostic that suppresses everyone's equipment should not outlive its use.

**No unit test added.** `ServerConstants.VERSION` is a compile-time constant, so a test cannot encode
the same record at v83 and v84 to assert the 4-byte delta the way atlas's test does — that would need
the version threaded through as a parameter. Constructing an `Equip` also drags in
`ItemInformationProvider` (WZ) and `ExpTable`. Flagged in §10 as the one place a small refactor would
buy a real regression test.

## 10. Still open after this fix

1. **Confirm live.** Expected: explorer's items and ETC tab populate, Evan enters the world. If the Evan
   still crashes but the explorer is now correct, the remaining fault is Evan-specific and §8.2 has
   already cleared its stat block.
2. **Mine the rest of `Chronicle20/atlas` — this is now the project's best instrument.** It has audits
   for v83, v84, v87, v92 and v95, version-gated writers with IDA citations, and it plainly encodes
   deltas this port has not discovered yet. Two already visible in `set_field.go` that Cosmic will hit:
   GMS≥87 adds a leading `WriteShort(0)` "decode opt" plus four trailing logout-gift ints to
   `SET_FIELD`, and GMS≥95 adds `m_dwOldDriverID` after the channel id. Neither affects v84, but the
   same file records `nSubJob` at v87 in the stat block. **A dedicated ticket should diff Cosmic's
   writers against atlas's v84 gates wholesale** rather than discovering each one by crashing the
   client — that is the durable fix for this whole class of bug.
3. **A regression test for the equip record** needs `ServerConstants.VERSION` threaded as a parameter
   into the packet layer instead of read as a constant (§9.4). Worth doing once more v84 gates land.
4. **The IDB behind `ida_export_gms_v84.json` does not exist on this machine** — a full sweep of C:, D:
   and E: for `GMS_v84*`, `*U_DEVM*`, `*.i64`, `*.idb` found nothing. The export references
   `GMS_v84.1_U_DEVM.i64` from an external `packet-audit` toolchain. `D:\games\dreamms\DreamMS.exe` is
   a genuinely unpacked v92 client (`.text` raw == virtual) but is **member-obfuscated** (rolling
   `0xbaadf00d` XOR with tamper checksums, e.g. the 2-byte reader at `0x00479af0`) and stripped of RTTI,
   so recovering struct layouts from it is a multi-hour job. If anyone tries anyway, the anchor is:
   only **two** windows in that binary contain all seven `CharacterData` flag immediates —
   `0x004f5278` and `0x004f841b`. Given atlas covers v92 with audits, this is almost certainly not
   worth doing.
