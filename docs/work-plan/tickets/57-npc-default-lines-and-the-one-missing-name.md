# 57 - the 40 NPCs that say "(...)", and the one "missing name" that is not a defect

**Class:** v84 parity
**Work rows:** R05 (in scope), R06 (**refused - see below**) - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

`LifeFactory` reads exactly two leaves out of `String.wz/Npc.img`: `name` at `LifeFactory.java:295`
and `d0` at `LifeFactory.java:299`. Forty NPCs are missing `d0`, so clicking any of them without a
script produces the `(...)` default. Every one of the forty values is present in the pristine v84
carve; that half is a text merge with no behaviour change.

**R06 is refused and removed from scope.** It is a coverage-table row, not a player-visible defect.
The reasoning is at the bottom of this ticket. Do not implement it.

## R05 - 40 NPCs have no v84 `d0` line

Node path `String.wz/Npc.img/<id>/d0`, one per id. The forty ids, which are the `read_by=GAP` rows
for that path in `docs/work-plan/V84-COVERAGE.tsv` - diffed programmatically against the tsv, **40
vs 40, zero symmetric difference**:

**1012119, 1013000, 1022008, 1032002, 1032102, 1052002, 1052006, 1061000, 1061006, 1063003,
1063004, 1063005, 1063006, 1063007, 1063008, 1063009, 1063010, 2001000, 2001001, 2002, 2003,
2020002, 2030015, 2032004, 2050009, 2060005, 2112003, 2112004, 22000, 9000007, 9000008, 9000009,
9001001, 9001002, 9001005, 9001006, 9020000, 9250005, 9250010, 9250022.**

**`2002` and `2003` are real NPC ids, not a parsing artifact.** The carve gives
`Npc.img/2002/name` = **Peter** and `Npc.img/2003/name` = **Robin** - Roger's brothers on Maple
Island. Both are in `docs/wz-baseline/add-list/String.txt:1366-1369`.

Reader: `LifeFactory.java:299`. **That line is also where the `(...)` literal lives:**
`return DataTool.getString(nid + "/d0", npcStringData, "(...)");`. `NPCConversationManager.java:95`
is the caller (`talk = LifeFactory.getNPCDefaultTalk(npcid);`) and contains no such string - an
earlier version of this ticket attributed the default to line 95, which is wrong.

Thirteen ids were sampled against both sides and every one confirms: ours has `name` and no `d0`, the
carve has `d0`. 22000 (Shanks, *"If you want to experience the world outside Maple Island..."*),
2060005 (Kenta), 1013000 (Mir), 1052002 (JM From tha Streetz), 2002 (Peter), 2003 (Robin), 1012119
(Power B. Fore), 1063003, 2001000 (Cliff), 9250022 (Yai Bua), 9020000 (Lakelis) among them. **There
is no missing-source problem in R05.**

Note the ids already live and clickable in the early game - **22000** is Shanks at Southperry,
**2060005** is Kenta the Aquarium Zoo Trainer, whom ticket 43 cites at
`docs/work-plan/tickets/43-evan-quest-sources.md:36` for the quest-3083 `chance 5` rate, and
**1013000** and **1052002** sit on the Evan route.

The other `String.wz/Npc.img/<id>/{d1,n0,n1,func}` misses are **not** in scope: the client draws
those from its own archive and no server reader opens them. **The count is 73, not 115.** From
`V84-COVERAGE.tsv`: `d1` 38, `func` 13, `n0` 11, `n1` 11 = 73; all non-GAP `Npc.img` rows including
the 9 `quest` rows and one odd leaf = 83. The figure **115** is the whole-`String`-archive benign
count and includes 32 `String.wz/Map.img/...` rows that have nothing to do with NPCs. The same
conflation is upstream at `V84-COVERAGE.md:36` and `:58` and needs fixing there by whoever owns the
trackers.

### Not "no risk" - two things to handle

**Some carve `d0` values are untranslated Korean.** `Npc.img/2001000/d0` = `우리 마을엔 무슨 일인가?`.
After the merge, Cliff speaks Korean. That is the same trade commit `df9e779a9` already made for the
11 Evan NPC names, so it is consistent policy - but it is a visible change, not a no-op, and the
owner should know before it lands.

**`WzPeek.exe` mangles non-ASCII to `?` on a default console.** Proven on
`Eqp.img/Eqp/Cap/1003043/name`, which prints `??? ?` for `순록의 뿔`. Force
`[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` before extracting, or the "character for
character" criterion below passes against corrupted data.

## R06 - REFUSED: 9901000 cannot render MISSINGNO

**Do not merge `String.wz/Npc.img/9901000/name`.** The premise of the row is false.

* 9901000 is inside the PlayerNPC band. A Hall-of-Fame PlayerNPC is a `MapObjectType.PLAYER_NPC` and
  **never** a `NPC`, so `LifeFactory.getNPC(9901000)` is never reached for a rendered object.
  `net/server/channel/handlers/QuestActionHandler.java:55-59` states this directly: *"A Hall-of-Fame
  PlayerNPC is a `MapObjectType.PLAYER_NPC` and never a `NPC`, so `getNPCById` cannot see one - quest
  22402 ... names 9901000, the first warrior slot of the `NpcId.PLAYER_NPC_BASE` band"*.
* No `wz/Map.wz` image contains a `9901000` life row - `grep -l` across the whole archive returns
  zero files. There is no placement to render.
* `PlayerNPC` takes its name from the database, not `String.wz`: `server/life/PlayerNPC.java:109`
  (`name = rs.getString("name")`), `:436` (`ps.setString(1, chr.getName())`), `:186-187`
  (`getName()` returns that field).
* So the carve's static value for that node - **`FangBlade`** - would be a fake name pinned onto a
  slot the allocator hands to a real player.

PlayerNPC band policy is settled and closed in ticket 53
(`docs/work-plan/tickets/53-v84-town-terrain-whole-image-replacement.md:108-128` - *"This is a closed
decision, not an open question"*, pinned by
`noHallOfFameMapCarriesAStaticNpcInThePlayerNpcBand`), which also names 9901000 as a live seeded
PlayerNPC. R06 contradicts that record.

`String.wz/Npc.img/9901000/name` is the single `name` GAP row in `V84-COVERAGE.tsv` (41 String GAPs =
40 `/d0` + this one). It should be reclassified benign by whoever owns the coverage tables. **This
ticket leaves it absent on purpose**, and any future re-file of the row should be pointed here.

## Precedent

- Values come from the pristine carve, `SOURCES.md` tier 1, at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\String.wz` - a **sibling of this repo**,
  not a subdirectory (`docs/work-plan/SOURCES.md:14`). The directory holds **17** `.wz` dated
  2010-03-29 (`SOURCES.md` says 18), byte-identical to a fresh carve of `GMSSetupv84.exe`.
  **Read-only.**
- Read it with `docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly
  two subcommands: **`dump`** and **`scan`**.
- **Commit `8c24b6fa5`** is the shape to copy: `wz/String.wz/Eqp.img.xml | 6 ++++++`, zero deletions.
  This merge is genuinely additive - all 40 `d0` leaves are absent - so an additive precedent is the
  right one.
- **Commit `df9e779a9`** took the 11 Evan NPC names from the same source and same image, and is the
  precedent for accepting Korean values from the carve. It is **not** an additive-shape precedent:
  it was 35 insertions and 35 deletions, replacing existing values.

## Acceptance criteria

- [ ] All 40 `String.wz/Npc.img/<id>/d0` leaves exist in `wz/String.wz/Npc.img.xml` with values
      matching the carve character for character, extracted under forced UTF-8 output.
- [ ] `String.wz/Npc.img/9901000/name` is still **absent**, and the refusal above is unchanged.
- [ ] A `*RealLoad` test asserts, one assertion per id, that `LifeFactory`'s NPC info for each of
      the 40 returns a non-null `d0` that is not the literal `(...)`.
- [ ] The Delivered note lists which of the 40 values are Korean, so the owner sees the visible
      change before it ships.
- [ ] The diff touches `wz/String.wz/Npc.img.xml` and the new test only. No `Npc.wz` image, no
      `life` array, no script is edited.
- [ ] The test class is named here and invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire's
      `default-test` execution **excludes** `*RealLoad` at `pom.xml:239`
      (`<exclude>**/*RealLoad.java</exclude>`); the **include** is in the separate `real-load-tests`
      execution at `pom.xml:272-274`. **Do not run maven while sibling agents are active**; hand the
      invocation to the orchestrator.

The old criterion "re-run `python tools/playthrough/v84coverage.py` and watch the String GAP count
drop from 41 to 0" has been removed. It is wrong twice: the script **writes**
`docs/work-plan/V84-COVERAGE.tsv` in full (`tools/playthrough/v84coverage.py:231-234`, mode `"w"`), so
it is not a read-only check and this ticket does not own that file; and it prints a per-archive
matrix, not a standalone "String GAP count". With R06 refused the count also lands at 1, not 0.

## Do not

- Do not implement R06. See the refusal above.
- Do not add `d1`, `n0`, `n1` or `func`. The server reads none of them; they are the 73 benign
  `Npc.img` rows already carved out in `V84-COVERAGE.md` and adding them re-opens a closed question.
- Do not write a script for any of these NPCs to work around the missing line. The defect is a
  missing data leaf, and a script would mask it for one NPC while 39 stay broken.
- Do not invent or translate a line. If the carve has no `d0` for an id, that id is not one of the
  40 - re-check the coverage row rather than filling it in. Equally, do not translate the Korean
  values that are there.
- Do not run `v84coverage.py` as part of this ticket. It rewrites a tracker file another agent owns.
- Do not compare positionally; match on node name (`SOURCES.md:103-104`, "storage order is not name
  order").
