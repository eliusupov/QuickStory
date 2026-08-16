# Ticket 19 — Server source survey: what can we port for v84?

**Scope as revised (2026-08-16):** target is the **GMS v84 client, feature-complete**, Cosmic as
the base. Owner verbatim: *"i want evan first, dual blade can be after"* and *"v84 feature complete
is more important"*. v92 / Dual Blade is deferred Phase 2 and is covered in §7 only.

**Evidence labels:** `[FACT-measured]` = measured on this machine, command reproducible ·
`[FACT-sourced]` = external source, URL given · `[REPORT]` = a claim made by a source, not verified
by me · `[INFERENCE]` = reasoning from the above, marked as such · `[NOT-FOUND]` = searched for,
not found.

> **Tooling note.** This session's WebSearch budget was exhausted before the first query. All
> external research below was done through the authenticated GitHub API (`gh search repos`,
> `gh search code`, `gh api`) and WebFetch. RaGEZONE returns HTTP 403 to WebFetch, so forum
> threads could not be read — see §6.

---

## 0. Lead findings

1. **No public GMS v84 opcode table exists.** `[NOT-FOUND]` Searched GitHub repos and code, and the
   community MapleShark script archive. The archive holds GMS v83, v85, v86 and v88 — **v84 is
   skipped**. This is the one genuine showstopper in Phase 1 and it is addressed in §1.
2. **But the v84 delta is now bounded, and it is small.** Measured against two independent v90
   sources: the v83→v90 opcode drift is a monotonic **insertion cascade** totalling **+58 on send,
   +43 on recv over seven versions** — roughly **8 insertions per version**. Opcodes 0–~30 do not
   move at all, even at v90. `[FACT-measured]` §1.2
3. **Cosmic is already ahead of every v83-lineage source on Evan Java code.** Cosmic has
   `EvanCreator.java`, `Dragon.java`, Evan branches in `StatEffect`, `MagicDamageHandler`,
   `SkillEffectHandler`, `AbstractDealDamageHandler`, `CreateCharHandler`. MapleSolaxia-90 has
   strictly less. Do not port Evan *Java* — port Evan **quest scripts**. §3
4. **The Evan gap is scripts, and it is 45 files.** `MapleStoryA/orion-server` ships **58** Evan
   quest scripts (22xxx); Cosmic ships **13**. Cosmic has **zero** of the 221xx range — the Evan
   2nd–4th job growth chain. `[FACT-measured]` §3.2
5. **Correction to a peer finding.** Vertisy's `sendops-92.properties` and `recvops-92.properties`
   are **byte-identical** to its v90 files. Vertisy has **no real v92 opcode table**; its "v92"
   label is aspirational. `[FACT-measured]` §7.2
6. **Two people publicly announced HeavenMS→v90 ports. Both repos are empty.** Zero commits, zero
   bytes, 2018 and 2019. `[FACT-measured]` §6.1

---

## 1. The v83 → v84 opcode delta

### 1.1 Is there a public v84 table? No. `[NOT-FOUND]`

Searched:

| Where | Query | Result |
|---|---|---|
| GitHub repos | `maplestory v84` | **0 results** |
| GitHub repos | `maplestory v85` | 1 result, `v3921358/EvanMS085` — CMS (Chinese), not GMS |
| GitHub repos | `maplestory v87` | **0 results** |
| GitHub code | `SendOpcode v84 language:java` | **0 results** |
| GitHub code | `SendOpcode v85 language:java` | **0 results** |
| [`LankMasterFlex/mssp`](https://github.com/LankMasterFlex/mssp) | MapleShark community script archive | GMS dirs exist for **v83, v85, v86, v88** — **no v84** |
| [`forum.ragezone.com`](https://forum.ragezone.com) | thread fetch | **HTTP 403** to WebFetch |

The MapleShark archive's version coverage is worth stating exactly, because it shows how thin the
v84-era record is `[FACT-sourced]`
([tree](https://github.com/LankMasterFlex/mssp)):

```
GMS (locale 8) dirs: 40 75 81 82 83 85 86 88 94 98 99 101 102 104 106 108 109 110 111 115 ...
                                    ^^         ^^ no 84, no 87
opcode NAME tables (send/recv.properties): only v109 and above
```

So even for v85/v86/v88 the archive gives **packet-structure scripts, not opcode name tables** —
a handful of individual packets each.

### 1.2 What the delta actually looks like — measured `[FACT-measured]`

Three independent higher-version sources were compared against Cosmic's live table
(`src/main/java/net/opcodes/{Send,Recv}Opcode.java`, 307 send + 178 recv = 485 entries).

**Source A — [`Chronicle20/Vertisy`](https://github.com/Chronicle20/Vertisy)** ships its opcode table
as data (`sendops-90.properties`, 341 entries; `recvops-90.properties`, 189 entries) in Cosmic's own
naming convention, so a name-keyed diff is high fidelity:

```
SEND  Cosmic-v83 (307) vs Vertisy-v90 (341):
      common names 277 | SAME value  27 | CHANGED value 250 | new at v90  64
RECV  Cosmic-v83 (178) vs Vertisy-v90 (189):
      common names 149 | SAME value  19 | CHANGED value 130 | new at v90  40
```

**Source B — [`Maple000/MapleSolaxia-90`](https://github.com/Maple000/MapleSolaxia-90)**, independent,
2016, hardcoded enum:

```
SEND  Cosmic-v83 (307) vs Solaxia-90 (283): common 273 | SAME 21 | CHANGED 252
RECV  Cosmic-v83 (178) vs Solaxia-90 (156): common 152 | SAME 21 | CHANGED 131
```

**Source C — [`MapleStoryA/orion-server`](https://github.com/MapleStoryA/orion-server)**, independent,
different package family, different constant names. Spot-check on the two anchors that share a name:

| | Cosmic v83 | Solaxia-90 | orion-server v90 | Vertisy v90 |
|---|---|---|---|---|
| `SET_FIELD` / `WARP_TO_MAP` | 125 (0x7D) | **140** | **140** | **140** |
| `INVENTORY_OPERATION` | 29 | **30** | **30** | **30** |

Three independent v90 sources agree on the v90 values. The v90 table is trustworthy.

### 1.3 The drift profile — the finding that bounds v84 `[FACT-measured]`

Drift is **not uniform**. Bucketing every common opcode by its v83 number and reporting
`v90_value − v83_value`:

```
SEND                                    RECV
v83   0- 31  n=29  drift  +0 .. +1      v83   0- 31  n=25  drift  +0 .. +1
v83  32- 63  n=28  drift  +0 .. +3      v83  32- 63  n=24  drift  +1 .. +6
v83  64- 95  n=30  drift  +3 .. +5      v83  64- 95  n=25  drift  +6 .. +11
v83  96-127  n=29  drift  +3 .. +15     v83  96-127  n=19  drift +10 .. +20
v83 128-159  n=28  drift  +0 .. +15     v83 128-159  n=18  drift +20 ..
v83 160-191  n=29  drift +17 .. +28     v83 160-191  n=15  drift +23 .. +32
v83 192-223  n=24  drift +28 .. +34     v83 192-223  n=13  drift +32 .. +38
v83 224-255  n=13  drift +34 .. +40     v83 224-255  n= 7  drift +39 ..
v83 256-287  n=25  drift +46 .. +49
v83 288-319  n=27  drift +48 .. +51
v83 320-351  n=14  drift +51 .. +55
v83 352-383  n= 1  drift +58
```

This is the signature of **pure insertion**: Nexon added ~58 send opcodes and ~43 recv opcodes
between v83 and v90, each insertion shifting everything above it up by one. Nothing was reordered
or removed.

**The 46 opcodes that do not move at all, even at v90** (27 send + 19 recv), are almost exclusively
the **login and character-select protocol**: `LOGIN_STATUS`, `SERVERLIST`, `CHARLIST`, `SERVER_IP`,
`CHAR_NAME_RESPONSE`, `ADD_NEW_CHAR_ENTRY`, `CHANGE_CHANNEL`, `PING`, `CHECK_CRC_RESULT`,
`RECOMMENDED_WORLD_MESSAGE`, `CREATE_CHAR`, `CHAR_SELECT`, `PLAYER_LOGGEDIN`, `RELOG`, … The
divergence begins at the first channel-server opcode (`CHECK_SPW_RESULT` 28→29) and compounds
upward. `[FACT-measured]`

### 1.4 Corroboration from real sniffs `[FACT-sourced]`

The MapleShark archive lets us watch one packet — `SET_FIELD`, the character-data packet, the
largest and most breakage-prone in the protocol — move version by version. Each dir contains the
parse script for that packet under its opcode as the filename
([Scripts/8/](https://github.com/LankMasterFlex/mssp/tree/master/Scripts/8)):

| Client | `SET_FIELD` opcode | Source |
|---|---|---|
| v83 | 0x7D = **125** | `mssp Scripts/8/83/Inbound/0x007D.txt` — **matches Cosmic exactly** |
| v85 | 0x81 = **129** | `mssp Scripts/8/85/Inbound/0x0081.txt` |
| v86 | 0x85 = **133** | `mssp Scripts/8/86/Inbound/0x0085.txt` |
| v88 | 0x88 = **136** | `mssp Scripts/8/88/Inbound/0x0088.txt` |
| v90 | **140** | Solaxia-90 + orion-server + Vertisy, all three |

Cosmic's v83 table matching a real v83 sniff is an independent validation that Cosmic's table is
correct GMS v83, not drifted.

**The structural warning in the same data** `[FACT-sourced]`: the v85 script begins directly with
`AddInt("Channel")`, exactly as Cosmic writes it. The v86 script begins `AddField("Unk", 18)` and
the v88 script `AddField("Unk", 22)` — a new prefix block appears **at v86** and grows by v88.

**`[INFERENCE]`** v84 sits below that structural break. For `SET_FIELD` specifically the v83→v84
change is very likely a **renumber only, in the range +0 to +4**, with no change to the packet
body. That is the single most encouraging fact in this ticket.

### 1.5 What this means for the v84 work `[INFERENCE]`

- v83→v90 is 58 send insertions over 7 versions ≈ **~8 per version**. Expect the v84 delta to be
  on the order of **5–10 inserted opcodes**, shifting a subset of the table by 0–8.
- The whole login/charselect path — the first thing you need working to even reach a channel —
  is very likely **unchanged**. You should be able to log in against a v84 client before touching
  a single channel opcode.
- Compare: the peer session's figure for v92 is ~421 of 468 changed. Against v84 we are talking
  about a small renumber, not a remap.
- **This is derivation work, not research work.** The table is not published; it must be extracted
  from the v84 client the owner already has, or sniffed. That is the Phase-1 critical path item.

### 1.6 The mechanism to adopt regardless — externalised opcode tables `[FACT-sourced]`

Vertisy solved the "server pinned to one client version" problem structurally. Its
`net/SendOpcode.java` declares a **bare enum with no values**:

```java
public enum SendOpcode implements IntValueHolder{
	LOGIN_STATUS,
	GUEST_ID_LOGIN,
	ACCOUNT_INFO,
	...
```

and loads the numbers at startup via `tools/ExternalCodeTableGetter` from
`sendops-<version>.properties`
([SendOpcode.java](https://github.com/Chronicle20/Vertisy/blob/master/src/main/java/net/SendOpcode.java)).

**Recommendation.** Adopt this pattern in Cosmic *before* attempting the version bump. It converts
the migration from "edit 485 Java constants and rebuild" into "ship a new `.properties` file", and
it lets the owner keep a working v83 table alongside a v84 one and A/B them against the client.
It is a ~60-line change (the enum, a loader, a config key) and it is the highest-leverage
structural thing in this ticket. Vertisy carries the same OdinMS AGPL header as Cosmic, so the
loader itself is licence-compatible to copy.

---

## 2. Sourcing table

Lineage legend: **Cosmic-line** = `net.server.channel.handlers` / `constants.skills` /
`server.quest.actions` package layout, i.e. OdinMS → Solaxia → HeavenMS → Cosmic. **handling-line**
= the OdinMS descendant that restructured networking into `handling.channel.handler` +
`networking.packet.SendPacketOpcode`; shares `client.MapleCharacter`, `constants.skills`,
`server.MapleInventoryManipulator` with Cosmic but not the net layer.

| Source | URL | Ver | Lang | Lineage | Licence | Last activity | Size | What it gives us | Portability |
|---|---|---|---|---|---|---|---|---|---|
| **MapleSolaxia-90** | [Maple000/MapleSolaxia-90](https://github.com/Maple000/MapleSolaxia-90) | v90 | Java | **Cosmic-line, direct** (Solaxia is HeavenMS's parent) | none stated; files carry OdinMS **AGPL-3** header | 2016-07-26, 6 commits | 5.4 MB | A v90 opcode table in Cosmic's exact enum; 1,100 scripts; Evan constants + Evan quests/portals | **Drop-in** for constants; opcode table is reference data |
| **Vertisy** | [Chronicle20/Vertisy](https://github.com/Chronicle20/Vertisy) | v90 (labelled v90/v92 — see §7.2) | Java | **Cosmic-line**, JDK 21 like Cosmic | none stated; OdinMS **AGPL-3** header | 2024-02-17, unmaintained | 66 MB | **Externalised opcode-table mechanism** (§1.6); `sendops/recvops-90.properties`; Cosmic-identical script tree | **Drop-in** for the loader pattern |
| **orion-server** | [MapleStoryA/orion-server](https://github.com/MapleStoryA/orion-server) | v90 | Java | handling-line | none stated | **2026-07-29, active** | 38 MB | **58 Evan quest scripts**; 500 quest / 931 npc / 766 portal total; full Blade\* constants | Scripts **drop-in**; Java **moderate** (net layer differs) |
| **maplev90** | [odasm/maplev90](https://github.com/odasm/maplev90) | v90 | Java | handling-line | none stated; OdinMS **AGPL-3** header | 2016-12-04 | 44 MB | Full Dual Blade incl. **Katara slot plumbing + DB char-creation packet**; Evan SP tables; 922 npc / 500 quest / 758 portal / 293 reactor scripts | `constants.skills` **drop-in**; inventory/creation code **moderate** |
| **kinoko** | [iw2d/kinoko](https://github.com/iw2d/kinoko) | **v95** | Java 21 | **from scratch** (`kinoko.*`) | **none — all rights reserved** | 2026-05-22, 95★ | 3.2 MB | Complete modern Dual Blade skill handling, 25 dispatch cases | **Full rewrite** to port; licence unusable |
| **Henesys** | [Descended/Henesys](https://github.com/Descended/Henesys) | v95 | Java | from scratch (`henesys.*`) | none stated | 2025-12-20 | 1.0 MB | v95 job scaffolding; small, likely incomplete | **Full rewrite** |
| **BeiDou** | [BeiDouMS/BeiDou-Server](https://github.com/BeiDouMS/BeiDou-Server) | **v83** | Java 21 + Vue | **explicitly forked from Cosmic** (README: *"本项目基于Cosmic"*) | **AGPL-3.0** | **2026-08-16, 637★** | 215 MB | Nothing v84+; it is a Cosmic sibling. Value is infrastructure: REST API, Swagger, i18n WZ/script paths | N/A for v84 content |
| **EvanMS085 / ZeroMS085** | [v3921358/EvanMS085](https://github.com/v3921358/EvanMS085) | **CMS v85** | Java | handling-line | none stated | 2025-06-06 | 16 MB | v85-era Evan, but **CMS not GMS** — opcodes and content IDs do not transfer | **Not usable** for GMS v84 |
| **mssp** | [LankMasterFlex/mssp](https://github.com/LankMasterFlex/mssp) | v83/85/86/88 GMS | MapleShark script | n/a | none stated | 2014-01-28 | small | Real sniffed packet structures at v83/85/86/88 — the §1.4 evidence chain | Reference data only |
| **HeavenMS** | [ronancpl/HeavenMS](https://github.com/ronancpl/HeavenMS) | v83 | Java | Cosmic-line, Cosmic's parent | AGPL-3.0 | 2019-12-28, 1213★ | — | Nothing v84+ | N/A |

**Ruled out on inspection, do not revisit:**

| Source | Why |
|---|---|
| [davidlafriniere/HeavenMSv90](https://github.com/davidlafriniere/HeavenMSv90) | **Empty repo**, 0 bytes, 0 commits `[FACT-measured]` |
| [mrscout144/HeavenMsV90-](https://github.com/mrscout144/HeavenMsV90-) | **Empty**, one `.gitattributes`, "Initial commit" only `[FACT-measured]` |
| Swordie family (v203–v246), Cellion v188, ElectronMS v316 | Post-Big-Bang. Jobs, skills, stat formulas and packets all rewritten. Nothing transfers to v84 |
| CMS/TMS sources (`CMS095`, `TWMS_118`, `dreamMS120`, …) | Different region: different opcodes, different content IDs |
| Non-Java (`gilmatok/Destiny` C#, `izarooni/NineToFive` C# v95, `3VNDR/hwabi` Zig v95, `v3921358/Rebirth` C++ v95) | Full rewrite for any port |

### 2.1 Licence position

Cosmic is **AGPL-3.0** (`LICENSE`, 544 lines, GNU AGPL v3) `[FACT-measured]`.

- **MapleSolaxia-90, maplev90, Vertisy** — no repo `LICENSE` file, but every source file carries the
  verbatim OdinMS AGPL-3 header (*"This program is free software… GNU Affero General Public License…
  version 3"*). `[FACT-sourced]` They are AGPL-3 derivatives of the same upstream Cosmic derives
  from. **Compatible.** Keep the headers on anything copied.
- **orion-server** — no licence file and no OdinMS header seen in the files inspected.
  `[INFERENCE]` It is an OdinMS descendant so AGPL almost certainly attaches upstream, but this is
  unstated. Its **scripts** (JavaScript quest files) are the part we want and are the part most
  clearly derived from the shared script corpus. Note the ambiguity; it is not a blocker.
- **kinoko** — **no licence at all** → all rights reserved by default. Do not copy code from it.
  Read it for understanding only. `[FACT-measured]`
- **BeiDou** — AGPL-3.0, clean.

---

## 3. Evan — where Cosmic already stands, and the actual gap

### 3.1 Do not port Evan Java. Cosmic is already the most complete v83-lineage Evan. `[FACT-measured]`

Cosmic's existing Evan surface:

```
src/main/java/client/creator/novice/EvanCreator.java      <- Evan character creation
src/main/java/server/maps/Dragon.java                     <- the dragon
src/main/java/constants/skills/Evan.java                  <- 43 skill constants
src/main/java/client/Job.java                             <- EVAN(2001), EVAN1..EVAN10 (2200-2218)
src/main/java/client/processor/stat/AssignSPProcessor.java<- Evan per-tier SP
src/main/java/server/StatEffect.java
src/main/java/net/server/channel/handlers/MagicDamageHandler.java
src/main/java/net/server/channel/handlers/SkillEffectHandler.java
src/main/java/net/server/channel/handlers/AbstractDealDamageHandler.java
src/main/java/net/server/channel/handlers/CancelBuffHandler.java
src/main/java/net/server/handlers/login/CreateCharHandler.java
src/main/java/server/maps/Mist.java
```

MapleSolaxia-90 — the closest-lineage higher-version source — has `constants/skills/Evan.java`,
`MapleJob`, `SkillFactory`, `GameConstants`, `MapleMist` and **no `EvanCreator` and no
`Dragon.java`**. `[FACT-measured]` Cosmic is strictly ahead.

The only Java-level Evan delta found anywhere is **four constants** Cosmic lacks. `orion-server`'s
and `maplev90`'s `constants/skills/Evan.java` are, whitespace-normalised, character-for-character
Cosmic's file plus:

```java
public static final int DECENT_HASTE       = 20018000;
public static final int DECENT_MYSTIC_DOOR = 20018001;
public static final int DECENT_SHARP_EYES  = 20018002;
public static final int DECENT_HYPER_BODY  = 20018003;
```

(and `BERSERK_FURY` where Cosmic names the same id 20011011 `POWER_EXPLOSION`). 47 constants vs
Cosmic's 43. `[FACT-measured]` That is the entire Java-side Evan gap in the public corpus.

### 3.2 The real Evan gap is quest scripts — 45 files `[FACT-measured]`

| | Cosmic | orion-server v90 |
|---|---|---|
| Evan quest scripts (`scripts/quest/22xxx.js`) | **13** | **58** |
| quest scripts total | 275 | 500 |
| npc scripts total | 708 | 931 |
| portal scripts total | 461 | 766 |
| reactor scripts total | 292 | — |

Cosmic has: `22000 22001 22002 22003 22004 22007 22008` and `22500 22501 22502 22503 22504 22507`.

orion-server has all of those plus:

```
22009 22010 22012
22100 22101 22102 22103 22104 22105 22106 22107 22108 22109   <- entire 221xx block missing in Cosmic
22300
22401 22403 22406 22411
22505 22506 22510 22512 22514 22518 22536 22541 22546 22560 22564 22565 22567
22575 22578 22581 22582 22585 22587 22591 22593 22594 22595
22602 22603 22606 22607
```

**The missing 221xx block is the Evan job-growth chain** (2nd through 4th advancement).
`[INFERENCE]` Its absence is the most likely reason Cosmic's Evan is described as "partial" —
the class exists and can be created, but cannot progress.

Cosmic's Evan **portal** scripts are the opposite story: Cosmic has 26 (`evanRoom0/1`,
`evantalk00–60`, `evanGarden0/1`, `evanFall`, `evanFarmCT`, `evanDollGR`, `evanlivingRoom`,
`enterEvanRoom`, `mirtalk00/01`, `DragonEggNotice`, `inDragonEgg`) against Solaxia-90's ~8.
`[FACT-measured]` No work needed there.

**Verdict:** the Evan deliverable is *port ~45 quest scripts from orion-server, principally the
221xx block*, plus 4 constants. Scripts are plain JavaScript against the same
`cm`/`qm` scripting API across this whole family — expected to be **drop-in to light edit**.

### 3.3 Dragon, mounts, HP/MP curve `[NOT-FOUND] / [INFERENCE]`

- **Dragon** — Cosmic has `server/maps/Dragon.java` and dragon references in `BuffStat`,
  `Character`, `SkillFactory`, `SpawnPetProcessor`, `GameConstants`, `ItemId`, `MapId`. No
  higher-version source inspected has a *better* dragon implementation. No porting source
  identified; whatever is wrong with Cosmic's dragon must be fixed in place.
- **Evan mounts** — no dedicated implementation found in any source surveyed. `[NOT-FOUND]`
- **Evan HP/MP-per-level curve** — no source found that documents or implements it separately from
  WZ data. `[NOT-FOUND]` `[INFERENCE]` This is data-driven from `Character.wz`/level-up tables, so
  it is a WZ-merge concern rather than a Java-port concern.

---

## 4. v84 content beyond Evan

`[NOT-FOUND]` on all of the following as *portable Java*:

- **Neo City** — code-searched for the map-ID range and for `NeoCity` across JavaScript. Only
  unrelated non-MapleStory projects matched. No server-side Neo City implementation found in any
  MapleStory source.
- **v84 cash-shop catalogue** — no source found that carries a v84-era catalogue. Cosmic's cash shop
  is `server/CashShop.java` + three handlers; the catalogue itself is data
  (`Etc.wz/Commodity.img`), so this is a WZ-merge concern. `[INFERENCE]`
- **v84 new items / equips** — data, not code. WZ-merge concern.

`[INFERENCE]` This is a *consistent* result across the survey, not a gap in the search: v84 added
content, and content in this engine is WZ data plus quest scripts. The Java engine barely changed
between v83 and v90 — the diff between Cosmic and MapleSolaxia-90 outside the opcode table is small
enough that both share identical `constants/skills` files. **The v84 job is: opcode table (§1) +
WZ merge (ticket 03) + ~45 quest scripts (§3.2).** Very little new Java.

---

## 5. Highest-version sources in Cosmic's own lineage

Establishing the ceiling, since same-lineage is the cheapest thing to port from.

| Rank | Source | Version | Lineage distance from Cosmic |
|---|---|---|---|
| 1 | [Vertisy](https://github.com/Chronicle20/Vertisy) | **v90** (not v92 — §7.2) | Same layout, JDK 21, same script tree |
| 2 | [MapleSolaxia-90](https://github.com/Maple000/MapleSolaxia-90) | **v90** | Cosmic's direct grandparent line |
| 3 | [orion-server](https://github.com/MapleStoryA/orion-server) | v90 | Cousin — `handling.*` net layer |
| 4 | [maplev90](https://github.com/odasm/maplev90) | v90 | Cousin — `handling.*` net layer |
| 5 | [kinoko](https://github.com/iw2d/kinoko) | v95 | Unrelated; no licence |

**The ceiling for Cosmic-lineage Java with public source is v90.** `[FACT-measured]` Nothing between
v90 and v95 exists in this family. Above v90 you leave the lineage entirely.

For a v84 target this is good news: **v84 is well below the ceiling**, and two same-layout v90
sources bracket it from above.

---

## 6. Has anyone migrated a HeavenMS-family v83 server upward?

### 6.1 Documented public attempts — and both failed at zero commits `[FACT-measured]`

| Repo | Declared intent | Reality |
|---|---|---|
| [`davidlafriniere/HeavenMSv90`](https://github.com/davidlafriniere/HeavenMSv90) | *"A fork of the marvelous HeavenMS (which is a fork of Solaxia) for v90."* (2019-08-26) | `size: 0`, API returns **"Git Repository is empty"**. Never pushed a line. |
| [`mrscout144/HeavenMsV90-`](https://github.com/mrscout144/HeavenMsV90-) | *"updating v83-v90"* (2018-04-15) | One commit, `Initial commit`, containing only `.gitattributes`. |

Two independent people announced this exact migration and neither produced a first commit. That is
the clearest available signal on how the community rates the difficulty.

### 6.2 The one attempt that produced code — and its own verdict `[FACT-sourced]`

Vertisy is the only Cosmic-lineage upward migration with real code. Its README
([Chronicle20/Vertisy](https://github.com/Chronicle20/Vertisy)) states:

> `## Vertisy v90/v92 Fork`
> `THIS SOURCE IS WIP, AND NOT ACTIVELY MAINTAINED.`
> `If you want to start using the publically released Vertisy source, the recommendation is to fork from initial check-in.`
> `### Known Issues`
> `* Script based things will not work. The scripting engine code has not been migrated.`
> `* Database queries need to be reviewed. getLong -> getTimestamp().getTime()`

Repository description: *"Unmaintained Check-In of v90/v92 MapleStory Source"*. Last push
2024-02-17, 4 stars.

**What this tells the owner** `[INFERENCE]`: the person who got furthest on a Cosmic-lineage version
bump got the **opcode layer** working (they externalised it, §1.6) and then stalled on the
**scripting engine** — and shipped with scripts broken. Given §3.2 and §4 conclude that the v84
deliverable is *mostly scripts*, this is the failure mode to design against. Do not let the
scripting engine drift while chasing opcodes.

### 6.3 Written guides / post-mortems `[NOT-FOUND]`

No guide, migration doc, or post-mortem for a HeavenMS-family v83→v84 (or any upward) migration was
found. RaGEZONE — where such threads would live — returns **HTTP 403** to WebFetch, so it could not
be searched from this session. **This is an unsearched region, not a confirmed absence.** Recommend
a human check of RaGEZONE with a logged-in browser before concluding nothing exists.

---

## 7. Phase 2 (v92 / Dual Blade) — corrections and additions only

The peer session settled Q1 and Q2. Three things my evidence adds or corrects.

### 7.1 A fourth Dual Blade source, and it is the most complete `[FACT-measured]`

[`odasm/maplev90`](https://github.com/odasm/maplev90) was not in the peer's list. It has the parts
that are actually hard — not just the skill constants:

- All five constant classes in Cosmic's exact package: `constants/skills/Blade{Recruit,Acolyte,
  Specialist,Lord,Master}.java`, carrying the same OdinMS AGPL header as Cosmic's own
  `constants/skills/` files.
- **Katara secondary-weapon plumbing**, the genuinely non-obvious part:
  - `constants/GameConstants.java` — `isKatara(itemId) { return itemId / 10000 == 134; }`
  - `server/MapleInventoryManipulator.java` — equips Katara to **slot -10** and gates it:
    `if ((chr.getJob() != 900 && (chr.getJob() < 430 || chr.getJob() > 434)) || weapon == null || !GameConstants.isDagger(weapon.getItemId()))`
  - `client/MapleWeaponType.java` — `KATARA` weapon type
  - `client/PlayerStats.java` — Katara damage for jobs 400–434
- **Dual Blade character creation**, `handling/login/handler/CreateCharHandler.java`:
  `final short db = slea.readShort(); // whether dual blade = 1 or adventurer = 0`
- `client/MapleJob.java` — `BLADE_RECRUIT(430)` … `BLADE_MASTER(434)`
- `client/MapleCharacter.java` — DB job progression (`if (job >= 430 && job <= 434) …`)
- Dual Blade portal scripts: `dual_lv20.js`, `dual_lv25.js`, `dual_lv30.js`, `dual_secret.js`,
  `Dual_moveGate.js`, `checkJumpingDual.js`, `dual_ballRoom.js`

Cosmic currently has **zero** Dual Blade support — no jobs 430–434 in `client/Job.java`, no
`KATARA` anywhere in `src/main/java`. `[FACT-measured]`

### 7.2 Correction: Vertisy has no v92 opcode table `[FACT-measured]`

Vertisy ships four properties files. Byte comparison:

```
cmp sendops-90.properties sendops-92.properties  -> IDENTICAL (411 lines, 341 entries)
cmp recvops-90.properties recvops-92.properties  -> IDENTICAL (370 lines, 189 entries)
```

Name-keyed: `V90(341) vs V92(341): common=341 SAME=341 CHANGED=0`.

The v92 files are copies of the v90 files. Vertisy is a **v90** source that intended to reach v92.
Treating it as a v92 opcode reference would silently give you a v90 table. Flagging because the
peer summary lists "Vertisy at v92" as an established data point.

### 7.3 kinoko's licence is a blocker for porting `[FACT-measured]`

`gh api repos/iw2d/kinoko` returns `"license": null` and the repo root has no `LICENSE` file. Under
default copyright that is all-rights-reserved. Its Dual Blade implementation is the most modern
(26 constants, 25 dispatch cases in `world/job/explorer/Thief.java`) but it cannot be copied into an
AGPL project. Read for reference only.

---

## 8. Ranked recommendation

**For Phase 1 (v84), pull from two sources:**

1. **[`Chronicle20/Vertisy`](https://github.com/Chronicle20/Vertisy)** — *for the mechanism, not the
   data.* Copy the externalised opcode-table pattern (`SendOpcode` as a valueless enum +
   `ExternalCodeTableGetter` + `sendops-NN.properties`). Same lineage, same JDK, same AGPL header.
   Do this **first**; it makes every subsequent opcode experiment a file swap instead of a rebuild,
   and it is what lets you iterate against the v84 client at all. Ignore its `-92` files (§7.2).

2. **[`MapleStoryA/orion-server`](https://github.com/MapleStoryA/orion-server)** — *for the Evan
   quest scripts.* 58 Evan quest scripts against Cosmic's 13; the missing 221xx job-growth block is
   the thing that makes Cosmic's Evan "partial". Actively maintained (July 2026). Scripts only —
   do not port its Java, the net layer is a different family. Note the unstated licence (§2.1).

**Reference, do not port:**

3. **[`Maple000/MapleSolaxia-90`](https://github.com/Maple000/MapleSolaxia-90)** — Cosmic's direct
   lineage at v90, useful as a second opinion whenever a v90 opcode value or a Cosmic-layout
   question comes up. Its Evan and script content are behind Cosmic's; do not port from it.

**For Phase 2 (v92 / Dual Blade), when it comes:**

4. **[`odasm/maplev90`](https://github.com/odasm/maplev90)** — most complete portable Dual Blade
   found: Katara slot plumbing, DB creation packet, job progression, portal scripts, and
   `constants.skills` files that drop straight into Cosmic (§7.1). AGPL headers throughout.

---

## 9. Showstoppers and open items

| # | Item | Severity | Note |
|---|---|---|---|
| 1 | **No public GMS v84 opcode table** | **Blocking Phase 1** | Must be derived from the v84 client or sniffed. Bounded at ~5–10 insertions (§1.5), and the login path is likely untouched, but it is not free and it is on the critical path. |
| 2 | RaGEZONE unreadable from this session (HTTP 403) | Medium | The one place a v84 opcode table or a migration post-mortem would plausibly live is unsearched. Needs a human with a logged-in browser. |
| 3 | Vertisy's own failure mode: scripting engine left broken | Medium | The only lineage-mate that attempted this stalled exactly where our v84 work is concentrated (scripts). Design the plan so scripts are validated continuously, not at the end. |
| 4 | orion-server licence unstated | Low | Scripts are the only thing we want from it. Note the ambiguity; not a blocker. |
| 5 | Evan dragon / mounts / HP-MP curve have no porting source | Low | `[NOT-FOUND]`. Must be fixed in Cosmic directly or resolved as WZ data. |
| 6 | kinoko unlicensed | Low (Phase 2) | Do not copy. Reference only. |

---

## Appendix — reproducing the measurements

Scratchpad artefacts, all fetched via `gh api … | base64 -d`:

```
sol90-SendOpcode.java  sol90-RecvOpcode.java   <- Maple000/MapleSolaxia-90 src/net/
sendops-90.properties  sendops-92.properties   <- Chronicle20/Vertisy (root)
recvops-90.properties  recvops-92.properties   <- Chronicle20/Vertisy (root)
orion-Send.java  orion-Evan.java               <- MapleStoryA/orion-server
v90-Assassin.java  v90-Evan.java               <- odasm/maplev90 constants/skills/
opcmp.py  cmp3.py  drift.py                    <- name-keyed opcode comparators
```

`opcmp.py` / `cmp3.py` parse both `NAME(value)` enum form and `NAME = value` properties form, key
on the constant name, and report same/changed/only-in-A/only-in-B. `drift.py` buckets common
opcodes by v83 value and reports min/max of `v90_value − v83_value` per bucket, which is what
produced §1.3.
