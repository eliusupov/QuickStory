# Ticket 17 — Client route analysis: stay on v83, move to v84, or move to v92

**Question this decides:** how to reach *"v84 feature-complete with all current features intact"* —
and, after the owner's directive, how to reach **"fully working"** rather than "cheapest".

> **Owner directive, verbatim (2026-08-16):** *"i dont want cheaper, i want fully working."*
> Everything below is therefore ranked by **end-state completeness**, not by effort. Effort is
> still reported, but it is not the deciding variable.

**Evidence labels:** `[FACT-measured]` = measured on this machine, command shown or reproducible ·
`[FACT-sourced]` = external source with URL · `[INFERENCE]` = reasoning from the above, marked as
such · `[NOT-FOUND]` = searched for, not found (reported as a gap, not filled with plausible prose).

---

## 0. The three routes

| | Route A | Route B | Route C |
|---|---|---|---|
| **Client** | keep v83 | install v84 | install v92 (or v90 — see §8.2) |
| **WZ direction** | merge v84 content *into* v83 tree | ship v84 tree, backport v83-only content | ship v92 tree, backport v83-only content |
| **Evan** | binary gate patches + WZ | native | native |
| **Dual Blade** | **impossible** | **impossible** (DB is v88) | native |
| **Server protocol** | unchanged | version bump + opcode delta | version bump + full opcode remap |
| **Work already banked** | ~30 commits, tickets 04–11 | partially transferable | mostly discarded |

---

## 1. Q1 — How much did the opcodes actually change, v83 → v84?

### 1.1 What this repo actually has `[FACT-measured]`

```
src/main/java/net/opcodes/SendOpcode.java   307 enum entries
src/main/java/net/opcodes/RecvOpcode.java   178 enum entries
                                            485 total
```

The prior claim of *"308 send + 180 recv = 488 hand-mapped values"* is **very slightly
overstated but essentially correct**: the true count is **307 + 178 = 485**.

**Only a subset is live** `[FACT-measured]`:

```
distinct SendOpcode.* constants referenced in src/  247  (of 307)
distinct RecvOpcode.* constants referenced in src/  170  (of 178)
                                                    417  actually load-bearing
```

**This repo's opcode table is stock HeavenMS v83, byte-identical** `[FACT-measured]`:

```
diff <cosmic SendOpcode> <HeavenMS-v83-upstream SendOpcode>   -> no output
diff <cosmic RecvOpcode> <HeavenMS-v83-upstream RecvOpcode>   -> no output
```
(`porting-resources/reference-sources/HeavenMS-v83-upstream/src/net/opcodes/`)

So Cosmic has inherited, unmodified, the community's v83 table. Nothing bespoke to preserve.

### 1.2 The packet layer is genuinely well isolated `[FACT-measured]`

Files referencing `SendOpcode` outside `net/opcodes/` — **10 files total**:

```
constants/net/OpcodeConstants.java          net/packet/out/ShowNotesPacket.java
net/packet/ByteBufOutPacket.java            net/packet/OutPacket.java
net/packet/logging/OutPacketLogger.java     net/server/channel/handlers/NPCAnimationHandler.java
net/packet/out/SendNoteSuccessPacket.java   net/server/guild/GuildPackets.java
tools/PacketCreator.java  (7,462 lines)     tools/packets/WeddingPackets.java
```

498 reference sites, but they are concentrated. **The opcode *names* never change — only the
*values*.** A remap therefore edits **two enum files**, not 498 call sites. That is the good news,
and it is real.

**The bad news, and it is the larger half:** the opcode value is only the packet's first two bytes.
The **structures** behind them live in `PacketCreator.java`'s 7,462 lines and in the 147 inbound
handlers. A version-up that shifts a struct — buff masks especially, where the whole bit-ordering
moves — is not found by any diff of enum values. `[INFERENCE]`

### 1.3 The v83 table already contains dragon opcodes `[FACT-measured]`

```
SendOpcode:  SPAWN_DRAGON 0xB5,  MOVE_DRAGON 0xB6,  REMOVE_DRAGON 0xB7
RecvOpcode:  MOVE_DRAGON 0xB5
```
They sit contiguously in the sequence (`SUMMON_SKILL 0xB4` → dragon `0xB5–0xB7` → `MOVE_PLAYER
0xB9`), i.e. they are **not** appended placeholders — they occupy their natural slot in the v83
numbering. `[FACT-measured]`

### 1.4 The mechanism of opcode drift between adjacent versions `[FACT-sourced]`

From the archived RaGEZONE thread *Updating OPCODES*
(<https://forum.ragezone.com/threads/updating-opcodes.1112067/>, archived locally at
`porting-resources/docs/13-updating-opcodes.md`), **Eric**:

> *"Most opcodes follow a shift after a certain point, making them all become +1 from v83 or +2, etc."*

and, on a concrete v83→v90 case:

> *"Guest Login isn't in v90. Nexon keeps the packets for most things but the actual UI for it won't
> draw anymore so there's no real need."*

**This is the decisive structural fact about opcode drift** `[FACT-sourced]`: changes are
**insertions and deletions that shift every subsequent value**, not scattered random remapping.
Consequences:

- A single removed packet (`GUEST_LOGIN`) shifts the *entire* remainder of the table by −1.
- The work is therefore *mechanical* once you know **where** the insertion points are — but you
  cannot guess them, and being wrong by one silently mis-routes every packet after that point.
- It also means **"how many differ"** is a misleading metric. If v84 inserted one packet at index
  5, then ~300 values "differ" while the actual edit is one insertion. Report the **insertion
  points**, not the diff count.

> ⚠️ **Correction to the brief's framing.** The brief asks *"how many differ, and which"*. Per the
> sourced mechanism above, a large differ-count would **not** mean the job is large. Conversely a
> small differ-count is only meaningful if the shifts are identified. This ticket reports both.

### 1.5 Live-web opcode findings

*(see §1.6 — external research)*

---

## 2. Q2 — What else does a v84 client require of the server?

### 2.1 The version constant is one line `[FACT-measured]`

```java
// constants/net/ServerConstants.java:6
public static final short VERSION = 83;
```

### 2.2 The handshake `[FACT-measured]`

`tools/PacketCreator.java:600`:

```java
public static Packet getHello(short mapleVersion, InitializationVector sendIv, InitializationVector recvIv) {
    OutPacket p = new ByteBufOutPacket();
    p.writeShort(0x0E);          // handshake header
    p.writeShort(mapleVersion);  // <- ServerConstants.VERSION
    p.writeShort(1);             // maple-string length 1
    p.writeByte(49);             // '1'  == the patch/subversion string
    p.writeBytes(recvIv.getBytes());
    p.writeBytes(sendIv.getBytes());
    p.writeByte(8);              // locale: GMS
    return p;
}
```

So there **is** a subversion string beyond `VERSION`: the literal **`"1"`**, and a locale byte
**`8`** (GMS). Both are hardcoded here. `[FACT-measured]`

### 2.3 The crypto `[FACT-measured]`

`net/encryption/MapleAESOFB.java:38-47` — the AES key is the long-standing static GMS UserKey:

```
13 00 00 00  08 00 00 00  06 00 00 00  B4 00 00 00
1B 00 00 00  0F 00 00 00  33 00 00 00  52 00 00 00
```

Critically, `MapleAESOFB.java:97` byte-swaps the version into the cipher state:

```java
this.mapleVersion = (short) (((mapleVersion >> 8) & 0xFF) | ((mapleVersion << 8) & 0xFF00));
```

**Therefore changing `VERSION` to 84 automatically produces the correct packet-header encoding.**
No crypto edit is required for a version bump *per se*. `[FACT-measured]` + `[INFERENCE]`

### 2.4 Character creation — the server is already done `[FACT-measured]`

`net/server/handlers/login/CreateCharHandler.java:62`:

```java
case 3: // Evan - v84 port. The v83 client does not send 3 on its own; ticket 15b is
    status = EvanCreator.createCharacter(c, name, face, hair + haircolor, skincolor, top, bottom, shoes, weapon, gender);
```

`client/creator/novice/EvanCreator.java` exists and is complete. `client/Job.java:59-63` enumerates
`LEGEND(2000), EVAN(2001)` and **all ten** advancements `EVAN1(2200) … EVAN10(2218)`.

> **This is one of the most important findings in this ticket.** The server side of Evan character
> creation is *finished*. Route A's remaining blocker is **purely the v83 client's inability to
> send race `3`**. Under Route B or C the client sends it natively and the existing server code
> answers it with zero further work.

### 2.5 New mandatory packets on login/char-select

*(see §2.6 — external research)*

---

## 3. Q3 — The WZ direction reverses under Route B/C. What does that cost?

This is fully measurable from the existing manifests and is the section where the brief's premise
needed correcting.

### 3.1 What v84 actually deletes `[FACT-measured]`

`docs/wz-baseline/removed-list/` — nodes present in v83-stock, absent from v84. **Copy-roots per
file** (each row is a root; everything beneath it travels with it):

| .wz | removed roots | | .wz | removed roots |
|---|---:|---|---|---:|
| Etc | **2,028** | | Quest | 39 |
| Map | **1,017** | | Npc | 31 |
| Mob | **674** | | UI | 7 |
| Character | 136 | | Sound | 2 |
| Item | 35 | | Base/Effect/Morph/Reactor/Skill/String/TamingMob | **0** |
| | | | **TOTAL** | **3,969** |

### 3.2 ⚠️ The brief's content claim is wrong — corrected here `[FACT-measured]`

> The brief states: *"the whole Monster Carnival series, Mu Lung Dojo, Sheep Ranch"* were deleted.
> **Two of those three are incorrect.**

Measured against `docs/wz-baseline/removed-list/Map.txt` and
`docs/wz-baseline/map-missing-names-v83.txt`:

```
Monster Carnival (98xxxxxxx) in removed list:  0        <- NOT deleted. Fully intact in v84.
Mu Lung Dojo     (925xxxxxx) in removed list:  1        <- ONE floor only: 925020610, "Mu Lung Dojo 6th Floor"
Sheep Ranch      (109090001-4)              :  4        <- correct, deleted
```

**833 whole `.img` maps are deleted; 832 are genuinely absent (1 was relocated).** Grouped:

| Group | count | What it is |
|---|---:|---|
| `970030100`–`970042711` | **810** | **Boss Rush** — "Stage 1 &lt;Mano&gt;" etc. |
| `Map0/0000xxxxx`, `0010xxxxx` | 17 | no `String.wz` entry — unnamed/dev maps |
| `109090001`–`109090004` | 4 | **Sheep Ranch Lobby** |
| `925020610` | 1 | **Mu Lung Dojo 6th Floor** |
| `Map.wz/Obj/tutorial_jp.img` | 1 | JP tutorial object set |

**The real story is Boss Rush, not Monster Carnival.** 810 of the 832 deletions (97%) are one
feature.

### 3.3 The server does implement the deleted content `[FACT-measured]`

```
constants/id/MapId.java:213   private static final int BOSS_RUSH_MIN = 970030100;
constants/id/MapId.java:214   private static final int BOSS_RUSH_MAX = 970042711;
```

The range matches the deleted set **exactly**. Live consumers:

```
scripts/event/BossRushPQ.js
scripts/npc/9000021.js   scripts/npc/9000037.js   scripts/npc/9977777.js
scripts/portal/raid_rest.js   scripts/portal/raid_stage.js
server/life/MobSkill.java
```

So yes — shipping a bare v84 `Map.wz` would delete a **fully implemented, script-backed PQ**.

### 3.4 The much larger cost nobody has counted: the *protect* set `[FACT-measured]`

`docs/wz-baseline/protect-list/` — nodes in the **live client** present in **neither** stock tree.
This is the server's own custom content and the HD mod's work.

| .wz | protect roots | | .wz | protect roots |
|---|---:|---|---|---:|
| String | **7,608** | | Item | 110 |
| Npc | **5,985** | | Reactor | 53 |
| Character | **2,991** | | UI | 45 |
| Map | 403 | | Etc | 10 |
| Quest | 229 | | Skill | 7 |
| Mob | 172 | | Base/Effect/Morph/Sound/TamingMob | 4 each |
| | | | **TOTAL** | **17,633** |

Samples confirm these are genuine custom content, not diff noise `[FACT-measured]`:

```
Npc.wz/2112018.img/info/script          <- custom NPC scripts
String.wz/Cash.img/5120033              <- custom cash-shop names
Character.wz/Accessory/01012011.img/info/cash
Map.wz/Map/Map1/100000003.img/info/onUserEnter   <- custom map hooks
```

### 3.5 Route B/C WZ job vs Route A WZ job `[FACT-measured]`

| | Route A (current) | Route B/C |
|---|---:|---:|
| v84 content to merge in | 16,177 add-list roots — **curated to 1,791** composed rows | 0 (free with the tree) |
| v83-only content to backport | 0 (already in place) | **3,969** |
| custom/HD content to re-merge | 0 (already in place, untouched) | **17,633** |
| **total roots to move** | **1,791** (largely already executed) | **21,602** |

> **This is a ~12× difference in WZ merge volume, in Route A's favour** — and it is the opposite of
> what the brief anticipated. The reason is the *protect* set: Route A never has to move custom
> content because custom content is already in the tree being edited. Route B/C start from a clean
> retail tree and must re-import every custom node the server has ever added. `[INFERENCE from FACT-measured]`

### 3.6 Two mitigations that reduce the above `[INFERENCE]`

1. **The server's XML tree does not need to move.** Cosmic reads a 599 MB XML tree at `wz/`
   (`Base.wz … Quest.wz`). The server does not care what version the *client* is; it needs data for
   what it simulates. The Route A merge work already landed into `wz/` **transfers unchanged to
   Route B or C.** Only the **client-side** binary WZ must be rebuilt.
2. **The tooling transfers.** `docs/wz-baseline/tool-merge/` (`WzMerge`) is direction-agnostic —
   it copies node subtrees between trees. The deny/force lists and collision triage
   (`03c`: 735 collisions triaged) are direction-*specific* and would need redoing.

### 3.7 v84 modified nodes arrive as v84's version `[FACT-measured]`

1,435 images differ between v83 and v84 without being adds or removes
(`docs/wz-baseline/modified-list/`): Mob 1,173 · Map 128 · Character 43 · Item 30 · String 14 ·
Npc 12 · Etc 12 · UI 10 · Quest 7 · Skill 6. Under Route B these silently become v84's version.
Server-side stats come from the XML tree so gameplay is unaffected, but **client-side visuals and
any `life`/`portal` node the server reads positionally would shift.** Not quantified here.

### 3.8 Route C has no baseline at all `[FACT-measured]`

```
porting-resources/wz-data/   ->   v83-stock/   v84/     (both complete, 17 .wz each)
```

**There is no v92 tree.** Route C requires extracting `GMSSetupv92.exe` and **regenerating every
manifest from scratch** — the whole of tickets 02, 02b, 02c, 02d, 02f, 02g redone against a third
tree, plus a fresh 735-row collision triage.

---

## 4. Q4 — Does the HD mod survive? **No.** `[FACT-sourced]`

The Ezorsia v2 HD mod (<https://github.com/444Ro666/MapleEzorsia-v2>) is **hard-pinned to one
specific v83 `MapleStory.exe`**.

- Repo tagline is literally *"v83 Standalone HD dll client/localhost"*; the only wiki page is
  [v83‐Client‐Setup‐and‐Development‐Guide](https://github.com/444Ro666/MapleEzorsia-v2/wiki/v83%E2%80%90Client%E2%80%90Setup%E2%80%90and%E2%80%90Development%E2%80%90Guide),
  which mandates a **specific pre-modified exe**. No other version is claimed anywhere.
- [`ezorsia/AddyLocations.h`](https://github.com/444Ro666/MapleEzorsia-v2/blob/main/ezorsia/AddyLocations.h)
  is ~120–130 `const DWORD` **raw absolute virtual addresses**:
  ```c
  const DWORD dwDInput8DLLInject      = 0x00796357;
  const DWORD dwMovementFlushInterval = 0x0068A83F;
  const DWORD dwRemoteAddress         = 0x00AFE084;
  const DWORD dwVersionNumberFix      = 0x005F464D;
  ```
  An in-file comment gives their provenance: *"taken from released semi-named v83 IDB"*.
- [`ezorsia/Memory.cpp`](https://github.com/444Ro666/MapleEzorsia-v2/blob/main/ezorsia/Memory.cpp)
  contains **no pattern or signature scanning of any kind** — only `WriteByte`, `WriteInt`,
  `CodeCave` at a caller-supplied absolute address.
- [`ezorsia/Client.cpp`](https://github.com/444Ro666/MapleEzorsia-v2/blob/main/ezorsia/Client.cpp)
  applies resolution as ~200 writes with hand-computed instruction-operand offsets
  (`WriteInt(dwViewPortHeight + 3, …)`). **No base-relative math, no version check, no hash check.**
  It patches blind.

`dinput8.dll` is only the **injection vector**, not the hooking mechanism. The README's *"compatible
with any set of WZ or IMG files"* claim refers to **WZ data only** — and is true, because
`EzorsiaV2_UI.wz` is a prebuilt blob extracted from the DLL's own resources (`FindResource(…,
IDR_RCDATA2, RT_RCDATA)`), never generated from the client's `UI.wz`. It says nothing about the exe.

| Client | HD mod survives? | Why |
|---|---|---|
| **v83** | **yes** — the only target | addresses derived from a v83 IDB |
| **v84** | **no** | different build ⇒ all ~125 addresses land on unrelated code; `CodeCave` writes 5-byte JMPs mid-instruction ⇒ expect an immediate crash, not degraded rendering `[INFERENCE, high confidence]` |
| **v92** | **no, worse** | same address problem **plus** v89+ clients rewrote the Gr2D resolution path, so the *technique* changes, not just the offsets — <https://forum.ragezone.com/threads/all-addresses-for-v83-resolution-change.1161938/page-3> |

`[NOT-FOUND]` Zero issues, forks, or discussions anywhere about running it on a non-v83 client.
~10 forks, all v83. v1 (`izarooni/MapleEzorsia`) is also v83-only.

### 4.1 Why this is worse than it looks — the IDB availability problem `[FACT-sourced]`

Re-deriving ~125 addresses requires an IDA database for the target exe, or the RE work from scratch.
Searching the local archive (`porting-resources/docs/`) for which client IDBs exist publicly:

```
v83 IDB  — exists (3 references)
v90 IDB  — exists (2 references, incl. "I downloaded the V90 IDB")
v95 IDB  — exists (the RaGEZONE archive's ".idb leak version")
v84 IDB  — NOT FOUND
v92 IDB  — NOT FOUND
```

> **This inverts the version ordering.** The HD mod is *more* portable to **v90** (IDB exists) than
> to **v84** or **v92** (no IDB). The owner has `GMSSetupv92.exe` on disk, but **v92 is the worst
> supported** of the candidate versions. See §8.2.

---

## 5. Q5 — Is the v84 client usable for a private server?

*(see §5.1 — external research)*

---

## 6. Route A's permanent ceilings

Assessed against *"fully working"*, not *"cheap"*.

### 6.1 (a) Memory-only gate patch — **real, but mitigable** `[FACT-measured]`

`tools/evan-gate-patch.log` shows the patcher running as a **watch daemon**, re-attaching to every
`MapleStory.exe` launch:

```
[2026-08-16 15:44:45] attached to PID 42484 (MapleStory)
[2026-08-16 15:44:45] GUARD PASS: GetSkill gate pattern found at 0x0075C776.
[2026-08-16 15:44:45] RESULT: GetSkill PATCHED and verified at 0x0075C776
[2026-08-16 15:44:45] GUARD PASS: GetSkillLevel gate pattern found at 0x00761714.
[2026-08-16 15:44:45] WATCH: 18 process(es) patched. Watching for more.
```

Two gates, both patched, verified by read-back, 18 processes in one session. `STATUS.md` records
that the static route is dead: *"`local.exe`/`localhome.exe` are memory dumps, not clients"*.

> **But the brief overstates this ceiling.** `tools/evan-gate-dll/evan-gate.c` already exists — a
> kernel32-only DLL that NOPs the gate, designed to be loaded **by Ezorsia's own `dinput8.dll`**
> via its `use_custom_dll_1` config hook. From its header comment `[FACT-measured]`:
>
> > *"Loaded by dinput8.dll via config.ini `use_custom_dll_1`. … NAME MATTERS. dinput8.dll at
> > 0x10008BAF does strcmp(value, "CUSTOM.dll") and SKIPS loading when they are equal."*
>
> The client **already ships an injected DLL**. Folding the gate patch into it makes the patch
> automatic, invisible, and distributable with the client — no separate patcher, nothing for a
> player to run. **It is written but not yet built.** This ceiling is an unfinished task, not a
> permanent defect.

**Verdict on (a): not disqualifying.**

### 6.2 (b) Character creation — **genuinely blocked, and now explained** `[FACT-measured]`

The brief notes that adding `UI.wz/Login.img/RaceSelect/BtEvan` did nothing. The manifests explain
exactly why. v84 did not *add a button* to the v83 screen — **it replaced the screen**:

```
removed-list/UI.txt  (v83 -> v84, deleted):        add-list/UI.txt  (v83 -> v84, added):
  Login.img/RaceSelect/normal                        Login.img/RaceSelect/backgrnd
  Login.img/RaceSelect/knight                        Login.img/RaceSelect/BtKnight
  Login.img/RaceSelect/aran                          Login.img/RaceSelect/BtAran
  Login.img/RaceSelect/aran1                         Login.img/RaceSelect/BtEvan
  Login.img/RaceSelect/BtSelect                      Login.img/NewCharEvan
  Login.img/RaceSelect/textGL                        Login.img/CharSelect/evan
```

> **The asset contract changed completely.** v83's binary drives `normal` / `knight` / `aran` /
> `BtSelect`. v84's drives a shared `backgrnd` plus `Bt*` buttons. Adding `BtEvan` to a v83 client
> does nothing **because the v83 code never looks up `Bt*` names at all.** `[INFERENCE from FACT-measured]`
>
> `Login.img/NewCharEvan` is an entire creation *screen* with no v83 counterpart — the same pattern
> as `UIWindow.img/SkillEx`.

This is a code-level feature, not data. It requires code-cave work in a Themida-packed binary, and
`porting-resources/docs/08-new-job-v83-help.md`
(<https://forum.ragezone.com/threads/new-job-v83-help.1131763/>) is the only prior art: **Eric**
says a real new class needs *"code-cave"* work and *"your own functions and checks for each job"* —
**no addresses given**. `[FACT-sourced]`

**Verdict on (b): a genuine blocker with no published prior art. `[NOT-FOUND]` for any solution.**

### 6.3 (c) Dragon rendering

*(see §6.3.1 — external research; this is the decisive ceiling)*

### 6.4 (d) Skill-window tabs — **partly refuted, mechanism identified** `[FACT-measured]`

Ticket `11b` disassembled `CUISkill::CreateTabs` @ `0x008AD2D1` and proved the naive form of this
concern **false**:

```
008AD4A5  xor  edi, edi              ; edi = tab INDEX
008AD4B5  cmp  edi, [eax-4]          ; index < roots array COUNT
008AD4C1  push edi                   ; <-- the INDEX, not roots[edi]
008AD4C5  push 0xaf2444              ; "%d"
008AD4CF  call 0x445b4b              ; sprintf(buf, "%d", index)
```

`roots[edi]` is never read; the tab resource name is the **decimal loop index**. So jobs 0, 2000 and
2001 build identical tab names — *"The tab strip cannot differ between the three jobs. Job 0 works,
so the tab strip works."* The `AranButton` special case never runs for Evan **or** Aran-beginner.

**But the count comes from `[roots-4]`.** `[INFERENCE from FACT-measured]` For Evan the roots array
has 10 entries, so the loop creates tabs `"0"`…`"9"` and looks each up under
`UIWindow.img/Skill/Tab/enabled`. The manifests show v84 **did not extend** `Skill/Tab/enabled` —
the only v84 UI additions are `Basic.img/Tab8`, `UIWindow.img/SkillEx`, `UIWindow.img/SkillMacroEx`.
v84 gave Evan a **separate skill window class** (`CUISkillEx`, 11 tabs) rather than widening the old
one (5 tabs).

Since a v83 client has no `CUISkillEx`, Evan renders in the **old 5-slot window**, and tabs 5–9 look
up resources that do not exist. Ticket 10 has already merged `SkillEx`/`SkillMacroEx` into the tree
— **but merging the data cannot add the class to a Themida-packed binary.**

**Verdict on (d): cosmetic, permanent, low severity — but confirms the pattern.** v84's Evan support
is repeatedly *new UI classes in the executable*, which is precisely what WZ merging cannot supply.

---

## 7. What Route A has already banked `[FACT-measured]`

Not a reason to continue, but it must be priced honestly when comparing.

~30 commits on `worktree-evan-dualblade`, tickets **04, 05, 06, 07, 08, 09, 10, 11, 16** landed:

```
4e8c49594  05: v84 mounts — 8 sprites, 27 skills, 25 morph states
59cb85105  06: Crimson Sky — 22 maps, 17 mobs, 6 NPCs, 2 reactors; drop tables added
8fc0a4b0f  07: Neo City Year 2227 playable
2a89da169  08: the tail of the v84 map delta — 22 misc areas
8e740646b  09: merge the 63 non-Evan v84 quests
15f1e81fe  10: Evan exists, renders, and has a dragon
```

**Split by what survives a client change** `[INFERENCE]`:

| Work | Survives Route B/C? |
|---|---|
| `wz/` XML server tree merges (599 MB) | **yes** — server tree is version-independent (§3.6) |
| SQL drop tables (`153-crimson-sky-drop-data.sql`, 776 rows) | **yes** |
| quest scripts, constants, `EvanCreator`, `Job.java` | **yes** |
| `WzMerge` tooling | **yes**, direction-agnostic |
| client-side binary WZ merges (1,791 composed rows) | **no** — unnecessary under B/C |
| collision triage / deny+force lists (735 rows) | **no** — direction-specific |
| both Evan gate patches + `evan-gate.c` | **no** — v83-only, and unnecessary under B/C |

**The majority of the banked work is server-side and transfers.** The discarded portion is the
client-WZ merge and the gate patching. `[INFERENCE]`

---

## 8. Route C — the v92 client

### 8.1 Dual Blade is absent from this server entirely `[FACT-measured]`

```
grep -rli "dualblade|dual_blade|katara" src/main/java   ->   (no matches)
client/Job.java: THIEF(400), ASSASSIN(410)…NIGHTLORD(412), BANDIT(420)…SHADOWER(422)
                 <- no 430/431/432/433/434
```

Evan by contrast is **fully enumerated**: `LEGEND(2000), EVAN(2001)` and `EVAN1(2200) … EVAN10(2218)`.

> **Route C is not "Route B plus a bigger opcode delta". It is Route B plus a bigger opcode delta
> plus implementing an entire character class server-side from zero** — job IDs, advancement, the
> katara second-weapon slot, and the full skill set. None of it exists. `[FACT-measured]`

### 8.2 ⚠️ The owner has the wrong installer for Route C `[FACT-sourced]`

From `porting-resources/docs/16-source-that-have-evan-dualblade.md`
(<https://forum.ragezone.com/threads/source-that-have-evan-dualblade.1069427/>), asked directly
about *"Evan + DB + Pre bb"*:

> *"LocalMS. Or v90 - TropikMS"* · *"v88 LotusMS Source: Currently not working, you have to fix it
> yourself"* · *"Last time I checked, LocalMS was pretty much playable."*

Combined with the IDB availability finding (§4.1) and `porting-resources/docs/13-updating-opcodes.md`
referencing **"sunnyboys V90.3 sendops"** — a *published v90 opcode table*:

| Version | Evan | Dual Blade | Pre-BB | Public IDB | Public opcode table | Existing source |
|---|:-:|:-:|:-:|:-:|:-:|---|
| v83 | gated | ✗ | ✓ | **✓** | ✓ | HeavenMS/Cosmic — mature |
| **v84** | **✓** | ✗ | ✓ | **✗** | ? (§1.6) | none specifically |
| v88 | ✓ | ✓ | ✓ | ✗ | ? | LocalMS, LotusMS ("buggy", "not working") |
| **v90** | **✓** | **✓** | **✓** | **✓** | **✓ (sunnyboy v90.3)** | **TropikMS, FusionSource** |
| **v92** | ✓ | ✓ | ✓ | **✗** | **✗** | **none** |
| v95 | ✓ | ✓ | **✗ post-BB** | ✓ | ✓ | Kinoko, Rebirth95 (incomplete) |

`docs/EVAN-DUALBLADE-SCOPE.md:282` already concedes the v92 gap in this repo's own words:

> *"**The one resource with no ready-made answer: there is no public v92 opcode table.** You
> generate it."*

> **Finding:** if the owner goes down the version-up path, the evidence points at **v90, not v92**.
> v92 is newer but is an **orphan version** — no IDB, no opcode table, no source. v90 has all three.
> The `GMSSetupv92.exe` on disk is not the asset Route C wants. `[INFERENCE from FACT-sourced]`
>
> This matches this repo's own earlier research (`11e:171`): *"v90 is the pragmatic pick inside it:
> it is the only one in that band with both a source and a working localhost."*

### 8.3 No Cosmic-class server exists above v83 `[FACT-sourced]`

From `docs/work-plan/tickets/11e-evan-v83-feasibility-research.md`, with URLs:

- **Kinoko v95** (<https://forum.ragezone.com/threads/kinoko-v95.1233229/>) — written from scratch,
  *"still missing a bunch of stuff like actual progression, but enough for people to flash jump
  around Henesys"*
- **Rebirth95** (<https://github.com/67-6f-64/Rebirth95.Server>) — C#/Python
- **Xeon v97**, **v90 FusionSource**

> *"The client is not the scarce resource. The scarce resource is the **server**. Every mature
> open-source HeavenMS-lineage server is v83-locked."* `[FACT-sourced]`

Route C therefore means **porting Cosmic**, not adopting an existing v90/v92 server. The 7,462-line
`PacketCreator` and 147 handlers are the real surface, not the 485 enum values.

### 8.4 Big Bang boundary `[FACT-sourced]`

`docs/EVAN-DUALBLADE-SCOPE.md:28-30` and
<https://msupdate.wordpress.com/2011/01/19/gms-v91v93v94v95-ratings/>:

| Version | Content | Date |
|---|---|---|
| v0.83 | "Year of the Tiger" | 2010-02-22 |
| v0.84 | **Evan / "Dragon Master"** | 2010-03-31 |
| v0.88 | **Dual Blade**, Chaos Bosses, Item Potential | 2010-07-21 |
| v0.93 | **Big Bang** — formula/map/job rewrite | 2010-12-07 |

Also **Eric**, on sourcing Evan data (`11e:47`): *"from v84, not anything above v92 because skill
data changed."* v88–v92 is confirmed as the only window with both classes and pre-BB gameplay.

---

## 9. Verdict

*(completed in §9 below once external research lands)*

---

## Appendix A — Reproducing the measurements

```bash
# opcode counts and stock-v83 identity
grep -cE '^\s*[A-Z_0-9]+\(' src/main/java/net/opcodes/SendOpcode.java     # 307
grep -cE '^\s*[A-Z_0-9]+\(' src/main/java/net/opcodes/RecvOpcode.java     # 178
grep -rhoE 'SendOpcode\.[A-Z_0-9]+' --include='*.java' src/main/java | sort -u | wc -l   # 247
grep -rhoE 'RecvOpcode\.[A-Z_0-9]+' --include='*.java' src/main/java | sort -u | wc -l   # 170

# WZ manifest volumes
wc -l docs/wz-baseline/removed-list/*.txt    # 3,969 + 16 header lines
wc -l docs/wz-baseline/protect-list/*.txt    # 17,633 + 16 header lines
wc -l docs/wz-baseline/add-list/*.txt        # 16,177 + 16 header lines
cat docs/wz-baseline/merge-lists/composed/*.txt | grep -vc '^#\|^$'   # 1,791

# what v84 deletes
grep -c '\.img$' docs/wz-baseline/removed-list/Map.txt                   # 833
grep -c '98[0-9]\{6\}' docs/wz-baseline/removed-list/Map.txt             # 0  (Monster Carnival intact)
grep '\.img$' docs/wz-baseline/removed-list/Map.txt | grep -o '[0-9]\{9\}' | cut -c1-6 | sort | uniq -c
```

## Appendix B — Sources

**Local archives** (`porting-resources/docs/`, archived 2026-08-15, each carries its source URL):

| File | Thread |
|---|---|
| `02-v83-in-evan-feasibility.md` | <https://forum.ragezone.com/threads/v83-in-evan.1107098/> |
| `08-new-job-v83-help.md` | <https://forum.ragezone.com/threads/new-job-v83-help.1131763/> |
| `13-updating-opcodes.md` | <https://forum.ragezone.com/threads/updating-opcodes.1112067/> |
| `16-source-that-have-evan-dualblade.md` | <https://forum.ragezone.com/threads/source-that-have-evan-dualblade.1069427/> |
| `07-client-localhost-archive.md` | <https://forum.ragezone.com/threads/maplestory-client-localhost-archive.1101897/> |

**Repo evidence:** `docs/wz-baseline/{SUMMARY.md,removed-list,protect-list,add-list,modified-list,merge-lists}`,
`docs/work-plan/tickets/11b`, `11e`, `docs/EVAN-DUALBLADE-SCOPE.md`,
`porting-resources/SCOPE-V84-UPGRADE.md`, `tools/evan-gate-patch.log`, `tools/evan-gate-dll/evan-gate.c`.
