# 64 - cash items 5550000 and 5550001 have no handler at all

**Class:** v84 parity
**Work rows:** R17 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** None - can start immediately

Item **5550001** is a v84-new cash item whose three `info` leaves describe a seven-day cash
inventory slot expansion, and nothing in the tree reads any of them. It has a **sibling already in
this tree** - `5550000`, same three leaves, 30 days - which is equally unhandled. Effort is medium
because the storage shape has to be chosen and justified before any handler is written.

## R17 - `slotIndex` and `addDay` are read nowhere

Both items live in `wz/Item.wz/Cash/0555.img.xml`, and that file contains exactly these two:

| item | `info/cash` | `info/slotIndex` | `info/addDay` | v84-new? |
|---|---|---|---|---|
| **5550000** | 1 | 0 | **30** | **no** - not in `add-list` |
| **5550001** | 1 | 0 | **7** | yes - `add-list/Item.txt:110` |

There are **zero references to `slotIndex` or `addDay` anywhere in the tree** - not in
`src/main/java`, not in `scripts/`, not in the packet-validator tools (the only grep hits are inside
a vendored `spine-csharp.dll`). `slotIndex` names which cash inventory partition grows; `addDay` is
the lifetime in days.

**Neither item has a handler.** `UseCashItemHandler` dispatches on `itemType = itemId / 10000`
(`:102`) and has branches for 504, 505, 506, 507, 508, 509, 510, 512, 517, 520, 523, 524, 530, 533,
537, 539, 540, 543, 545, 550, 552, 553, 557, 561 and 562 - **no 555**. Both items fall to the
default at `:646`, `log.warn("NEW CASH ITEM TYPE: {}, packet: {}", itemType, p)`.

**Whatever handler is written is therefore type-keyed and covers both items automatically.** Do not
write it for 5550001 alone.

Neither is obtainable today, so nothing regresses whichever shape is chosen.

## Precedent

**No code precedent.** There is no timed slot expansion implemented anywhere in this repo to copy.
Permanent slot growth and expiring cash items both exist separately; nothing combines them.

There *is* a **data** precedent, and it weakens the case that this shape is unprecedented: 5550000
has carried the identical three-leaf shape - including a non-zero `addDay` - since before the v84
cutover. v84 did not introduce the concept here, it added a second duration to a shape this tree
already shipped and never implemented. That is an argument for treating `addDay` as real data rather
than as a field v84's client ignores.

The implementing agent must choose one of two shapes and state the evidence in the commit:

1. **Permanent slot column.** The existing slot-count columns are incremented and the expiry is
   dropped. Cheapest, and wrong against `addDay` unless evidence is found that the client ignores
   the field. Note this shape cannot distinguish 5550000 from 5550001 at all - both would grant the
   same permanent slot, which is a visible argument against it.
2. **An expiring row.** A dated grant that the slot-count read consults, expiring after `addDay`
   days. Faithful to the data; needs a new persisted row and an expiry sweep. This is the only shape
   under which 30 and 7 mean different things.

If neither shape can be evidenced from v84 data or from an existing analogue in this tree, **this
row degrades to an owner decision** and no code is written. Say so plainly in the ticket's closing
note rather than picking one silently - the standing rule is that a value that cannot be derived
makes the row a decision, not an implementation.

## Acceptance criteria

- [ ] The chosen shape - permanent column or expiring row - is named in the commit message together
      with the evidence that selected it, or the ticket is closed as an owner decision with no code.
- [ ] The handler is keyed on `itemType == 555` and **both 5550000 and 5550001 route through it**.
      Neither id appears as a literal.
- [ ] Using either item increases the usable cash inventory slot count for partition `slotIndex=0`
      by the amount the item states, asserted by a test that reads the slot count before and after.
- [ ] If the expiring shape is taken: a grant from 5550001 whose timestamp is more than **7** days
      old no longer counts toward the slot total, and one from 5550000 is still counted at day 7 and
      gone at day 31 - asserted by a test that sets the timestamp back rather than by waiting. The
      two items must be distinguishable by this test.
- [ ] The 30, the 7 and the 0 come from `DataTool.getInt` reads of `info/addDay` and
      `info/slotIndex`, not from literals in Java.
- [ ] Using an item twice grants twice, and neither use throws.
- [ ] `UseCashItemHandler` no longer reaches its `NEW CASH ITEM TYPE` warning for type 555.
- [ ] No change to the NX/Maple Point credit path from ticket 63 appears in this diff.

Run the new test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** -
they collide on `target/`.

## Do not

- Do not write the handler for 5550001 only. 5550000 is in the same img with the same three leaves
  and the same absent handler; a type-keyed branch covers both and an id-keyed one leaves half the
  data broken.
- Do not fold this into ticket 63. The two rows were split deliberately; 63's fix is a read inside an
  existing branch and this one needs a new branch and a storage decision.
- Do not invent an expiry mechanism modelled on pet or equipment expiry without saying so. If an
  existing expiry path is reused, name it.
- Do not hardcode 7 days, 30 days, or partition 0. All three are data.

## Closing note (2026-08-18) - premise wrong, no code written

The ticket's reading of the data is wrong. `wz/String.wz/Cash.img.xml:1774` and `:1992` name these
items **"Add Pendant Slots: 7 Days"** and **"Increasing the Pendant Slots:30 Days"** - *"Allows you
to equip 1 additional pendant for 7 days"*. Type 555 is the **extra pendant equip slot**, not a cash
inventory partition. `slotIndex=0` is the extra-pendant slot index; it is not a cash inventory
partition id. Both shapes offered above, and the acceptance criterion "increases the usable cash
inventory slot count for partition `slotIndex=0`", are written against a feature that does not exist.

What the tree does have: `SendOpcode.SET_EXTRA_PENDANT_SLOT` (`sendops-84.properties:132` = `0x7C`,
verified against the v84 binary in `tools/v84/decode-models-v84-binary.tsv:24`) and
`PacketCreator.setExtraPendantSlot(boolean)` (`:373`), inherited from HeavenMS and called by nothing
but `BinaryDerivedModelTest`.

What it does not have: any second pendant equip position. `constants/inventory/EquipSlot.java:25`
has `PENDANT("Pe", -17)` only - no `-59`. Equip validation, char-look serialisation and the equipped
inventory all assume the v83 slot set. Sending the toggle without that support ships half a feature:
the client opens a slot the server cannot accept an equip into.

Timed-grant persistence has no per-character precedent either - the only expiry shapes in the tree
are `inventoryitems.expiration` (item-bound, `003-inventory.sql:14`) and the one-off
`characters.jailexpire` bigint (`002-character.sql:76`).

**This row is an owner decision**, and a larger one than the ticket scoped: it is "add a second
pendant equip slot" plus "persist a timed grant", not a storage-shape choice. Re-scope before
re-queueing. Ticket 63's credit path is untouched.
