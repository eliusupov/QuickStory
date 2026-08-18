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

**Closed for good.** v84's own `Etc.wz` has no `Reward.img` and no `Server/Reward.img`, and Nexon
never shipped drop tables in any client version. `add-list/Mob.txt:131` is exactly
`Mob.wz/4090000.img/info/category` - one leaf touched on v83 content. **There is no source to
recover.**

The proof, re-runnable in one command (`WzPeek.exe` is
`docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`; the carve is at
`D:\games\MapleStory\Server\porting-resources\wz-data\v84\`, binary `.wz`):

    WzPeek.exe dump <v84>\Etc.wz Reward.img 0          -> NOT-FOUND	Reward.img
    WzPeek.exe dump <v84>\Etc.wz Server/Reward.img 0   -> NOT-FOUND	Server/Reward.img

**Do not re-prove this with a byte grep, and disregard any prior claim that did.** An earlier
revision of this row cited "zero occurrences of `Reward` in either ASCII or UTF-16LE" over the raw
`.wz`. That test is void: the same grep returns **zero for `Commodity`** as well, yet
`WzPeek dump <v84>\Etc.wz Commodity.img 0` returns `FOUND` with thousands of children. WZ string
encoding makes a raw byte scan unable to distinguish present from absent, so it can neither confirm
nor refute anything here. Only a `WzPeek` lookup answers.

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

* `Map9/914100022.img/info/onUserEnter` = `"summonIceWall"` (`914100022.img.xml:18`).
* `Map9/914100022.img/portal/3..12` are ten `pt=9`, `delay=200` triggers named `scr00`..`scr09`.
  The ten measured `(x, y)` pairs, in portal order:

  | portal | pn | x | y |
  |---|---|---|---|
  | 3 | scr00 | 58 | -288 |
  | 4 | scr01 | 156 | -288 |
  | 5 | scr02 | 57 | -192 |
  | 6 | scr03 | 156 | -191 |
  | 7 | scr04 | 57 | -94 |
  | 8 | scr05 | 156 | -94 |
  | 9 | scr06 | 56 | 3 |
  | 10 | scr07 | 156 | 3 |
  | 11 | scr08 | 56 | 99 |
  | 12 | scr09 | 156 | 98 |

  Two columns (x 56-58, and x 156) by five rows. An earlier revision gave
  "x = 58 / 156, y = 99 / 2 / -95 / -193 / -290"; four of those five y values were off by 1-2 and the
  left column is not a single x. Use the table.

The wall is mob **9300391**: placed in **no map** and produced by **no revive chain**. Count,
positions and the stop mechanic exist in no WZ file, and `Map.wz` carries no ice-wall art on either
914100020 or 914100022.

The re-provable negative, one command from the repo root - this is the command the acceptance
criterion below asks for:

    grep -rl 9300391 wz/Map.wz/ wz/Mob.wz/
    -> wz/Mob.wz/9300391.img.xml        (its own image, and nothing else)

Zero hits under `wz/Map.wz/`, and no other mob image names it as a revive. An earlier revision quoted
"4,848 pristine v84 map images" and "1,605 mob images"; **those figures are dropped** - the pristine
carve is binary `.wz`, not per-image files, and no enumeration producing either number is recorded
anywhere. The grep above is the evidence; the image totals never were.

`stopIceWall2` additionally has **no quest record to write**: 22588's only record is 22605
(`Check.img/22588/1`, infoNumber 22605, infoex/0/value "1"), already written by
`scripts/reactor/1409000.js:25`; and 22600 belongs to quest **22589**, already written by
`scripts/portal/outSDI.js:41`. It is a no-op until `summonIceWall` exists.

### The precedent that DOES exist - name it before refusing

`scripts/portal/stopIceWall.js` **is shipped and tested.** It implements the identical ten-`pt=9`
trigger grid on the sibling map **914100020** (`portal/2..11`, same `delay=200`, same two-column
geometry), and is pinned by `MapAndPortalScriptsRealLoad.java:85`
(`PORTAL_HOOKS.put("stopIceWall", 914100020)`) and `:196`. Its body is four lines: guard on
`getQuestStatus(22580) == 1`, write record 22599 = "2", return true. No mob, no warp, no cutscene -
its own header comment says why.

The refusal still stands, but on **narrower** ground than "there is nothing to copy from", and this
ticket must not claim otherwise:

* The *script shape* is fully precedented - `stopIceWall.js` is the template.
* What `stopIceWall2` lacks is a **record**: every record in 914100022's room is already owned, so a
  copy of `stopIceWall.js` would have nothing to write and would be a literal no-op.
* What `summonIceWall` lacks is the **mob count and positions** for 9300391, which no artifact
  supplies. That is the real, unrecoverable gap.

Anyone reopening this must engage with `stopIceWall.js` explicitly rather than rediscovering it.

Question: *how many ice-wall mobs spawn, where, and what stops them?* Evidence that would settle it:
a v84 artifact naming a spawn for 9300391. Proven absent across every image in the archive.

**Writing either means inventing a mob count and coordinates.** The refusal stands and is already
visible in `STATUS.md`.

## R52 - the drop row for 4032530 that must NOT be written

**4032530 "Leviathan's Tear"** (`String.wz/Etc.img.xml:106-108`) looks like an obvious analogue row
for mob **9500382 Leviathan**, whose area ticket 06 opens. It is not. Ticket 06 does not even *place*
that mob: `06-crimson-sky.md:62` records that `9500374`-`9500382` "appear in no `life` node anywhere
in scope" - they are `summonType=1` clones. The name link is the only thread here, and it snaps.

Its `String.wz` description reads, in full, **"G-Star Clear"**. It is a G-Star 2009 convention
giveaway, sibling to **2430034 "G-star Reset Item"**.

**The name is a trap** of exactly the kind the tracker warns about - *"NAME_LINK is evidence, never a
verdict"*, which is `docs/work-plan/V84-QUEST-DROPPER-SWEEP.md:161` (**not** `SOURCES.md`, where an
earlier revision of this row wrongly placed it) - and it is the same shape as the quest 22529 Stump
Sap mistake, where a row was authored from a name link and then cited as its own evidence. This row is recorded so the next agent that
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
- [ ] The `9300391` negative is re-provable in one command - recorded under R47 above as
      `grep -rl 9300391 wz/Map.wz/ wz/Mob.wz/`, expected to return only `wz/Mob.wz/9300391.img.xml` -
      so the scan is not redone by hand.
- [ ] No claim in this ticket rests on a raw byte grep of a `.wz` file. Presence or absence inside a
      WZ archive is settled with `WzPeek dump` and nothing else.
- [ ] No new evidence is cited from Tier 3 for any of the four. Tier 3 is good for rates and dropper
      lists where an item exists in both versions; it is never sufficient for "did v84 ship this".

## Do not

- Do not write a drop row for 4090000 or for 4032530.
- Do not stock npcid 1011101 by copying Luna's 25 items. Same map and same family is not evidence of
  the same stock.
- Do not write `summonIceWall` or `stopIceWall2`. Ticket 55 opens the island; that is deliberately as
  far as the data goes. But do not restate the refusal as "no precedent exists" either -
  `scripts/portal/stopIceWall.js` is the shipped sibling and must be named whenever this is revisited.
- Do not copy `stopIceWall.js` to `stopIceWall2.js`. Every record in that room is already owned
  (22605 by reactor 1409000, 22600 by `outSDI.js`), so the copy would write nothing.
- Do not reopen any of these on the strength of a name, a wiki page, or a private server's table.
