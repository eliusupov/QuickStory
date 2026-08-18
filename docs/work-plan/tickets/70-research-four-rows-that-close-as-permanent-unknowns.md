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

## THE REFUSAL RECORD — verified 2026-08-18, this is the closure

Every claim below was re-run against Tier 1 (the pristine carve) rather than taken from this
ticket's own word. Each row carries a **verification stamp** giving the exact command and its
output. Three claims in the prior revision were wrong and are corrected in place; **none of the
three flipped a verdict** — one made a refusal weaker than it should be, one made it rest on a
non-discriminator, and one was a mis-citation of geometry.

| row | wanted | looked in | found | must never be written |
|---|---|---|---|---|
| R34 | Iron Hook 4090000's drop table | v84 `Etc.wz` (all 22 images), `MonsterBook.img`, `add-list/Mob.txt` | no `Reward.img`, no `Server/` dir, no book entry | any `drop_data` row with `dropperid=4090000` |
| R35 | General Mau 1011101's shop stock | v84 `Npc.wz` (all 1662 images), `Etc.wz`, `shops`, `leftover.txt` | v84 carries **no shop data for any NPC** | any `shops`/`shopitems` row for npcid 1011101 |
| R47 | ice-wall mob 9300391's count and positions | v84 `Map.wz` (4848 images), `Mob.wz` (1605 images), our `wz/` | the mob exists, is placed nowhere, is revived by nothing | `scripts/map/onUserEnter/summonIceWall.js`, `scripts/portal/stopIceWall2.js` |
| R52 | a Leviathan drop row for 4032530 | `String.wz/Etc.img`, ticket 06 | it is a G-Star 2009 giveaway; the mob is placed nowhere | any `drop_data` row with `itemid=4032530` |

Database state at verification, the acceptance criteria in one query:

    SELECT (SELECT COUNT(*) FROM drop_data WHERE dropperid=4090000),   -- 0
           (SELECT COUNT(*) FROM drop_data WHERE itemid=4032530),      -- 0
           (SELECT COUNT(*) FROM shops    WHERE npcid=1011101);        -- 0

`scripts/portal/stopIceWall2.js` and `scripts/map/onUserEnter/summonIceWall.js` do not exist.
The site comment the acceptance criteria ask for **already exists** and predates this ticket:
`scripts/portal/stopIceWall.js:27-30` carries the `ponytail:` refusal for 9300391, and
`scripts/reactor/1409000.js:18-20` and `scripts/portal/outSDI.js:37-38` each carry their own. No
new comment was added; adding a fourth would say the same thing in a fourth place.

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

### Verification stamp — R34, 2026-08-18: REFUSAL CONFIRMED, and it is stronger than stated

Re-run, all four commands, `<v84>` = `D:\games\MapleStory\Server\porting-resources\wz-data\v84`:

    WzPeek dump <v84>\Etc.wz Reward.img 0         -> NOT-FOUND	Reward.img
    WzPeek dump <v84>\Etc.wz Server/Reward.img 0  -> NOT-FOUND	Server/Reward.img
    WzPeek dump <v84>\Etc.wz Commodity.img 0      -> FOUND (control: the tool does find what is there)
    WzPeek dump <v84>\Etc.wz "" 1                 -> 22 images, listed in full below

The root listing is the part worth keeping, because it converts "the two paths we guessed are
absent" into "there is nowhere left to look". v84 `Etc.wz` holds exactly these, and **no `Server`
directory at all**:

`NPT_exception`, `Category`, `ScriptInfo`, `ChatBlockReason`, `BlockReason`, `RecommendSkill`,
`ScanBlock`, `NpcLocation`, `Halloween`, `OXQuiz`, `MakeCharInfo`, `ForbiddenName`,
`EmotionEffect`, `ItemMake`, `CashPackage`, `Curse`, `MedalQuestCategory`, `Tips`, `VegaSpell`,
`Swindle`, `QuestCategory`, `Commodity`.

Corroboration, all re-run: `grep -c 4090000 wz/String.wz/MonsterBook.img.xml` -> `0`;
`add-list/Mob.txt:131` is the file's only mention of 4090000 and is exactly
`Mob.wz/4090000.img/info/category`; and the mob's placement is real Tier 1 content -
`WzPeek scan <v84>\Map.wz id 4090000` returns 15 hits, **1 on `104010001`** and **14 on
`106000110`**, matching the row's placement claim exactly. So the mob is placed, is v83 content
with one v84 leaf touched, and has no table anywhere. Nothing further to chase.

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

### Verification stamp — R35, 2026-08-18: REFUSAL CONFIRMED, but the stated ground was wrong

The verdict holds. The *reason* given for it did not, and is corrected here so the next agent does
not go looking for a flag that does not exist in this version.

**"`1011101.img/info` is an empty node - no shop flag" is a non-discriminator.** The info node is
genuinely empty (`WzPeek dump <v84>\Npc.wz 1011101.img 3` shows `info` with zero children, then
`stand`/`say`/`hand`/`smile` art and `say/speak/0`=`n0`, `say/speak/1`=`n1`). But an empty info node
says nothing about shops here, because **v84's `Npc.wz` carries no shop flag on any NPC**:

    WzPeek scan <v84>\Npc.wz shop 1   -> 0 hits, scanned 1662 images
    WzPeek scan <v84>\Npc.wz 0 n0     -> HITs (control: the scan works, e.g. 1012004, 9201124)

And the two NPCs that *do* have shops in our database look identical to Mau in Tier 1:

    WzPeek dump <v84>\Npc.wz 1011100.img/info 2  -> info/speak/0 = n0        (Luna: 25 shopitems)
    WzPeek dump <v84>\Npc.wz 1001000.img/info 2  -> info/speak/0..1 = n0,n1  (a stocked vendor)

Neither carries a shop flag either. So the absence of one on Mau proves nothing about Mau.

**The real ground, and it is the same one as R34:** shop stock is *server-side data* that Nexon
never shipped in any client version, exactly like drop tables. `Etc.wz` has no shop image
(`scan <v84>\Etc.wz 1011101 1` -> 0 hits across all 22 images; `ScriptInfo.img` is keyed by script
name, not npcid, and names no Mau script). There is no node in v84 that could have held his stock,
so there is no node whose emptiness could be read either way. Tier 1 is structurally silent, not
suggestively silent. That is a permanent unknown, and it is the strongest form of one.

Re-verified state: `scripts/npc/1011101.js` exists (commit `9bbf0dbcd`, "NPC 1011101 General Mau:
give the dead Henesys vendor a script") and its one line is verbatim
`String.wz/Npc.img.xml:136`'s `n0`. `SELECT COUNT(*) FROM shops WHERE npcid=1011101` -> `0`, while
Luna's `shopid 1011100` carries 25 `shopitems`. `docs/leftover.txt:40` reads `General Mau (v84)`.

**Never written:** a `shops` row for 1011101, and above all not a copy of Luna's 25 items. Same map
and same family is not evidence of the same stock — and now that the shop flag is known to be
absent from the whole archive, there is not even a weak signal to argue from.

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

  Two columns (x 56-58, and x 156) by five rows. All ten carry `script="stopIceWall2"`.

  An earlier revision gave "x = 58 / 156, y = 99 / 2 / -95 / -193 / -290" and a later one explained
  it as "four y values off by 1-2". **Both explanations are wrong, and the real one matters:** that
  coordinate set is not a mis-measurement of 914100022 at all, it is *the sibling map*. 914100020's
  ten triggers sit at y = 99 / 2 / -95 / -193 / -290 exactly, in the left column x = 56 and the right
  column x = 153-155. The two maps have genuinely different grids and were conflated. Use the table
  for 914100022; do not reconcile it against 914100020's.

The wall is mob **9300391**: placed in **no map** and produced by **no revive chain**. Count,
positions and the stop mechanic exist in no WZ file, and `Map.wz` carries no ice-wall art on either
914100020 or 914100022.

The re-provable negative, one command from the repo root - this is the command the acceptance
criterion below asks for:

    grep -rl 9300391 wz/Map.wz/ wz/Mob.wz/
    -> wz/Mob.wz/9300391.img.xml        (its own image, and nothing else)

Zero hits under `wz/Map.wz/`, and no other mob image names it as a revive.

**Correction, 2026-08-18: the "4,848 / 1,605" figures were dropped in error and are restored below.**
A revision of this row quoted "4,848 pristine v84 map images" and "1,605 mob images", and a later
revision struck them on the grounds that "the pristine carve is binary `.wz`, not per-image files,
and no enumeration producing either number is recorded anywhere". That reasoning is wrong on both
halves: `WzPeek scan` walks *into* the binary archive and prints its own image count on the last
line, so the enumeration is a single command and the numbers are exactly reproducible. Striking them
downgraded this row's evidence from Tier 1 to a grep over `wz/` (Tier 2, our own tree), which is the
wrong direction — `SOURCES.md` is explicit that only Tier 1 settles "did v84 have X". The grep is
kept as a convenience; the scans below are the actual evidence.

`stopIceWall2` additionally has **no quest record to write**: 22588's only record is 22605
(`Check.img/22588/1`, infoNumber 22605, infoex/0/value "1"), already written by
`scripts/reactor/1409000.js:25`; and 22600 belongs to quest **22589**, already written by
`scripts/portal/outSDI.js:41`. It is a no-op until `summonIceWall` exists.

### The precedent that DOES exist - name it before refusing

`scripts/portal/stopIceWall.js` **is shipped and tested.** It implements the identical ten-`pt=9`
trigger grid on the sibling map **914100020** (`portal/2..11`, same `delay=200`, same two-column
geometry), and is pinned by `MapAndPortalScriptsRealLoad.java:85`
(`PORTAL_HOOKS.put("stopIceWall", 914100020)`) and `:200` (`runPortal("stopIceWall", ...)` - an
earlier revision said `:196`). Its body is four lines: guard on
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

### Verification stamp — R47, 2026-08-18: REFUSAL CONFIRMED on Tier 1, not on a grep

The Tier-1 negatives, both with a passing control run against the same leaf so that "0 hits" means
"absent" and not "the scan syntax was wrong":

    WzPeek scan <v84>\Map.wz id 9300391  -> 0 hits, scanned 4848 images
    WzPeek scan <v84>\Map.wz id 9300390  -> HIT Map/Map9/922030022.img/life/0/id   (control passes)
    WzPeek scan <v84>\Mob.wz 0 9300391   -> 0 hits, scanned 1605 images
    WzPeek scan <v84>\Mob.wz 0 8800001   -> HIT 8800000.img/info/revive/0          (control passes)

So across **every one of the 4848 map images and 1605 mob images in the pristine v84 carve**, mob
9300391 is placed in no `life` node and named by no `revive` chain. The Tier-2 grep agrees:
`grep -rl 9300391 wz/Map.wz/ wz/Mob.wz/` returns only `wz/Mob.wz/9300391.img.xml` (note the real
path of the map tree is `wz/Map.wz/Map/Map9/`, not `wz/Map.wz/Map9/` as cited above).

The mob is real and fully statted - `WzPeek dump <v84>\Mob.wz 9300391.img/info 2` gives boss=1,
level 64, maxHP 7700, eva 999, speed -50, noFlip=1, and **`summonType=0`**. It is an ordinary
spawnable mob that v84 simply never placed, which is precisely why the count and positions can only
have lived in server code. Nothing infers them.

Everything else in the row re-checked and correct: `914100022.img.xml:18` is
`onUserEnter="summonIceWall"`; the ten-portal table above is exact; **914100020 has an empty
`onUserEnter`**, so 914100022 is the only map in the pair with a summon hook. The record ownership
holds - `Check.img/22588/1` is infoNumber 22605 infoex/0/value "1" (written by
`scripts/reactor/1409000.js:25`) and `Check.img/22589/0` is infoNumber 22600 value "1" (written
by `scripts/portal/outSDI.js:41`), so a `stopIceWall2.js` would have no record left to write.

**Never written:** `scripts/map/onUserEnter/summonIceWall.js`, and `scripts/portal/stopIceWall2.js`
as a copy of `stopIceWall.js`. And never restate this refusal as "no precedent exists" -
`stopIceWall.js` is the shipped, tested sibling and its own header already refuses 9300391 in the
same terms.

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

### Verification stamp — R52, 2026-08-18: REFUSAL CONFIRMED, every citation exact

* `wz/String.wz/Etc.img.xml:110-113` - `4032530`, `name` = `Leviathan's Tear `, `desc` =
  `G-Star Clear ` and nothing else. (The row cited `:106-108`; the entry is four lines lower.)
* The sibling is `wz/String.wz/Consume.img.xml:7467-7470` - `2430034` `G-star Reset Item`,
  "An item that can be used to reset during an emergency." Different archive, same giveaway.
* `wz/String.wz/Mob.img.xml:150-152` - `9500382` is indeed named `Leviathan`. The name link is real;
  it is still not a verdict.
* `docs/work-plan/tickets/06-crimson-sky.md:62` says verbatim that `9500374`-`9500382` "appear in no
  `life` node anywhere in scope" and are `summonType=1` clones. So ticket 06 does **not** place
  9500382, and R52's own work-row line in `V84-WORK-ROWS.tsv` - which says ticket 06 "places" it -
  is the stale text. This ticket is the correct one.
* The `NAME_LINK` quote is `docs/work-plan/V84-QUEST-DROPPER-SWEEP.md:161-165`, and `SOURCES.md`
  contains zero occurrences of `NAME_LINK` - confirming the earlier mis-citation this row already
  flags.

**Never written:** a `drop_data` row pairing 4032530 with 9500382, or with any mob. A convention
giveaway has no dropper. `SELECT COUNT(*) FROM drop_data WHERE itemid=4032530` -> `0`, and it stays 0.

## Acceptance criteria

- [x] All four refusals are recorded in a place a future agent will actually look - the "Refusals
      worth keeping visible" list is the orchestrator's file, so the implementing agent adds nothing
      there and instead leaves the reasoning in this ticket plus, where the code is the natural
      place, a `ponytail:`-style comment at the site.
- [x] `drop_data` gains **zero** rows for itemid 4032530 and **zero** rows for dropperid 4090000.
      Verifiable by `SELECT COUNT(*)` on both.
- [x] `shops` gains **zero** rows for npcid 1011101.
- [x] `scripts/portal/stopIceWall2.js` and `scripts/map/onUserEnter/summonIceWall.js` do **not**
      exist. `MapAndPortalScriptsRealLoad` may assert their absence, the same way it pins other
      deliberate gaps.
- [x] The `9300391` negative is re-provable in one command - recorded under R47 above as
      `grep -rl 9300391 wz/Map.wz/ wz/Mob.wz/`, expected to return only `wz/Mob.wz/9300391.img.xml` -
      so the scan is not redone by hand.
- [x] No claim in this ticket rests on a raw byte grep of a `.wz` file. Presence or absence inside a
      WZ archive is settled with `WzPeek dump` and nothing else.
- [x] No new evidence is cited from Tier 3 for any of the four. Tier 3 is good for rates and dropper
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
