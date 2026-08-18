# 70 - RESEARCH: four rows that close as permanent unknowns, and the rows nobody may write

**Class:** research - the deliverable is a recorded refusal, not code
**Work rows:** R34, R35, R47, R52 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately. Nothing here waits on anybody.

Four rows where the honest answer is "v84 does not say, and nothing else may stand in for it". Each
one is a place where a plausible row, script or drop table could be written today and would look
right. Each has already been chased to the bottom, and the value of this ticket is that the chase is
not repeated and the tempting row is not written.

The governing standard is `SOURCES.md`: **is it in the v84 data?** Where the answer is no, we do not
build it, however broken it looks.

## R34 - mob 4090000 Iron Hook has no drops, and no artifact says what it should

* 0 rows in `drop_data`. Zero hits in `wz/String.wz/MonsterBook.img.xml`.
* Placed on **104010001** and **106000110**.

**Closed for good.** The pristine v84 `Etc.wz` contains **zero** occurrences of "Reward" in either
ASCII or UTF-16LE - v84's own `Etc.wz` has no `Server/Reward.img`, and Nexon never shipped drop
tables in any client version. `add-list/Mob.txt:131` carries only `info/category` for this mob, so it
is v83 content with one leaf touched. **There is no source to recover.**

Question, for the record: *what did Iron Hook drop in v84?* Evidence that would settle it: none
exists, in any carve, of any version.

## R35 - NPC 1011101 General Mau is a clickable vendor with no stock

* `scripts/npc/1011101.js` now exists (commit `9bbf0dbcd`) - a one-line greeting reusing his own
  `String.wz` `n0`.
* `SELECT * FROM shops WHERE npcid = 1011101` is **empty**.

Pristine v84 `Npc.wz/1011101.img/info` is an **empty node** - no shop flag, no script name. HeavenMS's
own `leftover.txt` lists "General Mau (v84)" as never coded, and a GMS v95 SQL dump carries no
`shops` row either. **The NPC is now as v84-correct as the data allows.**

Question: *what does General Mau sell?* Evidence that would settle it: a period GMS shop dump naming
npcid 1011101. None found across three sources. If one ever appears, the precedent shape is his
neighbour **Luna 1011100**, shopid 1011100, 25 items.

## R47 - `summonIceWall` and `stopIceWall2` cannot be written from data

* `Map9/914100022.img/info/onUserEnter` = `"summonIceWall"`.
* `Map9/914100022.img/portal/3..12` are ten `pt=9` triggers named `scr00`..`scr09`, at
  **x = 58 / 156**, **y = 99 / 2 / -95 / -193 / -290**.

The wall is mob **9300391**: placed in **no map** - a `WzPeek scan` over all 4,848 pristine v84 map
images returns 0 hits - and produced by **no revive chain** across all 1,605 mob images. Count,
positions and the stop mechanic exist in no WZ file, and `Map.wz` carries no ice-wall art on either
914100020 or 914100022.

`stopIceWall2` additionally has **no quest record to write**: 22588's only record 22605 is written by
reactor 1409000, and `outSDI.js` already owns 22600. It is a no-op until `summonIceWall` exists.

Question: *how many ice-wall mobs spawn, where, and what stops them?* Evidence that would settle it:
a v84 artifact naming a spawn for 9300391. Proven absent across every image in the archive.

**Writing either means inventing a mob count and coordinates.** The refusal stands and is already
visible in `STATUS.md`.

## R52 - the drop row for 4032530 that must NOT be written

**4032530 "Leviathan's Tear"** looks like an obvious analogue row for mob **9500382 Leviathan**, which
ticket 06 places. It is not.

Its `String.wz` description reads, in full, **"G-Star Clear"**. It is a G-Star 2009 convention
giveaway, sibling to **2430034 "G-star Reset Item"**.

**The name is a trap** of exactly the kind `SOURCES.md` warns about - *"NAME_LINK is evidence, never a
verdict"* - and it is the same shape as the quest 22529 Stump Sap mistake, where a row was authored
from a name link and then cited as its own evidence. This row is recorded so the next agent that
spots the name link does not write it.

## Acceptance criteria

- [ ] All four refusals are recorded in a place a future agent will actually look - the "Refusals
      worth keeping visible" list is the orchestrator's file, so the implementing agent adds nothing
      there and instead leaves the reasoning in this ticket plus, where the code is the natural
      place, a `ponytail:`-style comment at the site.
- [ ] `drop_data` gains **zero** rows for itemid 4032530 and **zero** rows for dropperid 4090000.
      Verifiable by `SELECT COUNT(*)` on both.
- [ ] `shops` gains **zero** rows for npcid 1011101.
- [ ] `scripts/portal/stopIceWall2.js` and `scripts/map/onUserEnter/summonIceWall.js` do **not**
      exist. `MapAndPortalScriptsRealLoad` may assert their absence, the same way it pins other
      deliberate gaps.
- [ ] The `9300391` negative is re-provable in one command, and this ticket records that command, so
      the scan is not redone by hand.
- [ ] No new evidence is cited from Tier 3 for any of the four. Tier 3 is good for rates and dropper
      lists where an item exists in both versions; it is never sufficient for "did v84 ship this".

## Do not

- Do not write a drop row for 4090000 or for 4032530.
- Do not stock npcid 1011101 by copying Luna's 25 items. Same map and same family is not evidence of
  the same stock.
- Do not write `summonIceWall` or `stopIceWall2`. Ticket 55 opens the island; that is deliberately as
  far as the data goes.
- Do not reopen any of these on the strength of a name, a wiki page, or a private server's table.
