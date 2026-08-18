# 66 - Two database tables that disagree with the WZ leaves they were built from

**Class:** mixed by row - **R19 is v84 parity; R20 is v83 legacy, NOT a v84 parity gap** (zero
`add-list` rows on any of its seven items - it is a data defect that predates the cutover).
**Work rows:** R19, R20 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** **OWNER Q3.** Both rows are `UPDATE`s against existing rows, and this project's
standing rule for database changes is additive-only. One decision covers both; R20's own row says to
fold it into R19's.

Nine rows across two tables say something the WZ file next to them contradicts. Every one is a
one-field correction with the correct value already sitting in a sibling row or in `Item.wz`. None of
them needs a derived value, a rate, or a judgement call - only permission to write an `UPDATE`.

## R19 - two `drop_data` rows are missing the questid their siblings carry

| itemid | dropperid | should be questid | the evidence |
|---|---|---|---|
| 4031568 | 2110301 | **3911** | the same item's `2100108` Meerkat row already carries 3911 |
| 4031405 | 9500108 | **8732** | the same item's `3110100` Ligator row already carries 8732 |

All four rows verified against the live database. The consequence today is that both items are
visible and lootable by players who are not on the quest - `Character.needQuestItem` refuses the
pickup for every quest but the one named, and a `questid` of 0 means no gate at all.

**Risk is real and one-directional:** applying this makes two items stop being visible to characters
without the quest. That is the intended v84 behaviour and it is what the sibling rows already do.

## R20 - seven `monstercarddata` rows name a different mob than the WZ leaf does

`Item.wz/Consume/0238.img.xml` `info/mob` is the authority. Our table:

| card | has | should be | Item.wz line |
|---|---|---|---|
| 2383045 | 6130102 | **6130103** | `:5265` |
| 2388011 | 9300105 | **9300119** | `:9180` |
| 2388017 | 6400006 | **8150000** | `:9370` |
| 2388026 | 6400008 | **8130100** | `:9653` |
| 2388043 | 8820001 | **8820000** | `:9996` |
| 2388068 | *swapped with 2388069* | **3300007** | `:10202` |
| 2388069 | *swapped with 2388068* | **3300006** | `:10231` |

`drop_data` already carries the WZ-correct pairing for four of the seven (changeSet 160), so only
`monstercarddata` is stale. **This is v83 content with a stale row, not a v84 parity gap** - all
seven items have zero `add-list` rows.

## Precedent

* For R19: the sibling row on the same item **is** the evidence. Nothing is derived; the value is
  copied from a row this database already holds.
* For R20: `Item.wz/Consume/0238.img/<id>/info/mob` is the leaf the client itself reads, and
  changeSet 160 already treated it as authoritative for `drop_data`.
* Shape of the change: a new Liquibase changeSet, the way changeSets 164, 165 and 167 corrected
  already-applied rows in place rather than editing a frozen changeSet.

## Acceptance criteria

- [ ] A new Liquibase changeSet carries exactly nine `UPDATE`s - two on `drop_data`, seven on
      `monstercarddata` - and no `INSERT`s and no `DELETE`s.
- [ ] Its header names, per row, the sibling row or `Item.wz` line the value came from, and states
      that R20's seven are v83 legacy rather than v84 parity.
- [ ] After it runs, `SELECT questid FROM drop_data WHERE itemid = 4031568 AND dropperid = 2110301`
      returns **3911**, and the 4031405 / 9500108 row returns **8732**.
- [ ] After it runs, all seven `monstercarddata` rows equal the `info/mob` value at the `Item.wz`
      line cited above - checked by a test or a script that reads both sides, not by eye.
- [ ] The 2388068 / 2388069 pair is asserted as a pair, so a half-applied swap fails the check.
- [ ] A character without quest 3911 no longer sees 4031568 drop from mob 2110301; a character with
      it still does.
- [ ] Row counts in both tables are unchanged before and after.

## Do not

- Do not make `Character.needQuestItem` reject `questid <= 0` instead. That path also serves mesos
  and ordinary loot (`MapleMap.java:1329`) and the change would make **everything** invisible.
- Do not apply anything before OWNER Q3 is answered. These are `UPDATE`s against the additive-only
  rule, and R19 changes loot visibility for live characters.
- Do not run the `UPDATE`s directly. Agents are SELECT only; the changeSet is applied by the owner.
- Do not edit a frozen changeSet. Correct in place with a new one.
