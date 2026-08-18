# 59 - the six Maker recipes, the 14 mob-skill levels, and the 44th reward entry

**Class:** v84 parity
**Work rows:** R09, R12, R13 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Three small leaf merges into three different archives, grouped because they are the same edit: a
node v84 added, and a value sitting in the pristine carve. Together they are **22 nodes** to merge
(6 + 14 + 2), plus one node - mob skill **137** - that is **scoped out** because merging it cannot
work; see R12.

**The earlier counts in this ticket were wrong in four places.** The R12 list has always contained
**14** `<id>/level/<n>` nodes, not 15; `docs/wz-baseline/add-list/Skill.txt:45-59` has **15** lines
total, which is those 14 levels **plus** skill 137 as a whole node; so "16 mob-skill nodes" was 15
and "24 nodes" was 23 (and is now 22 with 137 scoped out). `V84-WORK-ROWS.tsv:13` still says "15
mob-skill levels" and is stale on the same point.

Two rows are inert today (the Maker recipes change no behaviour until the fetcher runs; and see R12
on 137); the mob skill **levels** are live - they affect combat.

## R09 - the six v84 Maker recipes are absent from `ItemMake.img`

`wz/Etc.wz/ItemMake.img.xml` is still the v83 file. Missing ids, under groups **0** and **2** - and
note the node names are **zero-padded to 8 digits**:

* group **0**: `01142156`, `01142157`
* group **2**: `01942002`, `01952002`, `01962002`, `01972002`

All six grep to zero hits in our file; the carve confirms the group placement exactly. **A merge that
writes the bare `1142156` will not be found** - `getMakerStimulant` looks the node up via
`StringUtil.getLeftPaddedStr(..., '0', 8)` (`ItemInformationProvider.java:2264`), and
`src/main/resources/db/data/158-maker-v84-data.sql:20` flags the same thing.

Reader: `ItemInformationProvider.getMakerStimulant` at **`ItemInformationProvider.java:2256`**, whose
`ItemMake.img` loop is at **:2263** and whose only leaf read is `catalyst` at **:2267**. (The earlier
citations of 2258/2262 were off by 5; `V84-WORK-ROWS.tsv:10` still carries the wrong 2258.)

**"A server reader already opens it" overstates the case.** `getMakerStimulant` reads *only*
`catalyst`, and **none of the six carve nodes has a `catalyst` leaf** - so that method returns -1 for
all six before and after the merge. The real consumer is `SkillMakerFetcher`
(`src/main/java/tools/mapletools/SkillMakerFetcher.java:27`), an offline generator. The consequence
of the gap is that a `SkillMakerFetcher` run currently drops six rows instead of reproducing
changeSet 158. Nothing in live gameplay changes.

`docs/wz-baseline/add-list/Etc.txt:10475-10480` names exactly these six paths and nothing else under
`ItemMake` - a grep for `ItemMake` across the whole 10,638-line file returns exactly 6 hits, at those
lines.

### The values are only partly in the changeSet - a carve read is required

The file is `src/main/resources/db/data/158-maker-v84-data.sql` (**not** `database/`). Its ids are at
`:73-78`, one per `VALUES` row, matching the six exactly. But `:69-71` is only **three comment
lines**, holding the scalar leaves:

```
69  -- ItemMake.img/0/01142156 - reqLevel 80, reqSkillLevel 1, itemNum 1, reqItem 4032502, tuc 0, meso 0
70  -- ItemMake.img/0/01142157 - reqLevel 120, reqSkillLevel 1, itemNum 1, reqItem 4032503, tuc 0, meso 0
71  -- ItemMake.img/2/019x2002 - reqLevel 115, reqSkillLevel 3, itemNum 1, tuc 3, meso 300000 (-> 330000)
```

**The `recipe/<n>/{item,count}` sub-trees are not there** - five pairs for 01142156, five for
01142157, four each for the dragon equips - and they are the majority of the nodes to be merged. They
exist in that file only as `makerrecipedata` SQL rows from `:80` on, in a different shape. Two more
traps in line 71: it collapses four ids into a `019x2002` wildcard, and its `meso 300000 (-> 330000)`
records the DB-marked-up value, **not** the WZ leaf, which is 300000.

So the earlier claim that "R09 needs no carve read at all" is false. Derive the scalars from the
comment if you like; the recipe arrays come from the carve or from the `makerrecipedata` rows.

### R09 breaks an existing green test

`src/test/java/server/MakerV84RealLoad.java:38-40` **deliberately asserts the six ids are absent**
from `wz/Etc.wz/ItemMake.img.xml`, "so the day it changes, someone is told to tighten this class."
That assertion must be rewritten in the same commit that lands the merge.

## R12 - 14 mob-skill levels are absent from `MobSkill.img` (and skill 137 is scoped out)

`Skill.wz/MobSkill.img`, missing nodes - **14**, counted:

**110/level/10, 114/level/35, 114/level/36, 115/level/2, 123/level/24, 123/level/25, 123/level/26,
125/level/11, 127/level/15, 128/level/15, 133/level/6, 145/level/6, 200/level/177, 200/level/178.**

Every one is absent from `wz/Skill.wz/MobSkill.img.xml` and present in the carve. Our per-skill level
ranges stop exactly one short in each case (110: 1-9, 114: 1-34, 115: 1-1, 123: 1-23, 125: 1-10,
127: 1-14, 128: 1-14, 133: 1-5, 145: 1-5, 200: 1-176), which is a useful sanity check on the list.

Reader: `MobSkillFactory` (`src/main/java/server/life/MobSkillFactory.java`), which reads
`MobSkill.img` at `:48` via `skillRoot.getChildByPath("%d/level/%d")` at `:81`.
`docs/wz-baseline/add-list/Skill.txt:45-59` carries the paths. Mob skills affect live combat, which
is this ticket's only real risk.

### Skill 137: scoped out, and both of the old claims about it were wrong

Skill 137 is absent from ours (we have 41 top-level skill ids) and present in the carve with a single
`level/1`. Merge it and **nothing happens**. Two corrections:

* **"Referenced by no mob" is FALSE.** `wz/Mob.wz/8300003.img.xml:308-309` carries
  `<int name="disease" value="137"/>` with `<int name="level" value="1"/>`. Mob 8300003 is "Soaring
  Blue Wyvern" (`wz/String.wz/Mob.img.xml:87-88`). It is a live path:
  `MobAttackInfoFactory.java:65-73` reads `disease`/`level` off the attack node, and
  `TakeDamageHandler.java:165-166` calls `MobSkillType.from(attackInfo.getDiseaseSkill())` ->
  `MobSkillFactory.getMobSkillOrThrow(type, level)`. The reference is via `attack<N>/info/disease`,
  not `info/skill` - a scan of `info/skill` across all of `wz/Mob.wz` finds zero hits for 137, which
  is presumably how the old negative was reached.
* **Merging it is nonetheless inert, because `MobSkillType` has no 137 constant.**
  `src/main/java/server/life/MobSkillType.java:32-33` jumps `FEAR(136)` -> `PHYSICAL_IMMUNE(140)`.
  `MobSkillFactory.getMobSkill` (`:59`) is keyed on the enum, so `MobSkillType.from(137)` returns
  empty, `.map(...)` short-circuits, and the wyvern's disease silently does nothing - before and
  after the merge.

**So do not include 137 in this ticket's acceptance criteria.** Making it work needs a new
`MobSkillType` constant plus its `Disease` mapping, which is a code change with its own review.
Merge the WZ node if you want the archive complete, but record it as inert and file the enum work
separately. The old criterion asserting `MobSkillFactory` returns non-null for 137 was
**unsatisfiable**.

## R13 - two v84 reward boxes are missing their 44th entry

`Item.wz/Consume/0202.img/02022503/reward/43` and `Item.wz/Consume/0202.img/02022514/reward/43`.
Verified on both sides: ours has **43** children (indices 0-42) for each; the carve has **44**
(indices 0-43). Reader: `ItemRewardHandler.java:66`
(`Pair<Integer, List<RewardItem>> rewards = ii.getItemReward(itemId);`) - line number exact.
`docs/wz-baseline/add-list/Item.txt:143-144` is exactly the two `reward/43` paths.

Per ticket 53's rule, a present index N is **not** proof of equal content - assert the shared indices
agree before appending the new one.

**There is no index walk to break, though.** `ItemInformationProvider.java:1737` is
`for (Data child : getItemData(itemId).getChildByPath("reward").getChildren())` - an unordered
children iteration that never reads the index name. The earlier "`ItemRewardHandler`'s index walk
breaks on a shift" was wrong about the mechanism. Keeping the indices stable is still correct for a
readable diff; it is not a correctness requirement of this reader.

Evidence that document order already diverges from index order: in `wz/Item.wz/Consume/0202.img.xml`
both `02022503/reward` and `02022514/reward` list their children as `0..15, 17..42, 16` - index 16 is
last in the file. The carve does the same thing with index `1`, which it emits after `43`. Do not
"fix" either ordering.

## Precedent

- All three rows: values from the pristine carve at the absolute path
  `D:\games\MapleStory\Server\porting-resources\wz-data\v84\` - a **sibling of this repo**, not a
  subdirectory (`docs/work-plan/SOURCES.md:14`). `SOURCES.md` tier 1, **read-only**. Read it with
  `docs/wz-baseline/tool-peek/bin/Release/net10.0-windows/WzPeek.exe`, which has exactly two
  subcommands: **`dump`** and **`scan`**.
- **R09's scalar values** are in `src/main/resources/db/data/158-maker-v84-data.sql:69-71` and its ids
  at `:73-78`; **the recipe arrays are not** - see R09 above.
- **R12** and **R13**: `add-list/Skill.txt` and `add-list/Item.txt` name the paths; the values are in
  the carve.
- **Ticket 53** is the precedent for how array divergence is actually resolved, and governs R13.
- The additive-merge shape to copy is commit **`8c24b6fa5`** (`wz/String.wz/Eqp.img.xml | 6 ++++++`,
  zero deletions). `434c5cba5` and `32fa7879f` are quest-*halving* commits that rewrite existing leaf
  values, not additive merges - do not copy them.

## Acceptance criteria

- [ ] `wz/Etc.wz/ItemMake.img.xml` carries `01142156` and `01142157` under group 0 and `01942002`,
      `01952002`, `01962002`, `01972002` under group 2 - **zero-padded node names** - with the scalar
      values from `158-maker-v84-data.sql:69-71` and the `recipe` arrays from the carve or from that
      file's `makerrecipedata` rows.
- [ ] The `meso` leaf for the four `019x2002` ids is **300000**, the WZ value, not the 330000 the
      changeSet comment records as the marked-up figure.
- [ ] `MakerV84RealLoad.theRecipeIsNotInThisTreesItemMakeYet()`
      (`src/test/java/server/MakerV84RealLoad.java:38-40`) is rewritten to assert presence, and passes.
- [ ] A `SkillMakerFetcher` run over the edited tree reproduces changeSet **158** in full - all rows,
      including the six - rather than dropping six. Assert on the produced row set, not on a count.
- [ ] `wz/Skill.wz/MobSkill.img.xml` carries all **14** named `<id>/level/<n>` nodes, each value
      matching the carve leaf for leaf.
- [ ] A `*RealLoad` test asserts `MobSkillFactory` returns a non-null skill for each of the 14
      `(id, level)` pairs, and that the pre-existing levels of skills 110, 114, 115, 123, 125, 127,
      128, 133, 145 and 200 still resolve with their current values. **Skill 137 is not in this
      criterion** - see R12.
- [ ] If skill 137's node is merged, the Delivered section records that it is inert, names mob
      8300003 as its real referrer, and files the `MobSkillType` enum gap as separate work.
- [ ] `02022503/reward` and `02022514/reward` each have 44 entries, indices 0-43 present, and indices
      0-42 are byte-identical to what they held before the edit.
- [ ] A test asserts `ItemRewardHandler` sees 44 candidate entries for both box ids.
- [ ] Test classes named here, invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire's
      `default-test` execution **excludes** `*RealLoad` at `pom.xml:239`
      (`<exclude>**/*RealLoad.java</exclude>`); the **include** is in the separate `real-load-tests`
      execution at `pom.xml:272-274`. **Do not run maven while sibling agents are active.**

The old criterion "re-run `python tools/playthrough/v84coverage.py` and watch the Skill GAP count
drop by 16 and the Item GAP count by 2" has been removed. The Skill number was wrong (the add-list
carries 15 roots, and only 14 are being merged if 137 is scoped out), and the script **writes**
`docs/work-plan/V84-COVERAGE.tsv` (`tools/playthrough/v84coverage.py:231`), which this ticket does
not own. The Item half was correct: `add-list/Item.txt:143-144` is exactly two lines.

## Do not

- Do not merge anything else under `ItemMake`. `add-list/Etc.txt:10475-10480` is the complete set;
  the rest of that file is `Commodity`, which is ticket 60's problem and is closed there as a
  non-defect.
- Do not write the six ItemMake ids unpadded. `getMakerStimulant` pads to 8 digits and will miss them.
- Do not add a `MobSkillType` constant for 137 in this ticket. Record the gap; let it be reviewed on
  its own.
- Do not append to either `reward` array without first proving indices 0-42 match. A short array and
  a divergent array look identical to the coverage tool.
- Do not renumber, reorder or compact any array index, and do not "fix" the existing `0..15, 17..42,
  16` document order.
- Do not hand-write a Maker recipe value. If it is not in `158-maker-v84-data.sql` or the carve, stop
  and say so.
- Do not run `v84coverage.py` as part of this ticket. It rewrites a tracker file another agent owns.
