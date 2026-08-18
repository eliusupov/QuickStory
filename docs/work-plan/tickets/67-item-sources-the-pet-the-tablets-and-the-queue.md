# 67 - Item sources: the sourceless pet, the contradicted tablet dropper, and the 127-row queue

**Class:** v84 parity
**Work rows:** R18, R49, R50 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** **OWNER Q2** (R18) and **OWNER Q7** (R49). R50 needs no decision - it is a standing
queue and can be worked entry by entry today.

Three sourcing questions that share one seam and one rule: **v84 never shipped drop tables**, so
every source in this project is either a row v84's own data implies, an analogue row copied verbatim,
or nothing at all. `SOURCES.md` ranks those; read it before touching any of the three. The failure
mode this ticket exists to prevent is a plausible-looking row invented from a name.

**Zero live blockers.** Exactly 3 of the 273 unsourced rows are wanted by a quest without an expired
end date, and all 3 were settled by the quest sweep - the Mesoranger medals behind GM-only quest
19011, and two Cassandra items in the retired Korean 1049x block.

## R18 - pet 5000067 has no source while its food is on sale

* Pet **5000067**: 0 rows in `drop_data`, 0 in `shopitems`, 0 in `reactordrops`; no `ItemId` match in
  `Etc.wz/Commodity.img`; no grant in `Quest.wz/Act.img`.
* Its food **5240028** "Dynamite" (`wz/Item.wz/Cash/0524.img.xml:405-408`, `spec/0=5000067`) is
  purchasable on four `OnSale=1` SNs: **10002346, 10002347, 60200078, 60200079**.
* Both are v84-new whole items: `add-list/Item.txt:381` and `:98`.

v84 ships the pet and its food and states no source for the pet. That is the same shape as Iron Hook
(work row R34, ticket 70), and the correct default is to **close it as v84-faithful with the
reasoning recorded**. If the owner wants it purchasable instead, the precedent is the six local SNs
**60001000-60001005**, all `Period 90`, `OnSale 1`.

## R49 - two of our own changeSets disagree about mob 8300007

* Items **2047000 / 2047001 / 2047002** and **2047100 / 2047101 / 2047102** have rows in
  `160-monsterbook-drop-data.sql:282-287`, all on mob **8300007 "Dragon Rider"**.
* Mob 8300007 is **placed on no map**.
* `153-crimson-sky-drop-data.sql:39` records a deliberate refusal to table 8300007.
* The ten accessory tablets **2047300-2047309** have no row anywhere, and their only analogue is
  those six.

The two changeSets contradict each other and **no data settles it**. Because 8300007 is unplaced, the
six rows are inert either way, so this is a bookkeeping contradiction, not a live defect. The real
fix for the tablets is a spawner, not a drop row.

## R50 - the 127-item queue

Full enumeration with per-item verdict, bucket and evidence:
`docs/work-plan/V84-ITEM-SOURCE-SWEEP.tsv` (273 rows). Regenerate with
`python tools/playthrough/itemsweep.py` - **re-run it, do not re-derive it.**

The real split of the **394** v84-new items (the old tracker's "332" is unreproducible from any
defensible reading of `add-list`): 121 SOURCED, **127 PARITY_GAP**, 37 CONTAINER_ONLY, 15 CASH_ONLY,
46 NO_SOURCE_IN_V84, 48 cosmetic hair.

**No new drop row is proposed by the sweep and none should be added from it** - the analogue rule has
no applicable case across the 127.

## Precedent

* `SOURCES.md` "Deriving values we cannot look up": copy a real analogue row verbatim replacing only
  the id; take a Tier 3 rate only where the item exists in both versions; **never invent**.
* Analogue rule applied in practice: `153-crimson-sky-drop-data.sql` states it in its header;
  `168-evan-book-drop-data.sql` and `170` applied it; ticket 43's rate ladder (R1/R2/R3) is the
  worked example.
* Circular provenance trap, from `SOURCES.md`: quest 22529's drop row was authored by reading the
  quest's own mob token and then cited as evidence the row was right.
* If R18 becomes a shop entry: SNs 60001000-60001005.

## Acceptance criteria

- [ ] R18 closes one of exactly two ways, and the way it closed is written into this ticket:
      **(a)** closed as v84-faithful with no row added and the Iron Hook parallel recorded, or
      **(b)** a `shopitems`/`Commodity` entry whose `Period` and `OnSale` are copied from
      60001000-60001005 and whose header names that copy.
- [ ] R49's contradiction is resolved in one direction and written down in the resolving changeSet's
      header, so the next agent does not re-derive it - either the six rows on 8300007 stay and
      `153`'s refusal note is corrected, or the six are removed and `160`'s rationale is corrected.
- [ ] No row for **2047300-2047309** is added by copying the six. Asserted by a test or a grep over
      `database/` that finds zero rows for those ten ids.
- [ ] `python tools/playthrough/itemsweep.py` runs clean and reproduces the 273-row sweep, and the
      bucket counts in `V84-ITEM-SOURCE-SWEEP.md` still read 121 / 127 / 37 / 15 / 46 / 48.
- [ ] Any entry worked out of the 127 names, in its changeSet header, the analogue row it copied and
      the mob family or level band that justified the copy.
- [ ] `drop_data` row count changes by exactly the number of rows the ticket says it added, and by
      zero if the ticket adds none.

## Do not

- Do not invent a drop row for pet 5000067. v84 ships it with no source; that is an answer, not a
  gap.
- Do not add rows for 2047300-2047309 by copying the six tablet rows. That propagates the same
  unplaced dropper and manufactures the appearance of a source.
- Do not treat a quest's mob token as a drop table, and do not corroborate a row with the token it
  was derived from.
- Do not re-derive the sweep by hand. It has been derived three times; re-run the script.
- Do not edit `V84-ITEM-SOURCE-SWEEP.tsv`, `V84-ITEM-SOURCE-ROWS.tsv`, `V84-COVERAGE.*`,
  `V84-WORK-ROWS.tsv`, `STATUS.md` or `TICKET-LEDGER.tsv` - other agents own them. Regenerate the
  sweep instead.
