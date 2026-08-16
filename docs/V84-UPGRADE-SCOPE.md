# Scope of work — full GMS v83 → v84 upgrade for Cosmic

Reframed from "add Evan" to "take everything v84 added". This is the better project: Evan is a
subset of it, and the WZ pipeline is the same work either way.

Companion docs: `EVAN-DUALBLADE-SCOPE.md` (class detail, Dual Blade, v92 path),
`porting-resources/docs/92-V83-V84-CONTENT-DELTA.md` (the full node-by-node manifest),
`porting-resources/docs/99-AUDIT-FINDINGS.md` (evidence).

---

## 0. The headline finding

**v84 is a pure superset of v83. Zero content was removed, in every category.**

Measured by diffing the complete id lists for GMS/83 and GMS/84:

| Category | v83 | v84 | **added** | removed |
|---|---|---|---|---|
| Mobs | 1,597 | 1,638 | **41** | **0** |
| Maps | 4,411 | 4,504 | **93** | **0** |
| Items | 12,578 | 12,990 | **412** | **0** |
| NPCs | 1,733 | 1,760 | **27** | **0** |
| Quests | 2,817 | 3,015 | **198** | **0** |
| **Total new nodes** | | | **771** | **0** |

Plus Evan's 58 skills and the dragon animation set.

Zero removals means an upgrade cannot break anything the server already depends on. That is the
single most important fact for risk: this is additive work, not migration work.

---

## 1. What you actually get

v84 was not only Evan. Decoded from the delta:

### Crimson Sky — a Leafre/Dragon-Nest expansion
Maps `240080000`–`240080800`: Crimson Sky Dock, Crimson Sky 1–5, Crimson Sky Edge, Nest Entrance,
Crimson Sky Nest, Cave of the Deceased, Resurrection Site. Plus `683010000` Dragon's Nest.

New mobs to match: **Skelegon, Skelosaurus, Leviathan**, Green/Dark Cornian, Jr. Newtie, Nest
Golem, Blue/Red Dragon Turtle (`9500374`–`9500382`). New NPCs Matada, Crimson Sky Doorway,
Dragon Rider, Giant Twin Dragon's Egg.

This is real endgame content — the Leafre dragon area.

### Neo City, Year 2227
Maps `683070400`–`683070402` (Dangerous City Intersection / Center / Construction Site) and mobs
Imperial Guard Type A, Dunas Type D, Royal Guard Type S, Afterlord Type A (`9400658`–`9400661`).

### Evan's world
Dream Forest / Lush Forest / Lost Forest (`900010000`–`900020220`), tutorial and job-advancement
maps (`900090100`–`900090103`), Forest Hall, plus NPCs Afrien, Hiver, Olaf, Glowing Stele.

### Mounts
`8300000`–`8300007`: Soaring Hawk, Eagle, Red/Blue/Black Wyvern, Griffey, Dragonica, Dragon Rider.

### Cosmetics
A large share of the 412 items are **hairstyles** — Evan Hair, Tighty Bun, Babish, Spiky Shag
families in all eight colours — plus Evan equipment and Crimson Sky drops.

### Misc
Golem's Temple Entrance, Abandoned Cave/Hideout, Temporary Harbor, Snowy Forest, Cave of Silence,
Frog House, Power B. Fore's training centers, Christmas NPCs.

---

## 2. The constraint that shapes everything: merge, do not replace

**Your client is MapleEzorsia V2 HD, not stock v83 — and it carries ~24 MB of custom content.**

Stock v83 and stock v84 were both downloaded and extracted to prove this. Three-way comparison:

| WZ | stock v83 | **your client** | stock v84 | your custom surplus |
|---|---|---|---|---|
| `Character.wz` | 178.1 MB | **196.7 MB** | 183.5 MB | **+18.6 MB** |
| `Map.wz` | 606.0 MB | **608.9 MB** | 599.8 MB | **+2.9 MB** |
| `Npc.wz` | 48.8 MB | **51.0 MB** | 50.0 MB | **+2.2 MB** |
| `Etc.wz` | 1.1 MB | **1.7 MB** | 1.2 MB | **+0.6 MB** |
| `String.wz` | 3.1 MB | **3.4 MB** | 3.2 MB | **+0.3 MB** |
| `Skill.wz` | 73.3 MB | 73.0 MB | **113.0 MB** | — (+39.7 MB in v84 = Evan) |
| `Item.wz` | 17.5 MB | 17.5 MB | 18.2 MB | — |
| `Quest.wz` | 5.7 MB | 5.7 MB | 6.0 MB | — |
| `UI.wz` | 27.0 MB | 27.0 MB | 31.5 MB | — |

**Confirmed: ~24.6 MB of content exists in your client that is in neither stock v83 nor stock
v84.** In `Character.wz` your client even exceeds v84 by 13 MB. That is Ezorsia's HD work and
whatever you have added.

**Therefore: never swap WZ files wholesale.** Dropping v84's `Character.wz` over yours would
delete 18.6 MB of custom content outright. The job is to **import the 771 added nodes into your
existing WZ**, leaving your content intact.

The delta manifest in `92-V83-V84-CONTENT-DELTA.md` is that import list. Use **WzComparerR2**
(in `reference-sources/`) to drive the merge node-by-node, with stock v83 at
`wz-data/v83-stock/` as the clean baseline for identifying what is yours.

### One anomaly to be aware of

`Map.wz` **shrank** from v83 (606.0 MB) to v84 (599.8 MB) despite v84 having 93 more maps and
removing none. Most likely Nexon repacked/recompressed the archive that patch. Two consequences:

1. Do not use file size as a proxy for content when merging — compare nodes, not bytes.
2. The 771-node delta was computed from maplestory.io's id lists, which are built from the
   `String.wz` naming tables. A few entries may be *named* without carrying full data. Treat the
   manifest as an accurate upper bound and let WzComparerR2's node-level diff be authoritative
   before you commit to the import list.

---

## 3. Why the v83 client can run v84 content

New maps, mobs, items and NPCs are **pure data** — the client renders them from WZ. New content
only needs new client code when it introduces a new *mechanic*, and v84 introduced exactly one:
Evan. Evan's client code is already compiled into your v83 binary, just gated behind an `if`
(verified — see §5).

The one genuinely new hard mechanic of this era, Dual Blade's katara, arrives at **v88** — not
v84. That is why v84 is the easy jump and v88+ is not.

Two mechanical caveats:
- **WZ version header.** v84 WZ carries a different version hash than v83. Re-save as the v83
  version in HaRepacker — the documented, widely used technique.
- **UI.wz must be selective.** Do not bulk-copy it; take only `SkillEx` and `SkillMacroEx`.
  Bulk-copying breaks login/shop UI. Both source guides warn about this independently.

---

## 4. Work breakdown

### Phase A — WZ merge (771 nodes) — 4–6 days

| Step | Detail |
|---|---|
| A1 | Diff your client vs stock v83 with WzComparerR2 → inventory of *your* custom content (protect list) |
| A2 | Diff stock v83 vs v84 → authoritative add list (cross-check against the 771 in the manifest) |
| A3 | Import 93 maps into `Map.wz`, 41 mobs into `Mob.wz`, 412 items into `Item.wz`/`Character.wz`, 27 NPCs into `Npc.wz`, 198 quests into `Quest.wz` |
| A4 | `String.wz` — merge Eqp/Skill/Mob/Npc/Map name tables |
| A5 | `UI.wz` — `SkillEx` + `SkillMacroEx` only |
| A6 | Re-save everything at v83 version encoding |
| A7 | Export the same set as Private Server XML into `Cosmic\wz\` |

Two output trees, both required: binary `.wz` for the client, XML for the server.

### Phase B — server-side content data — 4–6 days

WZ import alone does **not** give you working content. These live in Cosmic's database and
scripts, not in WZ:

| Item | Volume | Note |
|---|---|---|
| **Drop tables for new mobs** | ~25 real mobs | `db/data/152-drop-data.sql` currently holds 22,161 rows. Skelegon/Leviathan/Cornians etc. drop nothing until you add entries. Source from a v84-era drop table or hand-build. |
| **Shops for new NPCs** | a handful | `101-shops-data.sql` (111 shops) / `102-shopitems-data.sql` (3,883 items) |
| **Quest scripts** | ~18 of 198 | Only ~9% of quests need scripts today (253 scripts for 2,818 quests). The rest are pure WZ. |
| **Reactor drops** | few | `131-reactordrops-data.sql` |
| Map/item id constants | few | `constants/id/MapId.java`, `ItemId.java` if you reference new content in code |

Mob **spawns** come free — they live in the map's `life` nodes and arrive with the map import.

### Phase C — Evan the class — 5–8 days

Covered in detail in `EVAN-DUALBLADE-SCOPE.md`. Summary:

- **Client patch (1 hour).** Verified: the 21-byte Evan gate exists in *your* `local.exe` at file
  offset `0x361714`, exactly 1 occurrence. Replace with `90`×21. Do this first — it is the
  project's go/no-go.
- **Skills: 58 exist, 14 are work.** 29 are pure data; 12 mount buffs reuse `MONSTER_RIDING`;
  3 are beginner-common. The 14 = 5 new BuffStat masks (Magic Shield, Magic Resistance, Soul
  Stone, Evan Slow, Phantom Imprint), 2 charge attacks (Ice/Fire Breath), 2 bespoke party effects
  (Recovery Aura, Blessing of the Onyx), 5 one-line additions.
- **16 skill ids missing** from `constants/skills/Evan.java`.
- **Server is ~80% done already** — `Dragon.java`, dragon packets/opcodes, `MoveDragonHandler`,
  extended-SP encoding and the `sp VARCHAR(128)` column all exist (Cosmic inherited Solaxia's
  Evan work). **Database schema needs zero changes.**
- **Skill crash audit (1–2 days)** — some Evan skills reference animations absent from v83's
  client-side string pool and *cannot* be added. Test in-client, strip the crashers from the WZ
  import, and do this **before** writing the 14 skills.

### Phase D — integration & test — 3–4 days

New areas reachable and populated; drops working; 198 quests accepting/completing; Evan playable
from creation (or job-change) through the dragon evolution chain.

---

## 5. Total estimate

| Phase | Days |
|---|---|
| A — WZ merge | 4–6 |
| B — server content data | 4–6 |
| C — Evan | 5–8 |
| D — integration & test | 3–4 |
| **Total** | **~16–24 working days (3–5 weeks)** |

Phases A and C share the same WZ pipeline, which is why doing them together costs less than
doing Evan alone and the content upgrade later.

---

## 6. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **WZ merge destroys your custom content** | medium | **severe** | never replace files; diff-and-merge only; backups taken (§7) |
| Themida rejects the patched `local.exe` | low–med | blocks Evan | patch a copy, launch it, hour one; fallback = runtime patch via `dinput8.dll` |
| Some Evan skills unfixably crash the client | **certain** | reduces Evan | audit early, strip from WZ import |
| New mobs drop nothing | **certain if skipped** | content feels broken | Phase B is not optional |
| WZ version re-save corrupts a file | medium | recoverable | work on copies; backups exist |
| 771-node merge is slower than estimated | medium | +days | WzComparerR2 makes it mechanical, not manual |

---

## 7. Backups (both verified, both taken before any work)

| What | Where | Size |
|---|---|---|
| Server tree (incl. `.git`, `wz\`) | `_backup\Cosmic-2026-08-15\` | 76,418 files / 1.77 GB |
| Client (`.wz`, exes, dlls) | `_backup\client-v83-EzorsiaV2-2026-08-15\` | 213 files / 2.4 GB |

Three WZ trees exist — client binary at `D:\games\MapleStory\`, Cosmic's XML at `Cosmic\wz\`, and
a third XML copy at `Server\wz\` whose consumer should be confirmed before editing.

---

## 8. What is out of scope here

**Dual Blade.** It arrives at v88, needs client code that does not exist in v83, and no public
v83 implementation has ever been achieved. It is not reachable from a v84 upgrade. The only real
path is the v92 version-up (a separate, months-long packet-layer project — see
`EVAN-DUALBLADE-SCOPE.md` Part 3), or a cosmetic reskin on thief job IDs.

Deciding that is a separate decision, after v84 ships.

---

## 9. Recommended sequence

1. **Hour 1 — patch a copy of `local.exe`, launch it.** Go/no-go for Evan.
2. **Day 1 — diff your client against stock v83.** Know exactly what custom content you must
   protect before touching anything.
3. Phase A on copies, one WZ at a time, testing after each.
4. Phase B in parallel — drop tables can be built while WZ work proceeds.
5. Phase C — Evan last, since it is the only part that can fail for reasons outside your control.
6. Ship. Then decide about Dual Blade.
