# 58 - the quest requirement leaves v84 added and we never merged

**Class:** v84 parity
**Work rows:** R07, R08 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

**123 leaves** under `Quest.wz/Check.img` are missing: **108** `lvmax` level caps (R07) and **15**
date and repeat fields (R08). The earlier headline of 122 = 108 + 14 was an arithmetic error - the
R08 table below has always listed 15 paths, and `docs/wz-baseline/add-list/Quest.txt` carries exactly
15 of them (lines 203, 244-252, 263-265, 522, 523). `V84-WORK-ROWS.tsv:9` still says 14 and is stale
on the same point.

**The "the reader exists, the value does not" premise holds for 10 of the 15 R08 leaves, not all
15.** Two of the three readers this ticket used to name do not do what it claimed:

* **`dayByDay` is read by nothing.** `server/quest/QuestRequirementType.java:108` is
  `case "daybyday":` - lowercase - and Java's string switch is case-sensitive, so the WZ leaf
  `dayByDay` falls through to `default:` and resolves to `UNDEFINED`. Even if the label matched,
  `server/quest/Quest.java:537-605` has no `DAY_BY_DAY` case (it hits `default: break`, returns null,
  and `Quest.java:164-166` `continue`s without registering anything), and there is no
  `DayByDayRequirement.java` in `src/main/java/server/quest/requirements/`. Merging `9260/0/dayByDay`
  is inert until someone writes a reader.
* **`start` is explicitly discarded.** `EndDateRequirement.java:53-58` reads a **single** `timeStr`
  and returns `cal >= now` - one bound, the end. `Quest.java:600-602` is
  `case NORMAL_AUTO_START: case START: case END: break;` - `START` builds no requirement at all. The
  four `2208-2211/0/start` leaves are inert data.

So 5 of the 15 R08 leaves (4 x `start`, plus `dayByDay`) merge as data with no enforcement path. Merge
them anyway for parity, but do not claim they change behaviour.

## R07 - 108 quests have no `lvmax`, so they stay startable past v84's level cap

Node path `Quest.wz/Check.img/<id>/0/lvmax`. The 108 quest ids:

- **28162 through 28266** inclusive (105 ids)
- **28282**, **28283**, **28325**

**Verified exactly.** All 105 integers in 28162..28266 exist as `Check.img/<id>/0/lvmax` in the
carve, contiguous with no gaps, as do the three outliers. All 108 are absent from
`wz/Quest.wz/Check.img.xml`. Nothing is already done. The carve holds 433 `lvmax` leaves and ours
holds 327; the set difference is exactly these 108 and nothing outside the list.

**Ours additionally has `28002/0/lvmax` and `28004/0/lvmax`, which the carve lacks.** They are
v83-only and not this ticket's business - but the "do not rewrite existing ids" rule has to protect
them, so a merge that reconciles our file to the carve wholesale is wrong.

Reader: `server/quest/QuestRequirementType.java:74` (`case "lvmax":` -> `MAX_LEVEL`), wired at
`Quest.java:566-568` to `MaxLevelRequirement.java`. `docs/wz-baseline/add-list/Quest.txt` lists
exactly 108 `lvmax` paths at lines 381-488, all inside this id set and none outside.

The range is described elsewhere as "the job-instructor training line". That is true at the start -
`QuestInfo.img.xml:9684` gives 28162 = "Meeting the Training Instructor" - but not across the whole
range: `:11032` gives 28266 = "Secret of Astaroth", `:11197` 28282 = "How to Avoid the Stink",
`:11477` 28325 = "Dirty Treasure Map". Harmless to the merge; do not rely on the description to
decide which ids belong.

## R08 - 15 date and repeat leaves are missing

| quest | missing leaves | carve values |
|---|---|---|
| **2208** | `0/start`, `0/end`, `0/interval` | 200801010000 / 200801020000 / 1440 |
| **2209** | `0/start`, `0/end`, `0/interval` | 200801010000 / 200801020000 / 1440 |
| **2210** | `0/start`, `0/end`, `0/interval` | 200801010000 / 200801020000 / 1440 |
| **2211** | `0/start`, `0/end`, `0/interval` | 200801010000 / 200801020000 / 1440 |
| **10109** | `0/interval` | 1440 |
| **3845** | `0/end` | 2010010100 |
| **9260** | `0/dayByDay` | 1 |

That is 12 + 3 = **15**. All 15 confirmed absent from our tree and present in the carve.

Readers: `QuestRequirementType.java:84` (`case "interval":` -> `INTERVAL`, wired at
`Quest.java:558-560` to `IntervalRequirement.java`) and
`server/quest/requirements/EndDateRequirement.java` for `end` only. See the header for what `start`
and `dayByDay` actually do, which is nothing.

### Merging `end` permanently retires quests 2208-2211

**This is the whole risk surface and it is worse than a closing window.** The carve gives all four
`end = 200801020000` - January 2008. `EndDateRequirement` refuses anything past its end date, and
`Quest.canStart` (`Quest.java:288-302`) refuses on the **first** unmet start requirement. Once `end`
lands, 2208-2211 become **permanently unstartable for everyone**, in exactly the way ticket 44's
1048-1054 are retired.

Only **2208** is "Bartol's Requests" (`wz/Quest.wz/QuestInfo.img.xml:6913-6914`). The other three are
separate quests in the chain Bartol starts: `:6920` 2209 "Bring a Lemon for Shulynch", `:6927` 2210
"Take the Gold Pouch to Muirhat", `:7034` 2211 "Deliver the Tattered Map to Black Bark". They are
live v83 quests a player can be part-way through. (Quest names live in
`wz/Quest.wz/QuestInfo.img.xml`; there is no `wz/String.wz/Quest.img.xml` in this tree.)

A consequence for the tests: because `end` refuses first, the `interval` on 2208-2211 can never be
reached through `canStart`. **An acceptance criterion asserting interval behaviour for those four ids
is unreachable and has been removed.** 10109 is the only id where `interval` is testable.

## Precedent

- `docs/wz-baseline/add-list/Quest.txt` enumerates every path in both rows - 108 `lvmax` at lines
  381-488, 15 R08 paths at 203, 244-252, 263-265, 522, 523.
- Values come from the pristine carve, `SOURCES.md` tier 1, at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\Quest.wz` - a **sibling of this repo**,
  not a subdirectory (`docs/work-plan/SOURCES.md:12-19`). **Read-only.** Read it with
  `docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly two
  subcommands: **`dump`** and **`scan`**.
- **The additive-merge precedent is commit `8c24b6fa5`** (`wz/String.wz/Eqp.img.xml | 6 ++++++`, zero
  deletions). Copy that shape: additive, leaf by leaf, no existing id rewritten.
- **Commits `434c5cba5` and `32fa7879f` are NOT additive-merge precedent** and were previously cited
  here as if they were. `434c5cba5` "Halve the v84-era quest requirements the merges brought in
  unhalved" **rewrites existing leaf values** - `wz/Quest.wz/Act.img.xml` 104 lines,
  `Check.img.xml` 142 lines, 301 insertions against 137 deletions. `32fa7879f` "Quest halving: the
  v84 client never got the client half of it" touches **no `wz/` file at all** (Program.cs, .csproj,
  one test). They are the wrong shape to copy.
- `EndDateRequirement` is already the live enforcement path for the expired quests documented in
  ticket 44. **That is seven quests - 1048 through 1054**
  (`docs/work-plan/tickets/44-medal-quests-and-orphan-quest-scripts.md:23-33`), not 327. The figure
  327 was wrong here and is wrong in `V84-WORK-ROWS.tsv:9`; it is the count of `lvmax` leaves
  currently in our `Check.img.xml`, which is a different number about a different thing.

## Acceptance criteria

- [ ] All 108 `Check.img/<id>/0/lvmax` leaves exist in `wz/Quest.wz/Check.img.xml`, and each value
      equals the carve's value for that id. A per-id assertion, not an aggregate count.
- [ ] All 15 leaves in the R08 table exist with the carve's values.
- [ ] `28002/0/lvmax` and `28004/0/lvmax` are unchanged and still present.
- [ ] A `*RealLoad` test asserts that a character above the cap is refused for a named sample of the
      108 (at minimum 28162, 28266, and the three outliers 28282, 28283, 28325) and accepted below
      it - exercising `QuestRequirementType` for real, not asserting the XML twice.
- [ ] The same test asserts `interval` is honoured for **10109**, and that `EndDateRequirement`
      refuses 3845 outside its window.
- [ ] The same test asserts 2208-2211 are refused after the merge, i.e. it pins the retirement as
      intended behaviour rather than letting it surprise someone later.
- [ ] The effect on 2208-2211 for a character mid-quest is stated in this ticket's Delivered section
      before the merge lands: what a `queststatus` row in state 1 does when its quest gains
      `start`/`end`/`interval`, given that `start` is discarded and `end` refuses.
- [ ] The Delivered section records that `9260/0/dayByDay` and the four `start` leaves are merged as
      inert data, with the `QuestRequirementType.java:108` case-mismatch noted so the next person
      does not re-derive it.
- [ ] Test class named here, invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire's
      `default-test` execution **excludes** `*RealLoad` at `pom.xml:239`
      (`<exclude>**/*RealLoad.java</exclude>`); the **include** is in the separate `real-load-tests`
      execution at `pom.xml:272-274`. **Do not run maven while sibling agents are active.**

The old criterion "re-run `python tools/playthrough/v84coverage.py` and watch the Quest GAP count
drop by 122" has been removed. The number was wrong (123), and the script **writes**
`docs/work-plan/V84-COVERAGE.tsv` in full (`tools/playthrough/v84coverage.py:231`), which this ticket
does not own.

## Do not

- Do not fix `QuestRequirementType.java:108` as part of this ticket. Making `dayByDay` live is a
  behaviour change with its own blast radius; record it and let it be filed separately.
- Do not derive a `lvmax` from the quest's `lvmin` or from a sibling quest. Every one of the 108
  values is in the carve; read it.
- Do not delete or modify a `queststatus` row to make a date window fit. Data repair on live
  characters is a separate, owner-gated decision.
- Do not extend the merge past the paths in `add-list/Quest.txt`. The 138 client-only and 5 cosmetic
  `QuestInfo.img` rows in `V84-COVERAGE.md` are out of scope.
- Do not run `v84coverage.py` as part of this ticket. It rewrites a tracker file another agent owns.
- Do not compare positionally. `Check.img` children are keyed by quest id, but the `0` block's
  leaves must still be matched by name.
- Do not drop an empty-valued leaf while copying. Empty is not absent.
