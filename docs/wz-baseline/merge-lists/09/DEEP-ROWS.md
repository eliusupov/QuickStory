# Ticket 09 — the 132 `Quest.wz` rows that write into content the live client already has

`add-list/Quest.txt` has **924 copy roots**. 792 of them are a whole new quest node
(`<Img>.img/<id>` where `<id>` is absent from the live client) — 252 mine, 540 ticket 13's Evan
chain. The other **132 write into something the live client already ships**, and every one of them
is the hazard class ticket 08 measured on `Map.wz` portals
([`../08/ROUTE-ROWS.md`](../08/ROUTE-ROWS.md)): the row does not collide, so `conflicts.txt` is
silent by construction, and the additive gate writes it happily.

**All 132 are refused.** Each was dumped from both trees and compared before the decision. None of
them is needed by any of this ticket's 63 quests — every one lands on an id this ticket does not
own — so refusing them costs this ticket nothing and the measurement is what is being delivered.

| shape | rows | verdict |
|---|---:|---|
| `Check.img/<id>/0/lvmax` on 108 live quests | 108 | **refuse — breaks working v83 content** |
| `Check.img/{2208,2209,2210,2211}/0/{start,end,interval}` | 12 | **refuse — disables four working v83 quests** |
| `Check.img/3845/0/end` | 1 | **refuse — disables a working v83 quest** |
| `Check.img/10109/0/interval` | 1 | refuse — inert (the quest is already date-dead), no benefit |
| `Check.img/9260/0/dayByDay` | 1 | refuse — inert (same), no benefit |
| `QuestInfo.img/1008/demandSummary`, `QuestInfo.img/9260/{demandSummary,rewardSummary}`, `QuestInfo.img/{20012,20311}/type` | 5 | refuse — client-side display text on quests this ticket does not own |
| `Exclusive.img/{0,1,2}` | 3 | **refuse — the two trees group this image differently; merging duplicates seven ids** |
| `PQuestSearch.img` | 1 | refuse — a whole new image indexing a party-search window the v83 client does not have |
| **total** | **132** | |

924 = 252 (`Quest.paths.txt`) + 540 (Evan, ticket 13) + 132 (this file).

---

## The 108 `lvmax` rows — the one that would have been a real regression

v84 adds `lvmax = 40` — the value is **uniform across all 108**, checked, not sampled — to
`Check.img/<id>/0` for 108 quests the live client already has: `28162`–`28266`, `28282`, `28283`,
`28325`. That is the beginner/training block ("Meeting the Training Instructor", "Even More
Challenging Training-1", "Secret of Astaroth", "How to Avoid the Stink", "Dirty Treasure Map", …).

Dumped for every one of them; the live node is the v84 node **minus `lvmax`**, e.g.

```
v84  Check.img/28266/0 : npc 1061011, lvmin 25, lvmax 40, quest{...}, job{...}
live Check.img/28266/0 : npc 1061011, lvmin 25,           quest{...}, job{...}
```

(`quest` and `job` are non-empty in both and identical; `lvmax` is the only difference.)

`QuestRequirementType.getByWZName("lvmax")` → `MAX_LEVEL`, and `Quest.canStart` fails the whole
quest if any start requirement fails. So merging these 108 rows makes **108 currently-startable
quests permanently unavailable to any character above Lv.40**. That is a faithful port of what
Nexon did in the Evan patch, and it is also exactly what acceptance criterion 5 — *"existing quests
still work — no regression from the `Quest.wz` merge"* — forbids.

Recorded rather than silently dropped: if an owner later decides Cosmic should match GMS v84 here,
the 108 rows are `grep '/0/lvmax$' docs/wz-baseline/add-list/Quest.txt` and nothing else needs
finding.

## The 14 date rows — four working quests turned off, one already off

| row(s) | live | v84 | effect of merging |
|---|---|---|---|
| `Check.img/{2208,2209,2210,2211}/0/{start,end,interval}` | no date gate at all — startable | `start=200801010000`, `end=200801020000`, `interval=1440` | **a 24-hour window in January 2008.** `EndDateRequirement.check` compares `end` against the wall clock, so all four quests (NPC `1092011`) become permanently un-startable |
| `Check.img/3845/0/end` | no date gate — startable | `2010010100` | same: the quest (NPC `2092001`, Lv.60–80) becomes un-startable |
| `Check.img/10109/0/interval` | already carries `start=2008111900 end=2008121900`, i.e. already date-dead | `interval=1440` | inert — it would only make a dead quest repeatable |
| `Check.img/9260/0/dayByDay` | already carries `end=2010010100` | `dayByDay=1` | inert, same reason |

The last two are harmless. They are refused anyway because "inert" is an argument this project has
already been burned by once (ticket 08's `enterDollcave.js`), and neither buys anything.

## `Exclusive.img` — the positional-array finding, in a second file

This is `Map.wz`'s portal problem with a different surface. The two trees do not disagree about an
index; they disagree about the **grouping scheme**, and the merge cannot see that.

```
live  Exclusive.img/medal : 29000 29001 29002 29003 29300 29301 29302 29303 29304
                            29400 29500 29501 29502 29503          (one named group, 14 ids)

v84   Exclusive.img/0     : 10415 10417 10418 10419 10420
      Exclusive.img/1     : 29002 29500
      Exclusive.img/2     : 29300 29301 29302 29303 29304          (three numeric groups)
```

v84 **replaced** `medal` with numeric groups and re-partitioned the ids. The add-list offers the
three numeric groups as additions, because the target has no `0`, `1` or `2` — so the additive gate
passes them and writes an image holding `medal` **and** `0`/`1`/`2`, which neither vendor ever
shipped. Seven ids (`29002`, `29500`, `29300`–`29304`) would then appear in two mutually-exclusive
groups at once. Exactly 03c's `MonsterBook/<mob>/reward` splice, one file over.

Nothing in `src/` reads `Exclusive.img` (grepped — zero hits), so this is a client-side rule and the
cost of refusing is that v84's new `10415`–`10420` exclusivity group is not enforced by the client.
That is strictly better than an image with two contradictory partitions in it.

*(Symmetrically: the 5 `QuestInfo` display rows are as free to take as to refuse — `Quest`'s
constructor never reads `type`, and `demandSummary` / `rewardSummary` have no server reader at all.
They are refused for consistency with the other 127, not because taking them would have cost
anything. That is the one row of this table where "zero cost" cuts both ways.)*

## `PQuestSearch.img`

A whole new image (`party/0`–`8`, `expedition/0`–`4`) with no counterpart in the live client. It
indexes the party-quest search window, a v84 client feature the v83 client does not have, and the
server never opens it. Left unclaimed rather than imported for nobody.

## The general rule this adds

Ticket 08's rule was about `<array>/<n>` and `<array>/<n>/<field>`. `Quest.wz` shows the same class
without any numeric index at all:

> **Any add-list row whose parent chain reaches a node the live client already has is an *edit*,
> not an addition, and the gate cannot tell you which.** Dump both sides of that parent before
> merging. In `Quest.wz` the tell is depth: a row deeper than `Quest.wz/<Img>.img/<id>` is by
> definition writing inside an existing quest, and 128 of the 132 refusals here are exactly that.
> The other four are a container whose *child names* changed meaning between the two trees, which
> depth alone will not catch — only dumping both sides does.
