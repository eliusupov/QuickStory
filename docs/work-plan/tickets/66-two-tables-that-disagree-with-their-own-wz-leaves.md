# 66 - The monster card table disagrees with its own WZ leaves

**Class:** **R20 is v83 legacy, NOT a v84 parity gap** (zero `add-list` rows on any of its seven
items - it is a data defect that predates the cutover). R19 is closed as a refusal; see below.
**Work rows:** R19, R20 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None. The additive-only rule is answered from the data below.

Seven `monstercarddata` rows name a different mob than the WZ leaf they were built from. Every one
is a one-field correction with the correct value sitting in `Item.wz`. R19, which used to sit
alongside them, is **withdrawn** - one of its two rows would have shipped a regression.

## The additive-only question, answered from the data

The standing rule for database changes is additive-only, and R20 is seven `UPDATE`s. It ships
anyway, because:

* `monstercarddata` has no additive expression of "this row is wrong". An `INSERT` of the correct
  pairing leaves the wrong one in place and the table would name two mobs for one card.
* This project has already made this exact call three times. changeSets **164**, **165** and **167**
  each correct already-applied rows in place rather than editing a frozen changeSet. The rule is
  "do not edit a frozen changeSet", not "never write an `UPDATE`".
* R20 changes no loot visibility and no player-facing gate - it corrects which mob a card is
  attributed to in the monster book.

The row that *did* change loot visibility for live characters is R19, and it is refused below on its
own evidence rather than on the additive rule.

## R19 - WITHDRAWN. One row is a regression and the other is unsupported

The earlier revision proposed two `drop_data` `UPDATE`s adding a `questid` "their siblings carry".

### 4031405 / dropper 9500108 -> questid 8732: **refused, this would break the item**

**Quest 8732 does not exist.** Measured across all three quest archives:

```
grep -c 'name="8732"' wz/Quest.wz/QuestInfo.img.xml wz/Quest.wz/Check.img.xml wz/Quest.wz/Act.img.xml
-- 0, 0, 0
```

`wz/Item.wz/Etc/0403.img.xml:4892` sets `info/quest = 1` on 4031405, so the gate is live. With a
`questid` of 8732 the pickup path runs `Character.needQuestItem:5825`:

```java
int amountNeeded, questStatus = this.getQuestStatus(questid);
if (questStatus == 0) {
    amountNeeded = Quest.getInstance(questid).getStartItemAmountNeeded(itemid);
    if (amountNeeded == Integer.MIN_VALUE) {
        return false;
    }
```

A quest with no data returns `Integer.MIN_VALUE`, so `needQuestItem` returns **false for every
character, permanently**. Applying this UPDATE makes 4031405 unlootable from mob 9500108 forever.

**This repo already rejected that row as evidence.** `156-evan-chain-drop-data.sql:186-187`:

> `(3110100, 4031405, 1, 1, 8732, 500000)   quest 8732 has NO QuestInfo entry at all -> a`
> `                                          non-GMS addition, rejected as a precedent`

The "sibling row already carries 8732" argument is therefore backwards: the sibling is the row this
project already refused to trust. **No UPDATE ships for 4031405.**

### 4031568 / dropper 2110301 -> questid 3911: **not shipped here**

This row is not a regression - quest 3911 exists - but it is the only survivor of a pair, its
evidence is the same "a sibling row does it" argument that just failed on the other half, and it
changes loot visibility for live characters. It does not travel with R20, which is a v83-legacy
attribution fix with no gate consequence. **Re-file it on its own if it is wanted**; do not attach
it to this changeSet.

## R20 - seven `monstercarddata` rows name a different mob than the WZ leaf does

`wz/Item.wz/Consume/0238.img.xml` `info/mob` is the authority. All seven line numbers and all seven
values were re-read for this revision and every one is exact:

| card | table has | should be | `0238.img.xml` line |
|---|---|---|---|
| 2383045 | 6130102 | **6130103** | `:5265` |
| 2388011 | 9300105 | **9300119** | `:9180` |
| 2388017 | 6400006 | **8150000** | `:9370` |
| 2388026 | 6400008 | **8130100** | `:9653` |
| 2388043 | 8820001 | **8820000** | `:9996` |
| 2388068 | *3300006 - swapped with 2388069* | **3300007** | `:10202` |
| 2388069 | *3300007 - swapped with 2388068* | **3300006** | `:10231` |

All seven items have **zero `add-list` rows**, so this is v83 content with a stale row, not a v84
parity gap.

### `drop_data` is stale too, and a `monstercarddata`-only fix leaves the two tables disagreeing

The earlier revision said "`drop_data` already carries the WZ-correct pairing for four of the seven
(changeSet 160), so only `monstercarddata` is stale." **Three errors in one sentence.** Measured:

* It is **five**, not four.
* The rows are in changeSet **152** (`152-drop-data.sql`), not 160. `160-monsterbook-drop-data.sql`
  contains **none** of these seven ids.
* `drop_data` **also still carries every stale pairing**, alongside the correct one.

| card | correct pairing in 152 | stale pairing also in 152 |
|---|---|---|
| 2383045 | `:5032` (6130103) | `:5000` (6130102) |
| 2388011 | `:10481` (9300119) | `:17523` (9300105) |
| 2388017 | `:10486` (8150000) | `:19186` (6400006) |
| 2388026 | `:10493` (8130100) | `:17957` (6400008) |
| 2388043 | `:19975` (8820000) | `:11817` (8820001) |
| 2388068 | **none** | `:9482` (3300006) - only the swapped pairing exists |
| 2388069 | **none** | `:9556` (3300007) - only the swapped pairing exists |

So correcting `monstercarddata` alone leaves `drop_data` naming both mobs for five cards and the
wrong mob for the 2388068/2388069 pair. **State this in the changeSet header.** Whether to also
correct `drop_data` is a separate, larger decision - those rows are live drop sources and removing
one changes what a mob drops - and this ticket does not make it. It only refuses to pretend the
inconsistency is not there.

## Precedent

* `wz/Item.wz/Consume/0238.img/<id>/info/mob` is the leaf the client itself reads.
* Shape of the change: a new Liquibase changeSet, the way changeSets 164, 165 and 167 corrected
  already-applied rows in place rather than editing a frozen changeSet.
* Refusing a row because its quest has no `QuestInfo` entry: `156-evan-chain-drop-data.sql:186-187`,
  on this very item.

## Acceptance criteria

- [ ] A new Liquibase changeSet carries exactly **seven** `UPDATE`s, all on `monstercarddata`, and no
      `INSERT`s and no `DELETE`s. **No `drop_data` UPDATE appears in it.**
- [ ] Its header names, per row, the `Item.wz` line the value came from, and states that all seven
      are v83 legacy rather than v84 parity.
- [ ] Its header records that `drop_data` still carries the five stale pairings and the swapped
      2388068/2388069 pair, with the `152-drop-data.sql` line numbers above, so the next agent does
      not read this changeSet as having made the two tables agree.
- [ ] Its header records that R19 was withdrawn, and why: quest 8732 has no data in any
      `wz/Quest.wz` archive, `needQuestItem` returns false for a quest with no data, and
      `156-evan-chain-drop-data.sql:186-187` already rejected that row.
- [ ] After it runs, all seven `monstercarddata` rows equal the `info/mob` value at the `Item.wz`
      line cited above - checked by a test or a script that reads both sides, not by eye.
- [ ] The 2388068 / 2388069 pair is asserted as a pair, so a half-applied swap fails the check.
- [ ] Row counts in `monstercarddata` are unchanged before and after, and `drop_data` is untouched.

## Do not

- Do not apply the 4031405 / 8732 UPDATE. It makes the item permanently unlootable from that mob.
- Do not cite "the sibling row already carries 8732" as evidence for anything. That sibling is the
  row changeSet 156 refused.
- Do not attach the 4031568 / 3911 row to this changeSet. Re-file it separately if it is wanted.
- Do not make `Character.needQuestItem` reject `questid <= 0` instead. That path also serves ordinary
  loot (`MapleMap.java:1329`) and the change would make everything invisible.
- Do not "fix" `drop_data` in this changeSet to make the tables agree. Those are live drop sources;
  record the inconsistency and leave the decision to its own ticket.
- Do not edit a frozen changeSet. Correct in place with a new one.
