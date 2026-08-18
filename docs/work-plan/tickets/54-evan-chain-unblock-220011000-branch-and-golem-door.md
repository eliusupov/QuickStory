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

## R01 - quests 22583 and 22584 have no route to their mobs

* `Check.img/22583/1/mob/0` = **9300389**, placed only on `wz/Map.wz/Map/Map9/922030011.img.xml:179`.
* `Check.img/22584/1/mob/0` = **9300390**, placed only on `wz/Map.wz/Map/Map9/922030022.img.xml:179`.
* The gateway is `wz/Map.wz/Map/Map2/220011000.img.xml` **portal/4**.

Ours: `pt=2`, `tm=220011001`, `tn=out00`, no script. Pristine v84 (read by `WzPeek portals`):
`pn=in00 pt=7 x=613 y=132 tm=999999999 script=enterBlackBC`.

**The branch is data-decided, not invented.** `QuestInfo.img/22583/1` names 922030010 and
`QuestInfo.img/22584/1` names 922030020; the two are mutually exclusive because
`Check.img/22584/0/quest/0` requires 22583 at state 2. Everything that matches neither falls through
to **220011001**, which is the destination our current `pt=2` portal already has. That fallback is
mandatory: 220011001 is a live map, and this portal is how a character reaches it.

Write `scripts/portal/enterBlackBC.js`.

## R02 - Golem's Temple Entrance 910600000 is unreachable

`wz/Map.wz/Map/Map1/106010101.img.xml` **portal/5**. Ours: `pn=in00 pt=2 x=92 y=-535 tm=106010102
tn=out00 horizontalImpact=0`. v84: `pn=in00 pt=7 tm=999999999 script=evanGolemDoor`.
`add-list/Map.txt:242` lists the script leaf.

Write `scripts/portal/evanGolemDoor.js` with 106010102 as the fallback branch and 910600000 behind
its gate.

**The index objection that blocked this row is retired.** Commit `80917ee28` already swapped slots
4/5 into v84 order and `6c7fb781d` retired the 106010102 refusal, so the slot this ticket edits is
the slot v84 means. Ticket 53's section "The one portal not taken whole" is the record of *why*
`pt`/`tm` were held back at the time, and it names this ticket's work as the condition for changing
it: *"If someone later adds `evanGolemDoor.js`, taking v84's node whole becomes correct - and that
test is where to change it."* The test is
`V84TerrainFootholdParityRealLoad.golemsTempleEntranceKeptAWorkingDestination`, and it must be
rewritten in the same commit that lands the script, not left asserting the old state.

## Precedent

`scripts/portal/enterBlackFrog.js`, shipped in commit `dda2d5f5a`, is the same shape: one portal
name, a quest-state branch, several destinations. `scripts/portal/enterDollcave.js` is the second
instance of that shape. `EvanQuestRecordGatesRealLoad.java:479-503` states the failure mode both
scripts must avoid.

Portal-type semantics, already established by ticket 47: `pt=7` is a scripted **warp**, `pt=9` is a
touch **trigger** that does not warp. Both portals here are `pt=7`.

## Acceptance criteria

- [ ] `scripts/portal/enterBlackBC.js` exists, loads under the real `GraalJSScriptEngine`, and warps
      to **922030010** when quest 22583 is started and 22584 is not, to **922030020** when 22584 is
      started, and to **220011001 / `out00`** otherwise.
- [ ] `wz/Map.wz/Map/Map2/220011000.img.xml` portal/4 reads `pt=7`, `tm=999999999`,
      `script=enterBlackBC`, `pn=in00`, `x=613`, `y=132` - all six leaves matching the pristine carve.
- [ ] `scripts/portal/evanGolemDoor.js` exists, loads, reaches **910600000** under its gate and
      **106010102 / `out00`** on the fallback branch.
- [ ] `wz/Map.wz/Map/Map1/106010101.img.xml` portal/5 reads `pt=7`, `tm=999999999`,
      `script=evanGolemDoor`, and keeps `pn=in00 x=92 y=-535`.
- [ ] `V84TerrainFootholdParityRealLoad.golemsTempleEntranceKeptAWorkingDestination` is rewritten to
      assert the v84 node plus the script's fallback, and passes.
- [ ] `MapAndPortalScriptsRealLoad` asserts, for both new scripts, that they call
      `playPortalSound()` plus exactly one `warp(...)` and nothing else
      (`verifyNoMoreInteractions`), and that neither names an `Effect.wz` `Direction*` scene - a
      scene path the client cannot resolve crashes the client.
- [ ] An Evan at level 68 with 22582 complete can accept 22583, reach 922030011 and kill 9300389;
      the same for 22584 into 922030022.
- [ ] Reaching 220011001 through 220011000 still works for a character on neither quest.

Tests run as `-Dtest=MapAndPortalScriptsRealLoad,V84TerrainFootholdParityRealLoad`. **Do not run
maven while sibling agents are active** - they collide on `target/`. State the invocation and hand
the run to the orchestrator.

## Do not

- Do not add only the `script` leaf. A `pt=2` portal never consults it, and the row would look done
  while nothing changed.
- Do not drop the 220011001 or 106010102 fallback. Both are live maps and both are the only way in
  for a character not on the quest.
- Do not invent a third destination for either script. Every branch above is named by a
  `QuestInfo.img` or `Check.img` node; anything past them is invention.
- Do not play an `Effect.wz` scene from either script.
