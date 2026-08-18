# 54 - The two portals the Evan chain dies on: 220011000 and 106010101

**Class:** v84 parity
**Work rows:** R01, R02 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

The Evan chain is walkable to level 68 and then stops. Quests 22583 and 22584 want mobs that live on
two maps behind a portal our tree routes past, and Golem's Temple Entrance 910600000 is unreachable
for the same reason one map over. Both portals exist in v84 as `pt=7` script portals with
`tm=999999999`; ours are plain `pt=2` warps with no script, so the branch v84 puts behind the script
never runs. **The `pt` and `tm` change too, not only the `script` leaf** - a script leaf added to a
`pt=2` portal is never consulted.

Do R01 first. R02 is the same edit with a different destination table, and copying R01's shape is
the whole reason they share a ticket.

**Read "Tests this ticket breaks" before writing anything.** Five assertions across four test classes
currently pin the broken state, and three of those classes are not in the obvious `-Dtest=`
invocation. A run that misses them looks green and breaks the suite.

## R01 - quests 22583 and 22584 have no route to their mobs

* `Check.img/22583/1/mob/0` = **9300389**, placed only on `wz/Map.wz/Map/Map9/922030011.img.xml:179`.
* `Check.img/22584/1/mob/0` = **9300390**, placed only on `wz/Map.wz/Map/Map9/922030022.img.xml:179`.
  Whole-tree grep over `wz/Map.wz` returns exactly one hit for each id.
* The gateway is `wz/Map.wz/Map/Map2/220011000.img.xml` **portal/4**.

Ours today: `pn=in00 pt=2 x=613 y=132 tm=220011001 tn=out00`, no script leaf. Pristine v84, read with
`WzPeek dump`: `pn=in00 pt=7 x=613 y=132 tm=999999999 tn="" horizontalImpact=0 script=enterBlackBC`.

**That is eight leaves, not six.** The pristine node carries `tn=""` and `horizontalImpact=0`; ours
carries `tn="out00"` and no `horizontalImpact`. Both differences are part of the edit - see the
acceptance criteria.

**The branch is data-decided, not invented.** `QuestInfo.img/22583/1` names 922030010 and
`QuestInfo.img/22584/1` names 922030020; the two are mutually exclusive because
`Check.img/22584/0/quest/0` requires 22583 at state 2. Everything that matches neither falls through
to **220011001**, which is the destination our current `pt=2` portal already has. That fallback is
mandatory: 220011001 is a live map, and this portal is the only way a character reaches it.

**One test of record says the opposite and must be retired in the same commit.**
`src/test/java/server/V84PortalIndexParityRealLoad.java:272-276` carries the javadoc claim that
`enterBlackBC` appears "exactly once in all 4848 v84 images, **so there is nothing to derive its gate
from**". The `QuestInfo.img` derivation above is what retires that claim. Rewrite the javadoc; do not
leave two files in the repo asserting opposite things.

Write `scripts/portal/enterBlackBC.js`.

## R02 - Golem's Temple Entrance 910600000 is unreachable

`wz/Map.wz/Map/Map1/106010101.img.xml` **portal/5**. Ours: `pn=in00 pt=2 x=92 y=-535 tm=106010102
tn=out00 horizontalImpact=0`. Pristine v84: `pn=in00 pt=7 x=92 y=-535 tm=999999999 tn=""
horizontalImpact=0 script=evanGolemDoor`. Note the pristine `tn` is empty here too.
`docs/wz-baseline/add-list/Map.txt:242` lists the script leaf; `:241` already lists
`horizontalImpact`.

910600000 is genuinely unreachable today: it is referenced by nothing in `wz/Map.wz`, nothing in
`scripts/`, and nothing in `src/main/java` beyond its own image header, and `WzPeek scan Map.wz tm
910600000` over all 4,848 pristine images returns zero hits.

### The gate is quest 22555

**This was previously left undefined, which put the row in conflict with this ticket's own "do not
invent a destination" rule. It is defined now, from the data:**

* `wz/Quest.wz/QuestInfo.img.xml:7950` - quest **22555**, "Chief Stan's Test": *"go to the
  #m910600000#, hunt the #o3000001#s there, and bring back #b#t4000068#s#k. You can enter #m910600000#
  through the Warning Sign at the #m106010100#"*.
* `Check.img/22555/1` = npc 1012003, item 4000068 x1. No mob node.
* Corroboration from the map data itself: `wz/Map.wz/Map/Map9/910600000.img.xml` **portal/1** =
  `out00 pt=2 tm=106010101 tn=in00` - the return side of exactly this door.

**The 106010102 branch is not a "nobody" fallback.** `QuestInfo.img.xml:7957` - quest **22556** -
explicitly sends the player to `#m106010102#`. Both destinations are quest-relevant; 106010102 is
simply where everything not gated to 910600000 goes.

**Trap: the quest text names 106010100, not 106010101.** Pristine `Map/Map1/106010100.img/portal` has
11 slots - `sp`, `in00` (pt=10), `west00`, `dun00` and siblings - and **no script portal at all**. The
Warning Sign door is 106010101/in00, the portal this row edits. Do not go looking for a script slot
on 106010100; there is none.

Write `scripts/portal/evanGolemDoor.js` with 910600000 behind the 22555 gate and 106010102 / `out00`
on the other branch.

**The index objection that blocked this row is retired.** Commit `80917ee28` already swapped slots
4/5 into v84 order and `6c7fb781d` retired the 106010102 refusal, so the slot this ticket edits is
the slot v84 means. Ticket 53's section "The one portal not taken whole"
(`docs/work-plan/tickets/53-v84-town-terrain-whole-image-replacement.md:214`) is the record of *why*
`pt`/`tm` were held back at the time, and it names this ticket's work as the condition for changing
it: *"If someone later adds `evanGolemDoor.js`, taking v84's node whole becomes correct - and that
test is where to change it."*

## Tests this ticket breaks

Five assertions across four classes pin the current broken state. **All five must be rewritten in the
same commit that lands the scripts.** Only the first was previously named.

| file:line | asserts | row |
|---|---|---|
| `src/test/java/server/V84TerrainFootholdParityRealLoad.java:432-441` | `106010101/5` tm==106010102, pt==2, script==null | R02 |
| `src/test/java/server/V84MiscAreasNodeTest.java:314-323` | `106010101/in00` tm==106010102 and `assertNull(getChildByPath("script"))` - "must not name evanGolemDoor" | R02 |
| `src/test/java/server/V84MiscAreasNodeTest.java:345-348` | 220011000 has 5 portals, `assertNoneOf(220011000,"in00","script",...)`, tm==220011001 | R01 |
| `src/test/java/server/EvanQuestRecordGatesRealLoad.java:493-503` | `106010101/portal/5` tm==106010102 and `script==""` | R02 |
| `src/test/java/server/V84PortalIndexParityRealLoad.java:278-299` | row `{220011000, 4, "in00", 220011001, "out00"}` still holds | R01 |

The named test `V84TerrainFootholdParityRealLoad.golemsTempleEntranceKeptAWorkingDestination` is the
first row; it was the only one this ticket used to name.

Note the irony on the R02 blocker: commit `6c7fb781d`, cited above as *retiring* the refusal, is the
commit that **added** `V84MiscAreasNodeTest:321` - the `assertNull(script)` that now blocks it.

`EvanQuestRecordGatesRealLoad.java:479-503` also states the failure mode both new scripts must avoid,
but it is a hard assertion on the old node, not merely documentation.

## Precedent

`scripts/portal/enterBlackFrog.js`, shipped in commit `dda2d5f5a`, is the same shape: one portal
name, a quest-state branch, several destinations. `scripts/portal/enterDollcave.js` is the second
instance of that shape.

Portal-type semantics, already established by ticket 47
(`docs/work-plan/tickets/47-evan-ice-cave.md:89-92`): `pt=7` is a scripted **warp**, `pt=9` is a touch
**trigger** that does not warp. Both portals here are `pt=7`.

The pristine carve is at the absolute path
`D:\games\MapleStory\Server\porting-resources\wz-data\v84\` - a **sibling of this repo**, not a
subdirectory of it (`docs/work-plan/SOURCES.md:14`). Read it with
`docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly two
subcommands: **`dump`** and **`scan`**. There is no `portals` subcommand.

## Acceptance criteria

- [ ] `scripts/portal/enterBlackBC.js` exists, loads under the real `GraalJSScriptEngine`, and warps
      to **922030010** when quest 22583 is started and 22584 is not, to **922030020** when 22584 is
      started, and to **220011001 / `out00`** otherwise.
- [ ] `wz/Map.wz/Map/Map2/220011000.img.xml` portal/4 reads `pn=in00`, `pt=7`, `x=613`, `y=132`,
      `tm=999999999`, `tn=""`, `horizontalImpact=0`, `script=enterBlackBC` - **all eight leaves**
      matching the pristine carve, including the two our node does not currently carry in that shape.
- [ ] `scripts/portal/evanGolemDoor.js` exists, loads, reaches **910600000** when quest **22555** is
      started, and **106010102 / `out00`** otherwise.
- [ ] `wz/Map.wz/Map/Map1/106010101.img.xml` portal/5 reads `pn=in00`, `pt=7`, `x=92`, `y=-535`,
      `tm=999999999`, `tn=""`, `horizontalImpact=0`, `script=evanGolemDoor`.
- [ ] All five assertions in "Tests this ticket breaks" are rewritten to assert the v84 node plus the
      script's branches, and pass.
- [ ] `V84PortalIndexParityRealLoad.java:272-276`'s "nothing to derive its gate from" javadoc is
      replaced with the `QuestInfo.img` derivation.
- [ ] `MapAndPortalScriptsRealLoad` asserts, for both new scripts, that they call
      `playPortalSound()` plus exactly one `warp(...)` and nothing else
      (`verifyNoMoreInteractions`), and that neither names an `Effect.wz` `Direction*` scene - a
      scene path the client cannot resolve crashes the client.
- [ ] An Evan at level 68 with 22582 complete can accept 22583, reach 922030011 and kill 9300389.
      For 22584 the route is **two hops**: `922030020/up00` -> `922030021/st00`, then
      `922030021/in00` -> `922030022/out00`. Map 922030021 is on the path and must be walkable.
- [ ] A character on quest 22555 reaches 910600000 through 106010101/in00 and returns via
      `910600000/portal/1`.
- [ ] Reaching 220011001 through 220011000, and 106010102 through 106010101, still works for a
      character on none of these quests.

Tests run as
`-Dtest=MapAndPortalScriptsRealLoad,V84TerrainFootholdParityRealLoad,V84MiscAreasNodeTest,EvanQuestRecordGatesRealLoad,V84PortalIndexParityRealLoad`.
**Do not run maven while sibling agents are active** - they collide on `target/`. State the
invocation and hand the run to the orchestrator.

Caveat on the walkability criteria: `wz/Quest.wz/Say.img.xml` has **zero** entries in the 22550-22600
range, and no `scripts/npc/` file exists for the NPCs on this chain. Accept and complete still work
through the quest-window path (`QuestActionHandler.java:129`, cases 1 and 2), which needs neither a
`Say` entry nor an NPC script - but clicking the NPC in-game produces nothing. That is out of scope
here; do not "fix" it by writing NPC scripts.

## Do not

- Do not add only the `script` leaf. A `pt=2` portal never consults it, and the row would look done
  while nothing changed.
- Do not leave `tn="out00"` on either node. Pristine is `tn=""` on both, and a literal reading of the
  old six-leaf criterion left it behind.
- Do not drop the 220011001 or 106010102 branch. Both are live maps, 106010102 is named by quest
  22556, and both are the only way in for a character not on the gating quest.
- Do not invent a third destination for either script. Every branch above is named by a
  `QuestInfo.img` or `Check.img` node; anything past them is invention.
- Do not go looking for a script portal on 106010100. It has eleven portals and none of them is one.
- Do not play an `Effect.wz` scene from either script.
