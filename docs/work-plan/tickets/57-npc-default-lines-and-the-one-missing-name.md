# 57 - the 40 NPCs that say "(...)" and the one that renders MISSINGNO

**Class:** v84 parity
**Work rows:** R05, R06 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

`LifeFactory` reads exactly two leaves out of `String.wz/Npc.img`: `name` at `LifeFactory.java:295`
and `d0` at `LifeFactory.java:299`. Forty NPCs are missing `d0`, so clicking any of them without a
script produces the `(...)` default that `NPCConversationManager.java:95` falls back to. One NPC is
missing `name` and renders MISSINGNO. Every value is present in the pristine v84 carve; this is a
text merge with no behaviour change and no risk.

## R05 - 40 NPCs have no v84 `d0` line

Node path `String.wz/Npc.img/<id>/d0`, one per id. The forty ids, which are the `read_by=GAP` rows
for that path in `docs/work-plan/V84-COVERAGE.tsv`:

**1012119, 1013000, 1022008, 1032002, 1032102, 1052002, 1052006, 1061000, 1061006, 1063003,
1063004, 1063005, 1063006, 1063007, 1063008, 1063009, 1063010, 2001000, 2001001, 2002, 2003,
2020002, 2030015, 2032004, 2050009, 2060005, 2112003, 2112004, 22000, 9000007, 9000008, 9000009,
9001001, 9001002, 9001005, 9001006, 9020000, 9250005, 9250010, 9250022.**

Reader: `LifeFactory.java:299`. Consumer: `NPCConversationManager.java:95`, which is where the
`(...)` comes from when the leaf is absent.

Note the ids that are already live and clickable in the early game - **22000** is Shanks at
Southperry, **2060005** is the Aquarium NPC ticket 43 cites for the Kenta rate, **1013000** and
**1052002** sit on the Evan route. The other 115 `String.wz/Npc.img/<id>/{d1,n0,n1,func}` misses in
the coverage table are **not** in scope: the client draws those from its own archive and no server
reader opens them.

## R06 - one NPC has no v84 `name`

Node path `String.wz/Npc.img/9901000/name` - the single `name` GAP row in
`docs/work-plan/V84-COVERAGE.tsv`. Reader: `LifeFactory.java:295`. A missing `name` renders
MISSINGNO.

9901000 is inside the PlayerNPC band. It is the *name string* that is missing, not a placement -
this ticket adds a `String.wz` leaf and touches nothing about PlayerNPC allocation, which ticket 53
settled and closed.

## Precedent

- Values come straight from the pristine carve at `porting-resources/wz-data/v84/String.wz`
  (`SOURCES.md` tier 1: 18 `.wz` dated 2010-03-29, byte-identical to a fresh carve of
  `GMSSetupv84.exe`). Read with `WzPeek`. **Read-only.**
- **Commit `df9e779a9`** took the 11 Evan NPC names from that same source and same image. It is the
  edit to copy, id for id.
- **Commit `8c24b6fa5`** is the second example of the additive `String.wz` leaf merge.

## Acceptance criteria

- [ ] All 40 `String.wz/Npc.img/<id>/d0` leaves exist in `wz/String.wz/Npc.img.xml` with values
      matching the carve character for character.
- [ ] `String.wz/Npc.img/9901000/name` exists with the carve's value.
- [ ] A `*RealLoad` test asserts, one assertion per id, that `LifeFactory`'s NPC info for each of
      the 40 returns a non-null `d0` that is not the literal `(...)`.
- [ ] The same test asserts `LifeFactory` returns a non-null, non-empty name for 9901000.
- [ ] Re-running `python tools/playthrough/v84coverage.py` drops the `String` GAP count from 41 to 0
      and leaves the other archives' counts unchanged.
- [ ] The diff touches `wz/String.wz/Npc.img.xml` and the new test only. No `Npc.wz` image, no
      `life` array, no script is edited.
- [ ] The test class is named here and invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire
      includes `*RealLoad` (`pom.xml:239,272-274`), **but do not run maven while sibling agents are
      active**; hand the invocation to the orchestrator.

## Do not

- Do not add `d1`, `n0`, `n1` or `func`. The server reads none of them; they are the 115 benign rows
  already carved out in `V84-COVERAGE.md` and adding them re-opens a closed question.
- Do not write a script for any of these NPCs to work around the missing line. The defect is a
  missing data leaf, and a script would mask it for one NPC while 39 stay broken.
- Do not invent or translate a line. If the carve has no `d0` for an id, that id is not one of the
  40 - re-check the coverage row rather than filling it in.
- Do not touch anything else about NPC 9901000. PlayerNPC band policy is settled in ticket 53.
- Do not compare positionally; match on node name (`SOURCES.md`, "storage order is not name order").
