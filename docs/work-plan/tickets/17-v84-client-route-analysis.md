# Ticket 17 — The v84 migration plan (primary), with a v92 sketch

**Owner decisions, verbatim, in order given (2026-08-16):**

> *"i dont want cheaper, i want fully working."*
> *"i want evan first, dual blade can be after."*
> *"v84 feature complete is more important"*
> *"i do want to keep cosmic working tho, with added features."*
> *"take features from where ever you can, just make sure the workflow is planned correctly."*
> *"hd client can be done last"*

**Therefore: v84 is a FULL DESTINATION — complete, verified, playable. Not a stepping stone.**
Cosmic is extended, never replaced. Everything working today keeps working, including the content
v84 *deleted*. Dual Blade is v88 and is deferred; it must not constrain phase 1. HD client is last.

**Labels:** `[FACT-measured]` = measured here (Appendix A) · `[FACT-sourced]` = URL given ·
`[INFERENCE]` = reasoning, marked · `[NOT-FOUND]` = searched, not found.

---

## 1. THE CRUX NUMBER — the v83→v84 opcode delta, measured

This was named the single most important number in the plan. **I measured it rather than assuming it,
and the honest answer is more nuanced than "one version, so barely anything changed."**

### 1.1 Method — prove the instrument first `[FACT-measured]`

[Chronicle20/atlas](https://github.com/Chronicle20/atlas) publishes IDA-derived opcode tables per GMS
version. Before trusting its **v84** table I validated its **v83** table against Cosmic's own file —
ground truth on this disk:

```
atlas v83 clientbound values found in Cosmic SendOpcode: 213/221 (96.4%)
atlas v83 serverbound values found in Cosmic RecvOpcode: 129/143 (90.2%)
```

Good enough to trust. Then the diff:

### 1.2 The result `[FACT-measured]`

```
clientbound (server->client)   shared=220   same= 44   DIFFER=176  (80%)
serverbound (client->server)   shared=143   same= 88   DIFFER= 55  (38%)
                                            TOTAL      DIFFER=231 of 363  (64%)

clientbound shift distribution: {0:44, +2:12, +3:42, +4:33, +5:4, +6:18, +7:56, +9:3, +10:8}
serverbound shift distribution: {0:88, +2:8, +3:3, +4:14, +5:12, +6:14, +7:4}
```

Landmarks, cross-checked against Cosmic's real values:

| | Cosmic v83 | v84 | | | Cosmic v83 | v84 |
|---|---|---|---|---|---|---|
| `SET_FIELD` | `0x7D` | `0x80` | | `SPAWN_MONSTER` | `0xEC` | `0xF2` |
| `SPAWN_PLAYER` | `0xA0` | `0xA3` | | `SPAWN_NPC` | `0x101` | `0x108` |
| `MOVE_PLAYER` | `0xB9` | `0xBD` | | | | |

### 1.3 What this actually means — read this carefully

> **The good news, and it is the load-bearing fact of this plan:** the drift is a **clean monotonic
> insertion staircase**. v84 inserts ~10 opcodes; everything above each insertion point moves by a fixed
> amount. It is not scattered remapping — it is arithmetic. And **the login band is byte-identical**
> (clientbound `0x00–0x3E`, serverbound `0x01–0x75`), which is what makes the incremental bring-up in
> §2 possible: you can reach character select before touching a single field opcode.

> ⚠ **CORRECTION (ticket 22, measured).** "The login band is byte-identical" is true of the *opcode
> values* and was read here as if it were also true of the *packet bodies*. It is not.
> `LOGIN_STATUS (0x00)` gained an 8-byte tail between v83 and v84 — v84's
> `CLogin::OnCheckPasswordResult` decodes it, v83's does not — and omitting it is what killed the v84
> client at the world list. The related standing belief that "packet structure only breaks at v86" is
> **wrong**: structure breaks at v84, in the login flow, on the very first packet after the handshake.
> Ticket 22 has the measured layouts for the whole login flow; the rest of them do check out.

> **The honest correction to the premise:** *"one version away, so the protocol drift is minimal"* is
> only half right. **64% of opcodes move.** The count is large; the *work* is small, because a
> staircase is mechanical and the table is published. But nobody should start this expecting a
> ten-line change.

**Comparison for context** — the v92 figure reported by a peer session is **421 of 468 (~90%)**
`[REPORT-peer, not verified by me]`. So v84 is meaningfully smaller *and* far better-shaped. The owner's
instinct to take the small step first is sound.

> ⚠ **And the fact that decides §9:** a peer measured that **exactly zero v84 opcode values survive to
> v92** `[REPORT-peer]`. This is consistent with my own staircase measurement — the shifts are
> cumulative, so v84's `+2→+10` table is not a partial v92 table, it is a different one. **Phase 1 is a
> rehearsal of the *procedure*, never of the *values*.** §9 says exactly what does and does not carry.

---

## 2. THE WORKFLOW — the deliverable

Design rules, from the owner's instructions and this project's own scar tissue:

1. **Cosmic never stops working.** Every phase ends launchable and testable.
2. **Prove the instrument before trusting the measurement.** This project has been burned three times
   already — a wrong client binary (`local.exe` turned out to be a memory dump, not a client), a
   self-defeating debug trap, and a merge tool that silently punched a hole in a positional array. Every
   gate below tests the *tool* before the *result*. §1.1 is that rule applied to the crux number.
3. **The v83 install stays the fallback until v84 is proven.** Verified present `[FACT-measured]`:
   `_backup\client-v83-EzorsiaV2-2026-08-15\` (2.5 GB) and `_backup\Cosmic-2026-08-15\`.
   v84 is built on a branch, in a **separate server instance and a separate client directory**.
4. **The owner's client launches are the scarce resource.** Batched into named gates, never scattered.

### 2.1 Phases

| # | Phase | Needs | Gate — "pass" means | Owner | Size |
|---|---|---|---|:-:|---|
| **0** | **Recon & instrument-proofing** | — | v84 client installed, localhost-routed, reaches **its own** login screen offline; `wz-data/v84` re-hashed against `GMSSetupv84.exe`; opcode table cross-validated against a **second** source (Riremito `GMS_v84_*.properties`) | 1 launch | **2–4 d** |
| **1** | **Protocol A — login** | 0 | **v84 client reaches character select.** Free-ish: login band is identical (§1.3) | 1 launch | **2–3 d** |
| **2** | **Protocol B — the field** | 1 | **character spawns, moves, attacks, loots; no desync over 10 min.** Includes the movement/attack prefix change (§3.3) | 1 launch | **1–2 w** |
| **3** | **WZ base swap + backport** | 0 | `WzMerge hash` over every image: **only named rows changed**; all 3,969 removed-roots + 17,633 protect-roots present; **Boss Rush, Monster Carnival, Mu Lung Dojo, Sheep Ranch all enterable** | 1 launch | **2–3 w** |
| **4** | **Parity — everything we have today** | 2,3 | **regression suite passes at parity with the v83 server** (§8) | 1 launch | **1–2 w** |
| **5** | **Evan, complete** | 4 | creation → 2001 → 2200 → 2210–2218; **all 58 skills**; dragon; mounts; the six named defects fixed (§6) | batched | **2–3 w** |
| **6** | **v84 content, complete and *reachable*** | 4 | every v84 map/mob/NPC/item/quest reachable in game — **no staged-but-unreachable rows** (§7) | batched | **2–3 w** |
| **7** | **Cash shop + items** | 4 | v84 catalogue purchasable; new equips/cosmetics usable | batched | **3–5 d** |
| **8** | **CUTOVER** | 4–7 | live switch; v83 retained; soak | yes | **2–3 d** |
| **9** | **HD client (LAST)** | 8 | 1024/1280/1366/1600/1920 render on the v84 exe | — | **open-ended** |

**Phases 0–7 → v84 feature-complete: ≈ 8–12 weeks focused.** Phase 9 gates nothing.

### 2.2 Ordering — what is sequential, what runs in parallel

```
0 Recon ──┬─► 1 Login ─► 2 Field ──┬─► 4 Parity ──┬─► 5 Evan ────┐
          │                         │              ├─► 6 Content ─┼─► 8 CUTOVER ─► 9 HD client
          └─► 3 WZ base + backport ─┘              └─► 7 Cash/items ┘
```

- **Strictly sequential: 0 → 1 → 2.** Nothing content-related can even be *tested* until a character
  spawns in a field. This is the one hard dependency in the plan.
- **Parallel with protocol: Phase 3 (WZ).** It needs only Phase 0's extracted tree, is pure data
  engineering on tooling that already exists, and shares no files with Phases 1–2. **Running 3 alongside
  1–2 is the single biggest schedule saving available.**
- **Parallel after Phase 4: 5, 6, 7** are largely independent. One ordering constraint: **classes before
  their quests** — Evan's job-advancement quests belong in Phase 5, not 6. (This matters concretely:
  9 of the 63 v84 non-Evan quests are Evan-locked, §7.)
- **Phase 9 is deliberately terminal** so no earlier decision is shaped by it.

### 2.3 Rollback points

| After | Known-good state | How to return |
|---|---|---|
| 0–7 | live v83 server + client, untouched | nothing to undo — v84 lives on a branch and a separate instance |
| 3 | v84 WZ tree pre-backport | rebuilt from `wz-data/v84`, which is hash-matched to the installer |
| 8 | full v83 client + server backup (2.5 GB, verified) | restore both `_backup\` trees; **keep them for one full soak period** |

### 2.4 Owner decisions vs autonomous work

**Needs the owner** — ~6 client launches total, one per gate: Phase 0 client validation; the two protocol
gates; the Phase 3 content playthrough; the Phase 4 parity sign-off; the Phase 8 cutover. Plus judgement
calls on the **quest-expiry policy** (§7.2) and **whether the HD mod is worth its cost** (§9).

**Autonomous:** all opcode/struct work, all WZ merging, all Java, all scripts, all SQL.

---

## 3. PROTOCOL

### 3.1 The seam is narrow `[FACT-measured]`

```
SendOpcode.java  307 entries (247 referenced)    RecvOpcode.java  178 (170 referenced)
                          485 total, 417 load-bearing
ServerConstants.java:6   public static final short VERSION = 83;
```
Both files are **byte-identical to stock HeavenMS v83** — `diff` against
`reference-sources/HeavenMS-v83-upstream/` returns no output. Nothing bespoke to preserve.
Only **10 files** reference `SendOpcode` outside `net/opcodes/`. **Opcode names never change, only
values — a remap edits two enum files, not the 498 reference sites.**

### 3.2 Crypto and handshake — free `[FACT-measured]`

`MapleAESOFB.java:38-47` holds the static GMS UserKey `13 00 00 00 08 00 00 00 06 00 00 00 B4 …`,
**unchanged v83→v95** across every source checked. `:97` byte-swaps the version into the cipher state, so
bumping `VERSION` produces correct header encoding automatically. `getHello()`
(`PacketCreator.java:600`) writes `0x0E`, version, maple-string `"1"`, IVs, locale `8` — and v84 keeps
**the same subversion `"1"` and locale `8`**. **A one-line change.**

### 3.3 The structure change v84 forces `[FACT-sourced]`

**Movement and attack packets gained ~20 bytes** of debug-register anti-cheat prefix. This is *not* a
v92-only cost — *"GMS v84–v98 clients had the ugly debug register bytes at the beginning of every attack
& player movement packet"* ([Localhost Workshop](https://forum.ragezone.com/threads/localhost-workshop.1202021/)).

> **Mitigation, measured `[FACT-measured]`:** parsing is centralised —
> `AbstractMovementPacketHandler.parseMovement()` is the shared parser and the prefix sits at
> `MovePlayerHandler.java:32` (`p.skip(9)`). A handful of edits, not a sweep.
> **This is the highest-risk item in Phase 2** because a wrong offset produces silent desync rather than
> an exception. The gate is explicitly "no desync over 10 minutes", not "it moved once".

**Not required at v84:** the Potential/Enhancement equip-serialiser rewrite. That is v88, and therefore
a v92 problem. `[INFERENCE from FACT-sourced]` *(Noted for §9: Cosmic's equip serialisation is
centralised in **one** function — `PacketCreator.addItemInfo()` at `:396/400` with **29 call sites all
routing through it** `[FACT-measured]` — so when v92 does force it, it is a single-function edit.)*

### 3.4 NGS / CRC / Themida `[FACT-sourced]`

- **No NGS.** nProtect GameGuard was removed at GMS v0.69 and replaced by AhnLab HackShield, which stayed
  until v115. NGS/BlackCipher is a 2017+ system — **irrelevant to v83 and v84 alike.**
- **HackShield ships as deletable files** (`HShield/`, `ASPLnchr.exe`); the standard v83 recipe this
  project already follows deletes them. Same for v84.
- **CRC is unchanged.** `CHECK_CRC_RESULT` exists identically in both v83 and v84 tables; Cosmic already
  implements it (`SendOpcode.java:50`).
- **Themida generation is the same** (2.x CISC-2, the "fully unvirtualizable" tier through ~v111).
- **v84's one new gate is trivial:** v84 introduced the GameLauncher — *"Running MapleStory.exe will now
  take players to the Nexon website"*. It is an `argv` check, bypassed with `MapleStory.exe GameLaunching`
  ([msupdate](https://msupdate.wordpress.com/2010/03/30/gms-v84-content-notes-neo-city-expansion-ui-updates/),
  [RaGEZONE 2010](https://forum.ragezone.com/threads/discussion-no-more-login-screen-for-v84.657229/)).

### 3.5 Sourcing the v84 table

| Source | What it gives |
|---|---|
| [Chronicle20/atlas](https://github.com/Chronicle20/atlas) | `gms_v84.yaml`, `template_gms_84_1.json`, `discover_gms_v84.md` — IDA-derived, switch-case constants read from the binary |
| [Riremito/JMSv186](https://github.com/Riremito/JMSv186/tree/master/properties/packet) | `GMS_v84_{Client,Server}Packet.properties` — **an independent second table** |

**Phase 0 cross-validates the two against each other.** Two independent tables that agree is the
strongest position available, and it removes IDA work from the critical path entirely.

> ⚠ **Known caveat to carry into Phase 2:** atlas's earlier `v84-packet-delta.md` claimed the *serverbound*
> enum was stable v83→v84, then **retracted it** after decompiling each sender: *"that assumption was
> wrong… the authoritative inbound opcodes live in `template_gms_84_1.json`, not this table."*
> **Use the deployed template, not the prose analysis.** `[FACT-sourced]`

---

## 4. CLIENT

| | v83 (today) | **v84 (target)** |
|---|---|---|
| Retail installer on disk | ✅ | ✅ `GMSSetupv84.exe` (1.76 GB) + `ManualPatcherv84.exe` |
| Public localhost-patched client | ✅ ubiquitous | ⚠ **one login-walled entry** (Riremito's bundle: v61–v111 incl. **83, 84**, 91, 95) |
| Public IDB | ✅ | ✅ (RaGEZONE IDB library covers v61–v89) |
| Emulator targeting the v84 client | — | ✅ [Riremito/JMSv186](https://github.com/Riremito/JMSv186) — `GMS084 \| 2010-03-30 \| Evan`, active |

> **Phase 0's real job is de-risking client acquisition**, because it is the one place v84 is *worse*
> supplied than v83. Three independent routes, tried in order:
> 1. Riremito's `GMS_v84.1_L.exe` localhost build.
> 2. Patch `GMSSetupv84.exe` ourselves — same Themida generation as v83, same technique, and **a public
>    v84 IDB exists**, which is exactly what the v83 client patching already relied on.
> 3. [Riremito/LocalHost](https://github.com/Riremito/LocalHost) — a **version-agnostic** connection
>    redirector that rewrites outbound connections to 127.0.0.1 without touching the packed exe.
>
> Route 3 means **client acquisition cannot hard-block the project**, which is why Phase 0 is only 2–4
> days despite this being the weakest link. `[INFERENCE from FACT-sourced]`

---

## 5. WZ — base swap and backport

### 5.1 What v84 actually deletes — the record, corrected `[FACT-measured]`

Settled against real WZ nodes, then confirmed against maplestory.io's **detail** endpoint (which reads
actual map data; the *list* endpoint is built from `String.wz` names and wrongly reports deleted maps as
present — a trap this project should not fall into twice):

```
GMS/83/map/970030100 -> 200      GMS/84/map/970030100 -> 404      GMS/92/map/970030100 -> 404
```

| map | v83 | v84 | verdict |
|---|:-:|:-:|---|
| `970030100` / `970042711` Boss Rush | 200 | 404 | **deleted** |
| `109090001` Sheep Ranch **Lobby** | 200 | 404 | **deleted** |
| `925020610` Mu Lung Dojo **6th Floor** | 200 | 404 | **deleted** |
| `925020000` Dojo entrance | 200 | 200 | intact |
| `980000000` **Monster Carnival** | 200 | 200 | **intact — never deleted** |
| `970030000` Exclusive Training Center | 200 | 200 | intact |
| `910040000` Sheep Ranch main | 200 | 200 | intact |

> **Correction that matters for scoping:** the long-standing claim that *"the whole Monster Carnival
> series, Mu Lung Dojo and Sheep Ranch"* were deleted is **wrong**. Monster Carnival is fully intact;
> only **one** Dojo floor and only the Sheep Ranch **lobby** maps went.
> **832 map `.img` nodes are deleted, and 810 of them (97%) are one feature: Boss Rush.**

| Group | count | What |
|---|---:|---|
| `970030100`–`970042711` | **810** | **Boss Rush** |
| `Map0/0000xxxxx`,`0010xxxxx` | 17 | unnamed/dev maps |
| `109090001`–`109090004` | 4 | Sheep Ranch Lobby |
| `925020610` | 1 | Mu Lung Dojo 6th Floor |
| `Map.wz/Obj/tutorial_jp.img` | 1 | JP tutorial objects |

Cosmic genuinely implements Boss Rush `[FACT-measured]`:
```
constants/id/MapId.java:213-214   BOSS_RUSH_MIN = 970030100;  BOSS_RUSH_MAX = 970042711;   <- exact match
scripts/event/BossRushPQ.js · scripts/npc/{9000021,9000037,9977777}.js
scripts/portal/{raid_rest,raid_stage}.js · server/life/MobSkill.java
```

### 5.2 Backport volume `[FACT-measured]`

| .wz | removed (v83-only) | protect (live custom) | | .wz | removed | protect |
|---|---:|---:|---|---|---:|---:|
| Etc | 2,028 | 10 | | Quest | 39 | 229 |
| Map | 1,017 | 403 | | Npc | 31 | 5,985 |
| Mob | 674 | 172 | | String | 0 | **7,608** |
| Character | 136 | 2,991 | | UI | 7 | 45 |
| Item | 35 | 110 | | others | 2 | 68 |
| | | | | **TOTAL** | **3,969** | **17,633** |

**Client-side WZ work = 21,602 copy-roots.**

> **The *protect* set is the half nobody had counted, and it is the larger half.** It is the owner's own
> custom content — `Npc.wz/2112018.img/info/script`, `String.wz/Cash.img/5120033`,
> `Map.wz/Map/Map1/100000003.img/info/onUserEnter`. Under Route A it is untouched because the live tree
> *is* the base; under a v84 base **all 17,633 roots must travel.** This is the main reason Phase 3 is
> 2–3 weeks rather than days.

### 5.3 Two mitigations `[INFERENCE from FACT-measured]`

1. **The server's XML tree does not move.** Cosmic reads a 599 MB XML tree at `wz/`; the server does not
   care what version the *client* is. **All merge work already landed into `wz/` transfers unchanged.**
   Only the **client-side** binary WZ is rebuilt.
2. **`removed-list` + `protect-list` ARE the backport manifest.** Built for the opposite direction, but
   direction-agnostic, as is `WzMerge`. The deny/force lists and the 735-row collision triage are
   direction-specific and get redone.

---

## 6. EVAN — complete, with the known defects fixed

### 6.1 What v84 gives us for free

Everything the v83 work fought for becomes unnecessary: **both memory gate patches, `Basic.img/Tab8`,
the per-launch watch-daemon patcher, and the unreachable race-select RE.** On v84 Evan's creation
screen, skill window and dragon are native.

Specifically, the v83 character-creation blocker evaporates. v84 did not add a button — **it replaced
the screen** `[FACT-measured]`: `RaceSelect/{normal,knight,aran,aran1,BtSelect,textGL}` deleted;
`RaceSelect/{backgrnd,BtKnight,BtAran,BtEvan}` + `NewCharEvan` + `CharSelect/evan` added. That is why
adding `BtEvan` to a v83 client did nothing — v83's code never looks up `Bt*` names at all.

**And the server side is already done** `[FACT-measured]`:
```java
// CreateCharHandler.java:62
case 3: // Evan - v84 port. The v83 client does not send 3 on its own; ticket 15b is
    status = EvanCreator.createCharacter(c, name, face, hair + haircolor, skincolor, top, bottom, shoes, weapon, gender);
```
plus `client/Job.java:59-63` — `LEGEND(2000), EVAN(2001)`, `EVAN1(2200)…EVAN10(2218)`, all ten
advancements — and `server/maps/Dragon.java`, `MoveDragonHandler`, `Character.createDragon()`,
`SPAWN/MOVE/REMOVE_DRAGON` opcodes, `remainingSp[10]`, `sp VARCHAR(128)`.

### 6.2 The skill numbers `[FACT-measured]`

Counted from the server's own `wz/Skill.wz` XML:
```
2001.img = 27   +   (2200, 2210..2218) = 31   =   58 total entries
```
**The often-quoted "58 Evan skills" conflates two things.** `2001.img` is mount buffs (reusing
`MONSTER_RIDING`), event entries, and beginner-common skills Cosmic already implements. **The real Evan
job skills are 31**, of which ~22 are data-driven (`StatEffect` reads them straight from `Skill.wz`) and
**9 need Java — and Cosmic already implements 6 of those.** `constants/skills/Evan.java` declares 43
ids; 22 are referenced in effect logic.

**Remaining new skill work: 3** — Critical Magic `22140000`, Dragon Fury `22160000`, Soul Stone `22181003`.
Plus **16 missing id constants** to declare.

### 6.3 The six defects to fix — all verified in code, not accepted

Phase 5 fixes these rather than shipping around them.

| # | Defect | Verified |
|---|---|---|
| 1 | **Evan gains 0 HP/MP per level** | `[FACT-measured]` `Character.java:6328-6362` — the `levelUp()` if/else chain covers Beginner, WARRIOR/DAWNWARRIOR1, MAGICIAN/BLAZEWIZARD1, BOWMAN/THIEF/(1299–1500), GM, PIRATE/THUNDERBREAKER1, ARAN1 — **and has no Evan branch.** Evan's parent chain is `LEGEND(2000)→EVAN(2001)→EVAN1(2200)`, never `MAGICIAN(200)`, so `job.isA(Job.MAGICIAN)` is false and both `addhp`/`addmp` stay 0. **Fix: one `else if` branch.** |
| 2 | **`20011004` not recognised as a mount** | `[FACT-measured]` the mount test is `sourceid % 10000000 == 1004` (`StatEffect.java:1689`, `Character.java:2788`). For Evan's `MONSTER_RIDER = 20011004` that yields **11004, not 1004** → test fails. Same root cause at `Character.java:7345`: `mountid = getJobType() * 10000000 + 1004` gives `20001004`, not `20011004`. **Fix at the shared predicate, not per caller.** |
| 3–5 | **Three mounts cast and do nothing** — `20011018` Yeti Rider, `20011019` Witch's Broomstick, `20011031` Balrog | flagged by ticket 11; almost certainly downstream of #2 `[INFERENCE]` — verify after fixing #2 before writing separate code |
| 6 | **Dragon equips / Mir saddles show `MISSING NAME`** | `String.wz` `Eqp/{Dragon,Taming}` rows deliberately left by ticket 04 for ticket 05; also `UIWindow.img/Equip/{DragonEquip,BtDragonEquip}` handed to ticket 14. **Fold both into Phase 5.** |

> Defects 2–5 are one bug wearing four hats. Fix the shared predicate once — patching each mount
> separately would leave every future mount broken the same way.

---

## 7. v84 CONTENT — complete means *reachable*

Per the owner: content that cannot be reached in game is a **phase-1 problem to solve, not defer**.

### 7.1 The delta `[FACT-measured]`

| Category | v83 | v84 | added |
|---|---:|---:|---:|
| Maps | 4,411 | 4,504 | **93** |
| Mobs | 1,597 | 1,638 | **41** |
| NPCs | 1,733 | 1,760 | **27** |
| Items | 12,578 | 12,990 | **412** |
| Quests | 2,817 | 3,015 | **198** |
| **TOTAL** | | | **771** |

Content: Evan's world (Dream/Lush/Lost Forest), **Crimson Sky** (Leafre dragon expansion — Skelegon,
Skelosaurus, Leviathan, Cornians), **Neo City 2227**, 8 mounts, and a large share of the 412 items being
hairstyles/cosmetics.

### 7.2 The three known reachability problems — with proposed fixes

These are already-discovered, real, and are Phase 6 work items:

| Problem | Detail | Proposed fix |
|---|---|---|
| **Crimson Sky has no travel route** in *either* v83 or v84 | 22 maps merged and confirmed present, but nothing in the WZ routes a player there | Add a portal/NPC travel route. This is **custom content by necessity** — v84 shipped it unreachable too, so there is no "correct" upstream to copy. Owner decision on where it hangs (Leafre is the natural anchor). |
| **48 of 63 v84 non-Evan quests shipped pre-expired** | v84 itself shipped them with end dates already in the past | Policy call, owner's: (a) strip the date gate, (b) rewrite dates to a live window, or (c) leave them out. **Recommend (b)** — it preserves the content and the quest text without pretending the dates never existed. |
| **9 Evan-locked, 5 behind a dead upstream quest, 1 (`19011`) currently acceptable** | of the same 63 | The 9 unblock automatically once Phase 5 lands (**this is why classes precede their quests, §2.2**). The 5 need their upstream prerequisite supplied or the chain re-rooted. |

### 7.3 Quest scripting load `[FACT-measured]`

```
scripts/ total .js = 1,940     npc 708 · portal 461 · reactor 292 · quest 275 · event 108 · map 90 · item 2
```
**275 quest scripts serve 2,817 quests = 9.8%.** Applied to +198 new quests → **≈19 new scripts**; the
rest are pure WZ data.

### 7.4 Cash shop — data-driven, so nearly free `[FACT-measured]`

```
server/CashShop.java:242    for (Data item : etc.getData("Commodity.img").getChildren())
UseCashItemHandler.java:102 int itemType = itemId / 10000;     // 24 types handled, 0 per-id special cases
```
`CashItemFactory` loads the **whole catalogue from `Etc.wz/Commodity.img`**, and effects dispatch by
**type**, not id. **New cash items of an existing type work with zero Java.** Only genuinely new *types*
need a branch. Ticket 04's decision to decline most of the 10,638 Etc roots (1,518 SNs pointing at
out-of-scope items = dead shop buttons) stays correct **until Phase 6 makes those items exist** — then
re-run that decision. `[INFERENCE]`

### 7.5 Drops and SQL

Precedent already set and it works: `153-crimson-sky-drop-data.sql`, 776 rows, as a **new Liquibase
changeset** — editing `152-drop-data.sql` in place fails checksum validation because it has already run.
New v84 mobs follow the same pattern.

---

## 8. REGRESSION — how "keep what we have working" is proven

Acceptance criterion for Phases 3, 4 and 8. Existing assets: 27 test files under `src/test/java`, 39
Liquibase changesets, and the **`WzMerge hash` digest technique proven in ticket 04** — digest every
image in a `.wz` before and after a merge, assert only the named rows moved. `[FACT-measured]`

1. **Instrument check, FIRST.** Run the whole suite against the **current v83 server** and confirm it
   passes there. A suite that fails on both proves nothing. *(This is the rule this project learned the
   hard way, three times.)*
2. **Data integrity (automated).** Every root in `removed-list` ∪ `protect-list` present and
   digest-identical post-merge; any image that gained children appears on the manifest by name.
3. **Deleted-content playthrough (manual, batched).** Boss Rush, Monster Carnival, Mu Lung Dojo, Sheep
   Ranch — enter, fight, complete, exit.
4. **Class parity (scripted).** Every existing v83 class to 4th job; every 4th-job skill fires.
5. **Economy.** Shops, cash shop, storage, trade, hired merchant, drops — re-tested after Phase 2.
6. **Evan acceptance (Phase 5).** Creation → 2001 → 2200 → 2210–2218; all 58 entries; dragon renders and
   moves; all 8 mounts ride; **HP/MP increases on level-up** (defect #1); no `MISSING NAME`.

---

## 9. WHAT PHASE 1 LEAVES BEHIND FOR PHASE 2 — and what it does not

The instruction was to make phase 1 a rehearsal, not a throwaway. It largely is — **but I will not
overstate it**, because one part is genuinely non-transferable.

### ✅ Reusable assets — name them, build them deliberately

| Asset | Why it carries |
|---|---|
| **The opcode-remap procedure** — validate a published table against ground truth *before* using it (§1.1), then remap two enum files, then bring up login before field | version-independent method; it is exactly what v92 needs |
| **The two-source cross-validation habit** — atlas × Riremito at v84; atlas × Vertisy × Riremito at v92 | the discipline is the asset |
| **The WZ base-swap + backport workflow** — `WzMerge`, the `hash` regression gate, the deny/force list procedure | direction-agnostic tooling, already built |
| **`removed-list` + `protect-list`** | the 832 deleted maps are **still absent at v92** (§5.1) — the *same* manifest serves both migrations unchanged |
| **The client-validation checklist** (§4's three acquisition routes, localhost routing, protection removal) | identical shape at v92 |
| **The regression suite** (§8) | grows, never rewritten |
| **A written record of what actually broke** | the real deliverable — this project's failures have all been instrument failures, and that knowledge is version-independent |
| **The centralised-seam findings** — `addItemInfo()` 1 function/29 call sites; `parseMovement()` shared | pre-scouts v92's equip-serialiser and movement work |
| **All server-side Java**: `EvanCreator`, `Job.EVAN1–10`, extended SP, the six defect fixes, drop SQL, 1,940 scripts, the 599 MB `wz/` XML tree | version-independent by construction |

### ❌ What phase 1 will NOT teach us — stated plainly

- **Not one opcode value.** Zero v84 values survive to v92 `[REPORT-peer]`, which my own staircase
  measurement corroborates — the shifts are cumulative. The v84 table is a *different* table, not a
  partial v92 one.
- **Not the equip serialiser.** Potential/Enhancement is v88. Phase 1 never touches it, so the single
  largest v92 structural change gets **no** rehearsal. It is scouted (§3.3) but not exercised.
- **Not Dual Blade.** No katara, no dual-wield, no `getWeaponType()` work.
- **Not the ~15 new v92 packet families**, nor the Family/MTS/Hammer unknowns.
- **Not the HD mod's portability.** v84 and v92 break it for *different* reasons — v84 by offsets alone,
  v92 by offsets **plus** the v89+ Gr2D resolution-path rewrite.

> **Honest summary:** phase 1 rehearses the **workflow** thoroughly and the **protocol content** not at
> all. That is still worth having — the workflow is where this project has actually been getting hurt —
> but nobody should expect the v92 opcode work to be cheaper for having done v84.

---

## 10. PHASE 2 — v92 sketch only

Recorded so it is not lost. **Not costed here, and its scope must not leak into phase 1.**

- **Protocol is the cliff**, not the classes: **421 of 468 opcodes differ (~90%)** `[REPORT-peer]`, plus
  the Potential-driven equip-serialiser rewrite touching every item-bearing packet, plus the movement
  prefix (already solved at v84 — one genuine carry-over).
- **Classes are a known-size port, not research** `[REPORT-peer]`: **Dual Blade = 26 skills** (430:3,
  431:4, 432:4, 433:6, 434:9) + 2 DB-exclusive Rogue ids (`4001334`, `4001344`) = 32.
  **`PHANTOM_BLOW` is post-Big-Bang — explicitly out of scope.**
- **Sourcing** (all AGPLv3, same licence as Cosmic, so porting is permitted):
  [MapleStoryA/orion-server](https://github.com/MapleStoryA/orion-server) (v90, OdinMS lineage, active
  2026-07, **complete Dual Blade**, ships `Skill/430-434.img.xml`) for the *mechanics*;
  [Chronicle20/Vertisy](https://github.com/Chronicle20/Vertisy) (v92, **Cosmic's exact package layout**,
  ~60% of DB logic, **zero katara**) for the *idiom*. [iw2d/kinoko](https://github.com/iw2d/kinoko) has
  **no licence → cannot be ported.**
- **Cosmic's concrete one-line blocker:** `ItemInformationProvider.getWeaponType()` maps category 34 →
  `NOT_A_WEAPON`; orion has `case 34: return MapleWeaponType.KATARA`. Trivial, but it gates everything.
  Also: no 430–434 in `client/Job.java`, and **watch the autoban** — Cosmic bans a weapon in a non
  −10/−11 slot, so the katara must be whitelisted or legitimate Dual Blades get banned.
- **Katara needs three things:** equip forced to slot −10 for `itemId/10000 == 134`, rejected unless job
  430–434 with a dagger in −11; mastery read from the **shield** slot; `attackCount *= 2` on basic
  attacks (again for Mirror Image).
- **Dead ends, do not chase:** LotusMS v88 (*"currently not working"*), TropikMS v90 (author:
  *"i don't recommend anyone use this source"*), FusionSource v90 (*"Recvs are wrong"*), LocalMS v88
  server source **not public**, maplestorylibary.weebly.com repack list dead.
- **Note:** the 832 deleted maps are gone at v92 too, so **§5's backport is needed either way** — doing
  it at v84 is not wasted.

---

## 11. RECOMMENDATION

**Deliverable 3 was answered by the owner** — *"v84 feature complete is more important"* — so this is not
re-litigated. **v84 as a full destination.** I agree with it, and here is why it holds up under the
"fully working" rule rather than the "cheaper" one:

- v84 makes Evan **native**. Every v83 workaround — two memory gate patches, `Tab8`, the per-launch
  patcher, and a character-creation blocker with *no published prior art anywhere* — stops existing
  rather than being worked around. That is a completeness argument, not a cost argument.
- The protocol step is bounded and published: **231 of 363 opcodes**, a clean staircase, two independent
  tables, identical login band, unchanged crypto.
- Nothing found is a showstopper. The weakest link is **client acquisition** (§4), and it has three
  independent routes, one of which (`Riremito/LocalHost`) does not require patching the binary at all.

**Honest cost: ≈8–12 weeks focused** for Phases 0–7, plus the HD client as a separately-scoped,
open-ended, gate-nothing tail.

**One thing I would push back on, gently and once:** phase 1 is a real rehearsal of the *workflow* and
not at all of the *protocol content* (§9). If the long-run goal is v92, the v84 protocol work is
genuinely spent, not banked. That is an acceptable price for having Evan working properly and soon — and
the owner has explicitly chosen it — but it should be a known price, not a surprise later.

**Start with Phase 0.** 2–4 days, one client launch, touches nothing live, and it retires the only
genuinely weak link before any irreversible work begins.

---

## Appendix A — Reproducing the measurements

```bash
# the crux number
python scratchpad/verify17.py     # atlas-vs-Cosmic validation, then the v83->v84 shift map
                                  # -> clientbound 176/220 differ, serverbound 55/143, staircase 0..+10

# opcodes
grep -cE '^\s*[A-Z_0-9]+\(' src/main/java/net/opcodes/SendOpcode.java     # 307
grep -cE '^\s*[A-Z_0-9]+\(' src/main/java/net/opcodes/RecvOpcode.java     # 178
diff <(cosmic SendOpcode) <(HeavenMS-v83-upstream SendOpcode)             # no output
grep -c 'addItemInfo' src/main/java/tools/PacketCreator.java              # 29 call sites, 1 function

# WZ
wc -l docs/wz-baseline/removed-list/*.txt   # 3,969      protect-list/*.txt   # 17,633
grep -c '\.img$'       docs/wz-baseline/removed-list/Map.txt              # 833
grep -c '98[0-9]\{6\}' docs/wz-baseline/removed-list/Map.txt              # 0   <- Monster Carnival INTACT
curl -s -o /dev/null -w '%{http_code}' https://maplestory.io/api/GMS/83/map/970030100   # 200
curl -s -o /dev/null -w '%{http_code}' https://maplestory.io/api/GMS/84/map/970030100   # 404

# Evan
python scratchpad/skillcount.py   # 87 books, 620 ids; Evan 2001=27 + 2200..2218=31 = 58
python scratchpad/skillratio.py   # 562 constants declared, 394 referenced, 168 pure data
sed -n '6328,6362p' src/main/java/client/Character.java     # levelUp(): no Evan branch -> 0 HP/MP
grep -n '10000000' src/main/java/server/StatEffect.java     # :1689 sourceid % 10000000 == 1004

# content / scripts
python scratchpad/delta92.py      # v83/v84/v92 content deltas (+771 at v84)
python scratchpad/probe92.py      # per-map 83/84/92 presence probe
find scripts -name '*.js' | wc -l                           # 1,940 (quest 275 of 2,817 = 9.8%)
```

Helper scripts in this session's scratchpad: `verify17.py`, `skillcount.py`, `skillratio.py`,
`delta92.py`, `probe92.py`.

## Appendix B — Sources

**v84 protocol** [Chronicle20/atlas](https://github.com/Chronicle20/atlas) —
[discover_gms_v84.md](https://github.com/Chronicle20/atlas/blob/master/docs/packets/registry/discover_gms_v84.md),
[v83 template](https://github.com/Chronicle20/atlas/blob/master/services/atlas-configurations/seed-data/templates/template_gms_83_1.json),
[v84 template](https://github.com/Chronicle20/atlas/blob/master/services/atlas-configurations/seed-data/templates/template_gms_84_1.json) ·
[Riremito/JMSv186](https://github.com/Riremito/JMSv186) ([version list](https://github.com/Riremito/JMSv186/blob/master/Readme_VersionList.md), `properties/packet/GMS_v84_*`) ·
[Riremito/LocalHost](https://github.com/Riremito/LocalHost)

**Phase-2 sourcing (AGPLv3)** [MapleStoryA/orion-server](https://github.com/MapleStoryA/orion-server) ·
[Chronicle20/Vertisy](https://github.com/Chronicle20/Vertisy) ·
[odasm/maplev90](https://github.com/odasm/maplev90) ·
[xDazedGamingx/LostStoryV90](https://github.com/xDazedGamingx/LostStoryV90) ·
[iw2d/kinoko](https://github.com/iw2d/kinoko) *(no licence — not portable)*

**Client / protections** [Localhost Workshop](https://forum.ragezone.com/threads/localhost-workshop.1202021/) ·
[some localhost clients (incl. v84)](https://forum.ragezone.com/threads/some-localhost-clients-kms-jms-cms-twms.1225637/) ·
[Client/Localhost Archive](https://forum.ragezone.com/threads/maplestory-client-localhost-archive.1101897/) ·
[IDB library v61–v89](https://forum.ragezone.com/threads/library-of-idbs-for-different-versions-with-named-addresses.987815/) ·
[v84 GameLauncher](https://forum.ragezone.com/threads/discussion-no-more-login-screen-for-v84.657229/) ·
[P0nk/Cosmic-client](https://github.com/P0nk/Cosmic-client) ·
[444Ro666/MapleEzorsia-v2](https://github.com/444Ro666/MapleEzorsia-v2)

**Patch record** [msupdate v84](https://msupdate.wordpress.com/2010/03/30/gms-v84-content-notes-neo-city-expansion-ui-updates/) ·
[msupdate v88](https://msupdate.wordpress.com/2010/07/20/gms-v88-update-notes/) ·
[msupdate v92](https://msupdate.wordpress.com/2010/11/22/gms-v92-compatibility-update/) ·
[Big Bang = v93](https://maplenewsnetwork.wordpress.com/2010/12/06/gmsv-93-patch-notes-big-bang-part-1/) ·
[DigitalTQ patch history](https://www.digitaltq.com/maplestory-patch-history-what-came-when)

**v83 Evan prior art (now moot under v84)**
[Twdtwd quote](https://forum.ragezone.com/threads/v83-in-evan.1107098/post-8658081) ·
[[Release] [v83] Evans](https://forum.ragezone.com/threads/release-v83-evans.1108138/) ·
[Evan class for v83 (2024)](https://forum.ragezone.com/threads/evan-class-for-v83.1226050/) ·
[New job v83 help](https://forum.ragezone.com/threads/new-job-v83-help.1131763/)

**This repo** `docs/wz-baseline/{SUMMARY.md,removed-list,protect-list,add-list,modified-list,merge-lists}` ·
`docs/work-plan/tickets/{11,11b,11e}` · `docs/EVAN-DUALBLADE-SCOPE.md` ·
`porting-resources/SCOPE-V84-UPGRADE.md` · `porting-resources/docs/{02,07,08,13,16}` ·
`tools/evan-gate-patch.log` · `tools/evan-gate-dll/evan-gate.c`
