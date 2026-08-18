# v84 open items

> **SECONDARY AS OF 2026-08-18. The source of truth is now `STATUS.md` + `V84-COVERAGE.md` +
> `V84-WORK-ROWS.tsv`.** This file is kept for its *reasoning* - the derivations of the quest-record
> writers, the cash-shop mode enum, the drop-rate methodology - which is worth having and is not
> repeated elsewhere. It is no longer the list of what is open.
>
> **Entries verified stale on 2026-08-18 have been cut and replaced with a one-line tombstone.**
> Every claim below that survived was re-checked against the code and the data, not against prose.
> The corrections, in full:
>
> | was | now |
> |---|---|
> | 4032474 Seruf Pearl unsourced | **stale** - reachable via the AreaBossSeruf revive chain, test-pinned |
> | Evan mount sprite ids UNKNOWN | **wrong** - two derive from `String.wz` names + offset, the third is visually a no-op. Row R14 |
> | Evan SP resets do not enforce their tier | **fixed** - `UseCashItemHandler.java:677-691` |
> | Evan never receives its own SPAWN_DRAGON | **fixed** - `b7f793e31` |
> | 11 Evan NPCs carry Korean names | **fixed** - `df9e779a9`; and v84 does ship English, the UNKNOWN is answered |
> | Medals 1142152/1142155 have no String entry | **fixed** - `8c24b6fa5` |
> | Map 100030301 Forest Hall absent | **fixed** - `93409fbea` + `8731d7516`, two tests pin it |
> | `910050300` dollCave01 - "quests unaffected" | **the map IS reachable** via `enterDollcave.js`; the hook is v84's own and benign |
> | `900090104` has no inbound portal | **deliberate and already handled** in `ChangeMapHandler.java:150-151` |
> | 328 non-Evan quests declare a script with no `.js` | **zero work** - 327 carry an expired `end` date, the 1 survivor's start NPC is placed nowhere. Also: the discriminator is *non-medal*, and "the 8 in 19011/29934-29940" should read **39** |
> | 331 dead portals / 105 names / ~50 silent | **does not reproduce** - 699/107/877 over all maps, 67/19/36 reachable. Nothing blocks a town or the Evan path |
> | NPC 1011101 General Mau has no script | **script exists** (`9bbf0dbcd`); v84's own `Npc.wz/1011101.img/info` is empty, so the stock is genuinely unknowable |
> | NPC 9000054 Ranch Owner, role UNKNOWN | **answered** - v84 names it `BF_master`, the Sheep Ranch event host. But it is v83 legacy, so out of scope |
> | "8 non-Evan maps unreachable", OWNER-deferred | **the portal-index objection died with ticket 53.** Two of those maps carry the only spawns of the mobs Evan quests 22583/22584 need, so this was never "non-Evan" and never an owner call. Rows R01/R02 |
> | 21 items 2430xxx with no script, "5 mount coupons ARE obtainable" | **both numbers wrong** - 41 dead scripts, and no 2430xxx item is obtainable by any route |
> | 79 Commodity rows have no Period | real, and 82 also lack a `Price`; but every affected row is `OnSale=0`, so it is latent debt. Row R10 |
> | Iron Hook 4090000 drop table UNKNOWN | **closed permanently** - v84's own `Etc.wz` has no `Server/Reward.img` at all |
> | Cash gachapon sub-opcodes UNVERIFIED | **settled by data already on this machine** - `ida_export_gms_v84.json` gives SUCCESS 0xEE and FAILED 0xED. Row R15 |
> | section 10 "written but never read" items | **provenance confirmed: v83 legacy, all of it.** Out of scope under "v84 parity only". Two corrections inside it: the `*Rate` potions grant their flat half rather than nothing, and Shadow Web was commented out on purpose upstream |
>
> **Three live defects this file never recorded at all**, found 2026-08-18 and now rows R41-R43:
> an Evan gains 6 MP per AP point instead of 18; there is no AP-reset HP/MP floor for Evan; and the
> owner's own character carries persisted STR-instead-of-INT damage from a monster-card bug that is
> already fixed in code.


Living tracker. Branch `worktree-evan-dualblade`. Acceptance bar is the owner's: **"as it was in gms
v84"**, additive only, and **code clean and concise, conforming to what is already there**.

## THE GOAL IS v84 PARITY. The operational test, and it is the only one:

> **Is it in the v84 data?** If yes, the server should support it. If no, we do not build it.

The owner has had to say this more than once, because the failure mode is real and I have committed
it: *"please, can you not invent or halucinate things? we have the files, we have the data. i just
want v84 feature parity. verify before working."*

So, before any work: **read the file**. Not the ticket, not a wiki, not a chain of plausible
inference - the WZ node, the `Act.img` entry, the pristine archive. `docs/wz-baseline/add-list/*.txt`
is the computed v84-minus-v83 diff and is the authority on what v84 actually added; the pristine
archives are at `porting-resources/wz-data/v84/` and a working query tool is at
`docs/wz-baseline/tool-peek/`. Where the data does not settle a value, the answer is **"unknown"** and
the work stops there. A fabricated value that looks official is worse than a documented gap - it has
cost this project real time twice.

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
| 23 | ~~Quest 22530 gates on record 22597="5"~~ **FIXED** `3000e03d1`. `Say.img/22530/0/yes/0` states the trigger outright - "go find the five Warning Signs ... and **click on them** to read them" - so the writer is npc 1022107's talk script, `scripts/npc/1022107.js`. The dedupe is no longer unknown: `QuestStatus.addMedalMap` is this codebase's existing "visit N distinct maps" idiom (`MapScriptMethods.explorerQuest` uses it for the same infoex/infoNumber shape), it refuses a map already seen, and it persists in `medalmaps` so it survives a relog. The script writes the full recount every click, never an increment, so a forfeit/restart cannot strand or overshoot. | DONE |
| 70 | ~~Quest 22588 gates on record 22605="1"~~ **WRITER BUILT** `44a8aef6a`, but **ACADEMIC** until the island opens. Fully stated by v84: `Act.img/22588/0` grants 4032473, `Map9/914100022/reactor/0` places reactor 1409000 at (-243,6) (its only placement in Map.wz), `Reactor.wz/1409000/0/event/0` is type **100** (drop-item) for exactly 4032473 x1, `info/info` = "break down the ice wall". Type 100 is already implemented (`MapleMap.searchItemReactors` -> `ActivateItemReactor` -> `act()`). File is `scripts/reactor/1409000.js`, **not** `SDIScript0.js` - `ReactorScriptManager` dispatches by reactor **id**; `Reactor.wz`'s `action` string is the client's animation script. Unreachable today: all four Cave of Silence rooms have zero static inbound portals, gated behind `enterSnowDragon` (row below). | QUEUED |
| 23-70 | ~~Five quests still gate on quest-records nothing writes~~ **ALL SIX WRITERS BUILT.** Every one was settled by the quest's own text plus the exhaustive hook list of the maps it names; none needed an owner decision. **22556/22557** (22598="1"/"2") -> `scripts/portal/evanDollGR.js`, the puppet door `106010102/portal/8`. `QuestInfo.img/22556/showLayerTag="22556"` and pristine `Map1/106010102.img/3/obj/13/tags="22556"` put a `guide/tutorial/key` "press UP" sprite at (1458,193), 5px from that portal at (1453,252), drawn only while 22556 runs - the client points at the trigger. `Say.img/22556/1/0` asks you to report "a door with a strange puppet sitting on top"; `Say.img/22557/0/0` has Camila dragged "into the Golem's Temple" and the door is the only way into 910600010, whose three `9300387` Enraged Golems are what 22559 later sends you back for. ONE record slot with TWO values = one hook hit twice, and v84 declares no map hook anywhere on that path (`910600010/info/onUserEnter` is the EMPTY STRING; 106010102 has no such node) and exactly one scripted portal. LIVE and reachable today - our tree kept the v83 plain `106010101/portal/5`, which ticket 08 refused to overwrite with v84's `evanGolemDoor`; in pristine v84 nothing else warps to 106010102 at all. **22580** (22599="1") -> `scripts/map/onUserEnter/onSDI.js`, `914100010/info/onUserEnter` - the first hook on the arrival path (200090080 and 914100000 declare every hook as `""`, and 914100000's Olaf is client-scripted `contimoveRitSDI`); (22599="2") -> `scripts/portal/stopIceWall.js`, the ten `pt=9` triggers at `914100020/portal/2..11`, which sit at x=56/153 against `VRLeft=-636 VRRight=723`, i.e. the exact "center of the island" `QuestInfo.img/22580/1` and `Say.img/22580/1/stop/default/0` both name. **22589** (22600="1") -> `scripts/portal/outSDI.js`; `QuestInfo.img/22589/0` says "**When you come out of the cave**", and 914100022's `out00` is the ONLY one of four identical sibling exits v84 scripted. The old "22588's autoComplete" candidate is refused: `Act.img/22588/1` writes no record and 22588 completes at Hiver, who is off-island. (22604="1") -> `scripts/map/onUserEnter/blackSDI.js`, `914100023/info/onUserEnter` - **the doc previously named the wrong room**; 914100021 is Afrien's and belongs to 22590/22591. 914100023 is the ambush room: only one with `bgm Bgm18/BlackWing` and ten `m 9300392` Black Wing Henchmen. **22591** (22601="1") -> `scripts/portal/outAfrienMemory.js`, `900030000/portal/1` - the memory's only server-reachable hook (both map hooks `""`, reactor empty, and its one NPC 1013205 is client-scripted `Afirentalk`); `Say.img/22591/1/stop/default/0` "if you desire to see the past again, forfeit the quest" proves the gate is satisfied INSIDE the memory, not on accept. All four island writers are ACADEMIC until `enterSnowDragon` lands (row below) - same footing as 22588's altar. Every guard tests a quest STATUS, not just membership, because `Quest.getInfoNumber` reads the START block for a NOT_STARTED quest and the COMPLETE block for a STARTED one - 22589 resolves 22600 in one and 22604 in the other, so a write one step late completes a quest that was never done. Pinned by `EvanQuestRecordGatesRealLoad` (19 tests). | DONE |
| 50 | ~~Quest 22402 Dragon Mount branch needs PlayerNPC 9901000~~ **NOT A DEFECT, already deployed.** The "0 rows" premise was stale. `playernpcs` has 6 rows and row 12 is `scriptid=9901000, map=102000004, cy=34, fh=5` - seeded by changeSet **163** (`163-hall-of-fame-data.sql`, EXECUTED 2026-08-17) and re-anchored onto v84 footholds by changeSet **165** (EXECUTED 2026-08-18); `HallOfFameSeedRealLoad` recomputes it from WZ and fails on drift. A row genuinely IS required - `QuestActionHandler.java:61-69` falls through `getNPCById` (a pnpc is `PLAYER_NPC`, not `NPC`) to `MapleMap.getPlayerNPCByScriptId`, and a `null` there is a hard refusal at :96-100; 22402 is neither autoStart nor autoComplete so it does not skip the block. `MapFactory.java:246-257` is the only producer and it reads that table. The server will never self-seed it: `PlayerNPC.createPlayerNPCInternal` requires `level == maxClassLevel` and refuses GMs, and every character in this DB is gm>=2. Cosmetic only: `NpcLocation.img/9901000/0` is **102000003** "Warriors' Sanctuary", not 102000004 "Hall of Warriors" - client-side quest-window hint, read by no server path, and the two are one `rankRoom` portal apart (`102000003/portal/9` -> `scripts/portal/rankRoom.js` -> `getMapId()+1`). Do not move the seed; `MapId.HALL_OF_WARRIORS` is 102000004. | DONE |
| 50 | ~~Item 4032474 "Seruf Pearl" has no reachable dropper~~ **STALE, CLOSED.** `scripts/event/AreaBossSeruf.js:44-51` spawns 4220001 into 230020100 at boot and `Mob.wz/4220001/info/revive/0` turns it into 4220000 on death. Pinned by `EvanPorkSourceRealLoad#theSerufPearlIsAlreadyReachableViaTheAreaBossReviveChain`. | DONE |
| 67 | ~~Route into 922030000 (Hiver)~~ **FIXED** `dda2d5f5a` - v84 has `220000300/scr00`, our merge had refused it on an index-arithmetic technicality. | DONE |
| 70 | **Slumbering Dragon Island: 5 of the 8 are written, and the list was missing a name.** Still absent: `enterSDI`, `enterSnowDragon`, `move_SDIRit`, `stopIceWall2`, `summonIceWall` - **plus `move_RitSDI`**, the outbound ferry leg on `Map2/200090080.img/portal/9..14`, which this row never recorded. Already written: `onSDI`, `stopIceWall`, `blackSDI`, `outSDI`, `outAfrienMemory`, `evanTogether`, `reactor/1409000`. Negatives re-proved over all **4,848** pristine v84 images: zero `tm` to 914100020/21/22/23, zero to 200090080/200090090/922030000. **`enterSDI` alone opens the Frog House route** (`220000300/scr00` -> `enterBlackFrog.js` -> `922030000` is live; only `922030000/tel00` is missing). `summonIceWall` and `stopIceWall2` stay REFUSED - mob 9300391 is placed in no map and produced by no revive, so count and coordinates would have to be invented. Work rows **R03**, **R46**, **R47**. | QUEUED |
| 70 | ~~Quest 22596's mob 9300393 "Gentleman" is placed by no map~~ **FIXED** - the coordinate objection turned out to be answerable. Not a revive chain: `Mob.wz/9300393` has no `info/revive` and a scan of all 1605 Mob images finds no `revive` producing it, so the AreaBossSeruf pattern is ruled out; `info/summonType=1`, `boss=1`, `exp=0`, level 70, 87000 HP. It is the hook v84 declares by name: `Map9/922030001.img/info/onUserEnter = "enterBlackfrog"` (lowercase f, deliberately a different file from `scripts/portal/enterBlackFrog.js`), now written as `scripts/map/onUserEnter/enterBlackfrog.js`. The room is a purpose-built one-mob arena - 922030001 is 922030000 with the platforms stripped: footholds 1/2/3 are identical in both (the shell, one continuous floor at y=31 from x=-310 to 314) and 922030001 keeps only the entry ledge, has empty `life`, empty `reactor` and two portals. **The spawn x is sourced, not invented**: the twin room stands Hiver at `Map9/922030000.img/life/0/x = -221`, so the hook mirrors that x and lets `spawnMonsterOnGroundBelow` settle the y onto the single floor - any x in the corridor resolves to the same surface, there is no second one to get wrong. `AreaBossSeruf.js:55` is the precedent for choosing a position at all. Identity corroborated: `Item.wz/Consume/0210.img/02100165/mob/0/id = 9300393` and `String.wz/Consume.img/2100165` is "Hiver Summoning Sack" / "Summons Hiver". Idempotence guard only, no quest gate - the room is reachable solely through `enterBlackFrog.js`, which routes there only while 22596 runs. Pinned by `V84MiscAreasNodeTest.theRageMobIsAScriptedSpawnAndTheHookNowExists`. | DONE |

## 2. Evan skills - **8 of 12 CLOSED**, re-verified at `379aa7554` (2026-08-18)

DONE, do not re-open: 22171004 Hero's Will (`ad6de3fbe`, `StatEffect.java:1791` + `Character.java:1096`);
2217-block mastery books (`c2cd9e78a`, `ItemInformationProvider.java:1548` now uses
`GameConstants.isInJobTree`); 22181000 Blessing of the Onyx (`09407afcc`, `SkillFactory.java:370`);
22181003 Soul Stone (`c9d0ca824`, `StatEffect.java:1632`); 22161003 Recovery Aura crash (`5d4e6673f`,
`MapleMap.java:2349` now uses `mist.getSourceX()`); 22140000 Critical Magic (`9da8dbb62`,
`AbstractDealDamageHandler.java:603`); 22131001 Magic Shield (`026f87e54`,
`TakeDamageHandler.java:262`); 22000000 Dragon Soul + 22170001 Magic Mastery (`a7239dcdd`,
`Character.java:7773-7783`).

Still open:

- **22151003 Magic Resistance** - `BuffStat.MAGIC_RESISTANCE` written at `StatEffect.java:665`, never
  read at `TakeDamageHandler.java:279`. Blocked on data, not on a diff: the mitigation is elemental-only
  and the element byte is read and discarded in `handlePacket`, with nothing in the tree mapping its
  values. `UNKNOWN`
- **22160000 Dragon Fury** - two references at HEAD, both inert (`constants/skills/Evan.java:43`, a
  comment at `Character.java:7769`). There is no passive damage multiplier to put +10% into and damage
  is client-computed, so the only insertion point is the autoban ceiling. Effectively unfixable and
  player-invisible. `QUEUED`
- **20011011 Power Explosion** - deliberately excluded from the BOOSTER arm (`StatEffect.java:766-772`,
  commit `875ae982b`). v84 gives Evan `x = 200` where the other three carry `-3`, and `BuffStat.BOOSTER`
  is an attack-speed step count. WONTFIX unless the real target stat is found in data.
- **Evan mounts 20011018/19/31** absent from `buildSkillMounts()` (`StatEffect.java:147-188`). **The "sprite ids are UNKNOWN" claim is wrong**: 20011031 "Balrog" shares offset 1031 with `Beginner.BALROG_MOUNT` -> **1932010**; 20011019 "Witch's Broomstick" is name-identical to `0001019` -> **1932005**; 20011018 "Yeti Rider" is ambiguous between 1932003 and 1932004, but `Character.wz/TamingMob/01932003.img:3-20` and `01932004.img:3-20` carry identical `info` and identical frame geometry, so the choice renders the same yeti either way. Three lines next to the v83 block at `:149-161`, plus inverting `V84EvanNodeTest.java:164-172`. Work row **R14**.
- **masterLevel never read from Skill.wz; `isFourthJob()` is a hardcoded 5-id list**
  (`client/Skill.java:55-62`). The node exists (`wz/Skill.wz/2217.img.xml:329,589`,
  `2218.img.xml:297,620`, all `value="10"`). Left alone deliberately: 22171004 has **no** `masterLevel`
  node, so reading it would demote that skill out of fourth-job and cap its SP at 5, which is a
  behaviour change, not a refactor. `GameConstants.getSkillBranch()` (added for the SP reset tier
  check) does **not** replace the list and must not be swapped in for it - it answers a different
  question. It returns 9 for **every** 2217 skill and 10 for **every** 2218 one, i.e. which
  advancement a skill belongs to; the list is five specific ids, and those five are disjoint from
  the four that actually carry a `masterLevel` node (22171000, 22171002, 22181000, 22181001).
  Advancement and fourth-job-ness are not the same predicate for Evan. `QUEUED`

## 3. Drops, cards, maker, reactors

- Mob drops **LARGELY CLOSED** - changeSet **160**, 214 rows over 25 mobs. Items taken from the v84 client's own `MonsterBook.img`; rates **all DERIVED** from our own tables as `median(item class x mob level band x boss flag)` over 22,461 non-quest-gated rows. Holdout-validated: **median fold-error 1.14x, 73.2% within 2x, 94.6% within 10x**; weakest classes `use` and `etc_mobdrop` at 2.0x, flagged in the seed header. `boss_drop_rate: 10` checked and neutral by construction, because each rate is the median of rows from the same (class x boss) bucket.
  - **Triage was most of the job.** 274 mobs can spawn with no drop row; **250 (91%) are event / PQ / Monster Carnival / boss-body-part** ids that never had drops in any version. Of the 24 on ordinary ids, nearly all are correctly empty (Papulatus and all four Targa/Scarlion phases drop on their FINAL form; the 6 Gatekeeper Nex are kill-count quest targets). **Genuine remainder: one mob, 4090000 Iron Hook** - spawns on `104010001` The Pig Beach and `106000110` The Burnt Land II, 0 rows in `drop_data`. **It has no `String.wz/MonsterBook.img` entry** (that image covers 385 card mobs; changeSet 160's item lists all came from it), so v84 states no item list for it and there is no other drop source in this tree. **UNKNOWN - do not invent one.**
  - **DreamMS rejected, measured twice.** `ours = their_pct * 10000 / 3` - a flat 0.333 at p10/p25/p50/p75 across 2,621 pairs, in all 7 level bands and every item class independently. It is our own lineage; calibrating against it is circular and a missed divisor would have tripled every rate. Valhalla/MCDB also rejected: p10 0.05 -> p50 0.114 -> p90 1.25, only 3.0% exact - noise, not a factor.
  - Authentic Nexon probabilities exist only in `Etc.wz/Server/Reward.img`, which our `Etc.wz` does not contain. **No number in our tables is a Nexon figure** and the seed header says so.
- Monster cards **CLOSED 39/39** `64f0858e1` - every card names its mob in `Item.wz/.../info/mob`. **4 still cannot drop** (mobs 3400008, 4300001/3/5 placed nowhere, in v84 either). **7 pre-existing rows disagree with the WZ leaf** (2383045, 2388011/17/26/43, swapped pair 2388068/69) - not touched, that is an edit not an addition. `QUEUED`
- Maker **CLOSED 6/6** `3f1f81b32` incl. all four Evan dragon slots; `req_meso` formula validated 145/145.
- `wz/Etc.wz/ItemMake.img.xml` is still the **v83 file** - harmless today (server reads only `catalyst`) but a fresh `SkillMakerFetcher` run would drop the six. `QUEUED`
- Reactors: only **1** genuinely new (2302006) and it is placed on no map, so nothing should drop. Closed as a false premise.
- **Ungated quest-item drops: 18 rows is the right count, but only 2 rows are defects.** Re-measured 2026-08-18 against 23,279 `drop_data` rows and the 1,060 items carrying `info/quest=1`: 545 rows carry a questid, **18 rows over 6 items carry `questid = 0`**. `c22ecc419` cannot touch them - its gate lives entirely downstream of the `questid > 0` test, because `Character.needQuestItem:5825` returns `true` for `questid <= 0`. Triage of the 18:
  - **14 rows are correct as-is.** 4031013 Dark Marble (8 second-job-advancement clone mobs, asked for by `scripts/npc/1072000.js:64`), 4031059 Black Charm (5 third-job clones) and 4032311 Sign of Acceptance (Aran 21202, implemented script-side at `scripts/quest/21202.js:62`) are all demanded by scripts, not by a `Quest.wz` quest, so there is no questid to gate on. Gating them would break job advancement.
  - **2 rows are genuine missing data**, each proved by a sibling row on the same item: 4031568 Cat's Eye from 2110301 Scorpion should be `questid 3911` (its 2100108 Meerkat row already is), and 4031405 Glass Shoes from 9500108 should be `questid 8732` (its 3110100 Ligator row already is).
  - 4031593 Lip Lock Key (2 rows) has no owning quest in `Check.img` at all - event item, nothing to gate on.
  - **Not fixed here**: correcting the two rows is an `UPDATE`, not an insert, and this project's standing rule is additive-only on data (same reason the 7 disagreeing monster-card rows were left). `OWNER` - the exact statements are `UPDATE drop_data SET questid = 3911 WHERE itemid = 4031568 AND dropperid = 2110301;` and `UPDATE drop_data SET questid = 8732 WHERE itemid = 4031405 AND dropperid = 9500108;`. Do **not** instead make `needQuestItem` reject `questid <= 0` - that path also serves mesos and ordinary loot (`MapleMap.java:1329`) and would make everything invisible.

## 4. Cash shop and new items

- ~~NX-loss: action `0x1E` has no `isPackage` guard~~ **FIXED** `225ee009b`, `CashOperationHandler.java:105`.
- ~~6 purchasable packages inert (9102289-9102294)~~ **FIXED** `c9d0ca824`, `UseCashItemHandler.java:552` (553) and `:633` (562). "522" in the old note was a typo for 552.
- ~~25 tablet scrolls 2047000-2047309 at 0% success~~ **FIXED** `cefab785d`, `ItemInformationProvider.java:1089-1093`.
- 79 Commodity rows have no `Period`: **the one purchasable row is FIXED** (`bdc49a200`, SN 60001005). The other 78 are all `OnSale=0` and were left deliberately. The trap survives for any hand-added row - `CashShop.java:246` defaults Period to 1 and `:142` maps 0 -> 90. Changing that default is an owner call. `OWNER`
- **279 of 332 new v84 items have no source of any kind** - no drop, shop, quest, reactor, Maker or cash-shop row. Includes all 18 new endgame weapons (reachable only from containers that are themselves unobtainable). `QUEUED`
- ~~21 items 2430xxx have `spec/script` and no script file (incl. 5 mount coupons that ARE obtainable)~~ **BOTH NUMBERS WRONG, AND NOT PARITY WORK.** There are **45** items in `0243.img`, all carrying a `spec/script`; only 2 script files exist; 2 more are rescued by the `scripts/npc/<spec-npc>.js` fallback at `NPCScriptManager.java:131`, so **41 are genuinely dead**. "21" was the `add-list` count of v84-new ids (`02430033`-`02430048`, `02430073`-`02430077`), not a missing-script count. **The "5 mount coupons that ARE obtainable" is false** - no 2430xxx item has a drop, shop, reactor, Commodity, CashPackage or quest source. The only obtainable-and-broken ones are 4 v83 quest rewards (2430007/8/31/32). So every in-scope id is unreachable and every reachable id is out of scope.
- 5240028 Dynamite feeds pet 5000067, which has no source. `QUEUED`
- ~~`isMasteryBook` range excludes the 13 new books~~ **FIXED** - `ItemConstants.java:443` now runs to 2290152, matching `ItemInformationProvider.usableMasteryBooks` and `wz/Item.wz/Consume/0229.img.xml`, whose last id is `02290152`. Its only caller is the mastery-book drop-rate multiplier at `MapleMap.java:774`, so Evan's 13 books were dropping at the ordinary rate instead of 5.0x/1.0x.
- ~~Evan SP resets 5050005-09 do not enforce their per-tier restriction~~ **FIXED** `4c3233805` + `8cfe43a6b`. `UseCashItemHandler.java:184` now calls `spResetCovers(itemId, SPTo, SPFrom)` (defined at `:677-691`), which implements exactly the `String.wz/Cash.img` pairing - 5050005 to branches 1/2 up to 5050009 to 9/10 - plus the Evan/non-Evan crossover refusal, and `:193-196` refuses to spend the scroll when nothing can move. The note "the codebase has none" is also stale: `GameConstants.getSkillBranch()` exists and is used here.
- ~~`UseCashItemHandler.java:552` unreachable dead code~~ **FIXED** `c9d0ca824`; guarded against recurrence by `CashItemTypeDispatchRealLoad.noItemTypeIsDispatchedTwice()`.
- ~~Cash Shop fails to open on v84: "Due to an unknown error, the request for Cash Shop has failed"~~ **FIXED** `652bd34df`, `PacketCreator.java:3604` (`cashShopMode`, applied at all 20 write sites). v84 inserted three arms into `CCashShop::OnCashItemResult` in the `0x4B`-`0x4D` gap, shifting the whole mode enum **+3**. All 21 modes this server sends match a v84 arm by name AND decode shape at +3 (`ida_export_gms_v84.json`); the fingerprints are `showWishList`'s 10x int32 -> `LOAD_WISH_DONE 0x52`, `showCouponRedeemedItems` -> `USE_COUPON_SUCCESS 0x5C` byte-identical (the unexplained `0x1F` short is `slotPos`), and `LOAD_LOCKER_DONE` placed at `0x4E` by dispatcher order. It presented as *unknown error* because `showWishList`'s `0x4F` is v84's `LOAD_LOCKER_FAILED`, whose body is one `NoticeFailReason` byte - an empty wish list writes `writeInt(0)`, so the client read reason `0x00`. **Wrong mode bytes are silent**: no exception, no log line, the client just dispatches elsewhere. `showCashInventory` (`0x4B`) and `showGifts` (`0x4D`) hit no v84 arm at all, so the locker and gift list were silently empty too.
- **Cash gachapon sub-opcodes - NO LONGER UNVERIFIED, and the fix is two byte literals.** `D:\games\MSv84\opcodes\ida_export_gms_v84.json` names `CCashShop::OnCashItemGachaponResult` at `0x47f8fc` with **SUCCESS mode=238 (0xEE)** and **FAILED mode=237 (0xED)**; we send `0xE5`/`0xE4` at `PacketCreator.java:6794`/`:6787`. The payload shape matches ours field for field (int64 sn, int32 remain, 55-byte `GW_CashItemInfo`, then itemId/count/jackpot), which proves it is the right handler and only the mode byte moved. Copy the `cashShopMode()` helper and its evidence javadoc at `PacketCreator.java:3570-3604`. Work row **R15**.
- **The `NoticeFailReason` byte table is UNVERIFIED at v84.** The 50-odd reason codes commented at `PacketCreator.java:7309-7358` (`00` = unknown error, `A5` = not enough cash, `C4` = check birthday...) are v83 values carried over untested. Low priority - they only change the wording of a failure notice, never whether an operation succeeds - but a wrong-worded failure is exactly what sends the next diagnosis down the wrong path. `QUEUED`
- Commodity divergence vs real v84 on shared SNs: ItemId 19, Price 28, Period 6, Count 4, OnSale 81 - none on v84-new SNs. SNs 60001000-60001005 are local additions absent from v84.

## 5. Naming and cosmetic

- ~~11 Evan NPCs carry Korean names~~ **FIXED** `df9e779a9`, and the UNKNOWN is answered: pristine v84 ships English for all 11 (Mir, Dragon, Dragon Nest, Anna, Utah, Bull Dog, Gustav, Hen, Dairy Cow, Camila, Black Shadow). 171 Korean values remain in that image; none is Evan.
- ~~Medals 1142152 and 1142155 have no `String.wz` entry~~ **FIXED** `8c24b6fa5`. Pristine v84 had both, and was the better source than `porting-resources`.
- Item 4032526 (quest 22572) has an `Item.wz` node and no `String.wz/Etc.img` entry. **Name recovered from pristine v84: "John's Map"**, desc "John's map that contains information on some island. That island is said to be the place where the dragon lies asleep." Awarded at `Act.img:4837`, consumed at `:4846`. Work row **R04**.
- ~~**Dragon equipment cannot be worn and has no name.**~~ **BOTH HALVES FIXED.** The name half was the `getStringData()` Taming route. The wearing half: all 12 do carry `islot="Tm"`, which `EquipSlot.java:26` maps to `TAMED_MOB(-18)`, so `ItemInformationProvider.canWearEquipment` refused the slot the client asked for (`logs/cosmic-log.log`, 2026-08-18 15:01: *"Chr evan2 tried to equip Silver Mask into slot -1000"*). **The slot is no longer UNKNOWN** - the client keys dragon equips off the item id, not the islot. `GetEquipPartFromItemID` switches on `itemId / 10000`; its 180-197 jump table (v83 `localhome.exe` @0x460a38, dispatched @0x4607e3) sends **194 -> 1000, 195 -> 1001, 196 -> 1002, 197 -> 1003** (arms @0x460823/31/3f/4d loading 0x3e8-0x3eb). The same table gives 190 -> 18 and 191 -> 19, i.e. the mount and saddle slots we already model, so it is the right table; and the live -1000 for a 194xxxx mask corroborates it on the v84 wire. The fourth equip list in `CharacterData::Decode` (the one `addInventoryInfo` used to leave empty) is the dragon list: v83 @0x4e5f18 clears **four** entries at `+0x427`, then accepts a decoded position only for `1000 <= pos < 1004` and stores it raw at `+esi*8-0x1b19`. Dragon equips are now written there, unencoded. Pinned by `DragonEquipNameRealLoad` (6 tests).

## 6. Maps and scripts

- ~~`100030301` "Forest Hall" is absent from `Map.wz`~~ **FIXED** `93409fbea` (import without the ten PlayerNPC anchor rows) + `8731d7516` (MapFactory guard). Pinned by `ForestHallRealLoad` and `MissingMapImageGuardRealLoad`. One residual found while verifying: `GameConstants.isHallOfFameMap()` has no `case 100030301`, so Evan statues fall into the custom branch - work row **R21**.
- ~~`910050300` declares `onUserEnter = "dollCave01"`; no such script~~ **BENIGN, CLOSED.** Our `info` node is byte-identical to pristine v84's, hook included, and the same name is shared with v83's Puppeteer's Cave family (`910510200/201/202`), none of which have scripts either. **The old note that the map is unreachable was wrong** - `scripts/portal/enterDollcave.js:12-15` warps there for anyone with quest 22549 started or complete, and quests 22550-22566 work today.
- ~~`900090104` has no inbound portal~~ **DELIBERATE AND HANDLED.** It is a client-side Direction4 cutscene map entered by a `pt` type-2 client warp, like the whole `9000901xx` band. Documented in code at `ChangeMapHandler.java:150-151` and `scripts/map/onUserEnter/incubation_dragon.js:23-30`.
- ~~328 non-Evan quests declare a script with no `.js`~~ **ZERO WORK.** Reproduces at HEAD (660 declare, 367 have none, minus 39 medal quests = 328) but **327 of the 328 carry a `Check.img/<id>/0/end` date in the past** - the latest is `201006`, all dead before the v84 client shipped - and `EndDateRequirement.java:54-58` refuses them first. The one survivor, quest 1028, is also dead: its start NPC 22000 is placed in 0 of 5,338 map images. Two wording fixes: the discriminator is **non-medal**, not non-Evan (Evan contributes 0), and "the 8 in 19011/29934-29940" should read **39**.
- ~~331 dead portals / 105 script names / ~50 silent-map instances~~ **DOES NOT REPRODUCE.** Correct at HEAD: **699** dead-portal instances (67 reachable), **107** distinct missing portal-script names (19 reachable), **877** silent-map instances (36 reachable). Nothing blocks a town or the Evan path: **0 of the 699 carry a `tm` other than 999999999 and none is `pt=2`**, so the `GenericPortal.java:143` fallback branch ticket 43 worried about has no instance in this `Map.wz`. Work row **R38**.

## 7. Infrastructure debt

- Packet validator covers **33 of 307 sendops (10.7%)** and is **blind to enum renumbering** - it checks byte counts, not meaning. `SPAWN_DRAGON` was a packet it modelled wrong, and that is what caused the login loop. Named next step: per-mode models for the 90 opcodes that branch on a mode byte. **Re-counted 2026-08-18 and the figure is current, not stale**: 307 keys in `sendops-84.properties`; 29 + 6 `verified` rows across `tools/v84/decode-models-v84.tsv` and `-binary.tsv` = 35 model names, 33 distinct opcodes once the three per-mode variants collapse. `candidate` rows are not validation-safe (`PacketStructureModels.java:46-50`).
- ~~`USE_DEBUG_SHOW_RCVD_PACKET` still true~~ **FIXED** - `config.yaml:211` set to false. It was **5,877 of 5,964 lines (98.5%)** of `tools/v84/cutover-server.log`, one line to console and one to `logs/cosmic-log.log` per received packet (`Client.java:214`, only 7 opcodes in `LoggingUtil`'s ignore list). **Turning it back on does NOT need a restart** - the GM5 command `!showpackets` toggles the flag live (`CommandsExecutor.java:535` -> `ShowPacketsCommand.java:37`, which writes the same `YamlConfig` field `Client.java:214` reads). Learned the hard way on 2026-08-18: a restart was nearly scheduled mid-session to get packet visibility back. Check this before scheduling one.
- CLIENT_START_ERROR de-dup is in-memory (`ClientStartErrorHandler.java:35`, a 512-entry LRU on heap), so the first reconnect after every restart re-WARNs the client's whole cumulative history, ~12 entries. Steady-state within one boot is correct. Nothing on disk or in the DB fits - the packet arrives pre-login, so `medalmaps`-style per-character keying is useless. Cheapest real fix is a flat file next to the log; a table is not warranted. `QUEUED`
- ~~Evan never receives its own `SPAWN_DRAGON` after a map change~~ **FIXED** `b7f793e31`. `MapleMap.addPlayer` now registers the dragon at `:2736-2739` *before* `sendObjectPlacement` at `:2741`, so the arriving owner gets it from the object list. The broadcast still excludes the source, deliberately.

## 8. Owner's hands or owner's call

- **One server restart owed** - the new changesets apply on next boot, and the `Map.wz` portal edits need a map reload. Live DB still shows the pre-changeSet counts.
- **HD client** - loader DLL builds and loads, byte guard proven 288/288. Everything about appearance is unproven; needs a client launch, which I must not do (it rewrites the shared `HKLM` ExecPath and would repoint the live v83 install). Procedure in `tools/hd/README.md`.
- **WZ phase B tree** built and content-verified at `D:\games\wz-stage\phaseB\tree` (18 .wz, 2.0 GB, 6,073/6,073), **not installed anywhere**. Open inside it: 5 deletion rows, 124 positional-array rows, additive-layer question on 17 images.
- **Dual Blade** - not started ("i want evan first, dual blade can be after").
- **The current owner-decision list is in `STATUS.md` and the report that produced it (Q1-Q9), not here.** Still never answered from before: 2x event on/when, PQ bonus EXP, cash-shop OnSale, Easy Balrog, CPQ party size.

## 9. v84 content that exists and cannot be reached  *(found 2026-08-17, non-Evan sweep)*

- **CRIMSON SKY - CLOSED AS OUT OF SCOPE. Do not re-flag this as a missing route.**
  **v84 ships the assets but not the way in, and that IS v84 parity.** Measured: all 21
  `240080xxx` maps are in the v84 archive, quests 3758/3759 are v84-new, and a scan of ALL 4,848
  images of v84's own `Map.wz` finds **zero** portals with `tm=240080000` (control: the same scan
  returns two real edges for `240030102`, so the method is not vacuous). Independently re-derived by
  the orchestrator on our merged tree with the same control - same result.
  Two consequences worth keeping:
  - **No flying mechanic needs building.** `MapFactory.loadMapFromWz` never reads `fly` or
    `needSkillForFly`; the CLIENT draws and validates flight. Grant the skill and it works - which is
    how `200090500`/`200090510` work today on five-line portal scripts.
  - **A server-side portal would be inert.** The client never sends an enter packet for a portal its
    own `Map.wz` does not draw, so reaching this content requires patching the CLIENT archive - a
    different class of work that would make the client diverge from an authentic v84 one.
  The owner's external reading puts the Dragon Rider PQ at v85; our files cannot confirm a patch
  number, only that v84 has the assets and no route. Either way it is beyond v84 parity. **Owner
  decision recorded: "lets focus on v84 for now".**
  Historical detail below, kept for whoever picks this up later:
- Ticket 06 merged 22 maps,
  7 huntable mobs and 776 drop rows, and left "travel route works" explicitly unchecked as an owner
  decision. Still true at HEAD: `240030102` (the only edge from existing content) has no `right00`
  back, and v84 ships that map byte-identical to v83; `grep` for `240080000` / `Sky_GateMapEnter` /
  `dragonLair_GL` across `scripts/` and `src/main/java` returns **zero hits**; the two "Crimson Sky
  Doorway" NPCs 2085001/2085002 have `func=None` and no script. Reachable today only by `!warp`.
  - **The flying key is skill 1026, and it is NOT Evan's.** `Act.img/3759` ("Towards the Sky 2")
    grants skill **1026** with a `job` list of `0, 100, 110, 120, 130, ...` - beginner and every
    branch. The chain is **3757** "The Dragon Rider's Identity 2" (Chief Tatamo 2081000) -> **3758**
    "Towards the Sky 1" (*"Discover the secret to flight from Matada"*, npc 2085000) -> **3759**
    grants 1026 -> **3760** "Dragonica's Horn" -> **3761** "Tears of Repentance". No `job` or `lvmin`
    on any of them in Check.img.
  - Provenance, measured against the v84-minus-v83 diff: **3758 and 3759 are v84-NEW**, and all 21
    `240080xxx` maps **are in the v84 archive**. Whether GMS switched the content on in v84 or later
    is NOT answerable from our files - do not assert it either way.
  - **ORCHESTRATOR ERROR, recorded so it is not repeated:** I claimed this was Evan progression gated
    behind the Evan dragon mount 20011004. That was a chain of inference from `needSkillForFly`, not
    a reading of the data, and it is wrong - 20011004 and 1026 are different mechanics. Read
    `Act.img` before claiming who content belongs to.
  - Likely real parity gap: NPCs 2081000 / 2085000 / 2085001 / 2085002 have no `scripts/npc/*.js`, so
    the Soaring chain cannot be walked. **Dragon Rider PQ itself is DEFERRED by owner decision**
    ("i dont want 'Dragon Rider Party Quest', we can do that in the future") - stages, doorway
    shuttling, party logic and resurrection sites are out of scope; the pre-quest chain is not.
- ~~8 more non-Evan maps unreachable; the merge tool was right to refuse~~ **THE OBJECTION IS RETIRED, AND THIS WAS NEVER "NON-EVAN".** Ticket 53 took v84's whole portal array on 28 maps and explicitly retired the `106010102` refusal, so the index argument no longer holds. Checked directly against the pristine carve: `106010101/portal/5` is v84's `in00 pt=7 tm=999999999 script=evanGolemDoor` (ours is `pt=2 tm=106010102`), and `220011000/portal/4` is `in00 pt=7 tm=999999999 script=enterBlackBC` (ours is `pt=2 tm=220011001`). **Two of the maps behind that second door carry the ONLY spawns of mobs 9300389 and 9300390**, which are `Check.img/22583/1/mob/0` and `Check.img/22584/1/mob/0` - Evan-only quests at `lvmin` 68. **The Evan chain hard-stops at 22583 today because of this row.** Two JS files close it. Work rows **R01** and **R02**.
- ~~NPC 1011101 "General Mau" is a dead vendor~~ **AS v84-CORRECT AS THE DATA ALLOWS.** `scripts/npc/1011101.js` now exists (`9bbf0dbcd`). There is still no `shops` row, and there should not be one invented: pristine v84 `Npc.wz/1011101.img/info` is an **empty node** - no shop flag, no script name - and HeavenMS's own `leftover.txt` lists him as never coded. Stock is genuinely UNKNOWN. Work row **R35**.
- NPC 9000054 "Ranch Owner" - **role answered, and it is OUT OF SCOPE.** Pristine v84 `Npc.wz/9000054.img/info/script/0/script` = `BF_master`: he is the host of the Sheep Ranch event mini-game (`109090000` Sheep Ranch Lobby, `910040000` Ranch Entrance, `910040002` Fenced Street). But `add-list/Npc.txt:80` lists only `info/reg/event`, so the NPC is **v83 legacy** and v84 added nothing but an animation reg. An unimplemented v83 event, not a parity gap. Work row **R36**.

## 10. Items and buffs that exist and do nothing  *(found 2026-08-17, parity-surface sweep)*

> **ALL OF THIS IS OUT OF SCOPE under "we are currently only doing v84 parity".** Re-verified by
> item id against `add-list/` on 2026-08-18: every id in this section is v83 legacy. Real defects,
> not v84 gaps. They are listed here and in `V84-WORK-ROWS.tsv` rows R24-R33 with their known fixes,
> and they are **not being built**. The only genuinely v84-new items in the section are the two NX
> coupons 5200009/5200010 and the slot coupon 5550001, which are rows R16 and R17.

**PROVENANCE CORRECTION - none of these are v84-new.** The sweep that found them called several
"v84-new" and I relayed that; it was wrong. Checked against `docs/wz-baseline/add-list/Item.txt`,
which is the computed v84-minus-v83 diff: it contains **zero** paths for `BFSkill`, `randstat`,
`onlyPickup`, `recoveryHP` or `recoveryMP`. All of the below is **v83 legacy**, so it is real-bug
territory rather than v84 parity - lower priority under the owner's "lets focus on v84 for now".

Also corrected: the follow-up agent reported `spec/consumeOnPickup` missing from the shipped tree on
items 2022539-2022549 and called it a merge gap. **It is present** - verified by exact node extraction,
all eight carry `consumeOnPickup=1` inside their own `spec`. No gap, nothing to merge.

The "written but never read" pattern the Evan audit found is **not Evan-specific**. Confirmed wider:

- **5 chaos scrolls are no-ops that still burn the upgrade slot**: `2049103/104/112/113/114` carry
  `info/randstat=1`, but `ItemInformationProvider.java:1110-1184` special-cases only `2049100-102` by
  hardcoded id; the rest fall through to `improveEquipStats` against an empty stat map. Slot and item
  both consumed, nothing applied. **Re-verified against the WZ 2026-08-18**: exactly 8 items in
  `wz/Item.wz/Consume/0204.img.xml` carry `randstat=1` - 2049100-104 and 2049112-114 - and the id list
  at `ItemInformationProvider.java:1170-1172` covers 3 of them. The fix is to match the node, not the
  ids: `ret.put("randstat", ...)` beside `fs` in `getEquipStats:580`, then replace those three `case`
  labels with `if (stats.get("randstat") > 0)` inside the `default:` arm. Written this session and
  **reverted unbuilt** when scope was cut - it is a behaviour change and the suite could not be run.
- **7 percentage buff potions grant only their FLAT half** - `2022359-2022365`. **Correction: the old "grant zero stats" was wrong.** Each carries BOTH nodes (e.g. 2022359 has `pad=10` AND `padRate=10`) and `StatEffect.java:375-382` reads the flat one. The `*Rate` half is dropped. Not a mechanical read-add either: no `*Rate` field exists on `StatEffect` and no percent-buff plumbing exists, so it would need new `BuffStat` percent handling in `Character.reapplyLocalStats`. v83 legacy.
- **6 brand-new v84 `BFSkill` items are wholly unwired**: `2022539/542/543/547/548/549`, zero code
  references anywhere.
- **Shadow Web's damage-over-time was commented out ON PURPOSE upstream - do not un-comment it.** `Monster.java:1328-1336`. `git log -L 1326,1336` shows `5f1abf3fb` ("HikariCP config + MaxHP/MP & EXP overhaul") wrapped it in `/* */` in the same commit as the HP rework that would have made a 2%-of-max-HP DoT abusive, and a later commit `94425ba61` migrated the dead code to the new `overtimeAction` API *without* un-commenting it. The value is hardcoded `getMaxHp()/50.0`, **not in Skill.wz** - there is no data node to consult. Only two skills route there, Hermit 4111003 and Night Walker 14111001; **the old note's "Blaze Wizard" is wrong**. Needs a balance decision, not a restore.
- ~~**Mage Seal never blocks mob skills**~~ **FIXED** `e364ed14a`, `Monster.java:1529` checks both statuses.
- ~~**Invincible Barrier and Cleric's Invincible do nothing**~~ **FIXED** `9deb94cef`, `TakeDamageHandler.java:212` and `:274`.
- ~~**Monster-card immunity piercing does nothing**~~ **FIXED** `66ec1e559`, `AbstractDealDamageHandler.java:264/268`.
- **Echo of Hero's +4% watk** is written at `StatEffect.java:581-585` and delivered map-wide by `applyEchoOfHero:972`, but `Character.reapplyLocalStats()` (`:7699`) has **zero** `ECHO_OF_HERO` references. Smallest open one in this section and genuinely 4 lines, inserted after the `BuffStat.WATK` block at `Character.java:7784-7787`. v83 bug, so out of scope - but Evan does have `ECHO_OF_HERO` (20011005), so it would affect the live playthrough if the owner wants it.
- **All chairs heal identically** - `info/recoveryHP`/`recoveryMP` on 56/35 items never read; regen comes solely from the player's own max HP/MP (`Character.getChairTaskIntervalRate` at `:2442` takes no item id). **Correction: the old note's "several v84-new" is wrong** - `Item.wz/Install/0301` has zero `add-list` rows. v83 legacy.
- ~~`spec/onlyPickup` unenforced on 83 items~~ **EFFECTIVELY ALREADY CORRECT.** 63 of the 83 also carry `consumeOnPickup=1`, and `Character.applyConsumeOnPickup` (`:1939`, called at `:2093`) already destroys them on pickup so they never reach inventory - enforcement would be a no-op. The other 20 all carry `tradeBlock=1`. The 5 v84-new ones are `2022652-2022656` and they **work**: their `reward` tables are wired at `ItemRewardHandler.java:66`. Separately, the cash coupons DO need work - `info/maplepoint` on 5200009/5200010 and `info/slotIndex`+`addDay` on 5550001 are v84-new whole items with no handler at all (rows **R16**, **R17**). And the `incMaxHP/incMaxMP/incReqLevel/incCraft/incLEV` claim is mostly not a bug: `MakerProcessor.java:415-421` already translates MaxHP/MaxMP and `:413` skips ReqLevel deliberately; only `incCraft` on tablet scrolls 2047204/2047304 truly falls through, and no craft stat exists on `Equip`.

Swept and **clean** - do not re-check: DB content tables (`area_info`, `plife`, `questactions`,
`questrequirements`, `specialcashitems`, `monsterbook`, maker siblings), Etc.wz image coverage, the
pet system end to end, mob `mobType`->`category` rename (dead field before and after), and skill
`isBuff` classification across all **627** skills - Blessing of the Onyx is the ONLY instance of that
failure shape.

---

## Server facts that are BY DESIGN - do not "fix" these

- **Every character in this database is `gm` 2 or 6. That is deliberate**, owner-confirmed. Both
  PlayerNPC auto-deploy paths gate on `!chr.isGM()` (`Character.java:6428` at max class level, and
  `NPCConversationManager.canSpawnPlayerNpc:343`), so the Hall of Fame will NEVER populate itself
  here. That is the guard working correctly, not a bug - do NOT weaken either check. Seeding at the
  admin layer (changeSet 163, or the `!playernpc` / `!spawnallpnpcs` commands, which carry no GM
  check by design) is the correct and permanent mechanism on this server.

## Corrections to keep - do not re-derive these wrongly

- "Playable 1 to 67" was **wrong**: the real stop is quest 22503 at **level 11**.
- "v84 adds 10,459 cash shop entries" was **wrong** - that counted paths; the real delta is **116 SNs**.
- "6 new reactors" was **wrong** - 1 genuinely new, placed nowhere.
- "Drop rates cannot be sourced" was **premature** - dreamms.gg renders numeric rates server-side in plain HTML.
- Char 48 `ghfgh` is a GM character with every skill maxed - never a valid benchmark for anything.
- Mob skill 137 is unimplemented but **no mob references it**; all 34 in-use ids are implemented. Not a gap.
- `medalmaps`, `ScriptInfo.img`, `NPT_exception.img` - not parity surfaces. Cash shop catalogue is data-driven and complete.
