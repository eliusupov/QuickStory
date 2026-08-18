# 64 - v84 cash item 5550001 has no handler at all

**Class:** v84 parity
**Work rows:** R17 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Item **5550001** is a v84-new cash item whose three `info` leaves describe a seven-day cash
inventory slot expansion, and nothing in the tree reads any of them. **No precedent exists in this
repo for a timed slot expansion** - that absence is the entire reason this row was split out of
ticket 63 and it must not be folded back in. Effort is medium because the storage shape has to be
chosen and justified before any handler is written.

## R17 - `slotIndex` and `addDay` are read nowhere

| item | node | value |
|---|---|---|
| **5550001** | `info/cash` | **1** |
| **5550001** | `info/slotIndex` | **0** |
| **5550001** | `info/addDay` | **7** |

`add-list/Item.txt:110` makes this a whole v84-new item. There are **zero references to `slotIndex`
or `addDay` anywhere in the tree** - not in `src/main/java`, not in `scripts/`, not in the
packet-validator tools. `slotIndex` names which cash inventory partition grows; `addDay` is the
lifetime in days.

Not obtainable today, so nothing regresses whichever shape is chosen.

## Precedent

**UNKNOWN.** There is no timed slot expansion anywhere in this repo to copy. Permanent slot growth
and expiring cash items both exist separately; nothing combines them.

The implementing agent must choose one of two shapes and state the evidence in the commit:

1. **Permanent slot column.** The existing slot-count columns are incremented and the expiry is
   dropped. Cheapest, and wrong against `addDay=7` unless evidence is found that v84's own client
   ignores the field.
2. **An expiring row.** A dated grant that the slot-count read consults, expiring after `addDay`
   days. Faithful to the data; needs a new persisted row and an expiry sweep.

If neither shape can be evidenced from v84 data or from an existing analogue in this tree, **this
row degrades to an owner decision** and no code is written. Say so plainly in the ticket's closing
note rather than picking one silently - the standing rule is that a value that cannot be derived
makes the row a decision, not an implementation.

## Acceptance criteria

- [ ] The chosen shape - permanent column or expiring row - is named in the commit message together
      with the evidence that selected it, or the ticket is closed as an owner decision with no code.
- [ ] Using 5550001 increases the usable cash inventory slot count for partition `slotIndex=0` by
      the amount the item states, asserted by a test that reads the slot count before and after.
- [ ] If the expiring shape is taken: a grant whose timestamp is more than `addDay=7` days old no
      longer counts toward the slot total, asserted by a test that sets the timestamp back rather
      than by waiting.
- [ ] The 7 and the 0 come from `DataTool.getInt` reads of `info/addDay` and `info/slotIndex`, not
      from literals in Java.
- [ ] Using the item twice grants twice, and neither use throws.
- [ ] No change to the NX/Maple Point credit path from ticket 63 appears in this diff.

Run the new test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`.

## Do not

- Do not fold this into ticket 63. The two rows were split deliberately; 63 has a precedent and this
  one does not.
- Do not invent an expiry mechanism modelled on pet or equipment expiry without saying so. If an
  existing expiry path is reused, name it.
- Do not hardcode 7 days or partition 0. Both are data.
