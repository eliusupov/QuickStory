# Ticket 18 — Public v88–v95 Source Survey

Research date: 2026-08-16. All claims labelled `[FACT-url]` or `[INFERENCE]`.

---

## VERDICT

> ## **Layer-3 work is PORTING — from `Chronicle20/Vertisy` (public v90/v92 Java, OdinMS/AGPL lineage).**
> Not from scratch. Not partial. A **publicly released, Java, OdinMS-descended, v92.1** server
> source exists with **Evan, Dual Blade, and complete v92 opcode tables**, using the *same package
> layout, same class names and same naming conventions as Cosmic*.

Secondary corroborating sources at v95 (`iw2d/kinoko`, Java; `Kaioru/Edelstein`, C#/MIT) are
higher-quality implementations to *cross-check behaviour against*, but they are post-Big-Bang and
must not be ported wholesale.

**Revised effort read:** weeks, not many months — for the protocol and class-scaffolding layers.
The residual cost is content (quests/maps/items), not engine work.

### Three findings that change the plan

1. **A public v92 opcode table exists** — `recvops-92.properties` (370 lines) and
   `sendops-92.properties` (411 lines), in Cosmic's *exact* `RecvOpcode`/`SendOpcode` naming
   convention, cross-annotated with Nexon's internal `CP_*` names. This removes the single biggest
   protocol risk outright. [FACT-https://github.com/Chronicle20/Vertisy]
2. **Cosmic already ships working Evan support.** `constants/skills/Evan.java`, `server/maps/Dragon.java`,
   `MoveDragonHandler.java`, `client/creator/novice/EvanCreator.java`, full `Job` enum (2001, 2200–2218),
   and **21 Evan references inside `server/StatEffect.java`**. Evan is a *completion* job, not a build job.
3. **Dual Blade is the only genuinely absent class** — zero `Blade*` files, zero `BLADE` entries in
   `client/Job.java`. This is the real Layer-3 work, and Vertisy supplies a v92 reference for it.

---

## Ranked candidate table

| # | Project | Ver | Lang | Lineage | Licence | Evan | Dual Blade | Opcodes | Activity |
|---|---------|-----|------|---------|---------|------|-----------|---------|----------|
| **1** | **[Chronicle20/Vertisy](https://github.com/Chronicle20/Vertisy)** | **v92.1** (+v90) | **Java 21** | **OdinMS → Arnah/Vertisy** (AGPL-3.0 headers in source) | ⚠️ no LICENSE file; **AGPL-3.0 headers in files** | ✅ `constants/skills/Evan.java`, `server/maps/objects/MapleDragon.java`, `MoveDragonHandler.java`, ~31 Evan scripts | ✅ `constants/skills/Blade{Recruit,Specialist,Lord,Master}.java` + logic in `MapleStatEffect`/`AbstractDealDamageHandler` | ✅ **`recvops-92.properties` (370) / `sendops-92.properties` (411)** + v90 pair | Archived 2024-02-17, 4 commits, 4★ |
| 2 | [MapleStoryA/orion-server](https://github.com/MapleStoryA/orion-server) | v90.3 | Java 21 | OdinMS (`handling/` branch) | none | ✅ `constants/skills/Evan.java`, `MapleDragon`, `TwinDragonEggHandler` | ✅ incl. **`BladeAcolyte.java`** (which Vertisy lacks) | `networking/packet/RecvPacketOpcode.java` (218) | **Active 2026-07-29**, 42★ — most-maintained v90 |
| 3 | [iw2d/kinoko](https://github.com/iw2d/kinoko) | v95 | Java 21 | **from scratch**, WZ-driven | ❌ **none (all rights reserved)** | ✅ **best-in-class** `world/job/legend/Evan.java`, `world/user/Dragon.java`, `DragonPacket.java` | ✅ **complete 26-skill kit** in `world/job/explorer/Thief.java` | `server/header/InHeader.java` (374) / `OutHeader.java` (593) — **v95** | Active 2026-05-22, 95★/53f, 673 commits |
| 4 | [Kaioru/Edelstein](https://github.com/Kaioru/Edelstein) | v95.1 | C# .NET | from scratch | ✅ **MIT** | ✅ `Evan1..10SkillHandler.cs`, `FieldDragon.cs` | ✅ `ThiefDual1..5SkillHandler.cs`, 26 skill entries | `PacketRecvOperations.cs` (320) / `PacketSendOperations.cs` (513) | Active 2026-07-14, 113★ |
| 5 | [NotACoinSync/v95](https://github.com/NotACoinSync/v95) | v95.1 | Java | Vertisy/Arnah, ported to v95 | none | ✅ | ✅ | `recvops-95.properties` (313) / `sendops-95.properties` (505) | 2021-12-31, 1★ |
| 6 | [alive2/vertisy](https://github.com/alive2/vertisy) | v90+**v92** | Java | Vertisy copy | none | ✅ | ✅ | v90+**v92** properties | 2025-03-24, 0★ — backup copy of #1 |
| 7 | [odasm/maplev90](https://github.com/odasm/maplev90) | v90.3 | Java | OdinMS `handling/` | none | ✅ | ✅ | `RecvPacketOpcode.java` | 2016-12-04, 5★ |
| 8 | [xDazedGamingx/LostStoryV90](https://github.com/xDazedGamingx/LostStoryV90) | v90.3 | Java | Vertisy | none | ✅ | ✅ | v90 properties | 2019-05-23, 1★ |
| 9 | [67-6f-64/Rebirth95.Server](https://github.com/67-6f-64/Rebirth95.Server) | v95.1 | C#/Python | ⚠️ "influences from Nexon BMS leaked server files" | none | ✅ `CDragon.cs` | partial (`JobLogic.cs`, ~10 skill IDs) | `OpCodes.cs` (856) | 2021-01-06, 16★ |
| 10 | [doriyan13/SpringStory](https://github.com/doriyan13/SpringStory) | v95 | Java/Spring | from scratch | ✅ MIT | ❌ job IDs only | ❌ job IDs only | `InHeader.java`/`OutHeader.java` | 2026-04-27, 45★ |
| 11 | [Descended/Henesys](https://github.com/Descended/Henesys) | v95 | Java | SwordieMS-derived | none | ❌ **empty stubs** | ❌ IDs only, no logic | `InHeader.java` (372) | 2024-12-19, 16★ |
| 12 | [3VNDR/hwabi](https://github.com/3VNDR/hwabi) | v95 | Zig | from scratch | MIT | ? early-stage | ? | ? | 2026-08-06, new |
| — | [ronancpl/HeavenMS](https://github.com/ronancpl/HeavenMS) | v83 | Java | **Cosmic's ancestor** | **AGPL-3.0** | ❌ | ❌ | v83 | 2019-12-28, 1213★ |
| — | [Hucaru/Valhalla](https://github.com/Hucaru/Valhalla) | v28 | Go | scratch | MIT | ❌ pre-dates | ❌ | `common/opcode/` | Active, 344★ |
| — | [gilmatok/Destiny](https://github.com/gilmatok/Destiny) | v83 | C# | scratch | none | ❌ | ❌ | `ClientOperationCode.cs` | 2019-04-01, 93★ |
| — | [conan513/MoopleDEV](https://github.com/conan513/MoopleDEV) | v83 | Java | OdinMS→Moople | none | ❌ | ❌ | v83 | 2012-10-09 |
| — | SwordieMS ([bitbucket](https://bitbucket.org/swordiemen/swordie), [mirror](https://github.com/ryantpayton/Swordie)) | **v176+** | Java | scratch | MIT | post-BB only | post-BB only | v176 | 2022 |

**Clean negatives** [FACT — GitHub code search]:
- **No public v88 or v93 Java source exists.** `MAPLE_VERSION = 88` / `= 93` → zero repos.
- **"MapleLand v92 source"** — no public GitHub source. The RaGEZONE thread exists but the content
  is login-gated; my [INFERENCE] is it is a rebrand of the Vertisy base. **The v92 source that is
  genuinely public is Vertisy.**
- **"Aeon"** MapleStory server — zero results.
- `github.com/maplestory-emulator` org — 40 repos, all mirrors/forks of others' work. Nothing original.
- Titan/TitanMS — pre-v40s C++, irrelevant.

---

## Deep dive: Vertisy (the recommendation)

`Unmaintained Check-In of v90/v92 MapleStory Source - Vertisy` — 23,877 files, 785 `.java`,
1,619 scripts. [FACT-https://github.com/Chronicle20/Vertisy]

**Publicly released, by the author's own statement** — the README says: *"If you want to start using
the **publically released** Vertisy source, the recommendation is to fork from initial check-in."*
Source files are signed `@Author Arnah / @Website http://Vertisy.ca/ / @since Aug 2017`.

### Why it is a near-drop-in for Cosmic

Package layout is *identical*, because both descend from OdinMS:

| | Vertisy v92 | Cosmic v83 |
|---|---|---|
| Skill constants | `constants/skills/Evan.java` | `constants/skills/Evan.java` ✅ same path |
| Dragon object | `server/maps/objects/MapleDragon.java` | `server/maps/Dragon.java` |
| Dragon handler | `net/server/channel/handlers/MoveDragonHandler.java` | same path ✅ |
| Channel handlers | 145 files | 147 files |
| Opcodes | `recvops-92.properties` → `net/RecvOpcode.java` | `net/opcodes/RecvOpcode.java` (183 recv / 311 send) |
| Top-level pkgs | `client constants net server tools scripting …` | `client config constants database model net provider scripting server service tools` |

Cosmic's `constants/skills/Evan.java` and Vertisy's are **near-identical** — same constants, same
`// EVAN1 … // EVAN10` comment blocks. Only divergence found: `20011011` is `POWER_EXPLOSION` in
Cosmic vs `BERSERK_FURY` in Vertisy. [FACT — direct file comparison]

> **Porting a skill implementation is copy-adapt, not rewrite.** [INFERENCE, high confidence — based
> on identical package paths, identical class names, and the shared `MapleStatEffect`/`StatEffect`
> and `AbstractDealDamageHandler` abstractions.]

### Do NOT replace Cosmic with Vertisy

Cosmic is **more** content-complete: 275 quest scripts vs Vertisy's 237; 708 npc vs 552; 1,940 total
vs 1,619. Vertisy is a **donor for the v83→v92 delta** (opcodes, Dual Blade, Evan maps/quests),
not a base to migrate onto.

---

## 2. Evan & Dual Blade — the headline

### Evan: already substantially implemented in Cosmic
[FACT — local repo inspection]

Present today at `src/main/java/`:
- `constants/skills/Evan.java` (58 lines, full skill ID set)
- `server/maps/Dragon.java` (65), `net/server/channel/handlers/MoveDragonHandler.java` (55)
- `client/creator/novice/EvanCreator.java` (54)
- `client/Job.java` — `LEGEND(2000), EVAN(2001), EVAN1(2200) … EVAN10(2218)`
- Wired into `server/StatEffect.java` (**21 refs**), `AbstractDealDamageHandler` (5),
  `Character.java` (4), `SkillEffectHandler` (2), `MagicDamageHandler` (1), `SkillFactory`, `Mist`

**Evan is a verification-and-completion job.** Three independent references exist to fill gaps:
Vertisy (same conventions), Kinoko (`world/job/legend/Evan.java`, cleanest logic — handles
`ICE_BREATH` freeze, `FIRE_BREATH`/`BLAZE` stun, `KILLER_WINGS` GuidedBullet, `PHANTOM_IMPRINT`
Weakness, `MAGIC_SHIELD`, `SLOW`, `RECOVERY_AURA`, `BLESSING_OF_THE_ONYX`, `SOUL_STONE`, plus the
`DRAGON_FURY` MP-window mechanic), and Edelstein (MIT, `Evan1..10SkillHandler.cs`).

### Dual Blade: entirely absent from Cosmic — this is the real work
[FACT — no `Blade*` file, no `BLADE`/`DUAL` token in `client/Job.java`]

Required additions:
- `Job` enum: `BLADE_RECRUIT(430) … BLADE_MASTER(434)`
- **Sub-job system** — Dual Blade is a Thief *sub-job*; job-advance levels differ (20/55 vs 30/70).
  Kinoko's `JobConstants.isDualJobBorn(jobId, subJob)` and `getJobChangeLevel(jobId, subJob, step)`
  are the clearest public reference. Cosmic has no sub-job concept today.
- **Katara** (secondary weapon) inventory/equip slot handling
- 26 skills

**Reference completeness for Dual Blade, ranked:**
1. **Kinoko** — complete, all 26 named, correct v92-era IDs. But v95 + **no licence**.
2. **Vertisy** — v92-correct but **sparse**: only 12 constants across 4 files, and
   `BladeAcolyte.java` is missing entirely. `BladeMaster.MAPLE_WARRIOR = 43410000` has a **typo**
   (extra digit; should be `4341000`). Use with care.
3. **orion-server** — has the `BladeAcolyte.java` that Vertisy lacks.
4. **Edelstein** — MIT, safest legally, C#.

### ⭐ The most important number: data-driven vs bespoke-Java ratio

OdinMS-lineage servers declare a skill constant **only when the skill needs custom Java**; everything
else is driven generically from WZ data through `StatEffect`. Vertisy's Dual Blade constant files are
therefore a direct readout of that ratio:

| Job | Constants (= needs Java) | Total v92 skills |
|-----|--------------------------|------------------|
| Blade Recruit (430) | 1 | 3 |
| Blade Acolyte (431) | 0 (file absent) | 4 |
| Blade Specialist (432) | 2 | 4 |
| Blade Lord (433) | 4 | 6 |
| Blade Master (434) | 5 | 9 |
| **Total** | **12** | **26** |

> **≈12 of 26 Dual Blade skills (~46%) need bespoke Java. The other ~14 (~54%) are data-driven and
> Cosmic's existing `StatEffect` machinery already handles them.** [INFERENCE — from the OdinMS
> constants-only-when-custom convention, corroborated by Vertisy's actual file contents.]

Evan's ratio is better still, since Cosmic already carries 21 Evan branches in `StatEffect`.

---

## 3. v92 opcode tables — SOLVED

`recvops-92.properties`, 370 lines, dual-annotated:

```
#CP_BEGIN_SOCKET = 0
LOGIN_PASSWORD = 1
GUEST_LOGIN = 2
#CP_AccountInfoRequest = 3
SERVERLIST_REREQUEST = 4
CHARLIST_REQUEST = 5
...
#CP_MigrateIn = 20
PLAYER_LOGGEDIN = 20
```

The left-hand names (`LOGIN_PASSWORD`, `CHARLIST_REQUEST`, `PLAYER_LOGGEDIN`) are **exactly** Cosmic's
`net/opcodes/RecvOpcode.java` identifiers; the `#CP_*` comments are Nexon's internal names, which
also match Kinoko's `InHeader` enum — giving **two independent public sources that cross-validate
each other**. Cosmic currently has 183 recv / 311 send at v83; the v92 tables are 370 / 411.

Loaded in Vertisy via `net/RecvOpcode.java` + `ExternalCodeTableGetter` — i.e. opcodes are external
config, so a Cosmic port can adopt the same mechanism and switch versions by swapping a properties file.

Additional public tables: v90 (Vertisy), v95 (`NotACoinSync/v95`, Kinoko, Edelstein, Rebirth95).

**Session/crypto layer** is unchanged across this whole era (MapleAES + Shanda, version-stamped
handshake) and ports cleanly. [INFERENCE, high confidence]

---

## 4. The v95 artifact & the Big Bang line

### What the "v95 IDB" actually is
Two separate things, commonly conflated [FACT — RaGEZONE threads]:

1. **GMS v95 client PDB leak** — the real reason for v95 clustering. Nexon's v95 client leaked *with
   Microsoft PDB debug symbols*, giving real function names and stack layouts, i.e. near-source-level
   reversing in IDA. A public "v95 localhost (PDB leak version)" exists, and a derived `.idb`
   circulated. [FACT-https://forum.ragezone.com/threads/release-v95-localhost-pdb-leak-version-early-marry-christmas.1150911/,
   https://forum.ragezone.com/threads/library-of-idbs-for-different-versions-with-named-addresses.987815/]
   Community summary: *"KMS v330 leak, GMS v95 leak, KMST v1029 leak — each contains the binaries and
   PDB files."*
2. **BMS = Brazil MapleStory (LevelUp Games)** — leaked Nexon *server* files, and **not v95**:
   *"a BMS v8 server (around GMS v53 or so)"*. This is what Rebirth95's README means by "influences
   from Nexon BMS leaked server files". [FACT-https://forum.ragezone.com/threads/stable-maple-story-source-for-basing-game-off-of.1174319/]

[INFERENCE, well supported] v95 is simply the *earliest post-Big-Bang GMS client with full debug
symbols*, plus a ready unpacked localhost and prior art (Rebirth95 → Edelstein → Kinoko). It is a
**tooling snowball, not a property of the game version**. No v92 PDB is known to exist.

### Big Bang: v92 is confirmed the last pre-Big-Bang version ✅

| GMS | Date | Content |
|-----|------|---------|
| v83 | Feb 22 2010 | *Cosmic's current base* |
| **v84** | **Mar 31 2010** | **EVAN released** |
| v85–v87 | May–Jul 2010 | 5th Anniversary, Golden Temple, Episode 1 revamp |
| **v88** | **Jul 21 2010** | **DUAL BLADE released**; **Potential system**; equip Enhancement; Chaos Zakum/Horntail |
| v89–v91 | Aug–Oct 2010 | Visitors, Ice Gorge, Ninja Castle, Ulu City |
| **v92** | **Nov 2010** | compatibility/bugfix — **LAST PRE-BIG-BANG** ✅ |
| **v93** | **Dec 7 2010** | **BIG BANG Part 1** |
| v94 | Dec 20 2010 | Resistance: Battle Mage, Wild Hunter |
| v95 | Jan 19 2011 | Mechanic |

Direct confirmation: the v93 patch notes state *"You must have v92 to use the Manual Patcher."*
[FACT-https://maplenewsnetwork.wordpress.com/2010/12/06/gmsv-93-patch-notes-big-bang-part-1/];
filed as "[GMS] [0.92-->0.93] Big Bang" [FACT-https://www.southperry.net/showthread.php?t=35406]

**Corrections to earlier assumptions:** Evan is **v84 (Mar 2010)** and Dual Blade is **v88 (Jul 2010)** —
*both are earlier than previously assumed, and both sit inside the v83→v92 window, pre-Big-Bang.*
[FACT-https://maplestory.fandom.com/wiki/Class_Release_Dates]

**Independent confirmation of the target:** DreamMS runs *"A GMS-like v92 MapleStory private server /
Before Big Bang MapleStory, Reimagined"* with **552 players online** at time of research
[FACT-https://dreamms.gg/]. Its class list is exactly **22 classes with no Resistance** — the correct
pre-Big-Bang roster. A feature-complete pre-BB v92 server is demonstrably achievable.

### Should we switch to v95 for better source availability? **No.**

Availability *is* better at v95 (10 repos vs effectively 1 at v92). But:
- v95 is **post**-Big-Bang — exactly what the owner does not want.
- Big Bang rewrote damage/accuracy/avoid/defence formulas and the EXP curve; deleted, merged and
  renumbered skills across every job; and rearranged Victoria Island.
- A v95 codebase is **not practically adaptable to a v92 client**. What survives is infrastructure
  (crypto, WZ loading, DB, channel/world architecture); what dies is everything version-visible.
- **Cosmic's v83 base already has correct pre-BB formulas and skills.** That is worth more than
  v95's repo count.

> **Stay on v92. The cheap path is v83 Cosmic + forward-ported v84–v92 content, never a v95 back-port.**

---

## 5. v92 content catalogue & the v83→v92 delta (hard counts)

Source: **maplestory.io public WZ API**, which serves *both* `GMS/83` and `GMS/92`. No auth required.
[FACT-https://maplestory.io/api/wz] — measured directly this session.

`https://maplestory.io/api/GMS/{83|92}/{job|quest|map|mob|npc|item}`
`.../job/{jobId}/skillbook`, `.../job/skill/{skillId}`

| Category | v83 | v92 | Delta |
|----------|-----|-----|-------|
| Jobs | 67 | 90 | **+23** |
| Skills | 437 | 593 | +156 ⚠️ see caveat |
| Quests | 2,817 | 3,215 | **+398** |
| Maps | 4,411 | 4,776 | **+365** |
| Mobs | 1,597 | 1,877 | **+280** |
| NPCs | 1,733 | 1,866 | **+133** |
| Items | 12,578 | 14,161 | **+1,568** |

*Cosmic today: 275 quest scripts, 708 npc scripts, 1,940 scripts total.*

### ⚠️ Important caveat on these numbers
The maplestory.io `GMS/92` dump **includes Resistance jobs** (Citizen 3000, Battle Mage 32xx, Wild
Hunter 33xx, Mechanic 35xx) which did **not** ship until v94/v95. Of the +156 skills, **117 are
Resistance** and are *not* v92 content. Subtracting them:

> **True v83→v92 new-class skill delta = 39 by job-bucket count; ≈57 counting every Evan sub-tier
> (Evan ≈31 + Dual Blade 26).** Verified per-tier: Dual Blade 3+4+4+6+9 = **26** — exactly matching
> Kinoko's independent implementation. ✅

Because the dump is contaminated with post-v92 data, **treat the quest/map/mob/npc/item deltas as
upper bounds**, not exact figures. The authoritative pre-BB class roster is DreamMS's live list of
22 classes. [FACT — cross-checked against https://dreamms.gg/classes]

### DreamLib / public API access
`dreamms.gg/classes|quests|maps|monsters|equipment|items|npcs|mounts|pets|chairs|cash` are
server-rendered pages. `api.dreamms.gg` is a **self-hosted [maplestory.io](https://maplestory.io)
instance** — the same open developer platform, fully documented, no auth. Because maplestory.io
itself serves `GMS/83` and `GMS/92` directly, **query maplestory.io rather than DreamMS's instance**:
same data, no load on their server. Documented endpoints include `/job`, `/job/{id}/skillbook`,
`/job/skill/{id}`, `/item/{id}`, `/mob/{id}`, `/map/{id}`, `/npc/{id}`, `/quest`, and `/api/wz`
(raw WZ). Nothing here touches DreamMS's private server code.

---

## 6. Licence analysis — read this before copying a line

Cosmic descends from HeavenMS, which is **AGPL-3.0**. That is a copyleft obligation Cosmic already carries.

| Source | Status | Verdict for porting |
|--------|--------|---------------------|
| **Vertisy** | No LICENSE file, but every core file carries the **2008 OdinMS AGPL-3.0 header** (Patrick Huy / Matthias Butz / Jan Christian Meyer) | ✅ **Best fit.** AGPL-in, AGPL-out — same licence Cosmic already operates under. The missing LICENSE file is untidy but the per-file headers govern. |
| orion-server, odasm, LostStoryV90 | Same OdinMS AGPL headers, no LICENSE file | ✅ Same reasoning |
| **Kinoko** | **`license: null`, no LICENSE file, no per-file headers** | ❌ **Do not copy code.** No licence = all rights reserved. Study for *behaviour and mechanics*; reimplement independently. Its value is as a **specification**, not a source. |
| **Edelstein** | **MIT** | ✅ Legally the safest reference; C#, so it is a behavioural spec rather than a code donor. MIT is AGPL-compatible in the inbound direction. |
| Rebirth95 | No licence + BMS-leak provenance | ⚠️ Avoid |
| SpringStory | MIT | ✅ but has no Evan/DB logic to take |

**Practical rule:** *port code* from Vertisy/orion-server (AGPL→AGPL). *Read* Kinoko and Edelstein to
understand correct behaviour, then write the implementation in Cosmic's own idiom.

---

## 7. Recommended plan

1. **Adopt the v92 opcode tables** from Vertisy's `recvops-92.properties`/`sendops-92.properties`;
   cross-validate against Kinoko's `InHeader`/`OutHeader` `CP_*` names. Consider Vertisy's external
   properties mechanism so version bumps are config, not code. *(Biggest risk, cheapest fix.)*
2. **Finish Evan.** Audit Cosmic's existing 21 `StatEffect` branches against Vertisy's Evan and
   Kinoko's `Evan.java`; fill gaps. Add the Dragon lifecycle if incomplete.
3. **Build Dual Blade.** `Job` enum 430–434, sub-job system (`isDualJobBorn`, 20/55 advance levels),
   Katara equip slot, then ~12 bespoke skills + ~14 data-driven. Port from Vertisy + orion-server's
   `BladeAcolyte.java`; cross-check completeness against Kinoko's 26-skill list.
4. **Content delta** via maplestory.io `GMS/92` — quests, maps, mobs, items. Largest raw volume,
   lowest technical risk, highly parallelisable.

**Residual unknowns:** exact packet *struct layouts* (opcodes are solved, field ordering is not);
Katara/secondary-weapon inventory semantics; Potential + equip Enhancement systems (v88) which no
audited source was checked for.

---

## Ethical boundary — observed

Only publicly released / open-source projects were pursued. **DreamMS's server source and opcode
tables were not sought, solicited, or reconstructed** — only their *published* public resources
(site, class list, documented API) were used, and even those turned out to be a self-hosted instance
of the open maplestory.io platform. Provenance ambiguities are flagged rather than dug into:
Rebirth95's "BMS leaked server files" dependency (noted, not pursued) and the Vertisy family's
licence-file/header mismatch (noted). Sources requiring RaGEZONE login were not circumvented.
