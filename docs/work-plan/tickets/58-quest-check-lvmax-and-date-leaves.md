# 58 - the quest requirement leaves v84 added and we never merged

**Class:** v84 parity
**Work rows:** R07, R08 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

122 leaves under `Quest.wz/Check.img` are missing, and every one of them is a *requirement* the
server already knows how to enforce - the reader exists, the value does not. 108 are the `lvmax`
level cap v84 put on the job-instructor training line, so those quests stay startable past the cap.
14 are date and repeat fields, so repeatable quests have no cooldown and date-retired quests stay
live. Both rows only tighten requirements; neither invents one.

## R07 - 108 quests have no `lvmax`, so they stay startable past v84's level cap

Node path `Quest.wz/Check.img/<id>/0/lvmax`. The 108 quest ids:

- **28162 through 28266** inclusive (105 ids - the job-instructor training line)
- **28282**, **28283**, **28325**

Reader: `server/quest/QuestRequirementType.java:74`. `add-list/Quest.txt` lists all 108 paths
explicitly; values come from `porting-resources/wz-data/v84/Quest.wz`.

## R08 - 14 date and repeat leaves are missing

| quest | missing leaves |
|---|---|
| **2208** | `0/start`, `0/end`, `0/interval` |
| **2209** | `0/start`, `0/end`, `0/interval` |
| **2210** | `0/start`, `0/end`, `0/interval` |
| **2211** | `0/start`, `0/end`, `0/interval` |
| **10109** | `0/interval` |
| **3845** | `0/end` |
| **9260** | `0/dayByDay` |

Readers: `QuestRequirementType.java:84` for `interval`, and
`server/quest/requirements/EndDateRequirement.java` for the date window.

**2208-2211 are Bartol's Requests - live v83 quests a player can be part-way through.** Adding the
`interval` gives them a cooldown they do not have today, and adding `start`/`end` can close a window
a character is standing inside. That is the whole risk surface of this ticket and it is why the
acceptance criteria below ask for the effect on an in-progress record to be stated, not assumed.

## Precedent

- `add-list/Quest.txt` enumerates every path in both rows. Values come from the pristine carve at
  `porting-resources/wz-data/v84/Quest.wz` - `SOURCES.md` tier 1, read with `WzPeek`, **read-only**.
- This is a leaf-merge of the same shape as the quest-halving work in commits **`434c5cba5`** and
  **`32fa7879f`**. Copy that shape: additive, leaf by leaf, no existing id rewritten.
- `EndDateRequirement` is already the live enforcement path for the 327 expired quests documented in
  ticket 44 - it refuses a quest before anything else is consulted. Nothing new is being built here;
  the data is simply arriving.

## Acceptance criteria

- [ ] All 108 `Check.img/<id>/0/lvmax` leaves exist in `wz/Quest.wz/Check.img.xml`, and each value
      equals the carve's value for that id. A per-id assertion, not an aggregate count.
- [ ] All 14 leaves in the R08 table exist with the carve's values, including `9260/0/dayByDay`.
- [ ] A `*RealLoad` test asserts that a character above the cap is refused for a named sample of the
      108 (at minimum the first, last and the three outliers 28282, 28283, 28325) and accepted below
      it - exercising `QuestRequirementType` for real, not asserting the XML twice.
- [ ] The same test asserts `interval` is honoured for 2208-2211 and 10109, and that
      `EndDateRequirement` refuses 3845 outside its window.
- [ ] Re-running `python tools/playthrough/v84coverage.py` drops the `Quest` GAP count by 122 and
      leaves every other archive unchanged.
- [ ] No pre-existing quest id's leaves are altered - proved by diffing `wz/Quest.wz/Check.img.xml`
      and confirming every changed hunk is an addition.
- [ ] The effect on 2208-2211 for a character mid-quest is stated in this ticket before the merge
      lands: what a `queststatus` row in state 1 does when its quest gains a `start`/`end`/`interval`.
- [ ] Test class named here, invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire includes
      `*RealLoad` (`pom.xml:239,272-274`), **but do not run maven while sibling agents are active**.

## Do not

- Do not derive a `lvmax` from the quest's `lvmin` or from a sibling quest. Every one of the 108
  values is in the carve; read it.
- Do not delete or modify a `queststatus` row to make a date window fit. Data repair on live
  characters is a separate, owner-gated decision.
- Do not extend the merge past the paths in `add-list/Quest.txt`. The 138 client-only and 5 cosmetic
  `QuestInfo.img` rows in `V84-COVERAGE.md` are out of scope.
- Do not compare positionally. `Check.img` children are keyed by quest id, but the `0` block's
  leaves must still be matched by name.
- Do not drop an empty-valued leaf while copying. Empty is not absent.
