# 67 - Item sources: the sourceless pet, the contradicted tablet dropper, and the 127-row queue

**Class:** v84 parity
**Work rows:** R18, R49, R50 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None. R18 and R49 are decided from the data below. R50 is a standing queue and can be
worked entry by entry today.

Three sourcing questions that share one seam and one rule: **v84 never shipped drop tables**, so
every source in this project is either a row v84's own data implies, an analogue row copied verbatim,
or nothing at all. `SOURCES.md` ranks those; read it before touching any of the three. The failure
mode this ticket exists to prevent is a plausible-looking row invented from a name.

**Zero live blockers.** Exactly 3 of the 273 unsourced rows are wanted by a quest without an expired
end date, and all 3 were settled by the quest sweep - a Mesoranger medal behind GM-only quest 19011,
and two Cassandra items in the retired Korean 1049x block.

## R18 - DECIDED: pet 5000067 closes as v84-faithful, no row added

* Pet **5000067**: 0 rows in `drop_data`, 0 in `shopitems`, 0 in `reactordrops`; no `ItemId` match in
  `Etc.wz/Commodity.img`; no grant in `Quest.wz/Act.img`.
* Its food **5240028** "Dynamite" (`wz/Item.wz/Cash/0524.img.xml:405-408`, `spec/0=5000067`) is
  purchasable on four `OnSale=1` SNs: **10002346, 10002347, 60200078, 60200079**.
* Both are v84-new whole items: `add-list/Item.txt:381` and `:98`.

**v84 ships the pet and its food and states no source for the pet. That is an answer, not a gap.**
The same shape as Iron Hook (work row R34, ticket 70), and it closes the same way: **no row is
added**, and the reasoning is recorded here and in nothing else. Adding a source would be inventing
one from the fact that the food is purchasable, which is exactly the failure mode `SOURCES.md` names.

If the owner later wants it purchasable, the precedent is the six local SNs **60001000-60001005**,
all `Period 90`, `OnSale 1` - but that is a new request, not this row.

## R49 - DECIDED: the six rows stay, and there are seventeen of them

Two of our own changeSets disagree about mob **8300007** "Dragon Rider":

* `160-monsterbook-drop-data.sql:270-287` tables 8300007.
* `153-crimson-sky-drop-data.sql:39-40` records a refusal: *"8300007 (Dragon Rider) gets no table: no
  map in scope places it and no v84 mob-skill summon list names it, so nothing can kill it. Add one
  when a spawner exists."*

**The contradiction is smaller than it looks and the framing was wrong.** 153's refusal is explicitly
scoped - *"no map **in scope**"* - so it declined to table 8300007 *for the Crimson Sky work*, not
in general. 160 later tabled it. Those are compatible statements, and 160 is the later and more
specific one.

**Decision: the rows stay. `153`'s note is annotated, not reverted.** Mob 8300007 is placed on no
map, so every row on it is inert either way; removing 17 working rows to satisfy a scoped aside in an
older changeSet buys nothing and loses the table for whenever a spawner does appear.

### It is 17 rows, not six

The earlier revision said "the six rows" and scoped its acceptance criterion to six. Measured
against the live database and against the changeSet:

```
SELECT COUNT(*) FROM drop_data WHERE dropperid = 8300007;   -- 17
```

`160-monsterbook-drop-data.sql:271-287`, all seventeen:

| line | item |
|---|---|
| `:271` | 2000005 |
| `:272-276` | 2049100, 2049201, 2049203, 2049205, 2049207 |
| `:277-281` | 2040502, 2040505, 2040514, 2040517, 2040534 |
| `:282-287` | 2047000, 2047001, 2047002, 2047100, 2047101, 2047102 |

The six weapon tablets at `:282-287` are the ones R49 was written about; the other eleven sit on the
same mob under the same contradiction and were never mentioned. **All seventeen are resolved by this
decision, not six.**

### The ten accessory tablets stay sourceless

**2047300-2047309** have no row anywhere and their only analogue is those six. They do not get one:
copying the six would propagate the same unplaced dropper and manufacture the appearance of a source.
The real fix for the tablets is a spawner, not a drop row.

## R50 - the 127-item queue

Full enumeration with per-item verdict, bucket and evidence:
`docs/work-plan/V84-ITEM-SOURCE-SWEEP.tsv` (273 rows). Regenerate with
`python tools/playthrough/itemsweep.py` - **re-run it, do not re-derive it.**

The real split of the **394** v84-new items (the old tracker's "332" is unreproducible from any
defensible reading of `add-list`): 121 SOURCED, **127 PARITY_GAP**, 37 CONTAINER_ONLY, 15 CASH_ONLY,
46 NO_SOURCE_IN_V84, 48 cosmetic hair.

The TSV holds only the non-SOURCED rows - `127 + 48 + 46 + 37 + 15 = 273` - and `121 + 273 = 394`.
The two numbers are consistent; do not "fix" one to match the other.

**No new drop row is proposed by the sweep and none should be added from it** - the analogue rule has
no applicable case across the 127.

## Precedent

* `SOURCES.md` "Deriving values we cannot look up": copy a real analogue row verbatim replacing only
  the id; take a Tier 3 rate only where the item exists in both versions; **never invent**.
* Analogue rule applied in practice: `153-crimson-sky-drop-data.sql` states it in its header and
  `168-evan-book-drop-data.sql` applied it. **`170-stump-sap-deep-valley-drop-data.sql` is not an
  example of it** - its own header, `:6-9`, says in caps: *"THIS IS A DELIBERATE, OWNER-APPROVED
  DEVIATION FROM A LITERAL READING OF v84's MOB TOKEN. It is not recovered v84 data and must not be
  presented as such."* Only its *rate* is analogue-derived. Cite 168, not 170.
* Circular provenance trap, from `SOURCES.md`: quest 22529's drop row was authored by reading the
  quest's own mob token and then cited as evidence the row was right.

## Acceptance criteria

- [ ] R18 is closed with **no row added** and the Iron Hook parallel recorded here. If a
      `shopitems`/`Commodity` entry is ever added instead, its `Period` and `OnSale` are copied from
      60001000-60001005 and its header names that copy - but that is a new request, not this row.
- [ ] R49's resolution is written into a changeSet header or into `153-crimson-sky-drop-data.sql` as
      an annotation: the 8300007 rows stay, 153's refusal was scoped to Crimson Sky and is not a
      general prohibition, and the mob remains unplaced so the rows are inert until a spawner exists.
- [ ] That annotation accounts for **all 17** rows on 8300007, not the six tablets alone, and names
      the `160-monsterbook-drop-data.sql:271-287` range.
- [ ] No row for **2047300-2047309** is added by copying the six. Asserted by a test or a grep over
      `src/main/resources/db/` that finds zero rows for those ten ids.
- [ ] `python tools/playthrough/itemsweep.py` runs clean and reproduces the 273-row sweep, and the
      bucket counts in `V84-ITEM-SOURCE-SWEEP.md` still read 121 / 127 / 37 / 15 / 46 / 48.
- [ ] Any entry worked out of the 127 names, in its changeSet header, the analogue row it copied and
      the mob family or level band that justified the copy.
- [ ] `drop_data` row count changes by exactly the number of rows the ticket says it added, and by
      zero if the ticket adds none.

## Do not

- Do not invent a drop row for pet 5000067. v84 ships it with no source; that is an answer, not a
  gap.
- Do not remove the 8300007 rows. The decision is that they stay; 153's refusal was scoped.
- Do not scope any 8300007 work to six rows. There are seventeen.
- Do not add rows for 2047300-2047309 by copying the six tablet rows. That propagates the same
  unplaced dropper and manufactures the appearance of a source.
- Do not cite changeSet 170 as an analogue-rule precedent. Its own header disclaims that reading. Use
  168.
- Do not treat a quest's mob token as a drop table, and do not corroborate a row with the token it
  was derived from.
- Do not re-derive the sweep by hand. It has been derived three times; re-run the script.
- Do not edit `V84-ITEM-SOURCE-SWEEP.tsv`, `V84-ITEM-SOURCE-ROWS.tsv`, `V84-COVERAGE.*`,
  `STATUS.md` or `TICKET-LEDGER.tsv` - other agents own them. Regenerate the sweep instead.
