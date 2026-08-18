# What v84 added, and whether we carry it

**This is the front page for "is it in v84, and did we do it".** It is computed, not written:
regenerate with

```
python tools/playthrough/v84coverage.py
```

which reads `docs/wz-baseline/add-list/*.txt` — the project's already-computed v84-minus-v83 diff —
and checks every copy root against our own `wz/` tree. Row detail lands in
`docs/work-plan/V84-COVERAGE.tsv`.

The point of this file is that **nobody should re-derive "what is in v84" again.** It has been done
three times. `add-list/` was always the answer.

---

## The matrix

| archive | v84 added | in our `wz/` | **GAP** | benign | client-only |
|---|---:|---:|---:|---:|---:|
| Base | 0 | 0 | 0 | 0 | 0 |
| Character | 438 | 251 | **0** | 0 | 187 |
| Effect | 23 | 1 | **0** | 0 | 22 |
| Etc | 10634 | 216 | **168** | 10181 | 69 |
| Item | 391 | 389 | **2** | 0 | 0 |
| Map | 601 | 410 | **40** | 0 | 151 |
| Mob | 1216 | 37 | **0** | 1170 | 9 |
| Morph | 25 | 25 | 0 | 0 | 0 |
| Npc | 98 | 42 | **0** | 9 | 47 |
| Quest | 924 | 658 | **128** | 0 | 138 |
| Reactor | 6 | 6 | 0 | 0 | 0 |
| Skill | 55 | 39 | **16** | 0 | 0 |
| Sound | 62 | 24 | **0** | 0 | 38 |
| String | 1579 | 1385 | **41** | 115 | 38 |
| TamingMob | 0 | 0 | 0 | 0 | 0 |
| UI | 61 | 2 | **0** | 0 | 59 |
| **TOTAL** | **16113** | **3485** | **395** | **11475** | **758** |

**The headline: of 16,113 nodes v84 added, 395 are genuine server-side gaps.** Everything else is
either already in our tree, or is a node the server never opens.

### Two results worth stating on their own

**1. Not one v84 image is missing.** Across all sixteen archives, zero copy roots failed with
`IMAGE_ABSENT`. Every v84-new map, every v84-new equip image, every v84-new item image, every
v84-new NPC and mob image is in `wz/`. The merge is complete at the image level; what remains is
leaf-level.

**2. The two biggest "missing" numbers are non-defects, and they are the ones that keep getting
re-filed.** They are carved out in the tool with their reasons attached, so they stop coming back:

- **11,346 of them are `Mob.wz/*/info/category` and `Etc.wz/Commodity.img/*/{Bonus,Class,Gender,
  Priority,Limit,PbPoint,PbGift,PbCash}`.** `category` is the `mobType` rename — a dead field before
  and after, already swept. The Commodity leaves are ones `CashShop.java:243-248` never reads; it
  reads `SN`, `ItemId`, `Price`, `Period`, `Count` and `OnSale` and nothing else.
- **115 are `String.wz/Npc.img/<id>/{d1,n0,n1,func}`** — NPC idle chatter and role labels, drawn by
  the client from its own archive. `LifeFactory.java:295` reads `name` and `:299` reads `d0`; those
  two are counted as gaps, everything else in that image is not.

---

## The 395, decomposed

Every gap below is a leaf the server demonstrably reads. Each is one work row.

| # | gap | count | server reader | note |
|---|---|---:|---|---|
| 1 | `Quest.wz/Check.img/<id>/0/lvmax` | 108 | `QuestRequirementType.java:74` | quests 28162-28266, 28282, 28283, 28325 — the job-instructor training line. v84 added a level cap; we have none, so they stay startable past it. |
| 2 | `Etc.wz/Commodity.img/<i>/Price` | 82 | `CashShop.java:245` | see the caveat below |
| 3 | `Etc.wz/Commodity.img/<i>/Period` | 78 | `CashShop.java:246` | every affected row is `OnSale=0`, so this is latent, not live |
| 4 | `Map.wz/.../{reactor,life,portal,info}` | 40 | `MapFactory` | enumerated below |
| 5 | `String.wz/Npc.img/<id>/d0` | 40 | `LifeFactory.java:299` | the default line a scripted NPC falls back to (`NPCConversationManager.java:95`); missing means `(...)` |
| 6 | `Skill.wz/MobSkill.img/<id>/level/<n>` | 15 | `MobSkillFactory` | v84 added higher levels to 9 existing mob skills |
| 7 | `Etc.wz/ItemMake.img/{0,2}/<id>` | 6 | `ItemInformationProvider.java:2258` | the six v84 Maker recipes — see below, fully settled |
| 8 | `Quest.wz/Check.img/<id>/0/{start,end,interval}` | 14 | `QuestRequirementType.java:84`, `EndDateRequirement` | quests 2208-2211 (Bartol), 3845, 10109, 9260 |
| 9 | `Skill.wz/MobSkill.img/137` | 1 | `MobSkillFactory` | already recorded: no mob references skill 137 |
| 10 | `Skill.wz/Dragon` (whole directory) | 1 | none | Mir's animation set — client art, the client has its own copy |
| 11 | `Quest.wz/QuestInfo.img/<id>/{type,demandSummary,rewardSummary}` | 5 | quest window text | cosmetic |
| 12 | `String.wz/Npc.img/<id>/name` | 1 | `LifeFactory.java:295` | one NPC; a missing name renders `MISSINGNO` |
| 13 | `Item.wz/Consume/0202.img/{02022503,02022514}/reward/43` | 2 | `ItemRewardHandler.java:66` | v84 added a 44th entry to two reward boxes; ours stop at 43 |

Two classes that *look* like gaps and are not, carved out in the tool with their reasons: all nine
`Npc.wz/<id>/info/{reg/*,script,default}` misses (the server reads `info/trunkPut` and
`info/trunkGet` from that archive and nothing else — `Storage.java:318,336`), and all five
`Mob.wz/<id>/info/default` misses (animation default; `MonsterStats` reads no such leaf).

### Rows 2 and 3 — read the caveat before acting

`Etc.wz/Commodity.img`'s children are **array indices, not SNs**. `add-list` reports these as
field-level additions under an existing index, which means the diff tool matched v83 and v84 at the
same index — so the indices line up and the missing `Price`/`Period` are real. Two independent
measurements agree on the count: this tool finds 78 indices with no `Period`, and a direct query of
our tree finds 78 rows with no `Period`.

**But every one of the 78 is `OnSale=0`.** No player can buy them, so nothing is mispriced today.
This is latent data debt, not a live defect. Do not let it outrank a row a player can actually hit.

Index alignment is exactly the trap `SOURCES.md` warns about ("storage order is not name order"), so
if this row is ever worked, key on `SN` and not on the index.

### Row 4, enumerated

| map | missing |
|---|---|
| `109090300` | `reactor/14` … `reactor/31` (18) |
| `230040000`, `230040100`, `230040200`, `230040400`, `230020000`, `230010400` | 12 reactors across the Aquarium maps |
| `220011001` | its whole `info` block: `fly`, `swim`, `noMapCmd`, `onUserEnter`, `onFirstUserEnter`, plus `2/info/tS` and `tSMag` |
| `220011000` | `portal/4/script`, `portal/4/horizontalImpact` |
| `102000003` | `life/1` |
| `106010101` | `portal/5/script` — this is the known `evanGolemDoor` refusal, deliberate |
| `120000105` | `ladderRope/1` |

`life`, `portal` and `reactor` are arrays. A missing index N means **our array is shorter than
v84's**, which is a real difference. It does **not** follow that a present index N holds the same
content — this tool cannot see that, and ticket 53 is the precedent for how that class actually gets
resolved.

### Row 7 is fully settled and purely mechanical

`add-list/Etc.txt:10475-10480` names exactly six paths under `ItemMake`, and they match changeSet
`158-maker-v84-data.sql:73-78` one for one:

```
Etc.wz/ItemMake.img/0/01142156      Etc.wz/ItemMake.img/2/01942002
Etc.wz/ItemMake.img/0/01142157      Etc.wz/ItemMake.img/2/01952002
                                    Etc.wz/ItemMake.img/2/01962002
                                    Etc.wz/ItemMake.img/2/01972002
```

The server reads only `catalyst` from this image (`ItemInformationProvider.java:2258`, `:2262`), so
merging them changes no behaviour today. It matters because a future `SkillMakerFetcher` run would
otherwise silently drop all six.

---

## What this matrix deliberately does not measure

Stated rather than hidden, in the house style:

- **Presence is not correctness.** A node can exist in our tree carrying a v83 value. This tool
  answers "is it there", never "is it right".
- **Arrays are index-keyed**, so array divergence is undercounted — see row 4.
- **`add-list` is v84-minus-v83-stock.** It says nothing about content this project added itself;
  `docs/wz-baseline/protect-list/` is that side of the ledger.
- **A merged node the server never reads is still not support.** This matrix is the *data* half of
  parity. The *behaviour* half — "v84 ships the item and nothing in our server can produce it", and
  "the skill's node exists but its effect is parsed and ignored" — is not computable from `add-list`
  and lives in `V84-OPEN-ITEMS.md` and `V84-ITEM-SOURCE-SWEEP.md`.

## Companion sweeps — reuse these, do not redo them

| sweep | question it answers | result |
|---|---|---|
| `V84-QUEST-SWEEP.{md,tsv}` | can each of the 198 v84-added quests be completed | 0 mechanical fixes; 7 scripts written; 0 owner decisions |
| `V84-QUEST-DROPPER-SWEEP.{md,tsv}` | does the dropper live where the quest sends you | 12 flagged, 12 cleared on the quest text, 0 rows changed |
| `V84-ITEM-SOURCE-SWEEP.{md,tsv}` | does every v84-new item have a source | see that file |
