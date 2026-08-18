# 59 - the six Maker recipes, the 16 mob-skill nodes, and the 44th reward entry

**Class:** v84 parity
**Work rows:** R09, R12, R13 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Three small leaf merges into three different archives, grouped because they are the same edit: a
node v84 added, a server reader that already opens it, and a value sitting in the pristine carve.
Together they are 24 nodes. Two are inert today (the Maker recipes change no behaviour until the
fetcher runs; mob skill 137 is referenced by no mob); the other two are live - mob skill levels
affect combat, and two reward boxes currently roll a short table.

## R09 - the six v84 Maker recipes are absent from `ItemMake.img`

`wz/Etc.wz/ItemMake.img.xml` is still the v83 file. Missing ids, under groups **0** and **2**:

**1142156, 1142157, 1942002, 1952002, 1962002, 1972002.**

Reader: `ItemInformationProvider.java:2258`, which reads only `catalyst` at
`ItemInformationProvider.java:2262`.

`add-list/Etc.txt:10475-10480` names exactly these six paths and nothing else under `ItemMake`, and
they match `158-maker-v84-data.sql:73-78` id for id. The leaf values are already recorded in that
changeSet's header comment at `158-maker-v84-data.sql:69-71`, so nothing here needs deriving. No
behaviour changes today - the consequence is that a `SkillMakerFetcher` run currently drops six rows
instead of reproducing changeSet 158.

## R12 - 15 mob-skill levels and mob skill 137 are absent from `MobSkill.img`

`Skill.wz/MobSkill.img`, missing nodes:

**110/level/10, 114/level/35, 114/level/36, 115/level/2, 123/level/24, 123/level/25, 123/level/26,
125/level/11, 127/level/15, 128/level/15, 133/level/6, 145/level/6, 200/level/177, 200/level/178** -
and **skill 137 entirely.**

Reader: `MobSkillFactory`. `add-list/Skill.txt` carries the paths. Skill 137 is already recorded as
referenced by no mob, so it is inert either way; the 15 levels are not - mob skills affect live
combat, which is this ticket's only real risk.

## R13 - two v84 reward boxes are missing their 44th entry

`Item.wz/Consume/0202.img/02022503/reward/43` and `Item.wz/Consume/0202.img/02022514/reward/43`.
Ours stop at index 42; v84 has 44 entries. Reader: `ItemRewardHandler.java:66`.

`reward` is an array, so index 43 being absent means our array is short. Per ticket 53's rule, a
present index N is **not** proof of equal content - assert the shared indices agree before appending
the new one.

## Precedent

- All three rows: values from the pristine carve at `porting-resources/wz-data/v84/`, read with
  `WzPeek`. `SOURCES.md` tier 1, **read-only**.
- **R09 needs no carve read at all** - the values are already in this repo, in the header comment of
  `158-maker-v84-data.sql:69-71`, and the six ids are already in `158-maker-v84-data.sql:73-78`.
  That changeSet is the precedent and the value source in one.
- **R12** and **R13**: `add-list/Skill.txt` and `add-list/Item.txt` name the paths; the values are in
  the carve.
- **Ticket 53** is the precedent for how array divergence is actually resolved, and governs R13.

## Acceptance criteria

- [ ] `wz/Etc.wz/ItemMake.img.xml` carries 1142156, 1142157, 1942002, 1952002, 1962002 and 1972002
      under groups 0 and 2, with the leaf values from `158-maker-v84-data.sql:69-71`.
- [ ] A `SkillMakerFetcher` run over the edited tree reproduces changeSet **158** in full - all rows,
      including the six - rather than dropping six. Assert on the produced row set, not on a count.
- [ ] `wz/Skill.wz/MobSkill.img.xml` carries all 15 named `<id>/level/<n>` nodes plus skill **137**,
      each value matching the carve leaf for leaf.
- [ ] A `*RealLoad` test asserts `MobSkillFactory` returns a non-null skill for each of the 15
      `(id, level)` pairs, and that the pre-existing levels of skills 110, 114, 115, 123, 125, 127,
      128, 133, 145 and 200 still resolve with their current values.
- [ ] `02022503/reward` and `02022514/reward` each have 44 entries, indices 0-43 consecutive, and
      indices 0-42 are byte-identical to what they held before the edit.
- [ ] A test asserts `ItemRewardHandler` sees 44 candidate entries for both box ids.
- [ ] Re-running `python tools/playthrough/v84coverage.py` drops the `Skill` GAP count by 16 and the
      `Item` GAP count by 2.
- [ ] Test classes named here, invoked as `mvnw.cmd -o test -Dtest=<ClassName>`. Surefire includes
      `*RealLoad` (`pom.xml:239,272-274`), **but do not run maven while sibling agents are active**.

## Do not

- Do not merge anything else under `ItemMake`. `add-list/Etc.txt:10475-10480` is the complete set;
  the rest of that file is the 10,181 benign `Commodity` leaves, which are ticket 60's problem and
  not `ItemMake` at all.
- Do not append to either `reward` array without first proving indices 0-42 match. A short array and
  a divergent array look identical to the coverage tool.
- Do not renumber, reorder or compact any array index. `PortalFactory`-style name-addressed lookups
  and `ItemRewardHandler`'s index walk both break on a shift.
- Do not skip mob skill 137 on the grounds that no mob references it. It is a v84 node the server
  reads; inert is not the same as absent.
- Do not hand-write a Maker recipe value. If it is not in `158-maker-v84-data.sql:69-71` or the
  carve, stop and say so.
