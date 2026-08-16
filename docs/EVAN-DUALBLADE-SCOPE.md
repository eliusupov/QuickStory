# Scope of work — Evan + Dual Blade for Cosmic (v83)

Final scope. Every claim below was verified against your actual install, client binary, and the
real v84 game data — not inferred. Branch `worktree-evan-dualblade`.
Resources live in `D:\games\MapleStory\Server\porting-resources\`.

---

## 0. Verdict

| Class | Verdict | Effort | Confidence |
|---|---|---|---|
| **Evan on v83** | **Do it.** The client gate is confirmed present in *your* binary. | **~2 weeks** | high — go/no-go already passed |
| **Dual Blade on v83** | **Don't.** No client code exists to un-gate. No public release has ever done it. | unbounded, may yield nothing | high |
| **Dual Blade via v92 version-up** | Viable, separate project. Only path where DB actually works. | months, linear | medium |
| **Dual Blade as a reskin** | Cheap consolation. Cosmetic only. | ~2 days | high |

---

## 1. Verified facts

### 1.1 Version timeline (this drives everything)

| GMS | Content | Date |
|---|---|---|
| **v0.83** | **your client** — Aran yes, Evan no | Feb 2010 |
| v0.84 | **Evan added** | Mar 31, 2010 |
| v0.88 | **Dual Blade added** | Jul 21, 2010 |
| v0.92 | last pre-Big-Bang (has both) | Nov 2010 |
| v0.93 | Big Bang — formula/map/job rewrite | Dec 7, 2010 |

You are one patch before Evan, five before Dual Blade.

### 1.2 Your client — confirmed patchable ✅

`D:\games\MapleStory\` is a **MapleEzorsia V2 HD** client, not stock v83.

The Evan job-gate in `CSkillInfo::GetSkill` was searched for in your real binary:

```
83 F8 16 0F 84 D7 00 00 00 81 FE D1 07 00 00 0F 84 CB 00 00 00
  cmp eax,0x16 (job/100==22)              cmp esi,0x7D1 (job==2001)
```

| | |
|---|---|
| Found in | `local.exe` **and** `localhome.exe` (9,920,523 B each) |
| Occurrences | **exactly 1** — unique, so search-and-replace is safe |
| File offset | **`0x361714`** (guide says `0x361710`; your build differs by 4 bytes) |
| Fix | replace those 21 bytes with `90` × 21 |

`local.exe` and `localhome.exe` have **different SHA256** (different baked-in IPs) — patch both
or know which you launch. Both carry **Themida** markers; the pattern is plaintext on disk so it
is patchable, but Themida sometimes self-CRCs. **Patch a copy and launch it before proceeding.**
Fallback if it refuses: runtime patch via the existing `dinput8.dll` hook layer.

`STREDIT.exe` is **already in that folder** — no need to source it.

### 1.3 What Cosmic already has (server side is ~80% done)

Cosmic ← HeavenMS ← MapleSolaxia, and Solaxia merged Eric's 2016 Evan release before shutting
down. Present and working in your tree today:

| Piece | Location |
|---|---|
| `Job` enum EVAN1–EVAN10 (2200–2218), EVAN (2001) | `client/Job.java:59-63` |
| Evan skill constants (42 of 58 ids) | `constants/skills/Evan.java` |
| `Dragon` map object | `server/maps/Dragon.java` |
| Dragon spawn/move/remove packets | `tools/PacketCreator.java:7340-7370` |
| Dragon opcodes `0xB5/0xB6/0xB7` | `net/opcodes/{Send,Recv}Opcode.java` |
| `MoveDragonHandler` + registration | `net/PacketProcessor.java:447` |
| Dragon spawn on map enter / despawn | `server/maps/MapleMap.java:2676,2856` |
| `createDragon()` on job change | `client/Character.java:1245-1266` |
| **Extended SP** (Evan's 10-slot SP array) | `tools/PacketCreator.java:166-178` |
| `sp VARCHAR(128) DEFAULT '0,0,...'` | `db/tables/002-character.sql:28` |
| `getSkillBook()` → 21 for Evan | `constants/game/GameConstants.java:411` |
| 13 Evan quest scripts + 26 portal/quest scripts | `scripts/quest/22*.js`, `scripts/portal/evan*.js` |

**Database schema needs zero changes.** `sp` is already the extended-SP column, `skillid` and
`job` are `INT`. Verified.

---

## 2. The complete gap list

Everything missing from your v83 data. Each was checked, not assumed.

| # | Gap | Evidence | Source of fix |
|---|---|---|---|
| 1 | Evan skills | `wz/Skill.wz` stops at Aran `2112.img.xml` | XML pack ✅ |
| 2 | Evan skill strings | `String.wz/Skill.img.xml`: zero `22xxxxxx` | XML pack ✅ |
| 3 | Dragon animations | no `Skill.wz/Dragon/` dir | XML pack ✅ |
| 4 | Dragon equip tiers 2003/2004 | you have `×2000-2002` only (12 of 20) | XML pack ✅ |
| 5 | Evan body action frames | you have 9 body imgs, v84 has 15 | XML pack ✅ |
| 6 | **Evan quest data** | `QuestInfo.img.xml` has Aran `21000/21001/21010…`, **zero `22xxx`**. Confirmed: GMS/84 serves quest 22000, GMS/83 404s | **v84 Quest.wz** ✅ |
| 7 | **Six Evan maps** | v83 has only `100030000/1`; scripts need `100030102, 100030103, 100030200, 100030300, 100030310, 100030400`. Confirmed present in GMS/84 | **v84 Map.wz** ✅ |
| 8 | **Evan NPCs** | NPC `1013101` (quest 22000's giver) absent; v83 has only `1013000` in that range | **v84 Npc.wz** ✅ |
| 9 | Evan character creation | `Etc.wz/MakeCharInfo.img` has `CharMale/Female`, `OrientChar*` (Aran), `PremiumChar*` (Cygnus) — no Evan block | v84 Etc.wz ✅ (or skip — use a job-change NPC) |
| 10 | Real v84 SkillEx UI | the XML pack's `UIWindow.img` is a **Big Bang** dump, author says so | **v84 UI.wz** ✅ |

**Gaps 6, 7 and 8 were not in the first draft of this scope and are not covered by the Evan XML
pack** — the pack is Skill/Character/String/UI only. They are real, and they're why the estimate
moved from ~1 week to ~2.

### Checked and fine — no work needed

Dragon equips `0194-0197 × 2000-2002` are already in your v83 tree **with full stats**; all 12
names already in `String.wz/Eqp.img` (lines 22289–22322); Afrien is present; maps
`100030000/100030001` present; Aran quests present; DB schema needs nothing.

---

## 3. Part 1 — Evan on v83 (~2 weeks)

### 3.1 Client patch — 1 hour

Hex-edit a **copy** of `local.exe`: search the 21-byte pattern, replace with `90`×21. Launch it.
If it runs, the whole project is viable. If Themida blocks it, switch to runtime patching.
Repeat for `localhome.exe`. Optionally rename `Evan0…Evan10` string pools with STREDIT.

**Do this first. It is one hour and it de-risks everything else.**

### 3.2 WZ imports — 3–5 days (the bulk)

Two distinct outputs, don't conflate them:
- **Client WZ** — binary `.wz` at `D:\games\MapleStory\`, edited in HaRepacker (GMS encryption)
- **Server WZ** — XML at `Cosmic\wz\`, "Private Server" export

| Step | From | Into |
|---|---|---|
| Skill `2001` + `2200/2210/2211/2212/2213/2214/2215/2216/2217/2218` | XML pack | Skill.wz |
| new `Dragon` WzDirectory (10 animation imgs) | XML pack | Skill.wz |
| `Character/Dragon` — 20 equips (8 new tiers) | XML pack | Character.wz |
| body imgs `00002000`–`00002014` (replace 9, add 6) | XML pack | Character.wz |
| `String.wz` Eqp.img + Skill.img (clear + reimport) | XML pack | String.wz |
| `UIWindow.img/SkillEx` + `SkillMacroEx` | **v84 UI.wz**, not the pack | UI.wz |
| **Evan quests `22xxx`** (QuestInfo/Check/Act/Say) | **v84 Quest.wz** | Quest.wz |
| **6 Evan maps** | **v84 Map.wz** | Map.wz |
| **Evan NPCs** (`1013101`+) | **v84 Npc.wz** | Npc.wz |
| `MakeCharInfo` Evan block *(optional)* | v84 Etc.wz | Etc.wz |

Then re-export everything imported as **Private Server XML** into `Cosmic\wz\`.

**Use WzComparerR2 to diff v83 vs v84** and get the exact node list for gaps 6/7/8 rather than
guessing — that tool is in `reference-sources/` for this reason.

Note the XML pack's XMLs carry `basedata="<base64 png>"` (full HaRepacker export). Correct and
required for the client. For the server, either re-export as Private Server format or strip the
attribute — `2218.img.xml` is 14 MB with images, a fraction of that without.

### 3.3 Skill implementation — 2–3 days

Counted from the real v84 skill data. **58 Evan skills exist. 14 are actual work.**

| Bucket | Count | Cost |
|---|---|---|
| Data-only — `StatEffect.loadFromData` already handles them | **29** | none |
| Mount/riding buffs — all reuse existing `MONSTER_RIDING` | **12** | one case list, once |
| Beginner-common (`RECOVERY`, `NIMBLE_FEET`, `ECHO_OF_HERO`) | **3** | add the Evan id |
| **Evan-specific** | **14** | below |

The 29 free ones include every nuke — `MAGIC_MISSILE`, `FIRE_CIRCLE`, `LIGHTNING_BOLT`,
`MAGIC_FLARE`, `DRAGON_THRUST`, `KILLER_WINGS`, `EARTHQUAKE`, `FLAME_WHEEL`, `BLAZE`, `DARK_FOG`
— and all passives. Damage, mob count, element, mastery come from WZ.

| Skill | ID | Work |
|---|---|---|
| Magic Guard | 22111001 | reuse existing stat — add case |
| Elemental Reset | 22121001 | reuse — add case |
| Magic Booster | 22141002 | reuse — add case |
| Maple Warrior | 22171000 | reuse — add case |
| Hero's Will | 22171004 | reuse — add case |
| **Magic Shield** | 22131001 | **new BuffStat mask** |
| **Magic Resistance** | 22151003 | **new BuffStat mask** |
| **Soul Stone** | 22181003 | **new BuffStat mask** (revive-on-death) |
| **Slow (Evan)** | 22141003 | **new BuffStat** — distinct from existing `SLOW` |
| **Phantom Imprint** | 22161002 | **new mob-debuff stat** |
| Ice Breath | 22121000 | charge/keydown attack — extra int in `AbstractDealDamageHandler` |
| Fire Breath | 22151001 | same charge path |
| Recovery Aura | 22161003 | bespoke — party HP/MP aura |
| Blessing of the Onyx | 22181000 | bespoke — party buff |

**Totals: 5 new buff masks, 2 charge attacks, 2 bespoke party effects, 5 one-liners.**

**16 skill IDs missing from `Evan.java`:** `20011018 20011019 20011020 20011025 20011026 20011027
20011028 20011029 20011030 20011031 20011037 20011038 20011039 20019000 20019001 20019002`.
Thirteen are named in the archived guide's `EvanJr` class; `20019000-2` are unidentified and
carry damage values.

### 3.4 Remaining server Java — 1 day

- `CreateCharHandler.java` — currently only handles job 0/1/2. Add `case 3` + `EvanCreator`
  (copy `LegendCreator`; start job 2001, map 100030000). *Or skip and use a job-change NPC —
  the creation UI needs a client edit regardless.*
- `constants/skills/EvanJr.java` — the `20011xxx` ids (guide has the list)
- `SkillFactory` buff/non-buff switch — add breaths + Evan buffs
- `client/BuffStat.java` — the 5 new masks
- `Character.levelUp()` — Evan HP/MP growth (as Magician) and Evan's fixed-job-level SP awards
- `AbstractDealDamageHandler.parseDamage` — add the two breath skills to the charge branch

### 3.5 Skill crash audit — 1–2 days

**Do this before 3.3, not after.** A skill can be perfectly implemented server-side and still
crash the client because its *action* (animation name) isn't in v83's string pool — and per the
release author, *"actions are client-sided StringPools, so you can't just add and implement an
entire new set of v84 actions."* Test every skill in-client; strip the crashers from your
Skill.wz import so they never appear. No point coding a skill you'll delete.

This is the permanent ceiling on Evan-at-v83: some skills will never work.

### 3.6 Integration & test — 2–3 days

Job-change to 2001 → dragon spawns → quest chain runs → dragon evolves through job levels →
SP allocation works → each surviving skill fires without crashing.

---

## 4. Part 2 — Dual Blade

### Why v83 can't have it

Evan works because the v83 binary *already contains* Evan support behind an `if`. Dual Blade
came 5 patches later; there is nothing to un-gate. Missing entirely from v83:

1. **Katara routing** — kataras (`1342xxx`) equip to the shield slot but must render as a
   *weapon*. Post-v88 clients have an `is_katara()` reroute. v83 looks in `Character.wz/Shield/`,
   finds nothing, renders nothing or crashes.
2. **Dual-wield animations** — DB attack actions and the two-afterimage blends don't exist in
   v83's body imgs, and actions are client-side string pools that cannot be added.
3. **Job-branch awareness for 430–434** — job level, skill tabs, SP allocation.
4. **The DB advancement flow** (Lith Harbor, the 2nd-job-at-20 quirk).

Items 1 and 3 need **new machine code injected into the client**, not a NOP. No public v83 Dual
Blade release exists; every DB implementation targets v95/v97/v117.

Server-side is nearly free by comparison — `getWeaponType()` at
`ItemInformationProvider.java:616` maps weapon category 34 (katara) to `NOT_A_WEAPON`; that's a
one-line array fix. The client is the entire problem.

### Options

**A. Reskin — ~2 days. Recommended.** Ship "Dual Blade" on existing thief job IDs (410/411/412),
dagger-only, with DB-flavoured names/icons/damage edited into existing v83 thief skill slots. No
katara, no dual-wield, no client edit. Players get the fantasy, not the mechanics.

**B. Real v83 port — unbounded, likely dead end.** Reverse the v83 client in IDA, inject katara
routing and the 43x branch. If you must try: **timebox 2 days to answer "can I make a katara
render on a v83 character at all?"** Everything downstream is moot if that fails.

**C. v92 version-up — see Part 3.** The only path where DB genuinely works.

---

## 5. Part 3 — v92 version-up (keep this codebase, change the client)

**Your changes survive this.** This codebase is v83 only at the packet layer, and that layer is
unusually well isolated — `grep writeShort(SendOpcode` returns 3 hits in `PacketCreator.java`,
1 in `AbstractAnimatedMapObject`, 1 in the `OutPacket` base. No packet building is scattered
through game logic.

| Surface | Size | Version-specific? |
|---|---|---|
| `net/opcodes/SendOpcode.java` | 307 entries | **yes** |
| `net/opcodes/RecvOpcode.java` | 178 entries | **yes** |
| `tools/PacketCreator.java` | 7,462 lines | **yes** |
| `net/server/channel/handlers/*` | 147 files | inbound parsing only |
| **everything else** | — | **no** |

Your drop tables, `config.yaml` rates, boss spawns, 708 NPC scripts, quest logic and balance work
all sit above that seam.

**Target v0.92 and nothing higher.** Final pre-Big-Bang; has Evan *and* Dual Blade natively — no
WZ surgery, no hex patching, no missing animations, because Nexon wrote that code. v93+ is Big
Bang and would invalidate your content and balance work. v95 is tempting (its `.idb` leaked) but
it is post-BB.

Work: bump `ServerConstants.VERSION`, re-map ~485 opcodes, fix the changed structs (char data,
item encoding, **buff masks — most error-prone, the whole ordering shifts**, damage packets,
movement fragments, login/world list, mob spawn), inbound parsing, swap `wz/`, audit
`constants/id/*.java`, audit 708 scripts.

**The one resource with no ready-made answer: there is no public v92 opcode table.** You generate
it — `tools/ida-universal-opcode-finder.py` walks `COutPacket` construction sites in IDA and dumps
opcodes from any client. Combine with MapleShark against a live v92 client. This affects the DB /
version-up track only; nothing on the Evan track is blocked by it.

---

## 6. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Themida rejects the patched binary | low–medium | blocks everything | patch a copy, launch it, hour one. Fallback: runtime patch via `dinput8.dll` |
| Some Evan skills unfixably crash the client | **certain** | reduces the class | audit early, strip crashers from the WZ import — §3.5 |
| WZ edit corrupts the client | medium | recoverable | backups exist (§7); WZ editing is destructive and not git-friendly |
| Quest/Map/NPC import larger than estimated | medium | +days | diff with WzComparerR2 before starting to get the real node count |
| Dual Blade v83 spike fails | **high** | 2 days lost | timeboxed by design |
| v92 port stalls on packet structs | medium | months | spike "v92 client reaches character select" first |

---

## 7. Backups (both verified)

| What | Where | Size |
|---|---|---|
| Server tree (incl. `.git`, `wz\`) | `_backup\Cosmic-2026-08-15\` | 76,418 files / 1.77 GB |
| Client (`.wz`, exes, dlls) | `_backup\client-v83-EzorsiaV2-2026-08-15\` | 213 files / 2.4 GB |

**Three WZ trees exist — know which you're editing:** the client's binary `.wz` at
`D:\games\MapleStory\` (~1.9 GB), Cosmic's XML at `Cosmic\wz\` (591 MB, git-tracked), and a third
XML copy at `Server\wz\` (592 MB) whose consumer you should confirm.

---

## 8. Resource inventory — all obtained

| Resource | Location | Note |
|---|---|---|
| Eric's Evan XML pack | `evan-xml/extracted/Evan WZ/` | 65 MB; Skill 2001+10 job files, Skill/Dragon, 20 dragon equips, 15 body imgs, String, UI |
| **v84 WZ, extracted** | `wz-data/v84/` | Quest, Map, Npc, Character, Skill, String, UI, Etc, Item, Mob, Reactor — **closes gaps 6–10** |
| GMS v0.84 / v0.92 installers | `clients/` | 1.85 GB / 2.18 GB |
| v83→v84 manual patcher | `clients/ManualPatcherv84.exe` | 99 MB — the precise Evan delta |
| HaRepacker + HaCreator 11.0.0 | `tools/` | WZ edit / map edit |
| **HxD** | `tools/HxDSetup.zip` | the hex patch |
| **IDA universal opcode finder** | `tools/ida-universal-opcode-finder.py` | generates the v92 opcode table |
| STREDIT | already at `D:\games\MapleStory\` | string pools |
| 16 archived threads | `docs/` | incl. both complete Evan walkthroughs |
| 11 reference repos | `reference-sources/` | MapleShark, WzComparerR2, MapleLib, HeavenMS upstream, 4× v95 sources |
| Audit findings | `docs/99-AUDIT-FINDINGS.md` | full evidence for §1–2 |

Nothing outstanding. The v84 installer was extracted directly with 7-Zip by carving its two
spanned MSZip CABs (`MapleStory_1.cab` / `MapleStory_2.cab`) — no install required, and the same
technique works on the v92 installer when you get there.

---

## 9. Recommended sequence

1. **Hour 1 — patch a copy of `local.exe` and launch it.** Go/no-go for the entire project.
2. **Day 1 — `!job 2001`, confirm a dragon spawns.** Proves the server half end-to-end.
3. Diff v83↔v84 with WzComparerR2; get the exact node lists for quests/maps/NPCs.
4. WZ import pass, both client and server trees.
5. Skill crash audit — decide what survives.
6. Java: the 14 skills, the 16 missing constants, `levelUp`, creation.
7. Ship Evan. Then decide Dual Blade: reskin (A) or commit to the v92 version-up (C).

**Do not start Dual Blade before Evan ships.** Evan proves out the entire WZ + client-patch
pipeline at a fraction of the risk.
