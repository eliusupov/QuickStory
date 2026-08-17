# v84 open items

Living tracker. Branch `worktree-evan-dualblade`. Acceptance bar is the owner's: **"as it was in gms
v84"**, additive only, and **code clean and concise, conforming to what is already there**.

Status keys: `IN FLIGHT` an agent is on it · `QUEUED` known, not started · `OWNER` needs his decision
or his hands · `UNKNOWN` correct value not establishable from data yet.

Rule that produced most of this list: **anything the server keeps in its own database is a candidate
gap; anything it reads from the WZ is already present.** The v84 client is complete - the gaps are
server-side.

---

## 1. Blocks an Evan playthrough, in play order

| lv | item | status |
|---|---|---|
| 11 | ~~Quest 22503 item 4032453 "Pork"~~ **FIXED** `d8b6372db`, changeSet 159. Drops from mob 1210100 "Pig", chance 200000 copied from that same mob's own 25-count Aran row (q21710) - NOT changeSet 155's count-1 rate, which is the wrong shape for a x10 fetch. Reachability re-derived independently: **28/135 startable before, 135/135 after**. Applies on next boot. | DONE |
| 23-70 | **Seven quests gate on quest-records 22597-22605 that nothing ever writes.** `Quest.canStart` AND `canComplete` both call `canQuestByInfoProgress`; `autoStart`/`autoComplete` do NOT bypass it. States: 22530(22597="5"), 22556(22598="1"), 22557(22598="2"), 22580(22599="1"/"2"), 22588(22605="1"), 22589(22600="1", 22604="1"), 22591(22601="1"). Mechanism is live - `setQuestProgress` exists and 59 scripts use it. 22530's writer is derivable: npc 1022107 already placed on the five right maps (101030000/100/200/300/400), needs `scripts/npc/1022107.js`; must land record 22597 on exactly `"5"` (string equality) so it needs a dedupe scheme - **UNKNOWN**. Other six triggers UNKNOWN. | QUEUED |
| 50 | **Quest 22402 Dragon Mount branch needs PlayerNPC 9901000 on map 102000004** (Hall of Warriors). Correct as designed - do NOT place it as a normal NPC; `NpcLocation.img` and `COLLISION-DENY.txt:406` both say so. `playernpcs` table has 0 rows. Kills 22403-22413 and the Monster Rider skill grant. | OWNER |
| 50 | Item 4032474 "Seruf Pearl" (22404/22405): drop rows exist on mobs 4220000 and 9303014, but neither mob is placed in any map. Which v84 map carries Seruf: **UNKNOWN**. | QUEUED |
| 67 | ~~Route into 922030000 (Hiver)~~ **FIXED** `dda2d5f5a` - v84 has `220000300/scr00`, our merge had refused it on an index-arithmetic technicality. | DONE |
| 70 | **Slumbering Dragon Island needs 8 missing scripts**: `enterSDI`, `move_SDIRit`, `enterSnowDragon`, `onSDI`, `stopIceWall`, `stopIceWall2`, `summonIceWall`, `blackSDI`. Both `enterSnowDragon` (pt=7) and `enterSDI` (pt=8) are `tm=999999999` - destinations are genuinely server-side-only and in no client file. Strands 22300, 22590, 22591. | QUEUED / UNKNOWN |
| 70 | **Quest 22596's mob 9300393 "Gentleman" is placed by no map** - confirmed in pristine v84 too (0 hits / 4505 images). Room IS named by the quest text: 922030001, whose `onUserEnter` hook is `enterBlackfrog` (lowercase f) and does not exist. Spawn **coordinates** are in no WZ file - UNKNOWN. | QUEUED / UNKNOWN |

## 2. Evan skills - data complete, code incomplete

Worsens with job level. `IN FLIGHT`.

- **22171004 Hero's Will unlearnable** - `isFourthJob()` true + `getMasterLevel()` 0; no mastery book exists in `wz/Item.wz`; also absent from `StatEffect.isHerosWill`.
- **2217-block mastery books unusable by a maxed Evan** - `ItemInformationProvider.java:1481` matches `curskill / 10000 == playerJob`, and Evan's skills stay in 2217 while the job advances to 2218. Every other class is immune. Fix must not change any other class.
- **22181000 Blessing of the Onyx applies nothing** - no `effect` node, `action/0="OnixBlessing"` != `alert2`, absent from SkillFactory's isBuff switch. Evan's capstone party buff is a no-op.
- **22181003 Soul Stone** - empty statup list, absent from `StatEffect.isResurrection()`.
- **22161003 Recovery Aura CRASHES** - `MapleMap.java:2310-2322` reads the *recipient's* skill level; a party member without the skill hits `effects.get(-1)` -> IOOBE in a scheduled task.
- 22140000 Critical Magic - Evan absent from `canCrit` list.
- 22131001 Magic Shield / 22151003 Magic Resistance - `BuffStat` written, never read.
- 22000000 Dragon Soul - Evan's FIRST job skill, pure passive, zero references, never enters `localmagic`.
- 22160000 Dragon Fury, 22170001 Magic Mastery - zero references.
- masterLevel never read from Skill.wz; `isFourthJob()` is a hardcoded 5-id list (over-permissive).
- 20011011 Power Explosion missing from the BOOSTER arm.
- Evan mounts 20011018/19/31 not in `SKILL_MOUNTS`.

## 3. Drops, cards, maker, reactors

- Mob drops **LARGELY CLOSED** - changeSet **160**, 214 rows over 25 mobs. Items taken from the v84 client's own `MonsterBook.img`; rates **all DERIVED** from our own tables as `median(item class x mob level band x boss flag)` over 22,461 non-quest-gated rows. Holdout-validated: **median fold-error 1.14x, 73.2% within 2x, 94.6% within 10x**; weakest classes `use` and `etc_mobdrop` at 2.0x, flagged in the seed header. `boss_drop_rate: 10` checked and neutral by construction, because each rate is the median of rows from the same (class x boss) bucket.
  - **Triage was most of the job.** 274 mobs can spawn with no drop row; **250 (91%) are event / PQ / Monster Carnival / boss-body-part** ids that never had drops in any version. Of the 24 on ordinary ids, nearly all are correctly empty (Papulatus and all four Targa/Scarlion phases drop on their FINAL form; the 6 Gatekeeper Nex are kill-count quest targets). **Genuine remainder: one mob, 4090000 Iron Hook.** `QUEUED`
  - **DreamMS rejected, measured twice.** `ours = their_pct * 10000 / 3` - a flat 0.333 at p10/p25/p50/p75 across 2,621 pairs, in all 7 level bands and every item class independently. It is our own lineage; calibrating against it is circular and a missed divisor would have tripled every rate. Valhalla/MCDB also rejected: p10 0.05 -> p50 0.114 -> p90 1.25, only 3.0% exact - noise, not a factor.
  - Authentic Nexon probabilities exist only in `Etc.wz/Server/Reward.img`, which our `Etc.wz` does not contain. **No number in our tables is a Nexon figure** and the seed header says so.
- Monster cards **CLOSED 39/39** `64f0858e1` - every card names its mob in `Item.wz/.../info/mob`. **4 still cannot drop** (mobs 3400008, 4300001/3/5 placed nowhere, in v84 either). **7 pre-existing rows disagree with the WZ leaf** (2383045, 2388011/17/26/43, swapped pair 2388068/69) - not touched, that is an edit not an addition. `QUEUED`
- Maker **CLOSED 6/6** `3f1f81b32` incl. all four Evan dragon slots; `req_meso` formula validated 145/145.
- `wz/Etc.wz/ItemMake.img.xml` is still the **v83 file** - harmless today (server reads only `catalyst`) but a fresh `SkillMakerFetcher` run would drop the six. `QUEUED`
- Reactors: only **1** genuinely new (2302006) and it is placed on no map, so nothing should drop. Closed as a false premise.

## 4. Cash shop and new items

- NX-loss: action `0x1E` has no `isPackage` guard - deducts cash then NPEs. `IN FLIGHT`
- **6 purchasable packages are completely inert** (9102289-9102294, 2000-4700 NX) - item types 522/553/562 have no handler. `IN FLIGHT`
- 25 tablet scrolls 2047000-2047309 stuck at **0% success** - they carry `successRates`, not `success`. `IN FLIGHT`
- 79 Commodity rows have no `Period` -> 1-day items, incl. **SN 60001005 Pink Bean pet at 20,000 NX**. `IN FLIGHT`
- **279 of 332 new v84 items have no source of any kind** - no drop, shop, quest, reactor, Maker or cash-shop row. Includes all 18 new endgame weapons (reachable only from containers that are themselves unobtainable). `QUEUED`
- 21 items 2430xxx have `spec/script` and no script file (incl. 5 mount coupons that ARE obtainable). `QUEUED`
- 5240028 Dynamite feeds pet 5000067, which has no source. `QUEUED`
- `isMasteryBook` range (2290000-2290139) excludes the 13 new books. Cosmetic.
- Evan SP resets 5050005-09 do not enforce their per-tier restriction.
- `UseCashItemHandler.java:552` is unreachable dead code (duplicate `itemType == 552`, commented "DS EGG THING", probably meant 553).
- Commodity divergence vs real v84 on shared SNs: ItemId 19, Price 28, Period 6, Count 4, OnSale 81 - none on v84-new SNs. SNs 60001000-60001005 are local additions absent from v84.

## 5. Naming and cosmetic

- **11 Evan NPCs still carry Korean names** in `wz/String.wz/Npc.img.xml`: 1013000 (Mir), 1013001, 1013002, 1013100 (quest 22000's first NPC), 1013101-1013105, 1013201, 1013202. Their neighbour 1013203 was force-merged to English, so per-id comparison was done and only that one forced. Whether v84 ships English for the other 11: **UNKNOWN**. 46 Korean names total in that image.
- Medals **1142152** and **1142155** have no `String.wz` entry -> medal chat prefix renders `<null>`. Correct values ARE recoverable from `porting-resources/evan-xml/.../String/Eqp.img.xml:1492-1503`: "Well-Behaved Child" and "Secret Organization Temporary Member", corroborated by `QuestInfo.img/29934` and `/29937`.
- Item 4032526 (22572) has an `Item.wz` node but no `String.wz/Etc.img` entry. Name UNKNOWN.
- **Dragon equipment cannot be worn and has no name.** All 12 ids exist with `islot="Tm"`, but `EquipSlot.java:26` has one `Tm` entry - `TAMED_MOB(-18)`, the mount slot. Correct v84 slot: UNKNOWN. Separately `getStringData()` routes 1900000-2000000 to `Eqp/Taming` while the names live at `Eqp/Dragon/<id>` - that half IS fixable from data already in the repo.

## 6. Maps and scripts

- `100030301` is absent from `Map.wz` (deliberate - its life places NPCs inside the PlayerNPC allocator range) but `scripts/portal/inDragonEgg.js:8` still warps there. `isQuestStarted` is `== STARTED` only, so it fires before 22005 starts AND after it completes. Caught as an NPE, not a stranding.
- `910050300` declares `onUserEnter = "dollCave01"`; no such script. Silent no-op, quests unaffected.
- `900090104` (incubation cutscene) has no inbound portal. Cosmetic.
- **328 non-Evan quests declare a script with no `.js`.** All 49 Evan ones have theirs. The 8 in `19011` / `29934-29940` were checked and need nothing - they are medal-title markers with empty `Act.img`, covered by the `medalQuest.js` fallback.
- 331 dead portals / 105 script names on reachable maps; ~50 silent-map instances.

## 7. Infrastructure debt

- Packet validator covers **33 of 307 sendops (10.7%)** and is **blind to enum renumbering** - it checks byte counts, not meaning. `SPAWN_DRAGON` was a packet it modelled wrong, and that is what caused the login loop. Named next step: per-mode models for the 90 opcodes that branch on a mode byte.
- `USE_DEBUG_SHOW_RCVD_PACKET` still true - log spam.
- CLIENT_START_ERROR de-dup is in-memory, so every restart re-reports old crashes.
- Evan never receives its **own** `SPAWN_DRAGON` after a map change (`addPlayer` places objects before the dragon is registered; `spawnDragon` broadcasts excluding the source). Server-side `getDragon()` stays non-null so quests are fine. Whether the v84 client tears down its local `CDragon` on field transfer: **UNKNOWN** - needs a client.

## 8. Owner's hands or owner's call

- **One server restart owed** - the new changesets apply on next boot, and the `Map.wz` portal edits need a map reload. Live DB still shows the pre-changeSet counts.
- **HD client** - loader DLL builds and loads, byte guard proven 288/288. Everything about appearance is unproven; needs a client launch, which I must not do (it rewrites the shared `HKLM` ExecPath and would repoint the live v83 install). Procedure in `tools/hd/README.md`.
- **WZ phase B tree** built and content-verified at `D:\games\wz-stage\phaseB\tree` (18 .wz, 2.0 GB, 6,073/6,073), **not installed anywhere**. Open inside it: 5 deletion rows, 124 positional-array rows, additive-layer question on 17 images.
- **Dual Blade** - not started ("i want evan first, dual blade can be after").
- Never answered: 2x event on/when, PQ bonus EXP (has not paid since 2015), cash-shop OnSale, Easy Balrog, CPQ party size.

## 9. v84 content that exists and cannot be reached  *(found 2026-08-17, non-Evan sweep)*

- **CRIMSON SKY - v84's single largest content addition has no entrance.** Ticket 06 merged 22 maps,
  7 huntable mobs and 776 drop rows, and left "travel route works" explicitly unchecked as an owner
  decision. Still true at HEAD: `240030102` (the only edge from existing content) has no `right00`
  back, and v84 ships that map byte-identical to v83; `grep` for `240080000` / `Sky_GateMapEnter` /
  `dragonLair_GL` across `scripts/` and `src/main/java` returns **zero hits**; the two "Crimson Sky
  Doorway" NPCs 2085001/2085002 have `func=None` and no script. Reachable today only by `!warp`.
  Second gate behind it: `240080000` has `fly=1` + `needSkillForFly=1` (Dragon Rider flying skill).
  **OWNER decision: NPC-script warp, hand-authored portal, or leave GM-only.**
- **8 more non-Evan maps unreachable for the same structural reason** (ticket 08): `910600000`
  Golem's Temple Entrance and the Frog House / Sky Terrace chain `922030010/011/020/021/022`. v84's
  inbound portal lands at an array index that means something else live - e.g. `106010101/portal/5`
  is the WORKING `out00`, so merging v84's row verbatim would break a live route. The merge tool was
  right to refuse; the data was never revisited. Same owner decision as Crimson Sky. `OWNER`
- **NPC 1011101 "General Mau" is a dead vendor in live Henesys Market** (`100000100`, town, reachable
  today). Its `String.wz` `func` reads "Street Vendor" but there is no `scripts/npc/1011101.js` and
  no `shops` row. Clickable, does nothing, in a hub players walk through. `QUEUED`
- NPC 9000054 "Ranch Owner" on `109090000`/`910040000`/`910040002` - no script, no shop, no quest
  reference. Shape resembles a Monster-Carnival-style instance. Intended role **UNKNOWN**.

## 10. Items and buffs that exist and do nothing  *(found 2026-08-17, parity-surface sweep)*

The "written but never read" pattern the Evan audit found is **not Evan-specific**. Confirmed wider:

- **5 chaos scrolls are no-ops that still burn the upgrade slot**: `2049103/104/112/113/114` carry
  `info/randstat=1`, but `ItemInformationProvider.java:1110-1184` special-cases only `2049100-102` by
  hardcoded id; the rest fall through to `improveEquipStats` against an empty stat map. Slot and item
  both consumed, nothing applied.
- **7 percentage buff potions grant zero stats**: `2022359-2022365` carry `padRate/madRate/pddRate/
  mddRate/accRate/evaRate/speedRate`; `StatEffect.java:370-378` reads only the flat versions.
- **6 brand-new v84 `BFSkill` items are wholly unwired**: `2022539/542/543/547/548/549`, zero code
  references anywhere.
- **Shadow Web's damage-over-time is commented-out dead code** (`Monster.java:1328-1336`) - Hermit,
  Night Walker and Blaze Wizard bind the target and deal 0.
- **Mage Seal never blocks mob skills** - `StatEffect` writes `MonsterStatus.SEAL` but the mob-skill
  gate checks a different constant, `SEAL_SKILL`. Affects FP/IL Mage and Blaze Wizard.
- **Invincible Barrier and Cleric's Invincible do nothing** - `DIVINE_BODY`/`INVINCIBLE` are written
  but absent from `TakeDamageHandler`'s mitigation chain. The buff icon shows.
- **Monster-card immunity piercing does nothing** - `RESPECT_PIMMUNE`/`RESPECT_MIMMUNE` written at
  `StatEffect.java:485/489`, never checked by the immunity gate at `AbstractDealDamageHandler.java:261-269`.
- **Echo of Hero's +4% watk** written but absent from `recalcLocalStats()`.
- **All chairs heal identically** - `info/recoveryHP/recoveryMP` (56/35 items, several v84-new) never
  read; regen is derived from the player's own max HP/MP.
- `spec/onlyPickup` unenforced on 83 items (5 v84-new). Cash coupons crediting nothing:
  `info/maplepoint` (5200009/10), `info/slotIndex`+`addDay` (5550001). `incMaxHP/incMaxMP/incReqLevel/
  incCraft/incLEV` reach the stat map but `improveEquipStats` has no case for them.

Swept and **clean** - do not re-check: DB content tables (`area_info`, `plife`, `questactions`,
`questrequirements`, `specialcashitems`, `monsterbook`, maker siblings), Etc.wz image coverage, the
pet system end to end, mob `mobType`->`category` rename (dead field before and after), and skill
`isBuff` classification across all **627** skills - Blessing of the Onyx is the ONLY instance of that
failure shape.

---

## Corrections to keep - do not re-derive these wrongly

- "Playable 1 to 67" was **wrong**: the real stop is quest 22503 at **level 11**.
- "v84 adds 10,459 cash shop entries" was **wrong** - that counted paths; the real delta is **116 SNs**.
- "6 new reactors" was **wrong** - 1 genuinely new, placed nowhere.
- "Drop rates cannot be sourced" was **premature** - dreamms.gg renders numeric rates server-side in plain HTML.
- Char 48 `ghfgh` is a GM character with every skill maxed - never a valid benchmark for anything.
- Mob skill 137 is unimplemented but **no mob references it**; all 34 in-use ids are implemented. Not a gap.
- `medalmaps`, `ScriptInfo.img`, `NPT_exception.img` - not parity surfaces. Cash shop catalogue is data-driven and complete.
